package com.example

import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.PlayerId
import com.example.model.SpecialCardKey
import com.example.model.Square
import com.example.model.TurnPhase
import com.example.model.UnoCard
import com.example.model.UnoCardType
import com.example.model.UnoColor
import com.example.network.LanAction
import com.example.network.LanConnectionStatus
import com.example.network.LanStateCodec
import com.example.viewmodel.UnoChessViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LanMultiplayerTest {

    private fun awaitCondition(timeoutMs: Long = 4000, predicate: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (predicate()) return
            Thread.sleep(25)
        }
        assertTrue("Timed out waiting for LAN state condition", predicate())
    }

    @Test
    fun `lan codec round trips actions and full game state including freeze and moved ids`() {
        val vm = UnoChessViewModel(Random(7)) {
            listOf(
                UnoCard(1, UnoColor.BLUE, UnoCardType.Freeze),
                UnoCard(2, UnoColor.RED, UnoCardType.NumberCard(2))
            )
        }
        vm.drawCard()
        val freezeState = vm.uiState.value
        val decodedFreeze = LanStateCodec.decodeState(LanStateCodec.encodeState(freezeState))
        assertNotNull(decodedFreeze)
        assertEquals(TurnPhase.FREEZING, decodedFreeze!!.phase)
        assertEquals(UnoCardType.Freeze, decodedFreeze.activeCard?.type)
        assertEquals(freezeState.validFreezeSquares, decodedFreeze.validFreezeSquares)

        // Lock Black Queen on d8 (0,3)
        vm.onSquareTapped(Square(0, 3))
        val afterFreeze = vm.uiState.value
        val decodedAfterFreeze = LanStateCodec.decodeState(LanStateCodec.encodeState(afterFreeze))
        assertNotNull(decodedAfterFreeze)
        assertEquals(afterFreeze.frozenPieceId, decodedAfterFreeze!!.frozenPieceId)
        assertEquals(PieceColor.BLACK, decodedAfterFreeze.frozenOwner)
        assertEquals(TurnPhase.MOVING, decodedAfterFreeze.phase)

        val actions = listOf(
            LanAction.DrawCard,
            LanAction.TapSquare(Square(6, 4)),
            LanAction.SelectPromotion(PieceType.QUEEN),
            LanAction.CancelPromotion,
            LanAction.SelectRevivePiece(12),
            LanAction.ConfirmMerge(PieceType.BISHOP),
            LanAction.ClearMergeSelection,
            LanAction.SkipMerge,
            LanAction.ConfirmSacrifice("r_b2p"),
            LanAction.SkipSacrifice,
            LanAction.ConfirmPawnPromote(PieceType.KNIGHT),
            LanAction.ClearSwapSelection,
            LanAction.SkipSwap,
            LanAction.ClearCloneSelection,
            LanAction.SkipClone,
            LanAction.SelectWildEffect(SpecialCardKey.CLONE),
            LanAction.Forfeit,
            LanAction.ResetGame
        )
        actions.forEach { action ->
            val decodedAction = LanStateCodec.decodeAction(LanStateCodec.encodeAction(action))
            assertEquals(action, decodedAction)
        }

        // Verify Shield + enabledSpecialCards + Wild state round-trip
        val customSpecialState = freezeState.copy(
            phase = TurnPhase.WILD_CHOOSING,
            activeCard = UnoCard(99, UnoColor.YELLOW, UnoCardType.Wild),
            shieldedPieceId = 14,
            shieldedOwner = PieceColor.WHITE,
            availableWildEffects = listOf(SpecialCardKey.SHIELD, SpecialCardKey.SWAP),
            enabledSpecialCards = setOf(SpecialCardKey.SHIELD, SpecialCardKey.SWAP, SpecialCardKey.WILD)
        )
        val decodedCustom = LanStateCodec.decodeState(LanStateCodec.encodeState(customSpecialState))
        assertNotNull(decodedCustom)
        assertEquals(TurnPhase.WILD_CHOOSING, decodedCustom!!.phase)
        assertEquals(UnoCardType.Wild, decodedCustom.activeCard?.type)
        assertEquals(14, decodedCustom.shieldedPieceId)
        assertEquals(PieceColor.WHITE, decodedCustom.shieldedOwner)
        assertEquals(listOf(SpecialCardKey.SHIELD, SpecialCardKey.SWAP), decodedCustom.availableWildEffects)
        assertEquals(
            setOf(SpecialCardKey.SHIELD, SpecialCardKey.SWAP, SpecialCardKey.WILD),
            decodedCustom.enabledSpecialCards
        )

        // Verify Forfeit & Suicide outcome encoding/decoding
        val forfeitState = freezeState.copy(
            phase = TurnPhase.GAME_OVER,
            outcome = com.example.model.GameOutcome.Forfeit(
                forfeitingPlayer = PlayerId.PLAYER_1,
                winnerPlayer = PlayerId.PLAYER_2,
                winnerColor = PieceColor.BLACK
            )
        )
        val decodedForfeit = LanStateCodec.decodeState(LanStateCodec.encodeState(forfeitState))
        assertEquals(forfeitState.outcome, decodedForfeit?.outcome)

        val suicideState = freezeState.copy(
            phase = TurnPhase.GAME_OVER,
            outcome = com.example.model.GameOutcome.Suicide(
                losingPlayer = PlayerId.PLAYER_2,
                winnerPlayer = PlayerId.PLAYER_1,
                winnerColor = PieceColor.WHITE
            )
        )
        val decodedSuicide = LanStateCodec.decodeState(LanStateCodec.encodeState(suicideState))
        assertEquals(suicideState.outcome, decodedSuicide?.outcome)

        // Verify MatchStatsManager JSON persistence round-trip under Robolectric
        val statsVm = UnoChessViewModel(Random(9))
        statsVm.forfeitGame()
        val encodedStats = com.example.engine.MatchStatsManager.encode(statsVm.matchStats.value)
        val decodedStats = com.example.engine.MatchStatsManager.decode(encodedStats)
        assertNotNull(decodedStats)
        assertEquals(1, decodedStats!!.currentStreak)
        assertEquals(1, decodedStats.bestStreak)
        assertEquals(1, decodedStats.history.size)
    }

    @Test
    fun `host and guest synchronize moves and freeze over local tcp`() {
        val deterministicDeck = listOf(
            UnoCard(1, UnoColor.BLUE, UnoCardType.NumberCard(1)),
            UnoCard(2, UnoColor.YELLOW, UnoCardType.Freeze),
            UnoCard(3, UnoColor.GREEN, UnoCardType.NumberCard(2))
        )
        val hostVm = UnoChessViewModel(Random(1)) { deterministicDeck }
        val guestVm = UnoChessViewModel(Random(2))

        try {
            hostVm.startLanHost(context = null, preferredPort = 0)
            awaitCondition { hostVm.lanState.value.hostPort > 0 }
            val port = hostVm.lanState.value.hostPort

            guestVm.joinLanHost("127.0.0.1", port)
            awaitCondition {
                hostVm.lanState.value.status == LanConnectionStatus.CONNECTED &&
                    guestVm.lanState.value.status == LanConnectionStatus.CONNECTED
            }

            // Guest cannot act while it is Player 1's turn
            guestVm.drawCard()
            Thread.sleep(60)
            assertEquals(TurnPhase.AWAITING_DRAW, hostVm.uiState.value.phase)

            // Host (Player 1) draws 1 and moves White pawn e2 -> e4
            hostVm.drawCard()
            hostVm.onSquareTapped(Square(6, 4))
            hostVm.onSquareTapped(Square(4, 4))

            awaitCondition {
                guestVm.uiState.value.board[4][4]?.type == PieceType.PAWN &&
                    guestVm.uiState.value.activePlayer == PlayerId.PLAYER_2 &&
                    guestVm.uiState.value.phase == TurnPhase.AWAITING_DRAW
            }

            // Guest (Player 2) draws Freeze -> enters FREEZING on both Host and Guest
            guestVm.drawCard()
            awaitCondition {
                hostVm.uiState.value.phase == TurnPhase.FREEZING &&
                    guestVm.uiState.value.phase == TurnPhase.FREEZING
            }

            // Guest taps White Queen on d1 (7,3) -> freezes it and transitions to 1 bonus move
            val whiteQueenId = hostVm.uiState.value.board[7][3]!!.id
            guestVm.onSquareTapped(Square(7, 3))
            awaitCondition {
                hostVm.uiState.value.frozenPieceId == whiteQueenId &&
                    guestVm.uiState.value.frozenPieceId == whiteQueenId &&
                    guestVm.uiState.value.phase == TurnPhase.MOVING &&
                    guestVm.uiState.value.movesRemaining == 1
            }
        } finally {
            guestVm.leaveLanMatch()
            hostVm.leaveLanMatch()
        }
    }

    @Test
    fun `host custom special card toggles sync deck to guest over local tcp`() {
        val hostVm = UnoChessViewModel(Random(11))
        val guestVm = UnoChessViewModel(Random(22))

        try {
            // Host disables all special cards, then enables only Shield (2) + Clone (2) = 20 total cards
            hostVm.setAllHostSpecialCards(false)
            hostVm.toggleHostSpecialCard(SpecialCardKey.SHIELD)
            hostVm.toggleHostSpecialCard(SpecialCardKey.CLONE)

            hostVm.startLanHost(context = null, preferredPort = 0)
            awaitCondition { hostVm.lanState.value.hostPort > 0 }
            val port = hostVm.lanState.value.hostPort

            guestVm.joinLanHost("127.0.0.1", port)
            awaitCondition {
                hostVm.lanState.value.status == LanConnectionStatus.CONNECTED &&
                    guestVm.lanState.value.status == LanConnectionStatus.CONNECTED &&
                    guestVm.uiState.value.drawPile.size == 20
            }

            val expectedEnabled = setOf(SpecialCardKey.SHIELD, SpecialCardKey.CLONE)
            assertEquals(expectedEnabled, hostVm.uiState.value.enabledSpecialCards)
            assertEquals(expectedEnabled, guestVm.uiState.value.enabledSpecialCards)
            assertEquals(20, hostVm.uiState.value.drawPile.size)
            assertEquals(20, guestVm.uiState.value.drawPile.size)
        } finally {
            guestVm.leaveLanMatch()
            hostVm.leaveLanMatch()
        }
    }
}
