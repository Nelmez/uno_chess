import {
  ChessPiece,
  colorDisplayName,
  GameUiState,
  getActiveColor,
  getActorName,
  isPieceFrozen,
  MoveAnimationEvent,
  opponentColor,
  opponentPlayer,
  PIECE_META,
  PieceType,
  ReviveAnimationEvent,
  sameSquare,
  SPECIAL_CARD_META,
  SpecialCardKey,
  Square,
  squareToAlgebraic,
} from '../model/gameModels';
import * as ChessEngine from '../engine/chessEngine';
import {
  computeNextPieceId,
  executeHijackMove,
  handleMovingSquareTap,
  handleRevivingSquareTap,
  nextTurnShield,
  prependLog,
  protectedPieceIdsForOwner,
  TransitionContext,
} from './gameTransitions';

function passTurnCheckStalemate(
  current: GameUiState,
  newBoard: GameUiState['board'],
  updatedCastling: GameUiState['castlingRights'],
  summary: string,
  category: GameUiState['gameLog'][number]['category'],
  removedIds: Set<number>,
  extraPatch: Partial<GameUiState>,
  isAlertNotice: boolean,
  ctx: TransitionContext
): GameUiState {
  const activePlayer = current.activePlayer;
  const activeColor = getActiveColor(current);
  const oppPlayer = opponentPlayer(activePlayer);
  const enemy = opponentColor(activeColor);

  let logs = prependLog(current.gameLog, summary, category, ctx);
  const nextShield = nextTurnShield(
    current.shieldedPieceId,
    current.shieldedOwner,
    activeColor,
    removedIds
  );
  const opponentProtected = protectedPieceIdsForOwner(nextShield.id, nextShield.owner, activeColor);

  const enemyHasMove = ChessEngine.hasAnyLegalMove(
    newBoard,
    enemy,
    updatedCastling,
    null,
    null,
    new Set(),
    opponentProtected
  );

  if (!enemyHasMove) {
    const msg = `Stalemate! ${getActorName(oppPlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer)} (${colorDisplayName(enemy)}) has no legal moves.`;
    logs = prependLog(logs, msg, 'GAME_OVER', ctx);
    ctx.onOutcomeRecorded?.({ kind: 'STALEMATE' });
    return {
      ...current,
      ...extraPatch,
      board: newBoard,
      phase: 'GAME_OVER',
      frozenPieceId: null,
      frozenOwner: null,
      shieldedPieceId: null,
      shieldedOwner: null,
      noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false },
      gameLog: logs,
      outcome: { kind: 'STALEMATE' },
      castlingRights: updatedCastling,
      enPassantTarget: null,
      enPassantOwner: null,
    };
  }

  const killedFrozen = current.frozenPieceId !== null && removedIds.has(current.frozenPieceId);
  const keptFrozenId = killedFrozen ? null : current.frozenPieceId;
  const keptFrozenOwner = killedFrozen ? null : current.frozenOwner;
  const nextTurnFrozenId = keptFrozenOwner === activeColor ? null : keptFrozenId;
  const nextTurnFrozenOwner = keptFrozenOwner === activeColor ? null : keptFrozenOwner;

  return {
    ...current,
    ...extraPatch,
    board: newBoard,
    activePlayer: oppPlayer,
    phase: 'AWAITING_DRAW',
    frozenPieceId: nextTurnFrozenId,
    frozenOwner: nextTurnFrozenOwner,
    shieldedPieceId: nextShield.id,
    shieldedOwner: nextShield.owner,
    noticeBanner: { id: ctx.nextNoticeId(), message: summary, isAlert: isAlertNotice },
    gameLog: logs,
    castlingRights: updatedCastling,
    enPassantTarget: null,
    enPassantOwner: null,
  };
}

export function performSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  if (current.outcome !== null || current.pendingPromotion !== null) return current;

  switch (current.phase) {
    case 'MOVING':
      return handleMovingSquareTap(current, square, ctx);
    case 'REVIVING':
      return handleRevivingSquareTap(current, square, ctx);
    case 'FREEZING':
      return handleFreezingSquareTap(current, square, ctx);
    case 'SHIELDING':
      return handleShieldingSquareTap(current, square, ctx);
    case 'MERGING':
      return handleMergingSquareTap(current, square, ctx);
    case 'SACRIFICING':
      return handleSacrificingSquareTap(current, square, ctx);
    case 'HIJACKING':
      return handleHijackingSquareTap(current, square, ctx);
    case 'PROMOTING_PAWN':
      return handlePromotingPawnSquareTap(current, square, ctx);
    case 'SUICIDING':
      return handleSuicidingSquareTap(current, square, ctx);
    case 'SWAPPING':
      return handleSwappingSquareTap(current, square, ctx);
    case 'CLONING':
      return handleCloningSquareTap(current, square, ctx);
    case 'SNIPING':
      return handleSniperSquareTap(current, square, ctx);
    case 'PORTALING':
      return handlePortalSquareTap(current, square, ctx);
    case 'TRAPPING':
      return handleTrapSquareTap(current, square, ctx);
    case 'WILD_CHOOSING':
      return {
        ...current,
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild Card! Pick a special card effect below.',
          isAlert: false,
        },
      };
    case 'AWAITING_DRAW':
      return {
        ...current,
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: `${getActorName(current.activePlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer)}: Draw a UNO card first!`,
          isAlert: false,
        },
      };
    case 'DRAW_TWO_CHOOSING':
      return {
        ...current,
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Draw Two! Pick one of the two cards below.',
          isAlert: false,
        },
      };
    case 'GAME_OVER':
      return current;
    default:
      return current;
  }
}

function handleFreezingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activeColor = getActiveColor(current);
  const enemy = opponentColor(activeColor);
  if (!current.validFreezeSquares.some((s) => sameSquare(s, square))) {
    const tapped = current.board[square.row][square.col];
    const msg =
      tapped && tapped.color === enemy && tapped.type === 'KING'
        ? "You cannot freeze the opponent's King."
        : 'Tap a highlighted enemy piece to freeze it.';
    return { ...current, noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: true } };
  }
  const target = current.board[square.row][square.col];
  if (!target) return current;
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const logs = prependLog(
    current.gameLog,
    `${activeLabel} froze ${colorDisplayName(enemy)}'s ${PIECE_META[target.type].displayName} on ${squareToAlgebraic(square)}.`,
    'FREEZE',
    ctx
  );
  return {
    ...current,
    phase: 'MOVING',
    totalMovesForCard: 1,
    movesRemaining: 1,
    movedPieceIdsThisTurn: [],
    frozenPieceId: target.id,
    frozenOwner: enemy,
    validFreezeSquares: [],
    selectedSquare: null,
    legalMovesForSelected: [],
    noticeBanner: {
      id: ctx.nextNoticeId(),
      message: `${PIECE_META[target.type].displayName} on ${squareToAlgebraic(square).toUpperCase()} frozen! Now make 1 move.`,
      isAlert: false,
    },
    gameLog: logs,
  };
}

function handleShieldingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activeColor = getActiveColor(current);
  if (!current.validShieldSquares.some((s) => sameSquare(s, square))) {
    const tapped = current.board[square.row][square.col];
    const msg =
      tapped && tapped.color === activeColor && tapped.type === 'KING'
        ? 'You cannot shield your King.'
        : 'Tap one of your gold-outlined pieces to shield it.';
    return { ...current, noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: true } };
  }
  const target = current.board[square.row][square.col];
  if (!target) return current;
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const logs = prependLog(
    current.gameLog,
    `${activeLabel} shielded ${colorDisplayName(activeColor)}'s ${PIECE_META[target.type].displayName} on ${squareToAlgebraic(square)}.`,
    'SHIELD',
    ctx
  );
  return {
    ...current,
    phase: 'MOVING',
    totalMovesForCard: 1,
    movesRemaining: 1,
    movedPieceIdsThisTurn: [],
    shieldedPieceId: target.id,
    shieldedOwner: activeColor,
    validShieldSquares: [],
    selectedSquare: null,
    legalMovesForSelected: [],
    noticeBanner: {
      id: ctx.nextNoticeId(),
      message: `${PIECE_META[target.type].displayName} on ${squareToAlgebraic(square).toUpperCase()} shielded! Now make 1 move.`,
      isAlert: false,
    },
    gameLog: logs,
  };
}

function handleHijackingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activeColor = getActiveColor(current);
  const enemy = opponentColor(activeColor);
  const selected = current.selectedSquare;

  if (selected) {
    const matching = current.legalMovesForSelected.filter((m) => sameSquare(m.to, square));
    if (matching.length > 0) {
      if (matching.some((m) => m.promotion)) {
        return { ...current, pendingPromotion: { from: selected, to: square } };
      }
      return executeHijackMove(current, matching[0], ctx);
    }
  }

  const tapped = current.board[square.row][square.col];
  if (tapped && tapped.color === enemy) {
    if (isPieceFrozen(current, tapped)) {
      return {
        ...current,
        selectedSquare: null,
        legalMovesForSelected: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: `${PIECE_META[tapped.type].displayName} is frozen and cannot be hijacked.`,
          isAlert: true,
        },
      };
    }
    if (sameSquare(selected, square)) {
      return { ...current, selectedSquare: null, legalMovesForSelected: [] };
    }
    const enemyExcluded = new Set<number>(
      current.frozenPieceId !== null && current.frozenOwner === enemy ? [current.frozenPieceId] : []
    );
    const protectedOwn = protectedPieceIdsForOwner(
      current.shieldedPieceId,
      current.shieldedOwner,
      activeColor
    );
    const legal = ChessEngine.getHijackMovesForSquare(
      current.board,
      square,
      activeColor,
      current.castlingRights,
      current.enPassantTarget,
      current.enPassantOwner,
      enemyExcluded,
      protectedOwn
    );
    return {
      ...current,
      selectedSquare: square,
      legalMovesForSelected: legal,
      noticeBanner:
        legal.length === 0
          ? {
              id: ctx.nextNoticeId(),
              message: `That ${PIECE_META[tapped.type].displayName} has no safe hijack moves — pick another.`,
              isAlert: false,
            }
          : null,
    };
  }

  return {
    ...current,
    selectedSquare: null,
    legalMovesForSelected: [],
    noticeBanner:
      tapped && tapped.color === activeColor
        ? {
            id: ctx.nextNoticeId(),
            message: `Hijack active! Tap an opponent (${colorDisplayName(enemy)}) piece to move.`,
            isAlert: false,
          }
        : current.noticeBanner,
  };
}

function handleMergingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activeColor = getActiveColor(current);
  const piece = current.board[square.row][square.col];

  if (current.selectedMergeSquares.some((s) => sameSquare(s, square))) {
    return {
      ...current,
      selectedMergeSquares: current.selectedMergeSquares.filter((s) => !sameSquare(s, square)),
      noticeBanner: null,
    };
  }
  if (!piece || piece.color !== activeColor) {
    return {
      ...current,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: 'Tap your own pieces to select them for Merge.',
        isAlert: false,
      },
    };
  }
  if (piece.type === 'KING') {
    return {
      ...current,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: 'You cannot merge your King.',
        isAlert: true,
      },
    };
  }
  if (isPieceFrozen(current, piece)) {
    return {
      ...current,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: `${PIECE_META[piece.type].displayName} is frozen and cannot be merged this turn.`,
        isAlert: true,
      },
    };
  }
  return {
    ...current,
    selectedMergeSquares: [...current.selectedMergeSquares, square],
    noticeBanner: null,
  };
}

