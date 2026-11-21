package com.example

import com.example.engine.ChessEngine
import com.example.engine.MatchStatsManager
import com.example.engine.UnoChessAi
import com.example.engine.UnoDeck
import com.example.model.AiDifficulty
import com.example.model.CastlingRights
import com.example.model.ChessPiece
import com.example.model.GameOutcome
import com.example.model.MatchEndReason
import com.example.model.MatchStats
import com.example.model.OpponentMode
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.PlayerId
import com.example.model.SpecialCardKey
import com.example.model.Square
import com.example.model.TurnPhase
import com.example.model.UnoCard
import com.example.model.UnoCardType
import com.example.model.UnoColor
import com.example.model.grantsMove
import com.example.network.LanStateCodec
import com.example.viewmodel.UnoChessViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class UnoChessEngineTest {

    private fun emptyBoard(): MutableList<MutableList<ChessPiece?>> =
        MutableList(8) { MutableList(8) { null } }

    private fun boardOf(vararg placements: Pair<Square, ChessPiece>): List<List<ChessPiece?>> {
        val board = emptyBoard()
        placements.forEach { (sq, piece) -> board[sq.row][sq.col] = piece }
        return board.map { it.toList() }
    }

    @Test
    fun `uno deck has exact 38 card distribution with all special cards`() {
        val deck = UnoDeck.createShuffledDeck(Random(42))
        assertEquals(38, deck.size)
        for (n in 1..4) {
            assertEquals(4, deck.count { it.type == UnoCardType.NumberCard(n) })
        }
        assertEquals(3, deck.count { it.type == UnoCardType.RevivePlusTwo })
        assertEquals(2, deck.count { it.type == UnoCardType.Freeze })
        assertEquals(2, deck.count { it.type == UnoCardType.Shield })
        assertEquals(2, deck.count { it.type == UnoCardType.Merge })
        assertEquals(2, deck.count { it.type == UnoCardType.Sacrifice })
        assertEquals(2, deck.count { it.type == UnoCardType.SwapPositions })
        assertEquals(2, deck.count { it.type == UnoCardType.Clone })
        assertEquals(2, deck.count { it.type == UnoCardType.Hijack })
        assertEquals(2, deck.count { it.type == UnoCardType.PawnPromote })
        assertEquals(2, deck.count { it.type == UnoCardType.Suicide })
        assertEquals(1, deck.count { it.type == UnoCardType.Wild })
    }

    @Test
    fun `custom deck filtering respects enabled special cards`() {
        val numbersOnly = UnoDeck.createShuffledDeck(Random(7), enabledSpecials = emptySet())
        assertEquals(16, numbersOnly.size)
        assertTrue(numbersOnly.all { it.type is UnoCardType.NumberCard })

        val subset = setOf(SpecialCardKey.SHIELD, SpecialCardKey.WILD)
        val filtered = UnoDeck.createShuffledDeck(Random(7), enabledSpecials = subset)
        assertEquals(UnoDeck.deckSizeFor(subset), filtered.size)
        assertEquals(19, filtered.size)
        assertEquals(2, filtered.count { it.type == UnoCardType.Shield })
        assertEquals(1, filtered.count { it.type == UnoCardType.Wild })
        assertEquals(0, filtered.count { it.type == UnoCardType.Freeze })
        assertEquals(0, filtered.count { it.type == UnoCardType.Suicide })
    }

    @Test
    fun `multi move card enforces max 1 move per piece per turn`() {
        val deck = listOf(
            UnoCard(1, UnoColor.BLUE, UnoCardType.NumberCard(3)),
            UnoCard(2, UnoColor.RED, UnoCardType.NumberCard(1))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        vm.drawCard()
        assertEquals(TurnPhase.MOVING, vm.uiState.value.phase)
        assertEquals(3, vm.uiState.value.movesRemaining)

        // Move 1: White Knight g1 -> f3
        vm.onSquareTapped(Square(7, 6))
        vm.onSquareTapped(Square(5, 5))
        assertEquals(2, vm.uiState.value.movesRemaining)

        // Attempting to select the same Knight on f3 MUST be rejected
        vm.onSquareTapped(Square(5, 5))
        assertNull(vm.uiState.value.selectedSquare)
        assertTrue(vm.uiState.value.legalMovesForSelected.isEmpty())
        assertNotNull(vm.uiState.value.noticeBanner)

        // Move 2: Move a distinct piece — White Pawn e2 -> e4
        vm.onSquareTapped(Square(6, 4))
        vm.onSquareTapped(Square(4, 4))
        assertEquals(1, vm.uiState.value.movesRemaining)

        // Move 3: Move another distinct piece — White Pawn d2 -> d4
        vm.onSquareTapped(Square(6, 3))
        vm.onSquareTapped(Square(4, 3))
        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
        assertTrue(vm.uiState.value.movedPieceIdsThisTurn.isEmpty())
    }

    @Test
    fun `multi move card auto passes when no unmoved piece has legal moves`() {
        val deck = listOf(
            UnoCard(1, UnoColor.GREEN, UnoCardType.NumberCard(3))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val board = boardOf(
            Square(7, 4) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(0, 4) to ChessPiece(2, PieceType.KING, PieceColor.BLACK)
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = board,
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        vm.drawCard()
        assertEquals(3, vm.uiState.value.movesRemaining)

        vm.onSquareTapped(Square(7, 4))
        vm.onSquareTapped(Square(7, 3))

        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
        assertEquals(0, vm.uiState.value.movesRemaining)
    }

    @Test
    fun `giving check mid card immediately forfeits remaining moves`() {
        val deck = listOf(
            UnoCard(1, UnoColor.RED, UnoCardType.NumberCard(4))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val customBoard = boardOf(
            Square(7, 4) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(0, 4) to ChessPiece(2, PieceType.KING, PieceColor.BLACK),
            Square(7, 0) to ChessPiece(3, PieceType.ROOK, PieceColor.WHITE)
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = customBoard,
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        vm.drawCard()
        assertEquals(4, vm.uiState.value.movesRemaining)

        vm.onSquareTapped(Square(7, 0))
        vm.onSquareTapped(Square(0, 0))

        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
        assertEquals(0, vm.uiState.value.movesRemaining)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
        assertEquals(Square(0, 4), vm.uiState.value.checkedKingSquare)
    }

    @Test
    fun `starting turn in check auto discards special cards until number card`() {
        val deck = listOf(
            UnoCard(1, UnoColor.GREEN, UnoCardType.RevivePlusTwo),
            UnoCard(2, UnoColor.YELLOW, UnoCardType.Freeze),
            UnoCard(3, UnoColor.RED, UnoCardType.Suicide),
            UnoCard(4, UnoColor.BLUE, UnoCardType.NumberCard(2))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val boardInCheck = boardOf(
            Square(7, 4) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(7, 0) to ChessPiece(4, PieceType.ROOK, PieceColor.WHITE),
            Square(0, 7) to ChessPiece(2, PieceType.KING, PieceColor.BLACK),
            Square(3, 4) to ChessPiece(3, PieceType.ROOK, PieceColor.BLACK)
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = boardInCheck,
                capturedPieces = mapOf(
                    PieceColor.WHITE to listOf(ChessPiece(9, PieceType.KNIGHT, PieceColor.WHITE)),
                    PieceColor.BLACK to emptyList()
                ),
                checkedKingSquare = Square(7, 4),
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        vm.drawCard()
        assertEquals(TurnPhase.MOVING, vm.uiState.value.phase)
        assertEquals(UnoCardType.NumberCard(2), vm.uiState.value.activeCard?.type)
        assertEquals(2, vm.uiState.value.movesRemaining)

        vm.onSquareTapped(Square(7, 4))
        vm.onSquareTapped(Square(7, 3))
        assertEquals(TurnPhase.MOVING, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.movesRemaining)
        assertNull(vm.uiState.value.checkedKingSquare)
    }

    @Test
    fun `freeze locks enemy piece grants 1 bonus move and expires after opponent turn`() {
        val deck = listOf(
            UnoCard(1, UnoColor.BLUE, UnoCardType.Freeze),
            UnoCard(2, UnoColor.RED, UnoCardType.NumberCard(1)),
            UnoCard(3, UnoColor.GREEN, UnoCardType.NumberCard(1))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        vm.drawCard()
        assertEquals(TurnPhase.FREEZING, vm.uiState.value.phase)
        assertFalse(Square(0, 4) in vm.uiState.value.validFreezeSquares)
        assertTrue(Square(0, 3) in vm.uiState.value.validFreezeSquares)

        val blackQueenId = vm.uiState.value.board[0][3]!!.id
        vm.onSquareTapped(Square(0, 3))

        assertEquals(TurnPhase.MOVING, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.movesRemaining)
        assertEquals(blackQueenId, vm.uiState.value.frozenPieceId)
        assertEquals(PieceColor.BLACK, vm.uiState.value.frozenOwner)

        vm.onSquareTapped(Square(6, 4))
        vm.onSquareTapped(Square(4, 4))
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
        assertEquals(blackQueenId, vm.uiState.value.frozenPieceId)

        vm.drawCard()
        assertEquals(TurnPhase.MOVING, vm.uiState.value.phase)
        vm.onSquareTapped(Square(0, 3))
        assertNull(vm.uiState.value.selectedSquare)

        vm.onSquareTapped(Square(1, 3))
        vm.onSquareTapped(Square(3, 3))
        assertEquals(PlayerId.PLAYER_1, vm.uiState.value.activePlayer)
        assertNull(vm.uiState.value.frozenPieceId)
        assertNull(vm.uiState.value.frozenOwner)
    }

    @Test
    fun `freeze auto thaws if frozen player starts turn in check`() {
        val deck = listOf(
            UnoCard(1, UnoColor.RED, UnoCardType.NumberCard(1))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val whiteRook = ChessPiece(10, PieceType.ROOK, PieceColor.WHITE)
        val board = boardOf(
            Square(7, 4) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(4, 0) to whiteRook,
            Square(0, 0) to ChessPiece(2, PieceType.KING, PieceColor.BLACK),
            Square(0, 4) to ChessPiece(3, PieceType.ROOK, PieceColor.BLACK)
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = board,
                frozenPieceId = whiteRook.id,
                frozenOwner = PieceColor.WHITE,
                checkedKingSquare = Square(7, 4),
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        vm.drawCard()
        assertNull(vm.uiState.value.frozenPieceId)
        assertNull(vm.uiState.value.frozenOwner)
        vm.onSquareTapped(Square(4, 0))
        assertTrue(vm.uiState.value.legalMovesForSelected.any { it.to == Square(4, 4) })
    }

    @Test
    fun `revive rework transforms 1 own pawn into captured piece and is capped to 1`() {
        val deck = listOf(
            UnoCard(1, UnoColor.GREEN, UnoCardType.RevivePlusTwo),
            UnoCard(2, UnoColor.BLUE, UnoCardType.NumberCard(1))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val board = boardOf(
            Square(7, 0) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(6, 2) to ChessPiece(5, PieceType.PAWN, PieceColor.WHITE), // c2 Pawn
            Square(0, 7) to ChessPiece(2, PieceType.KING, PieceColor.BLACK)
        )
        val capQueen = ChessPiece(10, PieceType.QUEEN, PieceColor.WHITE)
        val capRook = ChessPiece(11, PieceType.ROOK, PieceColor.WHITE)

        vm.setStateForTest(
            vm.uiState.value.copy(
                board = board,
                capturedPieces = mapOf(
                    PieceColor.WHITE to listOf(capQueen, capRook),
                    PieceColor.BLACK to emptyList()
                ),
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        vm.drawCard()
        assertEquals(TurnPhase.REVIVING, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.revivesRemaining)
        assertEquals(1, vm.uiState.value.revivesTotalForCard)
        assertTrue(Square(6, 2) in vm.uiState.value.validReviveSquares)

        vm.onCapturedPieceSelected(capQueen.id)
        vm.onSquareTapped(Square(6, 2))

        // Pawn on c2 transformed into Queen, turn finishes after 1 revive!
        assertEquals(PieceType.QUEEN, vm.uiState.value.board[6][2]?.type)
        assertEquals(capQueen.id, vm.uiState.value.board[6][2]?.id)
        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
    }

    @Test
    fun `revive rework auto redraws when player has captured pieces but no pawns on board`() {
        val deck = listOf(
            UnoCard(1, UnoColor.GREEN, UnoCardType.RevivePlusTwo),
            UnoCard(2, UnoColor.BLUE, UnoCardType.NumberCard(2))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val boardWithoutWhitePawns = boardOf(
            Square(7, 0) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(0, 7) to ChessPiece(2, PieceType.KING, PieceColor.BLACK)
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = boardWithoutWhitePawns,
                capturedPieces = mapOf(
                    PieceColor.WHITE to listOf(ChessPiece(10, PieceType.KNIGHT, PieceColor.WHITE)),
                    PieceColor.BLACK to emptyList()
                ),
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        vm.drawCard()
        assertEquals(TurnPhase.MOVING, vm.uiState.value.phase)
        assertEquals(UnoCardType.NumberCard(2), vm.uiState.value.activeCard?.type)
        assertNotNull(vm.uiState.value.noticeBanner)
    }

    @Test
    fun `revive rework with zero captured pieces allows placing a new pawn`() {
        val deck = listOf(
            UnoCard(1, UnoColor.RED, UnoCardType.RevivePlusTwo)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        // Standard starting board has empty rank 3 (row 5) squares for White
        vm.drawCard()
        assertEquals(TurnPhase.REVIVING, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.revivesRemaining)
        assertTrue(Square(5, 4) in vm.uiState.value.validReviveSquares) // e3 empty square

        vm.onSquareTapped(Square(5, 4))
        assertEquals(PieceType.PAWN, vm.uiState.value.board[5][4]?.type)
        assertEquals(PieceColor.WHITE, vm.uiState.value.board[5][4]?.color)
        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
    }

    @Test
    fun `merge card supports swap downgrade and 3 pawn merge into knight or bishop`() {
        val deck = listOf(
            UnoCard(1, UnoColor.GREEN, UnoCardType.Merge),
            UnoCard(2, UnoColor.RED, UnoCardType.Merge),
            UnoCard(3, UnoColor.BLUE, UnoCardType.Merge)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        // 1. Player 1 draws Merge and swaps Knight on g1 (7,6) -> Bishop
        vm.drawCard()
        assertEquals(TurnPhase.MERGING, vm.uiState.value.phase)
        vm.onSquareTapped(Square(7, 6))
        val singleTargets = ChessEngine.getValidTradeTargets(
            vm.uiState.value.board,
            PieceColor.WHITE,
            vm.uiState.value.selectedMergeSquares
        )
        assertTrue(PieceType.BISHOP in singleTargets)
        assertFalse(PieceType.KNIGHT in singleTargets)

        vm.onConfirmMerge(PieceType.BISHOP)
        assertEquals(PieceType.BISHOP, vm.uiState.value.board[7][6]?.type)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)

        // 2. Player 2 merges 3 Black Pawns (a7, b7, c7) -> Knight on a7
        vm.drawCard()
        assertEquals(TurnPhase.MERGING, vm.uiState.value.phase)
        vm.onSquareTapped(Square(1, 0))
        vm.onSquareTapped(Square(1, 1))
        vm.onSquareTapped(Square(1, 2))
        assertEquals(3, vm.uiState.value.selectedMergeSquares.size)

        vm.onConfirmMerge(PieceType.KNIGHT)
        assertEquals(PieceType.KNIGHT, vm.uiState.value.board[1][0]?.type)
        assertNull(vm.uiState.value.board[1][1])
        assertNull(vm.uiState.value.board[1][2])
        assertEquals(PlayerId.PLAYER_1, vm.uiState.value.activePlayer)

        // 3. Skip Merge cleanly
        vm.drawCard()
        vm.onSkipMerge()
        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
    }

    @Test
    fun `sacrifice card splits piece into equal value pieces safely`() {
        val deck = listOf(
            UnoCard(1, UnoColor.YELLOW, UnoCardType.Sacrifice),
            UnoCard(2, UnoColor.BLUE, UnoCardType.Sacrifice)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val board = boardOf(
            Square(7, 0) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(4, 3) to ChessPiece(3, PieceType.ROOK, PieceColor.WHITE), // d4 Rook (5 pts)
            Square(0, 5) to ChessPiece(2, PieceType.KING, PieceColor.BLACK), // f8 King (not on d4 diagonal)
            Square(2, 4) to ChessPiece(4, PieceType.QUEEN, PieceColor.BLACK) // e6 Queen (9 pts)
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = board,
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        // Player 1 draws Sacrifice and splits d4 Rook -> Bishop + 2 Pawns
        vm.drawCard()
        assertEquals(TurnPhase.SACRIFICING, vm.uiState.value.phase)
        assertTrue(Square(4, 3) in vm.uiState.value.validSacrificeSquares)

        vm.onSquareTapped(Square(4, 3))
        val recipes = ChessEngine.getValidSacrificeRecipes(vm.uiState.value.board, PieceColor.WHITE, Square(4, 3))
        val rookToBishopAndPawns = recipes.firstOrNull { it.id == "b2p" }
        assertNotNull(rookToBishopAndPawns)

        vm.onConfirmSacrifice(rookToBishopAndPawns!!.id)
        val whitePieces = vm.uiState.value.board.flatten().filterNotNull().filter { it.color == PieceColor.WHITE }
        assertEquals(1, whitePieces.count { it.type == PieceType.BISHOP })
        assertEquals(2, whitePieces.count { it.type == PieceType.PAWN })
        assertEquals(0, whitePieces.count { it.type == PieceType.ROOK })
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)

        // Player 2 can also skip Sacrifice
        vm.drawCard()
        assertEquals(TurnPhase.SACRIFICING, vm.uiState.value.phase)
        vm.onSkipSacrifice()
        assertEquals(PlayerId.PLAYER_1, vm.uiState.value.activePlayer)
    }

    @Test
    fun `hijack card lets active player move 1 opponent piece legally`() {
        val deck = listOf(
            UnoCard(1, UnoColor.BLUE, UnoCardType.Hijack)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        vm.drawCard()
        assertEquals(TurnPhase.HIJACKING, vm.uiState.value.phase)

        // Player 1 (White) selects Black's e7 pawn (1,4) and moves it to e5 (3,4)
        vm.onSquareTapped(Square(1, 4))
        assertEquals(Square(1, 4), vm.uiState.value.selectedSquare)
        assertTrue(vm.uiState.value.legalMovesForSelected.any { it.to == Square(3, 4) })

        vm.onSquareTapped(Square(3, 4))
        assertEquals(PieceType.PAWN, vm.uiState.value.board[3][4]?.type)
        assertEquals(PieceColor.BLACK, vm.uiState.value.board[3][4]?.color)
        assertNull(vm.uiState.value.board[1][4])
        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
    }

    @Test
    fun `pawn promote card upgrades own pawn on the spot to knight or bishop`() {
        val deck = listOf(
            UnoCard(1, UnoColor.GREEN, UnoCardType.PawnPromote)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        vm.drawCard()
        assertEquals(TurnPhase.PROMOTING_PAWN, vm.uiState.value.phase)
        assertTrue(Square(6, 4) in vm.uiState.value.validPromotePawnSquares)

        vm.onSquareTapped(Square(6, 4))
        assertEquals(Square(6, 4), vm.uiState.value.selectedPromotePawnSquare)

        vm.onConfirmPawnPromote(PieceType.KNIGHT)
        assertEquals(PieceType.KNIGHT, vm.uiState.value.board[6][4]?.type)
        assertEquals(PieceColor.WHITE, vm.uiState.value.board[6][4]?.color)
        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
    }

    @Test
    fun `suicide card removes 1 own piece and loses immediately when down to only king`() {
        val deck = listOf(
            UnoCard(1, UnoColor.RED, UnoCardType.Suicide),
            UnoCard(2, UnoColor.RED, UnoCardType.Suicide)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val board = boardOf(
            Square(7, 4) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(6, 0) to ChessPiece(3, PieceType.PAWN, PieceColor.WHITE),
            Square(0, 4) to ChessPiece(2, PieceType.KING, PieceColor.BLACK)
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = board,
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        // Player 1 has a Pawn -> enters SUICIDING and removes own Pawn on a2 (6,0)
        vm.drawCard()
        assertEquals(TurnPhase.SUICIDING, vm.uiState.value.phase)
        assertEquals(setOf(Square(6, 0)), vm.uiState.value.validSuicideSquares)

        vm.onSquareTapped(Square(6, 0))
        assertNull(vm.uiState.value.board[6][0])
        assertTrue(vm.uiState.value.capturedPieces[PieceColor.WHITE].orEmpty().any { it.id == 3 })
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)

        // Player 2 has ONLY a King -> drawing Suicide immediately loses!
        vm.drawCard()
        assertEquals(TurnPhase.GAME_OVER, vm.uiState.value.phase)
        val outcome = vm.uiState.value.outcome
        assertTrue(outcome is GameOutcome.Suicide)
        val suicideOutcome = outcome as GameOutcome.Suicide
        assertEquals(PlayerId.PLAYER_2, suicideOutcome.losingPlayer)
        assertEquals(PlayerId.PLAYER_1, suicideOutcome.winnerPlayer)
        assertEquals(1, vm.matchStats.value.currentStreak)
        assertEquals(MatchEndReason.SUICIDE, vm.matchStats.value.history.first().reason)
    }

    @Test
    fun `forfeit ends match awards win and updates win streak and match log`() {
        val vm = UnoChessViewModel(Random(1))
        vm.forfeitGame() // Player 1 forfeits -> Player 2 wins

        assertEquals(TurnPhase.GAME_OVER, vm.uiState.value.phase)
        val outcome = vm.uiState.value.outcome as? GameOutcome.Forfeit
        assertNotNull(outcome)
        assertEquals(PlayerId.PLAYER_1, outcome!!.forfeitingPlayer)
        assertEquals(PlayerId.PLAYER_2, outcome.winnerPlayer)

        assertEquals(1, vm.matchStats.value.currentStreak)
        assertEquals(1, vm.matchStats.value.bestStreak)
        assertEquals(1, vm.matchStats.value.totalWinsP2)
        assertEquals(MatchEndReason.FORFEIT, vm.matchStats.value.history.first().reason)

        // Reset and Player 1 forfeits again -> Player 2 streak becomes 2
        vm.resetGame()
        vm.forfeitGame()
        assertEquals(2, vm.matchStats.value.currentStreak)
        assertEquals(2, vm.matchStats.value.bestStreak)

        vm.clearMatchHistory()
        assertEquals(MatchStats(), vm.matchStats.value)
    }

    @Test
    fun `shield card protects own piece from capture grants 1 bonus move and expires after opponent turn`() {
        val deck = listOf(
            UnoCard(1, UnoColor.YELLOW, UnoCardType.Shield),
            UnoCard(2, UnoColor.RED, UnoCardType.NumberCard(1)),
            UnoCard(3, UnoColor.GREEN, UnoCardType.NumberCard(1))
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        val whiteRook = ChessPiece(10, PieceType.ROOK, PieceColor.WHITE)
        val board = boardOf(
            Square(7, 2) to ChessPiece(1, PieceType.KING, PieceColor.WHITE), // c1 White King (not in check)
            Square(4, 4) to whiteRook, // e4 White Rook
            Square(0, 7) to ChessPiece(2, PieceType.KING, PieceColor.BLACK),
            Square(4, 0) to ChessPiece(3, PieceType.ROOK, PieceColor.BLACK) // a4 Black Rook attacking e4
        )
        vm.setStateForTest(
            vm.uiState.value.copy(
                board = board,
                castlingRights = CastlingRights(false, false, false, false)
            )
        )

        // White draws Shield -> enters SHIELDING (King excluded)
        vm.drawCard()
        assertEquals(TurnPhase.SHIELDING, vm.uiState.value.phase)
        assertFalse(Square(7, 2) in vm.uiState.value.validShieldSquares)
        assertTrue(Square(4, 4) in vm.uiState.value.validShieldSquares)

        // Shield White Rook on e4 -> transitions to 1 bonus move
        vm.onSquareTapped(Square(4, 4))
        assertEquals(TurnPhase.MOVING, vm.uiState.value.phase)
        assertEquals(1, vm.uiState.value.movesRemaining)
        assertEquals(whiteRook.id, vm.uiState.value.shieldedPieceId)
        assertEquals(PieceColor.WHITE, vm.uiState.value.shieldedOwner)

        // White makes 1 move (King c1 -> d1) -> Shield stays active for Black's turn
        vm.onSquareTapped(Square(7, 2))
        vm.onSquareTapped(Square(7, 3))
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)
        assertEquals(whiteRook.id, vm.uiState.value.shieldedPieceId)

        // Black draws 1 -> Black Rook on a4 CANNOT capture shielded White Rook on e4
        vm.drawCard()
        vm.onSquareTapped(Square(4, 0))
        assertFalse(vm.uiState.value.legalMovesForSelected.any { it.to == Square(4, 4) })
        assertTrue(vm.uiState.value.legalMovesForSelected.any { it.to == Square(4, 1) })

        // Black moves Rook a4 -> b4 -> turn ends, White's Shield expires!
        vm.onSquareTapped(Square(4, 1))
        assertEquals(PlayerId.PLAYER_1, vm.uiState.value.activePlayer)
        assertNull(vm.uiState.value.shieldedPieceId)
        assertNull(vm.uiState.value.shieldedOwner)
    }

    @Test
    fun `swap positions exchanges two own pieces safely and supports skip`() {
        val deck = listOf(
            UnoCard(1, UnoColor.BLUE, UnoCardType.SwapPositions),
            UnoCard(2, UnoColor.RED, UnoCardType.SwapPositions)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        // Player 1 draws Swap Positions and swaps White Rook a1 (7,0) with White Knight b1 (7,1)
        vm.drawCard()
        assertEquals(TurnPhase.SWAPPING, vm.uiState.value.phase)
        assertTrue(Square(7, 0) in vm.uiState.value.validSwapSquares)

        vm.onSquareTapped(Square(7, 0))
        assertEquals(Square(7, 0), vm.uiState.value.selectedSwapSquare)
        assertTrue(Square(7, 1) in vm.uiState.value.validSwapSquares)
        // Pawn on a2 (6,0) cannot swap onto rank 1 (7,0)
        assertFalse(Square(6, 0) in vm.uiState.value.validSwapSquares)

        vm.onSquareTapped(Square(7, 1))
        assertEquals(PieceType.KNIGHT, vm.uiState.value.board[7][0]?.type)
        assertEquals(PieceType.ROOK, vm.uiState.value.board[7][1]?.type)
        assertFalse(vm.uiState.value.castlingRights.whiteQueenside)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)

        // Player 2 can skip Swap
        vm.drawCard()
        assertEquals(TurnPhase.SWAPPING, vm.uiState.value.phase)
        vm.onSkipSwap()
        assertEquals(PlayerId.PLAYER_1, vm.uiState.value.activePlayer)
        assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
    }

    @Test
    fun `clone card duplicates pawn knight or bishop onto open square`() {
        val deck = listOf(
            UnoCard(1, UnoColor.GREEN, UnoCardType.Clone),
            UnoCard(2, UnoColor.YELLOW, UnoCardType.Clone)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }

        // Player 1 draws Clone -> Pawn/Knight/Bishop eligible, Rook/Queen/King NOT eligible
        vm.drawCard()
        assertEquals(TurnPhase.CLONING, vm.uiState.value.phase)
        assertTrue(Square(7, 1) in vm.uiState.value.validCloneSquares) // b1 Knight
        assertFalse(Square(7, 0) in vm.uiState.value.validCloneSquares) // a1 Rook
        assertFalse(Square(7, 3) in vm.uiState.value.validCloneSquares) // d1 Queen

        // Tap b1 Knight -> empty square on own half or adjacent (wait: b1 is surrounded by a1/c1/a2/b2/c2 -> fallback to rank 3!)
        vm.onSquareTapped(Square(7, 1))
        assertEquals(Square(7, 1), vm.uiState.value.selectedCloneSourceSquare)
        assertTrue(vm.uiState.value.validCloneSquares.isNotEmpty())
        val targetSquare = vm.uiState.value.validCloneSquares.first()

        val initialWhiteKnights = vm.uiState.value.board.flatten().filterNotNull()
            .count { it.color == PieceColor.WHITE && it.type == PieceType.KNIGHT }
        vm.onSquareTapped(targetSquare)

        val updatedWhiteKnights = vm.uiState.value.board.flatten().filterNotNull()
            .count { it.color == PieceColor.WHITE && it.type == PieceType.KNIGHT }
        assertEquals(initialWhiteKnights + 1, updatedWhiteKnights)
        assertEquals(PieceType.KNIGHT, vm.uiState.value.board[targetSquare.row][targetSquare.col]?.type)
        assertEquals(PlayerId.PLAYER_2, vm.uiState.value.activePlayer)

        // Player 2 can skip Clone
        vm.drawCard()
        assertEquals(TurnPhase.CLONING, vm.uiState.value.phase)
        vm.onSkipClone()
        assertEquals(PlayerId.PLAYER_1, vm.uiState.value.activePlayer)
    }

    @Test
    fun `wild card offers enabled playable special cards and activates chosen effect`() {
        val deck = listOf(
            UnoCard(1, UnoColor.RED, UnoCardType.Wild)
        )
        val vm = UnoChessViewModel(Random(1)) { deck }
        vm.setStateForTest(
            vm.uiState.value.copy(
                enabledSpecialCards = setOf(SpecialCardKey.SHIELD, SpecialCardKey.CLONE, SpecialCardKey.WILD)
            )
        )

        vm.drawCard()
        assertEquals(TurnPhase.WILD_CHOOSING, vm.uiState.value.phase)
        assertTrue(SpecialCardKey.SHIELD in vm.uiState.value.availableWildEffects)
        assertTrue(SpecialCardKey.CLONE in vm.uiState.value.availableWildEffects)
        assertFalse(SpecialCardKey.FREEZE in vm.uiState.value.availableWildEffects)
        assertFalse(SpecialCardKey.WILD in vm.uiState.value.availableWildEffects)

        vm.onSelectWildEffect(SpecialCardKey.SHIELD)
        assertEquals(TurnPhase.SHIELDING, vm.uiState.value.phase)
        assertTrue(vm.uiState.value.availableWildEffects.isEmpty())
        assertTrue(vm.uiState.value.validShieldSquares.isNotEmpty())
    }

    @Test
    fun `ai handles all special uno cards legally across difficulties`() {
        AiDifficulty.entries.forEach { difficulty ->
            val deck = listOf(
                UnoCard(1, UnoColor.BLUE, UnoCardType.NumberCard(2)),
                UnoCard(2, UnoColor.RED, UnoCardType.Freeze),
                UnoCard(3, UnoColor.YELLOW, UnoCardType.Shield),
                UnoCard(4, UnoColor.GREEN, UnoCardType.RevivePlusTwo),
                UnoCard(5, UnoColor.YELLOW, UnoCardType.Merge),
                UnoCard(6, UnoColor.RED, UnoCardType.Sacrifice),
                UnoCard(7, UnoColor.BLUE, UnoCardType.SwapPositions),
                UnoCard(8, UnoColor.GREEN, UnoCardType.Clone),
                UnoCard(9, UnoColor.BLUE, UnoCardType.Hijack),
                UnoCard(10, UnoColor.GREEN, UnoCardType.PawnPromote),
                UnoCard(11, UnoColor.YELLOW, UnoCardType.Wild),
                UnoCard(12, UnoColor.RED, UnoCardType.Suicide)
            )
            val vm = UnoChessViewModel(Random(42), aiStepDelayMs = 0L) { deck }
            vm.setOpponentMode(OpponentMode.VsAi(difficulty))

            repeat(deck.size) { index ->
                vm.setStateForTest(
                    vm.uiState.value.copy(
                        activePlayer = PlayerId.PLAYER_2,
                        phase = TurnPhase.AWAITING_DRAW
                    )
                )
                val steps = vm.runAiTurnSynchronouslyForTest()
                assertTrue("AI ($difficulty) failed on card #$index", steps >= 1)
                if (vm.uiState.value.outcome == null) {
                    assertEquals(PlayerId.PLAYER_1, vm.uiState.value.activePlayer)
                    assertEquals(TurnPhase.AWAITING_DRAW, vm.uiState.value.phase)
                }
            }
        }
    }

    @Test
    fun `hard ai always finds immediate checkmate`() {
        val vm = UnoChessViewModel(Random(5)) {
            listOf(UnoCard(1, UnoColor.RED, UnoCardType.NumberCard(2)))
        }
        val mateBoard = boardOf(
            Square(7, 7) to ChessPiece(1, PieceType.KING, PieceColor.WHITE),
            Square(6, 6) to ChessPiece(2, PieceType.PAWN, PieceColor.WHITE),
            Square(6, 7) to ChessPiece(3, PieceType.PAWN, PieceColor.WHITE),
            Square(0, 4) to ChessPiece(4, PieceType.KING, PieceColor.BLACK),
            Square(6, 0) to ChessPiece(5, PieceType.ROOK, PieceColor.BLACK)
        )
        val state = vm.uiState.value.copy(
            board = mateBoard,
            activePlayer = PlayerId.PLAYER_2,
            phase = TurnPhase.MOVING,
            totalMovesForCard = 2,
            movesRemaining = 2,
            castlingRights = CastlingRights(false, false, false, false)
        )

        val move = UnoChessAi.chooseMove(state, AiDifficulty.HARD, Random(1))
        assertNotNull(move)
        assertEquals(Square(6, 0), move!!.from)
        assertEquals(Square(7, 0), move.to)
    }

    @Test
    fun `new game resets board shuffles 38 card deck and clears special states`() {
        val vm = UnoChessViewModel(Random(123))
        val mutatedState = vm.uiState.value.copy(
            activePlayer = PlayerId.PLAYER_2,
            phase = TurnPhase.MOVING,
            movesRemaining = 2,
            totalMovesForCard = 3,
            movedPieceIdsThisTurn = setOf(10, 11),
            frozenPieceId = 4,
            frozenOwner = PieceColor.WHITE,
            shieldedPieceId = 5,
            shieldedOwner = PieceColor.BLACK,
            drawPile = vm.uiState.value.drawPile.drop(6)
        )
        vm.setStateForTest(mutatedState)

        vm.resetGame()
        val reset = vm.uiState.value

        assertEquals(TurnPhase.AWAITING_DRAW, reset.phase)
        assertEquals(PlayerId.PLAYER_1, reset.activePlayer)
        assertEquals(38, reset.drawPile.size)
        assertTrue(reset.discardPile.isEmpty())
        assertTrue(reset.movedPieceIdsThisTurn.isEmpty())
        assertTrue(reset.selectedMergeSquares.isEmpty())
        assertNull(reset.selectedSacrificeSquare)
        assertNull(reset.selectedPromotePawnSquare)
        assertTrue(reset.validSuicideSquares.isEmpty())
        assertNull(reset.frozenPieceId)
        assertNull(reset.shieldedPieceId)
        assertNull(reset.selectedSwapSquare)
        assertNull(reset.selectedCloneSourceSquare)
        assertTrue(reset.availableWildEffects.isEmpty())
        assertNull(reset.activeCard)
        assertNull(reset.outcome)
        assertEquals(32, reset.board.flatten().count { it != null })
    }

    @Test
    fun `card pity chances scale according to non-move draws and match user specification`() {
        val p0 = UnoDeck.getPityChances(0)
        assertEquals(0, p0.totalMovePercent)
        assertEquals(100, p0.naturalDrawPercent)

        val p1 = UnoDeck.getPityChances(1)
        assertEquals(25, p1.twoMovesPercent)
        assertEquals(55, p1.totalMovePercent)
        assertEquals(45, p1.naturalDrawPercent)

        // Exact user requirement: "for example you get merge and revive and your percentage for 2 moves grows to like 40%"
        val p2 = UnoDeck.getPityChances(2)
        assertEquals(40, p2.twoMovesPercent)
        assertEquals(25, p2.threeMovesPercent)
        assertEquals(15, p2.fourMovesPercent)
        assertEquals(10, p2.oneMovePercent)
        assertEquals(90, p2.totalMovePercent)
        assertEquals(10, p2.naturalDrawPercent)

        // Exact user requirement: "and if you still dont get it your 3 and 4 move chance grows even higher"
        val p3 = UnoDeck.getPityChances(3)
        assertEquals(40, p3.threeMovesPercent)
        assertEquals(35, p3.fourMovesPercent)
        assertEquals(20, p3.twoMovesPercent)
        assertEquals(5, p3.oneMovePercent)
        assertEquals(75, p3.threeMovesPercent + p3.fourMovesPercent)
        assertEquals(100, p3.totalMovePercent)
        assertEquals(0, p3.naturalDrawPercent)

        val p4 = UnoDeck.getPityChances(4)
        assertEquals(50, p4.fourMovesPercent)
        assertEquals(40, p4.threeMovesPercent)
        assertEquals(10, p4.twoMovesPercent)
        assertEquals(0, p4.oneMovePercent)
        assertEquals(90, p4.threeMovesPercent + p4.fourMovesPercent)
        assertEquals(100, p4.totalMovePercent)
        assertEquals(0, p4.naturalDrawPercent)
    }

    @Test
    fun `pity target roll favors 2-moves at pity 2 and higher moves at pity 3`() {
        // At pity 2: roll 0..39 returns 2-moves (40%), 40..64 returns 3-moves (25%), 65..79 returns 4-moves (15%), 80..89 returns 1-move (10%), 90..99 returns natural (null)
        val mockRandomRoll0 = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 10 // Within 0..39 -> target 2
        }
        assertEquals(2, UnoDeck.rollPityTarget(2, mockRandomRoll0))

        val mockRandomRoll45 = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 45 // Within 40..64 -> target 3
        }
        assertEquals(3, UnoDeck.rollPityTarget(2, mockRandomRoll45))

        val mockRandomRoll70 = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 70 // Within 65..79 -> target 4
        }
        assertEquals(4, UnoDeck.rollPityTarget(2, mockRandomRoll70))

        val mockRandomRoll95 = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 95 // 90..99 -> natural draw
        }
        assertNull(UnoDeck.rollPityTarget(2, mockRandomRoll95))

        // At pity 3: 0..39 -> 3-moves (40%), 40..74 -> 4-moves (35%), 75..94 -> 2-moves (20%), 95..99 -> 1-move (5%)
        val mockP3Roll10 = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 10
        }
        assertEquals(3, UnoDeck.rollPityTarget(3, mockP3Roll10))

        val mockP3Roll50 = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 50
        }
        assertEquals(4, UnoDeck.rollPityTarget(3, mockP3Roll50))
    }

    @Test
    fun `drawCardWithPity extracts target card from deck and maintains deck conservation`() {
        val drawPile = mutableListOf(
            UnoCard(1, UnoColor.RED, UnoCardType.Merge),
            UnoCard(2, UnoColor.BLUE, UnoCardType.RevivePlusTwo),
            UnoCard(3, UnoColor.YELLOW, UnoCardType.NumberCard(2)),
            UnoCard(4, UnoColor.GREEN, UnoCardType.NumberCard(3))
        )
        val discardPile = mutableListOf<UnoCard>()

        // Mock roll to pick 2-moves
        val mockRandom = object : Random() {
            override fun nextBits(bitCount: Int): Int = 0
            override fun nextInt(until: Int): Int = 5 // within 2-move window at pity 2
        }

        val drawn = UnoDeck.drawCardWithPity(
            drawPile = drawPile,
            discardPile = discardPile,
            pityCount = 2,
            enabledSpecials = SpecialCardKey.entries.toSet(),
            random = mockRandom
        )

        assertEquals(3, drawn.id)
        assertEquals(UnoCardType.NumberCard(2), drawn.type)
        assertEquals(3, drawPile.size)
        // Original cards remaining: 1, 2, 4
        assertEquals(listOf(1, 2, 4), drawPile.map { it.id })
    }

    @Test
    fun `viewmodel tracks pity count per player and resets on move cards`() {
        val customDeck = listOf(
            UnoCard(1, UnoColor.GREEN, UnoCardType.Merge), // P1 draw #1 (non-move)
            UnoCard(2, UnoColor.YELLOW, UnoCardType.Merge), // P2 draw #1 (non-move)
            UnoCard(3, UnoColor.GREEN, UnoCardType.Merge) // P1 draw #2 (non-move)
        )
        val vm = UnoChessViewModel(Random(42)) { customDeck }

        // Start: both players pity = 0
        assertEquals(0, vm.uiState.value.movePityCount[PlayerId.PLAYER_1])
        assertEquals(0, vm.uiState.value.movePityCount[PlayerId.PLAYER_2])

        // Player 1 draws Merge (non-move) -> P1 pity = 1, P2 pity = 0
        vm.drawCard()
        val stateAfterMerge1 = vm.uiState.value
        assertEquals(TurnPhase.MERGING, stateAfterMerge1.phase)
        assertEquals(1, stateAfterMerge1.movePityCount[PlayerId.PLAYER_1])
        assertEquals(0, stateAfterMerge1.movePityCount[PlayerId.PLAYER_2])

        // Skip merge to pass turn to Player 2
        vm.onSkipMerge()
        val stateP2Turn1 = vm.uiState.value
        assertEquals(PlayerId.PLAYER_2, stateP2Turn1.activePlayer)
        assertEquals(1, stateP2Turn1.movePityCount[PlayerId.PLAYER_1])
        assertEquals(0, stateP2Turn1.movePityCount[PlayerId.PLAYER_2])

        // Player 2 draws Merge (non-move) -> P2 pity = 1, P1 pity = 1
        vm.drawCard()
        val stateAfterP2Merge = vm.uiState.value
        assertEquals(TurnPhase.MERGING, stateAfterP2Merge.phase)
        assertEquals(1, stateAfterP2Merge.movePityCount[PlayerId.PLAYER_1])
        assertEquals(1, stateAfterP2Merge.movePityCount[PlayerId.PLAYER_2])

        // Skip merge to pass turn back to Player 1
        vm.onSkipMerge()
        val stateP1Turn2 = vm.uiState.value
        assertEquals(PlayerId.PLAYER_1, stateP1Turn2.activePlayer)
        assertEquals(1, stateP1Turn2.movePityCount[PlayerId.PLAYER_1])
        assertEquals(1, stateP1Turn2.movePityCount[PlayerId.PLAYER_2])

        // Player 1 draws 2nd consecutive Merge (non-move) -> P1 pity increments to 2!
        vm.drawCard()
        val stateAfterMerge2 = vm.uiState.value
        assertEquals(TurnPhase.MERGING, stateAfterMerge2.phase)
        assertEquals(2, stateAfterMerge2.movePityCount[PlayerId.PLAYER_1])
        assertEquals(1, stateAfterMerge2.movePityCount[PlayerId.PLAYER_2])

        // Skip merge to pass turn to Player 2
        vm.onSkipMerge()
        val stateP2Turn2 = vm.uiState.value
        assertEquals(PlayerId.PLAYER_2, stateP2Turn2.activePlayer)

        // Set state to Player 1 awaiting draw with pity = 2 and a NumberCard in drawPile
        val p1Draw3State = stateP2Turn2.copy(
            activePlayer = PlayerId.PLAYER_1,
            phase = TurnPhase.AWAITING_DRAW,
            drawPile = listOf(UnoCard(10, UnoColor.RED, UnoCardType.NumberCard(3)))
        )
        vm.setStateForTest(p1Draw3State)
        assertEquals(2, vm.uiState.value.movePityCount[PlayerId.PLAYER_1])

        // Player 1 draws NumberCard(3) -> P1 pity resets to 0!
        vm.drawCard()
        val stateAfterNumberDraw = vm.uiState.value
        assertEquals(TurnPhase.MOVING, stateAfterNumberDraw.phase)
        assertEquals(0, stateAfterNumberDraw.movePityCount[PlayerId.PLAYER_1])
        // Opponent's pity is unaffected
        assertEquals(1, stateAfterNumberDraw.movePityCount[PlayerId.PLAYER_2])
    }
}
