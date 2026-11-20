package com.example.audio

import com.example.model.GameOutcome
import com.example.model.GameUiState
import com.example.model.PieceType
import com.example.model.TurnPhase
import com.example.model.UnoCardType

/**
 * Distinct tactile sound effects for UNO Chess gameplay actions.
 */
enum class SoundEffect(
    val fileName: String,
    val volume: Float,
    val pitchJitter: Float = 0f
) {
    PIECE_MOVE("sfx_piece_move.wav", volume = 0.85f, pitchJitter = 0.025f),
    PIECE_CAPTURE("sfx_piece_capture.wav", volume = 0.95f, pitchJitter = 0.02f),
    CASTLE("sfx_castle.wav", volume = 0.88f, pitchJitter = 0.015f),
    CHECK("sfx_check.wav", volume = 0.90f),
    CHECKMATE("sfx_checkmate.wav", volume = 1.0f),
    FORFEIT("sfx_forfeit.wav", volume = 0.90f),
    GAME_END_DRAW_OR_LOSS("sfx_game_end_draw.wav", volume = 0.85f),
    CARD_DRAW("sfx_card_draw.wav", volume = 0.80f, pitchJitter = 0.03f),
    SPECIAL_FREEZE("sfx_special_freeze.wav", volume = 0.90f),
    SPECIAL_SHIELD("sfx_special_shield.wav", volume = 0.92f),
    SPECIAL_REVIVE("sfx_special_revive.wav", volume = 0.90f),
    SPECIAL_HIJACK("sfx_special_hijack.wav", volume = 0.90f),
    SPECIAL_MERGE("sfx_special_merge.wav", volume = 0.92f),
    SPECIAL_SACRIFICE("sfx_special_sacrifice.wav", volume = 0.92f),
    SPECIAL_PROMOTE("sfx_special_promote.wav", volume = 0.92f),
    SPECIAL_SWAP("sfx_special_swap.wav", volume = 0.88f),
    SPECIAL_CLONE("sfx_special_clone.wav", volume = 0.88f),
    SPECIAL_WILD("sfx_special_wild.wav", volume = 0.95f),
    SPECIAL_SUICIDE("sfx_special_suicide.wav", volume = 0.92f)
}

/**
 * Abstraction over Android audio playback so game state transitions remain 100% JVM unit-testable.
 */
interface SoundPlayer {
    var isEnabled: Boolean
    fun play(effect: SoundEffect)
    fun playSequence(effects: List<SoundEffect>) {
        effects.forEach(::play)
    }
    fun release() {}
}

/**
 * Pure, stateless detector that maps any [GameUiState] transition (local, AI, or LAN)
 * to the exact sequence of [SoundEffect]s to trigger.
 */
object SoundEventDetector {

    fun soundForCardType(type: UnoCardType): SoundEffect = when (type) {
        is UnoCardType.NumberCard -> SoundEffect.CARD_DRAW
        UnoCardType.RevivePlusTwo -> SoundEffect.SPECIAL_REVIVE
        UnoCardType.Freeze -> SoundEffect.SPECIAL_FREEZE
        UnoCardType.Merge -> SoundEffect.SPECIAL_MERGE
        UnoCardType.Sacrifice -> SoundEffect.SPECIAL_SACRIFICE
        UnoCardType.Hijack -> SoundEffect.SPECIAL_HIJACK
        UnoCardType.PawnPromote -> SoundEffect.SPECIAL_PROMOTE
        UnoCardType.Suicide -> SoundEffect.SPECIAL_SUICIDE
        UnoCardType.Shield -> SoundEffect.SPECIAL_SHIELD
        UnoCardType.SwapPositions -> SoundEffect.SPECIAL_SWAP
        UnoCardType.Clone -> SoundEffect.SPECIAL_CLONE
        UnoCardType.Wild -> SoundEffect.SPECIAL_WILD
    }

