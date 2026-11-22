import React, { useEffect, useRef, useState } from 'react';
import {
  Flame,
  Globe,
  Settings,
  MoreVertical,
  BookOpen,
  RotateCcw,
  Flag,
  History,
  ArrowUpDown,
} from 'lucide-react';
import {
  GAME_MODE_META,
  getActiveColor,
  getActorName,
  opponentPlayer,
  PlayerId,
} from './model/gameModels';
import { useUnoChessGame } from './state/useUnoChessGame';
import { ChessBoardView } from './components/ChessBoardView';
import { UnoCardStrip } from './components/UnoCardComponents';
import {
  GameLogCard,
  PlayerStripCard,
  SidePanelCapturedTraysCard,
  SpecialCardActionHost,
} from './components/PlayerAndSidePanelComponents';
import {
  ConfirmModalDialog,
  GameOverBannerCard,
  MatchLogModalDialog,
  PromotionPickerDialog,
  RulesModalDialog,
} from './components/GameModals';
import { SettingsModalDialog } from './components/SettingsModalDialog';
import { MultiplayerModalDialog } from './components/MultiplayerModalDialog';
import { VictoryCelebrationOverlay } from './components/VictoryCelebrationOverlay';

function useIsExpandedViewport(): boolean {
  const [expanded, setExpanded] = useState(() =>
    typeof window !== 'undefined' ? window.innerWidth >= 680 : false
  );
  useEffect(() => {
    const onResize = () => setExpanded(window.innerWidth >= 680);
    window.addEventListener('resize', onResize);
    return () => window.removeEventListener('resize', onResize);
  }, []);
  return expanded;
}

