package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.GameUiState
import com.example.model.Square
import com.example.model.TurnPhase
import com.example.ui.theme.HighlightCheckRed
import com.example.ui.theme.HighlightLastMove
import com.example.ui.theme.HighlightReviveBorder
import com.example.ui.theme.HighlightReviveSquare
import com.example.ui.theme.HighlightSelected
import com.example.ui.theme.LocalUnoChessColors
import kotlin.math.roundToInt

@Composable
fun ChessBoardView(
    state: GameUiState,
    onSquareTapped: (Square) -> Unit,
    edgeToEdge: Boolean = false,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val legalTargets = remember(state.legalMovesForSelected) {
        state.legalMovesForSelected.map { it.to }.toSet()
    }

    // Smooth piece sliding animation
    val moveProgress = remember { Animatable(1f) }
    var activeMoveAnimId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.moveAnimationEvent?.id) {
        val ev = state.moveAnimationEvent
        if (ev != null) {
            activeMoveAnimId = ev.id
            moveProgress.snapTo(0f)
            moveProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            )
            activeMoveAnimId = null
        } else {
            activeMoveAnimId = null
            moveProgress.snapTo(1f)
        }
    }

    // Subtle piece revival scale-in animation
    val revivePieceScale = remember { Animatable(1f) }
    var activeReviveAnimId by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(state.reviveAnimationEvent?.id) {
        val ev = state.reviveAnimationEvent
        if (ev != null) {
            activeReviveAnimId = ev.id
            revivePieceScale.snapTo(0.35f)
            revivePieceScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(dampingRatio = 0.68f, stiffness = 360f)
            )
            activeReviveAnimId = null
        } else {
            activeReviveAnimId = null
            revivePieceScale.snapTo(1f)
        }
    }

    val currentMoveAnim = state.moveAnimationEvent?.takeIf {
        it.id == activeMoveAnimId && moveProgress.value < 1f
    }
    val currentReviveAnim = state.reviveAnimationEvent?.takeIf {
        it.id == activeReviveAnimId && revivePieceScale.value < 0.99f
    }

    val rowIndices = if (state.boardFlipped) (7 downTo 0).toList() else (0..7).toList()
    val colIndices = if (state.boardFlipped) (7 downTo 0).toList() else (0..7).toList()

    fun visualRow(boardRow: Int): Int = if (state.boardFlipped) 7 - boardRow else boardRow
    fun visualCol(boardCol: Int): Int = if (state.boardFlipped) 7 - boardCol else boardCol

    val boardShape = if (edgeToEdge) RoundedCornerShape(0.dp) else RoundedCornerShape(8.dp)
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1f)
            .clip(boardShape)
            .then(
                if (edgeToEdge) {
                    Modifier.border(0.5.dp, colors.boardBorder.copy(alpha = 0.65f), boardShape)
                } else {
                    Modifier.border(1.dp, colors.boardBorder, boardShape)
                }
            )
    ) {
        val boardWidthPx = constraints.maxWidth.toFloat()
        val boardHeightPx = constraints.maxHeight.toFloat()
        val cellWidthPx = boardWidthPx / 8f
        val cellHeightPx = boardHeightPx / 8f
        val cellWidthDp = maxWidth / 8f
        val cellHeightDp = maxHeight / 8f

        Column(modifier = Modifier.fillMaxSize()) {
            rowIndices.forEachIndexed { vRow, boardRow ->
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    colIndices.forEachIndexed { vCol, boardCol ->
                        val square = Square(boardRow, boardCol)
                        val piece = state.board[boardRow][boardCol]
                        val isLight = (boardRow + boardCol) % 2 == 0
                        val isSelected = state.selectedSquare == square
                        val isLastMove = state.lastMoveFrom == square || state.lastMoveTo == square
                        val isCheckedKing = state.checkedKingSquare == square
                        val isLegalTarget = square in legalTargets
                        val isReviveTarget = state.phase == TurnPhase.REVIVING && square in state.validReviveSquares
                        val isFreezeTarget = state.phase == TurnPhase.FREEZING && square in state.validFreezeSquares
                        val isMergeSelected = state.phase == TurnPhase.MERGING && square in state.selectedMergeSquares
                        val isMergeAnchor = state.phase == TurnPhase.MERGING && state.selectedMergeSquares.firstOrNull() == square
                        val isSacrificeTarget = state.phase == TurnPhase.SACRIFICING && square in state.validSacrificeSquares
                        val isSacrificeSelected = state.phase == TurnPhase.SACRIFICING && state.selectedSacrificeSquare == square
                        val isHijackSelected = state.phase == TurnPhase.HIJACKING && state.selectedSquare == square
                        val isPromoteTarget = state.phase == TurnPhase.PROMOTING_PAWN && square in state.validPromotePawnSquares
                        val isPromoteSelected = state.phase == TurnPhase.PROMOTING_PAWN && state.selectedPromotePawnSquare == square
                        val isSuicideTarget = state.phase == TurnPhase.SUICIDING && square in state.validSuicideSquares
                        val isShieldTarget = state.phase == TurnPhase.SHIELDING && square in state.validShieldSquares
                        val isShieldedPiece = piece != null && state.isPieceShielded(piece)
                        val isSwapTarget = state.phase == TurnPhase.SWAPPING && square in state.validSwapSquares
                        val isSwapSelected = state.phase == TurnPhase.SWAPPING && state.selectedSwapSquare == square
                        val isCloneTarget = state.phase == TurnPhase.CLONING && square in state.validCloneSquares
                        val isCloneSelected = state.phase == TurnPhase.CLONING && state.selectedCloneSourceSquare == square
                        val isFrozenPiece = piece != null && state.isPieceFrozen(piece)
                        val isAlreadyMovedThisTurn = piece != null &&
                            state.phase == TurnPhase.MOVING &&
                            piece.color == state.activeColor &&
                            piece.id in state.movedPieceIdsThisTurn

                        val isHiddenForMoveAnim = currentMoveAnim != null &&
                            (currentMoveAnim.to == square || currentMoveAnim.secondaryTo == square)

                        val baseColor = if (isLight) colors.boardLight else colors.boardDark
                        val coordColor = if (isLight) colors.boardCoordLight else colors.boardCoordDark

                        val squareDesc = if (piece != null) {
                            val suffix = when {
                                isShieldedPiece && isFrozenPiece -> " (shielded, frozen)"
                                isShieldedPiece -> " (shielded)"
                                isFrozenPiece -> " (frozen)"
                                isAlreadyMovedThisTurn -> " (already moved)"
                                else -> ""
                            }
                            "${square.toAlgebraic()} ${piece.color.displayName} ${piece.type.displayName}$suffix"
                        } else {
                            stringResource(R.string.cd_board_square, square.toAlgebraic())
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(baseColor)
                                .testTag("board_square_${square.toAlgebraic()}")
                                .semantics { contentDescription = squareDesc }
                                .clickable { onSquareTapped(square) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLastMove) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(HighlightLastMove)
                                )
                            }

                            if (isSelected && !isHijackSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(HighlightSelected)
                                )
                            }

                            if (isHijackSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.highlightHijackSelected)
                                        .border(2.dp, colors.highlightHijackBorder)
                                )
                            }

                            if (isReviveTarget) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(HighlightReviveSquare)
                                        .border(1.5.dp, HighlightReviveBorder)
                                )
                            }

                            if (isFreezeTarget) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.highlightFreezeTarget)
                                        .border(1.5.dp, colors.highlightFreezeBorder)
                                )
                            }

                            if (isFrozenPiece) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.highlightFrozenPiece)
                                        .border(1.5.dp, colors.highlightFreezeBorder)
                                )
                            }

                            if (isMergeSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.highlightTradeSelected)
                                        .border(
                                            width = if (isMergeAnchor) 2.dp else 1.5.dp,
                                            color = colors.highlightTradeBorder
                                        )
                                )
                            }

                            if (isSacrificeTarget || isSacrificeSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isSacrificeSelected) {
                                                colors.highlightTradeSelected
                                            } else {
                                                colors.highlightTradeSelected.copy(alpha = 0.24f)
                                            }
                                        )
                                        .border(
                                            width = if (isSacrificeSelected) 2.dp else 1.2.dp,
                                            color = colors.highlightTradeBorder
                                        )
                                )
                            }

                            if (isPromoteTarget || isPromoteSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isPromoteSelected) {
                                                colors.highlightPromoteSelected
                                            } else {
                                                colors.highlightPromoteTarget
                                            }
                                        )
                                        .border(
                                            width = if (isPromoteSelected) 2.dp else 1.2.dp,
                                            color = colors.highlightPromoteBorder
                                        )
                                )
                            }

                            if (isSuicideTarget) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.accentCheck.copy(alpha = 0.28f))
                                        .border(1.5.dp, colors.accentCheck)
                                )
                            }

                            if (isShieldTarget) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.highlightShieldTarget)
                                        .border(1.5.dp, colors.highlightShieldBorder)
                                )
                            }

                            if (isShieldedPiece) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(colors.highlightShieldedPiece)
                                        .border(1.5.dp, colors.highlightShieldBorder)
                                )
                            }

                            if (isSwapTarget || isSwapSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isSwapSelected) colors.highlightSwapSelected else colors.highlightSwapTarget
                                        )
                                        .border(
                                            width = if (isSwapSelected) 2.dp else 1.5.dp,
                                            color = colors.highlightSwapBorder
                                        )
                                )
                            }

                            if (isCloneTarget || isCloneSelected) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isCloneSelected) colors.highlightCloneSelected else colors.highlightCloneTarget
                                        )
                                        .border(
                                            width = if (isCloneSelected) 2.dp else 1.5.dp,
                                            color = colors.highlightCloneBorder
                                        )
                                )
                            }

                            if (isCheckedKing) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(HighlightCheckRed)
                                )
                            }

                            // Chess piece graphic
                            if (piece != null && !isHiddenForMoveAnim) {
                                val pieceScale = if (currentReviveAnim?.square == square) {
                                    revivePieceScale.value
                                } else {
                                    1f
                                }
                                val pieceAlpha = if (isAlreadyMovedThisTurn) 0.52f else 1f
                                ChessPieceGraphic(
                                    type = piece.type,
                                    color = piece.color,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .alpha(pieceAlpha)
                                        .graphicsLayer {
                                            scaleX = pieceScale
                                            scaleY = pieceScale
                                        }
                                        .padding(2.dp)
                                )

                                // Compact frost badge on currently frozen piece
                                if (isFrozenPiece) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentFreeze)
                                            .testTag("frozen_badge_${square.toAlgebraic()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.AcUnit,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(10.dp)
                                        )
                                    }
                                }

                                // Anchor/selection badge during Merge mode
                                if (isMergeSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentTrade)
                                            .testTag("merge_badge_${square.toAlgebraic()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = if (isMergeAnchor) "★" else "+",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                // Selection badge during Sacrifice mode
                                if (isSacrificeSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentTrade)
                                            .testTag("sacrifice_badge_${square.toAlgebraic()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "❖",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                // Selection badge during Pawn Promote mode
                                if (isPromoteSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentPromote)
                                            .testTag("promote_badge_${square.toAlgebraic()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "↑",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                // Elimination badge during Suicide mode
                                if (isSuicideTarget) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentCheck)
                                            .testTag("suicide_badge_${square.toAlgebraic()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "✕",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontSize = 8.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                // Gold shield badge on currently shielded piece
                                if (isShieldedPiece) {
                                    Box(
                                        modifier = Modifier
                                            .align(if (isFrozenPiece) Alignment.BottomStart else Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentShield)
                                            .testTag("shielded_badge_${square.toAlgebraic()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Shield,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(9.dp)
                                        )
                                    }
                                }

                                // Selection badge during Swap mode
                                if (isSwapSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentSwap)
                                            .testTag("swap_badge_${square.toAlgebraic()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "⇄",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }

                                // Selection badge during Clone mode
                                if (isCloneSelected) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(2.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(colors.accentClone)
                                            .testTag("clone_badge_${square.toAlgebraic()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "⧉",
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                color = Color.White,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }

                            // Clean legal move dot / capture ring
                            if (isLegalTarget) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    if (piece == null) {
                                        drawCircle(
                                            color = colors.legalMoveDot,
                                            radius = size.minDimension * 0.15f
                                        )
                                    } else {
                                        drawCircle(
                                            color = colors.legalCaptureRing,
                                            radius = size.minDimension * 0.43f,
                                            style = Stroke(width = size.minDimension * 0.085f)
                                        )
                                    }
                                }
                            }

                            if (vCol == 0) {
                                Text(
                                    text = "${8 - boardRow}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = coordColor
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(start = 2.dp, top = 1.dp)
                                )
                            }

                            if (vRow == 7) {
                                Text(
                                    text = "${'a' + boardCol}",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = coordColor
                                    ),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(end = 2.dp, bottom = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Smooth gliding piece overlay (and castling rook slide)
        if (currentMoveAnim != null) {
            val t = moveProgress.value
            val startX = visualCol(currentMoveAnim.from.col) * cellWidthPx
            val startY = visualRow(currentMoveAnim.from.row) * cellHeightPx
            val endX = visualCol(currentMoveAnim.to.col) * cellWidthPx
            val endY = visualRow(currentMoveAnim.to.row) * cellHeightPx

            Box(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (startX + (endX - startX) * t).roundToInt(),
                            (startY + (endY - startY) * t).roundToInt()
                        )
                    }
                    .size(width = cellWidthDp, height = cellHeightDp)
                    .padding(2.dp)
            ) {
                ChessPieceGraphic(
                    type = currentMoveAnim.piece.type,
                    color = currentMoveAnim.piece.color
                )
            }

            val rook = currentMoveAnim.secondaryPiece
            val rookFrom = currentMoveAnim.secondaryFrom
            val rookTo = currentMoveAnim.secondaryTo
            if (rook != null && rookFrom != null && rookTo != null) {
                val rStartX = visualCol(rookFrom.col) * cellWidthPx
                val rStartY = visualRow(rookFrom.row) * cellHeightPx
                val rEndX = visualCol(rookTo.col) * cellWidthPx
                val rEndY = visualRow(rookTo.row) * cellHeightPx
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (rStartX + (rEndX - rStartX) * t).roundToInt(),
                                (rStartY + (rEndY - rStartY) * t).roundToInt()
                            )
                        }
                        .size(width = cellWidthDp, height = cellHeightDp)
                        .padding(2.dp)
                ) {
                    ChessPieceGraphic(type = rook.type, color = rook.color)
                }
            }
        }
    }
}
