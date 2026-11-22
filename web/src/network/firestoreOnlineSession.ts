import { FirebaseApp, getApps, initializeApp } from 'firebase/app';
import {
  addDoc,
  collection,
  deleteField,
  doc,
  Firestore,
  getDoc,
  getFirestore,
  limit,
  onSnapshot,
  orderBy,
  query,
  runTransaction,
  Unsubscribe,
  updateDoc,
  where,
} from 'firebase/firestore';
import {
  ALL_SPECIAL_CARDS,
  INITIAL_LAN_SESSION,
  LanSessionState,
  OpenLobbySummary,
  SpecialCardKey,
} from '../model/gameModels';

const COLLECTION_LOBBIES = 'lobbies';
const SUBCOLLECTION_ACTIONS = 'actions';
const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
const CODE_LENGTH = 6;
const HEARTBEAT_INTERVAL_MS = 20_000;
const STALE_LOBBY_MS = 120_000;
const PLAYER_ID_KEY = 'unochess_online_player_id';

function getOrCreatePlayerId(): string {
  try {
    const existing = localStorage.getItem(PLAYER_ID_KEY);
    if (existing) return existing;
    const created =
      typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function'
        ? crypto.randomUUID()
        : `web-${Math.random().toString(36).slice(2)}-${Date.now()}`;
    localStorage.setItem(PLAYER_ID_KEY, created);
    return created;
  } catch {
    return `web-${Date.now()}`;
  }
}

function generateRoomCode(): string {
  let code = '';
  for (let i = 0; i < CODE_LENGTH; i++) {
    code += CODE_ALPHABET[Math.floor(Math.random() * CODE_ALPHABET.length)];
  }
  return code;
}

export function isFirebaseConfigured(): boolean {
  return Boolean(import.meta.env.VITE_FIREBASE_API_KEY && import.meta.env.VITE_FIREBASE_PROJECT_ID);
}

function getFirestoreInstance(): Firestore | null {
  if (!isFirebaseConfigured()) return null;
  let app: FirebaseApp;
  if (getApps().length > 0) {
    app = getApps()[0];
  } else {
    app = initializeApp({
      apiKey: import.meta.env.VITE_FIREBASE_API_KEY,
      authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN,
      projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID,
      storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET,
      messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID,
      appId: import.meta.env.VITE_FIREBASE_APP_ID,
    });
  }
  return getFirestore(app);
}

/**
 * Exact 1:1 wire-compatible port of Android FirestoreOnlineSession.kt:
 * - Lobbies live at /lobbies/{code} with status: "waiting" | "playing" | "closed"
 * - Host publishes game snapshots to "state" + "stateSeq" and heartbeats to "hostSeenAt"
 * - Guest publishes actions to /lobbies/{code}/actions with { fromId, line, createdAt }
 */
export class FirestoreOnlineSession {
  private readonly playerId = getOrCreatePlayerId();
  private state: LanSessionState = { ...INITIAL_LAN_SESSION };
  private onStateChange: (state: LanSessionState) => void;
  private onMessageReceived: (line: string) => void;
  private onPeerConnected: (isHost: boolean) => void;
  private onPeerDisconnected: () => void;

  private activeCode: string | null = null;
  private lobbyUnsub: Unsubscribe | null = null;
  private actionsUnsub: Unsubscribe | null = null;
  private openLobbiesUnsub: Unsubscribe | null = null;
  private heartbeatTimer: number | null = null;

  private lastDeliveredStateSeq = -1;
  private localStateSeq = 0;
  private processedActionIds = new Set<string>();
  private peerJoined = false;

  constructor(callbacks: {
    onStateChange: (state: LanSessionState) => void;
    onMessageReceived: (line: string) => void;
    onPeerConnected: (isHost: boolean) => void;
    onPeerDisconnected: () => void;
  }) {
    this.onStateChange = callbacks.onStateChange;
    this.onMessageReceived = callbacks.onMessageReceived;
    this.onPeerConnected = callbacks.onPeerConnected;
    this.onPeerDisconnected = callbacks.onPeerDisconnected;
  }

