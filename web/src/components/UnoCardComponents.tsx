import React from 'react';
import {
  cardBadgeLabel,
  cardSubtitle,
  cardTitle,
  GameUiState,
  getActivePlayerPity,
  UnoCard,
  UnoCardTheme,
  UnoCardType,
  UnoColor,
} from '../model/gameModels';

function getCardBaseColor(card: UnoCard): string {
  if (card.type.kind === 'WILD') return 'var(--uno-wild)';
  if (card.type.kind === 'FREEZE') return 'var(--uno-blue)';
  if (card.type.kind === 'SUICIDE') return 'var(--uno-red)';
  switch (card.color) {
    case 'RED':
      return 'var(--uno-red)';
    case 'BLUE':
      return 'var(--uno-blue)';
    case 'GREEN':
      return 'var(--uno-green)';
    case 'YELLOW':
      return 'var(--uno-yellow)';
  }
}

function getThemePalette(card: UnoCard, theme: UnoCardTheme): {
  bg1: string;
  bg2: string;
  border: string;
  accent: string;
  sublabel?: string;
} {
  if (theme === 'CLASSIC') {
    const base = getCardBaseColor(card);
    return { bg1: base, bg2: base, border: '#FFFFFF', accent: '#FFFFFF' };
  }

  if (theme === 'SPACE') {
    if (card.type.kind === 'WILD') {
      return { bg1: '#1A1038', bg2: '#3B0764', border: '#F472B6', accent: '#FDE047', sublabel: 'NEBULA' };
    }
    switch (card.color) {
      case 'RED':
        return { bg1: '#3A0A1A', bg2: '#7F1D1D', border: '#FB7185', accent: '#FDA4AF', sublabel: 'MARS' };
      case 'BLUE':
        return { bg1: '#071B3B', bg2: '#1E3A8A', border: '#60A5FA', accent: '#93C5FD', sublabel: 'NEPTUNE' };
      case 'GREEN':
        return { bg1: '#06281E', bg2: '#065F46', border: '#34D399', accent: '#6EE7B7', sublabel: 'AURORA' };
      case 'YELLOW':
        return { bg1: '#2B1B05', bg2: '#78350F', border: '#FBBF24', accent: '#FDE68A', sublabel: 'SOLAR' };
    }
  }

  if (theme === 'FANTASY') {
    if (card.type.kind === 'WILD') {
      return { bg1: '#2E1065', bg2: '#581C87', border: '#EAB308', accent: '#FDE047', sublabel: 'ARCANA' };
    }
    switch (card.color) {
      case 'RED':
        return { bg1: '#450A0A', bg2: '#7F1D1D', border: '#F59E0B', accent: '#FCD34D', sublabel: 'DRAGON' };
      case 'BLUE':
        return { bg1: '#0F172A', bg2: '#1E3A8A', border: '#D4AF37', accent: '#BAE6FD', sublabel: 'RUNE' };
      case 'GREEN':
        return { bg1: '#052E16', bg2: '#14532D', border: '#D4AF37', accent: '#BBF7D0', sublabel: 'SYLVAN' };
      case 'YELLOW':
        return { bg1: '#422006', bg2: '#854D0E', border: '#FEF08A', accent: '#FEF9C3', sublabel: 'CROWN' };
    }
  }

  // SPIRITS (Top Shelf Spirits)
  if (card.type.kind === 'WILD') {
    return { bg1: '#18181B', bg2: '#27272A', border: '#D4AF37', accent: '#FDE68A', sublabel: 'RESERVE' };
  }
  switch (card.color) {
    case 'RED':
      return { bg1: '#4A180B', bg2: '#7C2D12', border: '#E7B96D', accent: '#FDE68A', sublabel: 'BOURBON' };
    case 'BLUE':
      return { bg1: '#0C2D48', bg2: '#164E63', border: '#CBD5E1', accent: '#E2E8F0', sublabel: 'DRY GIN' };
    case 'GREEN':
      return { bg1: '#0E331A', bg2: '#1B4D2E', border: '#D4AF37', accent: '#D9F99D', sublabel: 'ABSINTHE' };
    case 'YELLOW':
      return { bg1: '#4C2A09', bg2: '#854D0E', border: '#F3D08B', accent: '#FEF08A', sublabel: 'COGNAC' };
  }
}

