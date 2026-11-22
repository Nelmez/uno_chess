import {
  Board,
  CastlingRights,
  ChessMove,
  ChessPiece,
  isValidSquare,
  opponentColor,
  PIECE_META,
  PieceColor,
  PieceType,
  SacrificeRecipe,
  sameSquare,
  Square,
  squareKey,
  squareToAlgebraic,
} from '../model/gameModels';

export interface MoveApplicationResult {
  board: Board;
  movedPiece: ChessPiece;
  capturedPiece: ChessPiece | null;
  castlingRights: CastlingRights;
  enPassantTarget: Square | null;
  enPassantOwner: PieceColor | null;
  notation: string;
}

const BACK_RANK: PieceType[] = [
  'ROOK',
  'KNIGHT',
  'BISHOP',
  'QUEEN',
  'KING',
  'BISHOP',
  'KNIGHT',
  'ROOK',
];

export function createInitialBoard(): Board {
  let nextId = 1;
  const board: Board = Array.from({ length: 8 }, () => Array<ChessPiece | null>(8).fill(null));

  for (let col = 0; col < 8; col++) {
    board[0][col] = { id: nextId++, type: BACK_RANK[col], color: 'BLACK' };
    board[1][col] = { id: nextId++, type: 'PAWN', color: 'BLACK' };
    board[6][col] = { id: nextId++, type: 'PAWN', color: 'WHITE' };
    board[7][col] = { id: nextId++, type: BACK_RANK[col], color: 'WHITE' };
  }
  return board;
}

export function cloneBoard(board: Board): Board {
  return board.map((row) => [...row]);
}

export function findKing(board: Board, color: PieceColor): Square | null {
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === color && p.type === 'KING') {
        return { row: r, col: c };
      }
    }
  }
  return null;
}

export function isSquareAttackedBy(
  board: Board,
  target: Square,
  attackerColor: PieceColor
): boolean {
  // Pawn attacks
  const pawnSourceRow = attackerColor === 'WHITE' ? target.row + 1 : target.row - 1;
  if (pawnSourceRow >= 0 && pawnSourceRow < 8) {
    for (const dc of [-1, 1]) {
      const sc = target.col + dc;
      if (sc >= 0 && sc < 8) {
        const p = board[pawnSourceRow][sc];
        if (p && p.color === attackerColor && p.type === 'PAWN') return true;
      }
    }
  }

  // Knight attacks
  const knightOffsets: [number, number][] = [
    [-2, -1],
    [-2, 1],
    [-1, -2],
    [-1, 2],
    [1, -2],
    [1, 2],
    [2, -1],
    [2, 1],
  ];
  for (const [dr, dc] of knightOffsets) {
    const r = target.row + dr;
    const c = target.col + dc;
    if (isValidSquare(r, c)) {
      const p = board[r][c];
      if (p && p.color === attackerColor && p.type === 'KNIGHT') return true;
    }
  }

  // King attacks
  for (let dr = -1; dr <= 1; dr++) {
    for (let dc = -1; dc <= 1; dc++) {
      if (dr === 0 && dc === 0) continue;
      const r = target.row + dr;
      const c = target.col + dc;
      if (isValidSquare(r, c)) {
        const p = board[r][c];
        if (p && p.color === attackerColor && p.type === 'KING') return true;
      }
    }
  }

  // Straight sliders (Rook / Queen)
  const orthoDirs: [number, number][] = [
    [-1, 0],
    [1, 0],
    [0, -1],
    [0, 1],
  ];
  for (const [dr, dc] of orthoDirs) {
    let r = target.row + dr;
    let c = target.col + dc;
    while (isValidSquare(r, c)) {
      const p = board[r][c];
      if (p) {
        if (p.color === attackerColor && (p.type === 'ROOK' || p.type === 'QUEEN')) return true;
        break;
      }
      r += dr;
      c += dc;
    }
  }

  // Diagonal sliders (Bishop / Queen)
  const diagDirs: [number, number][] = [
    [-1, -1],
    [-1, 1],
    [1, -1],
    [1, 1],
  ];
  for (const [dr, dc] of diagDirs) {
    let r = target.row + dr;
    let c = target.col + dc;
    while (isValidSquare(r, c)) {
      const p = board[r][c];
      if (p) {
        if (p.color === attackerColor && (p.type === 'BISHOP' || p.type === 'QUEEN')) return true;
        break;
      }
      r += dr;
      c += dc;
    }
  }

  return false;
}

export function isKingInCheck(board: Board, color: PieceColor): boolean {
  const kingSq = findKing(board, color);
  if (!kingSq) return false;
  return isSquareAttackedBy(board, kingSq, opponentColor(color));
}

