import {
  ALL_SPECIAL_CARDS,
  cardSubtitle,
  cardTitle,
  ChessMove,
  ChessPiece,
  colorDisplayName,
  GAME_MODE_META,
  GameMode,
  GameOutcome,
  GameUiState,
  getActiveColor,
  getActorName,
  INITIAL_CASTLING_RIGHTS,
  isPieceFrozen,
  LogCategory,
  LogEntry,
  MoveAnimationEvent,
  NoticeBanner,
  opponentColor,
  opponentPlayer,
  PIECE_META,
  PieceColor,
  PieceType,
  PlayerId,
  ReviveAnimationEvent,
  sameSquare,
  SpecialCardKey,
  Square,
  squareToAlgebraic,
  UnoCardTheme,
} from '../model/gameModels';
import * as ChessEngine from '../engine/chessEngine';
import * as UnoDeck from '../engine/unoDeck';

export interface TransitionContext {
  nextLogId: () => number;
  nextNoticeId: () => number;
  nextAnimId: () => number;
  isOnlineMultiplayer: boolean;
  localPlayer: PlayerId | null;
  onOutcomeRecorded?: (outcome: GameOutcome) => void;
  onTurnStarted?: () => void;
}

export function prependLog(
  existing: LogEntry[],
  message: string,
  category: LogCategory,
  ctx: TransitionContext
): LogEntry[] {
  const entry: LogEntry = { id: ctx.nextLogId(), message, category };
  return [entry, ...existing].slice(0, 60);
}

export function protectedPieceIdsForOwner(
  shieldedPieceId: number | null,
  shieldedOwner: PieceColor | null,
  ownerColor: PieceColor
): Set<number> {
  if (shieldedPieceId !== null && shieldedOwner === ownerColor) {
    return new Set([shieldedPieceId]);
  }
  return new Set();
}

export function nextTurnShield(
  currentShieldId: number | null,
  currentShieldOwner: PieceColor | null,
  endingTurnColor: PieceColor,
  removedPieceIds: Set<number> = new Set()
): { id: number | null; owner: PieceColor | null } {
  if (currentShieldId === null || currentShieldOwner === null) {
    return { id: null, owner: null };
  }
  if (removedPieceIds.has(currentShieldId)) {
    return { id: null, owner: null };
  }
  if (currentShieldOwner === endingTurnColor) {
    return { id: currentShieldId, owner: currentShieldOwner };
  }
  return { id: null, owner: null };
}

export function computeNextPieceId(state: GameUiState): number {
  let maxId = 64;
  for (const row of state.board) {
    for (const p of row) {
      if (p && p.id > maxId) maxId = p.id;
    }
  }
  for (const list of Object.values(state.capturedPieces)) {
    for (const p of list) {
      if (p.id > maxId) maxId = p.id;
    }
  }
  return maxId + 1;
}

export function activeExcludedPieceIds(state: GameUiState): Set<number> {
  const set = new Set<number>(state.movedPieceIdsThisTurn);
  const activeColor = getActiveColor(state);
  if (state.frozenPieceId !== null && state.frozenOwner === activeColor) {
    set.add(state.frozenPieceId);
  }
  return set;
}

export function createInitialGameState(
  options: {
    gameMode: GameMode;
    isDarkTheme: boolean;
    cardTheme: UnoCardTheme;
    isSoundMuted: boolean;
    enabledSpecialCards?: SpecialCardKey[];
    isOnlineMultiplayer?: boolean;
    localPlayer?: PlayerId | null;
  },
  ctx: TransitionContext
): GameUiState {
  const specials = options.enabledSpecialCards ?? ALL_SPECIAL_CARDS;
  const deck = UnoDeck.createShuffledDeck(specials);
  const actor1 = getActorName(
    'PLAYER_1',
    options.gameMode,
    options.isOnlineMultiplayer,
    options.localPlayer
  );
  const openingMsg = `${actor1} (White) starts — tap the UNO deck to draw!`;

  return {
    board: ChessEngine.createInitialBoard(),
    activePlayer: 'PLAYER_1',
    playerColors: { PLAYER_1: 'WHITE', PLAYER_2: 'BLACK' },
    phase: 'AWAITING_DRAW',
    drawPile: deck,
    discardPile: [],
    activeCard: null,
    drawRevision: 0,
    totalMovesForCard: 0,
    movesRemaining: 0,
    revivesTotalForCard: 0,
    revivesRemaining: 0,
    majorRevivedThisTurn: false,
    movedPieceIdsThisTurn: [],
    frozenPieceId: null,
    frozenOwner: null,
    validFreezeSquares: [],
    shieldedPieceId: null,
    shieldedOwner: null,
    validShieldSquares: [],
    selectedSwapSquare: null,
    validSwapSquares: [],
    selectedCloneSourceSquare: null,
    validCloneSquares: [],
    availableWildEffects: [],
    selectedMergeSquares: [],
    selectedSacrificeSquare: null,
    validSacrificeSquares: [],
    selectedPromotePawnSquare: null,
    validPromotePawnSquares: [],
    validSuicideSquares: [],
    selectedSquare: null,
    legalMovesForSelected: [],
    capturedPieces: { WHITE: [], BLACK: [] },
    selectedRevivePieceId: null,
    validReviveSquares: [],
    lastMoveFrom: null,
    lastMoveTo: null,
    lastRevivedSquare: null,
    checkedKingSquare: null,
    pendingPromotion: null,
    noticeBanner: null,
    moveAnimationEvent: null,
    reviveAnimationEvent: null,
    gameLog: [{ id: ctx.nextLogId(), message: openingMsg, category: 'SYSTEM' }],
    outcome: null,
    castlingRights: { ...INITIAL_CASTLING_RIGHTS },
    enPassantTarget: null,
    enPassantOwner: null,
    gameMode: options.gameMode,
    isAiThinking: false,
    isDarkTheme: options.isDarkTheme,
    cardTheme: options.cardTheme,
    isSoundMuted: options.isSoundMuted,
    movePityCount: { PLAYER_1: 0, PLAYER_2: 0 },
    enabledSpecialCards: [...specials],
    traps: [],
    validTrapSquares: [],
    selectedSniperSquare: null,
    validSniperSources: [],
    validSniperTargets: [],
    selectedPortalSourceSquare: null,
    validPortalSources: [],
    validPortalDestinations: [],
    drawTwoPendingCards: null,
  };
}