export function performConfirmMerge(
  current: GameUiState,
  targetType: PieceType,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'MERGING' || current.outcome !== null) return current;
  const activeColor = getActiveColor(current);
  const selected = current.selectedMergeSquares;
  const validTargets = ChessEngine.getValidTradeTargets(current.board, activeColor, selected);
  if (!validTargets.includes(targetType)) {
    return {
      ...current,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: `Cannot merge into ${PIECE_META[targetType].displayName} here (value, rank, or check restriction).`,
        isAlert: true,
      },
    };
  }

  const newPiece: ChessPiece = {
    id: computeNextPieceId(current),
    type: targetType,
    color: activeColor,
  };
  const anchor = selected[0];
  const { board: newBoard, castlingRights: updatedCastling } = ChessEngine.applyTrade(
    current.board,
    selected,
    newPiece,
    current.castlingRights
  );
  const popAnim: ReviveAnimationEvent = { id: ctx.nextAnimId(), piece: newPiece, square: anchor };
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const summary =
    selected.length === 1
      ? `${activeLabel} swapped ${PIECE_META[current.board[anchor.row][anchor.col]?.type ?? 'PAWN'].displayName} → ${PIECE_META[targetType].displayName} on ${squareToAlgebraic(anchor)}.`
      : `${activeLabel} merged ${selected.length} pieces → ${PIECE_META[targetType].displayName} on ${squareToAlgebraic(anchor)}.`;
  const removedIds = new Set(
    selected
      .map((sq) => current.board[sq.row][sq.col]?.id)
      .filter((id): id is number => typeof id === 'number')
  );

  return passTurnCheckStalemate(
    current,
    newBoard,
    updatedCastling,
    summary,
    'MERGE',
    removedIds,
    {
      selectedMergeSquares: [],
      lastRevivedSquare: anchor,
      moveAnimationEvent: null,
      reviveAnimationEvent: popAnim,
    },
    false,
    ctx
  );
}

export function performSkipMerge(current: GameUiState, ctx: TransitionContext): GameUiState {
  if (current.phase !== 'MERGING' || current.outcome !== null) return current;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const nextShield = nextTurnShield(current.shieldedPieceId, current.shieldedOwner, activeColor);
  const msg = `${activeLabel} skipped Merge.`;
  return {
    ...current,
    activePlayer: opponentPlayer(current.activePlayer),
    phase: 'AWAITING_DRAW',
    selectedMergeSquares: [],
    frozenPieceId: current.frozenOwner === activeColor ? null : current.frozenPieceId,
    frozenOwner: current.frozenOwner === activeColor ? null : current.frozenOwner,
    shieldedPieceId: nextShield.id,
    shieldedOwner: nextShield.owner,
    noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false },
    gameLog: prependLog(current.gameLog, msg, 'MERGE', ctx),
  };
}

function handleSacrificingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  if (current.validSacrificeSquares.some((s) => sameSquare(s, square))) {
    return { ...current, selectedSacrificeSquare: square, noticeBanner: null };
  }
  const piece = current.board[square.row][square.col];
  const activeColor = getActiveColor(current);
  let msg = 'Tap a highlighted piece to choose what to sacrifice.';
  if (piece && piece.color === activeColor) {
    if (piece.type === 'PAWN') {
      msg = 'Pawns (1 pt) cannot be split — pick a Knight, Bishop, Rook, or Queen.';
    } else if (piece.type === 'KING') {
      msg = 'You cannot sacrifice your King.';
    } else if (isPieceFrozen(current, piece)) {
      msg = `${PIECE_META[piece.type].displayName} is frozen and cannot be sacrificed this turn.`;
    }
  }
  return { ...current, noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false } };
}

export function performConfirmSacrifice(
  current: GameUiState,
  recipeId: string,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'SACRIFICING' || current.outcome !== null) return current;
  const activeColor = getActiveColor(current);
  const sacSquare = current.selectedSacrificeSquare;
  if (!sacSquare) return current;
  const piece = current.board[sacSquare.row][sacSquare.col];
  if (!piece) return current;

  const recipe = ChessEngine.getValidSacrificeRecipes(current.board, activeColor, sacSquare).find(
    (r) => r.id === recipeId
  );
  const placements = recipe
    ? ChessEngine.computeSacrificePlacement(current.board, activeColor, sacSquare, recipe)
    : null;
  if (!recipe || !placements) {
    return {
      ...current,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: `Cannot split ${PIECE_META[piece.type].displayName} that way right now.`,
        isAlert: true,
      },
    };
  }

  const startId = computeNextPieceId(current);
  const { board: newBoard, castlingRights: updatedCastling } = ChessEngine.applySacrifice(
    current.board,
    sacSquare,
    placements,
    activeColor,
    startId,
    current.castlingRights
  );
  const firstSpawn = placements[0];
  const popAnim: ReviveAnimationEvent = {
    id: ctx.nextAnimId(),
    piece: { id: startId, type: firstSpawn.type, color: activeColor },
    square: firstSpawn.square,
  };
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const summary = `${activeLabel} sacrificed ${PIECE_META[piece.type].displayName} on ${squareToAlgebraic(sacSquare)} → ${recipe.label}.`;

  return passTurnCheckStalemate(
    current,
    newBoard,
    updatedCastling,
    summary,
    'SACRIFICE',
    new Set([piece.id]),
    {
      selectedSacrificeSquare: null,
      validSacrificeSquares: [],
      lastRevivedSquare: firstSpawn.square,
      moveAnimationEvent: null,
      reviveAnimationEvent: popAnim,
    },
    false,
    ctx
  );
}

