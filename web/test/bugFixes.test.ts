import assert from 'node:assert';
import {
  createInitialGameState,
  executeChessMove,
  performPromotionSelected,
  TransitionContext,
} from '../src/state/gameTransitions';
import { handlePortalSquareTap } from '../src/state/specialTransitions';
import { choosePortalAction } from '../src/engine/unoChessAi';
import { ChessPiece, GameUiState, Square } from '../src/model/gameModels';

const dummyCtx: TransitionContext = {
  nextLogId: () => 1,
  nextNoticeId: () => 1,
  nextAnimId: () => 1,
  isOnlineMultiplayer: false,
  localPlayer: null,
};

function emptyBoard(): (ChessPiece | null)[][] {
  return Array.from({ length: 8 }, () => Array.from({ length: 8 }, () => null));
}

function createTestState(gameMode: 'PASS_AND_PLAY' | 'VS_AI_HARD' = 'PASS_AND_PLAY'): GameUiState {
  return createInitialGameState(
    {
      gameMode,
      isDarkTheme: false,
      cardTheme: 'MODERN',
      isSoundMuted: true,
    },
    dummyCtx
  );
}

function testKingStepsOnMine() {
  console.log('Testing: King steps on mine ends game in checkmate for opponent...');
  const base = createTestState('PASS_AND_PLAY');
  const board = emptyBoard();
  board[6][4] = { id: 1, type: 'KING', color: 'WHITE' };
  board[0][4] = { id: 2, type: 'KING', color: 'BLACK' };

  const state: GameUiState = {
    ...base,
    board,
    activePlayer: 'PLAYER_1',
    phase: 'MOVING',
    traps: [{ row: 5, col: 4, placedBy: 'PLAYER_2' }],
  };

  const move = {
    from: { row: 6, col: 4 },
    to: { row: 5, col: 4 },
    piece: board[6][4]!,
  };

  const nextState = executeChessMove(state, move, dummyCtx);

  assert.strictEqual(nextState.board[6][4], null, 'King left source square');
  assert.strictEqual(nextState.board[5][4], null, 'King was destroyed by trap');
  assert.strictEqual(nextState.traps.length, 0, 'Trap was consumed');
  assert.strictEqual(nextState.phase, 'GAME_OVER', 'Phase is GAME_OVER');
  assert.deepStrictEqual(
    nextState.outcome,
    { kind: 'CHECKMATE', winnerPlayer: 'PLAYER_2', winnerColor: 'BLACK' },
    'Outcome is Checkmate for PLAYER_2'
  );
  console.log('✓ King steps on mine test passed');
}

function testPawnPortalPromotion() {
  console.log('Testing: Pawn portal to back rank triggers promotion and promotes to chosen piece...');
  const base = createTestState('PASS_AND_PLAY');
  const board = emptyBoard();
  board[1][4] = { id: 10, type: 'PAWN', color: 'WHITE' };
  board[7][4] = { id: 1, type: 'KING', color: 'WHITE' };
  board[0][0] = { id: 2, type: 'KING', color: 'BLACK' };

  const state: GameUiState = {
    ...base,
    board,
    activePlayer: 'PLAYER_1',
    phase: 'PORTALING',
    validPortalSources: [{ row: 1, col: 4 }],
    traps: [],
  };

  // Step 1: tap source pawn
  const step1 = handlePortalSquareTap(state, { row: 1, col: 4 }, dummyCtx);
  assert.deepStrictEqual(step1.selectedPortalSourceSquare, { row: 1, col: 4 });
  assert.ok(
    step1.validPortalDestinations.some((d: Square) => d.row === 0 && d.col === 4),
    'Row 0 col 4 is valid portal destination'
  );

  // Step 2: tap back rank destination
  const step2 = handlePortalSquareTap(step1, { row: 0, col: 4 }, dummyCtx);
  assert.ok(step2.pendingPromotion !== null, 'pendingPromotion must be set');
  assert.deepStrictEqual(step2.pendingPromotion?.from, { row: 1, col: 4 });
  assert.deepStrictEqual(step2.pendingPromotion?.to, { row: 0, col: 4 });

  // Step 3: choose Queen promotion
  const step3 = performPromotionSelected(step2, 'QUEEN', dummyCtx);
  assert.strictEqual(step3.pendingPromotion, null, 'pendingPromotion cleared');
  assert.strictEqual(step3.board[1][4], null, 'Original pawn removed');
  const piece = step3.board[0][4];
  assert.ok(piece !== null, 'Piece placed on back rank');
  assert.strictEqual(piece?.type, 'QUEEN', 'Piece promoted to Queen');
  assert.strictEqual(piece?.color, 'WHITE', 'Piece is White');
  assert.strictEqual(step3.phase, 'AWAITING_DRAW', 'Turn passed');
  assert.strictEqual(step3.activePlayer, 'PLAYER_2', 'Active player is PLAYER_2');
  console.log('✓ Pawn portal promotion test passed');
}

