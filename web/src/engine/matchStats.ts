import {
  EMPTY_MATCH_STATS,
  GameMode,
  MatchRecord,
  MatchResultType,
  MatchStats,
  UnoCardTheme,
} from '../model/gameModels';

const STATS_KEY = 'unochess_match_stats_v1';
const PREFS_KEY = 'unochess_prefs_v1';
const MAX_HISTORY = 50;

export interface StoredPreferences {
  isDarkTheme: boolean;
  cardTheme: UnoCardTheme;
  isSoundMuted: boolean;
  gameMode: GameMode;
}

const DEFAULT_PREFS: StoredPreferences = {
  isDarkTheme: false,
  cardTheme: 'CLASSIC',
  isSoundMuted: false,
  gameMode: 'PASS_AND_PLAY',
};

export function loadPreferences(): StoredPreferences {
  try {
    const raw = localStorage.getItem(PREFS_KEY);
    if (!raw) return DEFAULT_PREFS;
    const parsed = JSON.parse(raw) as Partial<StoredPreferences>;
    return {
      isDarkTheme: Boolean(parsed.isDarkTheme),
      cardTheme:
        parsed.cardTheme === 'CLASSIC' ||
        parsed.cardTheme === 'SPACE' ||
        parsed.cardTheme === 'FANTASY' ||
        parsed.cardTheme === 'SPIRITS'
          ? parsed.cardTheme
          : 'CLASSIC',
      isSoundMuted: Boolean(parsed.isSoundMuted),
      gameMode:
        parsed.gameMode === 'PASS_AND_PLAY' ||
        parsed.gameMode === 'VS_AI_EASY' ||
        parsed.gameMode === 'VS_AI_MEDIUM' ||
        parsed.gameMode === 'VS_AI_HARD'
          ? parsed.gameMode
          : 'PASS_AND_PLAY',
    };
  } catch {
    return DEFAULT_PREFS;
  }
}

export function savePreferences(prefs: StoredPreferences): void {
  try {
    localStorage.setItem(PREFS_KEY, JSON.stringify(prefs));
  } catch {
    // Ignore storage quota errors
  }
}

export function loadMatchStats(): MatchStats {
  try {
    const raw = localStorage.getItem(STATS_KEY);
    if (!raw) return EMPTY_MATCH_STATS;
    const parsed = JSON.parse(raw) as Partial<MatchStats>;
    return {
      currentWinStreak: parsed.currentWinStreak ?? 0,
      bestWinStreak: parsed.bestWinStreak ?? 0,
      totalWins: parsed.totalWins ?? 0,
      totalLosses: parsed.totalLosses ?? 0,
      totalDraws: parsed.totalDraws ?? 0,
      history: Array.isArray(parsed.history) ? parsed.history : [],
    };
  } catch {
    return EMPTY_MATCH_STATS;
  }
}

export function recordMatchResult(
  prev: MatchStats,
  params: {
    gameMode: string;
    result: MatchResultType;
    winnerSummary: string;
    reason: string;
    turnsPlayed: number;
  }
): MatchStats {
  const nextStreak =
    params.result === 'WIN'
      ? prev.currentWinStreak + 1
      : params.result === 'LOSS'
        ? 0
        : prev.currentWinStreak;

  const record: MatchRecord = {
    id: Date.now(),
    timestampMs: Date.now(),
    gameMode: params.gameMode,
    result: params.result,
    winnerSummary: params.winnerSummary,
    reason: params.reason,
    turnsPlayed: params.turnsPlayed,
  };

  const next: MatchStats = {
    currentWinStreak: nextStreak,
    bestWinStreak: Math.max(prev.bestWinStreak, nextStreak),
    totalWins: prev.totalWins + (params.result === 'WIN' ? 1 : 0),
    totalLosses: prev.totalLosses + (params.result === 'LOSS' ? 1 : 0),
    totalDraws: prev.totalDraws + (params.result === 'DRAW' ? 1 : 0),
    history: [record, ...prev.history].slice(0, MAX_HISTORY),
  };

  try {
    localStorage.setItem(STATS_KEY, JSON.stringify(next));
  } catch {
    // Ignore storage quota errors
  }
  return next;
}

export function clearMatchStats(): MatchStats {
  try {
    localStorage.removeItem(STATS_KEY);
  } catch {
    // Ignore
  }
  return EMPTY_MATCH_STATS;
}