export function performSkipSacrifice(current: GameUiState, ctx: TransitionContext): GameUiState {
  if (current.phase !== 'SACRIFICING' || current.outcome !== null) return current;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const nextShield = nextTurnShield(current.shieldedPieceId, current.shieldedOwner, activeColor);
  const msg = `${activeLabel} skipped Sacrifice.`;
  return {
    ...current,
    activePlayer: opponentPlayer(current.activePlayer),
    phase: 'AWAITING_DRAW',
    selectedSacrificeSquare: null,
    validSacrificeSquares: [],
    frozenPieceId: current.frozenOwner === activeColor ? null : current.frozenPieceId,
    frozenOwner: current.frozenOwner === activeColor ? null : current.frozenOwner,
    shieldedPieceId: nextShield.id,
    shieldedOwner: nextShield.owner,
    noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false },
    gameLog: prependLog(current.gameLog, msg, 'SACRIFICE', ctx),
  };
}

function handlePromotingPawnSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  if (current.validPromotePawnSquares.some((s) => sameSquare(s, square))) {
    return { ...current, selectedPromotePawnSquare: square, noticeBanner: null };
  }
  const piece = current.board[square.row][square.col];
  const activeColor = getActiveColor(current);
  let msg = 'Tap a highlighted pawn, then choose Knight or Bishop.';
  if (piece && piece.color === activeColor && piece.type === 'PAWN') {
    msg = isPieceFrozen(current, piece)
      ? 'That pawn is frozen and cannot be promoted this turn.'
      : 'Promoting that pawn would immediately give check — pick a highlighted pawn.';
  }
  return { ...current, noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false } };
}

export function performConfirmPawnPromote(
  current: GameUiState,
  targetType: PieceType,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'PROMOTING_PAWN' || current.outcome !== null) return current;
  const activeColor = getActiveColor(current);
  const pawnSquare = current.selectedPromotePawnSquare;
  if (!pawnSquare) return current;
  const validTargets = ChessEngine.getValidPawnPromoteTargets(
    current.board,
    activeColor,
    pawnSquare
  );
  if (!validTargets.includes(targetType)) {
    return {
      ...current,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: `Cannot promote to ${PIECE_META[targetType].displayName} here (would immediately give check).`,
        isAlert: true,
      },
    };
  }
  const pawn = current.board[pawnSquare.row][pawnSquare.col];
  if (!pawn) return current;
  const promoted: ChessPiece = { ...pawn, type: targetType };
  const newBoard = ChessEngine.placePiece(current.board, pawnSquare, promoted);
  const popAnim: ReviveAnimationEvent = {
    id: ctx.nextAnimId(),
    piece: promoted,
    square: pawnSquare,
  };
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const summary = `${activeLabel} promoted pawn on ${squareToAlgebraic(pawnSquare)} → ${PIECE_META[targetType].displayName}.`;

  return passTurnCheckStalemate(
    current,
    newBoard,
    current.castlingRights,
    summary,
    'PROMOTE',
    new Set(),
    {
      selectedPromotePawnSquare: null,
      validPromotePawnSquares: [],
      lastRevivedSquare: pawnSquare,
      moveAnimationEvent: null,
      reviveAnimationEvent: popAnim,
    },
    false,
    ctx
  );
}

function handleSuicidingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activeColor = getActiveColor(current);
  if (!current.validSuicideSquares.some((s) => sameSquare(s, square))) {
    const piece = current.board[square.row][square.col];
    const msg =
      piece && piece.color === activeColor && piece.type === 'KING'
        ? 'You must eliminate a non-King piece.'
        : piece && piece.color === activeColor
          ? 'That piece is pinned to your King — pick another highlighted piece.'
          : 'Suicide! Tap one of your red-outlined pieces to eliminate it.';
    return { ...current, noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: true } };
  }
  const victim = current.board[square.row][square.col];
  if (!victim) return current;

  const { board: newBoard, castlingRights: updatedCastling } = ChessEngine.applySuicide(
    current.board,
    square,
    current.castlingRights
  );
  const updatedCaptured = {
    WHITE: [...(current.capturedPieces.WHITE ?? [])],
    BLACK: [...(current.capturedPieces.BLACK ?? [])],
  };
  updatedCaptured[activeColor] = [...updatedCaptured[activeColor], victim].sort(
    (a, b) => PIECE_META[b.type].materialValue - PIECE_META[a.type].materialValue
  );
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const summary = `${activeLabel} eliminated own ${PIECE_META[victim.type].displayName} on ${squareToAlgebraic(square)} (Suicide).`;

  return passTurnCheckStalemate(
    current,
    newBoard,
    updatedCastling,
    summary,
    'SUICIDE',
    new Set([victim.id]),
    {
      validSuicideSquares: [],
      capturedPieces: updatedCaptured,
      moveAnimationEvent: null,
      reviveAnimationEvent: null,
    },
    true,
    ctx
  );
}

function handleSwappingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activeColor = getActiveColor(current);
  const freezeExcluded = new Set<number>(
    current.frozenPieceId !== null && current.frozenOwner === activeColor
      ? [current.frozenPieceId]
      : []
  );
  const first = current.selectedSwapSquare;

  if (!first) {
    if (current.validSwapSquares.some((s) => sameSquare(s, square))) {
      const partners = ChessEngine.getValidSwapPartners(
        current.board,
        activeColor,
        square,
        freezeExcluded
      );
      return {
        ...current,
        selectedSwapSquare: square,
        validSwapSquares: partners,
        noticeBanner: null,
      };
    }
    const tapped = current.board[square.row][square.col];
    const msg =
      tapped && tapped.color === activeColor && isPieceFrozen(current, tapped)
        ? `${PIECE_META[tapped.type].displayName} is frozen and cannot be swapped this turn.`
        : tapped && tapped.color === activeColor
          ? 'That piece has no safe swap partner right now — pick a highlighted piece.'
          : 'Tap one of your highlighted pieces to start swapping.';
    return { ...current, noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false } };
  }

  if (sameSquare(square, first)) {
    return performClearSwapSelection(current);
  }

  if (current.validSwapSquares.some((s) => sameSquare(s, square))) {
    const p1 = current.board[first.row][first.col];
    const p2 = current.board[square.row][square.col];
    if (!p1 || !p2) return current;
    const { board: newBoard, castlingRights: updatedCastling } = ChessEngine.applySwap(
      current.board,
      first,
      square,
      current.castlingRights
    );
    const swapAnim: MoveAnimationEvent = {
      id: ctx.nextAnimId(),
      piece: p1,
      from: first,
      to: square,
      isCapture: false,
      secondaryPiece: p2,
      secondaryFrom: square,
      secondaryTo: first,
    };
    const activeLabel = getActorName(
      current.activePlayer,
      current.gameMode,
      ctx.isOnlineMultiplayer,
      ctx.localPlayer
    );
    const summary = `${activeLabel} swapped ${PIECE_META[p1.type].displayName} (${squareToAlgebraic(first)}) ⇄ ${PIECE_META[p2.type].displayName} (${squareToAlgebraic(square)}).`;
    return passTurnCheckStalemate(
      current,
      newBoard,
      updatedCastling,
      summary,
      'SWAP',
      new Set(),
      {
        selectedSwapSquare: null,
        validSwapSquares: [],
        lastMoveFrom: first,
        lastMoveTo: square,
        moveAnimationEvent: swapAnim,
        reviveAnimationEvent: null,
      },
      false,
      ctx
    );
  }

  const tapped = current.board[square.row][square.col];
  if (tapped && tapped.color === activeColor && !freezeExcluded.has(tapped.id)) {
    const partners = ChessEngine.getValidSwapPartners(
      current.board,
      activeColor,
      square,
      freezeExcluded
    );
    if (partners.length > 0) {
      return {
        ...current,
        selectedSwapSquare: square,
        validSwapSquares: partners,
        noticeBanner: null,
      };
    }
  }

  return {
    ...current,
    noticeBanner: {
      id: ctx.nextNoticeId(),
      message: 'Tap a highlighted partner piece to swap, or tap Clear.',
      isAlert: false,
    },
  };
}

export function performClearSwapSelection(current: GameUiState): GameUiState {
  if (current.phase !== 'SWAPPING') return current;
  const activeColor = getActiveColor(current);
  const freezeExcluded = new Set<number>(
    current.frozenPieceId !== null && current.frozenOwner === activeColor
      ? [current.frozenPieceId]
      : []
  );
  return {
    ...current,
    selectedSwapSquare: null,
    validSwapSquares: ChessEngine.getValidSwapSourceSquares(
      current.board,
      activeColor,
      freezeExcluded
    ),
    noticeBanner: null,
  };
}

export function performSkipSwap(current: GameUiState, ctx: TransitionContext): GameUiState {
  if (current.phase !== 'SWAPPING' || current.outcome !== null) return current;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const nextShield = nextTurnShield(current.shieldedPieceId, current.shieldedOwner, activeColor);
  const msg = `${activeLabel} skipped Swap Positions.`;
  return {
    ...current,
    activePlayer: opponentPlayer(current.activePlayer),
    phase: 'AWAITING_DRAW',
    selectedSwapSquare: null,
    validSwapSquares: [],
    frozenPieceId: current.frozenOwner === activeColor ? null : current.frozenPieceId,
    frozenOwner: current.frozenOwner === activeColor ? null : current.frozenOwner,
    shieldedPieceId: nextShield.id,
    shieldedOwner: nextShield.owner,
    noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false },
    gameLog: prependLog(current.gameLog, msg, 'SWAP', ctx),
  };
}

function handleCloningSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activeColor = getActiveColor(current);
  const freezeExcluded = new Set<number>(
    current.frozenPieceId !== null && current.frozenOwner === activeColor
      ? [current.frozenPieceId]
      : []
  );
  const source = current.selectedCloneSourceSquare;

  if (!source) {
    if (current.validCloneSquares.some((s) => sameSquare(s, square))) {
      const targets = ChessEngine.getValidCloneTargetSquares(current.board, activeColor, square);
      return {
        ...current,
        selectedCloneSourceSquare: square,
        validCloneSquares: targets,
        noticeBanner: null,
      };
    }
    const tapped = current.board[square.row][square.col];
    let msg = 'Tap a highlighted Pawn, Knight, or Bishop to clone.';
    if (tapped && tapped.color === activeColor) {
      if (isPieceFrozen(current, tapped)) {
        msg = `${PIECE_META[tapped.type].displayName} is frozen and cannot be cloned this turn.`;
      } else if (tapped.type !== 'PAWN' && tapped.type !== 'KNIGHT' && tapped.type !== 'BISHOP') {
        msg = 'Clone only duplicates a Pawn, Knight, or Bishop.';
      }
    }
    return { ...current, noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false } };
  }

  if (sameSquare(square, source)) {
    return performClearCloneSelection(current);
  }

  if (current.validCloneSquares.some((s) => sameSquare(s, square))) {
    const sourcePiece = current.board[source.row][source.col];
    if (!sourcePiece) return current;
    const clonePiece: ChessPiece = {
      id: computeNextPieceId(current),
      type: sourcePiece.type,
      color: activeColor,
    };
    const newBoard = ChessEngine.placePiece(current.board, square, clonePiece);
    const popAnim: ReviveAnimationEvent = { id: ctx.nextAnimId(), piece: clonePiece, square };
    const activeLabel = getActorName(
      current.activePlayer,
      current.gameMode,
      ctx.isOnlineMultiplayer,
      ctx.localPlayer
    );
    const summary = `${activeLabel} cloned ${PIECE_META[sourcePiece.type].displayName} (${squareToAlgebraic(source)}) → ${squareToAlgebraic(square)}.`;
    return passTurnCheckStalemate(
      current,
      newBoard,
      current.castlingRights,
      summary,
      'CLONE',
      new Set(),
      {
        selectedCloneSourceSquare: null,
        validCloneSquares: [],
        lastRevivedSquare: square,
        moveAnimationEvent: null,
        reviveAnimationEvent: popAnim,
      },
      false,
      ctx
    );
  }

  const tapped = current.board[square.row][square.col];
  if (
    tapped &&
    tapped.color === activeColor &&
    !freezeExcluded.has(tapped.id) &&
    (tapped.type === 'PAWN' || tapped.type === 'KNIGHT' || tapped.type === 'BISHOP')
  ) {
    const targets = ChessEngine.getValidCloneTargetSquares(current.board, activeColor, square);
    if (targets.length > 0) {
      return {
        ...current,
        selectedCloneSourceSquare: square,
        validCloneSquares: targets,
        noticeBanner: null,
      };
    }
  }

  return {
    ...current,
    noticeBanner: {
      id: ctx.nextNoticeId(),
      message: 'Tap a highlighted empty square to place the clone, or tap Clear.',
      isAlert: false,
    },
  };
}