function applyMoveToBoardOnly(board: Board, move: ChessMove): Board {
  const next = cloneBoard(board);
  const piece = next[move.from.row][move.from.col];
  if (!piece) return next;

  next[move.from.row][move.from.col] = null;

  if (move.isEnPassant) {
    next[move.from.row][move.to.col] = null;
  }

  if (move.isCastlingKingside) {
    const rook = next[move.from.row][7];
    next[move.from.row][7] = null;
    next[move.from.row][5] = rook;
  } else if (move.isCastlingQueenside) {
    const rook = next[move.from.row][0];
    next[move.from.row][0] = null;
    next[move.from.row][3] = rook;
  }

  next[move.to.row][move.to.col] = move.promotion
    ? { ...piece, type: move.promotion }
    : piece;

  return next;
}

function generatePseudoLegalMoves(
  board: Board,
  from: Square,
  piece: ChessPiece,
  castlingRights: CastlingRights,
  enPassantTarget: Square | null,
  enPassantOwner: PieceColor | null,
  protectedPieceIds: Set<number>
): ChessMove[] {
  const moves: ChessMove[] = [];
  const enemy = opponentColor(piece.color);

  const canCapture = (target: ChessPiece | null): boolean =>
    target !== null && target.color === enemy && !protectedPieceIds.has(target.id);

  switch (piece.type) {
    case 'PAWN': {
      const dir = piece.color === 'WHITE' ? -1 : 1;
      const startRow = piece.color === 'WHITE' ? 6 : 1;
      const promoRow = piece.color === 'WHITE' ? 0 : 7;
      const nextRow = from.row + dir;

      if (isValidSquare(nextRow, from.col) && board[nextRow][from.col] === null) {
        if (nextRow === promoRow) {
          for (const promo of ['QUEEN', 'ROOK', 'BISHOP', 'KNIGHT'] as PieceType[]) {
            moves.push({ from, to: { row: nextRow, col: from.col }, promotion: promo });
          }
        } else {
          moves.push({ from, to: { row: nextRow, col: from.col } });
          const doubleRow = from.row + dir * 2;
          if (from.row === startRow && board[doubleRow][from.col] === null) {
            moves.push({ from, to: { row: doubleRow, col: from.col } });
          }
        }
      }

      for (const dc of [-1, 1]) {
        const tc = from.col + dc;
        if (!isValidSquare(nextRow, tc)) continue;
        const target = board[nextRow][tc];
        if (canCapture(target)) {
          if (nextRow === promoRow) {
            for (const promo of ['QUEEN', 'ROOK', 'BISHOP', 'KNIGHT'] as PieceType[]) {
              moves.push({ from, to: { row: nextRow, col: tc }, promotion: promo });
            }
          } else {
            moves.push({ from, to: { row: nextRow, col: tc } });
          }
        } else if (
          enPassantTarget &&
          enPassantOwner === enemy &&
          enPassantTarget.row === nextRow &&
          enPassantTarget.col === tc
        ) {
          const epPawn = board[from.row][tc];
          if (
            epPawn &&
            epPawn.color === enemy &&
            epPawn.type === 'PAWN' &&
            !protectedPieceIds.has(epPawn.id)
          ) {
            moves.push({ from, to: { row: nextRow, col: tc }, isEnPassant: true });
          }
        }
      }
      break;
    }

    case 'KNIGHT': {
      const offsets: [number, number][] = [
        [-2, -1],
        [-2, 1],
        [-1, -2],
        [-1, 2],
        [1, -2],
        [1, 2],
        [2, -1],
        [2, 1],
      ];
      for (const [dr, dc] of offsets) {
        const r = from.row + dr;
        const c = from.col + dc;
        if (!isValidSquare(r, c)) continue;
        const target = board[r][c];
        if (target === null || canCapture(target)) {
          moves.push({ from, to: { row: r, col: c } });
        }
      }
      break;
    }

    case 'BISHOP':
    case 'ROOK':
    case 'QUEEN': {
      const dirs: [number, number][] = [];
      if (piece.type === 'BISHOP' || piece.type === 'QUEEN') {
        dirs.push([-1, -1], [-1, 1], [1, -1], [1, 1]);
      }
      if (piece.type === 'ROOK' || piece.type === 'QUEEN') {
        dirs.push([-1, 0], [1, 0], [0, -1], [0, 1]);
      }
      for (const [dr, dc] of dirs) {
        let r = from.row + dr;
        let c = from.col + dc;
        while (isValidSquare(r, c)) {
          const target = board[r][c];
          if (target === null) {
            moves.push({ from, to: { row: r, col: c } });
          } else {
            if (canCapture(target)) {
              moves.push({ from, to: { row: r, col: c } });
            }
            break;
          }
          r += dr;
          c += dc;
        }
      }
      break;
    }

    case 'KING': {
      for (let dr = -1; dr <= 1; dr++) {
        for (let dc = -1; dc <= 1; dc++) {
          if (dr === 0 && dc === 0) continue;
          const r = from.row + dr;
          const c = from.col + dc;
          if (!isValidSquare(r, c)) continue;
          const target = board[r][c];
          if (target === null || canCapture(target)) {
            moves.push({ from, to: { row: r, col: c } });
          }
        }
      }

      const homeRow = piece.color === 'WHITE' ? 7 : 0;
      if (from.row === homeRow && from.col === 4 && !isSquareAttackedBy(board, from, enemy)) {
        const canKingside =
          piece.color === 'WHITE' ? castlingRights.whiteKingside : castlingRights.blackKingside;
        const canQueenside =
          piece.color === 'WHITE' ? castlingRights.whiteQueenside : castlingRights.blackQueenside;

        if (canKingside) {
          const rook = board[homeRow][7];
          if (
            rook &&
            rook.color === piece.color &&
            rook.type === 'ROOK' &&
            board[homeRow][5] === null &&
            board[homeRow][6] === null &&
            !isSquareAttackedBy(board, { row: homeRow, col: 5 }, enemy) &&
            !isSquareAttackedBy(board, { row: homeRow, col: 6 }, enemy)
          ) {
            moves.push({ from, to: { row: homeRow, col: 6 }, isCastlingKingside: true });
          }
        }

        if (canQueenside) {
          const rook = board[homeRow][0];
          if (
            rook &&
            rook.color === piece.color &&
            rook.type === 'ROOK' &&
            board[homeRow][1] === null &&
            board[homeRow][2] === null &&
            board[homeRow][3] === null &&
            !isSquareAttackedBy(board, { row: homeRow, col: 3 }, enemy) &&
            !isSquareAttackedBy(board, { row: homeRow, col: 2 }, enemy)
          ) {
            moves.push({ from, to: { row: homeRow, col: 2 }, isCastlingQueenside: true });
          }
        }
      }
      break;
    }
  }

  return moves;
}