  private emit(patch: Partial<LanSessionState>): void {
    this.state = { ...this.state, ...patch };
    this.onStateChange(this.state);
  }

  getState(): LanSessionState {
    return this.state;
  }

  startListingOpenLobbies(): void {
    const db = getFirestoreInstance();
    if (!db) {
      this.emit({ openLobbies: [], isListingLobbies: false });
      return;
    }

    this.stopListingOpenLobbies();
    this.emit({ isListingLobbies: true });

    const q = query(
      collection(db, COLLECTION_LOBBIES),
      where('status', '==', 'waiting'),
      limit(50)
    );

    this.openLobbiesUnsub = onSnapshot(
      q,
      (snap) => {
        const now = Date.now();
        const lobbies: OpenLobbySummary[] = [];
        snap.forEach((docSnap) => {
          const data = docSnap.data();
          const hostSeenAt = typeof data.hostSeenAt === 'number' ? data.hostSeenAt : 0;
          if (now - hostSeenAt > STALE_LOBBY_MS) return;
          if (data.hostId === this.playerId) return;

          const createdAtMs = typeof data.createdAt === 'number' ? data.createdAt : now;
          const rawSpecials = Array.isArray(data.enabledSpecials)
            ? data.enabledSpecials.length
            : ALL_SPECIAL_CARDS.length;

          lobbies.push({
            code: typeof data.code === 'string' ? data.code : docSnap.id,
            hostName:
              typeof data.hostName === 'string' && data.hostName.trim()
                ? data.hostName
                : 'UNO Chess Host',
            createdAtMs,
            enabledSpecialsCount: rawSpecials,
          });
        });
        lobbies.sort((a, b) => b.createdAtMs - a.createdAtMs);
        this.emit({ openLobbies: lobbies, isListingLobbies: false });
      },
      () => {
        this.emit({ isListingLobbies: false });
      }
    );
  }

  stopListingOpenLobbies(): void {
    if (this.openLobbiesUnsub) {
      this.openLobbiesUnsub();
      this.openLobbiesUnsub = null;
    }
    this.emit({ isListingLobbies: false });
  }

  async hostMatch(
    hostName = 'Web Player',
    enabledSpecials: SpecialCardKey[] = ALL_SPECIAL_CARDS
  ): Promise<void> {
    const db = getFirestoreInstance();
    if (!db) {
      this.emit({
        role: 'NONE',
        status: 'ERROR',
        roomCode: null,
        statusMessage:
          'Online multiplayer requires Firebase Web config (.env VITE_FIREBASE_API_KEY & VITE_FIREBASE_PROJECT_ID).',
      });
      return;
    }

    this.disconnect();
    const safeName = hostName.trim().slice(0, 24) || 'Player 1';
    let createdCode: string | null = null;
    let lastError: unknown = null;

    for (let attempt = 0; attempt < 8 && !createdCode; attempt++) {
      const candidate = generateRoomCode();
      const now = Date.now();
      try {
        const lobbyRef = doc(db, COLLECTION_LOBBIES, candidate);
        await runTransaction(db, async (tx) => {
          const existing = await tx.get(lobbyRef);
          if (existing.exists()) throw new Error('Lobby code already exists.');
          tx.set(lobbyRef, {
            code: candidate,
            hostId: this.playerId,
            hostName: safeName,
            status: 'waiting',
            createdAt: now,
            hostSeenAt: now,
            stateSeq: 0,
            enabledSpecials,
          });
        });
        createdCode = candidate;
      } catch (e) {
        lastError = e;
      }
    }

    if (!createdCode) {
      this.emit({
        role: 'NONE',
        status: 'ERROR',
        roomCode: null,
        statusMessage:
          lastError instanceof Error ? lastError.message : 'Failed to create online lobby.',
      });
      return;
    }

    const code = createdCode;
    this.activeCode = code;
    this.localStateSeq = 0;
    this.lastDeliveredStateSeq = -1;
    this.processedActionIds.clear();
    this.peerJoined = false;

    this.emit({
      role: 'ONLINE_HOST',
      status: 'HOSTING_WAITING',
      roomCode: code,
      statusMessage: `Lobby ${code} created. Waiting for opponent…`,
    });

    this.startHeartbeat(db, code, true);

    const lobbyRef = doc(db, COLLECTION_LOBBIES, code);
    this.lobbyUnsub = onSnapshot(lobbyRef, (snap) => {
      if (!snap.exists()) {
        if (this.activeCode === code) {
          this.cleanupListeners();
          this.emit({
            role: 'NONE',
            status: 'DISCONNECTED',
            roomCode: null,
            statusMessage: 'Lobby closed.',
          });
          this.onPeerDisconnected();
        }
        return;
      }
      const data = snap.data();
      const status = data.status as string;
      const guestId = data.guestId as string | undefined;

      if (status === 'closed' && this.peerJoined) {
        this.cleanupListeners();
        this.emit({
          role: 'NONE',
          status: 'DISCONNECTED',
          roomCode: null,
          statusMessage: 'Opponent disconnected.',
        });
        this.onPeerDisconnected();
        return;
      }

      if (status === 'playing' && guestId && !this.peerJoined) {
        this.peerJoined = true;
        this.emit({
          status: 'CONNECTED',
          statusMessage: `Connected to ${data.guestName || 'Player 2'} in lobby ${code}`,
        });
        this.watchActions(db, code);
        this.onPeerConnected(true);
      }
    });
  }