    fun detect(prev: GameUiState, next: GameUiState): List<SoundEffect> {
        // Fresh match reset — stay silent
        if (
            next.drawRevision == 0 &&
            next.outcome == null &&
            next.moveAnimationEvent == null &&
            next.reviveAnimationEvent == null &&
            next.activeCard == null
        ) {
            return emptyList()
        }

        val effects = mutableListOf<SoundEffect>()

        // 1. Card draw (Number card or Special UNO card)
        val drewCard = next.drawRevision > prev.drawRevision && next.activeCard != null
        if (drewCard) {
            val drawnType = next.activeCard!!.type
            // If Suicide immediately ended the match, let the outcome section emit SPECIAL_SUICIDE once
            if (!(drawnType == UnoCardType.Suicide && prev.outcome == null && next.outcome is GameOutcome.Suicide)) {
                effects += soundForCardType(drawnType)
            }
        }

        // 2. Wild Card selection (choosing a special effect from the Wild picker)
        if (prev.phase == TurnPhase.WILD_CHOOSING && next.phase != TurnPhase.WILD_CHOOSING && !drewCard) {
            when (next.phase) {
                TurnPhase.FREEZING -> effects += SoundEffect.SPECIAL_FREEZE
                TurnPhase.SHIELDING -> effects += SoundEffect.SPECIAL_SHIELD
                TurnPhase.REVIVING -> effects += SoundEffect.SPECIAL_REVIVE
                TurnPhase.HIJACKING -> effects += SoundEffect.SPECIAL_HIJACK
                TurnPhase.MERGING -> effects += SoundEffect.SPECIAL_MERGE
                TurnPhase.SACRIFICING -> effects += SoundEffect.SPECIAL_SACRIFICE
                TurnPhase.PROMOTING_PAWN -> effects += SoundEffect.SPECIAL_PROMOTE
                TurnPhase.SWAPPING -> effects += SoundEffect.SPECIAL_SWAP
                TurnPhase.CLONING -> effects += SoundEffect.SPECIAL_CLONE
                else -> Unit
            }
        }

        // 3. Board piece movement, capture, castle, hijack, or swap
        val newMoveAnim = next.moveAnimationEvent
        if (newMoveAnim != null && newMoveAnim.id != prev.moveAnimationEvent?.id) {
            val primaryMoveEffect = when {
                prev.phase == TurnPhase.SWAPPING -> SoundEffect.SPECIAL_SWAP
                newMoveAnim.isCapture -> SoundEffect.PIECE_CAPTURE
                newMoveAnim.secondaryPiece != null -> SoundEffect.CASTLE
                prev.phase == TurnPhase.HIJACKING -> SoundEffect.SPECIAL_HIJACK
                else -> SoundEffect.PIECE_MOVE
            }
            effects += primaryMoveEffect

            // Back-rank pawn promotion bonus chime (unless checkmate will immediately fanfare)
            val wasPawn = prev.board[newMoveAnim.from.row][newMoveAnim.from.col]?.type == PieceType.PAWN
            val nowPiece = next.board[newMoveAnim.to.row][newMoveAnim.to.col]
            if (wasPawn && nowPiece != null && nowPiece.type != PieceType.PAWN && next.outcome == null) {
                effects += SoundEffect.SPECIAL_PROMOTE
            }
        }

        // 4. Special card board resolution effects (Revive / Merge / Sacrifice / Promote / Clone / Freeze / Shield / Suicide)
        val newReviveAnim = next.reviveAnimationEvent
        if (newReviveAnim != null && newReviveAnim.id != prev.reviveAnimationEvent?.id) {
            val spawnSound = when (prev.phase) {
                TurnPhase.REVIVING -> SoundEffect.SPECIAL_REVIVE
                TurnPhase.MERGING -> SoundEffect.SPECIAL_MERGE
                TurnPhase.SACRIFICING -> SoundEffect.SPECIAL_SACRIFICE
                TurnPhase.PROMOTING_PAWN -> SoundEffect.SPECIAL_PROMOTE
                TurnPhase.CLONING -> SoundEffect.SPECIAL_CLONE
                else -> SoundEffect.SPECIAL_REVIVE
            }
            effects += spawnSound
        }

        if (next.frozenPieceId != null && next.frozenPieceId != prev.frozenPieceId && !drewCard) {
            effects += SoundEffect.SPECIAL_FREEZE
        }

        if (next.shieldedPieceId != null && next.shieldedPieceId != prev.shieldedPieceId && !drewCard) {
            effects += SoundEffect.SPECIAL_SHIELD
        }

        if (prev.phase == TurnPhase.SUICIDING && next.phase != TurnPhase.SUICIDING && next.outcome == null) {
            effects += SoundEffect.SPECIAL_SUICIDE
        }

        // 5. Match-ending outcomes or Check alert
        if (prev.outcome == null && next.outcome != null) {
            when (next.outcome) {
                is GameOutcome.Checkmate -> effects += SoundEffect.CHECKMATE
                is GameOutcome.Forfeit -> effects += SoundEffect.FORFEIT
                is GameOutcome.Suicide -> effects += SoundEffect.SPECIAL_SUICIDE
                GameOutcome.Stalemate -> effects += SoundEffect.GAME_END_DRAW_OR_LOSS
            }
        } else if (
            next.outcome == null &&
            next.checkedKingSquare != null &&
            next.checkedKingSquare != prev.checkedKingSquare
        ) {
            effects += SoundEffect.CHECK
        }

        return effects.distinct()
    }
}