export function getLegalMovesForSquare(
  board: Board,
  from: Square,
  castlingRights: CastlingRights,
  enPassantTarget: Square | null,
  enPassantOwner: PieceColor | null,
  excludedPieceIds: Set<number> = new Set(),
  protectedPieceIds: Set<number> = new Set()
): ChessMove[] {
  const piece = board[from.row]?.[from.col];
  if (!piece || excludedPieceIds.has(piece.id)) return [];

  const pseudo = generatePseudoLegalMoves(
    board,
    from,
    piece,
    castlingRights,
    enPassantTarget,
    enPassantOwner,
    protectedPieceIds
  );

  return pseudo.filter((move) => {
    const nextBoard = applyMoveToBoardOnly(board, move);
    return !isKingInCheck(nextBoard, piece.color);
  });
}

export function getAllLegalMoves(
  board: Board,
  color: PieceColor,
  castlingRights: CastlingRights,
  enPassantTarget: Square | null,
  enPassantOwner: PieceColor | null,
  excludedPieceIds: Set<number> = new Set(),
  protectedPieceIds: Set<number> = new Set()
): ChessMove[] {
  const all: ChessMove[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === color && !excludedPieceIds.has(p.id)) {
        all.push(
          ...getLegalMovesForSquare(
            board,
            { row: r, col: c },
            castlingRights,
            enPassantTarget,
            enPassantOwner,
            excludedPieceIds,
            protectedPieceIds
          )
        );
      }
    }
  }
  return all;
}

export function hasAnyLegalMove(
  board: Board,
  color: PieceColor,
  castlingRights: CastlingRights,
  enPassantTarget: Square | null,
  enPassantOwner: PieceColor | null,
  excludedPieceIds: Set<number> = new Set(),
  protectedPieceIds: Set<number> = new Set()
): boolean {
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === color && !excludedPieceIds.has(p.id)) {
        if (
          getLegalMovesForSquare(
            board,
            { row: r, col: c },
            castlingRights,
            enPassantTarget,
            enPassantOwner,
            excludedPieceIds,
            protectedPieceIds
          ).length > 0
        ) {
          return true;
        }
      }
    }
  }
  return false;
}

function buildMoveNotation(piece: ChessPiece, move: ChessMove, isCapture: boolean): string {
  if (move.isCastlingKingside) return 'O-O';
  if (move.isCastlingQueenside) return 'O-O-O';
  const prefix = PIECE_META[piece.type].letter;
  const sep = isCapture ? '×' : '→';
  const promo = move.promotion ? `=${PIECE_META[move.promotion].letter}` : '';
  return `${prefix}${squareToAlgebraic(move.from)}${sep}${squareToAlgebraic(move.to)}${promo}`;
}

