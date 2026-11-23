import {
  Board,
  ChessMove,
  ChessPiece,
  GameMode,
  GameUiState,
  getActiveColor,
  opponentColor,
  PIECE_META,
  PieceColor,
  PieceType,
  SpecialCardKey,
  Square,
} from '../model/gameModels';
import * as ChessEngine from './chessEngine';

function pickRandom<T>(arr: T[]): T {
  return arr[Math.floor(Math.random() * arr.length)];
}

function centerBonus(sq: Square): number {
  const rowDist = Math.abs(sq.row - 3.5);
  const colDist = Math.abs(sq.col - 3.5);
  return Math.max(0, 4.0 - (rowDist + colDist));
}

function evaluateBoard(board: Board, perspective: PieceColor): number {
  let score = 0;
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (!p) continue;
      const base = p.type === 'KING' ? 0 : PIECE_META[p.type].materialValue * 100;
      let pos = 0;
      if (p.type === 'PAWN') {
        const advancement = p.color === 'WHITE' ? 6 - r : r - 1;
        pos = advancement * 10 + (c >= 2 && c <= 5 ? 8 : 0);
      } else if (p.type === 'KNIGHT' || p.type === 'BISHOP') {
        pos = centerBonus({ row: r, col: c }) * 8;
      } else if (p.type === 'QUEEN') {
        pos = centerBonus({ row: r, col: c }) * 3;
      }
      const total = base + pos;
      score += p.color === perspective ? total : -total;
    }
  }
  return score;
}

export function chooseChessMove(state: GameUiState, mode: GameMode): ChessMove | null {
  const activeColor = getActiveColor(state);
  const enemy = opponentColor(activeColor);
  const excluded = new Set<number>(state.movedPieceIdsThisTurn);
  if (state.frozenPieceId !== null && state.frozenOwner === activeColor) {
    excluded.add(state.frozenPieceId);
  }
  const protectedEnemy = new Set<number>(
    state.shieldedPieceId !== null && state.shieldedOwner === enemy ? [state.shieldedPieceId] : []
  );

  const legal = ChessEngine.getAllLegalMoves(
    state.board,
    activeColor,
    state.castlingRights,
    state.enPassantTarget,
    state.enPassantOwner,
    excluded,
    protectedEnemy
  );
  if (legal.length === 0) return null;

  if (mode === 'VS_AI_EASY') {
    const captures = legal.filter(
      (m) => state.board[m.to.row][m.to.col] !== null || m.isEnPassant
    );
    if (captures.length > 0 && Math.random() < 0.45) {
      return pickRandom(captures);
    }
    return pickRandom(legal);
  }

  let bestScore = -Infinity;
  let bestMoves: ChessMove[] = [];

  for (const move of legal) {
    const applied = ChessEngine.applyMove(state.board, move, state.castlingRights);
    const enemyInCheck = ChessEngine.isKingInCheck(applied.board, enemy);
    const enemyHasMove = ChessEngine.hasAnyLegalMove(
      applied.board,
      enemy,
      applied.castlingRights,
      applied.enPassantTarget,
      applied.enPassantOwner
    );

    if (!enemyHasMove && enemyInCheck) return move;

    let score = evaluateBoard(applied.board, activeColor);
    if (applied.capturedPiece) {
      score += PIECE_META[applied.capturedPiece.type].materialValue * 45;
    }
    if (move.promotion) {
      score += PIECE_META[move.promotion].materialValue * 60;
    }
    if (enemyInCheck) {
      // Giving check forfeits remaining moves on multi-move cards
      score += state.movesRemaining > 1 ? -15 : 35;
    }
    if (!enemyHasMove && !enemyInCheck) {
      score -= 400; // Avoid stalemate when ahead
    }

    if (mode === 'VS_AI_HARD') {
      // Penalize moving onto a square attacked by enemy if undefended or losing material
      const attacked = ChessEngine.isSquareAttackedBy(applied.board, move.to, enemy);
      if (attacked) {
        const pieceVal = PIECE_META[applied.movedPiece.type].materialValue;
        const defended = ChessEngine.isSquareAttackedBy(applied.board, move.to, activeColor);
        score -= defended ? pieceVal * 28 : pieceVal * 82;
      }
      // Small 1-ply opponent reply check on captures
      const enemyReplies = ChessEngine.getAllLegalMoves(
        applied.board,
        enemy,
        applied.castlingRights,
        applied.enPassantTarget,
        applied.enPassantOwner
      );
      let worstPenalty = 0;
      for (const reply of enemyReplies) {
        const target = applied.board[reply.to.row][reply.to.col];
        if (target && target.color === activeColor) {
          worstPenalty = Math.max(worstPenalty, PIECE_META[target.type].materialValue * 60);
        }
      }
      score -= worstPenalty * 0.45;
    } else {
      // Medium adds gentle randomness
      score += (Math.random() - 0.5) * 36;
    }

    if (score > bestScore + 0.01) {
      bestScore = score;
      bestMoves = [move];
    } else if (Math.abs(score - bestScore) <= 0.01) {
      bestMoves.push(move);
    }
  }

  return bestMoves.length > 0 ? pickRandom(bestMoves) : legal[0];
}

