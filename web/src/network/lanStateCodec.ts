import {
  ALL_SPECIAL_CARDS,
  Board,
  CastlingRights,
  ChessMove,
  ChessPiece,
  GameOutcome,
  GameUiState,
  LanAction,
  LogCategory,
  LogEntry,
  MoveAnimationEvent,
  NoticeBanner,
  opponentColor,
  PendingPromotion,
  PieceColor,
  PieceType,
  PlayerId,
  ReviveAnimationEvent,
  SpecialCardKey,
  Square,
  TurnPhase,
  UnoCard,
  UnoCardType,
  UnoColor,
} from '../model/gameModels';
import * as ChessEngine from '../engine/chessEngine';

const PREFIX_ACTION = 'ACT:';
const PREFIX_STATE = 'STATE:';

/* eslint-disable @typescript-eslint/no-explicit-any */

function encodeSquare(sq: Square): { r: number; c: number } {
  return { r: sq.row, c: sq.col };
}

function decodeSquare(obj: any): Square | null {
  if (!obj || typeof obj.r !== 'number' || typeof obj.c !== 'number') return null;
  return { row: obj.r, col: obj.c };
}

function encodePiece(p: ChessPiece): { id: number; t: PieceType; c: PieceColor } {
  return { id: p.id, t: p.type, c: p.color };
}

function decodePiece(obj: any): ChessPiece | null {
  if (!obj || typeof obj.id !== 'number') return null;
  return { id: obj.id, type: obj.t as PieceType, color: obj.c as PieceColor };
}

function encodeCard(card: UnoCard): { id: number; col: UnoColor; tk: string; m?: number } {
  const tk =
    card.type.kind === 'NUMBER'
      ? 'NUM'
      : card.type.kind === 'REVIVE'
        ? 'REVIVE'
        : card.type.kind === 'FREEZE'
          ? 'FREEZE'
          : card.type.kind === 'MERGE'
            ? 'MERGE'
            : card.type.kind === 'SACRIFICE'
              ? 'SACRIFICE'
              : card.type.kind === 'HIJACK'
                ? 'HIJACK'
                : card.type.kind === 'PROMOTE'
                  ? 'PAWN_PROMOTE'
                  : card.type.kind === 'SUICIDE'
                    ? 'SUICIDE'
                    : card.type.kind === 'SHIELD'
                      ? 'SHIELD'
                      : card.type.kind === 'SWAP'
                        ? 'SWAP'
                        : card.type.kind === 'CLONE'
                          ? 'CLONE'
                          : card.type.kind === 'SNIPER'
                            ? 'SNIPER'
                            : card.type.kind === 'PORTAL'
                              ? 'PORTAL'
                              : card.type.kind === 'TRAP'
                                ? 'TRAP'
                                : card.type.kind === 'DRAW_TWO'
                                  ? 'DRAW_TWO'
                                  : 'WILD';

  const base: { id: number; col: UnoColor; tk: string; m?: number } = {
    id: card.id,
    col: card.color,
    tk,
  };
  if (card.type.kind === 'NUMBER') {
    base.m = card.type.moves;
  }
  return base;
}

function decodeCard(obj: any): UnoCard | null {
  if (!obj || typeof obj.id !== 'number') return null;
  const color = obj.col as UnoColor;
  let type: UnoCardType;
  switch (obj.tk) {
    case 'NUM':
      type = { kind: 'NUMBER', moves: typeof obj.m === 'number' ? obj.m : 1 };
      break;
    case 'REVIVE':
      type = { kind: 'REVIVE' };
      break;
    case 'FREEZE':
      type = { kind: 'FREEZE' };
      break;
    case 'MERGE':
      type = { kind: 'MERGE' };
      break;
    case 'SACRIFICE':
      type = { kind: 'SACRIFICE' };
      break;
    case 'HIJACK':
      type = { kind: 'HIJACK' };
      break;
    case 'PAWN_PROMOTE':
      type = { kind: 'PROMOTE' };
      break;
    case 'SUICIDE':
      type = { kind: 'SUICIDE' };
      break;
    case 'SHIELD':
      type = { kind: 'SHIELD' };
      break;
    case 'SWAP':
      type = { kind: 'SWAP' };
      break;
    case 'CLONE':
      type = { kind: 'CLONE' };
      break;
    case 'WILD':
      type = { kind: 'WILD' };
      break;
    case 'SNIPER':
      type = { kind: 'SNIPER' };
      break;
    case 'PORTAL':
      type = { kind: 'PORTAL' };
      break;
    case 'TRAP':
      type = { kind: 'TRAP' };
      break;
    case 'DRAW_TWO':
      type = { kind: 'DRAW_TWO' };
      break;
    default:
      type = { kind: 'NUMBER', moves: 1 };
      break;
  }
  return { id: obj.id, color, type };
}

