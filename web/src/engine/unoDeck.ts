import {
  ALL_SPECIAL_CARDS,
  ALL_UNO_COLORS,
  SPECIAL_CARD_META,
  SpecialCardKey,
  UnoCard,
  UnoColor,
} from '../model/gameModels';

export const NUMBER_CARDS_COUNT = 16;

export function deckSizeFor(enabledSpecials: SpecialCardKey[]): number {
  const unique = new Set(enabledSpecials);
  let total = NUMBER_CARDS_COUNT;
  for (const key of unique) {
    total += SPECIAL_CARD_META[key].deckCopies;
  }
  return total;
}

function shuffle<T>(items: T[]): T[] {
  const arr = [...items];
  for (let i = arr.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [arr[i], arr[j]] = [arr[j], arr[i]];
  }
  return arr;
}

export function createShuffledDeck(
  enabledSpecials: SpecialCardKey[] = ALL_SPECIAL_CARDS
): UnoCard[] {
  const enabled = new Set(enabledSpecials);
  let nextId = 1;
  const cards: UnoCard[] = [];

  for (let moves = 1; moves <= 4; moves++) {
    for (const color of ALL_UNO_COLORS) {
      cards.push({ id: nextId++, color, type: { kind: 'NUMBER', moves } });
    }
  }

  const addSpecial = (key: SpecialCardKey, colors: UnoColor[], kind: UnoCard['type']['kind']) => {
    if (!enabled.has(key)) return;
    for (const color of colors) {
      cards.push({ id: nextId++, color, type: { kind } as UnoCard['type'] });
    }
  };

  addSpecial('REVIVE', ['RED', 'GREEN', 'BLUE'], 'REVIVE');
  addSpecial('FREEZE', ['BLUE', 'YELLOW'], 'FREEZE');
  addSpecial('SHIELD', ['BLUE', 'GREEN'], 'SHIELD');
  addSpecial('MERGE', ['GREEN', 'YELLOW'], 'MERGE');
  addSpecial('SACRIFICE', ['RED', 'BLUE'], 'SACRIFICE');
  addSpecial('SWAP', ['YELLOW', 'BLUE'], 'SWAP');
  addSpecial('CLONE', ['GREEN', 'RED'], 'CLONE');
  addSpecial('HIJACK', ['RED', 'BLUE'], 'HIJACK');
  addSpecial('PROMOTE', ['YELLOW', 'GREEN'], 'PROMOTE');
  addSpecial('SUICIDE', ['RED', 'YELLOW'], 'SUICIDE');
  addSpecial('WILD', ['YELLOW'], 'WILD');
  addSpecial('SNIPER', ['RED', 'BLUE'], 'SNIPER');
  addSpecial('PORTAL', ['BLUE', 'YELLOW'], 'PORTAL');
  addSpecial('TRAP', ['RED', 'YELLOW'], 'TRAP');
  addSpecial('DRAW_TWO', ['GREEN', 'BLUE'], 'DRAW_TWO');

  return shuffle(cards);
}

export interface PityChances {
  oneMovePercent: number;
  twoMovesPercent: number;
  threeMovesPercent: number;
  fourMovesPercent: number;
  naturalDrawPercent: number;
}

export function getPityChances(pityCount: number): PityChances {
  if (pityCount <= 0) {
    return {
      oneMovePercent: 0,
      twoMovesPercent: 0,
      threeMovesPercent: 0,
      fourMovesPercent: 0,
      naturalDrawPercent: 100,
    };
  }
  if (pityCount === 1) {
    return {
      oneMovePercent: 15,
      twoMovesPercent: 25,
      threeMovesPercent: 10,
      fourMovesPercent: 5,
      naturalDrawPercent: 45,
    };
  }
  if (pityCount === 2) {
    return {
      oneMovePercent: 10,
      twoMovesPercent: 40,
      threeMovesPercent: 25,
      fourMovesPercent: 15,
      naturalDrawPercent: 10,
    };
  }
  if (pityCount === 3) {
    return {
      oneMovePercent: 5,
      twoMovesPercent: 20,
      threeMovesPercent: 40,
      fourMovesPercent: 35,
      naturalDrawPercent: 0,
    };
  }
  return {
    oneMovePercent: 0,
    twoMovesPercent: 10,
    threeMovesPercent: 40,
    fourMovesPercent: 50,
    naturalDrawPercent: 0,
  };
}

export function rollPityTarget(pityCount: number): number | null {
  if (pityCount <= 0) return null;
  const chances = getPityChances(pityCount);
  if (chances.naturalDrawPercent >= 100) return null;

  const roll = Math.floor(Math.random() * 100);
  let cumulative = 0;

  if (pityCount >= 4) {
    cumulative += chances.fourMovesPercent;
    if (roll < cumulative) return 4;
    cumulative += chances.threeMovesPercent;
    if (roll < cumulative) return 3;
    cumulative += chances.twoMovesPercent;
    if (roll < cumulative) return 2;
    cumulative += chances.oneMovePercent;
    if (roll < cumulative) return 1;
  } else if (pityCount === 3) {
    cumulative += chances.threeMovesPercent;
    if (roll < cumulative) return 3;
    cumulative += chances.fourMovesPercent;
    if (roll < cumulative) return 4;
    cumulative += chances.twoMovesPercent;
    if (roll < cumulative) return 2;
    cumulative += chances.oneMovePercent;
    if (roll < cumulative) return 1;
  } else {
    cumulative += chances.twoMovesPercent;
    if (roll < cumulative) return 2;
    cumulative += chances.threeMovesPercent;
    if (roll < cumulative) return 3;
    cumulative += chances.fourMovesPercent;
    if (roll < cumulative) return 4;
    cumulative += chances.oneMovePercent;
    if (roll < cumulative) return 1;
  }
  return null;
}

export function drawCardWithPity(
  drawPile: UnoCard[],
  discardPile: UnoCard[],
  pityCount: number,
  enabledSpecials: SpecialCardKey[]
): UnoCard {
  if (drawPile.length === 0) {
    if (discardPile.length === 0) {
      drawPile.push(...createShuffledDeck(enabledSpecials));
    } else {
      drawPile.push(...shuffle(discardPile));
      discardPile.length = 0;
    }
  }

  const targetMoves = rollPityTarget(pityCount);
  if (targetMoves !== null) {
    const idx = drawPile.findIndex(
      (c) => c.type.kind === 'NUMBER' && c.type.moves === targetMoves
    );
    if (idx !== -1) return drawPile.splice(idx, 1)[0];

    const discardIdx = discardPile.findIndex(
      (c) => c.type.kind === 'NUMBER' && c.type.moves === targetMoves
    );
    if (discardIdx !== -1) {
      drawPile.push(...shuffle(discardPile));
      discardPile.length = 0;
      const reshuffledIdx = drawPile.findIndex(
        (c) => c.type.kind === 'NUMBER' && c.type.moves === targetMoves
      );
      if (reshuffledIdx !== -1) return drawPile.splice(reshuffledIdx, 1)[0];
    }

    const fallbackIdx = drawPile.findIndex((c) => c.type.kind === 'NUMBER');
    if (fallbackIdx !== -1) return drawPile.splice(fallbackIdx, 1)[0];

    if (discardPile.some((c) => c.type.kind === 'NUMBER')) {
      drawPile.push(...shuffle(discardPile));
      discardPile.length = 0;
      const reshuffledFallback = drawPile.findIndex((c) => c.type.kind === 'NUMBER');
      if (reshuffledFallback !== -1) return drawPile.splice(reshuffledFallback, 1)[0];
    }
  }

  return drawPile.shift()!;
}