export function chooseFreezeTarget(state: GameUiState, mode: GameMode): Square | null {
  if (state.validFreezeSquares.length === 0) return null;
  if (mode === 'VS_AI_EASY') return pickRandom(state.validFreezeSquares);

  const sorted = [...state.validFreezeSquares].sort((a, b) => {
    const pa = state.board[a.row][a.col];
    const pb = state.board[b.row][b.col];
    const va = (pa ? PIECE_META[pa.type].materialValue * 10 : 0) + centerBonus(a);
    const vb = (pb ? PIECE_META[pb.type].materialValue * 10 : 0) + centerBonus(b);
    return vb - va;
  });
  return sorted[0];
}

export function chooseShieldTarget(state: GameUiState, mode: GameMode): Square | null {
  if (state.validShieldSquares.length === 0) return null;
  if (mode === 'VS_AI_EASY') return pickRandom(state.validShieldSquares);

  const activeColor = getActiveColor(state);
  const enemy = opponentColor(activeColor);

  const sorted = [...state.validShieldSquares].sort((a, b) => {
    const pa = state.board[a.row][a.col];
    const pb = state.board[b.row][b.col];
    const sa =
      (pa ? PIECE_META[pa.type].materialValue * 12 : 0) +
      (ChessEngine.isSquareAttackedBy(state.board, a, enemy) ? 65 : 0);
    const sb =
      (pb ? PIECE_META[pb.type].materialValue * 12 : 0) +
      (ChessEngine.isSquareAttackedBy(state.board, b, enemy) ? 65 : 0);
    return sb - sa;
  });
  return sorted[0];
}

export function chooseReviveAction(
  state: GameUiState,
  mode: GameMode
): { pieceId: number | null; square: Square } | null {
  const activeColor = getActiveColor(state);
  const ownCaptured = state.capturedPieces[activeColor] ?? [];
  const freezeExcluded = new Set<number>(
    state.frozenPieceId !== null && state.frozenOwner === activeColor ? [state.frozenPieceId] : []
  );

  if (ownCaptured.length === 0) {
    if (state.validReviveSquares.length === 0) return null;
    const chosenSq =
      mode === 'VS_AI_EASY'
        ? pickRandom(state.validReviveSquares)
        : [...state.validReviveSquares].sort((a, b) => centerBonus(b) - centerBonus(a))[0];
    return { pieceId: null, square: chosenSq };
  }

  const prioritized = [...ownCaptured].sort(
    (a, b) => PIECE_META[b.type].materialValue - PIECE_META[a.type].materialValue
  );

  for (const piece of prioritized) {
    const squares = ChessEngine.getValidReviveSquares(state.board, piece, activeColor, freezeExcluded);
    if (squares.length > 0) {
      const sq =
        mode === 'VS_AI_EASY'
          ? pickRandom(squares)
          : [...squares].sort((a, b) => centerBonus(b) - centerBonus(a))[0];
      return { pieceId: piece.id, square: sq };
    }
  }
  return null;
}