export function encodeAction(action: LanAction): string {
  switch (action.type) {
    case 'DRAW':
      return PREFIX_ACTION + JSON.stringify({ kind: 'DRAW' });
    case 'TAP':
      return PREFIX_ACTION + JSON.stringify({ kind: 'TAP', r: action.square.row, c: action.square.col });
    case 'PROMO':
      return PREFIX_ACTION + JSON.stringify({ kind: 'PROMOTE', type: action.pieceType });
    case 'CANCEL_PROMO':
      return PREFIX_ACTION + JSON.stringify({ kind: 'PROMOTE_CANCEL' });
    case 'REVIVE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'REVIVE_PIECE', id: action.pieceId });
    case 'CONFIRM_MERGE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'MERGE_CONFIRM', type: action.targetType });
    case 'CLEAR_MERGE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'MERGE_CLEAR' });
    case 'SKIP_MERGE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'MERGE_SKIP' });
    case 'CONFIRM_SACRIFICE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'SACRIFICE_CONFIRM', recipe: action.recipeId });
    case 'SKIP_SACRIFICE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'SACRIFICE_SKIP' });
    case 'CONFIRM_PAWN_PROMOTE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'PAWN_PROMOTE_CONFIRM', type: action.targetType });
    case 'CLEAR_SWAP':
      return PREFIX_ACTION + JSON.stringify({ kind: 'SWAP_CLEAR' });
    case 'SKIP_SWAP':
      return PREFIX_ACTION + JSON.stringify({ kind: 'SWAP_SKIP' });
    case 'CLEAR_CLONE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'CLONE_CLEAR' });
    case 'SKIP_CLONE':
      return PREFIX_ACTION + JSON.stringify({ kind: 'CLONE_SKIP' });
    case 'SELECT_WILD':
      return PREFIX_ACTION + JSON.stringify({ kind: 'WILD_SELECT', effect: action.effect });
    case 'CHOOSE_DRAW_TWO':
      return PREFIX_ACTION + JSON.stringify({ kind: 'DRAW_TWO_CHOOSE', index: action.index });
    case 'SKIP_SNIPER':
      return PREFIX_ACTION + JSON.stringify({ kind: 'SNIPER_SKIP' });
    case 'SKIP_PORTAL':
      return PREFIX_ACTION + JSON.stringify({ kind: 'PORTAL_SKIP' });
    case 'SKIP_TRAP':
      return PREFIX_ACTION + JSON.stringify({ kind: 'TRAP_SKIP' });
    case 'FORFEIT':
      return PREFIX_ACTION + JSON.stringify({ kind: 'FORFEIT' });
    case 'NEW_GAME':
      return PREFIX_ACTION + JSON.stringify({ kind: 'RESET' });
  }
}