interface UnoCardFaceProps {
  card: UnoCard;
  theme: UnoCardTheme;
  width: number;
  height: number;
  animateKey?: number;
}

export const UnoCardFace: React.FC<UnoCardFaceProps> = ({
  card,
  theme,
  width,
  height,
  animateKey,
}) => {
  const palette = getThemePalette(card, theme);
  const badge = cardBadgeLabel(card.type);
  const isNum = card.type.kind === 'NUMBER';

  return (
    <div
      key={animateKey}
      className={animateKey !== undefined ? 'anim-card-draw' : undefined}
      style={{
        position: 'relative',
        width: `${width}px`,
        height: `${height}px`,
        borderRadius: '10px',
        background: `linear-gradient(145deg, ${palette.bg1}, ${palette.bg2})`,
        border: `2px solid ${palette.border}`,
        boxShadow: 'var(--shadow-sm)',
        overflow: 'hidden',
        flexShrink: 0,
        color: '#FFFFFF',
      }}
    >
      {/* Theme-specific decorative SVG background */}
      <svg
        viewBox="0 0 100 140"
        style={{
          position: 'absolute',
          inset: 0,
          width: '100%',
          height: '100%',
          pointerEvents: 'none',
        }}
        aria-hidden="true"
      >
        {theme === 'CLASSIC' && (
          <>
            <ellipse
              cx="50"
              cy="70"
              rx="34"
              ry="50"
              transform="rotate(-22 50 70)"
              fill="rgba(255,255,255,0.18)"
              stroke="rgba(255,255,255,0.45)"
              strokeWidth="2"
            />
            {card.type.kind === 'WILD' && (
              <>
                <circle cx="32" cy="48" r="10" fill="#C62828" opacity="0.85" />
                <circle cx="68" cy="48" r="10" fill="#1565C0" opacity="0.85" />
                <circle cx="32" cy="92" r="10" fill="#2E7D32" opacity="0.85" />
                <circle cx="68" cy="92" r="10" fill="#C88900" opacity="0.85" />
              </>
            )}
          </>
        )}

        {theme === 'SPACE' && (
          <>
            <circle cx="18" cy="22" r="1.6" fill="#FFFFFF" opacity="0.85" />
            <circle cx="82" cy="30" r="1.2" fill="#FFFFFF" opacity="0.7" />
            <circle cx="24" cy="112" r="1.5" fill="#FFFFFF" opacity="0.8" />
            <circle cx="76" cy="118" r="1.8" fill={palette.accent} opacity="0.9" />
            <circle cx="84" cy="86" r="1.1" fill="#FFFFFF" opacity="0.65" />
            <ellipse
              cx="50"
              cy="70"
              rx="42"
              ry="16"
              transform="rotate(-24 50 70)"
              fill="none"
              stroke={palette.border}
              strokeOpacity="0.55"
              strokeWidth="2.5"
            />
            <circle
              cx="50"
              cy="70"
              r="24"
              fill="rgba(255,255,255,0.12)"
              stroke={palette.accent}
              strokeOpacity="0.4"
              strokeWidth="1.5"
            />
          </>
        )}

        {theme === 'FANTASY' && (
          <>
            <rect
              x="7"
              y="7"
              width="86"
              height="126"
              rx="6"
              fill="none"
              stroke={palette.border}
              strokeOpacity="0.65"
              strokeWidth="1.6"
            />
            <polygon
              points="50,26 78,70 50,114 22,70"
              fill="rgba(255,255,255,0.1)"
              stroke={palette.border}
              strokeOpacity="0.6"
              strokeWidth="1.6"
            />
            <circle cx="50" cy="70" r="22" fill="none" stroke={palette.accent} strokeOpacity="0.4" strokeDasharray="4 3" />
          </>
        )}

        {theme === 'SPIRITS' && (
          <>
            <rect
              x="8"
              y="10"
              width="84"
              height="120"
              rx="4"
              fill="none"
              stroke={palette.border}
              strokeOpacity="0.7"
              strokeWidth="1.4"
            />
            <rect
              x="12"
              y="14"
              width="76"
              height="112"
              rx="2"
              fill="rgba(255,255,255,0.06)"
              stroke={palette.border}
              strokeOpacity="0.35"
              strokeWidth="1"
            />
            <line x1="20" y1="30" x2="80" y2="30" stroke={palette.border} strokeOpacity="0.5" strokeWidth="1" />
            <line x1="20" y1="110" x2="80" y2="110" stroke={palette.border} strokeOpacity="0.5" strokeWidth="1" />
          </>
        )}
      </svg>

      {/* Corner Top-Left Pip */}
      <span
        className="font-display"
        style={{
          position: 'absolute',
          top: '4px',
          left: '6px',
          fontSize: width < 58 ? '9px' : '11px',
          fontWeight: 800,
          lineHeight: 1,
          color: '#FFFFFF',
          textShadow: '0 1px 2px rgba(0,0,0,0.45)',
        }}
      >
        {badge}
      </span>

      {/* Center Emblem */}
      <div
        style={{
          position: 'absolute',
          inset: 0,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          gap: '2px',
          pointerEvents: 'none',
        }}
      >
        <span
          className="font-display"
          style={{
            fontSize: isNum ? (width < 58 ? '22px' : '28px') : width < 58 ? '19px' : '24px',
            fontWeight: 800,
            lineHeight: 1,
            color: '#FFFFFF',
            textShadow: '0 2px 4px rgba(0,0,0,0.45)',
          }}
        >
          {badge}
        </span>
        {palette.sublabel && width >= 54 && (
          <span
            className="font-mono"
            style={{
              fontSize: '7px',
              fontWeight: 700,
              letterSpacing: '0.08em',
              color: palette.accent,
              opacity: 0.92,
            }}
          >
            {palette.sublabel}
          </span>
        )}
      </div>

      {/* Corner Bottom-Right Pip */}
      <span
        className="font-display"
        style={{
          position: 'absolute',
          bottom: '4px',
          right: '6px',
          fontSize: width < 58 ? '9px' : '11px',
          fontWeight: 800,
          lineHeight: 1,
          color: '#FFFFFF',
          opacity: 0.9,
          textShadow: '0 1px 2px rgba(0,0,0,0.45)',
        }}
      >
        {badge}
      </span>
    </div>
  );
};