export function computePlayableWildEffects(
  state: GameUiState,
  activeColor: PieceColor,
  freezeExcluded: Set<number>
): SpecialCardKey[] {
  const enemy = opponentColor(activeColor);
  const enemyExcluded = new Set<number>(
    state.frozenPieceId !== null && state.frozenOwner === enemy ? [state.frozenPieceId] : []
  );
  const protectedOwn = protectedPieceIdsForOwner(
    state.shieldedPieceId,
    state.shieldedOwner,
    activeColor
  );

  const order: SpecialCardKey[] = [
    'SHIELD',
    'FREEZE',
    'REVIVE',
    'CLONE',
    'SWAP',
    'PROMOTE',
    'HIJACK',
    'MERGE',
    'SACRIFICE',
    'SNIPER',
    'PORTAL',
    'TRAP',
  ];

  return order
    .filter((k) => state.enabledSpecialCards.includes(k))
    .filter((key) => {
      switch (key) {
        case 'SHIELD':
          return ChessEngine.getValidShieldSquares(state.board, activeColor).length > 0;
        case 'FREEZE':
          return ChessEngine.getValidFreezeSquares(state.board, enemy).length > 0;
        case 'REVIVE': {
          const ownCap = state.capturedPieces[activeColor] ?? [];
          if (ownCap.length === 0) {
            return ChessEngine.getValidSpawnPawnSquares(state.board, activeColor).length > 0;
          }
          return ownCap.some(
            (p) => ChessEngine.getValidReviveSquares(state.board, p, activeColor, freezeExcluded).length > 0
          );
        }
        case 'CLONE':
          return ChessEngine.getValidCloneSourceSquares(state.board, activeColor, freezeExcluded).length > 0;
        case 'SWAP':
          return ChessEngine.getValidSwapSourceSquares(state.board, activeColor, freezeExcluded).length > 0;
        case 'PROMOTE':
          return ChessEngine.getValidPawnPromoteSquares(state.board, activeColor, freezeExcluded).length > 0;
        case 'HIJACK':
          return (
            ChessEngine.getAllHijackMoves(
              state.board,
              activeColor,
              state.castlingRights,
              state.enPassantTarget,
              state.enPassantOwner,
              enemyExcluded,
              protectedOwn
            ).length > 0
          );
        case 'MERGE':
          return ChessEngine.hasAnyValidTrade(state.board, activeColor, freezeExcluded);
        case 'SACRIFICE':
          return ChessEngine.getValidSacrificeSquares(state.board, activeColor, freezeExcluded).length > 0;
        case 'SNIPER':
          return ChessEngine.getValidSniperSources(state.board, activeColor, new Set()).length > 0;
        case 'PORTAL':
          return ChessEngine.getValidPortalSources(state.board, activeColor).length > 0;
        case 'TRAP':
          return ChessEngine.getValidTrapSquares(state.board, state.traps).length > 0;
        default:
          return false;
      }
    });
}

