import { useCallback, useEffect, useRef, useState } from 'react';
import {
  ALL_SPECIAL_CARDS,
  colorDisplayName,
  GAME_MODE_META,
  GameMode,
  GameOutcome,
  GameUiState,
  getActorName,
  INITIAL_LAN_SESSION,
  LanAction,
  LanSessionState,
  localPlayerForRole,
  MatchResultType,
  MatchStats,
  PieceType,
  PlayerId,
  SpecialCardKey,
  Square,
  UnoCardTheme,
} from '../model/gameModels';
import {
  clearMatchStats,
  loadMatchStats,
  loadPreferences,
  recordMatchResult,
  savePreferences,
} from '../engine/matchStats';
import * as UnoChessAi from '../engine/unoChessAi';
import { detectSoundEffects } from '../audio/soundEffect';
import { soundManager } from '../audio/soundSynthesizer';
import { decodeAction, decodeState, encodeAction, encodeState } from '../network/lanStateCodec';
import { FirestoreOnlineSession } from '../network/firestoreOnlineSession';
import {
  createInitialGameState,
  describeGameModeLabel,
  executeChessMove,
  executeHijackMove,
  performCapturedPieceSelected,
  performChooseDrawTwoCard,
  performDrawCard,
  performForfeit,
  performPromotionSelected,
  TransitionContext,
} from './gameTransitions';
import {
  performClearCloneSelection,
  performClearSwapSelection,
  performConfirmMerge,
  performConfirmPawnPromote,
  performConfirmSacrifice,
  performSelectWildEffect,
  performSkipClone,
  performSkipMerge,
  performSkipPortal,
  performSkipSacrifice,
  performSkipSniper,
  performSkipSwap,
  performSkipTrap,
  performSquareTap,
} from './specialTransitions';

