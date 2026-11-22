import React, { useEffect, useMemo, useState } from 'react';
import {
  ChessPiece,
  GameUiState,
  getActiveColor,
  isPieceFrozen,
  isPieceShielded,
  MoveAnimationEvent,
  opponentColor,
  PIECE_META,
  sameSquare,
  Square,
  squareKey,
  squareToAlgebraic,
} from '../model/gameModels';
import { ChessPieceGraphic } from './ChessPieceGraphic';

interface ChessBoardViewProps {
  state: GameUiState;
  flipped: boolean;
  onSquareTapped: (sq: Square) => void;
  edgeToEdge?: boolean;
}

export const ChessBoardView: React.FC<ChessBoardViewProps> = ({
  state,
  flipped,
  onSquareTapped,
  edgeToEdge = false,
}) => {
  const [activeMoveAnim, setActiveMoveAnim] = useState<MoveAnimationEvent | null>(null);
  const [moveAnimStarted, setMoveAnimStarted] = useState(false);
  const [activeReviveAnimId, setActiveReviveAnimId] = useState<number | null>(null);

  // Smooth 200ms glide animation on move / castle / swap
  useEffect(() => {
    const ev = state.moveAnimationEvent;
    if (!ev) return;
    setActiveMoveAnim(ev);
    setMoveAnimStarted(false);

    const raf = window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => setMoveAnimStarted(true));
    });
    const timer = window.setTimeout(() => {
      setActiveMoveAnim((cur) => (cur?.id === ev.id ? null : cur));
    }, 220);

    return () => {
      window.cancelAnimationFrame(raf);
      window.clearTimeout(timer);
    };
  }, [state.moveAnimationEvent?.id]);

  // Spring pop-in on revive / merge / sacrifice / promote / clone
  useEffect(() => {
    const ev = state.reviveAnimationEvent;
    if (!ev) return;
    setActiveReviveAnimId(ev.id);
    const timer = window.setTimeout(() => {
      setActiveReviveAnimId((cur) => (cur === ev.id ? null : cur));
    }, 360);
    return () => window.clearTimeout(timer);
  }, [state.reviveAnimationEvent?.id]);

  const legalDestinations = useMemo(
    () => new Set(state.legalMovesForSelected.map((m) => squareKey(m.to))),
    [state.legalMovesForSelected]
  );
  const validReviveSet = useMemo(
    () => new Set(state.validReviveSquares.map(squareKey)),
    [state.validReviveSquares]
  );
  const validFreezeSet = useMemo(
    () => new Set(state.validFreezeSquares.map(squareKey)),
    [state.validFreezeSquares]
  );
  const validShieldSet = useMemo(
    () => new Set(state.validShieldSquares.map(squareKey)),
    [state.validShieldSquares]
  );
  const validSwapSet = useMemo(
    () => new Set(state.validSwapSquares.map(squareKey)),
    [state.validSwapSquares]
  );
  const validCloneSet = useMemo(
    () => new Set(state.validCloneSquares.map(squareKey)),
    [state.validCloneSquares]
  );
  const selectedMergeSet = useMemo(
    () => new Set(state.selectedMergeSquares.map(squareKey)),
    [state.selectedMergeSquares]
  );
  const validSacrificeSet = useMemo(
    () => new Set(state.validSacrificeSquares.map(squareKey)),
    [state.validSacrificeSquares]
  );
  const validPromoteSet = useMemo(
    () => new Set(state.validPromotePawnSquares.map(squareKey)),
    [state.validPromotePawnSquares]
  );
  const validSuicideSet = useMemo(
    () => new Set(state.validSuicideSquares.map(squareKey)),
    [state.validSuicideSquares]
  );

  const activeColor = getActiveColor(state);
  const enemyColor = opponentColor(activeColor);

  const toVisualPos = (sq: Square) => ({
    row: flipped ? 7 - sq.row : sq.row,
    col: flipped ? 7 - sq.col : sq.col,
  });

  const renderGlidingPiece = (piece: ChessPiece, from: Square, to: Square, key: string) => {
    const start = toVisualPos(from);
    const end = toVisualPos(to);
    const cur = moveAnimStarted ? end : start;
    return (
      <div
        key={key}
        style={{
          position: 'absolute',
          left: 0,
          top: 0,
          width: '12.5%',
          height: '12.5%',
          transform: `translate(${cur.col * 100}%, ${cur.row * 100}%)`,
          transition: moveAnimStarted ? 'transform 200ms cubic-bezier(0.22, 1, 0.36, 1)' : 'none',
          pointerEvents: 'none',
          zIndex: 20,
          padding: '4px',
        }}
      >
        <ChessPieceGraphic type={piece.type} color={piece.color} />
      </div>
    );
  };

  return (
    <div
      data-testid="chess_board"
      style={{
        position: 'relative',
        width: '100%',
        aspectRatio: '1 / 1',
        borderRadius: edgeToEdge ? '0px' : '12px',
        overflow: 'hidden',
        border: edgeToEdge ? 'none' : '1.5px solid var(--board-border)',
        borderTop: edgeToEdge ? '1px solid var(--board-border)' : undefined,
        borderBottom: edgeToEdge ? '1px solid var(--board-border)' : undefined,
        boxShadow: edgeToEdge ? 'none' : 'var(--shadow-sm)',
        background: 'var(--board-dark)',
        touchAction: 'manipulation',
      }}
    >
      {/* 8x8 Grid */}
      <div
        style={{
          display: 'grid',
          gridTemplateColumns: 'repeat(8, 1fr)',
          gridTemplateRows: 'repeat(8, 1fr)',
          width: '100%',
          height: '100%',
        }}
      >
        {Array.from({ length: 8 }).map((_, visualRow) =>
          Array.from({ length: 8 }).map((__, visualCol) => {
            const row = flipped ? 7 - visualRow : visualRow;
            const col = flipped ? 7 - visualCol : visualCol;
            const sq: Square = { row, col };
            const key = squareKey(sq);
            const piece = state.board[row][col];
            const isLight = (row + col) % 2 === 0;

            const isSelected = sameSquare(state.selectedSquare, sq);
            const isLastMove = sameSquare(state.lastMoveFrom, sq) || sameSquare(state.lastMoveTo, sq);
            const isCheckedKing = sameSquare(state.checkedKingSquare, sq);
            const isLegalDest = legalDestinations.has(key);
            const isReviveTarget = state.phase === 'REVIVING' && validReviveSet.has(key);
            const isFreezeTarget = state.phase === 'FREEZING' && validFreezeSet.has(key);
            const isShieldTarget = state.phase === 'SHIELDING' && validShieldSet.has(key);
            const isSwapSelected = state.phase === 'SWAPPING' && sameSquare(state.selectedSwapSquare, sq);
            const isSwapTarget = state.phase === 'SWAPPING' && validSwapSet.has(key);
            const isCloneSelected =
              state.phase === 'CLONING' && sameSquare(state.selectedCloneSourceSquare, sq);
            const isCloneTarget = state.phase === 'CLONING' && validCloneSet.has(key);
            const isMergeSelected = state.phase === 'MERGING' && selectedMergeSet.has(key);
            const isMergeAnchor =
              state.phase === 'MERGING' &&
              state.selectedMergeSquares.length > 0 &&
              sameSquare(state.selectedMergeSquares[0], sq);
            const isSacSelected =
              state.phase === 'SACRIFICING' && sameSquare(state.selectedSacrificeSquare, sq);
            const isSacCandidate = state.phase === 'SACRIFICING' && validSacrificeSet.has(key);
            const isPromoteSelected =
              state.phase === 'PROMOTING_PAWN' && sameSquare(state.selectedPromotePawnSquare, sq);
            const isPromoteCandidate =
              state.phase === 'PROMOTING_PAWN' && validPromoteSet.has(key);
            const isSuicideTarget = state.phase === 'SUICIDING' && validSuicideSet.has(key);
            const isHijackCandidate =
              state.phase === 'HIJACKING' &&
              piece !== null &&
              piece.color === enemyColor &&
              !isPieceFrozen(state, piece);

            const isSniperSource =
              state.phase === 'SNIPING' && state.validSniperSources.some((s) => sameSquare(s, sq));
            const isSniperSelected =
              state.phase === 'SNIPING' && sameSquare(state.selectedSniperSquare, sq);
            const isSniperTarget =
              state.phase === 'SNIPING' && state.validSniperTargets.some((s) => sameSquare(s, sq));

            const isPortalSource =
              state.phase === 'PORTALING' && state.validPortalSources.some((s) => sameSquare(s, sq));
            const isPortalSelected =
              state.phase === 'PORTALING' && sameSquare(state.selectedPortalSourceSquare, sq);
            const isPortalDest =
              state.phase === 'PORTALING' && state.validPortalDestinations.some((s) => sameSquare(s, sq));

            const hasActiveTrap = state.traps.some((t) => sameSquare({ row: t.row, col: t.col }, sq));
            const isTrapTarget =
              state.phase === 'TRAPPING' && state.validTrapSquares.some((s) => sameSquare(s, sq));

            const pieceFrozen = piece !== null && isPieceFrozen(state, piece);
            const pieceShielded = piece !== null && isPieceShielded(state, piece);
            const pieceMoved =
              piece !== null &&
              state.phase === 'MOVING' &&
              piece.color === activeColor &&
              state.movedPieceIdsThisTurn.includes(piece.id);

            const isHiddenByMoveAnim =
              activeMoveAnim !== null &&
              (sameSquare(activeMoveAnim.to, sq) ||
                (activeMoveAnim.secondaryTo != null && sameSquare(activeMoveAnim.secondaryTo, sq)));

            const isRevivingPop =
              activeReviveAnimId !== null &&
              state.reviveAnimationEvent?.id === activeReviveAnimId &&
              sameSquare(state.reviveAnimationEvent.square, sq);

            let bg = isLight ? 'var(--board-light)' : 'var(--board-dark)';
            if (isLastMove) {
              bg = isLight ? 'var(--board-last-move-light)' : 'var(--board-last-move-dark)';
            }
            if (isSelected) {
              bg = 'var(--board-selected)';
            }

            // Special ring color & corner badge
            let ringColor: string | null = null;
            let ringFill: string | null = null;
            let badge: { text: string; bg: string } | null = null;

            if (isCheckedKing) {
              ringColor = 'var(--board-check)';
              ringFill = 'rgba(229, 57, 53, 0.32)';
            } else if (isSuicideTarget) {
              ringColor = 'var(--board-suicide)';
              ringFill = 'rgba(220, 38, 38, 0.22)';
              badge = { text: '✕', bg: 'var(--board-suicide)' };
            } else if (isReviveTarget) {
              ringColor = 'var(--board-revive)';
              ringFill = 'rgba(67, 160, 71, 0.24)';
            } else if (isFreezeTarget) {
              ringColor = 'var(--board-freeze)';
              ringFill = 'rgba(2, 136, 209, 0.22)';
            } else if (isShieldTarget) {
              ringColor = 'var(--board-shield)';
              ringFill = 'rgba(217, 119, 6, 0.22)';
            } else if (isSwapSelected || isSwapTarget) {
              ringColor = 'var(--board-swap)';
              ringFill = isSwapSelected ? 'rgba(13, 148, 136, 0.34)' : 'rgba(13, 148, 136, 0.18)';
              if (isSwapSelected) badge = { text: '⇄', bg: 'var(--board-swap)' };
            } else if (isCloneSelected || isCloneTarget) {
              ringColor = 'var(--board-clone)';
              ringFill = isCloneSelected
                ? 'rgba(124, 58, 237, 0.34)'
                : 'rgba(124, 58, 237, 0.18)';
              if (isCloneSelected) badge = { text: '⧉', bg: 'var(--board-clone)' };
            } else if (isMergeSelected) {
              ringColor = 'var(--board-merge)';
              ringFill = isMergeAnchor ? 'rgba(124, 58, 237, 0.34)' : 'rgba(124, 58, 237, 0.2)';
              badge = { text: isMergeAnchor ? '⊕' : '✓', bg: 'var(--board-merge)' };
            } else if (isSacSelected || isSacCandidate) {
              ringColor = 'var(--board-sacrifice)';
              ringFill = isSacSelected ? 'rgba(234, 88, 12, 0.3)' : 'rgba(234, 88, 12, 0.14)';
              if (isSacSelected) badge = { text: '❖', bg: 'var(--board-sacrifice)' };
            } else if (isPromoteSelected || isPromoteCandidate) {
              ringColor = 'var(--board-promote)';
              ringFill = isPromoteSelected
                ? 'rgba(22, 163, 74, 0.3)'
                : 'rgba(22, 163, 74, 0.14)';
              if (isPromoteSelected) badge = { text: '↑', bg: 'var(--board-promote)' };
            } else if (isHijackCandidate) {
              ringColor = 'var(--board-hijack)';
              ringFill = 'rgba(217, 119, 6, 0.14)';
            } else if (isSniperSelected || isSniperTarget || isSniperSource) {
              if (isSniperTarget) {
                ringColor = '#EF4444';
                ringFill = 'rgba(239, 68, 68, 0.35)';
                badge = { text: '🎯', bg: '#EF4444' };
              } else if (isSniperSelected) {
                ringColor = '#10B981';
                ringFill = 'rgba(16, 185, 129, 0.35)';
                badge = { text: '🎯', bg: '#10B981' };
              } else {
                ringColor = '#10B981';
                ringFill = 'rgba(16, 185, 129, 0.18)';
              }
            } else if (isPortalSelected || isPortalDest || isPortalSource) {
              if (isPortalDest) {
                ringColor = '#06B6D4';
                ringFill = 'rgba(6, 182, 212, 0.3)';
                badge = { text: '🌀', bg: '#06B6D4' };
              } else if (isPortalSelected) {
                ringColor = '#06B6D4';
                ringFill = 'rgba(6, 182, 212, 0.35)';
                badge = { text: '🌀', bg: '#06B6D4' };
              } else {
                ringColor = '#06B6D4';
                ringFill = 'rgba(6, 182, 212, 0.18)';
              }
            } else if (isTrapTarget) {
              ringColor = '#F59E0B';
              ringFill = 'rgba(245, 158, 11, 0.22)';
              badge = { text: '💣', bg: '#F59E0B' };
            }

            // Status badges (Frozen / Shielded / Armed Trap) persist during normal phases
            if (!badge && pieceFrozen) {
              badge = { text: '❄', bg: 'var(--board-freeze)' };
              if (!ringFill) ringFill = 'rgba(2, 136, 209, 0.18)';
            } else if (!badge && pieceShielded) {
              badge = { text: '🛡', bg: 'var(--board-shield)' };
              if (!ringFill) ringFill = 'rgba(217, 119, 6, 0.16)';
            } else if (!badge && hasActiveTrap) {
              badge = { text: '💣', bg: '#DC2626' };
              if (!ringFill) ringFill = 'rgba(220, 38, 38, 0.18)';
            }

            const coordColor = isLight ? 'var(--board-dark)' : 'var(--board-light)';
            const rankLabel = visualCol === 0 ? `${8 - row}` : null;
            const fileLabel = visualRow === 7 ? String.fromCharCode('a'.charCodeAt(0) + col) : null;

            const ariaLabel = piece
              ? `${squareToAlgebraic(sq)} ${piece.color.toLowerCase()} ${PIECE_META[piece.type].displayName.toLowerCase()}`
              : squareToAlgebraic(sq);

            return (
              <button
                key={key}
                type="button"
                aria-label={ariaLabel}
                data-testid={`square_${squareToAlgebraic(sq)}`}
                onClick={() => onSquareTapped(sq)}
                style={{
                  position: 'relative',
                  width: '100%',
                  height: '100%',
                  background: bg,
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  padding: '4px',
                  outline: 'none',
                }}
              >
                {/* Special tint / border highlight */}
                {(ringColor || ringFill) && (
                  <div
                    style={{
                      position: 'absolute',
                      inset: 0,
                      background: ringFill ?? 'transparent',
                      boxShadow: ringColor ? `inset 0 0 0 2.5px ${ringColor}` : undefined,
                      pointerEvents: 'none',
                    }}
                  />
                )}

                {/* Algebraic coordinates */}
                {rankLabel && (
                  <span
                    className="font-mono"
                    style={{
                      position: 'absolute',
                      top: '2px',
                      left: '3px',
                      fontSize: '9px',
                      fontWeight: 700,
                      lineHeight: 1,
                      color: coordColor,
                      opacity: 0.85,
                      pointerEvents: 'none',
                    }}
                  >
                    {rankLabel}
                  </span>
                )}
                {fileLabel && (
                  <span
                    className="font-mono"
                    style={{
                      position: 'absolute',
                      bottom: '2px',
                      right: '3px',
                      fontSize: '9px',
                      fontWeight: 700,
                      lineHeight: 1,
                      color: coordColor,
                      opacity: 0.85,
                      pointerEvents: 'none',
                    }}
                  >
                    {fileLabel}
                  </span>
                )}

                {/* Piece */}
                {piece && !isHiddenByMoveAnim && (
                  <div
                    className={isRevivingPop ? 'anim-revive-pop' : undefined}
                    style={{
                      width: '88%',
                      height: '88%',
                      opacity: pieceMoved ? 0.52 : 1,
                      transition: 'opacity 140ms ease',
                      pointerEvents: 'none',
                    }}
                  >
                    <ChessPieceGraphic type={piece.type} color={piece.color} />
                  </div>
                )}

                {/* Legal Move Dot (empty square) */}
                {isLegalDest && !piece && (
                  <div
                    style={{
                      width: '30%',
                      height: '30%',
                      borderRadius: '999px',
                      background: 'var(--board-legal-dot)',
                      pointerEvents: 'none',
                    }}
                  />
                )}

                {/* Legal Capture Ring (enemy piece) */}
                {isLegalDest && piece && (
                  <div
                    style={{
                      position: 'absolute',
                      inset: '5%',
                      borderRadius: '999px',
                      border: '3.5px solid var(--board-capture-ring)',
                      pointerEvents: 'none',
                    }}
                  />
                )}

                {/* Status / Special Badge */}
                {badge && (
                  <span
                    style={{
                      position: 'absolute',
                      top: '2px',
                      right: '2px',
                      minWidth: '15px',
                      height: '15px',
                      padding: '0 3px',
                      borderRadius: '999px',
                      background: badge.bg,
                      color: '#FFFFFF',
                      fontSize: '9px',
                      fontWeight: 800,
                      display: 'inline-flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      lineHeight: 1,
                      boxShadow: '0 1px 2px rgba(0,0,0,0.32)',
                      pointerEvents: 'none',
                    }}
                  >
                    {badge.text}
                  </span>
                )}
              </button>
            );
          })
        )}
      </div>

      {/* Gliding Piece Overlay */}
      {activeMoveAnim && (
        <>
          {renderGlidingPiece(
            activeMoveAnim.piece,
            activeMoveAnim.from,
            activeMoveAnim.to,
            `primary-${activeMoveAnim.id}`
          )}
          {activeMoveAnim.secondaryPiece &&
            activeMoveAnim.secondaryFrom &&
            activeMoveAnim.secondaryTo &&
            renderGlidingPiece(
              activeMoveAnim.secondaryPiece,
              activeMoveAnim.secondaryFrom,
              activeMoveAnim.secondaryTo,
              `secondary-${activeMoveAnim.id}`
            )}
        </>
      )}
    </div>
  );
};
