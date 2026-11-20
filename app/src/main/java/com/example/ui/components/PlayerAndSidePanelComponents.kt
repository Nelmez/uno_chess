package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.engine.ChessEngine
import com.example.model.ChessPiece
import com.example.model.GameOutcome
import com.example.model.GameUiState
import com.example.model.LogCategory
import com.example.model.LogEntry
import com.example.model.OpponentMode
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.PlayerId
import com.example.model.SpecialCardKey
import com.example.model.TurnPhase
import com.example.ui.theme.BlackPieceFill
import com.example.ui.theme.LocalUnoChessColors
import com.example.ui.theme.WhitePieceFill

fun resolvePlayerLabel(
    player: PlayerId,
    opponentMode: OpponentMode,
    isLanConnected: Boolean
): String {
    return if (!isLanConnected && player == PlayerId.PLAYER_2 && opponentMode is OpponentMode.VsAi) {
        "AI (${opponentMode.difficulty.displayName})"
    } else {
        player.displayName
    }
}

@Composable
fun PlayerStripCard(
    player: PlayerId,
    displayName: String,
    color: PieceColor,
    isActiveTurn: Boolean,
    isInCheck: Boolean,
    capturedOwnPieces: List<ChessPiece>,
    isReviveSelectable: Boolean,
    selectedRevivePieceId: Int?,
    onSelectRevivePiece: (Int) -> Unit,
    showInlineCapturedTray: Boolean = true,
    isLocalUser: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val expandReviveBar = showInlineCapturedTray && isReviveSelectable && capturedOwnPieces.isNotEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("player_strip_${player.name.lowercase()}"),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ColorControlBadge(color = color)

            Text(
                text = if (isLocalUser) "$displayName (You)" else displayName,
                style = MaterialTheme.typography.titleSmall.copy(
                    color = if (isActiveTurn) colors.inkPrimary else colors.inkSecondary,
                    fontWeight = if (isActiveTurn) FontWeight.Bold else FontWeight.Medium
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = color.displayName,
                style = MaterialTheme.typography.bodySmall.copy(
                    color = colors.inkMuted
                ),
                maxLines = 1,
                softWrap = false
            )

            if (isInCheck) {
                Text(
                    text = "· Check",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.accentCheck,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }

            // Inline compact captured tray on the same row so portrait fits on 1 screen
            if (showInlineCapturedTray && !expandReviveBar && capturedOwnPieces.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(1.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    capturedOwnPieces.forEach { piece ->
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .testTag("captured_piece_${piece.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            ChessPieceGraphic(type = piece.type, color = piece.color)
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            if (isActiveTurn) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isInCheck) colors.accentCheck else colors.inkPrimary)
                )
            }
        }

        if (expandReviveBar) {
            CapturedPiecesStrip(
                ownerColor = color,
                pieces = capturedOwnPieces,
                isReviveSelectable = true,
                selectedPieceId = selectedRevivePieceId,
                onSelectPiece = onSelectRevivePiece
            )
        }
    }
}