export function performClearCloneSelection(current: GameUiState): GameUiState {
  if (current.phase !== 'CLONING') return current;
  const activeColor = getActiveColor(current);
  const freezeExcluded = new Set<number>(
    current.frozenPieceId !== null && current.frozenOwner === activeColor
      ? [current.frozenPieceId]
      : []
  );
  return {
    ...current,
    selectedCloneSourceSquare: null,
    validCloneSquares: ChessEngine.getValidCloneSourceSquares(
      current.board,
      activeColor,
      freezeExcluded
    ),
    noticeBanner: null,
  };
}

export function performSkipClone(current: GameUiState, ctx: TransitionContext): GameUiState {
  if (current.phase !== 'CLONING' || current.outcome !== null) return current;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const nextShield = nextTurnShield(current.shieldedPieceId, current.shieldedOwner, activeColor);
  const msg = `${activeLabel} skipped Clone.`;
  return {
    ...current,
    activePlayer: opponentPlayer(current.activePlayer),
    phase: 'AWAITING_DRAW',
    selectedCloneSourceSquare: null,
    validCloneSquares: [],
    frozenPieceId: current.frozenOwner === activeColor ? null : current.frozenPieceId,
    frozenOwner: current.frozenOwner === activeColor ? null : current.frozenOwner,
    shieldedPieceId: nextShield.id,
    shieldedOwner: nextShield.owner,
    noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false },
    gameLog: prependLog(current.gameLog, msg, 'CLONE', ctx),
  };
}

