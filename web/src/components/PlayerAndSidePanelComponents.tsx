import React from 'react';
import {
  cardBadgeLabel,
  cardSubtitle,
  cardTitle,
  ChessPiece,
  colorDisplayName,
  GameUiState,
  getActiveColor,
  LogEntry,
  PIECE_META,
  PieceColor,
  PieceType,
  SPECIAL_CARD_META,
  SpecialCardKey,
  squareToAlgebraic,
} from '../model/gameModels';
import * as ChessEngine from '../engine/chessEngine';
import { ChessPieceGraphic } from './ChessPieceGraphic';

interface PlayerStripCardProps {
  name: string;
  color: PieceColor;
  isActive: boolean;
  isRevivePicking: boolean;
  capturedPieces: ChessPiece[];
  selectedRevivePieceId: number | null;
  onCapturedPieceSelected: (id: number) => void;
  showInlineCaptured?: boolean;
  isAiThinking?: boolean;
  testId?: string;
}

export const PlayerStripCard: React.FC<PlayerStripCardProps> = ({
  name,
  color,
  isActive,
  isRevivePicking,
  capturedPieces,
  selectedRevivePieceId,
  onCapturedPieceSelected,
  showInlineCaptured = true,
  isAiThinking = false,
  testId,
}) => {
  const showReviveRow = isRevivePicking && capturedPieces.length > 0;

  return (
    <div
      data-testid={testId}
      style={{
        display: 'flex',
        flexDirection: 'column',
        gap: showReviveRow ? '6px' : '0px',
        padding: '7px 10px',
        borderRadius: '12px',
        background: isActive ? 'var(--surface)' : 'var(--surface-subtle)',
        border: `1.5px solid ${
          isRevivePicking
            ? 'var(--accent-success)'
            : isActive
              ? 'var(--accent-primary)'
              : 'var(--hairline)'
        }`,
        boxShadow: isActive ? 'var(--shadow-sm)' : 'none',
        transition: 'border-color 0.16s ease, background-color 0.16s ease',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minHeight: '28px' }}>
        {/* Color control badge */}
        <span
          style={{
            width: '20px',
            height: '20px',
            borderRadius: '999px',
            background: color === 'WHITE' ? '#FAF8F3' : '#1E1E22',
            border: `2px solid ${color === 'WHITE' ? '#1B1B1E' : '#EAE8E1'}`,
            flexShrink: 0,
          }}
          title={colorDisplayName(color)}
        />

        <div style={{ display: 'flex', alignItems: 'center', gap: '6px', minWidth: 0 }}>
          <span
            className="font-display"
            style={{
              fontSize: '14px',
              fontWeight: 700,
              color: 'var(--ink-primary)',
              whiteSpace: 'nowrap',
            }}
          >
            {name}
          </span>
          <span
            style={{
              fontSize: '11px',
              fontWeight: 600,
              color: 'var(--ink-muted)',
              whiteSpace: 'nowrap',
            }}
          >
            {colorDisplayName(color)}
          </span>
          {isActive && (
            <span
              style={{
                padding: '2px 6px',
                borderRadius: '999px',
                background: 'var(--accent-primary)',
                color: 'var(--on-accent-primary)',
                fontSize: '10px',
                fontWeight: 700,
                whiteSpace: 'nowrap',
              }}
            >
              {isAiThinking ? 'Thinking…' : 'Turn'}
            </span>
          )}
        </div>

        {/* Compact inline captured pieces (when not in revive picker mode) */}
        {showInlineCaptured && !showReviveRow && (
          <div
            style={{
              flex: 1,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'flex-end',
              gap: '2px',
              overflow: 'hidden',
              minWidth: 0,
            }}
          >
            {capturedPieces.slice(0, 10).map((p) => (
              <div key={p.id} style={{ width: '18px', height: '18px', flexShrink: 0 }}>
                <ChessPieceGraphic type={p.type} color={p.color} />
              </div>
            ))}
            {capturedPieces.length > 10 && (
              <span
                className="font-mono"
                style={{ fontSize: '10px', color: 'var(--ink-muted)', marginLeft: '2px' }}
              >
                +{capturedPieces.length - 10}
              </span>
            )}
          </div>
        )}
      </div>

      {/* Expandable interactive Revive strip */}
      {showReviveRow && (
        <div
          style={{
            display: 'flex',
            alignItems: 'center',
            gap: '6px',
            overflowX: 'auto',
            paddingTop: '4px',
            borderTop: '1px solid var(--hairline)',
          }}
        >
          <span
            style={{
              fontSize: '11px',
              fontWeight: 700,
              color: 'var(--accent-success)',
              whiteSpace: 'nowrap',
            }}
          >
            Revive:
          </span>
          {capturedPieces.map((p) => {
            const selected = p.id === selectedRevivePieceId;
            return (
              <button
                key={p.id}
                type="button"
                data-testid={`revive_piece_${p.id}`}
                onClick={() => onCapturedPieceSelected(p.id)}
                style={{
                  width: '36px',
                  height: '36px',
                  borderRadius: '8px',
                  padding: '4px',
                  background: selected ? 'rgba(67, 160, 71, 0.22)' : 'var(--surface-subtle)',
                  border: `1.5px solid ${selected ? 'var(--accent-success)' : 'var(--hairline)'}`,
                  flexShrink: 0,
                }}
                aria-label={`Select captured ${PIECE_META[p.type].displayName} to revive`}
              >
                <ChessPieceGraphic type={p.type} color={p.color} />
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
};

interface SidePanelCapturedTraysCardProps {
  state: GameUiState;
  canLocalAct: boolean;
  onCapturedPieceSelected: (pieceId: number) => void;
}

export const SidePanelCapturedTraysCard: React.FC<SidePanelCapturedTraysCardProps> = ({
  state,
  canLocalAct,
  onCapturedPieceSelected,
}) => {
  const activeColor = getActiveColor(state);
  const renderRow = (color: PieceColor, label: string) => {
    const pieces = state.capturedPieces[color] ?? [];
    const interactive = canLocalAct && state.phase === 'REVIVING' && activeColor === color;

    return (
      <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <span style={{ fontSize: '11px', fontWeight: 700, color: 'var(--ink-secondary)' }}>
            {label} ({pieces.length})
          </span>
          {interactive && pieces.length > 0 && (
            <span style={{ fontSize: '10px', fontWeight: 700, color: 'var(--accent-success)' }}>
              Tap to revive
            </span>
          )}
        </div>
        {pieces.length === 0 ? (
          <span style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>None captured</span>
        ) : (
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: '4px' }}>
            {pieces.map((p) => {
              const selected = interactive && p.id === state.selectedRevivePieceId;
              return (
                <button
                  key={p.id}
                  type="button"
                  disabled={!interactive}
                  onClick={() => interactive && onCapturedPieceSelected(p.id)}
                  style={{
                    width: interactive ? '34px' : '24px',
                    height: interactive ? '34px' : '24px',
                    borderRadius: '6px',
                    padding: interactive ? '3px' : '1px',
                    background: selected
                      ? 'rgba(67, 160, 71, 0.22)'
                      : interactive
                        ? 'var(--surface-subtle)'
                        : 'transparent',
                    border: interactive
                      ? `1.5px solid ${selected ? 'var(--accent-success)' : 'var(--hairline)'}`
                      : 'none',
                    cursor: interactive ? 'pointer' : 'default',
                  }}
                >
                  <ChessPieceGraphic type={p.type} color={p.color} />
                </button>
              );
            })}
          </div>
        )}
      </div>
    );
  };

  return (
    <div
      style={{
        padding: '10px 12px',
        borderRadius: '12px',
        background: 'var(--surface)',
        border: '1px solid var(--hairline)',
        display: 'flex',
        flexDirection: 'column',
        gap: '8px',
      }}
    >
      <span
        style={{
          fontSize: '11px',
          fontWeight: 800,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          color: 'var(--ink-muted)',
        }}
      >
        Captured Pieces
      </span>
      {renderRow('WHITE', 'White Captured')}
      <div style={{ height: '1px', background: 'var(--hairline)' }} />
      {renderRow('BLACK', 'Black Captured')}
    </div>
  );
};

interface SpecialCardActionHostProps {
  state: GameUiState;
  canLocalAct: boolean;
  onConfirmMerge: (targetType: PieceType) => void;
  onClearMergeSelection: () => void;
  onSkipMerge: () => void;
  onConfirmSacrifice: (recipeId: string) => void;
  onSkipSacrifice: () => void;
  onConfirmPawnPromote: (targetType: PieceType) => void;
  onClearSwapSelection: () => void;
  onSkipSwap: () => void;
  onClearCloneSelection: () => void;
  onSkipClone: () => void;
  onSelectWildEffect: (effect: SpecialCardKey) => void;
  onChooseDrawTwo?: (index: number) => void;
  onSkipSniper?: () => void;
  onSkipPortal?: () => void;
  onSkipTrap?: () => void;
}

export const SpecialCardActionHost: React.FC<SpecialCardActionHostProps> = ({
  state,
  canLocalAct,
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
}) => {
  if (!canLocalAct || state.outcome !== null) return null;
  const activeColor = getActiveColor(state);

  if (state.phase === 'MERGING') {
    const selected = state.selectedMergeSquares;
    const totalPts = selected.reduce((sum, sq) => {
      const p = state.board[sq.row][sq.col];
      return sum + (p ? PIECE_META[p.type].materialValue : 0);
    }, 0);
    const targets = ChessEngine.getValidTradeTargets(state.board, activeColor, selected);

    return (
      <div
        data-testid="merge_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid var(--accent-merge)',
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--ink-primary)' }}>
              {selected.length === 0
                ? '⊕ Merge — Tap 1+ of your pieces'
                : `⊕ Selected ${selected.length} (${totalPts} pts) → ${squareToAlgebraic(selected[0]).toUpperCase()}`}
            </div>
            <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
              First tapped square is the anchor for the new piece
            </div>
          </div>
          <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
            {selected.length > 0 && (
              <button
                type="button"
                data-testid="merge_clear_button"
                onClick={onClearMergeSelection}
                className="pill-btn"
                style={{
                  minHeight: '36px',
                  background: 'var(--surface-subtle)',
                  border: '1px solid var(--hairline)',
                  fontSize: '12px',
                }}
              >
                Clear
              </button>
            )}
            <button
              type="button"
              data-testid="merge_skip_button"
              onClick={onSkipMerge}
              className="pill-btn"
              style={{
                minHeight: '36px',
                background: 'var(--accent-primary)',
                color: 'var(--on-accent-primary)',
                fontSize: '12px',
              }}
            >
              Skip
            </button>
          </div>
        </div>

        {targets.length > 0 && (
          <div style={{ display: 'flex', gap: '6px', overflowX: 'auto' }}>
            {targets.map((t) => (
              <button
                key={t}
                type="button"
                data-testid={`merge_target_${t.toLowerCase()}`}
                onClick={() => onConfirmMerge(t)}
                className="pill-btn"
                style={{
                  minHeight: '38px',
                  background: 'rgba(124, 58, 237, 0.14)',
                  border: '1px solid var(--accent-merge)',
                  color: 'var(--ink-primary)',
                  fontSize: '12px',
                  whiteSpace: 'nowrap',
                }}
              >
                → {PIECE_META[t].displayName} ({PIECE_META[t].materialValue}pt)
              </button>
            ))}
          </div>
        )}
      </div>
    );
  }

  if (state.phase === 'SACRIFICING') {
    const sq = state.selectedSacrificeSquare;
    const piece = sq ? state.board[sq.row][sq.col] : null;
    const recipes = sq ? ChessEngine.getValidSacrificeRecipes(state.board, activeColor, sq) : [];

    return (
      <div
        data-testid="sacrifice_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid var(--accent-sacrifice)',
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
        }}
      >
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: '8px' }}>
          <div style={{ minWidth: 0 }}>
            <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--ink-primary)' }}>
              {piece && sq
                ? `❖ Split ${PIECE_META[piece.type].displayName} (${squareToAlgebraic(sq).toUpperCase()})`
                : '❖ Sacrifice — Select a highlighted piece'}
            </div>
            <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
              Choose an equal-value split or skip
            </div>
          </div>
          <button
            type="button"
            data-testid="sacrifice_skip_button"
            onClick={onSkipSacrifice}
            className="pill-btn"
            style={{
              minHeight: '36px',
              background: 'var(--accent-primary)',
              color: 'var(--on-accent-primary)',
              fontSize: '12px',
            }}
          >
            Skip
          </button>
        </div>

        {recipes.length > 0 && (
          <div style={{ display: 'flex', gap: '6px', overflowX: 'auto' }}>
            {recipes.map((r) => (
              <button
                key={r.id}
                type="button"
                data-testid={`sacrifice_recipe_${r.id}`}
                onClick={() => onConfirmSacrifice(r.id)}
                className="pill-btn"
                style={{
                  minHeight: '38px',
                  background: 'rgba(234, 88, 12, 0.14)',
                  border: '1px solid var(--accent-sacrifice)',
                  color: 'var(--ink-primary)',
                  fontSize: '12px',
                  whiteSpace: 'nowrap',
                }}
              >
                {r.label}
              </button>
            ))}
          </div>
        )}
      </div>
    );
  }

  if (state.phase === 'PROMOTING_PAWN') {
    const sq = state.selectedPromotePawnSquare;
    const targets = sq ? ChessEngine.getValidPawnPromoteTargets(state.board, activeColor, sq) : [];

    return (
      <div
        data-testid="pawn_promote_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid var(--accent-promote)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '10px',
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--ink-primary)' }}>
            {sq ? `↑ Promote Pawn (${squareToAlgebraic(sq).toUpperCase()})` : '↑ Promote Pawn'}
          </div>
          <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
            Pick Knight or Bishop (or tap another pawn)
          </div>
        </div>
        <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
          {targets.map((t) => (
            <button
              key={t}
              type="button"
              data-testid={`promote_pawn_${t.toLowerCase()}`}
              onClick={() => onConfirmPawnPromote(t)}
              className="pill-btn"
              style={{
                minHeight: '38px',
                background: 'rgba(22, 163, 74, 0.14)',
                border: '1px solid var(--accent-promote)',
                color: 'var(--ink-primary)',
                fontSize: '12px',
              }}
            >
              {PIECE_META[t].displayName}
            </button>
          ))}
        </div>
      </div>
    );
  }

  if (state.phase === 'SWAPPING') {
    const firstSq = state.selectedSwapSquare;
    const firstPiece = firstSq ? state.board[firstSq.row][firstSq.col] : null;
    return (
      <div
        data-testid="swap_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid var(--accent-swap)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '10px',
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--ink-primary)' }}>
            {firstPiece && firstSq
              ? `Selected ${PIECE_META[firstPiece.type].displayName} (${squareToAlgebraic(firstSq).toUpperCase()})`
              : '⇄ Swap Positions'}
          </div>
          <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
            {firstSq ? 'Tap a highlighted partner square' : 'Tap 2 of your highlighted pieces'}
          </div>
        </div>
        <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
          {firstSq && (
            <button
              type="button"
              data-testid="swap_clear_button"
              onClick={onClearSwapSelection}
              className="pill-btn"
              style={{
                minHeight: '36px',
                background: 'var(--surface-subtle)',
                border: '1px solid var(--hairline)',
                fontSize: '12px',
              }}
            >
              Clear
            </button>
          )}
          <button
            type="button"
            data-testid="swap_skip_button"
            onClick={onSkipSwap}
            className="pill-btn"
            style={{
              minHeight: '36px',
              background: 'var(--accent-primary)',
              color: 'var(--on-accent-primary)',
              fontSize: '12px',
            }}
          >
            Skip
          </button>
        </div>
      </div>
    );
  }

  if (state.phase === 'CLONING') {
    const srcSq = state.selectedCloneSourceSquare;
    const srcPiece = srcSq ? state.board[srcSq.row][srcSq.col] : null;
    return (
      <div
        data-testid="clone_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid var(--accent-clone)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '10px',
        }}
      >
        <div style={{ minWidth: 0 }}>
          <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--ink-primary)' }}>
            {srcPiece && srcSq
              ? `Cloning ${PIECE_META[srcPiece.type].displayName} (${squareToAlgebraic(srcSq).toUpperCase()})`
              : '⧉ Clone (Pawn / Knight / Bishop)'}
          </div>
          <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
            {srcSq ? 'Tap a highlighted empty square' : 'Tap an eligible piece to duplicate'}
          </div>
        </div>
        <div style={{ display: 'flex', gap: '6px', flexShrink: 0 }}>
          {srcSq && (
            <button
              type="button"
              data-testid="clone_clear_button"
              onClick={onClearCloneSelection}
              className="pill-btn"
              style={{
                minHeight: '36px',
                background: 'var(--surface-subtle)',
                border: '1px solid var(--hairline)',
                fontSize: '12px',
              }}
            >
              Clear
            </button>
          )}
          <button
            type="button"
            data-testid="clone_skip_button"
            onClick={onSkipClone}
            className="pill-btn"
            style={{
              minHeight: '36px',
              background: 'var(--accent-primary)',
              color: 'var(--on-accent-primary)',
              fontSize: '12px',
            }}
          >
            Skip
          </button>
        </div>
      </div>
    );
  }

  if (state.phase === 'WILD_CHOOSING') {
    return (
      <div
        data-testid="wild_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid var(--accent-wild)',
          display: 'flex',
          flexDirection: 'column',
          gap: '8px',
        }}
      >
        <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--accent-wild)' }}>
          ✦ Wild Card — Pick a Special Effect
        </div>
        <div style={{ display: 'flex', gap: '6px', overflowX: 'auto', paddingBottom: '2px' }}>
          {state.availableWildEffects.map((effect) => (
            <button
              key={effect}
              type="button"
              data-testid={`wild_effect_${effect.toLowerCase()}`}
              onClick={() => onSelectWildEffect(effect)}
              className="pill-btn"
              style={{
                minHeight: '38px',
                background: 'rgba(236, 72, 153, 0.14)',
                border: '1px solid var(--accent-wild)',
                color: 'var(--ink-primary)',
                fontSize: '12px',
                whiteSpace: 'nowrap',
              }}
            >
              {SPECIAL_CARD_META[effect].shortSymbol} {SPECIAL_CARD_META[effect].displayName}
            </button>
          ))}
        </div>
      </div>
    );
  }

  if (state.phase === 'SNIPING') {
    return (
      <div
        data-testid="sniper_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid #EF4444',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
        }}
      >
        <div>
          <div style={{ fontSize: '13px', fontWeight: 700, color: '#EF4444' }}>
            🎯 Sniper — Ranged Attack
          </div>
          <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
            {state.selectedSniperSquare
              ? 'Tap a red enemy target to eliminate it without moving.'
              : 'Tap a green friendly piece with enemy pieces in range.'}
          </div>
        </div>
        {onSkipSniper && (
          <button
            type="button"
            onClick={onSkipSniper}
            className="pill-btn"
            style={{
              minHeight: '36px',
              background: 'var(--surface-subtle)',
              border: '1px solid var(--hairline)',
              fontSize: '12px',
              flexShrink: 0,
            }}
          >
            Skip
          </button>
        )}
      </div>
    );
  }

  if (state.phase === 'PORTALING') {
    return (
      <div
        data-testid="portal_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid #06B6D4',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
        }}
      >
        <div>
          <div style={{ fontSize: '13px', fontWeight: 700, color: '#06B6D4' }}>
            🌀 Portal — Instant Teleport
          </div>
          <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
            {state.selectedPortalSourceSquare
              ? 'Tap any cyan-highlighted empty square to teleport.'
              : 'Tap any of your non-King pieces to engage the portal.'}
          </div>
        </div>
        {onSkipPortal && (
          <button
            type="button"
            onClick={onSkipPortal}
            className="pill-btn"
            style={{
              minHeight: '36px',
              background: 'var(--surface-subtle)',
              border: '1px solid var(--hairline)',
              fontSize: '12px',
              flexShrink: 0,
            }}
          >
            Skip
          </button>
        )}
      </div>
    );
  }

  if (state.phase === 'TRAPPING') {
    return (
      <div
        data-testid="trap_action_card"
        style={{
          padding: '10px 12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid #F59E0B',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '8px',
        }}
      >
        <div>
          <div style={{ fontSize: '13px', fontWeight: 700, color: '#F59E0B' }}>
            💣 Trap — Arm Hidden Hazard
          </div>
          <div style={{ fontSize: '11px', color: 'var(--ink-muted)' }}>
            Tap any empty square to place a trap. The first piece to step on it will detonate.
          </div>
        </div>
        {onSkipTrap && (
          <button
            type="button"
            onClick={onSkipTrap}
            className="pill-btn"
            style={{
              minHeight: '36px',
              background: 'var(--surface-subtle)',
              border: '1px solid var(--hairline)',
              fontSize: '12px',
              flexShrink: 0,
            }}
          >
            Skip
          </button>
        )}
      </div>
    );
  }

  if (state.phase === 'DRAW_TWO_CHOOSING' && state.drawTwoPendingCards && onChooseDrawTwo) {
    const [c0, c1] = state.drawTwoPendingCards;
    return (
      <div
        data-testid="draw_two_action_card"
        style={{
          padding: '12px',
          borderRadius: '12px',
          background: 'var(--surface)',
          border: '1.5px solid var(--accent-primary)',
          display: 'flex',
          flexDirection: 'column',
          gap: '10px',
        }}
      >
        <div style={{ fontSize: '13px', fontWeight: 700, color: 'var(--ink-primary)' }}>
          +2 Draw Two — Choose 1 Card to Play
        </div>
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '8px' }}>
          {[c0, c1].map((card, idx) => (
            <button
              key={card.id}
              type="button"
              onClick={() => onChooseDrawTwo(idx)}
              className="pill-btn"
              style={{
                padding: '10px 8px',
                borderRadius: '10px',
                background: 'var(--surface-subtle)',
                border: '1.5px solid var(--hairline)',
                display: 'flex',
                flexDirection: 'column',
                alignItems: 'center',
                gap: '4px',
                cursor: 'pointer',
              }}
            >
              <span style={{ fontSize: '18px' }}>{cardBadgeLabel(card.type)}</span>
              <span style={{ fontSize: '12px', fontWeight: 700, color: 'var(--ink-primary)' }}>
                {cardTitle(card.type)}
              </span>
              <span style={{ fontSize: '10px', color: 'var(--ink-muted)', textAlign: 'center' }}>
                {cardSubtitle(card.type)}
              </span>
            </button>
          ))}
        </div>
      </div>
    );
  }

  return null;
};