export function chooseMergeAction(
  state: GameUiState,
  mode: GameMode
): { squares: Square[]; targetType: PieceType } | null {
  const activeColor = getActiveColor(state);
  const freezeExcluded = new Set<number>(
    state.frozenPieceId !== null && state.frozenOwner === activeColor ? [state.frozenPieceId] : []
  );

  const own: { sq: Square; piece: ChessPiece }[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = state.board[r][c];
      if (p && p.color === activeColor && p.type !== 'KING' && !freezeExcluded.has(p.id)) {
        own.push({ sq: { row: r, col: c }, piece: p });
      }
    }
  }

  // Try combining 2 minor/rook pieces into Queen or Rook
  const candidates: Array<{ squares: Square[]; targetType: PieceType; gain: number }> = [];

  for (let i = 0; i < own.length; i++) {
    for (let j = i + 1; j < own.length; j++) {
      const pair = [own[i].sq, own[j].sq];
      const valSum =
        PIECE_META[own[i].piece.type].materialValue + PIECE_META[own[j].piece.type].materialValue;
      const targets = ChessEngine.getValidTradeTargets(state.board, activeColor, pair);
      for (const t of targets) {
        if (PIECE_META[t].materialValue === valSum && (t === 'QUEEN' || t === 'ROOK')) {
          candidates.push({
            squares: pair,
            targetType: t,
            gain: PIECE_META[t].materialValue * 10,
          });
        }
      }
    }
  }

  // Try 3 pawns -> Bishop/Knight
  const pawns = own.filter((o) => o.piece.type === 'PAWN');
  if (pawns.length >= 3) {
    const trio = [pawns[0].sq, pawns[1].sq, pawns[2].sq];
    const targets = ChessEngine.getValidTradeTargets(state.board, activeColor, trio);
    for (const t of targets) {
      if (t === 'BISHOP' || t === 'KNIGHT') {
        candidates.push({ squares: trio, targetType: t, gain: 25 });
      }
    }
  }

  if (candidates.length === 0) return null;
  if (mode === 'VS_AI_EASY' && Math.random() < 0.4) return null;

  candidates.sort((a, b) => b.gain - a.gain);
  return { squares: candidates[0].squares, targetType: candidates[0].targetType };
}

export function chooseSacrificeAction(
  state: GameUiState,
  mode: GameMode
): { square: Square; recipeId: string } | null {
  if (state.validSacrificeSquares.length === 0) return null;
  // Generally splitting major pieces isn't always advantageous; Hard AI skips unless attacked or Easy rolls it
  const activeColor = getActiveColor(state);
  const enemy = opponentColor(activeColor);

  const threatened = state.validSacrificeSquares.filter((sq) =>
    ChessEngine.isSquareAttackedBy(state.board, sq, enemy)
  );
  if (mode === 'VS_AI_HARD' && threatened.length === 0) return null;
  if (mode === 'VS_AI_MEDIUM' && threatened.length === 0 && Math.random() < 0.65) return null;

  const pool = threatened.length > 0 ? threatened : state.validSacrificeSquares;
  const sq = pickRandom(pool);
  const recipes = ChessEngine.getValidSacrificeRecipes(state.board, activeColor, sq);
  if (recipes.length === 0) return null;
  return { square: sq, recipeId: recipes[0].id };
}

export function chooseHijackMove(state: GameUiState, mode: GameMode): ChessMove | null {
  const activeColor = getActiveColor(state);
  const enemy = opponentColor(activeColor);
  const enemyExcluded = new Set<number>(
    state.frozenPieceId !== null && state.frozenOwner === enemy ? [state.frozenPieceId] : []
  );
  const protectedOwn = new Set<number>(
    state.shieldedPieceId !== null && state.shieldedOwner === activeColor
      ? [state.shieldedPieceId]
      : []
  );

  const moves = ChessEngine.getAllHijackMoves(
    state.board,
    activeColor,
    state.castlingRights,
    state.enPassantTarget,
    state.enPassantOwner,
    enemyExcluded,
    protectedOwn
  );
  if (moves.length === 0) return null;
  if (mode === 'VS_AI_EASY') return pickRandom(moves);

  // Pick move that minimizes opponent position / puts opponent piece en prise
  let bestScore = -Infinity;
  let bestMove = moves[0];
  for (const move of moves) {
    const applied = ChessEngine.applyMove(state.board, move, state.castlingRights);
    let score = evaluateBoard(applied.board, activeColor);
    if (applied.capturedPiece && applied.capturedPiece.color === activeColor) {
      score -= PIECE_META[applied.capturedPiece.type].materialValue * 110;
    }
    if (ChessEngine.isSquareAttackedBy(applied.board, move.to, activeColor)) {
      score += PIECE_META[applied.movedPiece.type].materialValue * 55;
    }
    if (score > bestScore) {
      bestScore = score;
      bestMove = move;
    }
  }
  return bestMove;
}

