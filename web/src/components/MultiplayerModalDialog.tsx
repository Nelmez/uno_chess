import React, { useEffect, useState } from 'react';
import { Globe, Copy, Check, RefreshCw, X, LogOut, Users } from 'lucide-react';
import {
  ALL_SPECIAL_CARDS,
  LanSessionState,
  SPECIAL_CARD_META,
  SpecialCardKey,
} from '../model/gameModels';
import { deckSizeFor } from '../engine/unoDeck';
import { isFirebaseConfigured } from '../network/firestoreOnlineSession';

interface MultiplayerModalDialogProps {
  session: LanSessionState;
  onHostOnline: (hostName: string, enabledSpecials: SpecialCardKey[]) => void;
  onJoinOnline: (code: string) => void;
  onDisconnect: () => void;
  onStartListingLobbies: () => void;
  onStopListingLobbies: () => void;
  onDismiss: () => void;
}

export const MultiplayerModalDialog: React.FC<MultiplayerModalDialogProps> = ({
  session,
  onHostOnline,
  onJoinOnline,
  onDisconnect,
  onStartListingLobbies,
  onStopListingLobbies,
  onDismiss,
}) => {
  const [tab, setTab] = useState<'HOST' | 'JOIN'>('HOST');
  const [hostName, setHostName] = useState('Web Player');
  const [joinCode, setJoinCode] = useState('');
  const [selectedSpecials, setSelectedSpecials] = useState<SpecialCardKey[]>([
    ...ALL_SPECIAL_CARDS,
  ]);
  const [copied, setCopied] = useState(false);

  const firebaseReady = isFirebaseConfigured();
  const isConnected = session.status === 'CONNECTED';
  const isWaiting = session.status === 'HOSTING_WAITING';
  const isConnecting = session.status === 'CONNECTING';

  useEffect(() => {
    if (tab === 'JOIN' && !isConnected && firebaseReady) {
      onStartListingLobbies();
      return () => onStopListingLobbies();
    }
    return undefined;
  }, [tab, isConnected, firebaseReady, onStartListingLobbies, onStopListingLobbies]);

  const toggleSpecial = (key: SpecialCardKey) => {
    setSelectedSpecials((prev) =>
      prev.includes(key) ? prev.filter((k) => k !== key) : [...prev, key]
    );
  };

  const handleCopyCode = () => {
    if (!session.roomCode) return;
    void navigator.clipboard?.writeText(session.roomCode).then(() => {
      setCopied(true);
      window.setTimeout(() => setCopied(false), 1800);
    });
  };

  return (
    <div className="modal-backdrop" onClick={onDismiss}>
      <div className="modal-card" onClick={(e) => e.stopPropagation()}>
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '14px 16px',
            borderBottom: '1px solid var(--hairline)',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Globe size={18} />
            <h3 className="font-display" style={{ margin: 0, fontSize: '18px' }}>
              Online Multiplayer
            </h3>
          </div>
          <button type="button" onClick={onDismiss} className="icon-btn-48" aria-label="Close">
            <X size={20} />
          </button>
        </div>

        <div
          style={{
            padding: '14px 16px 18px',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column',
            gap: '14px',
          }}
        >
          {/* Cross-play badge */}
          <div
            style={{
              padding: '9px 12px',
              borderRadius: '10px',
              background: 'var(--surface-subtle)',
              border: '1px solid var(--hairline)',
              fontSize: '12px',
              color: 'var(--ink-secondary)',
            }}
          >
            Uses the shared Firestore lobby protocol — supports real-time{' '}
            <strong style={{ color: 'var(--ink-primary)' }}>Web ↔ Android</strong> cross-play.
          </div>

          {!firebaseReady && (
            <div
              style={{
                padding: '10px 12px',
                borderRadius: '10px',
                background: 'rgba(217, 119, 6, 0.14)',
                border: '1px solid var(--accent-shield)',
                fontSize: '12px',
                color: 'var(--ink-primary)',
                lineHeight: 1.45,
              }}
            >
              <strong>Firebase Web Config Needed:</strong> Copy <code>web/.env.example</code> to{' '}
              <code>web/.env</code> and add your Firebase Web App keys for project{' '}
              <code>unochess-online</code> to enable online lobbies.
            </div>
          )}

          {/* Status Message */}
          {session.statusMessage && (
            <div
              style={{
                padding: '10px 12px',
                borderRadius: '10px',
                background:
                  session.status === 'ERROR'
                    ? 'rgba(198, 40, 40, 0.14)'
                    : session.status === 'CONNECTED'
                      ? 'rgba(46, 125, 50, 0.14)'
                      : 'var(--surface-subtle)',
                border: `1px solid ${
                  session.status === 'ERROR'
                    ? 'var(--accent-alert)'
                    : session.status === 'CONNECTED'
                      ? 'var(--accent-success)'
                      : 'var(--hairline)'
                }`,
                fontSize: '12px',
                fontWeight: 600,
              }}
            >
              {session.statusMessage}
            </div>
          )}

          {/* Connected or Waiting View */}
          {(isConnected || isWaiting) && session.roomCode ? (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', alignItems: 'center' }}>
              <span style={{ fontSize: '12px', color: 'var(--ink-muted)', fontWeight: 700 }}>
                ROOM CODE
              </span>
              <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                <span
                  className="font-mono"
                  style={{
                    fontSize: '28px',
                    fontWeight: 800,
                    letterSpacing: '0.16em',
                    padding: '8px 16px',
                    borderRadius: '12px',
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--hairline)',
                  }}
                >
                  {session.roomCode}
                </span>
                <button
                  type="button"
                  onClick={handleCopyCode}
                  className="icon-btn-48"
                  style={{
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--hairline)',
                  }}
                  aria-label="Copy room code"
                >
                  {copied ? <Check size={18} color="var(--accent-success)" /> : <Copy size={18} />}
                </button>
              </div>
              <button
                type="button"
                onClick={onDisconnect}
                className="pill-btn"
                style={{
                  width: '100%',
                  background: 'var(--accent-alert)',
                  color: '#FFFFFF',
                }}
              >
                <LogOut size={15} /> {isConnected ? 'Leave Match' : 'Cancel Lobby'}
              </button>
            </div>
          ) : (
            <>
              {/* Host / Join Switcher */}
              <div
                style={{
                  display: 'grid',
                  gridTemplateColumns: 'repeat(2, 1fr)',
                  gap: '6px',
                  padding: '4px',
                  borderRadius: '12px',
                  background: 'var(--surface-subtle)',
                }}
              >
                {(['HOST', 'JOIN'] as const).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => setTab(t)}
                    style={{
                      minHeight: '38px',
                      borderRadius: '9px',
                      background: tab === t ? 'var(--surface)' : 'transparent',
                      fontWeight: 700,
                      fontSize: '13px',
                      boxShadow: tab === t ? 'var(--shadow-sm)' : 'none',
                    }}
                  >
                    {t === 'HOST' ? 'Host Match' : 'Join Match'}
                  </button>
                ))}
              </div>

              {tab === 'HOST' ? (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <label style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                    <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--ink-secondary)' }}>
                      Host Display Name
                    </span>
                    <input
                      type="text"
                      value={hostName}
                      maxLength={24}
                      onChange={(e) => setHostName(e.target.value)}
                      placeholder="Web Player"
                      style={{
                        height: '42px',
                        padding: '0 12px',
                        borderRadius: '10px',
                        border: '1px solid var(--hairline)',
                        background: 'var(--surface-subtle)',
                        color: 'var(--ink-primary)',
                        fontSize: '14px',
                      }}
                    />
                  </label>

                  {/* Custom Special Card Deck Picker */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--ink-secondary)' }}>
                        Special Cards ({deckSizeFor(selectedSpecials)}-card deck)
                      </span>
                      <div style={{ display: 'flex', gap: '6px' }}>
                        <button
                          type="button"
                          onClick={() => setSelectedSpecials([...ALL_SPECIAL_CARDS])}
                          style={{ fontSize: '11px', fontWeight: 700, color: 'var(--accent-primary)', textDecoration: 'underline' }}
                        >
                          All
                        </button>
                        <button
                          type="button"
                          onClick={() => setSelectedSpecials([])}
                          style={{ fontSize: '11px', fontWeight: 700, color: 'var(--ink-muted)', textDecoration: 'underline' }}
                        >
                          None
                        </button>
                      </div>
                    </div>

                    <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                      {ALL_SPECIAL_CARDS.map((key) => {
                        const active = selectedSpecials.includes(key);
                        const meta = SPECIAL_CARD_META[key];
                        return (
                          <button
                            key={key}
                            type="button"
                            onClick={() => toggleSpecial(key)}
                            style={{
                              padding: '6px 10px',
                              borderRadius: '999px',
                              fontSize: '11px',
                              fontWeight: 700,
                              background: active ? 'var(--accent-primary)' : 'var(--surface-subtle)',
                              color: active ? 'var(--on-accent-primary)' : 'var(--ink-secondary)',
                              border: `1px solid ${active ? 'var(--accent-primary)' : 'var(--hairline)'}`,
                            }}
                          >
                            {meta.shortSymbol} {meta.displayName}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  <button
                    type="button"
                    disabled={!firebaseReady || isConnecting}
                    onClick={() => onHostOnline(hostName, selectedSpecials)}
                    className="pill-btn"
                    style={{
                      width: '100%',
                      minHeight: '44px',
                      background: 'var(--accent-primary)',
                      color: 'var(--on-accent-primary)',
                      opacity: firebaseReady ? 1 : 0.5,
                    }}
                  >
                    Create Online Lobby (Play as White)
                  </button>
                </div>
              ) : (
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
                  <div style={{ display: 'flex', gap: '8px' }}>
                    <input
                      type="text"
                      value={joinCode}
                      maxLength={6}
                      onChange={(e) => setJoinCode(e.target.value.toUpperCase())}
                      placeholder="6-CHAR CODE"
                      className="font-mono"
                      style={{
                        flex: 1,
                        height: '44px',
                        padding: '0 12px',
                        borderRadius: '10px',
                        border: '1px solid var(--hairline)',
                        background: 'var(--surface-subtle)',
                        color: 'var(--ink-primary)',
                        fontSize: '16px',
                        fontWeight: 700,
                        letterSpacing: '0.12em',
                        textTransform: 'uppercase',
                      }}
                    />
                    <button
                      type="button"
                      disabled={!firebaseReady || joinCode.trim().length !== 6 || isConnecting}
                      onClick={() => onJoinOnline(joinCode)}
                      className="pill-btn"
                      style={{
                        minHeight: '44px',
                        padding: '0 18px',
                        background: 'var(--accent-primary)',
                        color: 'var(--on-accent-primary)',
                        opacity: firebaseReady && joinCode.trim().length === 6 ? 1 : 0.5,
                      }}
                    >
                      Join
                    </button>
                  </div>

                  {/* Open Lobbies List */}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
                    <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
                      <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--ink-secondary)', display: 'inline-flex', alignItems: 'center', gap: '5px' }}>
                        <Users size={14} /> Open Lobbies ({session.openLobbies.length})
                      </span>
                      <button
                        type="button"
                        onClick={onStartListingLobbies}
                        style={{ display: 'inline-flex', alignItems: 'center', gap: '4px', fontSize: '11px', fontWeight: 700, color: 'var(--ink-muted)' }}
                      >
                        <RefreshCw size={12} className={session.isListingLobbies ? 'anim-spin' : undefined} />
                        Refresh
                      </button>
                    </div>

                    {session.openLobbies.length === 0 ? (
                      <div style={{ padding: '16px 0', textAlign: 'center', fontSize: '12px', color: 'var(--ink-muted)' }}>
                        No open lobbies found right now.
                      </div>
                    ) : (
                      session.openLobbies.map((lobby) => (
                        <button
                          key={lobby.code}
                          type="button"
                          onClick={() => onJoinOnline(lobby.code)}
                          style={{
                            padding: '10px 12px',
                            borderRadius: '10px',
                            background: 'var(--surface-subtle)',
                            border: '1px solid var(--hairline)',
                            display: 'flex',
                            alignItems: 'center',
                            justifyContent: 'space-between',
                            textAlign: 'left',
                          }}
                        >
                          <div>
                            <div style={{ fontSize: '13px', fontWeight: 700 }}>{lobby.hostName}</div>
                            <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
                              {lobby.enabledSpecialsCount} special cards enabled
                            </div>
                          </div>
                          <span
                            className="font-mono"
                            style={{
                              padding: '4px 8px',
                              borderRadius: '8px',
                              background: 'var(--surface)',
                              border: '1px solid var(--hairline)',
                              fontSize: '12px',
                              fontWeight: 800,
                            }}
                          >
                            {lobby.code}
                          </span>
                        </button>
                      ))
                    )}
                  </div>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </div>
  );
};
