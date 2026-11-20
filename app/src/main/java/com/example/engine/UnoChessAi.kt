package com.example.engine

import com.example.model.AiDifficulty
import com.example.model.CastlingRights
import com.example.model.ChessMove
import com.example.model.ChessPiece
import com.example.model.GameUiState
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.SpecialCardKey
import com.example.model.Square
import kotlin.math.abs
import kotlin.random.Random

data class AiReviveDecision(
    val pieceId: Int,
    val square: Square
)

data class AiTradeDecision(
    val selectedSquares: List<Square>,
    val targetType: PieceType
)

data class AiPawnPromoteDecision(
    val pawnSquare: Square,
    val targetType: PieceType
)

data class AiSacrificeDecision(
    val square: Square,
    val recipeId: String
)

data class AiSwapDecision(
    val first: Square,
    val second: Square
)

data class AiCloneDecision(
    val source: Square,
    val target: Square
)

/**
 * Local chess + UNO strategy AI supporting Easy, Medium, and Hard difficulties.
 * Respects all legal chess rules, 1-move-per-piece constraints, check-forfeiture,
 * Freeze targeting, and the 1-major-piece +2 Revive cap.
 */
object UnoChessAi {

    fun chooseMove(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): ChessMove? {
        val aiColor = state.activeColor
        val excluded = excludedIdsFor(state, aiColor)
        val protectedIds = protectedIdsFor(state, aiColor.opponent())
        val legalMoves = ChessEngine.getAllLegalMoves(
            board = state.board,
            color = aiColor,
            castlingRights = state.castlingRights,
            enPassantTarget = state.enPassantTarget,
            enPassantOwner = state.enPassantOwner,
            excludedPieceIds = excluded,
            protectedPieceIds = protectedIds
        )
        if (legalMoves.isEmpty()) return null

        return when (difficulty) {
            AiDifficulty.EASY -> chooseEasyMove(state, legalMoves, random)
            AiDifficulty.MEDIUM -> chooseMediumMove(state, legalMoves, random)
            AiDifficulty.HARD -> chooseHardMove(state, legalMoves, excluded, protectedIds, random)
        }
    }

    fun chooseFreezeTarget(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): Square? {
        val targets = state.validFreezeSquares.toList()
        if (targets.isEmpty()) return null

        return when (difficulty) {
            AiDifficulty.EASY -> targets[random.nextInt(targets.size)]
            AiDifficulty.MEDIUM -> {
                targets.maxByOrNull { sq ->
                    val p = state.board[sq.row][sq.col]
                    val base = pieceScore(p?.type)
                    val centerBonus = centerDistanceBonus(sq)
                    base * 10 + centerBonus + random.nextInt(3)
                }
            }
            AiDifficulty.HARD -> {
                val aiColor = state.activeColor
                val enemyColor = aiColor.opponent()
                targets.maxByOrNull { sq ->
                    val p = state.board[sq.row][sq.col] ?: return@maxByOrNull -1000
                    var score = pieceScore(p.type) * 14 + centerDistanceBonus(sq) * 3
                    // Extra priority if this enemy piece currently attacks a valuable AI piece or AI King zone
                    val enemyMoves = ChessEngine.getLegalMovesForSquare(
                        board = state.board,
                        from = sq,
                        castlingRights = state.castlingRights,
                        enPassantTarget = null,
                        enPassantOwner = null
                    )
                    score += enemyMoves.size * 2
                    for (m in enemyMoves) {
                        val victim = state.board[m.to.row][m.to.col]
                        if (victim != null && victim.color == aiColor) {
                            score += pieceScore(victim.type) * 18
                        }
                    }
                    // Slight preference for freezing enemy pieces near AI's half of the board
                    val forwardProgress = if (enemyColor == PieceColor.WHITE) (7 - sq.row) else sq.row
                    score += forwardProgress * 4
                    score
                }
            }
        }
    }

    fun chooseRevive(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): AiReviveDecision? {
        val aiColor = state.activeColor
        val opponentColor = aiColor.opponent()
        val excluded = excludedIdsFor(state, aiColor)
        val ownCaptured = state.capturedPieces[aiColor].orEmpty()

        // Mode B: No captured pieces -> spawn a new Pawn onto a valid empty square
        if (ownCaptured.isEmpty() || state.selectedRevivePieceId == null) {
            val spawnSquares = state.validReviveSquares.toList()
            if (spawnSquares.isEmpty()) return null
            val chosenSq = if (difficulty == AiDifficulty.EASY) {
                spawnSquares[random.nextInt(spawnSquares.size)]
            } else {
                spawnSquares.maxByOrNull { sq ->
                    val probe = ChessPiece(id = -1, type = PieceType.PAWN, color = aiColor)
                    val after = ChessEngine.placePiece(state.board, sq, probe)
                    var score = centerFileBonus(sq.col) * 10
                    if (ChessEngine.isSquareAttacked(after, sq, opponentColor)) {
                        val defended = ChessEngine.isSquareAttacked(after, sq, aiColor)
                        score -= if (defended) 12 else 55
                    }
                    score + random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 12)
                } ?: spawnSquares.first()
            }
            return AiReviveDecision(pieceId = -1, square = chosenSq)
        }

