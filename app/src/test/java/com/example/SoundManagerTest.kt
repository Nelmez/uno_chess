package com.example

import com.example.audio.SoundEffect
import com.example.audio.SoundPlayer
import com.example.audio.SoundSynthesizer
import com.example.model.CastlingRights
import com.example.model.ChessPiece
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.SpecialCardKey
import com.example.model.Square
import com.example.model.UnoCard
import com.example.model.UnoCardType
import com.example.model.UnoColor
import com.example.viewmodel.UnoChessViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class SoundManagerTest {

    private class RecordingSoundPlayer : SoundPlayer {
        override var isEnabled: Boolean = true
        val played = mutableListOf<SoundEffect>()
        val sequences = mutableListOf<List<SoundEffect>>()

        override fun play(effect: SoundEffect) {
            if (!isEnabled) return
            played += effect
        }

        override fun playSequence(effects: List<SoundEffect>) {
            if (!isEnabled || effects.isEmpty()) return
            sequences += effects
            played += effects
        }

        fun clear() {
            played.clear()
            sequences.clear()
        }
    }

    private fun boardOf(vararg placements: Pair<Square, ChessPiece>): List<List<ChessPiece?>> {
        val board = MutableList(8) { MutableList<ChessPiece?>(8) { null } }
        placements.forEach { (sq, piece) -> board[sq.row][sq.col] = piece }
        return board.map { it.toList() }
    }

    @Test
    fun `synthesizer produces valid distinct non-silent WAV buffers for every sound effect`() {
        val fingerprints = mutableSetOf<Int>()
        for (effect in SoundEffect.entries) {
            val wav = SoundSynthesizer.generateWavBytes(effect)
            assertTrue("WAV too short for $effect", wav.size > 44 + 1000)
            // Verify RIFF / WAVE header
            assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
            assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
            assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
            assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))

            val samples = SoundSynthesizer.generatePcmSamples(effect)
            val maxAmp = samples.maxOf { abs(it.toInt()) }
            assertTrue("Audio should be audible for $effect (peak=$maxAmp)", maxAmp > 10_000)
            assertTrue("Each effect must have a distinct waveform ($effect)", fingerprints.add(wav.contentHashCode()))
        }
    }

    @Test
    fun `viewmodel emits distinct sound effects for draw move capture check and checkmate`() {
        val deck = listOf(
            UnoCard(1, UnoColor.RED, UnoCardType.NumberCard(2)),
            UnoCard(2, UnoColor.BLUE, UnoCardType.NumberCard(1)),
            UnoCard(3, UnoColor.GREEN, UnoCardType.NumberCard(1))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }
        val recorder = RecordingSoundPlayer()
        vm.setSoundPlayerForTest(recorder)

        // White King f6 (2,5), Black King h8 (0,7), White Rook a2 (6,0) captures Black Pawn d2 (6,3),
        // then White Queen g1 (7,6) -> g7 (1,6) delivers checkmate.
        val cleanBoard = boardOf(
            Square(2, 5) to ChessPiece(1, PieceType.KING, PieceColor.WHITE), // f6
            Square(6, 0) to ChessPiece(2, PieceType.ROOK, PieceColor.WHITE), // a2
            Square(6, 3) to ChessPiece(3, PieceType.PAWN, PieceColor.BLACK), // d2
            Square(7, 6) to ChessPiece(4, PieceType.QUEEN, PieceColor.WHITE), // g1
            Square(0, 7) to ChessPiece(5, PieceType.KING, PieceColor.BLACK) // h8
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = cleanBoard,
                castlingRights = CastlingRights(false, false, false, false)
            )
        )
        assertTrue("setStateForTest should stay silent", recorder.played.isEmpty())

        // 1. Draw NumberCard(2) -> CARD_DRAW
        vm.drawCard()
        assertEquals(listOf(SoundEffect.CARD_DRAW), recorder.played)
        recorder.clear()

        // 2. Capture Black Pawn d2 with White Rook a2 -> PIECE_CAPTURE
        vm.onSquareTapped(Square(6, 0))
        assertTrue("Selecting a piece should stay silent", recorder.played.isEmpty())
        vm.onSquareTapped(Square(6, 3))
        assertEquals(listOf(SoundEffect.PIECE_CAPTURE), recorder.played)
        recorder.clear()

        // 3. Move White Queen g1 (7,6) -> g7 (1,6) delivering Checkmate -> PIECE_MOVE + CHECKMATE
        vm.onSquareTapped(Square(7, 6))
        vm.onSquareTapped(Square(1, 6))
        assertEquals(listOf(SoundEffect.PIECE_MOVE, SoundEffect.CHECKMATE), recorder.played)
        recorder.clear()

        // 4. Resetting game should remain silent
        vm.resetGame()
        assertTrue("Reset game should not trigger sound", recorder.played.isEmpty())

        // 5. Normal non-capture move -> PIECE_MOVE
        vm.drawCard() // NumberCard(2)
        recorder.clear()
        vm.onSquareTapped(Square(6, 4)) // e2 Pawn
        vm.onSquareTapped(Square(4, 4)) // e4
        assertEquals(listOf(SoundEffect.PIECE_MOVE), recorder.played)
        recorder.clear()

        // 6. Forfeit -> FORFEIT
        vm.forfeitGame()
        assertEquals(listOf(SoundEffect.FORFEIT), recorder.played)
    }

    @Test
    fun `viewmodel emits distinct special card sound effects when drawing and applying special cards`() {
        val deck = listOf(
            UnoCard(1, UnoColor.YELLOW, UnoCardType.Shield),
            UnoCard(2, UnoColor.BLUE, UnoCardType.Freeze),
            UnoCard(3, UnoColor.GREEN, UnoCardType.SwapPositions),
            UnoCard(4, UnoColor.RED, UnoCardType.Wild)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }
        val recorder = RecordingSoundPlayer()
        vm.setSoundPlayerForTest(recorder)

        // 1. Draw Shield -> SPECIAL_SHIELD
        vm.drawCard()
        assertEquals(listOf(SoundEffect.SPECIAL_SHIELD), recorder.played)
        recorder.clear()

        // Apply Shield on White Pawn e2 (6,4) -> SPECIAL_SHIELD
        vm.onSquareTapped(Square(6, 4))
        assertEquals(listOf(SoundEffect.SPECIAL_SHIELD), recorder.played)
        recorder.clear()

        // Bonus move e2 -> e4 -> PIECE_MOVE
        vm.onSquareTapped(Square(6, 4))
        vm.onSquareTapped(Square(4, 4))
        assertEquals(listOf(SoundEffect.PIECE_MOVE), recorder.played)
        recorder.clear()

        // 2. Black draws Freeze -> SPECIAL_FREEZE
        vm.drawCard()
        assertEquals(listOf(SoundEffect.SPECIAL_FREEZE), recorder.played)
        recorder.clear()

        // Black freezes White Queen d1 (7,3) -> SPECIAL_FREEZE
        vm.onSquareTapped(Square(7, 3))
        assertEquals(listOf(SoundEffect.SPECIAL_FREEZE), recorder.played)
        recorder.clear()

        // Black bonus move e7 -> e5 -> PIECE_MOVE
        vm.onSquareTapped(Square(1, 4))
        vm.onSquareTapped(Square(3, 4))
        assertEquals(listOf(SoundEffect.PIECE_MOVE), recorder.played)
        recorder.clear()

        // 3. White draws Swap Positions -> SPECIAL_SWAP
        vm.drawCard()
        assertEquals(listOf(SoundEffect.SPECIAL_SWAP), recorder.played)
        recorder.clear()

        // White swaps Rook a1 (7,0) and Knight b1 (7,1) -> SPECIAL_SWAP
        vm.onSquareTapped(Square(7, 0))
        vm.onSquareTapped(Square(7, 1))
        assertEquals(listOf(SoundEffect.SPECIAL_SWAP), recorder.played)
        recorder.clear()

        // 4. Black draws Wild Card -> SPECIAL_WILD
        vm.drawCard()
        assertEquals(listOf(SoundEffect.SPECIAL_WILD), recorder.played)
        recorder.clear()

        // Black picks Clone from Wild picker -> SPECIAL_CLONE
        vm.onSelectWildEffect(SpecialCardKey.CLONE)
        assertEquals(listOf(SoundEffect.SPECIAL_CLONE), recorder.played)
        assertFalse(recorder.played.contains(SoundEffect.CARD_DRAW))
    }
}
