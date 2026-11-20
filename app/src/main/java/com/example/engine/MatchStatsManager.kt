package com.example.engine

import android.content.Context
import com.example.model.GameOutcome
import com.example.model.MatchEndReason
import com.example.model.MatchRecord
import com.example.model.MatchStats
import com.example.model.OpponentMode
import com.example.model.PieceColor
import com.example.model.PlayerId
import org.json.JSONArray
import org.json.JSONObject

object MatchStatsManager {

    private const val PREFS_NAME = "uno_chess_prefs"
    private const val KEY_MATCH_STATS = "match_stats_v1"
    private const val MAX_HISTORY = 50

    fun recordOutcome(
        current: MatchStats,
        outcome: GameOutcome,
        opponentMode: OpponentMode,
        isLanConnected: Boolean,
        totalTurns: Int,
        winnerLabelOverride: String? = null,
        timestampMs: Long = System.currentTimeMillis()
    ): MatchStats {
        val modeLabel = when {
            isLanConnected -> "Wi-Fi LAN"
            opponentMode is OpponentMode.VsAi -> "vs AI (${opponentMode.difficulty.displayName})"
            else -> "Pass & Play"
        }

        val winnerPlayer: PlayerId?
        val winnerColor: PieceColor?
        val reason: MatchEndReason
        when (outcome) {
            is GameOutcome.Checkmate -> {
                winnerPlayer = outcome.winnerPlayer
                winnerColor = outcome.winnerColor
                reason = MatchEndReason.CHECKMATE
            }
            is GameOutcome.Forfeit -> {
                winnerPlayer = outcome.winnerPlayer
                winnerColor = outcome.winnerColor
                reason = MatchEndReason.FORFEIT
            }
            is GameOutcome.Suicide -> {
                winnerPlayer = outcome.winnerPlayer
                winnerColor = outcome.winnerColor
                reason = MatchEndReason.SUICIDE
            }
            GameOutcome.Stalemate -> {
                winnerPlayer = null
                winnerColor = null
                reason = MatchEndReason.STALEMATE
            }
        }

        val winnerLabel = when {
            winnerPlayer == null -> "Draw"
            winnerLabelOverride != null -> winnerLabelOverride
            !isLanConnected && opponentMode is OpponentMode.VsAi && winnerPlayer == PlayerId.PLAYER_2 ->
                "AI (${opponentMode.difficulty.displayName})"
            else -> winnerPlayer.displayName
        }

        val nextId = (current.history.maxOfOrNull { it.id } ?: 0) + 1
        val record = MatchRecord(
            id = nextId,
            timestampMs = timestampMs,
            modeLabel = modeLabel,
            winnerLabel = winnerLabel,
            winnerPlayer = winnerPlayer,
            winnerColor = winnerColor,
            reason = reason,
            totalTurns = totalTurns.coerceAtLeast(1)
        )

        var newStreak = current.currentStreak
        var newHolder = current.streakHolder
        var winsP1 = current.totalWinsP1
        var winsP2 = current.totalWinsP2
        var draws = current.totalDraws

        when (winnerPlayer) {
            null -> {
                draws++
                newStreak = 0
                newHolder = null
            }
            PlayerId.PLAYER_1 -> {
                winsP1++
                if (newHolder == winnerLabel) {
                    newStreak += 1
                } else {
                    newStreak = 1
                    newHolder = winnerLabel
                }
            }
            PlayerId.PLAYER_2 -> {
                winsP2++
                if (!isLanConnected && opponentMode is OpponentMode.VsAi) {
                    // In vs AI mode, win streak tracks Player 1's consecutive wins against AI
                    newStreak = 0
                    newHolder = null
                } else {
                    if (newHolder == winnerLabel) {
                        newStreak += 1
                    } else {
                        newStreak = 1
                        newHolder = winnerLabel
                    }
                }
            }
        }

        val newBest = maxOf(current.bestStreak, newStreak)
        return MatchStats(
            currentStreak = newStreak,
            bestStreak = newBest,
            streakHolder = newHolder,
            totalWinsP1 = winsP1,
            totalWinsP2 = winsP2,
            totalDraws = draws,
            history = (listOf(record) + current.history).take(MAX_HISTORY)
        )
    }

    fun load(context: Context): MatchStats {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_MATCH_STATS, null) ?: return MatchStats()
        return decode(raw) ?: MatchStats()
    }

    fun save(context: Context, stats: MatchStats) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MATCH_STATS, encode(stats))
            .apply()
    }

    fun encode(stats: MatchStats): String {
        val obj = JSONObject()
        obj.put("currentStreak", stats.currentStreak)
        obj.put("bestStreak", stats.bestStreak)
        obj.put("streakHolder", stats.streakHolder ?: JSONObject.NULL)
        obj.put("totalWinsP1", stats.totalWinsP1)
        obj.put("totalWinsP2", stats.totalWinsP2)
        obj.put("totalDraws", stats.totalDraws)
        val arr = JSONArray()
        stats.history.take(MAX_HISTORY).forEach { r ->
            arr.put(
                JSONObject().apply {
                    put("id", r.id)
                    put("ts", r.timestampMs)
                    put("mode", r.modeLabel)
                    put("winner", r.winnerLabel)
                    put("wp", r.winnerPlayer?.name ?: JSONObject.NULL)
                    put("wc", r.winnerColor?.name ?: JSONObject.NULL)
                    put("reason", r.reason.name)
                    put("turns", r.totalTurns)
                }
            )
        }
        obj.put("history", arr)
        return obj.toString()
    }

    fun decode(json: String): MatchStats? {
        return runCatching {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("history") ?: JSONArray()
            val history = List(arr.length()) { i ->
                val item = arr.getJSONObject(i)
                MatchRecord(
                    id = item.getInt("id"),
                    timestampMs = item.optLong("ts", 0L),
                    modeLabel = item.optString("mode", "Pass & Play"),
                    winnerLabel = item.optString("winner", "Draw"),
                    winnerPlayer = if (item.isNull("wp")) null else PlayerId.valueOf(item.getString("wp")),
                    winnerColor = if (item.isNull("wc")) null else PieceColor.valueOf(item.getString("wc")),
                    reason = MatchEndReason.valueOf(item.getString("reason")),
                    totalTurns = item.optInt("turns", 1)
                )
            }
            MatchStats(
                currentStreak = obj.optInt("currentStreak", 0),
                bestStreak = obj.optInt("bestStreak", 0),
                streakHolder = if (obj.isNull("streakHolder")) null else obj.optString("streakHolder"),
                totalWinsP1 = obj.optInt("totalWinsP1", 0),
                totalWinsP2 = obj.optInt("totalWinsP2", 0),
                totalDraws = obj.optInt("totalDraws", 0),
                history = history
            )
        }.getOrNull()
    }
}