export function applyMove(
  board: Board,
  move: ChessMove,
  castlingRights: CastlingRights
): MoveApplicationResult {
  const movingPiece = board[move.from.row][move.from.col];
  if (!movingPiece) {
    throw new Error(`No piece at ${squareToAlgebraic(move.from)}`);
  }

  const capturedPiece = move.isEnPassant
    ? board[move.from.row][move.to.col]
    : board[move.to.row][move.to.col];

  const newBoard = applyMoveToBoardOnly(board, move);

  let wk = castlingRights.whiteKingside;
  let wq = castlingRights.whiteQueenside;
  let bk = castlingRights.blackKingside;
  let bq = castlingRights.blackQueenside;

  if (movingPiece.type === 'KING') {
    if (movingPiece.color === 'WHITE') {
      wk = false;
      wq = false;
    } else {
      bk = false;
      bq = false;
    }
  }
  if (sameSquare(move.from, { row: 7, col: 7 }) || sameSquare(move.to, { row: 7, col: 7 })) wk = false;
  if (sameSquare(move.from, { row: 7, col: 0 }) || sameSquare(move.to, { row: 7, col: 0 })) wq = false;
  if (sameSquare(move.from, { row: 0, col: 7 }) || sameSquare(move.to, { row: 0, col: 7 })) bk = false;
  if (sameSquare(move.from, { row: 0, col: 0 }) || sameSquare(move.to, { row: 0, col: 0 })) bq = false;

  let newEnPassantTarget: Square | null = null;
  let newEnPassantOwner: PieceColor | null = null;
  if (movingPiece.type === 'PAWN' && Math.abs(move.to.row - move.from.row) === 2) {
    newEnPassantTarget = { row: (move.from.row + move.to.row) / 2, col: move.from.col };
    newEnPassantOwner = movingPiece.color;
  }

  return {
    board: newBoard,
    movedPiece: movingPiece,
    capturedPiece,
    castlingRights: {
      whiteKingside: wk,
      whiteQueenside: wq,
      blackKingside: bk,
      blackQueenside: bq,
    },
    enPassantTarget: newEnPassantTarget,
    enPassantOwner: newEnPassantOwner,
    notation: buildMoveNotation(movingPiece, move, capturedPiece !== null),
  };
}

export function placePiece(board: Board, square: Square, piece: ChessPiece): Board {
  const next = cloneBoard(board);
  next[square.row][square.col] = piece;
  return next;
}

export function removePiece(board: Board, square: Square): Board {
  const next = cloneBoard(board);
  next[square.row][square.col] = null;
  return next;
}

// ---- Special Card Engines ----

export function getValidFreezeSquares(board: Board, opponentCol: PieceColor): Square[] {
  const out: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === opponentCol && p.type !== 'KING') {
        out.push({ row: r, col: c });
      }
    }
  }
  return out;
}

export function getOwnNonKingSquares(board: Board, color: PieceColor): Square[] {
  const out: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === color && p.type !== 'KING') {
        out.push({ row: r, col: c });
      }
    }
  }
  return out;
}

export function getValidShieldSquares(board: Board, color: PieceColor): Square[] {
  return getOwnNonKingSquares(board, color);
}

export function getValidReviveSquares(
  board: Board,
  piece: ChessPiece,
  color: PieceColor,
  excludedPieceIds: Set<number> = new Set()
): Square[] {
  const enemy = opponentColor(color);
  const out: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const existing = board[r][c];
      if (
        existing &&
        existing.color === color &&
        existing.type === 'PAWN' &&
        !excludedPieceIds.has(existing.id)
      ) {
        const candidate = placePiece(board, { row: r, col: c }, piece);
        if (!isKingInCheck(candidate, color) && !isKingInCheck(candidate, enemy)) {
          out.push({ row: r, col: c });
        }
      }
    }
  }
  return out;
}

export function getValidSpawnPawnSquares(board: Board, color: PieceColor): Square[] {
  const rows = color === 'WHITE' ? [5, 6] : [1, 2];
  const enemy = opponentColor(color);
  const probe: ChessPiece = { id: -999, type: 'PAWN', color };
  const out: Square[] = [];
  for (const r of rows) {
    for (let c = 0; c < 8; c++) {
      if (board[r][c] === null) {
        const candidate = placePiece(board, { row: r, col: c }, probe);
        if (!isKingInCheck(candidate, enemy)) {
          out.push({ row: r, col: c });
        }
      }
    }
  }
  return out;
}

export function getHijackMovesForSquare(
  board: Board,
  from: Square,
  activeColor: PieceColor,
  castlingRights: CastlingRights,
  enPassantTarget: Square | null,
  enPassantOwner: PieceColor | null,
  excludedPieceIds: Set<number> = new Set(),
  protectedPieceIds: Set<number> = new Set()
): ChessMove[] {
  const enemy = opponentColor(activeColor);
  const piece = board[from.row]?.[from.col];
  if (!piece || piece.color !== enemy || excludedPieceIds.has(piece.id)) return [];

  const legal = getLegalMovesForSquare(
    board,
    from,
    castlingRights,
    enPassantTarget,
    enPassantOwner,
    excludedPieceIds,
    protectedPieceIds
  );

  return legal.filter((move) => {
    const nextBoard = applyMoveToBoardOnly(board, move);
    return !isKingInCheck(nextBoard, activeColor);
  });
}

export function getAllHijackMoves(
  board: Board,
  activeColor: PieceColor,
  castlingRights: CastlingRights,
  enPassantTarget: Square | null,
  enPassantOwner: PieceColor | null,
  excludedPieceIds: Set<number> = new Set(),
  protectedPieceIds: Set<number> = new Set()
): ChessMove[] {
  const enemy = opponentColor(activeColor);
  const all: ChessMove[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === enemy && !excludedPieceIds.has(p.id)) {
        all.push(
          ...getHijackMovesForSquare(
            board,
            { row: r, col: c },
            activeColor,
            castlingRights,
            enPassantTarget,
            enPassantOwner,
            excludedPieceIds,
            protectedPieceIds
          )
        );
      }
    }
  }
  return all;
}

