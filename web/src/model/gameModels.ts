export type PieceColor = 'WHITE' | 'BLACK';

export function opponentColor(color: PieceColor): PieceColor {
  return color === 'WHITE' ? 'BLACK' : 'WHITE';
}

export function colorDisplayName(color: PieceColor): string {
  return color === 'WHITE' ? 'White' : 'Black';
}

export type PlayerId = 'PLAYER_1' | 'PLAYER_2';

export function opponentPlayer(player: PlayerId): PlayerId {
  return player === 'PLAYER_1' ? 'PLAYER_2' : 'PLAYER_1';
}

export function playerDisplayName(player: PlayerId): string {
  return player === 'PLAYER_1' ? 'Player 1' : 'Player 2';
}

export function playerShortName(player: PlayerId): string {
  return player === 'PLAYER_1' ? 'P1' : 'P2';
}

export type PieceType = 'KING' | 'QUEEN' | 'ROOK' | 'BISHOP' | 'KNIGHT' | 'PAWN';

export const PIECE_META: Record<
  PieceType,
  { displayName: string; letter: string; materialValue: number; isMajor: boolean }
> = {
  KING: { displayName: 'King', letter: 'K', materialValue: 0, isMajor: false },
  QUEEN: { displayName: 'Queen', letter: 'Q', materialValue: 9, isMajor: true },
  ROOK: { displayName: 'Rook', letter: 'R', materialValue: 5, isMajor: true },
  BISHOP: { displayName: 'Bishop', letter: 'B', materialValue: 3, isMajor: false },
  KNIGHT: { displayName: 'Knight', letter: 'N', materialValue: 3, isMajor: false },
  PAWN: { displayName: 'Pawn', letter: '', materialValue: 1, isMajor: false },
};

export interface ChessPiece {
  id: number;
  type: PieceType;
  color: PieceColor;
}

export interface Square {
  row: number; // 0 = rank 8 (top), 7 = rank 1 (bottom)
  col: number; // 0 = file a, 7 = file h
}

export function squareKey(sq: Square): string {
  return `${sq.row},${sq.col}`;
}

export function sameSquare(a: Square | null | undefined, b: Square | null | undefined): boolean {
  if (!a || !b) return false;
  return a.row === b.row && a.col === b.col;
}

export function isValidSquare(row: number, col: number): boolean {
  return row >= 0 && row < 8 && col >= 0 && col < 8;
}

export function squareToAlgebraic(sq: Square): string {
  const file = String.fromCharCode('a'.charCodeAt(0) + sq.col);
  const rank = 8 - sq.row;
  return `${file}${rank}`;
}

export type Board = (ChessPiece | null)[][];

export interface ChessMove {
  from: Square;
  to: Square;
  promotion?: PieceType | null;
  isEnPassant?: boolean;
  isCastlingKingside?: boolean;
  isCastlingQueenside?: boolean;
}

export interface CastlingRights {
  whiteKingside: boolean;
  whiteQueenside: boolean;
  blackKingside: boolean;
  blackQueenside: boolean;
}

export const INITIAL_CASTLING_RIGHTS: CastlingRights = {
  whiteKingside: true,
  whiteQueenside: true,
  blackKingside: true,
  blackQueenside: true,
};

export interface SacrificeRecipe {
  id: string;
  label: string;
  pieces: PieceType[];
}

export type UnoColor = 'RED' | 'BLUE' | 'GREEN' | 'YELLOW';

export const ALL_UNO_COLORS: UnoColor[] = ['RED', 'BLUE', 'GREEN', 'YELLOW'];

export type SpecialCardKey =
  | 'REVIVE'
  | 'FREEZE'
  | 'SHIELD'
  | 'MERGE'
  | 'SACRIFICE'
  | 'SWAP'
  | 'CLONE'
  | 'HIJACK'
  | 'PROMOTE'
  | 'SUICIDE'
  | 'WILD'
  | 'SNIPER'
  | 'PORTAL'
  | 'TRAP'
  | 'DRAW_TWO';

export const ALL_SPECIAL_CARDS: SpecialCardKey[] = [
  'REVIVE',
  'FREEZE',
  'SHIELD',
  'MERGE',
  'SACRIFICE',
  'SWAP',
  'CLONE',
  'HIJACK',
  'PROMOTE',
  'SUICIDE',
  'WILD',
  'SNIPER',
  'PORTAL',
  'TRAP',
  'DRAW_TWO',
];