export function decodeAction(line: string): LanAction | null {
  if (!line.startsWith(PREFIX_ACTION)) return null;
  try {
    const obj = JSON.parse(line.slice(PREFIX_ACTION.length));
    switch (obj.kind) {
      case 'DRAW':
        return { type: 'DRAW' };
      case 'TAP':
        return { type: 'TAP', square: { row: obj.r, col: obj.c } };
      case 'PROMOTE':
        return { type: 'PROMO', pieceType: obj.type as PieceType };
      case 'PROMOTE_CANCEL':
        return { type: 'CANCEL_PROMO' };
      case 'REVIVE_PIECE':
        return { type: 'REVIVE', pieceId: obj.id };
      case 'MERGE_CONFIRM':
        return { type: 'CONFIRM_MERGE', targetType: obj.type as PieceType };
      case 'MERGE_CLEAR':
        return { type: 'CLEAR_MERGE' };
      case 'MERGE_SKIP':
        return { type: 'SKIP_MERGE' };
      case 'SACRIFICE_CONFIRM':
        return { type: 'CONFIRM_SACRIFICE', recipeId: obj.recipe };
      case 'SACRIFICE_SKIP':
        return { type: 'SKIP_SACRIFICE' };
      case 'PAWN_PROMOTE_CONFIRM':
        return { type: 'CONFIRM_PAWN_PROMOTE', targetType: obj.type as PieceType };
      case 'SWAP_CLEAR':
        return { type: 'CLEAR_SWAP' };
      case 'SWAP_SKIP':
        return { type: 'SKIP_SWAP' };
      case 'CLONE_CLEAR':
        return { type: 'CLEAR_CLONE' };
      case 'CLONE_SKIP':
        return { type: 'SKIP_CLONE' };
      case 'WILD_SELECT':
        return { type: 'SELECT_WILD', effect: obj.effect as SpecialCardKey };
      case 'DRAW_TWO_CHOOSE':
        return { type: 'CHOOSE_DRAW_TWO', index: typeof obj.index === 'number' ? obj.index : 0 };
      case 'SNIPER_SKIP':
        return { type: 'SKIP_SNIPER' };
      case 'PORTAL_SKIP':
        return { type: 'SKIP_PORTAL' };
      case 'TRAP_SKIP':
        return { type: 'SKIP_TRAP' };
      case 'FORFEIT':
        return { type: 'FORFEIT' };
      case 'RESET':
        return { type: 'NEW_GAME' };
      default:
        return null;
    }
  } catch {
    return null;
  }
}