interface UnoDeckBackProps {
  theme: UnoCardTheme;
  deckCount: number;
  pityCount: number;
  canDraw: boolean;
  onClick: () => void;
  width: number;
  height: number;
}

const UnoDeckBack: React.FC<UnoDeckBackProps> = ({
  theme,
  deckCount,
  pityCount,
  canDraw,
  onClick,
  width,
  height,
}) => {
  const bg =
    theme === 'SPACE'
      ? 'linear-gradient(145deg, #090D24, #1E1B4B)'
      : theme === 'FANTASY'
        ? 'linear-gradient(145deg, #271509, #451A03)'
        : theme === 'SPIRITS'
          ? 'linear-gradient(145deg, #18181B, #27272A)'
          : 'linear-gradient(145deg, #1B1B1F, #2C2C34)';

  const borderCol =
    canDraw
      ? 'var(--accent-primary)'
      : theme === 'FANTASY' || theme === 'SPIRITS'
        ? '#D4AF37'
        : 'var(--hairline)';

  const pityLabel =
    pityCount >= 3 ? '3-4★' : pityCount === 2 ? '40%' : pityCount === 1 ? '+Move' : null;

  return (
    <button
      type="button"
      data-testid="uno_draw_deck"
      aria-label={`Draw UNO card, ${deckCount} cards left`}
      disabled={!canDraw}
      onClick={onClick}
      style={{
        position: 'relative',
        width: `${width}px`,
        height: `${height}px`,
        borderRadius: '10px',
        background: bg,
        border: `2px solid ${borderCol}`,
        boxShadow: canDraw
          ? '0 0 0 2px rgba(242, 207, 91, 0.5), var(--shadow-md)'
          : 'var(--shadow-sm)',
        opacity: canDraw ? 1 : 0.72,
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '2px',
        flexShrink: 0,
        transition: 'transform 0.12s ease, box-shadow 0.2s ease',
      }}
    >
      <div
        style={{
          padding: '2px 5px',
          borderRadius: '6px',
          background: '#C62828',
          color: '#FFFFFF',
          fontSize: '9px',
          fontWeight: 800,
          letterSpacing: '0.06em',
          transform: 'rotate(-8deg)',
        }}
        className="font-display"
      >
        UNO
      </div>
      <span
        className="font-mono"
        style={{
          fontSize: '10px',
          fontWeight: 700,
          color: '#F0EFEA',
          marginTop: '2px',
        }}
      >
        {deckCount}
      </span>
      {canDraw && (
        <span
          style={{
            fontSize: '8px',
            fontWeight: 800,
            letterSpacing: '0.04em',
            color: '#F2CF5B',
            textTransform: 'uppercase',
          }}
        >
          DRAW
        </span>
      )}

      {pityLabel && (
        <span
          className="font-mono"
          style={{
            position: 'absolute',
            top: '-7px',
            right: '-7px',
            padding: '2px 5px',
            borderRadius: '999px',
            background: pityCount >= 3 ? '#16A34A' : '#D97706',
            color: '#FFFFFF',
            fontSize: '9px',
            fontWeight: 800,
            boxShadow: '0 1px 3px rgba(0,0,0,0.35)',
          }}
        >
          {pityLabel}
        </span>
      )}
    </button>
  );
};