export function performSelectWildEffect(
  current: GameUiState,
  effect: SpecialCardKey,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'WILD_CHOOSING' || current.outcome !== null) return current;
  if (!current.availableWildEffects.includes(effect)) return current;

  const activeColor = getActiveColor(current);
  const enemy = opponentColor(activeColor);
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const freezeExcluded = new Set<number>(
    current.frozenPieceId !== null && current.frozenOwner === activeColor
      ? [current.frozenPieceId]
      : []
  );
  const logs = prependLog(
    current.gameLog,
    `${activeLabel} chose ${SPECIAL_CARD_META[effect].displayName} from Wild Card!`,
    'WILD',
    ctx
  );

  switch (effect) {
    case 'SHIELD':
      return {
        ...current,
        phase: 'SHIELDING',
        totalMovesForCard: 1,
        movesRemaining: 1,
        validShieldSquares: ChessEngine.getValidShieldSquares(current.board, activeColor),
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Shield! Tap 1 of your highlighted pieces to protect it, then make 1 move.',
          isAlert: false,
        },
        gameLog: logs,
      };
    case 'FREEZE':
      return {
        ...current,
        phase: 'FREEZING',
        totalMovesForCard: 1,
        movesRemaining: 1,
        validFreezeSquares: ChessEngine.getValidFreezeSquares(current.board, enemy),
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Freeze! Tap a blue-outlined enemy piece, then make 1 move.',
          isAlert: false,
        },
        gameLog: logs,
      };
    case 'REVIVE': {
      const ownCap = current.capturedPieces[activeColor] ?? [];
      if (ownCap.length === 0) {
        return {
          ...current,
          phase: 'REVIVING',
          revivesTotalForCard: 1,
          revivesRemaining: 1,
          selectedRevivePieceId: null,
          validReviveSquares: ChessEngine.getValidSpawnPawnSquares(current.board, activeColor),
          availableWildEffects: [],
          noticeBanner: {
            id: ctx.nextNoticeId(),
            message: 'Wild → Revive! Tap a green square to place a Pawn.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }
      const firstRevivable = [...ownCap]
        .sort((a, b) => PIECE_META[b.type].materialValue - PIECE_META[a.type].materialValue)
        .find(
          (p) => ChessEngine.getValidReviveSquares(current.board, p, activeColor, freezeExcluded).length > 0
        );
      if (!firstRevivable) return current;
      return {
        ...current,
        phase: 'REVIVING',
        revivesTotalForCard: 1,
        revivesRemaining: 1,
        selectedRevivePieceId: firstRevivable.id,
        validReviveSquares: ChessEngine.getValidReviveSquares(
          current.board,
          firstRevivable,
          activeColor,
          freezeExcluded
        ),
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Revive! Pick a captured piece & tap a green Pawn to transform it.',
          isAlert: false,
        },
        gameLog: logs,
      };
    }
    case 'SWAP':
      return {
        ...current,
        phase: 'SWAPPING',
        selectedSwapSquare: null,
        validSwapSquares: ChessEngine.getValidSwapSourceSquares(
          current.board,
          activeColor,
          freezeExcluded
        ),
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Swap! Tap 2 of your highlighted pieces to swap their positions.',
          isAlert: false,
        },
        gameLog: logs,
      };
    case 'CLONE':
      return {
        ...current,
        phase: 'CLONING',
        selectedCloneSourceSquare: null,
        validCloneSquares: ChessEngine.getValidCloneSourceSquares(
          current.board,
          activeColor,
          freezeExcluded
        ),
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Clone! Tap a highlighted Pawn, Knight, or Bishop, then an open square.',
          isAlert: false,
        },
        gameLog: logs,
      };
    case 'MERGE':
      return {
        ...current,
        phase: 'MERGING',
        selectedMergeSquares: [],
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Merge! Tap 1+ of your pieces to swap or combine.',
          isAlert: false,
        },
        gameLog: logs,
      };
    case 'SACRIFICE': {
      const validSac = ChessEngine.getValidSacrificeSquares(
        current.board,
        activeColor,
        freezeExcluded
      );
      return {
        ...current,
        phase: 'SACRIFICING',
        selectedSacrificeSquare: validSac[0] ?? null,
        validSacrificeSquares: validSac,
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Sacrifice! Pick a split below or tap another highlighted piece.',
          isAlert: false,
        },
        gameLog: logs,
      };
    }
    case 'HIJACK':
      return {
        ...current,
        phase: 'HIJACKING',
        totalMovesForCard: 1,
        movesRemaining: 1,
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Hijack! Tap an opponent piece and make 1 move with it.',
          isAlert: false,
        },
        gameLog: logs,
      };
    case 'PROMOTE': {
      const validPawns = ChessEngine.getValidPawnPromoteSquares(
        current.board,
        activeColor,
        freezeExcluded
      );
      return {
        ...current,
        phase: 'PROMOTING_PAWN',
        selectedPromotePawnSquare: validPawns[0] ?? null,
        validPromotePawnSquares: validPawns,
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Promote! Pick Knight or Bishop below (or tap another pawn).',
          isAlert: false,
        },
        gameLog: logs,
      };
    }
    case 'SNIPER': {
      const sources = ChessEngine.getValidSniperSources(current.board, activeColor, new Set());
      return {
        ...current,
        phase: 'SNIPING',
        selectedSniperSquare: null,
        validSniperSources: sources,
        validSniperTargets: [],
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Sniper! Tap a piece with attack range, then tap the target to eliminate it.',
          isAlert: false,
        },
        gameLog: logs,
      };
    }
    case 'PORTAL': {
      const sources = ChessEngine.getValidPortalSources(current.board, activeColor);
      return {
        ...current,
        phase: 'PORTALING',
        selectedPortalSourceSquare: null,
        validPortalSources: sources,
        validPortalDestinations: [],
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Portal! Tap 1 of your non-King pieces, then tap an empty square to teleport.',
          isAlert: false,
        },
        gameLog: logs,
      };
    }
    case 'TRAP': {
      const validTraps = ChessEngine.getValidTrapSquares(current.board, current.traps);
      return {
        ...current,
        phase: 'TRAPPING',
        validTrapSquares: validTraps,
        availableWildEffects: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Wild → Trap! Tap any empty square to arm a hidden landmine.',
          isAlert: false,
        },
        gameLog: logs,
      };
    }
    default:
      return current;
  }
}

// ---------------------------------------------------------------------------
// Sniper / Portal / Trap Handlers
// ---------------------------------------------------------------------------

export function handleSniperSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'SNIPING') return current;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(current.activePlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer);
  const enemyColor = opponentColor(activeColor);
  const oppProtected = protectedPieceIdsForOwner(current.shieldedPieceId, current.shieldedOwner, activeColor);

  if (!current.selectedSniperSquare) {
    const isSource = current.validSniperSources.some((s) => sameSquare(s, square));
    if (!isSource) return current;

    const targets = ChessEngine.getValidSniperTargets(current.board, square, enemyColor, oppProtected);
    return {
      ...current,
      selectedSniperSquare: square,
      validSniperTargets: targets,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: 'Sniper locked! Tap a highlighted enemy piece to eliminate it.',
        isAlert: false,
      },
    };
  }

  if (sameSquare(current.selectedSniperSquare, square)) {
    return {
      ...current,
      selectedSniperSquare: null,
      validSniperTargets: [],
      noticeBanner: null,
    };
  }

  const isOtherSource = current.validSniperSources.some((s) => sameSquare(s, square));
  if (isOtherSource) {
    const targets = ChessEngine.getValidSniperTargets(current.board, square, enemyColor, oppProtected);
    return {
      ...current,
      selectedSniperSquare: square,
      validSniperTargets: targets,
    };
  }

  const isTarget = current.validSniperTargets.some((s) => sameSquare(s, square));
  if (!isTarget) return current;

  const sniperPiece = current.board[current.selectedSniperSquare.row][current.selectedSniperSquare.col];
  const targetPiece = current.board[square.row][square.col];
  if (!sniperPiece || !targetPiece) return current;

  const newBoard = ChessEngine.removePiece(current.board, square);
  const updatedCaptured = {
    ...current.capturedPieces,
    [targetPiece.color]: [...current.capturedPieces[targetPiece.color], targetPiece],
  };

  const summary = `${activeLabel}'s ${PIECE_META[sniperPiece.type].displayName} sniped ${colorDisplayName(targetPiece.color)} ${PIECE_META[targetPiece.type].displayName} on ${squareToAlgebraic(square).toUpperCase()}!`;

  return passTurnCheckStalemate(
    current,
    newBoard,
    current.castlingRights,
    summary,
    'SNIPER',
    new Set([targetPiece.id]),
    {
      capturedPieces: updatedCaptured,
      selectedSniperSquare: null,
      validSniperSources: [],
      validSniperTargets: [],
    },
    false,
    ctx
  );
}

