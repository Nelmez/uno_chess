import React from 'react';
import { X, Trophy, Trash2 } from 'lucide-react';
import {
  ALL_SPECIAL_CARDS,
  colorDisplayName,
  GameMode,
  GameOutcome,
  getActorName,
  MatchStats,
  PIECE_META,
  PieceColor,
  PieceType,
  PlayerId,
  SPECIAL_CARD_META,
} from '../model/gameModels';
import { ChessPieceGraphic } from './ChessPieceGraphic';

export const PromotionPickerDialog: React.FC<{
  color: PieceColor;
  onSelect: (type: PieceType) => void;
  onCancel: () => void;
}> = ({ color, onSelect, onCancel }) => {
  const options: PieceType[] = ['QUEEN', 'ROOK', 'BISHOP', 'KNIGHT'];

  return (
    <div className="modal-backdrop" onClick={onCancel}>
      <div
        className="modal-card"
        style={{ maxWidth: '340px', padding: '18px' }}
        onClick={(e) => e.stopPropagation()}
      >
        <h3 className="font-display" style={{ margin: '0 0 12px', fontSize: '18px' }}>
          Promote Pawn
        </h3>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px' }}>
          {options.map((t) => (
            <button
              key={t}
              type="button"
              data-testid={`promo_${t.toLowerCase()}`}
              onClick={() => onSelect(t)}
              style={{
                padding: '10px 6px',
                borderRadius: '12px',
                background: 'var(--surface-subtle)',
                border: '1px solid var(--hairline)',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '4px',
              }}
            >
              <div style={{ width: '40px', height: '40px' }}>
                <ChessPieceGraphic type={t} color={color} />
              </div>
              <span style={{ fontSize: '11px', fontWeight: 700 }}>{PIECE_META[t].displayName}</span>
            </button>
          ))}
        </div>
        <button
          type="button"
          onClick={onCancel}
          className="pill-btn"
          style={{
            marginTop: '12px',
            background: 'var(--surface-subtle)',
            border: '1px solid var(--hairline)',
            width: '100%',
          }}
        >
          Cancel
        </button>
      </div>
    </div>
  );
};