export const SPECIAL_CARD_META: Record<
  SpecialCardKey,
  {
    displayName: string;
    shortSymbol: string;
    deckCopies: number;
    description: string;
  }
> = {
  REVIVE: {
    displayName: 'Revive',
    shortSymbol: '+1',
    deckCopies: 3,
    description: 'Turn a Pawn into a captured piece, or place a Pawn.',
  },
  FREEZE: {
    displayName: 'Freeze',
    shortSymbol: '❄',
    deckCopies: 2,
    description: 'Lock 1 enemy piece for a turn + 1 move.',
  },
  SHIELD: {
    displayName: 'Shield',
    shortSymbol: '🛡',
    deckCopies: 2,
    description: 'Protect 1 own piece from capture for 1 enemy turn + 1 move.',
  },
  MERGE: {
    displayName: 'Merge',
    shortSymbol: '⊕',
    deckCopies: 2,
    description: 'Combine or swap your pieces by material value.',
  },
  SACRIFICE: {
    displayName: 'Sacrifice',
    shortSymbol: '❖',
    deckCopies: 2,
    description: 'Split a piece into equal-value pieces nearby.',
  },
  SWAP: {
    displayName: 'Swap',
    shortSymbol: '⇄',
    deckCopies: 2,
    description: 'Exchange the positions of 2 of your pieces safely.',
  },
  CLONE: {
    displayName: 'Clone',
    shortSymbol: '⧉',
    deckCopies: 2,
    description: 'Duplicate a Pawn, Knight, or Bishop to an empty square.',
  },
  HIJACK: {
    displayName: 'Hijack',
    shortSymbol: '⚡',
    deckCopies: 2,
    description: 'Make 1 legal move with an opponent piece.',
  },
  PROMOTE: {
    displayName: 'Promote',
    shortSymbol: '↑',
    deckCopies: 2,
    description: 'Upgrade 1 of your Pawns to a Knight or Bishop.',
  },
  SUICIDE: {
    displayName: 'Suicide',
    shortSymbol: '✕',
    deckCopies: 2,
    description: 'Must eliminate 1 of your pieces (King only = loss).',
  },
  WILD: {
    displayName: 'Wild',
    shortSymbol: '✦',
    deckCopies: 1,
    description: 'Choose any enabled special card effect that can be played.',
  },
  SNIPER: {
    displayName: 'Sniper',
    shortSymbol: '🎯',
    deckCopies: 2,
    description: 'Capture 1 enemy piece along an attack line without moving.',
  },
  PORTAL: {
    displayName: 'Portal',
    shortSymbol: '🌀',
    deckCopies: 2,
    description: 'Teleport 1 of your non-King pieces to any safe empty square.',
  },
  TRAP: {
    displayName: 'Trap',
    shortSymbol: '💣',
    deckCopies: 2,
    description: 'Arm a hidden trap on an empty square. The first piece to step on it is destroyed.',
  },
  DRAW_TWO: {
    displayName: 'Draw Two',
    shortSymbol: '+2',
    deckCopies: 2,
    description: 'Draw 2 cards from the deck and choose which one to play this turn.',
  },
};

export type UnoCardType =
  | { kind: 'NUMBER'; moves: number }
  | { kind: 'REVIVE' }
  | { kind: 'FREEZE' }
  | { kind: 'SHIELD' }
  | { kind: 'MERGE' }
  | { kind: 'SACRIFICE' }
  | { kind: 'SWAP' }
  | { kind: 'CLONE' }
  | { kind: 'HIJACK' }
  | { kind: 'PROMOTE' }
  | { kind: 'SUICIDE' }
  | { kind: 'WILD' }
  | { kind: 'SNIPER' }
  | { kind: 'PORTAL' }
  | { kind: 'TRAP' }
  | { kind: 'DRAW_TWO' };

export interface UnoCard {
  id: number;
  color: UnoColor;
  type: UnoCardType;
}

export function cardGrantsMove(card: UnoCard): boolean {
  return card.type.kind === 'NUMBER';
}

export function cardTitle(type: UnoCardType): string {
  switch (type.kind) {
    case 'NUMBER':
      return type.moves === 1 ? '1 Move' : `${type.moves} Moves`;
    case 'REVIVE':
      return 'Revive';
    case 'FREEZE':
      return 'Freeze';
    case 'SHIELD':
      return 'Shield';
    case 'MERGE':
      return 'Merge';
    case 'SACRIFICE':
      return 'Sacrifice';
    case 'SWAP':
      return 'Swap';
    case 'CLONE':
      return 'Clone';
    case 'HIJACK':
      return 'Hijack';
    case 'PROMOTE':
      return 'Promote';
    case 'SUICIDE':
      return 'Suicide';
    case 'WILD':
      return 'Wild Card';
    case 'SNIPER':
      return 'Sniper';
    case 'PORTAL':
      return 'Portal';
    case 'TRAP':
      return 'Trap';
    case 'DRAW_TWO':
      return 'Draw Two';
  }
}