export const App: React.FC = () => {
  const game = useUnoChessGame();
  const { uiState, matchStats, lanSession, isOnlineMultiplayer, localPlayer } = game;
  const isExpanded = useIsExpandedViewport();

  const [showRules, setShowRules] = useState(false);
  const [showSettings, setShowSettings] = useState(false);
  const [showMultiplayer, setShowMultiplayer] = useState(false);
  const [showMatchLog, setShowMatchLog] = useState(false);
  const [showResetConfirm, setShowResetConfirm] = useState(false);
  const [showForfeitConfirm, setShowForfeitConfirm] = useState(false);
  const [showMenuDropdown, setShowMenuDropdown] = useState(false);
  const [manualFlip, setManualFlip] = useState(false);
  const [dismissedOutcomeKey, setDismissedOutcomeKey] = useState<string | null>(null);

  const menuRef = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    if (!showMenuDropdown) return;
    const onClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setShowMenuDropdown(false);
      }
    };
    window.addEventListener('mousedown', onClick);
    return () => window.removeEventListener('mousedown', onClick);
  }, [showMenuDropdown]);

  // Reset victory dismissal whenever a new match starts
  const outcomeKey = uiState.outcome
    ? `${uiState.outcome.kind}-${uiState.drawRevision}-${uiState.gameLog[0]?.id ?? 0}`
    : null;

  useEffect(() => {
    if (!uiState.outcome) {
      setDismissedOutcomeKey(null);
    }
  }, [uiState.outcome]);

  const canLocalAct = isOnlineMultiplayer
    ? uiState.activePlayer === localPlayer
    : GAME_MODE_META[uiState.gameMode].isAi
      ? uiState.activePlayer === 'PLAYER_1'
      : true;

  const boardFlipped = isOnlineMultiplayer
    ? localPlayer === 'PLAYER_2'
    : manualFlip;

  const bottomPlayer: PlayerId = boardFlipped ? 'PLAYER_2' : 'PLAYER_1';
  const topPlayer: PlayerId = opponentPlayer(bottomPlayer);

  const topColor = uiState.playerColors[topPlayer];
  const bottomColor = uiState.playerColors[bottomPlayer];

  const topName = getActorName(topPlayer, uiState.gameMode, isOnlineMultiplayer, localPlayer);
  const bottomName = getActorName(bottomPlayer, uiState.gameMode, isOnlineMultiplayer, localPlayer);

  const modeBadge = isOnlineMultiplayer
    ? `Online · ${lanSession.roomCode ?? ''}`
    : GAME_MODE_META[uiState.gameMode].shortLabel;

  const renderHeaderBar = () => (
    <header
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '8px',
        padding: isExpanded ? '2px 0' : '4px 12px',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: 0 }}>
        <h1
          className="font-display"
          style={{
            margin: 0,
            fontSize: '18px',
            fontWeight: 800,
            letterSpacing: '-0.02em',
            whiteSpace: 'nowrap',
          }}
        >
          UNO Chess
        </h1>
        <span
          className="font-mono"
          style={{
            padding: '2px 7px',
            borderRadius: '999px',
            background: isOnlineMultiplayer
              ? 'rgba(46, 125, 50, 0.16)'
              : 'var(--surface-subtle)',
            color: isOnlineMultiplayer ? 'var(--accent-success)' : 'var(--ink-secondary)',
            border: '1px solid var(--hairline)',
            fontSize: '10px',
            fontWeight: 700,
            whiteSpace: 'nowrap',
          }}
        >
          {modeBadge}
        </span>
      </div>

      <div style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
        {/* Win Streak Pill */}
        <button
          type="button"
          data-testid="streak_pill_button"
          onClick={() => setShowMatchLog(true)}
          style={{
            height: '36px',
            padding: '0 10px',
            borderRadius: '999px',
            background: 'var(--surface)',
            border: '1px solid var(--hairline)',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '4px',
            fontSize: '12px',
            fontWeight: 800,
          }}
          aria-label="Open match history and win streak"
        >
          <Flame size={14} color={matchStats.currentWinStreak > 0 ? '#EA580C' : 'var(--ink-muted)'} />
          <span className="font-mono">{matchStats.currentWinStreak}</span>
        </button>

        {/* Online Multiplayer */}
        <button
          type="button"
          data-testid="multiplayer_button"
          onClick={() => setShowMultiplayer(true)}
          className="icon-btn-48"
          style={{
            background: isOnlineMultiplayer ? 'rgba(46, 125, 50, 0.16)' : 'var(--surface)',
            border: `1px solid ${isOnlineMultiplayer ? 'var(--accent-success)' : 'var(--hairline)'}`,
          }}
          aria-label="Online multiplayer"
        >
          <Globe size={18} color={isOnlineMultiplayer ? 'var(--accent-success)' : 'var(--ink-primary)'} />
        </button>

        {/* Settings */}
        <button
          type="button"
          data-testid="settings_button"
          onClick={() => setShowSettings(true)}
          className="icon-btn-48"
          style={{
            background: 'var(--surface)',
            border: '1px solid var(--hairline)',
          }}
          aria-label="Settings"
        >
          <Settings size={18} />
        </button>

        {/* Match Menu Dropdown */}
        <div ref={menuRef} style={{ position: 'relative' }}>
          <button
            type="button"
            data-testid="match_menu_button"
            onClick={() => setShowMenuDropdown((v) => !v)}
            className="icon-btn-48"
            style={{
              background: 'var(--surface)',
              border: '1px solid var(--hairline)',
            }}
            aria-label="Match menu"
          >
            <MoreVertical size={18} />
          </button>

          {showMenuDropdown && (
            <div
              style={{
                position: 'absolute',
                top: '48px',
                right: 0,
                zIndex: 50,
                width: '188px',
                borderRadius: '12px',
                background: 'var(--surface)',
                border: '1px solid var(--hairline)',
                boxShadow: 'var(--shadow-md)',
                padding: '6px',
                display: 'flex',
                flexDirection: 'column',
                gap: '2px',
              }}
            >
              <button
                type="button"
                onClick={() => {
                  setShowMenuDropdown(false);
                  setShowRules(true);
                }}
                style={{
                  height: '40px',
                  padding: '0 10px',
                  borderRadius: '8px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  fontSize: '13px',
                  fontWeight: 600,
                }}
              >
                <BookOpen size={15} /> Rules
              </button>
              <button
                type="button"
                onClick={() => {
                  setShowMenuDropdown(false);
                  setShowMatchLog(true);
                }}
                style={{
                  height: '40px',
                  padding: '0 10px',
                  borderRadius: '8px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  fontSize: '13px',
                  fontWeight: 600,
                }}
              >
                <History size={15} /> Match History
              </button>
              {!isOnlineMultiplayer && (
                <button
                  type="button"
                  onClick={() => {
                    setShowMenuDropdown(false);
                    setManualFlip((v) => !v);
                  }}
                  style={{
                    height: '40px',
                    padding: '0 10px',
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    fontSize: '13px',
                    fontWeight: 600,
                  }}
                >
                  <ArrowUpDown size={15} /> Flip Board
                </button>
              )}
              <button
                type="button"
                onClick={() => {
                  setShowMenuDropdown(false);
                  setShowResetConfirm(true);
                }}
                style={{
                  height: '40px',
                  padding: '0 10px',
                  borderRadius: '8px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  fontSize: '13px',
                  fontWeight: 600,
                }}
              >
                <RotateCcw size={15} /> New Game
              </button>
              {uiState.outcome === null && (
                <button
                  type="button"
                  onClick={() => {
                    setShowMenuDropdown(false);
                    setShowForfeitConfirm(true);
                  }}
                  style={{
                    height: '40px',
                    padding: '0 10px',
                    borderRadius: '8px',
                    display: 'flex',
                    alignItems: 'center',
                    gap: '10px',
                    fontSize: '13px',
                    fontWeight: 600,
                    color: 'var(--accent-alert)',
                  }}
                >
                  <Flag size={15} /> Forfeit Match
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </header>
  );

  const renderNoticeHost = () => {
    const notice = uiState.noticeBanner;
    if (!notice) return null;
    return (
      <div
        key={notice.id}
        className="anim-notice"
        style={{
          padding: '8px 12px',
          borderRadius: '10px',
          background: notice.isAlert ? 'rgba(198, 40, 40, 0.14)' : 'var(--surface)',
          border: `1px solid ${notice.isAlert ? 'var(--accent-alert)' : 'var(--hairline)'}`,
          color: notice.isAlert ? 'var(--accent-alert)' : 'var(--ink-primary)',
          fontSize: '12px',
          fontWeight: 600,
          lineHeight: 1.35,
        }}
      >
        {notice.message}
      </div>
    );
  };

  const specialActionProps = {
    state: uiState,
    canLocalAct,
    onConfirmMerge: game.onConfirmMerge,
    onClearMergeSelection: game.onClearMergeSelection,
    onSkipMerge: game.onSkipMerge,
    onConfirmSacrifice: game.onConfirmSacrifice,
    onSkipSacrifice: game.onSkipSacrifice,
    onConfirmPawnPromote: game.onConfirmPawnPromote,
    onClearSwapSelection: game.onClearSwapSelection,
    onSkipSwap: game.onSkipSwap,
    onClearCloneSelection: game.onClearCloneSelection,
    onSkipClone: game.onSkipClone,
    onSelectWildEffect: game.onSelectWildEffect,
    onChooseDrawTwo: game.onChooseDrawTwo,
    onSkipSniper: game.onSkipSniper,
    onSkipPortal: game.onSkipPortal,
    onSkipTrap: game.onSkipTrap,
  };

  const renderPlayerStrip = (player: PlayerId, name: string, color: typeof topColor, showInline: boolean) => (
    <PlayerStripCard
      name={name}
      color={color}
      isActive={uiState.activePlayer === player && uiState.outcome === null}
      isRevivePicking={
        canLocalAct && uiState.phase === 'REVIVING' && uiState.activePlayer === player
      }
      capturedPieces={uiState.capturedPieces[color] ?? []}
      selectedRevivePieceId={uiState.selectedRevivePieceId}
      onCapturedPieceSelected={game.onCapturedPieceSelected}
      showInlineCaptured={showInline}
      isAiThinking={uiState.isAiThinking && player === 'PLAYER_2'}
      testId={`player_strip_${player.toLowerCase()}`}
    />
  );

  return (
    <div className="app-shell">
      {isExpanded ? (
        /* Expanded Tabletop Layout (>= 680px) */
        <main className="expanded-root">
          <div className="expanded-board-col">
            <div className="expanded-board-frame">
              {renderPlayerStrip(topPlayer, topName, topColor, false)}
              <ChessBoardView
                state={uiState}
                flipped={boardFlipped}
                onSquareTapped={game.onSquareTapped}
                edgeToEdge={false}
              />
              {renderPlayerStrip(bottomPlayer, bottomName, bottomColor, false)}
            </div>
          </div>

          <aside className="expanded-side-col">
            {renderHeaderBar()}
            {renderNoticeHost()}
            {uiState.outcome && (
              <GameOverBannerCard
                outcome={uiState.outcome}
                gameMode={uiState.gameMode}
                isOnlineMultiplayer={isOnlineMultiplayer}
                localPlayer={localPlayer}
                onNewGame={game.onResetGame}
              />
            )}
            <UnoCardStrip
              state={uiState}
              canLocalAct={canLocalAct}
              onDrawCard={game.onDrawCard}
              compact={false}
            />
            <SpecialCardActionHost {...specialActionProps} />
            <SidePanelCapturedTraysCard
              state={uiState}
              canLocalAct={canLocalAct}
              onCapturedPieceSelected={game.onCapturedPieceSelected}
            />
            <GameLogCard entries={uiState.gameLog} />
          </aside>
        </main>
      ) : (
        /* Compact Portrait Layout (< 680px) — Edge-to-Edge Maximized Chessboard */
        <main className="portrait-root">
          {renderHeaderBar()}
          <div className="portrait-content">
            <div className="portrait-pad" style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {renderNoticeHost()}
              {renderPlayerStrip(topPlayer, topName, topColor, true)}
            </div>

            <ChessBoardView
              state={uiState}
              flipped={boardFlipped}
              onSquareTapped={game.onSquareTapped}
              edgeToEdge={true}
            />

            <div className="portrait-pad" style={{ display: 'flex', flexDirection: 'column', gap: '8px', paddingBottom: '6px' }}>
              {renderPlayerStrip(bottomPlayer, bottomName, bottomColor, true)}
              {uiState.outcome && (
                <GameOverBannerCard
                  outcome={uiState.outcome}
                  gameMode={uiState.gameMode}
                  isOnlineMultiplayer={isOnlineMultiplayer}
                  localPlayer={localPlayer}
                  onNewGame={game.onResetGame}
                />
              )}
              <SpecialCardActionHost {...specialActionProps} />
              <UnoCardStrip
                state={uiState}
                canLocalAct={canLocalAct}
                onDrawCard={game.onDrawCard}
                compact={true}
              />
            </div>
          </div>
        </main>
      )}

      {/* Modals & Overlays */}
      {uiState.pendingPromotion && (
        <PromotionPickerDialog
          color={
            uiState.phase === 'HIJACKING'
              ? getActiveColor(uiState) === 'WHITE'
                ? 'BLACK'
                : 'WHITE'
              : getActiveColor(uiState)
          }
          onSelect={game.onPromotionSelected}
          onCancel={game.onPromotionCancelled}
        />
      )}

      {showRules && <RulesModalDialog onDismiss={() => setShowRules(false)} />}

      {showSettings && (
        <SettingsModalDialog
          gameMode={uiState.gameMode}
          isDarkTheme={uiState.isDarkTheme}
          cardTheme={uiState.cardTheme}
          isSoundMuted={uiState.isSoundMuted}
          isOnlineMultiplayer={isOnlineMultiplayer}
          onGameModeChanged={game.onGameModeChanged}
          onThemeChanged={game.onThemeChanged}
          onCardThemeChanged={game.onCardThemeChanged}
          onSoundMutedChanged={game.onSoundMutedChanged}
          onDismiss={() => setShowSettings(false)}
        />
      )}

      {showMultiplayer && (
        <MultiplayerModalDialog
          session={lanSession}
          onHostOnline={game.onHostOnline}
          onJoinOnline={game.onJoinOnline}
          onDisconnect={game.onDisconnectOnline}
          onStartListingLobbies={game.onStartListingLobbies}
          onStopListingLobbies={game.onStopListingLobbies}
          onDismiss={() => setShowMultiplayer(false)}
        />
      )}

      {showMatchLog && (
        <MatchLogModalDialog
          stats={matchStats}
          onClearStats={game.onClearMatchStats}
          onDismiss={() => setShowMatchLog(false)}
        />
      )}

      {showResetConfirm && (
        <ConfirmModalDialog
          title="Start New Game?"
          body="This will reset the current board, deck, and captured pieces."
          confirmLabel="New Game"
          onConfirm={() => {
            setShowResetConfirm(false);
            game.onResetGame();
          }}
          onCancel={() => setShowResetConfirm(false)}
        />
      )}

      {showForfeitConfirm && (
        <ConfirmModalDialog
          title="Forfeit Match?"
          body="Forfeiting immediately awards the victory to your opponent."
          confirmLabel="Forfeit"
          isDanger={true}
          onConfirm={() => {
            setShowForfeitConfirm(false);
            game.onForfeitGame();
          }}
          onCancel={() => setShowForfeitConfirm(false)}
        />
      )}

      {uiState.outcome && outcomeKey !== dismissedOutcomeKey && (
        <VictoryCelebrationOverlay
          outcome={uiState.outcome}
          gameMode={uiState.gameMode}
          isOnlineMultiplayer={isOnlineMultiplayer}
          localPlayer={localPlayer}
          onDismiss={() => setDismissedOutcomeKey(outcomeKey)}
          onNewGame={() => {
            setDismissedOutcomeKey(null);
            game.onResetGame();
          }}
        />
      )}
    </div>
  );
};

export default App;