export function skipSniper(current: GameUiState, ctx: TransitionContext): GameUiState {
  if (current.phase !== 'SNIPING') return current;
  const activeLabel = getActorName(current.activePlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer);
  return passTurnCheckStalemate(
    current,
    current.board,
    current.castlingRights,
    `${activeLabel} skipped Sniper.`,
    'SNIPER',
    new Set(),
    {
      selectedSniperSquare: null,
      validSniperSources: [],
      validSniperTargets: [],
    },
    false,
    ctx
  );
}

export function handlePortalSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'PORTALING') return current;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(current.activePlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer);

  if (!current.selectedPortalSourceSquare) {
    const isSource = current.validPortalSources.some((s) => sameSquare(s, square));
    if (!isSource) return current;

    const destinations = ChessEngine.getValidPortalDestinations(current.board, activeColor, square);
    return {
      ...current,
      selectedPortalSourceSquare: square,
      validPortalDestinations: destinations,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: 'Portal engaged! Tap a highlighted empty square to teleport.',
        isAlert: false,
      },
    };
  }

  if (sameSquare(current.selectedPortalSourceSquare, square)) {
    return {
      ...current,
      selectedPortalSourceSquare: null,
      validPortalDestinations: [],
      noticeBanner: null,
    };
  }

  const isOtherSource = current.validPortalSources.some((s) => sameSquare(s, square));
  if (isOtherSource) {
    const destinations = ChessEngine.getValidPortalDestinations(current.board, activeColor, square);
    return {
      ...current,
      selectedPortalSourceSquare: square,
      validPortalDestinations: destinations,
    };
  }

  const isDest = current.validPortalDestinations.some((s) => sameSquare(s, square));
  if (!isDest) return current;

  const piece = current.board[current.selectedPortalSourceSquare.row][current.selectedPortalSourceSquare.col];
  if (!piece) return current;

  let newBoard = ChessEngine.removePiece(current.board, current.selectedPortalSourceSquare);
  let updatedTraps = [...current.traps];
  let updatedCaptured = { ...current.capturedPieces };
  let summary = `${activeLabel} teleported ${PIECE_META[piece.type].displayName} from ${squareToAlgebraic(current.selectedPortalSourceSquare).toUpperCase()} to ${squareToAlgebraic(square).toUpperCase()}!`;
  const removedIds = new Set<number>();

  const trapIndex = updatedTraps.findIndex((t) => t.row === square.row && t.col === square.col);
  if (trapIndex >= 0) {
    updatedTraps.splice(trapIndex, 1);
    removedIds.add(piece.id);
    updatedCaptured = {
      ...updatedCaptured,
      [piece.color]: [...updatedCaptured[piece.color], piece],
    };
    summary += ` 💣 Trap triggered! The piece was destroyed!`;
  } else {
    newBoard = ChessEngine.placePiece(newBoard, square, piece);
  }

  return passTurnCheckStalemate(
    current,
    newBoard,
    current.castlingRights,
    summary,
    'PORTAL',
    removedIds,
    {
      traps: updatedTraps,
      capturedPieces: updatedCaptured,
      selectedPortalSourceSquare: null,
      validPortalSources: [],
      validPortalDestinations: [],
      moveAnimationEvent: {
        id: Date.now(),
        from: current.selectedPortalSourceSquare,
        to: square,
        piece,
        isCapture: trapIndex >= 0,
      },
    },
    false,
    ctx
  );
}

export function skipPortal(current: GameUiState, ctx: TransitionContext): GameUiState {
  if (current.phase !== 'PORTALING') return current;
  const activeLabel = getActorName(current.activePlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer);
  return passTurnCheckStalemate(
    current,
    current.board,
    current.castlingRights,
    `${activeLabel} skipped Portal.`,
    'PORTAL',
    new Set(),
    {
      selectedPortalSourceSquare: null,
      validPortalSources: [],
      validPortalDestinations: [],
    },
    false,
    ctx
  );
}

export function handleTrapSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'TRAPPING') return current;
  const isTarget = current.validTrapSquares.some((s) => sameSquare(s, square));
  if (!isTarget) return current;

  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(current.activePlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer);
  const newTrap = {
    id: Date.now() + Math.floor(Math.random() * 1000),
    row: square.row,
    col: square.col,
    owner: activeColor,
  };

  const updatedTraps = [...current.traps, newTrap];
  const summary = `${activeLabel} armed a hidden trap on ${squareToAlgebraic(square).toUpperCase()}!`;

  return passTurnCheckStalemate(
    current,
    current.board,
    current.castlingRights,
    summary,
    'TRAP',
    new Set(),
    {
      traps: updatedTraps,
      validTrapSquares: [],
    },
    false,
    ctx
  );
}

export function skipTrap(current: GameUiState, ctx: TransitionContext): GameUiState {
  if (current.phase !== 'TRAPPING') return current;
  const activeLabel = getActorName(current.activePlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer);
  return passTurnCheckStalemate(
    current,
    current.board,
    current.castlingRights,
    `${activeLabel} skipped Trap.`,
    'TRAP',
    new Set(),
    {
      validTrapSquares: [],
    },
    false,
    ctx
  );
}

export const performSkipSniper = skipSniper;
export const performSkipPortal = skipPortal;
export const performSkipTrap = skipTrap;