export function encodeState(state: GameUiState): string {
  const payload: any = {
    board: state.board.map((row) => row.map((p) => (p ? encodePiece(p) : null))),
    p1Color: state.playerColors.PLAYER_1,
    p2Color: state.playerColors.PLAYER_2,
    activePlayer: state.activePlayer,
    boardFlipped: false,
    phase: state.phase,
    drawPile: state.drawPile.map(encodeCard),
    discardPile: state.discardPile.map(encodeCard),
    activeCard: state.activeCard ? encodeCard(state.activeCard) : null,
    drawRevision: state.drawRevision,
    totalMovesForCard: state.totalMovesForCard,
    movesRemaining: state.movesRemaining,
    revivesTotalForCard: state.revivesTotalForCard,
    revivesRemaining: state.revivesRemaining,
    majorRevivedThisTurn: state.majorRevivedThisTurn,
    movedPieceIds: state.movedPieceIdsThisTurn,
    frozenPieceId: state.frozenPieceId,
    frozenOwner: state.frozenOwner,
    validFreezeSquares: state.validFreezeSquares.map(encodeSquare),
    selectedMergeSquares: state.selectedMergeSquares.map(encodeSquare),
    selectedSacrificeSquare: state.selectedSacrificeSquare
      ? encodeSquare(state.selectedSacrificeSquare)
      : null,
    validSacrificeSquares: state.validSacrificeSquares.map(encodeSquare),
    selectedPromotePawnSquare: state.selectedPromotePawnSquare
      ? encodeSquare(state.selectedPromotePawnSquare)
      : null,
    validPromotePawnSquares: state.validPromotePawnSquares.map(encodeSquare),
    validSuicideSquares: state.validSuicideSquares.map(encodeSquare),
    traps: state.traps.map((t) => ({ id: t.id, r: t.row, c: t.col, owner: t.owner })),
    validTrapSquares: state.validTrapSquares.map(encodeSquare),
    selectedSniperSquare: state.selectedSniperSquare ? encodeSquare(state.selectedSniperSquare) : null,
    validSniperSources: state.validSniperSources.map(encodeSquare),
    validSniperTargets: state.validSniperTargets.map(encodeSquare),
    selectedPortalSourceSquare: state.selectedPortalSourceSquare
      ? encodeSquare(state.selectedPortalSourceSquare)
      : null,
    validPortalSources: state.validPortalSources.map(encodeSquare),
    validPortalDestinations: state.validPortalDestinations.map(encodeSquare),
    drawTwoPendingCards: state.drawTwoPendingCards
      ? [encodeCard(state.drawTwoPendingCards[0]), encodeCard(state.drawTwoPendingCards[1])]
      : null,
    shieldedPieceId: state.shieldedPieceId,
    shieldedOwner: state.shieldedOwner,
    validShieldSquares: state.validShieldSquares.map(encodeSquare),
    selectedSwapSquare: state.selectedSwapSquare ? encodeSquare(state.selectedSwapSquare) : null,
    validSwapSquares: state.validSwapSquares.map(encodeSquare),
    selectedCloneSourceSquare: state.selectedCloneSourceSquare
      ? encodeSquare(state.selectedCloneSourceSquare)
      : null,
    validCloneSquares: state.validCloneSquares.map(encodeSquare),
    availableWildEffects: state.availableWildEffects,
    enabledSpecialCards: state.enabledSpecialCards,
    selectedSquare: state.selectedSquare ? encodeSquare(state.selectedSquare) : null,
    selectedRevivePieceId: state.selectedRevivePieceId,
    validReviveSquares: state.validReviveSquares.map(encodeSquare),
    capturedWhite: (state.capturedPieces.WHITE ?? []).map(encodePiece),
    capturedBlack: (state.capturedPieces.BLACK ?? []).map(encodePiece),
    lastMoveFrom: state.lastMoveFrom ? encodeSquare(state.lastMoveFrom) : null,
    lastMoveTo: state.lastMoveTo ? encodeSquare(state.lastMoveTo) : null,
    lastRevivedSquare: state.lastRevivedSquare ? encodeSquare(state.lastRevivedSquare) : null,
    checkedKingSquare: state.checkedKingSquare ? encodeSquare(state.checkedKingSquare) : null,
    pendingPromotion: state.pendingPromotion
      ? {
          from: encodeSquare(state.pendingPromotion.from),
          to: encodeSquare(state.pendingPromotion.to),
        }
      : null,
    noticeBanner: state.noticeBanner
      ? {
          id: state.noticeBanner.id,
          message: state.noticeBanner.message,
          isAlert: state.noticeBanner.isAlert,
        }
      : null,
    moveAnim: state.moveAnimationEvent
      ? {
          id: state.moveAnimationEvent.id,
          piece: encodePiece(state.moveAnimationEvent.piece),
          from: encodeSquare(state.moveAnimationEvent.from),
          to: encodeSquare(state.moveAnimationEvent.to),
          isCapture: state.moveAnimationEvent.isCapture,
          secPiece: state.moveAnimationEvent.secondaryPiece
            ? encodePiece(state.moveAnimationEvent.secondaryPiece)
            : null,
          secFrom: state.moveAnimationEvent.secondaryFrom
            ? encodeSquare(state.moveAnimationEvent.secondaryFrom)
            : null,
          secTo: state.moveAnimationEvent.secondaryTo
            ? encodeSquare(state.moveAnimationEvent.secondaryTo)
            : null,
        }
      : null,
    reviveAnim: state.reviveAnimationEvent
      ? {
          id: state.reviveAnimationEvent.id,
          piece: encodePiece(state.reviveAnimationEvent.piece),
          square: encodeSquare(state.reviveAnimationEvent.square),
        }
      : null,
    log: state.gameLog.slice(0, 30).map((e) => ({ id: e.id, msg: e.message, cat: e.category })),
    outcome: (() => {
      if (!state.outcome) return null;
      switch (state.outcome.kind) {
        case 'CHECKMATE':
          return {
            kind: 'CHECKMATE',
            player: state.outcome.winnerPlayer,
            color: state.outcome.winnerColor,
          };
        case 'FORFEIT':
          return {
            kind: 'FORFEIT',
            forfeiter: state.outcome.forfeitingPlayer,
            player: state.outcome.winnerPlayer,
            color: state.outcome.winnerColor,
          };
        case 'SUICIDE':
          return {
            kind: 'SUICIDE',
            loser: state.outcome.losingPlayer,
            player: state.outcome.winnerPlayer,
            color: state.outcome.winnerColor,
          };
        case 'STALEMATE':
          return { kind: 'STALEMATE' };
      }
    })(),
    castling: {
      wk: state.castlingRights.whiteKingside,
      wq: state.castlingRights.whiteQueenside,
      bk: state.castlingRights.blackKingside,
      bq: state.castlingRights.blackQueenside,
    },
    epTarget: state.enPassantTarget ? encodeSquare(state.enPassantTarget) : null,
    epOwner: state.enPassantOwner,
    pityP1: state.movePityCount.PLAYER_1 ?? 0,
    pityP2: state.movePityCount.PLAYER_2 ?? 0,
  };

  return PREFIX_STATE + JSON.stringify(payload);
}