export function choosePawnPromoteAction(
  state: GameUiState,
  mode: GameMode
): { square: Square; targetType: PieceType } | null {
  if (state.validPromotePawnSquares.length === 0) return null;
  const activeColor = getActiveColor(state);
  const sq =
    mode === 'VS_AI_EASY'
      ? pickRandom(state.validPromotePawnSquares)
      : [...state.validPromotePawnSquares].sort((a, b) => centerBonus(b) - centerBonus(a))[0];

  const targets = ChessEngine.getValidPawnPromoteTargets(state.board, activeColor, sq);
  if (targets.length === 0) return null;
  return { square: sq, targetType: targets.includes('BISHOP') ? 'BISHOP' : targets[0] };
}

export function chooseSuicideTarget(state: GameUiState, mode: GameMode): Square | null {
  if (state.validSuicideSquares.length === 0) return null;
  if (mode === 'VS_AI_EASY') return pickRandom(state.validSuicideSquares);

  const sorted = [...state.validSuicideSquares].sort((a, b) => {
    const pa = state.board[a.row][a.col];
    const pb = state.board[b.row][b.col];
    const va = pa ? PIECE_META[pa.type].materialValue : 99;
    const vb = pb ? PIECE_META[pb.type].materialValue : 99;
    return va - vb;
  });
  return sorted[0];
}

export function chooseSwapAction(
  state: GameUiState,
  mode: GameMode
): { first: Square; second: Square } | null {
  const activeColor = getActiveColor(state);
  const enemy = opponentColor(activeColor);
  const freezeExcluded = new Set<number>(
    state.frozenPieceId !== null && state.frozenOwner === activeColor ? [state.frozenPieceId] : []
  );

  const sources = ChessEngine.getValidSwapSourceSquares(state.board, activeColor, freezeExcluded);
  if (sources.length === 0) return null;

  if (mode === 'VS_AI_EASY') {
    const first = pickRandom(sources);
    const partners = ChessEngine.getValidSwapPartners(state.board, activeColor, first, freezeExcluded);
    if (partners.length === 0) return null;
    return { first, second: pickRandom(partners) };
  }

  // Prefer rescuing an attacked high-value piece by swapping with a safer lower-value piece
  let bestGain = 10;
  let bestPair: { first: Square; second: Square } | null = null;

  for (const first of sources) {
    const p1 = state.board[first.row][first.col];
    if (!p1) continue;
    const p1Attacked = ChessEngine.isSquareAttackedBy(state.board, first, enemy);
    const partners = ChessEngine.getValidSwapPartners(state.board, activeColor, first, freezeExcluded);
    for (const second of partners) {
      const p2 = state.board[second.row][second.col];
      if (!p2) continue;
      const p2Attacked = ChessEngine.isSquareAttackedBy(state.board, second, enemy);
      let gain = 0;
      if (p1Attacked && !p2Attacked) {
        gain += (PIECE_META[p1.type].materialValue - PIECE_META[p2.type].materialValue) * 20;
      }
      gain += (centerBonus(second) - centerBonus(first)) * (p1.type === 'KNIGHT' || p1.type === 'BISHOP' ? 6 : 2);
      if (gain > bestGain) {
        bestGain = gain;
        bestPair = { first, second };
      }
    }
  }

  return bestPair;
}

export function chooseCloneAction(
  state: GameUiState,
  mode: GameMode
): { source: Square; target: Square } | null {
  const activeColor = getActiveColor(state);
  const freezeExcluded = new Set<number>(
    state.frozenPieceId !== null && state.frozenOwner === activeColor ? [state.frozenPieceId] : []
  );

  const sources = ChessEngine.getValidCloneSourceSquares(state.board, activeColor, freezeExcluded);
  if (sources.length === 0) return null;

  const sortedSources =
    mode === 'VS_AI_EASY'
      ? sources
      : [...sources].sort((a, b) => {
          const pa = state.board[a.row][a.col];
          const pb = state.board[b.row][b.col];
          return (
            (pb ? PIECE_META[pb.type].materialValue : 0) -
            (pa ? PIECE_META[pa.type].materialValue : 0)
          );
        });

  const source = mode === 'VS_AI_EASY' ? pickRandom(sortedSources) : sortedSources[0];
  const targets = ChessEngine.getValidCloneTargetSquares(state.board, activeColor, source);
  if (targets.length === 0) return null;
  const target =
    mode === 'VS_AI_EASY'
      ? pickRandom(targets)
      : [...targets].sort((a, b) => centerBonus(b) - centerBonus(a))[0];

  return { source, target };
}