export function getValidPawnPromoteTargets(
  board: Board,
  color: PieceColor,
  pawnSquare: Square
): PieceType[] {
  const piece = board[pawnSquare.row]?.[pawnSquare.col];
  if (!piece || piece.color !== color || piece.type !== 'PAWN') return [];
  const enemy = opponentColor(color);
  return (['BISHOP', 'KNIGHT'] as PieceType[]).filter((targetType) => {
    const nextBoard = placePiece(board, pawnSquare, { ...piece, type: targetType });
    return !isKingInCheck(nextBoard, color) && !isKingInCheck(nextBoard, enemy);
  });
}

export function getValidPawnPromoteSquares(
  board: Board,
  color: PieceColor,
  excludedPieceIds: Set<number> = new Set()
): Square[] {
  const out: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === color && p.type === 'PAWN' && !excludedPieceIds.has(p.id)) {
        const sq = { row: r, col: c };
        if (getValidPawnPromoteTargets(board, color, sq).length > 0) {
          out.push(sq);
        }
      }
    }
  }
  return out;
}

export function getValidTradeTargets(
  board: Board,
  color: PieceColor,
  selectedSquares: Square[]
): PieceType[] {
  if (selectedSquares.length === 0) return [];
  const pieces: ChessPiece[] = [];
  for (const sq of selectedSquares) {
    const p = board[sq.row]?.[sq.col];
    if (!p || p.color !== color || p.type === 'KING') return [];
    pieces.push(p);
  }
  const totalValue = pieces.reduce((sum, p) => sum + PIECE_META[p.type].materialValue, 0);
  const anchor = selectedSquares[0];
  const enemy = opponentColor(color);
  const candidates: PieceType[] = ['QUEEN', 'ROOK', 'BISHOP', 'KNIGHT', 'PAWN'];

  return candidates.filter((targetType) => {
    if (PIECE_META[targetType].materialValue > totalValue) return false;
    if (selectedSquares.length === 1 && pieces[0].type === targetType) return false;
    if (targetType === 'PAWN' && (anchor.row === 0 || anchor.row === 7)) return false;

    const next = cloneBoard(board);
    for (const sq of selectedSquares) {
      next[sq.row][sq.col] = null;
    }
    next[anchor.row][anchor.col] = { id: -500, type: targetType, color };
    return !isKingInCheck(next, color) && !isKingInCheck(next, enemy);
  });
}

export function hasAnyValidTrade(
  board: Board,
  color: PieceColor,
  excludedPieceIds: Set<number> = new Set()
): boolean {
  const ownNonKing: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === color && p.type !== 'KING' && !excludedPieceIds.has(p.id)) {
        ownNonKing.push({ row: r, col: c });
      }
    }
  }
  if (ownNonKing.length === 0) return false;

  for (const sq of ownNonKing) {
    if (getValidTradeTargets(board, color, [sq]).length > 0) return true;
  }
  if (ownNonKing.length >= 2) {
    for (let i = 0; i < ownNonKing.length; i++) {
      for (let j = i + 1; j < ownNonKing.length; j++) {
        if (getValidTradeTargets(board, color, [ownNonKing[i], ownNonKing[j]]).length > 0) {
          return true;
        }
      }
    }
  }
  if (ownNonKing.length >= 3) {
    if (getValidTradeTargets(board, color, ownNonKing.slice(0, 3)).length > 0) return true;
  }
  return false;
}

export function applyTrade(
  board: Board,
  selectedSquares: Square[],
  newPiece: ChessPiece,
  castlingRights: CastlingRights
): { board: Board; castlingRights: CastlingRights } {
  const next = cloneBoard(board);
  let wk = castlingRights.whiteKingside;
  let wq = castlingRights.whiteQueenside;
  let bk = castlingRights.blackKingside;
  let bq = castlingRights.blackQueenside;

  for (const sq of selectedSquares) {
    next[sq.row][sq.col] = null;
    if (sameSquare(sq, { row: 7, col: 7 })) wk = false;
    if (sameSquare(sq, { row: 7, col: 0 })) wq = false;
    if (sameSquare(sq, { row: 0, col: 7 })) bk = false;
    if (sameSquare(sq, { row: 0, col: 0 })) bq = false;
  }
  const anchor = selectedSquares[0];
  next[anchor.row][anchor.col] = newPiece;

  return {
    board: next,
    castlingRights: { whiteKingside: wk, whiteQueenside: wq, blackKingside: bk, blackQueenside: bq },
  };
}