export function cardBadgeLabel(type: UnoCardType): string {
  switch (type.kind) {
    case 'NUMBER':
      return `${type.moves}`;
    case 'REVIVE':
      return '+1';
    case 'FREEZE':
      return '❄';
    case 'SHIELD':
      return '🛡';
    case 'MERGE':
      return '⊕';
    case 'SACRIFICE':
      return '❖';
    case 'SWAP':
      return '⇄';
    case 'CLONE':
      return '⧉';
    case 'HIJACK':
      return '⚡';
    case 'PROMOTE':
      return '↑';
    case 'SUICIDE':
      return '✕';
    case 'WILD':
      return '✦';
    case 'SNIPER':
      return '🎯';
    case 'PORTAL':
      return '🌀';
    case 'TRAP':
      return '💣';
    case 'DRAW_TWO':
      return '+2';
  }
}

export function cardSubtitle(type: UnoCardType): string {
  switch (type.kind) {
    case 'NUMBER':
      return type.moves === 1 ? 'Make 1 legal move' : `Up to ${type.moves} moves (1/piece)`;
    case 'REVIVE':
      return 'Turn 1 pawn → captured (or +Pawn)';
    case 'FREEZE':
      return 'Freeze 1 enemy piece + 1 move';
    case 'SHIELD':
      return 'Protect 1 own piece + 1 move';
    case 'MERGE':
      return 'Merge or swap pieces by value';
    case 'SACRIFICE':
      return 'Split 1 piece into equal-value pieces';
    case 'SWAP':
      return 'Swap 2 of your pieces safely';
    case 'CLONE':
      return 'Duplicate 1 Pawn, Knight, or Bishop';
    case 'HIJACK':
      return 'Make 1 move with an enemy piece';
    case 'PROMOTE':
      return 'Upgrade 1 pawn → Knight or Bishop';
    case 'SUICIDE':
      return 'Remove 1 own piece (King only = lose)';
    case 'WILD':
      return 'Pick any enabled special card effect';
    case 'SNIPER':
      return 'Capture an enemy piece without moving';
    case 'PORTAL':
      return 'Teleport 1 piece to a safe empty square';
    case 'TRAP':
      return 'Arm a hidden trap on an empty square';
    case 'DRAW_TWO':
      return 'Draw 2 cards and pick 1 to play';
  }
}

export type TurnPhase =
  | 'AWAITING_DRAW'
  | 'MOVING'
  | 'REVIVING'
  | 'FREEZING'
  | 'SHIELDING'
  | 'MERGING'
  | 'SACRIFICING'
  | 'SWAPPING'
  | 'CLONING'
  | 'HIJACKING'
  | 'PROMOTING_PAWN'
  | 'SUICIDING'
  | 'WILD_CHOOSING'
  | 'SNIPING'
  | 'PORTALING'
  | 'TRAPPING'
  | 'DRAW_TWO_CHOOSING'
  | 'GAME_OVER';

export type LogCategory =
  | 'CARD'
  | 'MOVE'
  | 'REVIVE'
  | 'FREEZE'
  | 'SHIELD'
  | 'MERGE'
  | 'SACRIFICE'
  | 'SWAP'
  | 'CLONE'
  | 'HIJACK'
  | 'PROMOTE'
  | 'SUICIDE'
  | 'WILD'
  | 'SNIPER'
  | 'PORTAL'
  | 'TRAP'
  | 'DRAW_TWO'
  | 'CHECK'
  | 'GAME_OVER'
  | 'SYSTEM';

export interface LogEntry {
  id: number;
  message: string;
  category: LogCategory;
}

export interface NoticeBanner {
  id: number;
  message: string;
  isAlert: boolean;
}

export interface PendingPromotion {
  from: Square;
  to: Square;
}

export interface MoveAnimationEvent {
  id: number;
  piece: ChessPiece;
  from: Square;
  to: Square;
  isCapture: boolean;
  secondaryPiece?: ChessPiece | null;
  secondaryFrom?: Square | null;
  secondaryTo?: Square | null;
}