  private watchActions(db: Firestore, code: string): void {
    if (this.actionsUnsub) this.actionsUnsub();
    const actionsQ = query(
      collection(db, COLLECTION_LOBBIES, code, SUBCOLLECTION_ACTIONS),
      orderBy('createdAt', 'asc')
    );
    this.actionsUnsub = onSnapshot(actionsQ, (snap) => {
      snap.docChanges().forEach((change) => {
        if (change.type !== 'added') return;
        const id = change.doc.id;
        if (this.processedActionIds.has(id)) return;
        this.processedActionIds.add(id);
        const data = change.doc.data();
        if (data.fromId === this.playerId) return;
        if (typeof data.line === 'string') {
          this.onMessageReceived(data.line);
        }
      });
    });
  }

  async joinMatch(rawCode: string, displayName = 'Web Player'): Promise<void> {
    const db = getFirestoreInstance();
    if (!db) {
      this.emit({
        role: 'NONE',
        status: 'ERROR',
        roomCode: null,
        statusMessage:
          'Online multiplayer requires Firebase Web config (.env VITE_FIREBASE_API_KEY & VITE_FIREBASE_PROJECT_ID).',
      });
      return;
    }

    const code = rawCode.trim().toUpperCase();
    if (code.length !== CODE_LENGTH) {
      this.emit({
        status: 'ERROR',
        statusMessage: `Enter a ${CODE_LENGTH}-character room code.`,
      });
      return;
    }

    this.disconnect();
    this.activeCode = code;
    this.lastDeliveredStateSeq = -1;
    this.peerJoined = false;
    const safeName = displayName.trim().slice(0, 24) || 'Player 2';

    this.emit({
      role: 'ONLINE_GUEST',
      status: 'CONNECTING',
      roomCode: code,
      statusMessage: `Joining lobby ${code}…`,
    });

    try {
      const lobbyRef = doc(db, COLLECTION_LOBBIES, code);
      await runTransaction(db, async (tx) => {
        const snap = await tx.get(lobbyRef);
        if (!snap.exists()) throw new Error(`Lobby ${code} was not found.`);
        const data = snap.data();
        if (data.status !== 'waiting' || data.guestId) {
          throw new Error(`Lobby ${code} is no longer open.`);
        }
        if (data.hostId === this.playerId) {
          throw new Error('You are already hosting this lobby.');
        }
        tx.update(lobbyRef, {
          guestId: this.playerId,
          guestName: safeName,
          status: 'playing',
          guestSeenAt: Date.now(),
        });
      });

      this.peerJoined = true;
      this.emit({
        status: 'CONNECTED',
        statusMessage: `Connected to lobby ${code} (Player 2)`,
      });
      this.startHeartbeat(db, code, false);
      this.onPeerConnected(false);

      this.lobbyUnsub = onSnapshot(lobbyRef, (snap) => {
        if (!snap.exists()) {
          if (this.activeCode === code) {
            this.cleanupListeners();
            this.emit({
              role: 'NONE',
              status: 'DISCONNECTED',
              roomCode: null,
              statusMessage: 'Lobby closed.',
            });
            this.onPeerDisconnected();
          }
          return;
        }
        const data = snap.data();
        if (data.status === 'closed') {
          this.cleanupListeners();
          this.emit({
            role: 'NONE',
            status: 'DISCONNECTED',
            roomCode: null,
            statusMessage: 'Opponent disconnected.',
          });
          this.onPeerDisconnected();
          return;
        }
        const seq = typeof data.stateSeq === 'number' ? data.stateSeq : 0;
        const stateLine = typeof data.state === 'string' ? data.state : null;
        if (stateLine && seq !== this.lastDeliveredStateSeq) {
          this.lastDeliveredStateSeq = seq;
          this.onMessageReceived(stateLine);
        }
      });
    } catch (err) {
      this.cleanupListeners();
      this.emit({
        role: 'NONE',
        status: 'ERROR',
        roomCode: null,
        statusMessage: err instanceof Error ? err.message : 'Unable to join lobby.',
      });
    }
  }