export const GameLogCard: React.FC<{ entries: LogEntry[] }> = ({ entries }) => {
  const dotColor = (cat: LogEntry['category']): string => {
    switch (cat) {
      case 'CHECK':
      case 'SUICIDE':
        return 'var(--accent-alert)';
      case 'REVIVE':
      case 'PROMOTE':
        return 'var(--accent-success)';
      case 'FREEZE':
        return 'var(--accent-freeze)';
      case 'SHIELD':
      case 'HIJACK':
        return 'var(--accent-shield)';
      case 'SWAP':
        return 'var(--accent-swap)';
      case 'CLONE':
      case 'MERGE':
        return 'var(--accent-merge)';
      case 'SACRIFICE':
        return 'var(--accent-sacrifice)';
      case 'WILD':
        return 'var(--accent-wild)';
      case 'SNIPER':
        return '#EF4444';
      case 'PORTAL':
        return '#06B6D4';
      case 'TRAP':
        return '#F59E0B';
      case 'DRAW_TWO':
        return '#3B82F6';
      default:
        return 'var(--ink-muted)';
    }
  };

  return (
    <div
      style={{
        flex: 1,
        minHeight: '120px',
        padding: '10px 12px',
        borderRadius: '12px',
        background: 'var(--surface)',
        border: '1px solid var(--hairline)',
        display: 'flex',
        flexDirection: 'column',
        gap: '6px',
        overflow: 'hidden',
      }}
    >
      <span
        style={{
          fontSize: '11px',
          fontWeight: 800,
          textTransform: 'uppercase',
          letterSpacing: '0.06em',
          color: 'var(--ink-muted)',
        }}
      >
        Move Log
      </span>
      <div style={{ flex: 1, overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '5px' }}>
        {entries.map((entry) => (
          <div key={entry.id} style={{ display: 'flex', alignItems: 'flex-start', gap: '7px' }}>
            <span
              style={{
                width: '6px',
                height: '6px',
                borderRadius: '999px',
                background: dotColor(entry.category),
                marginTop: '6px',
                flexShrink: 0,
              }}
            />
            <span style={{ fontSize: '12px', lineHeight: 1.35, color: 'var(--ink-secondary)' }}>
              {entry.message}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
};