export const RulesModalDialog: React.FC<{ onDismiss: () => void }> = ({ onDismiss }) => {
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
          <h3 className="font-display" style={{ margin: 0, fontSize: '18px' }}>
            How to Play UNO Chess
          </h3>
          <button
            type="button"
            onClick={onDismiss}
            className="icon-btn-48"
            aria-label="Close rules"
          >
            <X size={20} />
          </button>
        </div>

        <div
          style={{
            padding: '14px 16px',
            overflowY: 'auto',
            display: 'flex',
            flexDirection: 'column',
            gap: '12px',
            fontSize: '13px',
            lineHeight: 1.5,
            color: 'var(--ink-secondary)',
          }}
        >
          <div>
            <strong style={{ color: 'var(--ink-primary)' }}>1. Draw a UNO Card Each Turn</strong>
            <p style={{ margin: '4px 0 0' }}>
              Number cards (1–4) let you make up to that many legal chess moves — max 1 move per
              piece per turn. Giving check immediately ends your turn and forfeits remaining moves.
            </p>
          </div>
          <div>
            <strong style={{ color: 'var(--ink-primary)' }}>2. Starting a Turn in Check</strong>
            <p style={{ margin: '4px 0 0' }}>
              Any frozen piece thaws immediately, and special cards are auto-discarded until you
              draw a Number card so you can always defend your King.
            </p>
          </div>
          <div>
            <strong style={{ color: 'var(--ink-primary)' }}>3. Card Draw Pity System</strong>
            <p style={{ margin: '4px 0 0' }}>
              Drawing consecutive non-move special cards escalates your chance of drawing a Number
              card (up to guaranteed 3–4 move cards on Pity 3+).
            </p>
          </div>
          <div>
            <strong style={{ color: 'var(--ink-primary)' }}>
              4. Special Cards ({ALL_SPECIAL_CARDS.length} Types)
            </strong>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', marginTop: '6px' }}>
              {ALL_SPECIAL_CARDS.map((key) => {
                const m = SPECIAL_CARD_META[key];
                return (
                  <div
                    key={key}
                    style={{
                      padding: '8px 10px',
                      borderRadius: '10px',
                      background: 'var(--surface-subtle)',
                      display: 'flex',
                      gap: '8px',
                    }}
                  >
                    <span
                      className="font-mono"
                      style={{
                        minWidth: '24px',
                        fontWeight: 800,
                        color: 'var(--ink-primary)',
                        textAlign: 'center',
                      }}
                    >
                      {m.shortSymbol}
                    </span>
                    <div>
                      <span style={{ fontWeight: 700, color: 'var(--ink-primary)' }}>
                        {m.displayName} (×{m.deckCopies}):{' '}
                      </span>
                      <span>{m.description}</span>
                    </div>
                  </div>
                );
              })}
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export const ConfirmModalDialog: React.FC<{
  title: string;
  body: string;
  confirmLabel: string;
  isDanger?: boolean;
  onConfirm: () => void;
  onCancel: () => void;
}> = ({ title, body, confirmLabel, isDanger = false, onConfirm, onCancel }) => (
  <div className="modal-backdrop" onClick={onCancel}>
    <div
      className="modal-card"
      style={{ maxWidth: '360px', padding: '18px', gap: '12px' }}
      onClick={(e) => e.stopPropagation()}
    >
      <h3 className="font-display" style={{ margin: 0, fontSize: '18px' }}>
        {title}
      </h3>
      <p style={{ margin: 0, fontSize: '13px', color: 'var(--ink-secondary)', lineHeight: 1.5 }}>
        {body}
      </p>
      <div style={{ display: 'flex', gap: '8px', justifyContent: 'flex-end', marginTop: '4px' }}>
        <button
          type="button"
          onClick={onCancel}
          className="pill-btn"
          style={{
            background: 'var(--surface-subtle)',
            border: '1px solid var(--hairline)',
          }}
        >
          Cancel
        </button>
        <button
          type="button"
          onClick={onConfirm}
          className="pill-btn"
          style={{
            background: isDanger ? 'var(--accent-alert)' : 'var(--accent-primary)',
            color: isDanger ? '#FFFFFF' : 'var(--on-accent-primary)',
          }}
        >
          {confirmLabel}
        </button>
      </div>
    </div>
  </div>
);

export const MatchLogModalDialog: React.FC<{
  stats: MatchStats;
  onClearStats: () => void;
  onDismiss: () => void;
}> = ({ stats, onClearStats, onDismiss }) => {
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
            <Trophy size={18} color="#D97706" />
            <h3 className="font-display" style={{ margin: 0, fontSize: '18px' }}>
              Match History & Streak
            </h3>
          </div>
          <button type="button" onClick={onDismiss} className="icon-btn-48" aria-label="Close">
            <X size={20} />
          </button>
        </div>

        <div style={{ padding: '14px 16px', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '12px' }}>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '8px' }}>
            {[
              { label: 'Streak', value: `${stats.currentWinStreak}` },
              { label: 'Best', value: `${stats.bestWinStreak}` },
              { label: 'Wins', value: `${stats.totalWins}` },
              { label: 'L / D', value: `${stats.totalLosses}/${stats.totalDraws}` },
            ].map((item) => (
              <div
                key={item.label}
                style={{
                  padding: '10px 6px',
                  borderRadius: '10px',
                  background: 'var(--surface-subtle)',
                  textAlign: 'center',
                }}
              >
                <div className="font-display" style={{ fontSize: '18px', fontWeight: 800 }}>
                  {item.value}
                </div>
                <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>{item.label}</div>
              </div>
            ))}
          </div>

          {stats.history.length === 0 ? (
            <div style={{ textAlign: 'center', padding: '24px 0', color: 'var(--ink-muted)', fontSize: '13px' }}>
              No completed matches yet. Finish a match to record your streak!
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '8px' }}>
              {stats.history.map((rec) => (
                <div
                  key={rec.id}
                  style={{
                    padding: '10px 12px',
                    borderRadius: '10px',
                    background: 'var(--surface-subtle)',
                    border: '1px solid var(--hairline)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'space-between',
                    gap: '10px',
                  }}
                >
                  <div style={{ minWidth: 0 }}>
                    <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--ink-primary)' }}>
                      {rec.winnerSummary} · {rec.reason}
                    </div>
                    <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
                      {rec.gameMode} · {rec.turnsPlayed} turns ·{' '}
                      {new Date(rec.timestampMs).toLocaleDateString()}
                    </div>
                  </div>
                  <span
                    className="font-mono"
                    style={{
                      padding: '3px 7px',
                      borderRadius: '999px',
                      fontSize: '10px',
                      fontWeight: 800,
                      background:
                        rec.result === 'WIN'
                          ? 'rgba(46, 125, 50, 0.16)'
                          : rec.result === 'LOSS'
                            ? 'rgba(198, 40, 40, 0.16)'
                            : 'var(--hairline)',
                      color:
                        rec.result === 'WIN'
                          ? 'var(--accent-success)'
                          : rec.result === 'LOSS'
                            ? 'var(--accent-alert)'
                            : 'var(--ink-secondary)',
                    }}
                  >
                    {rec.result}
                  </span>
                </div>
              ))}
            </div>
          )}

          {stats.history.length > 0 && (
            <button
              type="button"
              onClick={onClearStats}
              className="pill-btn"
              style={{
                background: 'var(--surface-subtle)',
                border: '1px solid var(--hairline)',
                color: 'var(--accent-alert)',
                width: '100%',
              }}
            >
              <Trash2 size={15} /> Clear Match History
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export const GameOverBannerCard: React.FC<{
  outcome: GameOutcome;
  gameMode: GameMode;
  isOnlineMultiplayer: boolean;
  localPlayer: PlayerId | null;
  onNewGame: () => void;
}> = ({ outcome, gameMode, isOnlineMultiplayer, localPlayer, onNewGame }) => {
  const headline =
    outcome.kind === 'STALEMATE'
      ? 'Stalemate — Draw'
      : `${getActorName(outcome.winnerPlayer, gameMode, isOnlineMultiplayer, localPlayer)} (${colorDisplayName(outcome.winnerColor)}) Wins!`;

  return (
    <div
      style={{
        padding: '10px 12px',
        borderRadius: '12px',
        background: 'var(--surface)',
        border: '1.5px solid var(--accent-primary)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        gap: '10px',
      }}
    >
      <div>
        <div className="font-display" style={{ fontSize: '14px', fontWeight: 800 }}>
          {headline}
        </div>
        <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
          {outcome.kind === 'CHECKMATE'
            ? 'Checkmate'
            : outcome.kind === 'FORFEIT'
              ? 'Opponent forfeited'
              : outcome.kind === 'SUICIDE'
                ? 'King-only Suicide defeat'
                : 'No legal moves remaining'}
        </div>
      </div>
      <button
        type="button"
        onClick={onNewGame}
        className="pill-btn"
        style={{
          minHeight: '38px',
          background: 'var(--accent-primary)',
          color: 'var(--on-accent-primary)',
        }}
      >
        New Game
      </button>
    </div>
  );
};