export function getSacrificeRecipes(pieceType: PieceType): SacrificeRecipe[] {
  switch (pieceType) {
    case 'KNIGHT':
    case 'BISHOP':
      return [{ id: '3p', label: '3 Pawns', pieces: ['PAWN', 'PAWN', 'PAWN'] }];
    case 'ROOK':
      return [
        { id: 'b2p', label: 'Bishop + 2P', pieces: ['BISHOP', 'PAWN', 'PAWN'] },
        { id: 'n2p', label: 'Knight + 2P', pieces: ['KNIGHT', 'PAWN', 'PAWN'] },
        { id: '5p', label: '5 Pawns', pieces: ['PAWN', 'PAWN', 'PAWN', 'PAWN', 'PAWN'] },
      ];
    case 'QUEEN':
      return [
        { id: 'rbp', label: 'Rook + B + P', pieces: ['ROOK', 'BISHOP', 'PAWN'] },
        { id: 'rnp', label: 'Rook + N + P', pieces: ['ROOK', 'KNIGHT', 'PAWN'] },
        { id: '2bn', label: '2 Bishops + N', pieces: ['BISHOP', 'BISHOP', 'KNIGHT'] },
        { id: '2nb', label: '2 Knights + B', pieces: ['KNIGHT', 'KNIGHT', 'BISHOP'] },
      ];
    default:
      return [];
  }
}

export function computeSacrificePlacement(
  board: Board,
  color: PieceColor,
  sacrificeSquare: Square,
  recipe: SacrificeRecipe
): Array<{ square: Square; type: PieceType }> | null {
  const piece = board[sacrificeSquare.row]?.[sacrificeSquare.col];
  if (!piece || piece.color !== color || piece.type === 'KING') return null;
  const sumVal = recipe.pieces.reduce((s, t) => s + PIECE_META[t].materialValue, 0);
  if (sumVal !== PIECE_META[piece.type].materialValue) return null;

  const working = cloneBoard(board);
  working[sacrificeSquare.row][sacrificeSquare.col] = null;

  const isOwnHalf = (row: number) => (color === 'WHITE' ? row >= 4 : row <= 3);
  const candidates: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      if (working[r][c] === null) candidates.push({ row: r, col: c });
    }
  }

  candidates.sort((a, b) => {
    const aSac = sameSquare(a, sacrificeSquare) ? 0 : 1;
    const bSac = sameSquare(b, sacrificeSquare) ? 0 : 1;
    if (aSac !== bSac) return aSac - bSac;

    const aHalf = isOwnHalf(a.row) ? 0 : 1;
    const bHalf = isOwnHalf(b.row) ? 0 : 1;
    if (aHalf !== bHalf) return aHalf - bHalf;

    const aDist = Math.abs(a.row - sacrificeSquare.row) + Math.abs(a.col - sacrificeSquare.col);
    const bDist = Math.abs(b.row - sacrificeSquare.row) + Math.abs(b.col - sacrificeSquare.col);
    if (aDist !== bDist) return aDist - bDist;

    return Math.abs(a.col - 3) - Math.abs(b.col - 3);
  });

  const placements: Array<{ square: Square; type: PieceType }> = [];
  const used = new Set<string>();
  let tempId = -1000;

  for (const spawnType of recipe.pieces) {
    const chosen = candidates.find(
      (sq) => !used.has(squareKey(sq)) && !(spawnType === 'PAWN' && (sq.row === 0 || sq.row === 7))
    );
    if (!chosen) return null;
    used.add(squareKey(chosen));
    placements.push({ square: chosen, type: spawnType });
    working[chosen.row][chosen.col] = { id: tempId--, type: spawnType, color };
  }

  if (isKingInCheck(working, color) || isKingInCheck(working, opponentColor(color))) {
    return null;
  }
  return placements;
}

export function getValidSacrificeRecipes(
  board: Board,
  color: PieceColor,
  sacrificeSquare: Square
): SacrificeRecipe[] {
  const piece = board[sacrificeSquare.row]?.[sacrificeSquare.col];
  if (!piece || piece.color !== color) return [];
  return getSacrificeRecipes(piece.type).filter(
    (recipe) => computeSacrificePlacement(board, color, sacrificeSquare, recipe) !== null
  );
}

export function getValidSacrificeSquares(
  board: Board,
  color: PieceColor,
  excludedPieceIds: Set<number> = new Set()
): Square[] {
  const out: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === color && p.type !== 'KING' && !excludedPieceIds.has(p.id)) {
        const sq = { row: r, col: c };
        if (getValidSacrificeRecipes(board, color, sq).length > 0) {
          out.push(sq);
        }
      }
    }
  }
  return out;
}