@Composable
fun ColorControlBadge(
    color: PieceColor,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val isWhite = color == PieceColor.WHITE
    Box(
        modifier = modifier
            .size(14.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(if (isWhite) WhitePieceFill else BlackPieceFill)
            .border(
                width = 1.dp,
                color = if (isWhite) colors.hairline else colors.inkMuted.copy(alpha = 0.5f),
                shape = RoundedCornerShape(3.dp)
            )
            .testTag("color_badge_${color.name.lowercase()}")
    )
}

@Composable
fun CapturedPiecesStrip(
    ownerColor: PieceColor,
    pieces: List<ChessPiece>,
    isReviveSelectable: Boolean,
    selectedPieceId: Int?,
    onSelectPiece: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (isReviveSelectable) {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentRevive.copy(alpha = 0.10f))
                        .border(1.dp, colors.accentRevive.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                } else {
                    Modifier.padding(vertical = 1.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (isReviveSelectable) {
            Text(
                text = "Revive",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = colors.accentRevive,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                softWrap = false
            )
            Spacer(Modifier.width(2.dp))
        }

        if (pieces.isEmpty()) {
            Text(
                text = stringResource(R.string.captured_tray_empty),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkMuted)
            )
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                pieces.forEach { piece ->
                    val isSelected = isReviveSelectable && piece.id == selectedPieceId
                    val chipDesc = "${ownerColor.displayName} ${piece.type.displayName}"
                    Box(
                        modifier = Modifier
                            .size(if (isReviveSelectable) 32.dp else 22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                when {
                                    isSelected -> colors.accentRevive.copy(alpha = 0.24f)
                                    isReviveSelectable -> colors.surface
                                    else -> Color.Transparent
                                }
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else if (isReviveSelectable) 1.dp else 0.dp,
                                color = when {
                                    isSelected -> colors.accentRevive
                                    isReviveSelectable -> colors.hairline
                                    else -> Color.Transparent
                                },
                                shape = RoundedCornerShape(6.dp)
                            )
                            .testTag("captured_piece_${piece.id}")
                            .semantics { contentDescription = chipDesc }
                            .clickable(enabled = isReviveSelectable) {
                                onSelectPiece(piece.id)
                            }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ChessPieceGraphic(type = piece.type, color = piece.color)
                    }
                }
            }
        }
    }
}

@Composable
fun SidePanelCapturedTraysCard(
    state: GameUiState,
    opponentMode: OpponentMode,
    isLanConnected: Boolean,
    isLocalTurn: Boolean,
    onSelectRevivePiece: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Captured",
            style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
        )

        listOf(PieceColor.WHITE, PieceColor.BLACK).forEach { color ->
            val ownerPlayer = state.playerControlling(color)
            val ownerName = resolvePlayerLabel(ownerPlayer, opponentMode, isLanConnected)
            val pieces = state.capturedPieces[color].orEmpty()
            val canSelect = isLocalTurn &&
                state.phase == TurnPhase.REVIVING &&
                state.activeColor == color &&
                pieces.isNotEmpty()
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "$ownerName · ${color.displayName}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = if (canSelect) colors.accentRevive else colors.inkMuted,
                        fontWeight = if (canSelect) FontWeight.Bold else FontWeight.Normal
                    )
                )
                CapturedPiecesStrip(
                    ownerColor = color,
                    pieces = pieces,
                    isReviveSelectable = canSelect,
                    selectedPieceId = state.selectedRevivePieceId,
                    onSelectPiece = onSelectRevivePiece
                )
            }
        }
    }
}