function testPawnPortalTrap() {
  console.log('Testing: Pawn portal to back rank with trap destroys pawn without promotion...');
  const base = createTestState('PASS_AND_PLAY');
  const board = emptyBoard();
  board[1][4] = { id: 10, type: 'PAWN', color: 'WHITE' };
  board[7][4] = { id: 1, type: 'KING', color: 'WHITE' };
  board[0][0] = { id: 2, type: 'KING', color: 'BLACK' };

  const state: GameUiState = {
    ...base,
    board,
    activePlayer: 'PLAYER_1',
    phase: 'PORTALING',
    validPortalSources: [{ row: 1, col: 4 }],
    traps: [{ row: 0, col: 4, placedBy: 'PLAYER_2' }],
  };

  const step1 = handlePortalSquareTap(state, { row: 1, col: 4 }, dummyCtx);
  const step2 = handlePortalSquareTap(step1, { row: 0, col: 4 }, dummyCtx);

  assert.strictEqual(step2.pendingPromotion, null, 'No pending promotion dialog when trap hit');
  assert.strictEqual(step2.board[1][4], null, 'Pawn removed from source');
  assert.strictEqual(step2.board[0][4], null, 'Pawn destroyed by trap on destination');
  assert.strictEqual(step2.traps.length, 0, 'Trap removed');
  assert.strictEqual(step2.phase, 'AWAITING_DRAW', 'Turn ended');
  assert.strictEqual(step2.activePlayer, 'PLAYER_2', 'Active player is PLAYER_2');
  console.log('✓ Pawn portal trap test passed');
}

function testAiPortalDecision() {
  console.log('Testing: AI portal decision prioritizes pawn promotion...');
  const base = createTestState('VS_AI_HARD');
  const board = emptyBoard();
  board[6][4] = { id: 10, type: 'PAWN', color: 'BLACK' };
  board[6][0] = { id: 11, type: 'BISHOP', color: 'BLACK' };
  board[0][4] = { id: 2, type: 'KING', color: 'BLACK' };
  board[7][0] = { id: 1, type: 'KING', color: 'WHITE' };

  const state: GameUiState = {
    ...base,
    board,
    activePlayer: 'PLAYER_2',
    phase: 'PORTALING',
    validPortalSources: [{ row: 6, col: 4 }, { row: 6, col: 0 }],
  };

  const decision = choosePortalAction(state, 'VS_AI_HARD');
  assert.ok(decision !== null, 'Decision should not be null');
  assert.deepStrictEqual(decision?.pieceSquare, { row: 6, col: 4 }, 'AI chose pawn');
  assert.strictEqual(decision?.destSquare.row, 7, 'AI teleported pawn to row 7 (promotion rank)');
  console.log('✓ AI portal decision test passed');
}

testKingStepsOnMine();
testPawnPortalPromotion();
testPawnPortalTrap();
testAiPortalDecision();
console.log('\nAll web tests passed successfully!');