        // Mode A: Transform 1 own unfrozen Pawn into 1 captured piece
        val candidates = ownCaptured.mapNotNull { piece ->
            val squares = ChessEngine.getValidReviveSquares(state.board, piece, aiColor, excluded)
            if (squares.isEmpty()) null else piece to squares.toList()
        }
        if (candidates.isEmpty()) return null

        return when (difficulty) {
            AiDifficulty.EASY -> {
                val (piece, squares) = candidates[random.nextInt(candidates.size)]
                AiReviveDecision(piece.id, squares[random.nextInt(squares.size)])
            }
            AiDifficulty.MEDIUM, AiDifficulty.HARD -> {
                var bestDecision: AiReviveDecision? = null
                var bestScore = Int.MIN_VALUE

                for ((piece, squares) in candidates) {
                    val valueWeight = pieceScore(piece.type) * 110
                    for (sq in squares) {
                        val afterBoard = ChessEngine.placePiece(state.board, sq, piece)
                        var score = valueWeight + evaluateBoard(afterBoard, aiColor, kingSafetyMultiplier = 1) / 3
                        val attacked = ChessEngine.isSquareAttacked(afterBoard, sq, opponentColor)
                        if (attacked) {
                            val defended = ChessEngine.isSquareAttacked(afterBoard, sq, aiColor)
                            score -= pieceScore(piece.type) * (if (defended) 25 else 95)
                        }
                        score += random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 12)
                        if (score > bestScore) {
                            bestScore = score
                            bestDecision = AiReviveDecision(piece.id, sq)
                        }
                    }
                }
                bestDecision
            }
        }
    }

    /**
     * Chooses a Trade (single-piece equal-value swap / downgrade or multi-piece merge such as
     * 3 Pawns -> Knight/Bishop or Rook + Minor + Pawn -> Queen), or returns null to skip if
     * no trade improves the AI's position.
     */
    fun chooseTrade(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): AiTradeDecision? {
        val aiColor = state.activeColor
        val excluded = excludedIdsFor(state, aiColor)
        val sources = ChessEngine.getValidTradeSourceSquares(state.board, aiColor, excluded).toList()
        if (sources.isEmpty()) return null

        val candidateGroups = mutableListOf<List<Square>>()

        // 1. Single-piece swaps (prefer equal-value Knight <-> Bishop)
        for (sq in sources) {
            val p = state.board[sq.row][sq.col] ?: continue
            if (p.type == PieceType.KNIGHT || p.type == PieceType.BISHOP) {
                candidateGroups.add(listOf(sq))
            }
        }

        val pawns = sources.filter { state.board[it.row][it.col]?.type == PieceType.PAWN }
        val minors = sources.filter {
            val t = state.board[it.row][it.col]?.type
            t == PieceType.KNIGHT || t == PieceType.BISHOP
        }
        val rooks = sources.filter { state.board[it.row][it.col]?.type == PieceType.ROOK }

        // 2. Classic 3-Pawn merge into Knight or Bishop (choose the most central/advanced pawn as anchor)
        if (pawns.size >= 3) {
            val sortedPawns = pawns.sortedByDescending { sq ->
                val adv = if (aiColor == PieceColor.WHITE) (6 - sq.row) else (sq.row - 1)
                adv * 4 + centerDistanceBonus(sq) * 3
            }
            candidateGroups.add(sortedPawns.take(3))
        }

        // 3. Minor + 2 Pawns (5 pts) -> Rook
        if (minors.isNotEmpty() && pawns.size >= 2) {
            val backPawns = pawns.sortedBy { sq ->
                if (aiColor == PieceColor.WHITE) (6 - sq.row) else (sq.row - 1)
            }.take(2)
            candidateGroups.add(listOf(minors.first()) + backPawns)
        }

        // 4. Rook + Minor + Pawn (9 pts) -> Queen
        if (rooks.isNotEmpty() && minors.isNotEmpty() && pawns.isNotEmpty()) {
            candidateGroups.add(listOf(rooks.first(), minors.first(), pawns.first()))
        }

        // 5. Two Rooks (10 pts) -> Queen
        if (rooks.size >= 2) {
            candidateGroups.add(rooks.take(2))
        }

        // 6. Two Minors (6 pts) -> Rook
        if (minors.size >= 2) {
            candidateGroups.add(minors.take(2))
        }

        val validDecisions = mutableListOf<AiTradeDecision>()
        for (group in candidateGroups) {
            val targets = ChessEngine.getValidTradeTargets(state.board, aiColor, group)
            for (target in targets) {
                // Skip wasteful downgrades for Medium/Hard
                val totalPts = group.sumOf { state.board[it.row][it.col]?.type?.materialValue ?: 0 }
                if (difficulty != AiDifficulty.EASY && target.materialValue < totalPts - 1) continue
                validDecisions.add(AiTradeDecision(group, target))
            }
        }

        if (validDecisions.isEmpty()) return null

        if (difficulty == AiDifficulty.EASY) {
            if (random.nextFloat() < 0.4f) return null
            return validDecisions[random.nextInt(validDecisions.size)]
        }

        val baseScore = evaluateBoard(state.board, aiColor, kingSafetyMultiplier = 1)
        var bestDecision: AiTradeDecision? = null
        var bestGain = 10 // Require at least a slight positional/tactical improvement to trade

        for (decision in validDecisions) {
            val testPiece = ChessPiece(id = -1, type = decision.targetType, color = aiColor)
            val (afterBoard, _) = ChessEngine.applyTrade(
                board = state.board,
                selectedSquares = decision.selectedSquares,
                newPiece = testPiece,
                castlingRights = state.castlingRights
            )
            var score = evaluateBoard(afterBoard, aiColor, kingSafetyMultiplier = 1)
            // Bonus for consolidating pawns into a mobile minor/major piece (huge advantage with 1-move-per-piece)
            if (decision.selectedSquares.size >= 2) {
                score += when (decision.targetType) {
                    PieceType.QUEEN -> 95
                    PieceType.ROOK -> 50
                    PieceType.BISHOP, PieceType.KNIGHT -> 45
                    else -> 0
                }
            }
            // Penalize spawning directly onto a square attacked by opponent if undefended
            val anchor = decision.selectedSquares.first()
            if (ChessEngine.isSquareAttacked(afterBoard, anchor, aiColor.opponent())) {
                val defended = ChessEngine.isSquareAttacked(afterBoard, anchor, aiColor)
                score -= if (defended) pieceScore(decision.targetType) * 15 else pieceScore(decision.targetType) * 80
            }

            val gain = score - baseScore + random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 12)
            if (gain > bestGain) {
                bestGain = gain
                bestDecision = decision
            }
        }

        return bestDecision
    }

    /**
     * Chooses an opponent piece move for the Hijack card.
     * Medium & Hard AI try to put an enemy high-value piece onto an attacked square (hanging it!)
     * or push enemy pieces into passive positions while avoiding helping the opponent.
     */
    fun chooseHijackMove(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): ChessMove? {
        val aiColor = state.activeColor
        val opponentColor = aiColor.opponent()
        val enemyExcluded = if (state.frozenPieceId != null && state.frozenOwner == opponentColor) {
            setOf(state.frozenPieceId)
        } else {
            emptySet()
        }
        val hijackMoves = ChessEngine.getAllHijackMoves(
            board = state.board,
            activeColor = aiColor,
            castlingRights = state.castlingRights,
            enPassantTarget = state.enPassantTarget,
            enPassantOwner = state.enPassantOwner,
            excludedPieceIds = enemyExcluded,
            protectedPieceIds = protectedIdsFor(state, aiColor)
        )
        if (hijackMoves.isEmpty()) return null

        if (difficulty == AiDifficulty.EASY) {
            // Avoid giving opponent free captures of AI's Queen on Easy, otherwise casual move
            val nonQueenCaptures = hijackMoves.filter { m ->
                state.board[m.to.row][m.to.col]?.type != PieceType.QUEEN
            }
            val pool = nonQueenCaptures.ifEmpty { hijackMoves }
            return pool[random.nextInt(pool.size)]
        }

        return hijackMoves.maxByOrNull { move ->
            val res = ChessEngine.applyMove(state.board, move, state.castlingRights)
            var score = evaluateBoard(res.board, aiColor, kingSafetyMultiplier = 1)
            val movedEnemy = res.board[move.to.row][move.to.col]

            // Huge tactical bonus if AI moves opponent's valuable piece onto a square attacked by AI!
            if (movedEnemy != null && ChessEngine.isSquareAttacked(res.board, move.to, aiColor)) {
                val defendedByEnemy = ChessEngine.isSquareAttacked(res.board, move.to, opponentColor)
                val hangBonus = if (defendedByEnemy) {
                    pieceScore(movedEnemy.type) * 35
                } else {
                    pieceScore(movedEnemy.type) * 95
                }
                score += hangBonus
            }

            // Never willingly promote an enemy pawn to a Queen!
            if (move.promotion != null) {
                score -= pieceScore(move.promotion) * 60
            }

            score + random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 14)
        } ?: hijackMoves.first()
    }

    /**
     * Chooses which own Pawn to upgrade to Knight or Bishop via the Promote card.
     */
    fun choosePawnPromote(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): AiPawnPromoteDecision? {
        val aiColor = state.activeColor
        val opponentColor = aiColor.opponent()
        val validSquares = state.validPromotePawnSquares.toList()
        if (validSquares.isEmpty()) return null

        val allChoices = validSquares.flatMap { sq ->
            ChessEngine.getValidPawnPromoteTargets(state.board, aiColor, sq).map { target ->
                AiPawnPromoteDecision(sq, target)
            }
        }
        if (allChoices.isEmpty()) return null

        if (difficulty == AiDifficulty.EASY) {
            return allChoices[random.nextInt(allChoices.size)]
        }

        return allChoices.maxByOrNull { decision ->
            val pawn = state.board[decision.pawnSquare.row][decision.pawnSquare.col] ?: return@maxByOrNull -1000
            val upgradedBoard = ChessEngine.placePiece(
                state.board,
                decision.pawnSquare,
                pawn.copy(type = decision.targetType)
            )
            var score = evaluateBoard(upgradedBoard, aiColor, kingSafetyMultiplier = 1)
            if (ChessEngine.isSquareAttacked(upgradedBoard, decision.pawnSquare, opponentColor)) {
                val defended = ChessEngine.isSquareAttacked(upgradedBoard, decision.pawnSquare, aiColor)
                score -= if (defended) 45 else 180
            }
            score + random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 12)
        } ?: allChoices.first()
    }

    /**
     * Chooses whether to sacrifice a piece into equal-value pieces (or return null to skip).
     */
    fun chooseSacrifice(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): AiSacrificeDecision? {
        val aiColor = state.activeColor
        val validSquares = state.validSacrificeSquares.toList()
        if (validSquares.isEmpty()) return null

        val allOptions = validSquares.flatMap { sq ->
            ChessEngine.getValidSacrificeRecipes(state.board, aiColor, sq).map { recipe ->
                Triple(sq, recipe, ChessEngine.computeSacrificePlacement(state.board, aiColor, sq, recipe))
            }
        }.filter { it.third != null }
        if (allOptions.isEmpty()) return null

        if (difficulty == AiDifficulty.EASY) {
            if (random.nextFloat() < 0.45f) return null
            val pick = allOptions[random.nextInt(allOptions.size)]
            return AiSacrificeDecision(pick.first, pick.second.id)
        }

        val baseScore = evaluateBoard(state.board, aiColor, kingSafetyMultiplier = 1)
        var best: AiSacrificeDecision? = null
        var bestGain = 15

        for ((sq, recipe, placements) in allOptions) {
            val (afterBoard, _) = ChessEngine.applySacrifice(
                board = state.board,
                sacrificeSquare = sq,
                placements = placements!!,
                color = aiColor,
                startingPieceId = -500,
                castlingRights = state.castlingRights
            )
            var score = evaluateBoard(afterBoard, aiColor, kingSafetyMultiplier = 1)
            // Bonus if the sacrificed piece was currently hanging/attacked!
            if (ChessEngine.isSquareAttacked(state.board, sq, aiColor.opponent())) {
                score += pieceScore(state.board[sq.row][sq.col]?.type) * 30
            }
            val gain = score - baseScore + random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 12)
            if (gain > bestGain) {
                bestGain = gain
                best = AiSacrificeDecision(sq, recipe.id)
            }
        }
        return best
    }

    /**
     * Chooses which own piece to eliminate when drawing the Suicide card.
     */
    fun chooseSuicideTarget(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): Square? {
        val aiColor = state.activeColor
        val targets = state.validSuicideSquares.toList()
        if (targets.isEmpty()) return null

        if (difficulty == AiDifficulty.EASY) {
            return targets[random.nextInt(targets.size)]
        }

        // Medium & Hard sacrifice the least valuable piece while maximizing remaining board strength
        return targets.maxByOrNull { sq ->
            val piece = state.board[sq.row][sq.col]
            val afterBoard = ChessEngine.removePiece(state.board, sq)
            val boardVal = evaluateBoard(afterBoard, aiColor, kingSafetyMultiplier = 1)
            val lossPenalty = pieceScore(piece?.type) * 140
            boardVal - lossPenalty + random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 16)
        } ?: targets.first()
    }

    /**
     * Chooses which own non-King piece to protect with the Shield card.
     */
    fun chooseShieldTarget(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): Square? {
        val targets = state.validShieldSquares.toList()
        if (targets.isEmpty()) return null

        val aiColor = state.activeColor
        val enemyColor = aiColor.opponent()

        return when (difficulty) {
            AiDifficulty.EASY -> targets[random.nextInt(targets.size)]
            AiDifficulty.MEDIUM -> {
                targets.maxByOrNull { sq ->
                    val p = state.board[sq.row][sq.col]
                    val attacked = ChessEngine.isSquareAttacked(state.board, sq, enemyColor)
                    pieceScore(p?.type) * (if (attacked) 20 else 10) + random.nextInt(6)
                }
            }
            AiDifficulty.HARD -> {
                targets.maxByOrNull { sq ->
                    val p = state.board[sq.row][sq.col] ?: return@maxByOrNull -1000
                    val attacked = ChessEngine.isSquareAttacked(state.board, sq, enemyColor)
                    val defended = ChessEngine.isSquareAttacked(state.board, sq, aiColor)
                    var score = pieceScore(p.type) * 14 + centerDistanceBonus(sq) * 3
                    if (attacked) {
                        score += pieceScore(p.type) * (if (defended) 24 else 45)
                    }
                    score
                }
            }
        }
    }

    /**
     * Chooses a safe Swap Positions pair (or returns null to skip if no swap helps).
     */
    fun chooseSwap(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): AiSwapDecision? {
        val aiColor = state.activeColor
        val excluded = excludedIdsFor(state, aiColor)
        val sources = ChessEngine.getValidSwapSourceSquares(state.board, aiColor, excluded).toList()
        if (sources.isEmpty()) return null

        val pairs = mutableListOf<AiSwapDecision>()
        val seen = mutableSetOf<Pair<Square, Square>>()
        for (first in sources) {
            for (second in ChessEngine.getValidSwapPartners(state.board, aiColor, first, excluded)) {
                val key = if (first.row < second.row || (first.row == second.row && first.col < second.col)) {
                    first to second
                } else {
                    second to first
                }
                if (seen.add(key)) {
                    pairs.add(AiSwapDecision(first, second))
                }
            }
        }
        if (pairs.isEmpty()) return null

        if (difficulty == AiDifficulty.EASY) {
            if (random.nextFloat() < 0.35f) return null
            return pairs[random.nextInt(pairs.size)]
        }

        val baseScore = evaluateBoard(state.board, aiColor, kingSafetyMultiplier = 2) -
            undefendedMaterialPenalty(state.board, aiColor)
        var best: AiSwapDecision? = null
        var bestGain = 14

        for (pair in pairs) {
            val (afterBoard, _) = ChessEngine.applySwap(state.board, pair.first, pair.second, state.castlingRights)
            val score = evaluateBoard(afterBoard, aiColor, kingSafetyMultiplier = 2) -
                undefendedMaterialPenalty(afterBoard, aiColor)
            val gain = score - baseScore + random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 14)
            if (gain > bestGain) {
                bestGain = gain
                best = pair
            }
        }
        return best
    }

    /**
     * Chooses which Pawn/Knight/Bishop to duplicate via the Clone card and where to place it.
     */
    fun chooseClone(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): AiCloneDecision? {
        val aiColor = state.activeColor
        val enemyColor = aiColor.opponent()
        val excluded = excludedIdsFor(state, aiColor)
        val sources = ChessEngine.getValidCloneSourceSquares(state.board, aiColor, excluded).toList()
        if (sources.isEmpty()) return null

        val choices = sources.flatMap { src ->
            ChessEngine.getValidCloneTargetSquares(state.board, aiColor, src).map { dst ->
                AiCloneDecision(src, dst)
            }
        }
        if (choices.isEmpty()) return null

        if (difficulty == AiDifficulty.EASY) {
            return choices[random.nextInt(choices.size)]
        }

        return choices.maxByOrNull { decision ->
            val srcPiece = state.board[decision.source.row][decision.source.col] ?: return@maxByOrNull -1000
            val probe = ChessPiece(id = -777, type = srcPiece.type, color = aiColor)
            val afterBoard = ChessEngine.placePiece(state.board, decision.target, probe)
            var score = pieceScore(srcPiece.type) * 120 +
                evaluateBoard(afterBoard, aiColor, kingSafetyMultiplier = 1) / 3
            if (ChessEngine.isSquareAttacked(afterBoard, decision.target, enemyColor)) {
                val defended = ChessEngine.isSquareAttacked(afterBoard, decision.target, aiColor)
                score -= pieceScore(srcPiece.type) * (if (defended) 22 else 95)
            }
            score + random.nextInt(if (difficulty == AiDifficulty.HARD) 4 else 14)
        } ?: choices.first()
    }

    /**
     * Chooses which enabled special effect to activate when drawing the Wild Card.
     */
    fun chooseWildEffect(
        state: GameUiState,
        difficulty: AiDifficulty,
        random: Random = Random.Default
    ): SpecialCardKey? {
        val available = state.availableWildEffects
        if (available.isEmpty()) return null

        if (difficulty == AiDifficulty.EASY) {
            return available[random.nextInt(available.size)]
        }

        val aiColor = state.activeColor
        val ownCaptured = state.capturedPieces[aiColor].orEmpty()
        val hasHighCaptured = ownCaptured.any { it.type == PieceType.QUEEN || it.type == PieceType.ROOK }

        val priority = if (difficulty == AiDifficulty.HARD) {
            buildList {
                if (hasHighCaptured) add(SpecialCardKey.REVIVE)
                add(SpecialCardKey.CLONE)
                add(SpecialCardKey.PROMOTE)
                add(SpecialCardKey.FREEZE)
                add(SpecialCardKey.SHIELD)
                add(SpecialCardKey.HIJACK)
                add(SpecialCardKey.MERGE)
                add(SpecialCardKey.REVIVE)
                add(SpecialCardKey.SWAP)
                add(SpecialCardKey.SACRIFICE)
            }
        } else {
            listOf(
                SpecialCardKey.CLONE,
                SpecialCardKey.FREEZE,
                SpecialCardKey.SHIELD,
                SpecialCardKey.PROMOTE,
                SpecialCardKey.REVIVE,
                SpecialCardKey.HIJACK,
                SpecialCardKey.MERGE,
                SpecialCardKey.SWAP,
                SpecialCardKey.SACRIFICE
            )
        }

        return priority.firstOrNull { it in available } ?: available.first()
    }

    private fun chooseEasyMove(
        state: GameUiState,
        legalMoves: List<ChessMove>,
        random: Random
    ): ChessMove {
        // Still take obvious immediate checkmate 60% of the time, otherwise play casual/blunder-prone moves
        if (random.nextFloat() < 0.58f) {
            return legalMoves[random.nextInt(legalMoves.size)]
        }

        // Mild preference for captures/development with large noise so basic blunders happen naturally
        return legalMoves.maxByOrNull { move ->
            val victim = state.board[move.to.row][move.to.col]
            val captureBonus = if (victim != null) pieceScore(victim.type) * 8 else 0
            val promoBonus = if (move.promotion == PieceType.QUEEN) 30 else 0
            captureBonus + promoBonus + random.nextInt(40)
        } ?: legalMoves.first()
    }

    private fun chooseMediumMove(
        state: GameUiState,
        legalMoves: List<ChessMove>,
        random: Random
    ): ChessMove {
        val aiColor = state.activeColor
        val opponentColor = aiColor.opponent()

        return legalMoves.maxByOrNull { move ->
            val res = ChessEngine.applyMove(state.board, move, state.castlingRights)
            val oppInCheck = ChessEngine.isKingInCheck(res.board, opponentColor)
            val oppHasMove = ChessEngine.hasAnyLegalMove(
                board = res.board,
                color = opponentColor,
                castlingRights = res.castlingRights,
                enPassantTarget = res.enPassantTarget,
                enPassantOwner = res.enPassantOwner
            )
            if (!oppHasMove) {
                return@maxByOrNull if (oppInCheck) 100_000 else -150
            }

            var score = evaluateBoard(res.board, aiColor, kingSafetyMultiplier = 1)
            val movedPiece = res.board[move.to.row][move.to.col]

            // Penalize hanging the moved piece on an attacked square
            if (movedPiece != null && ChessEngine.isSquareAttacked(res.board, move.to, opponentColor)) {
                val defended = ChessEngine.isSquareAttacked(res.board, move.to, aiColor)
                val penalty = if (defended) pieceScore(movedPiece.type) * 30 else pieceScore(movedPiece.type) * 85
                score -= penalty
            }

            // Castling bonus
            if (move.isCastlingKingside || move.isCastlingQueenside) {
                score += 45
            }

            // UNO-aware check forfeiture: discourage wasteful early check when moves remain
            if (oppInCheck) {
                if (state.movesRemaining > 1) {
                    score -= (state.movesRemaining - 1) * 55
                } else {
                    score += 18
                }
            }

            score + random.nextInt(14)
        } ?: legalMoves.first()
    }

    private fun chooseHardMove(
        state: GameUiState,
        legalMoves: List<ChessMove>,
        excluded: Set<Int>,
        protectedIds: Set<Int>,
        random: Random
    ): ChessMove {
        val aiColor = state.activeColor
        val opponentColor = aiColor.opponent()

        // Pre-rank moves to keep 2-ply search crisp and fast on mobile
        val orderedMoves = legalMoves.sortedByDescending { move ->
            quickOrderScore(state.board, move, aiColor)
        }.take(24)

        var bestMove = orderedMoves.first()
        var bestScore = Int.MIN_VALUE

        for (move in orderedMoves) {
            val res = ChessEngine.applyMove(state.board, move, state.castlingRights)
            val oppInCheck = ChessEngine.isKingInCheck(res.board, opponentColor)
            val oppHasMove = ChessEngine.hasAnyLegalMove(
                board = res.board,
                color = opponentColor,
                castlingRights = res.castlingRights,
                enPassantTarget = res.enPassantTarget,
                enPassantOwner = res.enPassantOwner
            )

            // Immediate checkmate is always #1
            if (!oppHasMove) {
                if (oppInCheck) return move
                val stalemateScore = if (materialBalance(state.board, aiColor) < -3) 500 else -800
                if (stalemateScore > bestScore) {
                    bestScore = stalemateScore
                    bestMove = move
                }
                continue
            }

            val movedIdsAfter = excluded + buildMovedIds(state.board, move, res.movedPiece.id)
            val movesLeftAfter = state.movesRemaining - 1

            val score = if (!oppInCheck && movesLeftAfter > 0) {
                // Multi-move UNO combo lookahead: find best 2nd distinct-piece follow-up move!
                val followUpMoves = ChessEngine.getAllLegalMoves(
                    board = res.board,
                    color = aiColor,
                    castlingRights = res.castlingRights,
                    enPassantTarget = null,
                    enPassantOwner = null,
                    excludedPieceIds = movedIdsAfter,
                    protectedPieceIds = protectedIds
                )
                if (followUpMoves.isEmpty()) {
                    evaluateAfterTurnPasses(res.board, res.castlingRights, aiColor)
                } else {
                    val topFollowUps = followUpMoves.sortedByDescending {
                        quickOrderScore(res.board, it, aiColor)
                    }.take(10)

                    var bestCombo = Int.MIN_VALUE
                    for (second in topFollowUps) {
                        val res2 = ChessEngine.applyMove(res.board, second, res.castlingRights)
                        val oppCheck2 = ChessEngine.isKingInCheck(res2.board, opponentColor)
                        val oppHas2 = ChessEngine.hasAnyLegalMove(
                            board = res2.board,
                            color = opponentColor,
                            castlingRights = res2.castlingRights,
                            enPassantTarget = res2.enPassantTarget,
                            enPassantOwner = res2.enPassantOwner
                        )
                        val comboVal = if (!oppHas2) {
                            if (oppCheck2) 95_000 else -600
                        } else {
                            var v = evaluateAfterTurnPasses(res2.board, res2.castlingRights, aiColor)
                            if (oppCheck2 && movesLeftAfter > 1) {
                                v -= (movesLeftAfter - 1) * 45
                            }
                            v
                        }
                        if (comboVal > bestCombo) bestCombo = comboVal
                    }
                    bestCombo
                }
            } else {
                // Turn passes to opponent (either last move on card or check forfeited remaining moves)
                var endTurnVal = evaluateAfterTurnPasses(res.board, res.castlingRights, aiColor)
                if (oppInCheck && movesLeftAfter > 0) {
                    // Penalize forfeiting extra moves unless check delivers a big material win
                    endTurnVal -= movesLeftAfter * 65
                }
                endTurnVal
            }

            val tieBreak = random.nextInt(4)
            if (score + tieBreak > bestScore) {
                bestScore = score + tieBreak
                bestMove = move
            }
        }

        return bestMove
    }

    /**
     * Evaluates the position after the AI finishes its turn by anticipating the opponent's
     * strongest reply—plus extra weighting for hanging pieces & King safety because the opponent
     * may draw a 2, 3, or 4 multi-move UNO card next!
     */
    private fun evaluateAfterTurnPasses(
        board: List<List<ChessPiece?>>,
        castlingRights: CastlingRights,
        aiColor: PieceColor
    ): Int {
        val opponentColor = aiColor.opponent()
        val oppMoves = ChessEngine.getAllLegalMoves(
            board = board,
            color = opponentColor,
            castlingRights = castlingRights,
            enPassantTarget = null,
            enPassantOwner = null
        )
        if (oppMoves.isEmpty()) {
            return if (ChessEngine.isKingInCheck(board, opponentColor)) 90_000 else -200
        }

        // Check opponent's top tactical threats (captures, checks, promotions)
        val sharpReplies = oppMoves.sortedByDescending {
            quickOrderScore(board, it, opponentColor)
        }.take(10)

        var worstForAi = Int.MAX_VALUE
        for (reply in sharpReplies) {
            val afterReply = ChessEngine.applyMove(board, reply, castlingRights)
            val aiInCheck = ChessEngine.isKingInCheck(afterReply.board, aiColor)
            val aiHasMove = ChessEngine.hasAnyLegalMove(
                board = afterReply.board,
                color = aiColor,
                castlingRights = afterReply.castlingRights,
                enPassantTarget = afterReply.enPassantTarget,
                enPassantOwner = afterReply.enPassantOwner
            )
            val eval = if (!aiHasMove) {
                if (aiInCheck) -90_000 else 0
            } else {
                // Weight King safety & undefended-piece defense heavily to anticipate opponent multi-move cards
                evaluateBoard(afterReply.board, aiColor, kingSafetyMultiplier = 2) -
                    undefendedMaterialPenalty(afterReply.board, aiColor)
            }
            if (eval < worstForAi) {
                worstForAi = eval
            }
        }
        return worstForAi
    }

    private fun evaluateBoard(
        board: List<List<ChessPiece?>>,
        perspective: PieceColor,
        kingSafetyMultiplier: Int
    ): Int {
        var score = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c] ?: continue
                val sign = if (p.color == perspective) 1 else -1
                val material = pieceScore(p.type) * 100
                val positional = positionalBonus(p.type, p.color, r, c, kingSafetyMultiplier)
                score += sign * (material + positional)
            }
        }
        return score
    }

    /**
     * In UNO Chess, leaving multiple pieces undefended is fatal when the opponent draws a 3 or 4 card.
     */
    private fun undefendedMaterialPenalty(
        board: List<List<ChessPiece?>>,
        aiColor: PieceColor
    ): Int {
        val oppColor = aiColor.opponent()
        var penalty = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c] ?: continue
                if (p.color == aiColor && p.type != PieceType.KING) {
                    val sq = Square(r, c)
                    if (ChessEngine.isSquareAttacked(board, sq, oppColor)) {
                        val defended = ChessEngine.isSquareAttacked(board, sq, aiColor)
                        penalty += if (defended) pieceScore(p.type) * 10 else pieceScore(p.type) * 35
                    }
                }
            }
        }
        return penalty
    }

    private fun materialBalance(board: List<List<ChessPiece?>>, color: PieceColor): Int {
        var sum = 0
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c] ?: continue
                if (p.type != PieceType.KING) {
                    sum += if (p.color == color) p.type.materialValue else -p.type.materialValue
                }
            }
        }
        return sum
    }

    private fun quickOrderScore(
        board: List<List<ChessPiece?>>,
        move: ChessMove,
        moverColor: PieceColor
    ): Int {
        val mover = board[move.from.row][move.from.col]
        val victim = board[move.to.row][move.to.col]
        var s = 0
        if (victim != null) {
            // MVV-LVA: prefer capturing high-value pieces with low-value pieces
            s += pieceScore(victim.type) * 20 - pieceScore(mover?.type) * 2
        }
        if (move.promotion != null) {
            s += pieceScore(move.promotion) * 18
        }
        if (move.isCastlingKingside || move.isCastlingQueenside) {
            s += 24
        }
        s += centerDistanceBonus(move.to) * 2
        if (mover?.type == PieceType.PAWN) {
            val adv = if (moverColor == PieceColor.WHITE) (6 - move.to.row) else (move.to.row - 1)
            s += adv * 2
        }
        return s
    }

    private fun positionalBonus(
        type: PieceType,
        color: PieceColor,
        row: Int,
        col: Int,
        kingSafetyMultiplier: Int
    ): Int {
        val r = if (color == PieceColor.WHITE) row else 7 - row
        val center = centerDistanceBonus(Square(row, col))
        return when (type) {
            PieceType.PAWN -> (6 - r) * 7 + center * 4
            PieceType.KNIGHT -> center * 12 - if (col == 0 || col == 7) 14 else 0
            PieceType.BISHOP -> center * 9 + if (r < 7) 10 else 0
            PieceType.ROOK -> if (r == 1) 24 else center * 3
            PieceType.QUEEN -> center * 6
            PieceType.KING -> {
                // Keep King tucked safely on castled wing (g1/c1) in UNO Chess where multi-moves punish central Kings
                val castledWingBonus = if (r == 7 && (col <= 2 || col >= 6)) 24 else 0
                val exposedPenalty = if (r < 6) -30 else 0
                (castledWingBonus + exposedPenalty) * kingSafetyMultiplier
            }
        }
    }

    private fun buildMovedIds(
        board: List<List<ChessPiece?>>,
        move: ChessMove,
        movedPieceId: Int
    ): Set<Int> {
        val ids = mutableSetOf(movedPieceId)
        if (move.isCastlingKingside) {
            board[move.from.row][7]?.id?.let(ids::add)
        } else if (move.isCastlingQueenside) {
            board[move.from.row][0]?.id?.let(ids::add)
        }
        return ids
    }

    private fun excludedIdsFor(state: GameUiState, color: PieceColor): Set<Int> {
        val set = state.movedPieceIdsThisTurn.toMutableSet()
        if (state.frozenPieceId != null && state.frozenOwner == color) {
            set.add(state.frozenPieceId)
        }
        return set
    }

    private fun protectedIdsFor(state: GameUiState, ownerColor: PieceColor): Set<Int> {
        return if (state.shieldedPieceId != null && state.shieldedOwner == ownerColor) {
            setOf(state.shieldedPieceId)
        } else {
            emptySet()
        }
    }

    private fun pieceScore(type: PieceType?): Int = when (type) {
        PieceType.PAWN -> 1
        PieceType.KNIGHT -> 3
        PieceType.BISHOP -> 3
        PieceType.ROOK -> 5
        PieceType.QUEEN -> 9
        PieceType.KING -> 0
        null -> 0
    }

    private fun centerDistanceBonus(sq: Square): Int {
        val dr = abs(sq.row - 3.5)
        val dc = abs(sq.col - 3.5)
        return (7 - (dr + dc).toInt()).coerceAtLeast(0)
    }

    private fun centerFileBonus(col: Int): Int = when (col) {
        3, 4 -> 4
        2, 5 -> 3
        1, 6 -> 2
        else -> 1
    }
}
