package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.model.GameOutcome
import com.example.model.LogCategory
import com.example.model.LogEntry
import com.example.model.MatchEndReason
import com.example.model.MatchRecord
import com.example.model.MatchStats
import com.example.model.OpponentMode
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.Square
import com.example.ui.theme.LocalUnoChessColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PromotionPickerDialog(
    pieceColor: PieceColor,
    targetSquare: Square,
    onSelect: (PieceType) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    val options = listOf(
        PieceType.QUEEN,
        PieceType.ROOK,
        PieceType.BISHOP,
        PieceType.KNIGHT
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier.testTag("promotion_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.promotion_dialog_title),
                    style = MaterialTheme.typography.titleLarge.copy(color = colors.inkPrimary)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${pieceColor.displayName} pawn on ${targetSquare.toAlgebraic().uppercase()}",
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.inkSecondary),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { pieceType ->
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceSubtle)
                                .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                                .testTag("promote_option_${pieceType.name.lowercase()}")
                                .clickable { onSelect(pieceType) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(modifier = Modifier.size(44.dp)) {
                                ChessPieceGraphic(type = pieceType, color = pieceColor)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = pieceType.displayName,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = colors.inkPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("promote_cancel_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_cancel),
                        color = colors.inkSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun RulesModalDialog(
    onDismiss: () -> Unit
) {
    val colors = LocalUnoChessColors.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("rules_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.rules_modal_title),
                        style = MaterialTheme.typography.titleLarge.copy(color = colors.inkPrimary)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("rules_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colors.inkSecondary
                        )
                    }
                }

                HorizontalDivider(color = colors.hairline, modifier = Modifier.padding(vertical = 8.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RuleItem(
                        title = "Number Cards (1–4)",
                        body = "Draw a card to start your turn. Numbers 1–4 grant that many consecutive legal chess moves, with a maximum of 1 move per piece per turn."
                    )
                    RuleItem(
                        title = "+1 Revive (Pawn Transform or Spawn)",
                        body = "Capped to 1 piece. If you have captured pieces, pick one and tap one of your Pawns on the board to transform that Pawn into the revived piece (auto-redraws if you have no eligible Pawn). If you have no captured pieces yet, tap a highlighted square on ranks 2–3 (White) or 6–7 (Black) to place a new Pawn."
                    )
                    RuleItem(
                        title = "Freeze",
                        body = "Lock 1 enemy piece (not the King) through their next turn, then make 1 follow-up chess move. Auto-thaws immediately if the frozen player starts their turn in check."
                    )
                    RuleItem(
                        title = "Merge (Swap or Combine)",
                        body = "Select 1 or more of your non-King pieces (P=1, N/B=3, R=5, Q=9) to trade into an equal- or lower-value piece—or merge multiple pieces (e.g., 3 Pawns → Bishop or Knight) onto the first selected square. You may also Skip."
                    )
                    RuleItem(
                        title = "Sacrifice (Split)",
                        body = "Pick 1 of your pieces (Knight, Bishop, Rook, or Queen) and split it into equal-value pieces placed on and around that square (e.g., Rook → Bishop + 2 Pawns; Knight/Bishop → 3 Pawns). Cannot leave your King in check or immediately give check; you may also Skip."
                    )
                    RuleItem(
                        title = "Hijack",
                        body = "Take control of 1 opponent piece for a single legal move (cannot leave your own King in check or put the opponent in illegal self-check)."
                    )
                    RuleItem(
                        title = "Promote",
                        body = "Promote 1 of your unfrozen Pawns on the spot into a Knight or Bishop without needing to reach the back rank."
                    )
                    RuleItem(
                        title = "Shield",
                        body = "Protect 1 of your non-King pieces from being captured (including en passant and Hijack) throughout your opponent's next turn, then make 1 follow-up chess move."
                    )
                    RuleItem(
                        title = "Swap Positions",
                        body = "Pick any 2 of your unfrozen pieces and exchange their squares (cannot place Pawns on rank 1 or 8, leave your King in check, or immediately check the opponent). You may also Skip."
                    )
                    RuleItem(
                        title = "Clone",
                        body = "Duplicate 1 of your unfrozen Pawns, Knights, or Bishops onto an adjacent empty square (or your own half if surrounded). You may also Skip."
                    )
                    RuleItem(
                        title = "Wild Card",
                        body = "Choose any currently enabled special card effect that has a legal play on the board and immediately activate it."
                    )
                    RuleItem(
                        title = "Suicide",
                        body = "You must eliminate 1 of your own non-King pieces from the board. If you are down to ONLY your King when you draw Suicide, you immediately lose the match!"
                    )
                    RuleItem(
                        title = "Check, Custom Decks & Modes",
                        body = "Giving check ends your turn immediately. Starting a turn in check auto-discards special cards until you draw a Number card. When hosting a Wi-Fi LAN match or an online lobby, customize which special cards are in the deck! Tap the Flag button to Forfeit, and tap the Win Streak badge to view match history."
                    )
                }

                Spacer(Modifier.height(14.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.inkPrimary,
                        contentColor = colors.surface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(text = stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun RuleItem(title: String, body: String) {
    val colors = LocalUnoChessColors.current
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(
                color = colors.inkPrimary,
                fontWeight = FontWeight.Bold
            )
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
        )
    }
}

@Composable
fun ResetConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalUnoChessColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.inkPrimary,
        textContentColor = colors.inkSecondary,
        title = {
            Text(
                text = stringResource(R.string.dialog_reset_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_reset_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.inkPrimary,
                    contentColor = colors.surface
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_reset_button")
            ) {
                Text(stringResource(R.string.action_confirm_reset))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_reset_button")
            ) {
                Text(stringResource(R.string.action_cancel), color = colors.inkSecondary)
            }
        }
    )
}

@Composable
fun ForfeitConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalUnoChessColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colors.surface,
        titleContentColor = colors.inkPrimary,
        textContentColor = colors.inkSecondary,
        title = {
            Text(
                text = stringResource(R.string.dialog_forfeit_title),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Text(
                text = stringResource(R.string.dialog_forfeit_message),
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accentCheck,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_forfeit_button")
            ) {
                Text(stringResource(R.string.action_confirm_forfeit))
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("cancel_forfeit_button")
            ) {
                Text(stringResource(R.string.action_cancel), color = colors.inkSecondary)
            }
        }
    )
}

@Composable
fun MatchLogModalDialog(
    matchStats: MatchStats,
    gameLog: List<LogEntry>,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("match_log_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.LocalFireDepartment,
                            contentDescription = null,
                            tint = colors.accentTrade,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            text = stringResource(R.string.match_log_modal_title),
                            style = MaterialTheme.typography.titleMedium.copy(
                                color = colors.inkPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("match_log_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colors.inkSecondary
                        )
                    }
                }

                // Segmented tab selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSubtle)
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(
                        stringResource(R.string.match_log_tab_matches) to "match_log_tab_matches",
                        stringResource(R.string.match_log_tab_moves) to "match_log_tab_moves"
                    ).forEachIndexed { idx, (label, tag) ->
                        val active = selectedTab == idx
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (active) colors.surface else Color.Transparent)
                                .then(
                                    if (active) {
                                        Modifier.border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                                    } else {
                                        Modifier
                                    }
                                )
                                .testTag(tag)
                                .clickable { selectedTab = idx },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge.copy(
                                    color = if (active) colors.inkPrimary else colors.inkSecondary,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                                )
                            )
                        }
                    }
                }

                if (selectedTab == 0) {
                    // Win Streak & Record summary tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        StatMetricTile(
                            label = "Streak",
                            value = "${matchStats.currentStreak}",
                            subtext = matchStats.streakHolder ?: "—",
                            accent = colors.accentTrade,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_current_streak")
                        )
                        StatMetricTile(
                            label = "Best",
                            value = "${matchStats.bestStreak}",
                            subtext = "All-time",
                            accent = colors.accentRevive,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("stat_best_streak")
                        )
                        StatMetricTile(
                            label = "W / L / D",
                            value = "${matchStats.totalWinsP1}-${matchStats.totalWinsP2}-${matchStats.totalDraws}",
                            subtext = "P1 vs P2",
                            accent = colors.inkPrimary,
                            modifier = Modifier
                                .weight(1.2f)
                                .testTag("stat_record")
                        )
                    }

                    HorizontalDivider(color = colors.hairline)

                    if (matchStats.history.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Finish a match to start tracking your win streak and match history.",
                                style = MaterialTheme.typography.bodyMedium.copy(color = colors.inkMuted),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp, max = 260.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(matchStats.history, key = { it.id }) { record ->
                                MatchRecordRow(record = record)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(
                                onClick = onClearHistory,
                                modifier = Modifier.testTag("clear_match_history_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.action_clear_history),
                                    color = colors.accentCheck,
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }
                    }
                } else {
                    // Full current match move log
                    if (gameLog.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.game_log_empty),
                                style = MaterialTheme.typography.bodyMedium.copy(color = colors.inkMuted)
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp, max = 310.dp),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            items(gameLog, key = { it.id }) { entry ->
                                val textColor = when (entry.category) {
                                    LogCategory.CHECK, LogCategory.SUICIDE -> colors.accentCheck
                                    LogCategory.REVIVE -> colors.accentRevive
                                    LogCategory.FREEZE -> colors.accentFreeze
                                    LogCategory.SHIELD -> colors.accentShield
                                    LogCategory.SWAP -> colors.accentSwap
                                    LogCategory.CLONE -> colors.accentClone
                                    LogCategory.WILD -> colors.accentWild
                                    LogCategory.MERGE, LogCategory.SACRIFICE -> colors.accentTrade
                                    LogCategory.HIJACK -> colors.accentHijack
                                    LogCategory.PROMOTE -> colors.accentPromote
                                    LogCategory.GAME_OVER -> colors.inkPrimary
                                    else -> colors.inkSecondary
                                }
                                Text(
                                    text = entry.message,
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = textColor,
                                        fontWeight = if (
                                            entry.category == LogCategory.CHECK ||
                                            entry.category == LogCategory.GAME_OVER ||
                                            entry.category == LogCategory.SUICIDE
                                        ) {
                                            FontWeight.SemiBold
                                        } else {
                                            FontWeight.Normal
                                        }
                                    )
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.inkPrimary,
                        contentColor = colors.surface
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(text = stringResource(R.string.action_close), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun StatMetricTile(
    label: String,
    value: String,
    subtext: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceSubtle)
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(color = colors.inkMuted),
            maxLines = 1
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                color = accent,
                fontWeight = FontWeight.ExtraBold
            ),
            maxLines = 1
        )
        Text(
            text = subtext,
            style = MaterialTheme.typography.labelSmall.copy(
                color = colors.inkSecondary,
                fontSize = 10.sp
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MatchRecordRow(record: MatchRecord) {
    val colors = LocalUnoChessColors.current
    val reasonColor = when (record.reason) {
        MatchEndReason.CHECKMATE -> colors.accentRevive
        MatchEndReason.FORFEIT -> colors.accentTrade
        MatchEndReason.SUICIDE -> colors.accentCheck
        MatchEndReason.STALEMATE -> colors.inkSecondary
    }
    val timeText = SimpleDateFormat("MMM d · HH:mm", Locale.getDefault()).format(Date(record.timestampMs))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surfaceSubtle.copy(alpha = 0.65f))
            .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = if (record.winnerPlayer == null) {
                    "Draw"
                } else {
                    "${record.winnerLabel} won"
                },
                style = MaterialTheme.typography.labelLarge.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${record.modeLabel} · ${record.totalTurns} turns · $timeText",
                style = MaterialTheme.typography.labelSmall.copy(color = colors.inkMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(reasonColor.copy(alpha = 0.14f))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = record.reason.label,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = reasonColor,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
fun GameOverBannerCard(
    outcome: GameOutcome,
    opponentMode: OpponentMode,
    isLanConnected: Boolean,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val headline = when (outcome) {
        is GameOutcome.Checkmate -> stringResource(R.string.status_game_over_checkmate)
        is GameOutcome.Forfeit -> stringResource(R.string.status_game_over_forfeit)
        is GameOutcome.Suicide -> stringResource(R.string.status_game_over_suicide)
        GameOutcome.Stalemate -> stringResource(R.string.status_game_over_stalemate)
    }
    val subtext = when (outcome) {
        is GameOutcome.Checkmate -> {
            val winnerLabel = resolvePlayerLabel(outcome.winnerPlayer, opponentMode, isLanConnected)
            "$winnerLabel (${outcome.winnerColor.displayName}) wins"
        }
        is GameOutcome.Forfeit -> {
            val forfeiterLabel = resolvePlayerLabel(outcome.forfeitingPlayer, opponentMode, isLanConnected)
            val winnerLabel = resolvePlayerLabel(outcome.winnerPlayer, opponentMode, isLanConnected)
            "$forfeiterLabel forfeited · $winnerLabel wins"
        }
        is GameOutcome.Suicide -> {
            val loserLabel = resolvePlayerLabel(outcome.losingPlayer, opponentMode, isLanConnected)
            val winnerLabel = resolvePlayerLabel(outcome.winnerPlayer, opponentMode, isLanConnected)
            "$loserLabel had only a King · $winnerLabel wins"
        }
        GameOutcome.Stalemate ->
            "No legal moves remain"
    }

    val isVictory = outcome is GameOutcome.Checkmate || outcome is GameOutcome.Forfeit
    val entranceScale = remember(outcome) { Animatable(0.86f) }
    LaunchedEffect(outcome) {
        entranceScale.snapTo(0.86f)
        entranceScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }
    val trophyPulse = if (isVictory) {
        val transition = rememberInfiniteTransition(label = "banner_trophy_pulse")
        val value by transition.animateFloat(
            initialValue = 0.94f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "trophy_scale"
        )
        value
    } else {
        1f
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = entranceScale.value
                scaleY = entranceScale.value
            }
            .testTag("game_over_banner"),
        shape = RoundedCornerShape(12.dp),
        color = colors.surface.copy(alpha = 0.96f),
        border = BorderStroke(
            width = 1.5.dp,
            color = if (isVictory) colors.accentTrade else colors.inkPrimary
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isVictory) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .graphicsLayer {
                            scaleX = trophyPulse
                            scaleY = trophyPulse
                        }
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentTrade.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = null,
                        tint = colors.accentTrade,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtext,
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onNewGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.inkPrimary,
                    contentColor = colors.surface
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("play_again_button")
            ) {
                Text(
                    text = stringResource(R.string.action_new_game),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