interface UnoCardStripProps {
  state: GameUiState;
  canLocalAct: boolean;
  onDrawCard: () => void;
  compact?: boolean;
}

export const UnoCardStrip: React.FC<UnoCardStripProps> = ({
  state,
  canLocalAct,
  onDrawCard,
  compact = false,
}) => {
  const canDraw = canLocalAct && state.phase === 'AWAITING_DRAW' && state.outcome === null;
  const pityCount = getActivePlayerPity(state);
  const cardW = compact ? 56 : 64;
  const cardH = compact ? 78 : 88;

  const statusHeadline = (() => {
    if (state.outcome) return 'Match Finished';
    if (state.isAiThinking) return 'AI is thinking…';
    if (!canLocalAct) return 'Opponent’s Turn';
    switch (state.phase) {
      case 'AWAITING_DRAW':
        return 'Tap deck to draw';
      case 'MOVING':
        return state.movesRemaining === 1
          ? '1 Move Remaining'
          : `${state.movesRemaining} Moves Left`;
      case 'REVIVING':
        return 'Revive Piece';
      case 'FREEZING':
        return 'Freeze Enemy Piece';
      case 'SHIELDING':
        return 'Shield Own Piece';
      case 'MERGING':
        return 'Merge Pieces';
      case 'SACRIFICING':
        return 'Sacrifice Piece';
      case 'SWAPPING':
        return 'Swap 2 Pieces';
      case 'CLONING':
        return 'Clone Piece';
      case 'HIJACKING':
        return 'Hijack Enemy Piece';
      case 'PROMOTING_PAWN':
        return 'Promote Pawn';
      case 'SUICIDING':
        return 'Eliminate Own Piece';
      case 'WILD_CHOOSING':
        return 'Choose Wild Effect';
      default:
        return 'UNO Chess';
    }
  })();

  const statusDetail = (() => {
    if (state.outcome) return 'Start a new game or review the board.';
    if (state.activeCard && state.phase !== 'AWAITING_DRAW') {
      return `${cardTitle(state.activeCard.type)} · ${cardSubtitle(state.activeCard.type)}`;
    }
    if (pityCount >= 3) return 'Pity active: guaranteed Move card (75%+ 3–4 moves).';
    if (pityCount === 2) return 'Pity active: boosted 2–4 Move card odds.';
    if (pityCount === 1) return 'Pity active: higher chance for a Move card.';
    return `Deck: ${state.drawPile.length} cards · Discard: ${state.discardPile.length}`;
  })();

  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '10px',
        padding: compact ? '8px 10px' : '10px 12px',
        borderRadius: '14px',
        background: 'var(--surface)',
        border: '1px solid var(--hairline)',
        boxShadow: 'var(--shadow-sm)',
      }}
    >
      <UnoDeckBack
        theme={state.cardTheme}
        deckCount={state.drawPile.length}
        pityCount={pityCount}
        canDraw={canDraw}
        onClick={onDrawCard}
        width={cardW}
        height={cardH}
      />

      {state.activeCard ? (
        <UnoCardFace
          card={state.activeCard}
          theme={state.cardTheme}
          width={cardW}
          height={cardH}
          animateKey={state.drawRevision}
        />
      ) : (
        <div
          style={{
            width: `${cardW}px`,
            height: `${cardH}px`,
            borderRadius: '10px',
            border: '1.5px dashed var(--hairline)',
            background: 'var(--surface-subtle)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: 'var(--ink-muted)',
            fontSize: '10px',
            fontWeight: 700,
            textAlign: 'center',
            padding: '4px',
            flexShrink: 0,
          }}
        >
          No Card
        </div>
      )}

      {/* Turn Status Summary */}
      <div style={{ flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: '4px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '6px' }}>
          <span
            className="font-display"
            style={{
              fontSize: compact ? '14px' : '15px',
              fontWeight: 700,
              color: 'var(--ink-primary)',
              overflow: 'hidden',
              textOverflow: 'ellipsis',
              whiteSpace: 'nowrap',
            }}
          >
            {statusHeadline}
          </span>
        </div>

        <span
          style={{
            fontSize: '12px',
            color: 'var(--ink-secondary)',
            lineHeight: 1.3,
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
          }}
        >
          {statusDetail}
        </span>

        {/* Move Pip Counter when moving */}
        {state.phase === 'MOVING' && state.totalMovesForCard > 0 && (
          <div style={{ display: 'flex', alignItems: 'center', gap: '5px', marginTop: '2px' }}>
            {Array.from({ length: state.totalMovesForCard }).map((_, idx) => {
              const used = idx < state.totalMovesForCard - state.movesRemaining;
              return (
                <span
                  key={idx}
                  style={{
                    width: '18px',
                    height: '6px',
                    borderRadius: '999px',
                    background: used ? 'var(--hairline)' : 'var(--accent-primary)',
                    transition: 'background-color 0.16s ease',
                  }}
                />
              );
            })}
          </div>
        )}

        {/* Quick Draw CTA button on desktop/waiting */}
        {canDraw && (
          <button
            type="button"
            data-testid="draw_card_cta"
            onClick={onDrawCard}
            className="pill-btn"
            style={{
              marginTop: '2px',
              alignSelf: 'flex-start',
              minHeight: '34px',
              padding: '0 14px',
              background: 'var(--accent-primary)',
              color: 'var(--on-accent-primary)',
              fontSize: '12px',
            }}
          >
            Draw Card
          </button>
        )}
      </div>
    </div>
  );
};

export const UnoCardThemePreviewPair: React.FC<{ theme: UnoCardTheme }> = ({ theme }) => {
  const sampleNum: UnoCard = { id: -1, color: 'RED', type: { kind: 'NUMBER', moves: 3 } };
  const sampleSpecial: UnoCard = { id: -2, color: 'BLUE', type: { kind: 'FREEZE' } as UnoCardType };
  return (
    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', flexShrink: 0 }}>
      <UnoCardFace card={sampleNum} theme={theme} width={44} height={62} />
      <UnoCardFace card={sampleSpecial} theme={theme} width={44} height={62} />
    </div>
  );
};