export interface ReviveAnimationEvent {
  id: number;
  piece: ChessPiece;
  square: Square;
}

export type GameOutcome =
  | { kind: 'CHECKMATE'; winnerPlayer: PlayerId; winnerColor: PieceColor }
  | { kind: 'STALEMATE' }
  | { kind: 'FORFEIT'; winnerPlayer: PlayerId; winnerColor: PieceColor; forfeitingPlayer: PlayerId }
  | { kind: 'SUICIDE'; losingPlayer: PlayerId; winnerPlayer: PlayerId; winnerColor: PieceColor };

export type GameMode = 'PASS_AND_PLAY' | 'VS_AI_EASY' | 'VS_AI_MEDIUM' | 'VS_AI_HARD';

export const GAME_MODE_META: Record<
  GameMode,
  { label: string; shortLabel: string; isAi: boolean }
> = {
  PASS_AND_PLAY: { label: '2 Players (Pass & Play)', shortLabel: '2P Local', isAi: false },
  VS_AI_EASY: { label: 'vs AI · Easy', shortLabel: 'AI Easy', isAi: true },
  VS_AI_MEDIUM: { label: 'vs AI · Medium', shortLabel: 'AI Med', isAi: true },
  VS_AI_HARD: { label: 'vs AI · Hard', shortLabel: 'AI Hard', isAi: true },
};

export type UnoCardTheme = 'CLASSIC' | 'SPACE' | 'FANTASY' | 'SPIRITS';

export const UNO_CARD_THEME_META: Record<
  UnoCardTheme,
  { displayName: string; description: string }
> = {
  CLASSIC: {
    displayName: 'Classic',
    description: 'Clean UNO-inspired bold geometric cards.',
  },
  SPACE: {
    displayName: 'Space',
    description: 'Deep starfields, planetary rings & cosmic nebulae.',
  },
  FANTASY: {
    displayName: 'Fantasy',
    description: 'Gilded grimoire borders, arcane runes & enchanted seals.',
  },
  SPIRITS: {
    displayName: 'Top Shelf Spirits',
    description: 'Bourbon, gin, absinthe & cognac apothecary bottle labels.',
  },
};

export type MatchResultType = 'WIN' | 'LOSS' | 'DRAW';

export interface MatchRecord {
  id: number;
  timestampMs: number;
  gameMode: string;
  result: MatchResultType;
  winnerSummary: string;
  reason: string;
  turnsPlayed: number;
}

export interface MatchStats {
  currentWinStreak: number;
  bestWinStreak: number;
  totalWins: number;
  totalLosses: number;
  totalDraws: number;
  history: MatchRecord[];
}

export const EMPTY_MATCH_STATS: MatchStats = {
  currentWinStreak: 0,
  bestWinStreak: 0,
  totalWins: 0,
  totalLosses: 0,
  totalDraws: 0,
  history: [],
};

export interface Trap {
  id: number;
  row: number;
  col: number;
  owner: PieceColor;
}

export interface GameUiState {
  board: Board;
  activePlayer: PlayerId;
  playerColors: Record<PlayerId, PieceColor>;
  phase: TurnPhase;
  drawPile: UnoCard[];
  discardPile: UnoCard[];
  activeCard: UnoCard | null;
  drawRevision: number;
  totalMovesForCard: number;
  movesRemaining: number;
  revivesTotalForCard: number;
  revivesRemaining: number;
  majorRevivedThisTurn: boolean;
  movedPieceIdsThisTurn: number[];
  frozenPieceId: number | null;
  frozenOwner: PieceColor | null;
  validFreezeSquares: Square[];
  shieldedPieceId: number | null;
  shieldedOwner: PieceColor | null;
  validShieldSquares: Square[];
  selectedSwapSquare: Square | null;
  validSwapSquares: Square[];
  selectedCloneSourceSquare: Square | null;
  validCloneSquares: Square[];
  availableWildEffects: SpecialCardKey[];
  selectedMergeSquares: Square[];
  selectedSacrificeSquare: Square | null;
  validSacrificeSquares: Square[];
  selectedPromotePawnSquare: Square | null;
  validPromotePawnSquares: Square[];
  validSuicideSquares: Square[];
  traps: Trap[];
  validTrapSquares: Square[];
  selectedSniperSquare: Square | null;
  validSniperSources: Square[];
  validSniperTargets: Square[];
  selectedPortalSourceSquare: Square | null;
  validPortalSources: Square[];
  validPortalDestinations: Square[];
  drawTwoPendingCards: [UnoCard, UnoCard] | null;
  selectedSquare: Square | null;
  legalMovesForSelected: ChessMove[];
  capturedPieces: Record<PieceColor, ChessPiece[]>;
  selectedRevivePieceId: number | null;
  validReviveSquares: Square[];
  lastMoveFrom: Square | null;
  lastMoveTo: Square | null;
  lastRevivedSquare: Square | null;
  checkedKingSquare: Square | null;
  pendingPromotion: PendingPromotion | null;
  noticeBanner: NoticeBanner | null;
  moveAnimationEvent: MoveAnimationEvent | null;
  reviveAnimationEvent: ReviveAnimationEvent | null;
  gameLog: LogEntry[];
  outcome: GameOutcome | null;
  castlingRights: CastlingRights;
  enPassantTarget: Square | null;
  enPassantOwner: PieceColor | null;
  gameMode: GameMode;
  isAiThinking: boolean;
  isDarkTheme: boolean;
  cardTheme: UnoCardTheme;
  isSoundMuted: boolean;
  movePityCount: Record<PlayerId, number>;
  enabledSpecialCards: SpecialCardKey[];
}