export function performDrawCard(current: GameUiState, ctx: TransitionContext): GameUiState {
  if (current.phase !== 'AWAITING_DRAW' || current.outcome !== null) return current;

  const activePlayer = current.activePlayer;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(
    activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const enemy = opponentColor(activeColor);

  const inCheckAtStart = ChessEngine.isKingInCheck(current.board, activeColor);
  let logs = current.gameLog;
  let notice: NoticeBanner | null = null;

  let workingFrozenId = current.frozenPieceId;
  let workingFrozenOwner = current.frozenOwner;

  // Auto-thaw if starting turn in check
  if (inCheckAtStart && workingFrozenId !== null && workingFrozenOwner === activeColor) {
    workingFrozenId = null;
    workingFrozenOwner = null;
    logs = prependLog(
      logs,
      `${activeLabel} is in check — frozen piece thawed to defend!`,
      'FREEZE',
      ctx
    );
    notice = {
      id: ctx.nextNoticeId(),
      message: 'In check! Frozen piece thawed so you can defend.',
      isAlert: true,
    };
  }

  let freezeExcluded = new Set<number>(
    workingFrozenId !== null && workingFrozenOwner === activeColor ? [workingFrozenId] : []
  );
  const protectedEnemy = protectedPieceIdsForOwner(
    current.shieldedPieceId,
    current.shieldedOwner,
    enemy
  );

  let activeHasMoves = ChessEngine.hasAnyLegalMove(
    current.board,
    activeColor,
    current.castlingRights,
    current.enPassantTarget,
    current.enPassantOwner,
    freezeExcluded,
    protectedEnemy
  );

  if (!activeHasMoves && freezeExcluded.size > 0) {
    const hasMovesIfThawed = ChessEngine.hasAnyLegalMove(
      current.board,
      activeColor,
      current.castlingRights,
      current.enPassantTarget,
      current.enPassantOwner,
      new Set(),
      protectedEnemy
    );
    if (hasMovesIfThawed) {
      workingFrozenId = null;
      workingFrozenOwner = null;
      freezeExcluded = new Set();
      activeHasMoves = true;
      logs = prependLog(
        logs,
        `Freeze thawed so ${activeLabel} has a legal move.`,
        'FREEZE',
        ctx
      );
    }
  }

  if (!activeHasMoves) {
    const outcome: GameOutcome = inCheckAtStart
      ? {
          kind: 'CHECKMATE',
          winnerPlayer: opponentPlayer(activePlayer),
          winnerColor: enemy,
        }
      : { kind: 'STALEMATE' };
    const msg =
      outcome.kind === 'CHECKMATE'
        ? `Checkmate! ${getActorName(outcome.winnerPlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer)} (${colorDisplayName(outcome.winnerColor)}) wins!`
        : 'Stalemate! The game ends in a draw.';
    ctx.onOutcomeRecorded?.(outcome);
    return {
      ...current,
      phase: 'GAME_OVER',
      outcome,
      frozenPieceId: null,
      frozenOwner: null,
      shieldedPieceId: null,
      shieldedOwner: null,
      gameLog: prependLog(logs, msg, 'GAME_OVER', ctx),
    };
  }

  ctx.onTurnStarted?.();

  const playerPity = current.movePityCount[activePlayer] ?? 0;
  let drawPile = [...current.drawPile];
  const discardPile = [...current.discardPile];

  if (
    current.activeCard &&
    !discardPile.some((c) => c.id === current.activeCard!.id) &&
    !drawPile.some((c) => c.id === current.activeCard!.id)
  ) {
    discardPile.push(current.activeCard);
  }

  let safety = 0;
  while (safety < 64) {
    safety++;
    if (drawPile.length === 0) {
      if (discardPile.length === 0) {
        drawPile = UnoDeck.createShuffledDeck(current.enabledSpecialCards);
      } else {
        drawPile = [...discardPile].sort(() => Math.random() - 0.5);
        discardPile.length = 0;
      }
      logs = prependLog(logs, 'UNO deck reshuffled.', 'SYSTEM', ctx);
    }

    const drawn = UnoDeck.drawCardWithPity(
      drawPile,
      discardPile,
      playerPity,
      current.enabledSpecialCards
    );

    if (inCheckAtStart && drawn.type.kind !== 'NUMBER') {
      discardPile.push(drawn);
      const name = cardTitle(drawn.type);
      logs = prependLog(
        logs,
        `${activeLabel} is in check — ${name} discarded, redrawing.`,
        'CHECK',
        ctx
      );
      notice = {
        id: ctx.nextNoticeId(),
        message: `In check! ${name} discarded — auto-redrew a Number card.`,
        isAlert: true,
      };
      continue;
    }

    const nextPityMap: Record<PlayerId, number> = {
      ...current.movePityCount,
      [activePlayer]: drawn.type.kind === 'NUMBER' ? 0 : playerPity + 1,
    };

    const baseCleared: GameUiState = {
      ...current,
      drawPile: [...drawPile],
      discardPile: [...discardPile],
      activeCard: drawn,
      drawRevision: current.drawRevision + 1,
      totalMovesForCard: 0,
      movesRemaining: 0,
      revivesTotalForCard: 0,
      revivesRemaining: 0,
      majorRevivedThisTurn: false,
      movedPieceIdsThisTurn: [],
      frozenPieceId: workingFrozenId,
      frozenOwner: workingFrozenOwner,
      validFreezeSquares: [],
      validShieldSquares: [],
      selectedSwapSquare: null,
      validSwapSquares: [],
      selectedCloneSourceSquare: null,
      validCloneSquares: [],
      availableWildEffects: [],
      selectedMergeSquares: [],
      selectedSacrificeSquare: null,
      validSacrificeSquares: [],
      selectedPromotePawnSquare: null,
      validPromotePawnSquares: [],
      validSuicideSquares: [],
      validTrapSquares: [],
      selectedSniperSquare: null,
      validSniperSources: [],
      validSniperTargets: [],
      selectedPortalSourceSquare: null,
      validPortalSources: [],
      validPortalDestinations: [],
      drawTwoPendingCards: null,
      selectedSquare: null,
      legalMovesForSelected: [],
      selectedRevivePieceId: null,
      validReviveSquares: [],
      movePityCount: nextPityMap,
    };

    switch (drawn.type.kind) {
      case 'NUMBER': {
        const moves = drawn.type.moves;
        const moveWord = moves === 1 ? '1 move' : `${moves} moves`;
        const pityNote = playerPity > 0 ? ' (Pity reset)' : '';
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew ${moves} (${moveWord})${pityNote}.`,
          'CARD',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'MOVING',
          totalMovesForCard: moves,
          movesRemaining: moves,
          checkedKingSquare: inCheckAtStart ? ChessEngine.findKing(current.board, activeColor) : null,
          noticeBanner: notice,
          gameLog: logs,
        };
      }

      case 'REVIVE': {
        const ownCap = current.capturedPieces[activeColor] ?? [];
        if (ownCap.length === 0) {
          const spawnSquares = ChessEngine.getValidSpawnPawnSquares(current.board, activeColor);
          if (spawnSquares.length === 0) {
            discardPile.push(drawn);
            logs = prependLog(
              logs,
              `${activeLabel} drew Revive — no empty square to place a Pawn. Redrawing!`,
              'REVIVE',
              ctx
            );
            notice = {
              id: ctx.nextNoticeId(),
              message: 'No empty square to place a Pawn — drew another card!',
              isAlert: false,
            };
            continue;
          }
          logs = prependLog(
            logs,
            `${activeLabel} (${colorDisplayName(activeColor)}) drew Revive — no captured pieces, place 1 Pawn.`,
            'REVIVE',
            ctx
          );
          return {
            ...baseCleared,
            phase: 'REVIVING',
            revivesTotalForCard: 1,
            revivesRemaining: 1,
            validReviveSquares: spawnSquares,
            noticeBanner: notice ?? {
              id: ctx.nextNoticeId(),
              message: 'Revive! No captured pieces — tap a green square to place a Pawn.',
              isAlert: false,
            },
            gameLog: logs,
          };
        }

        const prioritized = [...ownCap].sort(
          (a, b) => PIECE_META[b.type].materialValue - PIECE_META[a.type].materialValue
        );
        const firstRevivable = prioritized.find(
          (p) => ChessEngine.getValidReviveSquares(current.board, p, activeColor, freezeExcluded).length > 0
        );
        if (!firstRevivable) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Revive — requires an eligible Pawn on the board. Redrawing!`,
            'REVIVE',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'Revive requires a Pawn on the board — drew another card!',
            isAlert: false,
          };
          continue;
        }
        const validPawns = ChessEngine.getValidReviveSquares(
          current.board,
          firstRevivable,
          activeColor,
          freezeExcluded
        );
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Revive — turn 1 Pawn into a captured piece.`,
          'REVIVE',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'REVIVING',
          revivesTotalForCard: 1,
          revivesRemaining: 1,
          selectedRevivePieceId: firstRevivable.id,
          validReviveSquares: validPawns,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Revive! Pick a captured piece & tap a green Pawn to transform it.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'FREEZE': {
        const validTargets = ChessEngine.getValidFreezeSquares(current.board, enemy);
        if (validTargets.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Freeze — only enemy King remains. Redrawing!`,
            'FREEZE',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No enemy piece to freeze — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Freeze — pick an enemy piece to lock + 1 move.`,
          'FREEZE',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'FREEZING',
          totalMovesForCard: 1,
          movesRemaining: 1,
          validFreezeSquares: validTargets,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Freeze! Tap a blue-outlined enemy piece to lock it, then make 1 move.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'SHIELD': {
        const validShields = ChessEngine.getValidShieldSquares(current.board, activeColor);
        if (validShields.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Shield — only King remains. Redrawing!`,
            'SHIELD',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No non-King piece to shield — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Shield — protect 1 piece + 1 move.`,
          'SHIELD',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'SHIELDING',
          totalMovesForCard: 1,
          movesRemaining: 1,
          validShieldSquares: validShields,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Shield! Tap 1 of your highlighted pieces to protect it, then make 1 move.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'MERGE': {
        if (!ChessEngine.hasAnyValidTrade(current.board, activeColor, freezeExcluded)) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Merge — no valid pieces to merge. Redrawing!`,
            'MERGE',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No valid pieces to merge — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Merge — combine or swap pieces by value.`,
          'MERGE',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'MERGING',
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Merge! Tap 1+ of your pieces to swap or combine.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'SACRIFICE': {
        const validSac = ChessEngine.getValidSacrificeSquares(
          current.board,
          activeColor,
          freezeExcluded
        );
        if (validSac.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Sacrifice — no piece available to split. Redrawing!`,
            'SACRIFICE',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No piece can be sacrificed — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Sacrifice — split 1 piece into equal-value pieces.`,
          'SACRIFICE',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'SACRIFICING',
          selectedSacrificeSquare: validSac[0],
          validSacrificeSquares: validSac,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Sacrifice! Pick a split below (or tap another highlighted piece).',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'HIJACK': {
        const enemyExcluded = new Set<number>(
          workingFrozenId !== null && workingFrozenOwner === enemy ? [workingFrozenId] : []
        );
        const protectedOwn = protectedPieceIdsForOwner(
          current.shieldedPieceId,
          current.shieldedOwner,
          activeColor
        );
        const hijackMoves = ChessEngine.getAllHijackMoves(
          current.board,
          activeColor,
          current.castlingRights,
          current.enPassantTarget,
          current.enPassantOwner,
          enemyExcluded,
          protectedOwn
        );
        if (hijackMoves.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Hijack — no opponent piece can safely move. Redrawing!`,
            'HIJACK',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No opponent piece can be hijacked — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Hijack — move 1 ${colorDisplayName(enemy)} piece.`,
          'HIJACK',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'HIJACKING',
          totalMovesForCard: 1,
          movesRemaining: 1,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Hijack! Tap an opponent piece and make 1 move with it.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'PROMOTE': {
        const validPawns = ChessEngine.getValidPawnPromoteSquares(
          current.board,
          activeColor,
          freezeExcluded
        );
        if (validPawns.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Promote — no eligible pawn to promote. Redrawing!`,
            'PROMOTE',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No eligible pawn to promote — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Promote — upgrade 1 pawn to Knight or Bishop.`,
          'PROMOTE',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'PROMOTING_PAWN',
          selectedPromotePawnSquare: validPawns[0],
          validPromotePawnSquares: validPawns,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Promote! Pick Knight or Bishop below (or tap another pawn).',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'SUICIDE': {
        const ownNonKing = ChessEngine.getOwnNonKingSquares(current.board, activeColor);
        if (ownNonKing.length === 0) {
          const oppPlayer = opponentPlayer(activePlayer);
          const outcome: GameOutcome = {
            kind: 'SUICIDE',
            losingPlayer: activePlayer,
            winnerPlayer: oppPlayer,
            winnerColor: enemy,
          };
          const msg = `${activeLabel} drew Suicide with only their King left — ${getActorName(oppPlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer)} (${colorDisplayName(enemy)}) wins!`;
          logs = prependLog(logs, msg, 'GAME_OVER', ctx);
          ctx.onOutcomeRecorded?.(outcome);
          return {
            ...baseCleared,
            phase: 'GAME_OVER',
            frozenPieceId: null,
            frozenOwner: null,
            noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: true },
            gameLog: logs,
            outcome,
          };
        }

        const validSuicide = ChessEngine.getValidSuicideSquares(current.board, activeColor);
        if (validSuicide.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Suicide — all remaining pieces are pinned to the King. Redrawing!`,
            'SUICIDE',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'All pieces pinned to King — Suicide redrawn!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Suicide — eliminate 1 of your pieces.`,
          'SUICIDE',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'SUICIDING',
          validSuicideSquares: validSuicide,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Suicide! Tap 1 of your highlighted pieces to remove it.',
            isAlert: true,
          },
          gameLog: logs,
        };
      }

      case 'SWAP': {
        const validSources = ChessEngine.getValidSwapSourceSquares(
          current.board,
          activeColor,
          freezeExcluded
        );
        if (validSources.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Swap Positions — no legal pair to swap. Redrawing!`,
            'SWAP',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No pieces can safely swap — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Swap Positions — swap 2 of your pieces.`,
          'SWAP',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'SWAPPING',
          validSwapSquares: validSources,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Swap! Tap 2 of your highlighted pieces to exchange their squares.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'CLONE': {
        const validSources = ChessEngine.getValidCloneSourceSquares(
          current.board,
          activeColor,
          freezeExcluded
        );
        if (validSources.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Clone — no eligible Pawn, Knight, or Bishop to clone. Redrawing!`,
            'CLONE',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No Pawn, Knight, or Bishop can be cloned — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Clone — duplicate 1 Pawn, Knight, or Bishop.`,
          'CLONE',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'CLONING',
          validCloneSquares: validSources,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Clone! Tap a highlighted Pawn, Knight, or Bishop, then an open square.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'WILD': {
        const wildOptions = computePlayableWildEffects(
          { ...current, frozenPieceId: workingFrozenId, frozenOwner: workingFrozenOwner },
          activeColor,
          freezeExcluded
        );
        if (wildOptions.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Wild Card — no special card effects available. Redrawing!`,
            'WILD',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No special effects available for Wild Card — drew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Wild Card — choose any special effect!`,
          'WILD',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'WILD_CHOOSING',
          availableWildEffects: wildOptions,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Wild Card! Pick any special card effect below.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'SNIPER': {
        const oppProtected = protectedPieceIdsForOwner(
          current.shieldedPieceId,
          current.shieldedOwner,
          enemy
        );
        const sources = ChessEngine.getValidSniperSources(current.board, activeColor, oppProtected);
        if (sources.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Sniper — no enemy piece in attack range. Redrawing!`,
            'SNIPER',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'Sniper requires an enemy piece in range — redrew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Sniper! Tap a piece to take aim.`,
          'CARD',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'SNIPING',
          validSniperSources: sources,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Sniper! Tap a piece with attack range, then tap the target to eliminate it.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'PORTAL': {
        const sources = ChessEngine.getValidPortalSources(current.board, activeColor);
        if (sources.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Portal — no non-King piece to teleport. Redrawing!`,
            'PORTAL',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'Portal requires a non-King piece — redrew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Portal!`,
          'CARD',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'PORTALING',
          validPortalSources: sources,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Portal! Tap 1 of your non-King pieces, then tap an empty square to teleport.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'TRAP': {
        const validTraps = ChessEngine.getValidTrapSquares(current.board, current.traps);
        if (validTraps.length === 0) {
          discardPile.push(drawn);
          logs = prependLog(
            logs,
            `${activeLabel} drew Trap — no empty square available. Redrawing!`,
            'TRAP',
            ctx
          );
          notice = {
            id: ctx.nextNoticeId(),
            message: 'No empty square for trap — redrew another card!',
            isAlert: false,
          };
          continue;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Trap!`,
          'CARD',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'TRAPPING',
          validTrapSquares: validTraps,
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Trap! Tap any empty square to arm a hidden landmine.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }

      case 'DRAW_TWO': {
        let cardA = drawPile.pop();
        if (!cardA) {
          if (discardPile.length > 0) {
            drawPile.push(...[...discardPile].sort(() => Math.random() - 0.5));
            discardPile.length = 0;
          } else {
            drawPile.push(...UnoDeck.createShuffledDeck(current.enabledSpecialCards));
          }
          cardA = drawPile.pop()!;
        }
        let cardB = drawPile.pop();
        if (!cardB) {
          if (discardPile.length > 0) {
            drawPile.push(...[...discardPile].sort(() => Math.random() - 0.5));
            discardPile.length = 0;
          } else {
            drawPile.push(...UnoDeck.createShuffledDeck(current.enabledSpecialCards));
          }
          cardB = drawPile.pop()!;
        }
        logs = prependLog(
          logs,
          `${activeLabel} (${colorDisplayName(activeColor)}) drew Draw Two! Pick a card below.`,
          'CARD',
          ctx
        );
        return {
          ...baseCleared,
          phase: 'DRAW_TWO_CHOOSING',
          drawTwoPendingCards: [cardA, cardB],
          noticeBanner: notice ?? {
            id: ctx.nextNoticeId(),
            message: 'Draw Two! Choose which card you want to play for this turn.',
            isAlert: false,
          },
          gameLog: logs,
        };
      }
    }
  }

  return current;
}

export function executeChessMove(
  current: GameUiState,
  move: ChessMove,
  ctx: TransitionContext
): GameUiState {
  const activePlayer = current.activePlayer;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(
    activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const oppPlayer = opponentPlayer(activePlayer);
  const enemy = opponentColor(activeColor);

  const result = ChessEngine.applyMove(current.board, move, current.castlingRights);
  const animatedPiece: ChessPiece = move.promotion
    ? { ...result.movedPiece, type: move.promotion }
    : result.movedPiece;

  let rookPiece: ChessPiece | null = null;
  let rookFrom: Square | null = null;
  let rookTo: Square | null = null;
  if (move.isCastlingKingside) {
    rookPiece = current.board[move.from.row][7];
    rookFrom = { row: move.from.row, col: 7 };
    rookTo = { row: move.from.row, col: 5 };
  } else if (move.isCastlingQueenside) {
    rookPiece = current.board[move.from.row][0];
    rookFrom = { row: move.from.row, col: 0 };
    rookTo = { row: move.from.row, col: 3 };
  }

  const moveAnim: MoveAnimationEvent = {
    id: ctx.nextAnimId(),
    piece: animatedPiece,
    from: move.from,
    to: move.to,
    isCapture: result.capturedPiece !== null,
    secondaryPiece: rookPiece,
    secondaryFrom: rookFrom,
    secondaryTo: rookTo,
  };

  const updatedMovedIds = Array.from(
    new Set([
      ...current.movedPieceIdsThisTurn,
      result.movedPiece.id,
      ...(rookPiece ? [rookPiece.id] : []),
    ])
  );

  const capturedFrozen =
    result.capturedPiece !== null && result.capturedPiece.id === current.frozenPieceId;
  const keptFrozenId = capturedFrozen ? null : current.frozenPieceId;
  const keptFrozenOwner = capturedFrozen ? null : current.frozenOwner;
  const nextTurnFrozenId = keptFrozenOwner === activeColor ? null : keptFrozenId;
  const nextTurnFrozenOwner = keptFrozenOwner === activeColor ? null : keptFrozenOwner;

  const updatedCaptured = {
    WHITE: [...(current.capturedPieces.WHITE ?? [])],
    BLACK: [...(current.capturedPieces.BLACK ?? [])],
  };
  if (result.capturedPiece) {
    const col = result.capturedPiece.color;
    updatedCaptured[col] = [...updatedCaptured[col], result.capturedPiece].sort(
      (a, b) => PIECE_META[b.type].materialValue - PIECE_META[a.type].materialValue
    );
  }

  let finalBoard = result.board;
  let updatedTraps = [...current.traps];
  const trapIndex = updatedTraps.findIndex((t) => t.row === move.to.row && t.col === move.to.col);
  let trapTriggered = false;
  if (trapIndex >= 0) {
    trapTriggered = true;
    updatedTraps.splice(trapIndex, 1);
    finalBoard = ChessEngine.removePiece(finalBoard, move.to);
    const pieceCol = animatedPiece.color;
    updatedCaptured[pieceCol] = [...updatedCaptured[pieceCol], animatedPiece].sort(
      (a, b) => PIECE_META[b.type].materialValue - PIECE_META[a.type].materialValue
    );
  }

  const nextShield = nextTurnShield(
    current.shieldedPieceId,
    current.shieldedOwner,
    activeColor,
    new Set(result.capturedPiece ? [result.capturedPiece.id] : [])
  );
  const opponentProtected = protectedPieceIdsForOwner(nextShield.id, nextShield.owner, activeColor);

  const remainingAfterMove = Math.max(0, current.movesRemaining - 1);
  const enemyInCheck = ChessEngine.isKingInCheck(finalBoard, enemy);
  const enemyHasMove = ChessEngine.hasAnyLegalMove(
    finalBoard,
    enemy,
    result.castlingRights,
    result.enPassantTarget,
    result.enPassantOwner,
    new Set(),
    opponentProtected
  );

  let logs = prependLog(
    current.gameLog,
    `${activeLabel} (${colorDisplayName(activeColor)}): ${result.notation}`,
    'MOVE',
    ctx
  );

  if (trapTriggered) {
    const trapMsg = `💣 Trap triggered on ${squareToAlgebraic(move.to).toUpperCase()}! ${activeLabel}'s ${PIECE_META[animatedPiece.type].displayName} was destroyed!`;
    logs = prependLog(logs, trapMsg, 'TRAP', ctx);
    return {
      ...current,
      board: finalBoard,
      activePlayer: oppPlayer,
      phase: 'AWAITING_DRAW',
      movesRemaining: 0,
      movedPieceIdsThisTurn: [],
      frozenPieceId: nextTurnFrozenId,
      frozenOwner: nextTurnFrozenOwner,
      validFreezeSquares: [],
      shieldedPieceId: nextShield.id,
      shieldedOwner: nextShield.owner,
      traps: updatedTraps,
      selectedSquare: null,
      legalMovesForSelected: [],
      capturedPieces: updatedCaptured,
      lastMoveFrom: move.from,
      lastMoveTo: move.to,
      lastRevivedSquare: null,
      checkedKingSquare: enemyInCheck ? ChessEngine.findKing(finalBoard, enemy) : null,
      pendingPromotion: null,
      noticeBanner: { id: ctx.nextNoticeId(), message: trapMsg, isAlert: true },
      moveAnimationEvent: moveAnim,
      reviveAnimationEvent: null,
      gameLog: logs,
      castlingRights: result.castlingRights,
      enPassantTarget: null,
      enPassantOwner: null,
    };
  }

  if (!enemyHasMove) {
    const outcome: GameOutcome = enemyInCheck
      ? { kind: 'CHECKMATE', winnerPlayer: activePlayer, winnerColor: activeColor }
      : { kind: 'STALEMATE' };
    const endMessage =
      outcome.kind === 'CHECKMATE'
        ? `Checkmate! ${activeLabel} (${colorDisplayName(activeColor)}) wins!`
        : `Stalemate! ${getActorName(oppPlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer)} (${colorDisplayName(enemy)}) has no legal moves.`;
    logs = prependLog(logs, endMessage, 'GAME_OVER', ctx);
    ctx.onOutcomeRecorded?.(outcome);
    return {
      ...current,
      board: finalBoard,
      phase: 'GAME_OVER',
      movesRemaining: 0,
      movedPieceIdsThisTurn: [],
      frozenPieceId: null,
      frozenOwner: null,
      validFreezeSquares: [],
      shieldedPieceId: null,
      shieldedOwner: null,
      traps: updatedTraps,
      selectedSquare: null,
      legalMovesForSelected: [],
      capturedPieces: updatedCaptured,
      lastMoveFrom: move.from,
      lastMoveTo: move.to,
      lastRevivedSquare: null,
      checkedKingSquare: enemyInCheck ? ChessEngine.findKing(finalBoard, enemy) : null,
      pendingPromotion: null,
      noticeBanner: { id: ctx.nextNoticeId(), message: endMessage, isAlert: enemyInCheck },
      moveAnimationEvent: moveAnim,
      reviveAnimationEvent: null,
      gameLog: logs,
      outcome,
      castlingRights: result.castlingRights,
      enPassantTarget: result.enPassantTarget,
      enPassantOwner: result.enPassantOwner,
    };
  }

  if (enemyInCheck) {
    const checkMsg =
      remainingAfterMove > 0
        ? `Check! ${remainingAfterMove} remaining ${remainingAfterMove === 1 ? 'move' : 'moves'} forfeited.`
        : `Check on ${colorDisplayName(enemy)}!`;
    logs = prependLog(logs, checkMsg, 'CHECK', ctx);
    return {
      ...current,
      board: finalBoard,
      activePlayer: oppPlayer,
      phase: 'AWAITING_DRAW',
      movesRemaining: 0,
      movedPieceIdsThisTurn: [],
      frozenPieceId: nextTurnFrozenId,
      frozenOwner: nextTurnFrozenOwner,
      validFreezeSquares: [],
      shieldedPieceId: nextShield.id,
      shieldedOwner: nextShield.owner,
      traps: updatedTraps,
      selectedSquare: null,
      legalMovesForSelected: [],
      capturedPieces: updatedCaptured,
      lastMoveFrom: move.from,
      lastMoveTo: move.to,
      lastRevivedSquare: null,
      checkedKingSquare: ChessEngine.findKing(finalBoard, enemy),
      pendingPromotion: null,
      noticeBanner: { id: ctx.nextNoticeId(), message: checkMsg, isAlert: true },
      moveAnimationEvent: moveAnim,
      reviveAnimationEvent: null,
      gameLog: logs,
      castlingRights: result.castlingRights,
      enPassantTarget: result.enPassantTarget,
      enPassantOwner: result.enPassantOwner,
    };
  }

  if (remainingAfterMove > 0) {
    const nextExcluded = new Set<number>(updatedMovedIds);
    if (keptFrozenId !== null && keptFrozenOwner === activeColor) {
      nextExcluded.add(keptFrozenId);
    }
    const activeProtectedEnemy = protectedPieceIdsForOwner(
      current.shieldedPieceId,
      current.shieldedOwner,
      enemy
    );
    const activeStillHasMove = ChessEngine.hasAnyLegalMove(
      finalBoard,
      activeColor,
      result.castlingRights,
      null,
      null,
      nextExcluded,
      activeProtectedEnemy
    );

    if (activeStillHasMove) {
      return {
        ...current,
        board: finalBoard,
        phase: 'MOVING',
        movesRemaining: remainingAfterMove,
        movedPieceIdsThisTurn: updatedMovedIds,
        frozenPieceId: keptFrozenId,
        frozenOwner: keptFrozenOwner,
        traps: updatedTraps,
        selectedSquare: null,
        legalMovesForSelected: [],
        capturedPieces: updatedCaptured,
        lastMoveFrom: move.from,
        lastMoveTo: move.to,
        lastRevivedSquare: null,
        checkedKingSquare: null,
        pendingPromotion: null,
        noticeBanner: null,
        moveAnimationEvent: moveAnim,
        reviveAnimationEvent: null,
        gameLog: logs,
        castlingRights: result.castlingRights,
        enPassantTarget: null,
        enPassantOwner: null,
      };
    }

    logs = prependLog(logs, 'No other pieces can move — turn passed.', 'SYSTEM', ctx);
    return {
      ...current,
      board: finalBoard,
      activePlayer: oppPlayer,
      phase: 'AWAITING_DRAW',
      movesRemaining: 0,
      movedPieceIdsThisTurn: [],
      frozenPieceId: nextTurnFrozenId,
      frozenOwner: nextTurnFrozenOwner,
      validFreezeSquares: [],
      shieldedPieceId: nextShield.id,
      shieldedOwner: nextShield.owner,
      traps: updatedTraps,
      selectedSquare: null,
      legalMovesForSelected: [],
      capturedPieces: updatedCaptured,
      lastMoveFrom: move.from,
      lastMoveTo: move.to,
      lastRevivedSquare: null,
      checkedKingSquare: null,
      pendingPromotion: null,
      noticeBanner: {
        id: ctx.nextNoticeId(),
        message: 'No other pieces can move — turn passed.',
        isAlert: false,
      },
      moveAnimationEvent: moveAnim,
      reviveAnimationEvent: null,
      gameLog: logs,
      castlingRights: result.castlingRights,
      enPassantTarget: result.enPassantTarget,
      enPassantOwner: result.enPassantOwner,
    };
  }

  return {
    ...current,
    board: result.board,
    activePlayer: oppPlayer,
    phase: 'AWAITING_DRAW',
    movesRemaining: 0,
    movedPieceIdsThisTurn: [],
    frozenPieceId: nextTurnFrozenId,
    frozenOwner: nextTurnFrozenOwner,
    validFreezeSquares: [],
    shieldedPieceId: nextShield.id,
    shieldedOwner: nextShield.owner,
    selectedSquare: null,
    legalMovesForSelected: [],
    capturedPieces: updatedCaptured,
    lastMoveFrom: move.from,
    lastMoveTo: move.to,
    lastRevivedSquare: null,
    checkedKingSquare: null,
    pendingPromotion: null,
    noticeBanner: null,
    moveAnimationEvent: moveAnim,
    reviveAnimationEvent: null,
    gameLog: logs,
    castlingRights: result.castlingRights,
    enPassantTarget: result.enPassantTarget,
    enPassantOwner: result.enPassantOwner,
  };
}

export function handleMovingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activeColor = getActiveColor(current);
  const selected = current.selectedSquare;

  if (selected) {
    const matching = current.legalMovesForSelected.filter((m) => sameSquare(m.to, square));
    if (matching.length > 0) {
      if (matching.some((m) => m.promotion)) {
        return { ...current, pendingPromotion: { from: selected, to: square } };
      }
      return executeChessMove(current, matching[0], ctx);
    }
  }

  const tapped = current.board[square.row][square.col];
  if (tapped && tapped.color === activeColor) {
    if (isPieceFrozen(current, tapped)) {
      return {
        ...current,
        selectedSquare: null,
        legalMovesForSelected: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: `${PIECE_META[tapped.type].displayName} is frozen until next turn!`,
          isAlert: true,
        },
      };
    }
    if (current.movedPieceIdsThisTurn.includes(tapped.id)) {
      return {
        ...current,
        selectedSquare: null,
        legalMovesForSelected: [],
        noticeBanner: {
          id: ctx.nextNoticeId(),
          message: 'Max 1 move per piece per turn — pick another piece.',
          isAlert: false,
        },
      };
    }
    if (sameSquare(selected, square)) {
      return { ...current, selectedSquare: null, legalMovesForSelected: [] };
    }
    const protectedEnemy = protectedPieceIdsForOwner(
      current.shieldedPieceId,
      current.shieldedOwner,
      opponentColor(activeColor)
    );
    const legal = ChessEngine.getLegalMovesForSquare(
      current.board,
      square,
      current.castlingRights,
      current.enPassantTarget,
      current.enPassantOwner,
      activeExcludedPieceIds(current),
      protectedEnemy
    );
    return { ...current, selectedSquare: square, legalMovesForSelected: legal };
  }

  if (selected) {
    return { ...current, selectedSquare: null, legalMovesForSelected: [] };
  }
  return current;
}

export function executeHijackMove(
  current: GameUiState,
  move: ChessMove,
  ctx: TransitionContext
): GameUiState {
  const activePlayer = current.activePlayer;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(
    activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const oppPlayer = opponentPlayer(activePlayer);
  const enemy = opponentColor(activeColor);

  const result = ChessEngine.applyMove(current.board, move, current.castlingRights);
  const animatedPiece: ChessPiece = move.promotion
    ? { ...result.movedPiece, type: move.promotion }
    : result.movedPiece;

  let rookPiece: ChessPiece | null = null;
  let rookFrom: Square | null = null;
  let rookTo: Square | null = null;
  if (move.isCastlingKingside) {
    rookPiece = current.board[move.from.row][7];
    rookFrom = { row: move.from.row, col: 7 };
    rookTo = { row: move.from.row, col: 5 };
  } else if (move.isCastlingQueenside) {
    rookPiece = current.board[move.from.row][0];
    rookFrom = { row: move.from.row, col: 0 };
    rookTo = { row: move.from.row, col: 3 };
  }

  const moveAnim: MoveAnimationEvent = {
    id: ctx.nextAnimId(),
    piece: animatedPiece,
    from: move.from,
    to: move.to,
    isCapture: result.capturedPiece !== null,
    secondaryPiece: rookPiece,
    secondaryFrom: rookFrom,
    secondaryTo: rookTo,
  };

  const capturedFrozen =
    result.capturedPiece !== null && result.capturedPiece.id === current.frozenPieceId;
  const keptFrozenId = capturedFrozen ? null : current.frozenPieceId;
  const keptFrozenOwner = capturedFrozen ? null : current.frozenOwner;
  const nextTurnFrozenId = keptFrozenOwner === activeColor ? null : keptFrozenId;
  const nextTurnFrozenOwner = keptFrozenOwner === activeColor ? null : keptFrozenOwner;

  const updatedCaptured = {
    WHITE: [...(current.capturedPieces.WHITE ?? [])],
    BLACK: [...(current.capturedPieces.BLACK ?? [])],
  };
  if (result.capturedPiece) {
    const col = result.capturedPiece.color;
    updatedCaptured[col] = [...updatedCaptured[col], result.capturedPiece].sort(
      (a, b) => PIECE_META[b.type].materialValue - PIECE_META[a.type].materialValue
    );
  }

  const nextShield = nextTurnShield(
    current.shieldedPieceId,
    current.shieldedOwner,
    activeColor,
    new Set(result.capturedPiece ? [result.capturedPiece.id] : [])
  );
  const opponentProtected = protectedPieceIdsForOwner(nextShield.id, nextShield.owner, activeColor);

  const enemyInCheck = ChessEngine.isKingInCheck(result.board, enemy);
  const enemyHasMove = ChessEngine.hasAnyLegalMove(
    result.board,
    enemy,
    result.castlingRights,
    result.enPassantTarget,
    result.enPassantOwner,
    new Set(),
    opponentProtected
  );

  let logs = prependLog(
    current.gameLog,
    `${activeLabel} hijacked ${colorDisplayName(enemy)}: ${result.notation}`,
    'HIJACK',
    ctx
  );

  if (!enemyHasMove) {
    const outcome: GameOutcome = enemyInCheck
      ? { kind: 'CHECKMATE', winnerPlayer: activePlayer, winnerColor: activeColor }
      : { kind: 'STALEMATE' };
    const endMessage =
      outcome.kind === 'CHECKMATE'
        ? `Checkmate! ${activeLabel} (${colorDisplayName(activeColor)}) wins!`
        : `Stalemate! ${getActorName(oppPlayer, current.gameMode, ctx.isOnlineMultiplayer, ctx.localPlayer)} (${colorDisplayName(enemy)}) has no legal moves.`;
    logs = prependLog(logs, endMessage, 'GAME_OVER', ctx);
    ctx.onOutcomeRecorded?.(outcome);
    return {
      ...current,
      board: result.board,
      phase: 'GAME_OVER',
      movesRemaining: 0,
      movedPieceIdsThisTurn: [],
      frozenPieceId: null,
      frozenOwner: null,
      validFreezeSquares: [],
      shieldedPieceId: null,
      shieldedOwner: null,
      selectedSquare: null,
      legalMovesForSelected: [],
      capturedPieces: updatedCaptured,
      lastMoveFrom: move.from,
      lastMoveTo: move.to,
      lastRevivedSquare: null,
      checkedKingSquare: enemyInCheck ? ChessEngine.findKing(result.board, enemy) : null,
      pendingPromotion: null,
      noticeBanner: { id: ctx.nextNoticeId(), message: endMessage, isAlert: enemyInCheck },
      moveAnimationEvent: moveAnim,
      reviveAnimationEvent: null,
      gameLog: logs,
      outcome,
      castlingRights: result.castlingRights,
      enPassantTarget: result.enPassantTarget,
      enPassantOwner: result.enPassantOwner,
    };
  }

  if (enemyInCheck) {
    logs = prependLog(logs, `Check on ${colorDisplayName(enemy)}!`, 'CHECK', ctx);
  }

  return {
    ...current,
    board: result.board,
    activePlayer: oppPlayer,
    phase: 'AWAITING_DRAW',
    movesRemaining: 0,
    movedPieceIdsThisTurn: [],
    frozenPieceId: nextTurnFrozenId,
    frozenOwner: nextTurnFrozenOwner,
    validFreezeSquares: [],
    shieldedPieceId: nextShield.id,
    shieldedOwner: nextShield.owner,
    selectedSquare: null,
    legalMovesForSelected: [],
    capturedPieces: updatedCaptured,
    lastMoveFrom: move.from,
    lastMoveTo: move.to,
    lastRevivedSquare: null,
    checkedKingSquare: enemyInCheck ? ChessEngine.findKing(result.board, enemy) : null,
    pendingPromotion: null,
    noticeBanner: enemyInCheck
      ? { id: ctx.nextNoticeId(), message: `Check on ${colorDisplayName(enemy)}!`, isAlert: true }
      : null,
    moveAnimationEvent: moveAnim,
    reviveAnimationEvent: null,
    gameLog: logs,
    castlingRights: result.castlingRights,
    enPassantTarget: result.enPassantTarget,
    enPassantOwner: result.enPassantOwner,
  };
}

export function performPromotionSelected(
  current: GameUiState,
  pieceType: PieceType,
  ctx: TransitionContext
): GameUiState {
  const pending = current.pendingPromotion;
  if (!pending) return current;
  const activeColor = getActiveColor(current);
  const enemy = opponentColor(activeColor);

  if (current.phase === 'HIJACKING') {
    const enemyExcluded = new Set<number>(
      current.frozenPieceId !== null && current.frozenOwner === enemy ? [current.frozenPieceId] : []
    );
    const protectedOwn = protectedPieceIdsForOwner(
      current.shieldedPieceId,
      current.shieldedOwner,
      activeColor
    );
    const move = ChessEngine.getHijackMovesForSquare(
      current.board,
      pending.from,
      activeColor,
      current.castlingRights,
      current.enPassantTarget,
      current.enPassantOwner,
      enemyExcluded,
      protectedOwn
    ).find((m) => sameSquare(m.to, pending.to) && m.promotion === pieceType);
    if (!move) return current;
    return executeHijackMove({ ...current, pendingPromotion: null }, move, ctx);
  }

  const protectedEnemy = protectedPieceIdsForOwner(
    current.shieldedPieceId,
    current.shieldedOwner,
    enemy
  );
  const move = ChessEngine.getLegalMovesForSquare(
    current.board,
    pending.from,
    current.castlingRights,
    current.enPassantTarget,
    current.enPassantOwner,
    activeExcludedPieceIds(current),
    protectedEnemy
  ).find((m) => sameSquare(m.to, pending.to) && m.promotion === pieceType);
  if (!move) return current;
  return executeChessMove({ ...current, pendingPromotion: null }, move, ctx);
}

export function performForfeit(
  current: GameUiState,
  forfeitingPlayer: PlayerId,
  ctx: TransitionContext
): GameUiState {
  if (current.outcome !== null) return current;
  const winnerPlayer = opponentPlayer(forfeitingPlayer);
  const winnerColor = current.playerColors[winnerPlayer];
  const outcome: GameOutcome = {
    kind: 'FORFEIT',
    winnerPlayer,
    winnerColor,
    forfeitingPlayer,
  };
  const forfeiterName = getActorName(
    forfeitingPlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const winnerName = getActorName(
    winnerPlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const msg = `${forfeiterName} forfeited — ${winnerName} (${colorDisplayName(winnerColor)}) wins!`;
  const logs = prependLog(current.gameLog, msg, 'GAME_OVER', ctx);
  ctx.onOutcomeRecorded?.(outcome);

  return {
    ...current,
    phase: 'GAME_OVER',
    outcome,
    movesRemaining: 0,
    revivesRemaining: 0,
    selectedSquare: null,
    legalMovesForSelected: [],
    pendingPromotion: null,
    noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: true },
    gameLog: logs,
  };
}

export function performCapturedPieceSelected(
  current: GameUiState,
  pieceId: number,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'REVIVING' || current.outcome !== null) return current;
  const activeColor = getActiveColor(current);
  const ownCap = current.capturedPieces[activeColor] ?? [];
  const piece = ownCap.find((p) => p.id === pieceId);
  if (!piece) return current;
  const freezeExcluded = new Set<number>(
    current.frozenPieceId !== null && current.frozenOwner === activeColor
      ? [current.frozenPieceId]
      : []
  );
  const validSquares = ChessEngine.getValidReviveSquares(
    current.board,
    piece,
    activeColor,
    freezeExcluded
  );
  return {
    ...current,
    selectedRevivePieceId: piece.id,
    validReviveSquares: validSquares,
    noticeBanner:
      validSquares.length === 0
        ? {
            id: ctx.nextNoticeId(),
            message: `${PIECE_META[piece.type].displayName} cannot safely replace any of your Pawns right now. Pick another piece.`,
            isAlert: true,
          }
        : null,
  };
}

export function handleRevivingSquareTap(
  current: GameUiState,
  square: Square,
  ctx: TransitionContext
): GameUiState {
  const activePlayer = current.activePlayer;
  const activeColor = getActiveColor(current);
  const activeLabel = getActorName(
    activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );
  const oppPlayer = opponentPlayer(activePlayer);
  const enemy = opponentColor(activeColor);

  const ownCap = current.capturedPieces[activeColor] ?? [];
  const isSpawnPawnMode = ownCap.length === 0 || current.selectedRevivePieceId === null;

  if (!current.validReviveSquares.some((s) => sameSquare(s, square))) {
    const message = isSpawnPawnMode
      ? 'Tap a green empty square to place your new Pawn.'
      : 'Tap one of your green-highlighted Pawns to turn it into your revived piece.';
    return {
      ...current,
      noticeBanner: { id: ctx.nextNoticeId(), message, isAlert: true },
    };
  }

  let newPiece: ChessPiece;
  const updatedCaptured = {
    WHITE: [...(current.capturedPieces.WHITE ?? [])],
    BLACK: [...(current.capturedPieces.BLACK ?? [])],
  };
  let logText: string;

  if (isSpawnPawnMode) {
    newPiece = { id: computeNextPieceId(current), type: 'PAWN', color: activeColor };
    logText = `${activeLabel} placed a new Pawn on ${squareToAlgebraic(square)} (Revive).`;
  } else {
    const pieceToRevive = ownCap.find((p) => p.id === current.selectedRevivePieceId);
    if (!pieceToRevive) return current;
    newPiece = pieceToRevive;
    updatedCaptured[activeColor] = ownCap.filter((p) => p.id !== pieceToRevive.id);
    logText = `${activeLabel} turned Pawn on ${squareToAlgebraic(square)} into revived ${PIECE_META[pieceToRevive.type].displayName}.`;
  }

  const newBoard = ChessEngine.placePiece(current.board, square, newPiece);
  const reviveAnim: ReviveAnimationEvent = {
    id: ctx.nextAnimId(),
    piece: newPiece,
    square,
  };
  let logs = prependLog(current.gameLog, logText, 'REVIVE', ctx);

  const nextShield = nextTurnShield(current.shieldedPieceId, current.shieldedOwner, activeColor);
  const opponentProtected = protectedPieceIdsForOwner(nextShield.id, nextShield.owner, activeColor);

  const enemyHasMove = ChessEngine.hasAnyLegalMove(
    newBoard,
    enemy,
    current.castlingRights,
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
      board: newBoard,
      phase: 'GAME_OVER',
      revivesRemaining: 0,
      majorRevivedThisTurn: false,
      frozenPieceId: null,
      frozenOwner: null,
      shieldedPieceId: null,
      shieldedOwner: null,
      selectedRevivePieceId: null,
      validReviveSquares: [],
      capturedPieces: updatedCaptured,
      lastRevivedSquare: square,
      noticeBanner: { id: ctx.nextNoticeId(), message: msg, isAlert: false },
      moveAnimationEvent: null,
      reviveAnimationEvent: reviveAnim,
      gameLog: logs,
      outcome: { kind: 'STALEMATE' },
      enPassantTarget: null,
      enPassantOwner: null,
    };
  }

  const nextTurnFrozenId = current.frozenOwner === activeColor ? null : current.frozenPieceId;
  const nextTurnFrozenOwner = current.frozenOwner === activeColor ? null : current.frozenOwner;

  return {
    ...current,
    board: newBoard,
    activePlayer: oppPlayer,
    phase: 'AWAITING_DRAW',
    revivesRemaining: 0,
    majorRevivedThisTurn: false,
    frozenPieceId: nextTurnFrozenId,
    frozenOwner: nextTurnFrozenOwner,
    shieldedPieceId: nextShield.id,
    shieldedOwner: nextShield.owner,
    selectedRevivePieceId: null,
    validReviveSquares: [],
    capturedPieces: updatedCaptured,
    lastRevivedSquare: square,
    noticeBanner: { id: ctx.nextNoticeId(), message: logText, isAlert: false },
    moveAnimationEvent: null,
    reviveAnimationEvent: reviveAnim,
    gameLog: logs,
    enPassantTarget: null,
    enPassantOwner: null,
  };
}

export function describeGameModeLabel(mode: GameMode, isOnline: boolean): string {
  if (isOnline) return 'Online Multiplayer';
  return GAME_MODE_META[mode].label;
}

export function performChooseDrawTwoCard(
  current: GameUiState,
  chosenIndex: number,
  ctx: TransitionContext
): GameUiState {
  if (current.phase !== 'DRAW_TWO_CHOOSING' || !current.drawTwoPendingCards) return current;
  const chosenCard = current.drawTwoPendingCards[chosenIndex] ?? current.drawTwoPendingCards[0];
  const otherIndex = chosenIndex === 0 ? 1 : 0;
  const discardedCard = current.drawTwoPendingCards[otherIndex];

  const newDiscard = [...current.discardPile, discardedCard];
  const activeLabel = getActorName(
    current.activePlayer,
    current.gameMode,
    ctx.isOnlineMultiplayer,
    ctx.localPlayer
  );

  const logs = prependLog(
    current.gameLog,
    `${activeLabel} chose ${cardTitle(chosenCard.type)} (${cardSubtitle(chosenCard.type)}) from Draw Two!`,
    'CARD',
    ctx
  );

  const stateWithChosen: GameUiState = {
    ...current,
    discardPile: newDiscard,
    drawTwoPendingCards: null,
    gameLog: logs,
  };

  const drawPileWithCard = [...current.drawPile, chosenCard];
  return performDrawCard({ ...stateWithChosen, drawPile: drawPileWithCard }, ctx);
}