export function decodeState(line: string, localTemplate: GameUiState): GameUiState | null {
  if (!line.startsWith(PREFIX_STATE)) return null;
  try {
    const obj = JSON.parse(line.slice(PREFIX_STATE.length));
    const board: Board = (obj.board as any[][]).map((row) =>
      row.map((cell) => (cell ? decodePiece(cell) : null))
    );
    const decodeSquares = (arr: any): Square[] =>
      Array.isArray(arr) ? arr.map(decodeSquare).filter((s): s is Square => s !== null) : [];

    const playerColors: Record<PlayerId, PieceColor> = {
      PLAYER_1: obj.p1Color as PieceColor,
      PLAYER_2: obj.p2Color as PieceColor,
    };
    const activePlayer = obj.activePlayer as PlayerId;
    const phase = obj.phase as TurnPhase;
    const castlingRights: CastlingRights = {
      whiteKingside: Boolean(obj.castling?.wk),
      whiteQueenside: Boolean(obj.castling?.wq),
      blackKingside: Boolean(obj.castling?.bk),
      blackQueenside: Boolean(obj.castling?.bq),
    };
    const epTarget = decodeSquare(obj.epTarget);
    const epOwner = obj.epOwner ? (obj.epOwner as PieceColor) : null;
    const movedPieceIds: number[] = Array.isArray(obj.movedPieceIds) ? obj.movedPieceIds : [];
    const frozenPieceId = typeof obj.frozenPieceId === 'number' ? obj.frozenPieceId : null;
    const frozenOwner = obj.frozenOwner ? (obj.frozenOwner as PieceColor) : null;
    const shieldedPieceId = typeof obj.shieldedPieceId === 'number' ? obj.shieldedPieceId : null;
    const shieldedOwner = obj.shieldedOwner ? (obj.shieldedOwner as PieceColor) : null;
    const selectedSquare = decodeSquare(obj.selectedSquare);

    // Recompute legal moves for selected square locally (matches Android LanStateCodec.kt)
    let legalMoves: ChessMove[] = [];
    if (selectedSquare) {
      const activeColor = playerColors[activePlayer];
      const enemy = opponentColor(activeColor);
      if (phase === 'MOVING') {
        const excluded = new Set<number>(movedPieceIds);
        if (frozenPieceId !== null && frozenOwner === activeColor) excluded.add(frozenPieceId);
        const protectedEnemy = new Set<number>(
          shieldedPieceId !== null && shieldedOwner === enemy ? [shieldedPieceId] : []
        );
        legalMoves = ChessEngine.getLegalMovesForSquare(
          board,
          selectedSquare,
          castlingRights,
          epTarget,
          epOwner,
          excluded,
          protectedEnemy
        );
      } else if (phase === 'HIJACKING') {
        const enemyExcluded = new Set<number>(
          frozenPieceId !== null && frozenOwner === enemy ? [frozenPieceId] : []
        );
        const protectedOwn = new Set<number>(
          shieldedPieceId !== null && shieldedOwner === activeColor ? [shieldedPieceId] : []
        );
        legalMoves = ChessEngine.getHijackMovesForSquare(
          board,
          selectedSquare,
          activeColor,
          castlingRights,
          epTarget,
          epOwner,
          enemyExcluded,
          protectedOwn
        );
      }
    }

    let outcome: GameOutcome | null = null;
    if (obj.outcome) {
      if (obj.outcome.kind === 'CHECKMATE') {
        outcome = {
          kind: 'CHECKMATE',
          winnerPlayer: obj.outcome.player as PlayerId,
          winnerColor: obj.outcome.color as PieceColor,
        };
      } else if (obj.outcome.kind === 'FORFEIT') {
        outcome = {
          kind: 'FORFEIT',
          forfeitingPlayer: obj.outcome.forfeiter as PlayerId,
          winnerPlayer: obj.outcome.player as PlayerId,
          winnerColor: obj.outcome.color as PieceColor,
        };
      } else if (obj.outcome.kind === 'SUICIDE') {
        outcome = {
          kind: 'SUICIDE',
          losingPlayer: obj.outcome.loser as PlayerId,
          winnerPlayer: obj.outcome.player as PlayerId,
          winnerColor: obj.outcome.color as PieceColor,
        };
      } else if (obj.outcome.kind === 'STALEMATE') {
        outcome = { kind: 'STALEMATE' };
      }
    }

    let moveAnimationEvent: MoveAnimationEvent | null = null;
    if (obj.moveAnim) {
      const piece = decodePiece(obj.moveAnim.piece);
      const from = decodeSquare(obj.moveAnim.from);
      const to = decodeSquare(obj.moveAnim.to);
      if (piece && from && to) {
        moveAnimationEvent = {
          id: obj.moveAnim.id,
          piece,
          from,
          to,
          isCapture: Boolean(obj.moveAnim.isCapture),
          secondaryPiece: obj.moveAnim.secPiece ? decodePiece(obj.moveAnim.secPiece) : null,
          secondaryFrom: obj.moveAnim.secFrom ? decodeSquare(obj.moveAnim.secFrom) : null,
          secondaryTo: obj.moveAnim.secTo ? decodeSquare(obj.moveAnim.secTo) : null,
        };
      }
    }

    let reviveAnimationEvent: ReviveAnimationEvent | null = null;
    if (obj.reviveAnim) {
      const piece = decodePiece(obj.reviveAnim.piece);
      const square = decodeSquare(obj.reviveAnim.square);
      if (piece && square) {
        reviveAnimationEvent = { id: obj.reviveAnim.id, piece, square };
      }
    }

    let pendingPromotion: PendingPromotion | null = null;
    if (obj.pendingPromotion) {
      const from = decodeSquare(obj.pendingPromotion.from);
      const to = decodeSquare(obj.pendingPromotion.to);
      if (from && to) pendingPromotion = { from, to };
    }

    const noticeBanner: NoticeBanner | null = obj.noticeBanner
      ? {
          id: obj.noticeBanner.id,
          message: obj.noticeBanner.message,
          isAlert: Boolean(obj.noticeBanner.isAlert),
        }
      : null;

    return {
      ...localTemplate,
      board,
      activePlayer,
      playerColors,
      phase,
      drawPile: Array.isArray(obj.drawPile)
        ? obj.drawPile.map(decodeCard).filter((c: UnoCard | null): c is UnoCard => c !== null)
        : [],
      discardPile: Array.isArray(obj.discardPile)
        ? obj.discardPile.map(decodeCard).filter((c: UnoCard | null): c is UnoCard => c !== null)
        : [],
      activeCard: obj.activeCard ? decodeCard(obj.activeCard) : null,
      drawRevision: obj.drawRevision ?? 0,
      totalMovesForCard: obj.totalMovesForCard ?? 0,
      movesRemaining: obj.movesRemaining ?? 0,
      revivesTotalForCard: obj.revivesTotalForCard ?? 0,
      revivesRemaining: obj.revivesRemaining ?? 0,
      majorRevivedThisTurn: Boolean(obj.majorRevivedThisTurn),
      movedPieceIdsThisTurn: movedPieceIds,
      frozenPieceId,
      frozenOwner,
      validFreezeSquares: decodeSquares(obj.validFreezeSquares),
      shieldedPieceId,
      shieldedOwner,
      validShieldSquares: decodeSquares(obj.validShieldSquares),
      selectedSwapSquare: decodeSquare(obj.selectedSwapSquare),
      validSwapSquares: decodeSquares(obj.validSwapSquares),
      selectedCloneSourceSquare: decodeSquare(obj.selectedCloneSourceSquare),
      validCloneSquares: decodeSquares(obj.validCloneSquares),
      availableWildEffects: Array.isArray(obj.availableWildEffects)
        ? (obj.availableWildEffects as SpecialCardKey[])
        : [],
      enabledSpecialCards: Array.isArray(obj.enabledSpecialCards)
        ? (obj.enabledSpecialCards as SpecialCardKey[])
        : ALL_SPECIAL_CARDS,
      selectedMergeSquares: decodeSquares(obj.selectedMergeSquares),
      selectedSacrificeSquare: decodeSquare(obj.selectedSacrificeSquare),
      validSacrificeSquares: decodeSquares(obj.validSacrificeSquares),
      selectedPromotePawnSquare: decodeSquare(obj.selectedPromotePawnSquare),
      validPromotePawnSquares: decodeSquares(obj.validPromotePawnSquares),
      validSuicideSquares: decodeSquares(obj.validSuicideSquares),
      traps: Array.isArray(obj.traps)
        ? obj.traps.map((t: any) => ({
            id: typeof t.id === 'number' ? t.id : Date.now(),
            row: t.r,
            col: t.c,
            owner: t.owner as PieceColor,
          }))
        : [],
      validTrapSquares: decodeSquares(obj.validTrapSquares),
      selectedSniperSquare: decodeSquare(obj.selectedSniperSquare),
      validSniperSources: decodeSquares(obj.validSniperSources),
      validSniperTargets: decodeSquares(obj.validSniperTargets),
      selectedPortalSourceSquare: decodeSquare(obj.selectedPortalSourceSquare),
      validPortalSources: decodeSquares(obj.validPortalSources),
      validPortalDestinations: decodeSquares(obj.validPortalDestinations),
      drawTwoPendingCards:
        Array.isArray(obj.drawTwoPendingCards) && obj.drawTwoPendingCards.length === 2
          ? [decodeCard(obj.drawTwoPendingCards[0])!, decodeCard(obj.drawTwoPendingCards[1])!]
          : null,
      selectedSquare,
      legalMovesForSelected: legalMoves,
      selectedRevivePieceId:
        typeof obj.selectedRevivePieceId === 'number' ? obj.selectedRevivePieceId : null,
      validReviveSquares: decodeSquares(obj.validReviveSquares),
      capturedPieces: {
        WHITE: Array.isArray(obj.capturedWhite)
          ? obj.capturedWhite
              .map(decodePiece)
              .filter((p: ChessPiece | null): p is ChessPiece => p !== null)
          : [],
        BLACK: Array.isArray(obj.capturedBlack)
          ? obj.capturedBlack
              .map(decodePiece)
              .filter((p: ChessPiece | null): p is ChessPiece => p !== null)
          : [],
      },
      lastMoveFrom: decodeSquare(obj.lastMoveFrom),
      lastMoveTo: decodeSquare(obj.lastMoveTo),
      lastRevivedSquare: decodeSquare(obj.lastRevivedSquare),
      checkedKingSquare: decodeSquare(obj.checkedKingSquare),
      pendingPromotion,
      noticeBanner,
      moveAnimationEvent,
      reviveAnimationEvent,
      gameLog: Array.isArray(obj.log)
        ? (obj.log as any[]).map(
            (e): LogEntry => ({ id: e.id, message: e.msg, category: e.cat as LogCategory })
          )
        : [],
      outcome,
      castlingRights,
      enPassantTarget: epTarget,
      enPassantOwner: epOwner,
      movePityCount: {
        PLAYER_1: typeof obj.pityP1 === 'number' ? obj.pityP1 : 0,
        PLAYER_2: typeof obj.pityP2 === 'number' ? obj.pityP2 : 0,
      },
      isAiThinking: false,
    };
  } catch {
    return null;
  }
}