export function getActiveColor(state: GameUiState): PieceColor {
  return state.playerColors[state.activePlayer];
}

export function getActivePlayerPity(state: GameUiState): number {
  return state.movePityCount[state.activePlayer] ?? 0;
}

export function isPieceFrozen(state: GameUiState, piece: ChessPiece): boolean {
  return state.frozenPieceId === piece.id && state.frozenOwner === piece.color;
}

export function isPieceShielded(state: GameUiState, piece: ChessPiece): boolean {
  return state.shieldedPieceId === piece.id && state.shieldedOwner === piece.color;
}

export function getActorName(
  player: PlayerId,
  gameMode: GameMode,
  isOnlineMultiplayer = false,
  localPlayer: PlayerId | null = null
): string {
  if (isOnlineMultiplayer && localPlayer) {
    return player === localPlayer ? 'You' : 'Opponent';
  }
  if (GAME_MODE_META[gameMode].isAi) {
    return player === 'PLAYER_1' ? 'You' : 'AI';
  }
  return playerDisplayName(player);
}

// Online Multiplayer Models
export type LanRole = 'NONE' | 'ONLINE_HOST' | 'ONLINE_GUEST';

export type LanConnectionStatus =
  | 'DISCONNECTED'
  | 'HOSTING_WAITING'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'ERROR';

export interface OpenLobbySummary {
  code: string;
  hostName: string;
  createdAtMs: number;
  enabledSpecialsCount: number;
}

export interface LanSessionState {
  role: LanRole;
  status: LanConnectionStatus;
  roomCode: string | null;
  statusMessage: string | null;
  openLobbies: OpenLobbySummary[];
  isListingLobbies: boolean;
}

export const INITIAL_LAN_SESSION: LanSessionState = {
  role: 'NONE',
  status: 'DISCONNECTED',
  roomCode: null,
  statusMessage: null,
  openLobbies: [],
  isListingLobbies: false,
};

export function localPlayerForRole(role: LanRole): PlayerId | null {
  if (role === 'ONLINE_HOST') return 'PLAYER_1';
  if (role === 'ONLINE_GUEST') return 'PLAYER_2';
  return null;
}

export type LanAction =
  | { type: 'DRAW' }
  | { type: 'TAP'; square: Square }
  | { type: 'PROMO'; pieceType: PieceType }
  | { type: 'CANCEL_PROMO' }
  | { type: 'REVIVE'; pieceId: number }
  | { type: 'CONFIRM_MERGE'; targetType: PieceType }
  | { type: 'CLEAR_MERGE' }
  | { type: 'SKIP_MERGE' }
  | { type: 'CONFIRM_SACRIFICE'; recipeId: string }
  | { type: 'SKIP_SACRIFICE' }
  | { type: 'CONFIRM_PAWN_PROMOTE'; targetType: PieceType }
  | { type: 'CLEAR_SWAP' }
  | { type: 'SKIP_SWAP' }
  | { type: 'CLEAR_CLONE' }
  | { type: 'SKIP_CLONE' }
  | { type: 'SELECT_WILD'; effect: SpecialCardKey }
  | { type: 'CHOOSE_DRAW_TWO'; index: number }
  | { type: 'SKIP_SNIPER' }
  | { type: 'SKIP_PORTAL' }
  | { type: 'SKIP_TRAP' }
  | { type: 'FORFEIT' }
  | { type: 'NEW_GAME' };