@Composable
fun TurnStatusSummary(
    state: GameUiState,
    opponentMode: OpponentMode,
    isLanConnected: Boolean,
    isAiThinking: Boolean,
    waitingForRemotePlayer: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val isCheck = state.checkedKingSquare != null && state.outcome == null
    val activeLabel = resolvePlayerLabel(state.activePlayer, opponentMode, isLanConnected)
    val hasCapturedForRevive = state.capturedPieces[state.activeColor].orEmpty().isNotEmpty()

    Column(
        modifier = modifier.testTag("turn_status_card"),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        when {
            state.outcome != null -> {
                val headline = when (state.outcome) {
                    is GameOutcome.Checkmate -> "Checkmate"
                    is GameOutcome.Forfeit -> "Forfeit"
                    is GameOutcome.Suicide -> "King Eliminated"
                    GameOutcome.Stalemate -> "Stalemate"
                }
                Text(
                    text = headline,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            isAiThinking -> {
                Text(
                    text = "AI thinking…",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.inkSecondary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("ai_thinking_badge")
                )
            }

            state.phase == TurnPhase.FREEZING -> {
                Text(
                    text = "Freeze 1 piece",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentFreeze,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("freeze_phase_badge")
                )
            }

            state.phase == TurnPhase.MERGING -> {
                val pts = state.selectedMergeSquares.sumOf { sq ->
                    state.board[sq.row][sq.col]?.type?.materialValue ?: 0
                }
                val title = if (pts > 0) "Merge · $pts pts" else "Merge Pieces"
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentTrade,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("merge_phase_badge")
                )
            }

            state.phase == TurnPhase.SACRIFICING -> {
                val selectedPiece = state.selectedSacrificeSquare?.let { state.board[it.row][it.col] }
                val title = if (selectedPiece != null) {
                    "Sacrifice ${selectedPiece.type.displayName}"
                } else {
                    "Sacrifice Piece"
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentTrade,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("sacrifice_phase_badge")
                )
            }

            state.phase == TurnPhase.HIJACKING -> {
                Text(
                    text = "Hijack 1 enemy",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentHijack,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("hijack_phase_badge")
                )
            }

            state.phase == TurnPhase.PROMOTING_PAWN -> {
                Text(
                    text = "Promote Pawn",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentPromote,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("promote_phase_badge")
                )
            }

            state.phase == TurnPhase.SUICIDING -> {
                Text(
                    text = "Suicide Card",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentCheck,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("suicide_phase_badge")
                )
            }

            state.phase == TurnPhase.SHIELDING -> {
                Text(
                    text = "Shield 1 piece",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentShield,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("shield_phase_badge")
                )
            }

            state.phase == TurnPhase.SWAPPING -> {
                Text(
                    text = "Swap Positions",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentSwap,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("swap_phase_badge")
                )
            }

            state.phase == TurnPhase.CLONING -> {
                Text(
                    text = "Clone Piece",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentClone,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("clone_phase_badge")
                )
            }

            state.phase == TurnPhase.WILD_CHOOSING -> {
                Text(
                    text = "Wild Card",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentWild,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("wild_phase_badge")
                )
            }

            state.phase == TurnPhase.MOVING -> {
                Text(
                    text = "${state.movesRemaining} / ${state.totalMovesForCard} moves",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (isCheck) colors.accentCheck else colors.inkPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("moves_remaining_badge")
                )
            }

            state.phase == TurnPhase.REVIVING -> {
                Text(
                    text = if (hasCapturedForRevive) "Revive (1 Pawn)" else "Place 1 Pawn",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = colors.accentRevive,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.testTag("revives_remaining_badge")
                )
            }

            else -> {
                Text(
                    text = if (waitingForRemotePlayer) "Opponent's turn" else "Draw a card",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = if (waitingForRemotePlayer) colors.inkSecondary else colors.inkPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        val subline = when (val outcome = state.outcome) {
            is GameOutcome.Checkmate -> {
                val winnerName = resolvePlayerLabel(outcome.winnerPlayer, opponentMode, isLanConnected)
                "$winnerName (${outcome.winnerColor.displayName}) wins"
            }
            is GameOutcome.Forfeit -> {
                val winnerName = resolvePlayerLabel(outcome.winnerPlayer, opponentMode, isLanConnected)
                "$winnerName (${outcome.winnerColor.displayName}) wins"
            }
            is GameOutcome.Suicide -> {
                val winnerName = resolvePlayerLabel(outcome.winnerPlayer, opponentMode, isLanConnected)
                "$winnerName (${outcome.winnerColor.displayName}) wins"
            }
            GameOutcome.Stalemate -> "Draw — no legal moves"
            null -> "$activeLabel · ${state.activeColor.displayName}"
        }
        Text(
            text = subline,
            style = MaterialTheme.typography.bodySmall.copy(
                color = colors.inkSecondary,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        val hint = when {
            state.outcome != null -> null
            isAiThinking -> "Playing card & choosing moves"
            waitingForRemotePlayer -> "Waiting for $activeLabel over Wi-Fi…"
            state.phase == TurnPhase.AWAITING_DRAW -> "Tap deck to start turn"
            state.phase == TurnPhase.FREEZING -> "Tap a blue enemy piece, then move 1"
            state.phase == TurnPhase.MERGING -> if (state.selectedMergeSquares.isEmpty()) {
                "Tap 1+ own pieces to swap or merge"
            } else {
                "Pick target below or tap more"
            }
            state.phase == TurnPhase.SACRIFICING -> if (state.selectedSacrificeSquare == null) {
                "Tap a highlighted piece to split"
            } else {
                "Choose a split below or Skip"
            }
            state.phase == TurnPhase.HIJACKING -> "Select & move 1 opponent piece"
            state.phase == TurnPhase.PROMOTING_PAWN -> "Tap a teal pawn & pick Knight/Bishop"
            state.phase == TurnPhase.SUICIDING -> "Tap 1 of your red pieces to remove"
            state.phase == TurnPhase.SHIELDING -> "Tap a gold own piece to protect, then move 1"
            state.phase == TurnPhase.SWAPPING -> if (state.selectedSwapSquare == null) {
                "Tap 1st own piece to swap positions"
            } else {
                "Tap a highlighted partner to swap"
            }
            state.phase == TurnPhase.CLONING -> if (state.selectedCloneSourceSquare == null) {
                "Tap a Pawn, Knight, or Bishop to clone"
            } else {
                "Tap a highlighted empty square to spawn copy"
            }
            state.phase == TurnPhase.WILD_CHOOSING -> "Pick any special card effect below"
            state.phase == TurnPhase.MOVING && isCheck -> "Resolve check on first move"
            state.phase == TurnPhase.REVIVING -> if (hasCapturedForRevive) {
                "Pick captured piece & tap a green pawn"
            } else {
                "Tap a green square to place a Pawn"
            }
            else -> state.activeCard?.type?.subtitle()
        }
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall.copy(
                    color = when {
                        isCheck || state.phase == TurnPhase.SUICIDING -> colors.accentCheck
                        state.phase == TurnPhase.FREEZING -> colors.accentFreeze
                        state.phase == TurnPhase.SHIELDING -> colors.accentShield
                        state.phase == TurnPhase.SWAPPING -> colors.accentSwap
                        state.phase == TurnPhase.CLONING -> colors.accentClone
                        state.phase == TurnPhase.WILD_CHOOSING -> colors.accentWild
                        state.phase == TurnPhase.MERGING || state.phase == TurnPhase.SACRIFICING -> colors.accentTrade
                        state.phase == TurnPhase.HIJACKING -> colors.accentHijack
                        state.phase == TurnPhase.PROMOTING_PAWN -> colors.accentPromote
                        state.phase == TurnPhase.REVIVING -> colors.accentRevive
                        else -> colors.inkMuted
                    }
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun MergeActionCard(
    state: GameUiState,
    onConfirmMerge: (PieceType) -> Unit,
    onClearSelection: () -> Unit,
    onSkipMerge: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val selected = state.selectedMergeSquares
    val totalPoints = selected.sumOf { sq ->
        state.board[sq.row][sq.col]?.type?.materialValue ?: 0
    }
    val validTargets = ChessEngine.getValidTradeTargets(
        board = state.board,
        color = state.activeColor,
        selectedSquares = selected
    ).toSet()
    val anchor = selected.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentTrade.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("merge_action_card"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (selected.isEmpty()) {
                    "Merge · P=1 N/B=3 R=5 Q=9"
                } else {
                    "${selected.size} sel · ${totalPoints}pt → ★${anchor?.toAlgebraic()?.uppercase() ?: ""}"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selected.isNotEmpty()) {
                    TextButton(
                        onClick = onClearSelection,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier
                            .height(32.dp)
                            .testTag("merge_clear_button")
                    ) {
                        Text("Clear", color = colors.inkSecondary, style = MaterialTheme.typography.labelSmall)
                    }
                }
                TextButton(
                    onClick = onSkipMerge,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("merge_skip_button")
                ) {
                    Text("Skip", color = colors.inkPrimary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }

        val allTargets = listOf(
            PieceType.QUEEN,
            PieceType.ROOK,
            PieceType.BISHOP,
            PieceType.KNIGHT,
            PieceType.PAWN
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            allTargets.forEach { targetType ->
                val enabled = targetType in validTargets
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                        .alpha(if (enabled) 1f else 0.34f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(
                            if (enabled) colors.accentTrade.copy(alpha = 0.14f) else colors.surfaceSubtle
                        )
                        .border(
                            width = 1.dp,
                            color = if (enabled) colors.accentTrade else colors.hairline,
                            shape = RoundedCornerShape(7.dp)
                        )
                        .testTag("merge_target_${targetType.name.lowercase()}")
                        .clickable(enabled = enabled) { onConfirmMerge(targetType) }
                        .padding(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(22.dp)) {
                        ChessPieceGraphic(type = targetType, color = state.activeColor)
                    }
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = "${targetType.materialValue}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (enabled) colors.inkPrimary else colors.inkMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun SacrificeActionCard(
    state: GameUiState,
    onConfirmSacrifice: (String) -> Unit,
    onSkipSacrifice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val selectedSquare = state.selectedSacrificeSquare
    val selectedPiece = selectedSquare?.let { state.board[it.row][it.col] }
    val recipes = if (selectedSquare != null) {
        ChessEngine.getValidSacrificeRecipes(
            board = state.board,
            color = state.activeColor,
            sacrificeSquare = selectedSquare
        )
    } else {
        emptyList()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentTrade.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("sacrifice_action_card"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (selectedPiece != null && selectedSquare != null) {
                    "Split ${selectedPiece.type.displayName} (${selectedPiece.type.materialValue}pt) on ${selectedSquare.toAlgebraic().uppercase()}"
                } else {
                    "Tap a highlighted piece (N/B/R/Q) to split"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onSkipSacrifice,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("sacrifice_skip_button")
            ) {
                Text("Skip", color = colors.inkPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }

        if (recipes.isEmpty()) {
            Text(
                text = if (selectedSquare == null) {
                    "Splits piece into equal-value pieces nearby"
                } else {
                    "No safe split available for this square"
                },
                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                recipes.forEach { recipe ->
                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.accentTrade.copy(alpha = 0.14f))
                            .border(1.dp, colors.accentTrade, RoundedCornerShape(8.dp))
                            .testTag("sacrifice_recipe_${recipe.id}")
                            .clickable { onConfirmSacrifice(recipe.id) }
                            .padding(horizontal = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = recipe.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = colors.inkPrimary,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PawnPromoteActionCard(
    state: GameUiState,
    onConfirmPawnPromote: (PieceType) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val selectedSquare = state.selectedPromotePawnSquare
    val validTargets = if (selectedSquare != null) {
        ChessEngine.getValidPawnPromoteTargets(
            board = state.board,
            color = state.activeColor,
            pawnSquare = selectedSquare
        ).toSet()
    } else {
        emptySet()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentPromote.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("pawn_promote_action_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (selectedSquare != null) {
                    "Pawn on ${selectedSquare.toAlgebraic().uppercase()}"
                } else {
                    "Tap a teal pawn"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Upgrade to Knight or Bishop",
                style = MaterialTheme.typography.labelSmall.copy(color = colors.inkMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        listOf(PieceType.BISHOP, PieceType.KNIGHT).forEach { target ->
            val enabled = target in validTargets
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .alpha(if (enabled) 1f else 0.36f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (enabled) colors.accentPromote.copy(alpha = 0.16f) else colors.surfaceSubtle
                    )
                    .border(
                        width = 1.dp,
                        color = if (enabled) colors.accentPromote else colors.hairline,
                        shape = RoundedCornerShape(8.dp)
                    )
                    .testTag("pawn_promote_${target.name.lowercase()}")
                    .clickable(enabled = enabled) { onConfirmPawnPromote(target) }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(modifier = Modifier.size(22.dp)) {
                    ChessPieceGraphic(type = target, color = state.activeColor)
                }
                Text(
                    text = target.displayName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}

@Composable
fun SwapActionCard(
    state: GameUiState,
    onClearSelection: () -> Unit,
    onSkipSwap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val firstSquare = state.selectedSwapSquare
    val firstPiece = firstSquare?.let { state.board[it.row][it.col] }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentSwap.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("swap_action_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (firstPiece != null && firstSquare != null) {
                    "Selected ${firstPiece.type.displayName} (${firstSquare.toAlgebraic().uppercase()})"
                } else {
                    "Swap Positions"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (firstSquare != null) {
                    "Tap a highlighted partner square to swap"
                } else {
                    "Tap 2 of your highlighted pieces to swap"
                },
                style = MaterialTheme.typography.labelSmall.copy(color = colors.inkMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (firstSquare != null) {
                TextButton(
                    onClick = onClearSelection,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("swap_clear_button")
                ) {
                    Text("Clear", color = colors.inkSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(
                onClick = onSkipSwap,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("swap_skip_button")
            ) {
                Text("Skip", color = colors.inkPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun CloneActionCard(
    state: GameUiState,
    onClearSelection: () -> Unit,
    onSkipClone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val sourceSquare = state.selectedCloneSourceSquare
    val sourcePiece = sourceSquare?.let { state.board[it.row][it.col] }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentClone.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("clone_action_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (sourcePiece != null && sourceSquare != null) {
                    "Cloning ${sourcePiece.type.displayName} (${sourceSquare.toAlgebraic().uppercase()})"
                } else {
                    "Clone (Pawn / Knight / Bishop)"
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (sourceSquare != null) {
                    "Tap a highlighted empty square to spawn the copy"
                } else {
                    "Tap an eligible piece to duplicate it"
                },
                style = MaterialTheme.typography.labelSmall.copy(color = colors.inkMuted),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (sourceSquare != null) {
                TextButton(
                    onClick = onClearSelection,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .testTag("clone_clear_button")
                ) {
                    Text("Clear", color = colors.inkSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
            TextButton(
                onClick = onSkipClone,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(36.dp)
                    .testTag("clone_skip_button")
            ) {
                Text("Skip", color = colors.inkPrimary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun WildCardActionCard(
    state: GameUiState,
    onSelectWildEffect: (SpecialCardKey) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.accentWild.copy(alpha = 0.65f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("wild_action_card"),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Text(
            text = "✦ Wild Card — Pick a Special Effect",
            style = MaterialTheme.typography.labelMedium.copy(
                color = colors.accentWild,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            state.availableWildEffects.forEach { effect ->
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.accentWild.copy(alpha = 0.14f))
                        .border(1.dp, colors.accentWild, RoundedCornerShape(8.dp))
                        .testTag("wild_effect_${effect.name.lowercase()}")
                        .clickable { onSelectWildEffect(effect) }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${effect.shortSymbol} ${effect.displayName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.inkPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
fun GameLogCard(
    entries: List<LogEntry>,
    maxHeight: Dp? = null,
    showHeader: Boolean = true,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surface)
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 7.dp)
            .testTag("game_log_card"),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (showHeader) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(R.string.match_log_tab_moves),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    softWrap = false
                )
                if (onClick != null) {
                    Text(
                        text = "History & Streak →",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = colors.inkMuted,
                            fontSize = 10.sp
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.hairline)
            )
        }

        val listModifier = if (maxHeight != null) {
            Modifier
                .fillMaxWidth()
                .height(maxHeight)
        } else {
            Modifier
                .fillMaxWidth()
                .weight(1f)
        }

        if (entries.isEmpty()) {
            Box(
                modifier = listModifier,
                contentAlignment = Alignment.TopStart
            ) {
                Text(
                    text = stringResource(R.string.game_log_empty),
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = colors.inkMuted,
                        fontSize = 11.5.sp
                    )
                )
            }
        } else {
            LazyColumn(
                modifier = listModifier,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(entries, key = { it.id }) { entry ->
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
                            fontSize = 11.5.sp,
                            lineHeight = 15.sp,
                            fontWeight = if (
                                entry.category == LogCategory.CHECK ||
                                entry.category == LogCategory.GAME_OVER ||
                                entry.category == LogCategory.FREEZE ||
                                entry.category == LogCategory.SHIELD ||
                                entry.category == LogCategory.WILD ||
                                entry.category == LogCategory.SUICIDE
                            ) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            }
                        ),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