  sendLine(line: string): void {
    const db = getFirestoreInstance();
    const code = this.activeCode;
    if (!db || !code) return;

    const lobbyRef = doc(db, COLLECTION_LOBBIES, code);
    if (this.state.role === 'ONLINE_HOST') {
      this.localStateSeq += 1;
      void updateDoc(lobbyRef, {
        state: line,
        stateSeq: this.localStateSeq,
        hostSeenAt: Date.now(),
      }).catch(() => undefined);
    } else if (this.state.role === 'ONLINE_GUEST') {
      const actionsCol = collection(db, COLLECTION_LOBBIES, code, SUBCOLLECTION_ACTIONS);
      void addDoc(actionsCol, {
        fromId: this.playerId,
        line,
        createdAt: Date.now(),
      }).catch(() => undefined);
    }
  }

  disconnect(): void {
    const db = getFirestoreInstance();
    const code = this.activeCode;
    const role = this.state.role;
    this.cleanupListeners();

    if (db && code) {
      const lobbyRef = doc(db, COLLECTION_LOBBIES, code);
      if (role === 'ONLINE_HOST') {
        void updateDoc(lobbyRef, { status: 'closed' }).catch(() => undefined);
      } else if (role === 'ONLINE_GUEST') {
        void getDoc(lobbyRef)
          .then((snap) => {
            if (snap.exists() && snap.data().status === 'waiting') {
              return updateDoc(lobbyRef, {
                guestId: deleteField(),
                guestName: deleteField(),
                guestSeenAt: deleteField(),
              });
            }
            return updateDoc(lobbyRef, { status: 'closed' });
          })
          .catch(() => undefined);
      }
    }

    this.emit({
      role: 'NONE',
      status: 'DISCONNECTED',
      roomCode: null,
      statusMessage: null,
    });
  }

  private startHeartbeat(db: Firestore, code: string, asHost: boolean): void {
    if (this.heartbeatTimer !== null) {
      window.clearInterval(this.heartbeatTimer);
    }
    this.heartbeatTimer = window.setInterval(() => {
      if (this.activeCode !== code) return;
      const lobbyRef = doc(db, COLLECTION_LOBBIES, code);
      const field = asHost ? 'hostSeenAt' : 'guestSeenAt';
      void updateDoc(lobbyRef, { [field]: Date.now() }).catch(() => undefined);
    }, HEARTBEAT_INTERVAL_MS);
  }

  private cleanupListeners(): void {
    this.activeCode = null;
    this.peerJoined = false;
    if (this.heartbeatTimer !== null) {
      window.clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
    if (this.lobbyUnsub) {
      this.lobbyUnsub();
      this.lobbyUnsub = null;
    }
    if (this.actionsUnsub) {
      this.actionsUnsub();
      this.actionsUnsub = null;
    }
  }
}