export function applySacrifice(
  board: Board,
  sacrificeSquare: Square,
  placements: Array<{ square: Square; type: PieceType }>,
  color: PieceColor,
  startingPieceId: number,
  castlingRights: CastlingRights
): { board: Board; castlingRights: CastlingRights } {
  const next = cloneBoard(board);
  next[sacrificeSquare.row][sacrificeSquare.col] = null;

  let nextId = startingPieceId;
  for (const { square, type } of placements) {
    next[square.row][square.col] = { id: nextId++, type, color };
  }

  let wk = castlingRights.whiteKingside;
  let wq = castlingRights.whiteQueenside;
  let bk = castlingRights.blackKingside;
  let bq = castlingRights.blackQueenside;
  if (sameSquare(sacrificeSquare, { row: 7, col: 7 })) wk = false;
  if (sameSquare(sacrificeSquare, { row: 7, col: 0 })) wq = false;
  if (sameSquare(sacrificeSquare, { row: 0, col: 7 })) bk = false;
  if (sameSquare(sacrificeSquare, { row: 0, col: 0 })) bq = false;

  return {
    board: next,
    castlingRights: { whiteKingside: wk, whiteQueenside: wq, blackKingside: bk, blackQueenside: bq },
  };
}

function swapBoardOnly(board: Board, first: Square, second: Square): Board {
  const next = cloneBoard(board);
  const p1 = next[first.row][first.col];
  next[first.row][first.col] = next[second.row][second.col];
  next[second.row][second.col] = p1;
  return next;
}

export function getValidSwapPartners(
  board: Board,
  color: PieceColor,
  firstSquare: Square,
  excludedPieceIds: Set<number> = new Set()
): Square[] {
  const firstPiece = board[firstSquare.row]?.[firstSquare.col];
  if (!firstPiece || firstPiece.color !== color || excludedPieceIds.has(firstPiece.id)) return [];

  const enemy = opponentColor(color);
  const out: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const second = { row: r, col: c };
      if (sameSquare(second, firstSquare)) continue;
      const secondPiece = board[r][c];
      if (!secondPiece || secondPiece.color !== color || excludedPieceIds.has(secondPiece.id)) {
        continue;
      }
      if (firstPiece.type === 'PAWN' && (r === 0 || r === 7)) continue;
      if (secondPiece.type === 'PAWN' && (firstSquare.row === 0 || firstSquare.row === 7)) continue;

      const swapped = swapBoardOnly(board, firstSquare, second);
      if (!isKingInCheck(swapped, color) && !isKingInCheck(swapped, enemy)) {
        out.push(second);
      }
    }
  }
  return out;
}

export function getValidSwapSourceSquares(
  board: Board,
  color: PieceColor,
  excludedPieceIds: Set<number> = new Set()
): Square[] {
  const out: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (p && p.color === color && !excludedPieceIds.has(p.id)) {
        const sq = { row: r, col: c };
        if (getValidSwapPartners(board, color, sq, excludedPieceIds).length > 0) {
          out.push(sq);
        }
      }
    }
  }
  return out;
}

export function applySwap(
  board: Board,
  first: Square,
  second: Square,
  castlingRights: CastlingRights
): { board: Board; castlingRights: CastlingRights } {
  const nextBoard = swapBoardOnly(board, first, second);
  let wk = castlingRights.whiteKingside;
  let wq = castlingRights.whiteQueenside;
  let bk = castlingRights.blackKingside;
  let bq = castlingRights.blackQueenside;

  for (const sq of [first, second]) {
    const orig = board[sq.row][sq.col];
    if (orig?.type === 'KING') {
      if (orig.color === 'WHITE') {
        wk = false;
        wq = false;
      } else {
        bk = false;
        bq = false;
      }
    }
    if (sameSquare(sq, { row: 7, col: 7 })) wk = false;
    if (sameSquare(sq, { row: 7, col: 0 })) wq = false;
    if (sameSquare(sq, { row: 0, col: 7 })) bk = false;
    if (sameSquare(sq, { row: 0, col: 0 })) bq = false;
  }

  return {
    board: nextBoard,
    castlingRights: { whiteKingside: wk, whiteQueenside: wq, blackKingside: bk, blackQueenside: bq },
  };
}

export function getValidCloneTargetSquares(
  board: Board,
  color: PieceColor,
  sourceSquare: Square
): Square[] {
  const sourcePiece = board[sourceSquare.row]?.[sourceSquare.col];
  if (!sourcePiece || sourcePiece.color !== color) return [];
  if (
    sourcePiece.type !== 'PAWN' &&
    sourcePiece.type !== 'KNIGHT' &&
    sourcePiece.type !== 'BISHOP'
  ) {
    return [];
  }

  const enemy = opponentColor(color);
  const probe: ChessPiece = { id: -777, type: sourcePiece.type, color };

  const isLegal = (sq: Square): boolean => {
    if (!isValidSquare(sq.row, sq.col) || board[sq.row][sq.col] !== null) return false;
    if (sourcePiece.type === 'PAWN' && (sq.row === 0 || sq.row === 7)) return false;
    const next = placePiece(board, sq, probe);
    return !isKingInCheck(next, color) && !isKingInCheck(next, enemy);
  };

  const adjacent: Square[] = [];
  for (let dr = -1; dr <= 1; dr++) {
    for (let dc = -1; dc <= 1; dc++) {
      if (dr === 0 && dc === 0) continue;
      const sq = { row: sourceSquare.row + dr, col: sourceSquare.col + dc };
      if (isLegal(sq)) adjacent.push(sq);
    }
  }
  if (adjacent.length > 0) return adjacent;

  const ownRows = color === 'WHITE' ? [4, 5, 6, 7] : [0, 1, 2, 3];
  const fallback: Square[] = [];
  for (const r of ownRows) {
    for (let c = 0; c < 8; c++) {
      const sq = { row: r, col: c };
      if (isLegal(sq)) fallback.push(sq);
    }
  }
  return fallback;
}