export function useUnoChessGame() {
  const initialPrefs = useRef(loadPreferences());
  const logIdRef = useRef(1);
  const noticeIdRef = useRef(1);
  const animIdRef = useRef(1);
  const turnsPlayedRef = useRef(0);
  const outcomeRecordedRef = useRef(false);

  const [matchStats, setMatchStats] = useState<MatchStats>(() => loadMatchStats());
  const [lanSession, setLanSession] = useState<LanSessionState>(INITIAL_LAN_SESSION);
  const lanSessionRef = useRef<LanSessionState>(INITIAL_LAN_SESSION);

  const sessionRef = useRef<FirestoreOnlineSession | null>(null);
  const hostCustomSpecialsRef = useRef<SpecialCardKey[]>([...ALL_SPECIAL_CARDS]);

  const recordOutcome = useCallback((outcome: GameOutcome, currentState: GameUiState) => {
    if (outcomeRecordedRef.current) return;
    outcomeRecordedRef.current = true;

    const session = lanSessionRef.current;
    const isOnline = session.role !== 'NONE' && session.status === 'CONNECTED';
    const localPlayer = localPlayerForRole(session.role);
    const referencePlayer: PlayerId = localPlayer ?? 'PLAYER_1';

    let result: MatchResultType = 'DRAW';
    let winnerSummary = 'Draw';
    let reason = 'Stalemate';

    if (outcome.kind === 'CHECKMATE') {
      result = outcome.winnerPlayer === referencePlayer ? 'WIN' : 'LOSS';
      winnerSummary = `${getActorName(outcome.winnerPlayer, currentState.gameMode, isOnline, localPlayer)} (${colorDisplayName(outcome.winnerColor)})`;
      reason = 'Checkmate';
    } else if (outcome.kind === 'FORFEIT') {
      result = outcome.winnerPlayer === referencePlayer ? 'WIN' : 'LOSS';
      winnerSummary = `${getActorName(outcome.winnerPlayer, currentState.gameMode, isOnline, localPlayer)} (${colorDisplayName(outcome.winnerColor)})`;
      reason = 'Forfeit';
    } else if (outcome.kind === 'SUICIDE') {
      result = outcome.winnerPlayer === referencePlayer ? 'WIN' : 'LOSS';
      winnerSummary = `${getActorName(outcome.winnerPlayer, currentState.gameMode, isOnline, localPlayer)} (${colorDisplayName(outcome.winnerColor)})`;
      reason = 'Suicide';
    }

    setMatchStats((prev) =>
      recordMatchResult(prev, {
        gameMode: describeGameModeLabel(currentState.gameMode, isOnline),
        result,
        winnerSummary,
        reason,
        turnsPlayed: turnsPlayedRef.current,
      })
    );
  }, []);

  const makeCtx = useCallback(
    (stateForRecord?: GameUiState): TransitionContext => {
      const session = lanSessionRef.current;
      const isOnline = session.role !== 'NONE' && session.status === 'CONNECTED';
      return {
        nextLogId: () => logIdRef.current++,
        nextNoticeId: () => noticeIdRef.current++,
        nextAnimId: () => animIdRef.current++,
        isOnlineMultiplayer: isOnline,
        localPlayer: localPlayerForRole(session.role),
        onTurnStarted: () => {
          turnsPlayedRef.current += 1;
        },
        onOutcomeRecorded: (outcome) => {
          if (stateForRecord) {
            recordOutcome(outcome, stateForRecord);
          }
        },
      };
    },
    [recordOutcome]
  );

  const [uiState, setUiState] = useState<GameUiState>(() => {
    const p = initialPrefs.current;
    soundManager.setMuted(p.isSoundMuted);
    return createInitialGameState(
      {
        gameMode: p.gameMode,
        isDarkTheme: p.isDarkTheme,
        cardTheme: p.cardTheme,
        isSoundMuted: p.isSoundMuted,
      },
      {
        nextLogId: () => logIdRef.current++,
        nextNoticeId: () => noticeIdRef.current++,
        nextAnimId: () => animIdRef.current++,
        isOnlineMultiplayer: false,
        localPlayer: null,
      }
    );
  });

  const uiStateRef = useRef<GameUiState>(uiState);
  useEffect(() => {
    uiStateRef.current = uiState;
  }, [uiState]);

  // Apply theme attribute to document root
  useEffect(() => {
    document.documentElement.setAttribute('data-theme', uiState.isDarkTheme ? 'dark' : 'light');
    const metaTheme = document.querySelector('meta[name="theme-color"]');
    if (metaTheme) {
      metaTheme.setAttribute('content', uiState.isDarkTheme ? '#141517' : '#F6F5F1');
    }
  }, [uiState.isDarkTheme]);

  // Sound transition detector
  const prevStateForSoundRef = useRef<GameUiState>(uiState);
  useEffect(() => {
    const prev = prevStateForSoundRef.current;
    if (prev !== uiState) {
      const effects = detectSoundEffects(prev, uiState);
      if (effects.length > 0) {
        soundManager.playSequence(effects);
      }
      prevStateForSoundRef.current = uiState;
    }
  }, [uiState]);

  // Auto-dismiss notice banner after 3800ms
  const noticeId = uiState.noticeBanner?.id ?? null;
  useEffect(() => {
    if (noticeId === null) return;
    const timer = window.setTimeout(() => {
      setUiState((prev) =>
        prev.noticeBanner?.id === noticeId ? { ...prev, noticeBanner: null } : prev
      );
    }, 3800);
    return () => window.clearTimeout(timer);
  }, [noticeId]);

  const broadcastIfHost = useCallback((nextState: GameUiState) => {
    const session = lanSessionRef.current;
    if (session.role === 'ONLINE_HOST' && session.status === 'CONNECTED') {
      sessionRef.current?.sendLine(encodeState(nextState));
    }
  }, []);

  const commitState = useCallback(
    (updater: (prev: GameUiState) => GameUiState, shouldBroadcast = true) => {
      setUiState((prev) => {
        const next = updater(prev);
        uiStateRef.current = next;
        if (shouldBroadcast && next !== prev) {
          broadcastIfHost(next);
        }
        return next;
      });
    },
    [broadcastIfHost]
  );

  const isLocalPlayersTurn = useCallback((): boolean => {
    const state = uiStateRef.current;
    const session = lanSessionRef.current;
    if (session.role !== 'NONE' && session.status === 'CONNECTED') {
      return state.activePlayer === localPlayerForRole(session.role);
    }
    if (GAME_MODE_META[state.gameMode].isAi) {
      return state.activePlayer === 'PLAYER_1';
    }
    return true;
  }, []);

  const executeHostAction = useCallback(
    (action: LanAction) => {
      commitState((prev) => {
        const ctx = makeCtx(prev);
        switch (action.type) {
          case 'DRAW':
            return performDrawCard(prev, ctx);
          case 'TAP':
            return performSquareTap(prev, action.square, ctx);
          case 'PROMO':
            return performPromotionSelected(prev, action.pieceType, ctx);
          case 'CANCEL_PROMO':
            return { ...prev, pendingPromotion: null };
          case 'REVIVE':
            return performCapturedPieceSelected(prev, action.pieceId, ctx);
          case 'CONFIRM_MERGE':
            return performConfirmMerge(prev, action.targetType, ctx);
          case 'CLEAR_MERGE':
            return prev.phase === 'MERGING' ? { ...prev, selectedMergeSquares: [] } : prev;
          case 'SKIP_MERGE':
            return performSkipMerge(prev, ctx);
          case 'CONFIRM_SACRIFICE':
            return performConfirmSacrifice(prev, action.recipeId, ctx);
          case 'SKIP_SACRIFICE':
            return performSkipSacrifice(prev, ctx);
          case 'CONFIRM_PAWN_PROMOTE':
            return performConfirmPawnPromote(prev, action.targetType, ctx);
          case 'CLEAR_SWAP':
            return performClearSwapSelection(prev);
          case 'SKIP_SWAP':
            return performSkipSwap(prev, ctx);
          case 'CLEAR_CLONE':
            return performClearCloneSelection(prev);
          case 'SKIP_CLONE':
            return performSkipClone(prev, ctx);
          case 'SELECT_WILD':
            return performSelectWildEffect(prev, action.effect, ctx);
          case 'CHOOSE_DRAW_TWO':
            return performChooseDrawTwoCard(prev, action.index, ctx);
          case 'SKIP_SNIPER':
            return performSkipSniper(prev, ctx);
          case 'SKIP_PORTAL':
            return performSkipPortal(prev, ctx);
          case 'SKIP_TRAP':
            return performSkipTrap(prev, ctx);
          case 'FORFEIT':
            return performForfeit(prev, 'PLAYER_2', ctx);
          case 'NEW_GAME': {
            turnsPlayedRef.current = 0;
            outcomeRecordedRef.current = false;
            return createInitialGameState(
              {
                gameMode: 'PASS_AND_PLAY',
                isDarkTheme: prev.isDarkTheme,
                cardTheme: prev.cardTheme,
                isSoundMuted: prev.isSoundMuted,
                enabledSpecialCards: prev.enabledSpecialCards,
                isOnlineMultiplayer: true,
                localPlayer: 'PLAYER_1',
              },
              ctx
            );
          }
        }
      });
    },
    [commitState, makeCtx]
  );

  // Initialize Firestore online session manager once
  useEffect(() => {
    const session = new FirestoreOnlineSession({
      onStateChange: (nextSession) => {
        lanSessionRef.current = nextSession;
        setLanSession(nextSession);
      },
      onPeerConnected: (isHost) => {
        turnsPlayedRef.current = 0;
        outcomeRecordedRef.current = false;
        if (isHost) {
          commitState((prev) => {
            const ctx = makeCtx(prev);
            return createInitialGameState(
              {
                gameMode: 'PASS_AND_PLAY',
                isDarkTheme: prev.isDarkTheme,
                cardTheme: prev.cardTheme,
                isSoundMuted: prev.isSoundMuted,
                enabledSpecialCards: hostCustomSpecialsRef.current,
                isOnlineMultiplayer: true,
                localPlayer: 'PLAYER_1',
              },
              ctx
            );
          }, true);
        }
      },
      onPeerDisconnected: () => {
        commitState(
          (prev) => ({
            ...prev,
            noticeBanner: {
              id: noticeIdRef.current++,
              message: 'Online opponent disconnected.',
              isAlert: true,
            },
          }),
          false
        );
      },
      onMessageReceived: (line) => {
        const curSession = lanSessionRef.current;
        if (curSession.role === 'ONLINE_HOST') {
          const action = decodeAction(line);
          if (!action) return;
          if (action.type === 'FORFEIT' || action.type === 'NEW_GAME') {
            executeHostAction(action);
            return;
          }
          if (uiStateRef.current.activePlayer === 'PLAYER_2') {
            executeHostAction(action);
          }
        } else if (curSession.role === 'ONLINE_GUEST') {
          commitState((prev) => {
            const decoded = decodeState(line, prev);
            if (!decoded) return prev;
            if (prev.outcome === null && decoded.outcome !== null) {
              recordOutcome(decoded.outcome, decoded);
            }
            if (prev.outcome !== null && decoded.outcome === null) {
              turnsPlayedRef.current = 0;
              outcomeRecordedRef.current = false;
            }
            return decoded;
          }, false);
        }
      },
    });
    sessionRef.current = session;
    return () => {
      session.stopListingOpenLobbies();
      session.disconnect();
    };
  }, [commitState, executeHostAction, makeCtx, recordOutcome]);

  // AI Turn Orchestrator (460ms per step, mirroring Android)
  useEffect(() => {
    const isOnline = lanSession.role !== 'NONE' && lanSession.status === 'CONNECTED';
    if (isOnline) return;
    if (!GAME_MODE_META[uiState.gameMode].isAi) return;
    if (uiState.activePlayer !== 'PLAYER_2' || uiState.outcome !== null) {
      if (uiState.isAiThinking) {
        setUiState((prev) => ({ ...prev, isAiThinking: false }));
      }
      return;
    }

    if (!uiState.isAiThinking) {
      setUiState((prev) => ({ ...prev, isAiThinking: true }));
    }

    const timer = window.setTimeout(() => {
      commitState((prev) => {
        if (prev.activePlayer !== 'PLAYER_2' || prev.outcome !== null) {
          return { ...prev, isAiThinking: false };
        }
        const ctx = makeCtx(prev);
        let next = prev;

        switch (prev.phase) {
          case 'AWAITING_DRAW':
            next = performDrawCard(prev, ctx);
            break;
          case 'MOVING': {
            const move = UnoChessAi.chooseChessMove(prev, prev.gameMode);
            if (move) next = executeChessMove(prev, move, ctx);
            break;
          }
          case 'FREEZING': {
            const sq = UnoChessAi.chooseFreezeTarget(prev, prev.gameMode);
            if (sq) next = performSquareTap(prev, sq, ctx);
            break;
          }
          case 'SHIELDING': {
            const sq = UnoChessAi.chooseShieldTarget(prev, prev.gameMode);
            if (sq) next = performSquareTap(prev, sq, ctx);
            break;
          }
          case 'REVIVING': {
            const choice = UnoChessAi.chooseReviveAction(prev, prev.gameMode);
            if (choice) {
              const withPiece =
                choice.pieceId !== null
                  ? performCapturedPieceSelected(prev, choice.pieceId, ctx)
                  : prev;
              next = performSquareTap(withPiece, choice.square, ctx);
            }
            break;
          }
          case 'MERGING': {
            const choice = UnoChessAi.chooseMergeAction(prev, prev.gameMode);
            if (choice) {
              const selectedState: GameUiState = {
                ...prev,
                selectedMergeSquares: choice.squares,
              };
              next = performConfirmMerge(selectedState, choice.targetType, ctx);
            } else {
              next = performSkipMerge(prev, ctx);
            }
            break;
          }
          case 'SACRIFICING': {
            const choice = UnoChessAi.chooseSacrificeAction(prev, prev.gameMode);
            if (choice) {
              const selectedState: GameUiState = {
                ...prev,
                selectedSacrificeSquare: choice.square,
              };
              next = performConfirmSacrifice(selectedState, choice.recipeId, ctx);
            } else {
              next = performSkipSacrifice(prev, ctx);
            }
            break;
          }
          case 'HIJACKING': {
            const move = UnoChessAi.chooseHijackMove(prev, prev.gameMode);
            if (move) next = executeHijackMove(prev, move, ctx);
            break;
          }
          case 'PROMOTING_PAWN': {
            const choice = UnoChessAi.choosePawnPromoteAction(prev, prev.gameMode);
            if (choice) {
              const selectedState: GameUiState = {
                ...prev,
                selectedPromotePawnSquare: choice.square,
              };
              next = performConfirmPawnPromote(selectedState, choice.targetType, ctx);
            }
            break;
          }
          case 'SUICIDING': {
            const sq = UnoChessAi.chooseSuicideTarget(prev, prev.gameMode);
            if (sq) next = performSquareTap(prev, sq, ctx);
            break;
          }
          case 'SWAPPING': {
            const choice = UnoChessAi.chooseSwapAction(prev, prev.gameMode);
            if (choice) {
              const step1 = performSquareTap(prev, choice.first, ctx);
              next = performSquareTap(step1, choice.second, ctx);
            } else {
              next = performSkipSwap(prev, ctx);
            }
            break;
          }
          case 'CLONING': {
            const choice = UnoChessAi.chooseCloneAction(prev, prev.gameMode);
            if (choice) {
              const step1 = performSquareTap(prev, choice.source, ctx);
              next = performSquareTap(step1, choice.target, ctx);
            } else {
              next = performSkipClone(prev, ctx);
            }
            break;
          }
          case 'WILD_CHOOSING': {
            const effect = UnoChessAi.chooseWildEffect(prev, prev.gameMode);
            if (effect) next = performSelectWildEffect(prev, effect, ctx);
            break;
          }
          case 'SNIPING': {
            const choice = UnoChessAi.chooseSniperAction(prev, prev.gameMode);
            if (choice) {
              const step1 = performSquareTap(prev, choice.sniperSquare, ctx);
              next = performSquareTap(step1, choice.targetSquare, ctx);
            } else {
              next = performSkipSniper(prev, ctx);
            }
            break;
          }
          case 'PORTALING': {
            const choice = UnoChessAi.choosePortalAction(prev, prev.gameMode);
            if (choice) {
              const step1 = performSquareTap(prev, choice.pieceSquare, ctx);
              next = performSquareTap(step1, choice.destSquare, ctx);
            } else {
              next = performSkipPortal(prev, ctx);
            }
            break;
          }
          case 'TRAPPING': {
            const sq = UnoChessAi.chooseTrapSquare(prev, prev.gameMode);
            if (sq) next = performSquareTap(prev, sq, ctx);
            else next = performSkipTrap(prev, ctx);
            break;
          }
          case 'DRAW_TWO_CHOOSING': {
            const choice = UnoChessAi.chooseDrawTwoCard(prev, prev.gameMode);
            next = performChooseDrawTwoCard(prev, choice, ctx);
            break;
          }
          default:
            break;
        }

        const stillAi = next.activePlayer === 'PLAYER_2' && next.outcome === null;
        return { ...next, isAiThinking: stillAi };
      }, false);
    }, 460);

    return () => window.clearTimeout(timer);
  }, [
    uiState.activePlayer,
    uiState.phase,
    uiState.movesRemaining,
    uiState.drawRevision,
    uiState.outcome,
    uiState.gameMode,
    uiState.isAiThinking,
    lanSession.role,
    lanSession.status,
    commitState,
    makeCtx,
  ]);

  // Public User Action Handlers
  const dispatchAction = useCallback(
    (action: LanAction, localHandler: (prev: GameUiState, ctx: TransitionContext) => GameUiState) => {
      if (!isLocalPlayersTurn()) return;
      const session = lanSessionRef.current;
      if (session.role === 'ONLINE_GUEST') {
        sessionRef.current?.sendLine(encodeAction(action));
        return;
      }
      commitState((prev) => localHandler(prev, makeCtx(prev)));
    },
    [commitState, isLocalPlayersTurn, makeCtx]
  );

  const onDrawCard = useCallback(
    () => dispatchAction({ type: 'DRAW' }, performDrawCard),
    [dispatchAction]
  );

  const onSquareTapped = useCallback(
    (square: Square) =>
      dispatchAction({ type: 'TAP', square }, (prev, ctx) => performSquareTap(prev, square, ctx)),
    [dispatchAction]
  );

  const onPromotionSelected = useCallback(
    (pieceType: PieceType) =>
      dispatchAction({ type: 'PROMO', pieceType }, (prev, ctx) =>
        performPromotionSelected(prev, pieceType, ctx)
      ),
    [dispatchAction]
  );

  const onPromotionCancelled = useCallback(
    () =>
      dispatchAction({ type: 'CANCEL_PROMO' }, (prev) => ({
        ...prev,
        pendingPromotion: null,
      })),
    [dispatchAction]
  );

  const onCapturedPieceSelected = useCallback(
    (pieceId: number) =>
      dispatchAction({ type: 'REVIVE', pieceId }, (prev, ctx) =>
        performCapturedPieceSelected(prev, pieceId, ctx)
      ),
    [dispatchAction]
  );

  const onConfirmMerge = useCallback(
    (targetType: PieceType) =>
      dispatchAction({ type: 'CONFIRM_MERGE', targetType }, (prev, ctx) =>
        performConfirmMerge(prev, targetType, ctx)
      ),
    [dispatchAction]
  );

  const onClearMergeSelection = useCallback(
    () =>
      dispatchAction({ type: 'CLEAR_MERGE' }, (prev) =>
        prev.phase === 'MERGING' ? { ...prev, selectedMergeSquares: [] } : prev
      ),
    [dispatchAction]
  );

  const onSkipMerge = useCallback(
    () => dispatchAction({ type: 'SKIP_MERGE' }, performSkipMerge),
    [dispatchAction]
  );

  const onConfirmSacrifice = useCallback(
    (recipeId: string) =>
      dispatchAction({ type: 'CONFIRM_SACRIFICE', recipeId }, (prev, ctx) =>
        performConfirmSacrifice(prev, recipeId, ctx)
      ),
    [dispatchAction]
  );

  const onSkipSacrifice = useCallback(
    () => dispatchAction({ type: 'SKIP_SACRIFICE' }, performSkipSacrifice),
    [dispatchAction]
  );

  const onConfirmPawnPromote = useCallback(
    (targetType: PieceType) =>
      dispatchAction({ type: 'CONFIRM_PAWN_PROMOTE', targetType }, (prev, ctx) =>
        performConfirmPawnPromote(prev, targetType, ctx)
      ),
    [dispatchAction]
  );

  const onClearSwapSelection = useCallback(
    () => dispatchAction({ type: 'CLEAR_SWAP' }, (prev) => performClearSwapSelection(prev)),
    [dispatchAction]
  );

  const onSkipSwap = useCallback(
    () => dispatchAction({ type: 'SKIP_SWAP' }, performSkipSwap),
    [dispatchAction]
  );

  const onClearCloneSelection = useCallback(
    () => dispatchAction({ type: 'CLEAR_CLONE' }, (prev) => performClearCloneSelection(prev)),
    [dispatchAction]
  );

  const onSkipClone = useCallback(
    () => dispatchAction({ type: 'SKIP_CLONE' }, performSkipClone),
    [dispatchAction]
  );

  const onSelectWildEffect = useCallback(
    (effect: SpecialCardKey) =>
      dispatchAction({ type: 'SELECT_WILD', effect }, (prev, ctx) =>
        performSelectWildEffect(prev, effect, ctx)
      ),
    [dispatchAction]
  );

  const onChooseDrawTwo = useCallback(
    (index: number) =>
      dispatchAction({ type: 'CHOOSE_DRAW_TWO', index }, (prev, ctx) =>
        performChooseDrawTwoCard(prev, index, ctx)
      ),
    [dispatchAction]
  );

  const onSkipSniper = useCallback(
    () => dispatchAction({ type: 'SKIP_SNIPER' }, performSkipSniper),
    [dispatchAction]
  );

  const onSkipPortal = useCallback(
    () => dispatchAction({ type: 'SKIP_PORTAL' }, performSkipPortal),
    [dispatchAction]
  );

  const onSkipTrap = useCallback(
    () => dispatchAction({ type: 'SKIP_TRAP' }, performSkipTrap),
    [dispatchAction]
  );

  const onForfeitGame = useCallback(() => {
    const session = lanSessionRef.current;
    if (session.role === 'ONLINE_GUEST') {
      sessionRef.current?.sendLine(encodeAction({ type: 'FORFEIT' }));
      return;
    }
    commitState((prev) => {
      const ctx = makeCtx(prev);
      const forfeiting: PlayerId =
        session.role === 'ONLINE_HOST'
          ? 'PLAYER_1'
          : GAME_MODE_META[prev.gameMode].isAi
            ? 'PLAYER_1'
            : prev.activePlayer;
      return performForfeit(prev, forfeiting, ctx);
    });
  }, [commitState, makeCtx]);

  const onResetGame = useCallback(() => {
    const session = lanSessionRef.current;
    if (session.role === 'ONLINE_GUEST') {
      sessionRef.current?.sendLine(encodeAction({ type: 'NEW_GAME' }));
      return;
    }
    turnsPlayedRef.current = 0;
    outcomeRecordedRef.current = false;
    commitState((prev) =>
      createInitialGameState(
        {
          gameMode: session.role === 'ONLINE_HOST' ? 'PASS_AND_PLAY' : prev.gameMode,
          isDarkTheme: prev.isDarkTheme,
          cardTheme: prev.cardTheme,
          isSoundMuted: prev.isSoundMuted,
          enabledSpecialCards:
            session.role === 'ONLINE_HOST' ? prev.enabledSpecialCards : ALL_SPECIAL_CARDS,
          isOnlineMultiplayer: session.role !== 'NONE' && session.status === 'CONNECTED',
          localPlayer: localPlayerForRole(session.role),
        },
        makeCtx(prev)
      )
    );
  }, [commitState, makeCtx]);

  const onGameModeChanged = useCallback(
    (mode: GameMode) => {
      if (lanSessionRef.current.role !== 'NONE') {
        sessionRef.current?.disconnect();
      }
      turnsPlayedRef.current = 0;
      outcomeRecordedRef.current = false;
      commitState((prev) => {
        savePreferences({
          isDarkTheme: prev.isDarkTheme,
          cardTheme: prev.cardTheme,
          isSoundMuted: prev.isSoundMuted,
          gameMode: mode,
        });
        return createInitialGameState(
          {
            gameMode: mode,
            isDarkTheme: prev.isDarkTheme,
            cardTheme: prev.cardTheme,
            isSoundMuted: prev.isSoundMuted,
          },
          makeCtx(prev)
        );
      }, false);
    },
    [commitState, makeCtx]
  );

  const onThemeChanged = useCallback(
    (isDark: boolean) => {
      commitState((prev) => {
        savePreferences({
          isDarkTheme: isDark,
          cardTheme: prev.cardTheme,
          isSoundMuted: prev.isSoundMuted,
          gameMode: prev.gameMode,
        });
        return { ...prev, isDarkTheme: isDark };
      }, false);
    },
    [commitState]
  );

  const onCardThemeChanged = useCallback(
    (theme: UnoCardTheme) => {
      commitState((prev) => {
        savePreferences({
          isDarkTheme: prev.isDarkTheme,
          cardTheme: theme,
          isSoundMuted: prev.isSoundMuted,
          gameMode: prev.gameMode,
        });
        return { ...prev, cardTheme: theme };
      }, false);
    },
    [commitState]
  );

  const onSoundMutedChanged = useCallback(
    (muted: boolean) => {
      soundManager.setMuted(muted);
      commitState((prev) => {
        savePreferences({
          isDarkTheme: prev.isDarkTheme,
          cardTheme: prev.cardTheme,
          isSoundMuted: muted,
          gameMode: prev.gameMode,
        });
        return { ...prev, isSoundMuted: muted };
      }, false);
    },
    [commitState]
  );

  const onClearMatchStats = useCallback(() => {
    setMatchStats(clearMatchStats());
  }, []);

  const onHostOnline = useCallback((hostName: string, enabledSpecials: SpecialCardKey[]) => {
    hostCustomSpecialsRef.current = enabledSpecials;
    void sessionRef.current?.hostMatch(hostName, enabledSpecials);
  }, []);

  const onJoinOnline = useCallback((code: string) => {
    void sessionRef.current?.joinMatch(code);
  }, []);

  const onDisconnectOnline = useCallback(() => {
    sessionRef.current?.disconnect();
  }, []);

  const onStartListingLobbies = useCallback(() => {
    sessionRef.current?.startListingOpenLobbies();
  }, []);

  const onStopListingLobbies = useCallback(() => {
    sessionRef.current?.stopListingOpenLobbies();
  }, []);

  return {
    uiState,
    matchStats,
    lanSession,
    isOnlineMultiplayer: lanSession.role !== 'NONE' && lanSession.status === 'CONNECTED',
    localPlayer: localPlayerForRole(lanSession.role),
    onDrawCard,
    onSquareTapped,
    onPromotionSelected,
    onPromotionCancelled,
    onCapturedPieceSelected,
    onConfirmMerge,
    onClearMergeSelection,
    onSkipMerge,
    onConfirmSacrifice,
    onSkipSacrifice,
    onConfirmPawnPromote,
    onClearSwapSelection,
    onSkipSwap,
    onClearCloneSelection,
    onSkipClone,
    onSelectWildEffect,
    onChooseDrawTwo,
    onSkipSniper,
    onSkipPortal,
    onSkipTrap,
    onForfeitGame,
    onResetGame,
    onGameModeChanged,
    onThemeChanged,
    onCardThemeChanged,
    onSoundMutedChanged,
    onClearMatchStats,
    onHostOnline,
    onJoinOnline,
    onDisconnectOnline,
    onStartListingLobbies,
    onStopListingLobbies,
  };
}