export function chooseWildEffect(state: GameUiState, mode: GameMode): SpecialCardKey | null {
  if (state.availableWildEffects.length === 0) return null;
  if (mode === 'VS_AI_EASY') return pickRandom(state.availableWildEffects);

  const priority: SpecialCardKey[] = [
    'REVIVE',
    'SNIPER',
    'CLONE',
    'PROMOTE',
    'FREEZE',
    'SHIELD',
    'HIJACK',
    'PORTAL',
    'TRAP',
    'MERGE',
    'SWAP',
    'SACRIFICE',
  ];
  for (const key of priority) {
    if (state.availableWildEffects.includes(key)) return key;
  }
  return state.availableWildEffects[0];
}

export function chooseSniperAction(
  state: GameUiState,
  mode: GameMode
): { sniperSquare: Square; targetSquare: Square } | null {
  if (state.validSniperSources.length === 0) return null;
  const activeColor = getActiveColor(state);
  const oppColor = opponentColor(activeColor);

  const candidates: Array<{ sniperSquare: Square; targetSquare: Square; value: number }> = [];

  for (const src of state.validSniperSources) {
    const targets = ChessEngine.getValidSniperTargets(state.board, src, oppColor, new Set());
    for (const tgt of targets) {
      const p = state.board[tgt.row][tgt.col];
      const val = p ? PIECE_META[p.type].materialValue : 0;
      candidates.push({ sniperSquare: src, targetSquare: tgt, value: val });
    }
  }

  if (candidates.length === 0) return null;
  if (mode === 'VS_AI_EASY') return pickRandom(candidates);

  candidates.sort((a, b) => b.value - a.value);
  return { sniperSquare: candidates[0].sniperSquare, targetSquare: candidates[0].targetSquare };
}

export function choosePortalAction(
  state: GameUiState,
  mode: GameMode
): { pieceSquare: Square; destSquare: Square } | null {
  if (state.validPortalSources.length === 0) return null;
  const activeColor = getActiveColor(state);
  const promoRow = activeColor === 'WHITE' ? 0 : 7;

  const candidates: Array<{ pieceSquare: Square; destSquare: Square; score: number }> = [];

  for (const src of state.validPortalSources) {
    const piece = state.board[src.row][src.col];
    if (!piece) continue;
    const dests = ChessEngine.getValidPortalDestinations(state.board, activeColor, src);
    for (const dest of dests) {
      let score = PIECE_META[piece.type].materialValue * 10 + centerBonus(dest);
      if (piece.type === 'PAWN' && dest.row === promoRow) {
        score += 900;
      }
      candidates.push({ pieceSquare: src, destSquare: dest, score });
    }
  }

  if (candidates.length === 0) return null;
  if (mode === 'VS_AI_EASY') {
    return pickRandom(candidates);
  }

  candidates.sort((a, b) => b.score - a.score);
  return candidates[0];
}

export function chooseTrapSquare(state: GameUiState, mode: GameMode): Square | null {
  if (state.validTrapSquares.length === 0) return null;
  if (mode === 'VS_AI_EASY') return pickRandom(state.validTrapSquares);

  // High priority squares: center squares d4, e4, d5, e5 or 3rd/4th/5th ranks
  const sorted = [...state.validTrapSquares].sort((a, b) => {
    return centerBonus(b) - centerBonus(a);
  });
  return sorted[0];
}

export function chooseDrawTwoCard(state: GameUiState, mode: GameMode): number {
  if (!state.drawTwoPendingCards) return 0;
  if (mode === 'VS_AI_EASY') return Math.random() < 0.5 ? 0 : 1;

  // Evaluate card 0 vs card 1
  const scoreCard = (card: typeof state.drawTwoPendingCards[0]): number => {
    if (card.type.kind === 'NUMBER') return card.type.moves * 3;
    if (card.type.kind === 'SNIPER') return 12;
    if (card.type.kind === 'REVIVE') return 10;
    if (card.type.kind === 'FREEZE') return 8;
    if (card.type.kind === 'PORTAL') return 7;
    if (card.type.kind === 'TRAP') return 6;
    if (card.type.kind === 'CLONE') return 9;
    if (card.type.kind === 'SHIELD') return 5;
    if (card.type.kind === 'WILD') return 11;
    return 4;
  };

  const score0 = scoreCard(state.drawTwoPendingCards[0]);
  const score1 = scoreCard(state.drawTwoPendingCards[1]);
  return score0 >= score1 ? 0 : 1;
}