export function getValidCloneSourceSquares(
  board: Board,
  color: PieceColor,
  excludedPieceIds: Set<number> = new Set()
): Square[] {
  const out: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (
        p &&
        p.color === color &&
        !excludedPieceIds.has(p.id) &&
        (p.type === 'PAWN' || p.type === 'KNIGHT' || p.type === 'BISHOP')
      ) {
        const sq = { row: r, col: c };
        if (getValidCloneTargetSquares(board, color, sq).length > 0) {
          out.push(sq);
        }
      }
    }
  }
  return out;
}

export function getValidSuicideSquares(board: Board, color: PieceColor): Square[] {
  return getOwnNonKingSquares(board, color).filter((sq) => {
    const after = removePiece(board, sq);
    return !isKingInCheck(after, color);
  });
}

export function applySuicide(
  board: Board,
  square: Square,
  castlingRights: CastlingRights
): { board: Board; castlingRights: CastlingRights } {
  const newBoard = removePiece(board, square);
  let wk = castlingRights.whiteKingside;
  let wq = castlingRights.whiteQueenside;
  let bk = castlingRights.blackKingside;
  let bq = castlingRights.blackQueenside;
  if (sameSquare(square, { row: 7, col: 7 })) wk = false;
  if (sameSquare(square, { row: 7, col: 0 })) wq = false;
  if (sameSquare(square, { row: 0, col: 7 })) bk = false;
  if (sameSquare(square, { row: 0, col: 0 })) bq = false;

  return {
    board: newBoard,
    castlingRights: { whiteKingside: wk, whiteQueenside: wq, blackKingside: bk, blackQueenside: bq },
  };
}

// ---------------------------------------------------------------------------
// Sniper / Portal / Trap helpers
// ---------------------------------------------------------------------------

export function getValidSniperTargets(
  board: Board,
  sourceSquare: Square,
  targetColor: PieceColor,
  protectedPieceIds: Set<number>
): Square[] {
  const piece = board[sourceSquare.row][sourceSquare.col];
  if (!piece) return [];
  const ownColor = piece.color;

  const candidateTargets: Square[] = [];
  const moves = getLegalMovesForSquare(board, sourceSquare, {
    whiteKingside: false,
    whiteQueenside: false,
    blackKingside: false,
    blackQueenside: false,
  }, null, null, new Set(), protectedPieceIds);

  for (const m of moves) {
    const destPiece = board[m.to.row][m.to.col];
    if (!destPiece || destPiece.color !== targetColor) continue;
    if (destPiece.type === 'KING') continue;
    if (protectedPieceIds.has(destPiece.id)) continue;

    // Verify removing this piece without moving sniper piece leaves own King safe
    const boardAfter = removePiece(board, m.to);
    if (!isKingInCheck(boardAfter, ownColor)) {
      candidateTargets.push(m.to);
    }
  }

  return candidateTargets;
}

export function getValidSniperSources(
  board: Board,
  color: PieceColor,
  protectedPieceIds: Set<number>
): Square[] {
  const oppColor = opponentColor(color);
  const sources: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      const p = board[r][c];
      if (!p || p.color !== color) continue;
      const targets = getValidSniperTargets(board, { row: r, col: c }, oppColor, protectedPieceIds);
      if (targets.length > 0) {
        sources.push({ row: r, col: c });
      }
    }
  }
  return sources;
}

export function getValidPortalSources(board: Board, color: PieceColor): Square[] {
  return getOwnNonKingSquares(board, color);
}

export function getValidPortalDestinations(
  board: Board,
  color: PieceColor,
  sourceSquare: Square
): Square[] {
  const piece = board[sourceSquare.row][sourceSquare.col];
  if (!piece || piece.color !== color || piece.type === 'KING') return [];

  const destinations: Square[] = [];
  const withoutPiece = removePiece(board, sourceSquare);

  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      if (board[r][c] !== null) continue;
      const dest = { row: r, col: c };
      const simulated = placePiece(withoutPiece, dest, piece);
      if (!isKingInCheck(simulated, color)) {
        destinations.push(dest);
      }
    }
  }
  return destinations;
}

export function getValidTrapSquares(
  board: Board,
  traps: Array<{ row: number; col: number }>
): Square[] {
  const valid: Square[] = [];
  for (let r = 0; r < 8; r++) {
    for (let c = 0; c < 8; c++) {
      if (board[r][c] !== null) continue;
      if (traps.some((t) => t.row === r && t.col === c)) continue;
      valid.push({ row: r, col: c });
    }
  }
  return valid;
}
