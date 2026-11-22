package com.example.engine

import com.example.model.CastlingRights
import com.example.model.ChessMove
import com.example.model.ChessPiece
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.Square

data class MoveApplicationResult(
    val board: List<List<ChessPiece?>>,
    val movedPiece: ChessPiece,
    val capturedPiece: ChessPiece?,
    val castlingRights: CastlingRights,
    val enPassantTarget: Square?,
    val enPassantOwner: PieceColor?,
    val notation: String
)

object ChessEngine {

    fun createInitialBoard(): List<List<ChessPiece?>> {
        var nextId = 1
        fun piece(type: PieceType, color: PieceColor) = ChessPiece(nextId++, type, color)

        val backRankOrder = listOf(
            PieceType.ROOK,
            PieceType.KNIGHT,
            PieceType.BISHOP,
            PieceType.QUEEN,
            PieceType.KING,
            PieceType.BISHOP,
            PieceType.KNIGHT,
            PieceType.ROOK
        )

        return List(8) { row ->
            List(8) { col ->
                when (row) {
                    0 -> piece(backRankOrder[col], PieceColor.BLACK)
                    1 -> piece(PieceType.PAWN, PieceColor.BLACK)
                    6 -> piece(PieceType.PAWN, PieceColor.WHITE)
                    7 -> piece(backRankOrder[col], PieceColor.WHITE)
                    else -> null
                }
            }
        }
    }

    fun findKing(board: List<List<ChessPiece?>>, color: PieceColor): Square? {
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == color && p.type == PieceType.KING) {
                    return Square(r, c)
                }
            }
        }
        return null
    }

    fun isKingInCheck(board: List<List<ChessPiece?>>, color: PieceColor): Boolean {
        val kingSq = findKing(board, color) ?: return false
        return isSquareAttacked(board, kingSq, color.opponent())
    }

    fun isSquareAttacked(
        board: List<List<ChessPiece?>>,
        target: Square,
        byColor: PieceColor
    ): Boolean {
        // 1. Pawn attacks: a pawn of byColor attacks target from one rank behind target
        val pawnSourceRow = if (byColor == PieceColor.WHITE) target.row + 1 else target.row - 1
        if (pawnSourceRow in 0..7) {
            for (dc in listOf(-1, 1)) {
                val c = target.col + dc
                if (c in 0..7) {
                    val p = board[pawnSourceRow][c]
                    if (p != null && p.color == byColor && p.type == PieceType.PAWN) {
                        return true
                    }
                }
            }
        }

        // 2. Knight attacks
        val knightOffsets = arrayOf(
            -2 to -1, -2 to 1, -1 to -2, -1 to 2,
            1 to -2, 1 to 2, 2 to -1, 2 to 1
        )
        for ((dr, dc) in knightOffsets) {
            val r = target.row + dr
            val c = target.col + dc
            if (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == byColor && p.type == PieceType.KNIGHT) {
                    return true
                }
            }
        }

        // 3. King attacks
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val r = target.row + dr
                val c = target.col + dc
                if (r in 0..7 && c in 0..7) {
                    val p = board[r][c]
                    if (p != null && p.color == byColor && p.type == PieceType.KING) {
                        return true
                    }
                }
            }
        }

        // 4. Orthogonal sliding (Rook / Queen)
        val orthogonalDirs = arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1)
        for ((dr, dc) in orthogonalDirs) {
            var r = target.row + dr
            var c = target.col + dc
            while (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.ROOK || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                r += dr
                c += dc
            }
        }

        // 5. Diagonal sliding (Bishop / Queen)
        val diagonalDirs = arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1)
        for ((dr, dc) in diagonalDirs) {
            var r = target.row + dr
            var c = target.col + dc
            while (r in 0..7 && c in 0..7) {
                val p = board[r][c]
                if (p != null) {
                    if (p.color == byColor && (p.type == PieceType.BISHOP || p.type == PieceType.QUEEN)) {
                        return true
                    }
                    break
                }
                r += dr
                c += dc
            }
        }

        return false
    }

    fun getLegalMovesForSquare(
        board: List<List<ChessPiece?>>,
        from: Square,
        castlingRights: CastlingRights,
        enPassantTarget: Square?,
        enPassantOwner: PieceColor?,
        excludedPieceIds: Set<Int> = emptySet(),
        protectedPieceIds: Set<Int> = emptySet()
    ): List<ChessMove> {
        val piece = board.getOrNull(from.row)?.getOrNull(from.col) ?: return emptyList()
        if (piece.id in excludedPieceIds) return emptyList()
        val pseudoMoves = generatePseudoLegalMoves(
            board = board,
            from = from,
            piece = piece,
            castlingRights = castlingRights,
            enPassantTarget = enPassantTarget,
            enPassantOwner = enPassantOwner,
            excludedPieceIds = excludedPieceIds
        )
        return pseudoMoves.filter { move ->
            // Block capturing any Shield-protected piece (regular capture or en passant)
            if (protectedPieceIds.isNotEmpty()) {
                val targetVictim = board[move.to.row][move.to.col]
                if (targetVictim != null && targetVictim.id in protectedPieceIds) {
                    return@filter false
                }
                if (move.isEnPassant) {
                    val epVictim = board[from.row][move.to.col]
                    if (epVictim != null && epVictim.id in protectedPieceIds) {
                        return@filter false
                    }
                }
            }
            val nextBoard = applyMoveToBoardOnly(board, move)
            !isKingInCheck(nextBoard, piece.color)
        }
    }

    fun getAllLegalMoves(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        castlingRights: CastlingRights,
        enPassantTarget: Square?,
        enPassantOwner: PieceColor?,
        excludedPieceIds: Set<Int> = emptySet(),
        protectedPieceIds: Set<Int> = emptySet()
    ): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (piece != null && piece.color == color && piece.id !in excludedPieceIds) {
                    moves.addAll(
                        getLegalMovesForSquare(
                            board = board,
                            from = Square(r, c),
                            castlingRights = castlingRights,
                            enPassantTarget = enPassantTarget,
                            enPassantOwner = enPassantOwner,
                            excludedPieceIds = excludedPieceIds,
                            protectedPieceIds = protectedPieceIds
                        )
                    )
                }
            }
        }
        return moves
    }

    fun hasAnyLegalMove(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        castlingRights: CastlingRights,
        enPassantTarget: Square?,
        enPassantOwner: PieceColor?,
        excludedPieceIds: Set<Int> = emptySet(),
        protectedPieceIds: Set<Int> = emptySet()
    ): Boolean {
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (piece != null && piece.color == color && piece.id !in excludedPieceIds) {
                    if (
                        getLegalMovesForSquare(
                            board = board,
                            from = Square(r, c),
                            castlingRights = castlingRights,
                            enPassantTarget = enPassantTarget,
                            enPassantOwner = enPassantOwner,
                            excludedPieceIds = excludedPieceIds,
                            protectedPieceIds = protectedPieceIds
                        ).isNotEmpty()
                    ) {
                        return true
                    }
                }
            }
        }
        return false
    }

    /**
     * Valid targets for a Freeze card: any enemy non-King piece currently on the board.
     */
    fun getValidFreezeSquares(
        board: List<List<ChessPiece?>>,
        enemyColor: PieceColor
    ): Set<Square> {
        val targets = mutableSetOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (piece != null && piece.color == enemyColor && piece.type != PieceType.KING) {
                    targets.add(Square(r, c))
                }
            }
        }
        return targets
    }

    /**
     * Own non-King, non-excluded pieces eligible to be selected for a Trade / Merge.
     */
    fun getValidTradeSourceSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        excludedPieceIds: Set<Int> = emptySet()
    ): Set<Square> {
        val sources = mutableSetOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val piece = board[r][c]
                if (
                    piece != null &&
                    piece.color == color &&
                    piece.type != PieceType.KING &&
                    piece.id !in excludedPieceIds
                ) {
                    sources.add(Square(r, c))
                }
            }
        }
        return sources
    }

    /**
     * Returns all non-King PieceTypes that the given selectedSquares can legally trade or merge into.
     * - Total material value of selected pieces must be >= target PieceType.materialValue.
     * - Single-piece trades cannot pick the exact same PieceType (must swap equal value or downgrade).
     * - Pawns cannot be created on rank 1 or rank 8 (rows 0 or 7).
     * - The resulting position must not leave own King in check or immediately check the opponent's King.
     */
    fun getValidTradeTargets(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        selectedSquares: List<Square>
    ): List<PieceType> {
        if (selectedSquares.isEmpty()) return emptyList()
        val selectedPieces = selectedSquares.map { sq ->
            board.getOrNull(sq.row)?.getOrNull(sq.col) ?: return emptyList()
        }
        if (selectedPieces.any { it.color != color || it.type == PieceType.KING }) return emptyList()

        val totalValue = selectedPieces.sumOf { it.type.materialValue }
        val anchor = selectedSquares.first()
        val opponent = color.opponent()
        val candidates = listOf(
            PieceType.QUEEN,
            PieceType.ROOK,
            PieceType.BISHOP,
            PieceType.KNIGHT,
            PieceType.PAWN
        )

        return candidates.filter { targetType ->
            if (targetType.materialValue > totalValue) return@filter false
            if (selectedSquares.size == 1 && selectedPieces.first().type == targetType) return@filter false
            if (targetType == PieceType.PAWN && (anchor.row == 0 || anchor.row == 7)) return@filter false

            val testPiece = ChessPiece(id = -1, type = targetType, color = color)
            val (nextBoard, _) = applyTrade(board, selectedSquares, testPiece, CastlingRights())
            !isKingInCheck(nextBoard, color) && !isKingInCheck(nextBoard, opponent)
        }
    }

    fun applyTrade(
        board: List<List<ChessPiece?>>,
        selectedSquares: List<Square>,
        newPiece: ChessPiece,
        castlingRights: CastlingRights
    ): Pair<List<List<ChessPiece?>>, CastlingRights> {
        val mutableBoard = board.map { it.toMutableList() }
        var wk = castlingRights.whiteKingside
        var wq = castlingRights.whiteQueenside
        var bk = castlingRights.blackKingside
        var bq = castlingRights.blackQueenside

        selectedSquares.forEach { sq ->
            mutableBoard[sq.row][sq.col] = null
            when (sq) {
                Square(7, 7) -> wk = false
                Square(7, 0) -> wq = false
                Square(0, 7) -> bk = false
                Square(0, 0) -> bq = false
            }
        }
        val anchor = selectedSquares.first()
        mutableBoard[anchor.row][anchor.col] = newPiece

        return mutableBoard.map { it.toList() } to CastlingRights(wk, wq, bk, bq)
    }

    /**
     * Checks whether the active player has at least one legal single-piece swap/downgrade
     * or multi-piece merge (2+ pieces) available.
     */
    fun hasAnyValidTrade(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        excludedPieceIds: Set<Int> = emptySet()
    ): Boolean {
        val sources = getValidTradeSourceSquares(board, color, excludedPieceIds).toList()
        if (sources.isEmpty()) return false

        // 1. Check any single-piece trade (any non-Pawn piece can usually swap or downgrade)
        for (sq in sources) {
            if (getValidTradeTargets(board, color, listOf(sq)).isNotEmpty()) return true
        }

        // 2. Check 2-piece merges
        for (i in sources.indices) {
            for (j in i + 1 until sources.size) {
                if (getValidTradeTargets(board, color, listOf(sources[i], sources[j])).isNotEmpty()) {
                    return true
                }
            }
        }

        // 3. Check 3-piece merges (e.g., 3 Pawns -> Knight / Bishop)
        if (sources.size >= 3) {
            val capped = sources.take(8)
            for (i in capped.indices) {
                for (j in i + 1 until capped.size) {
                    for (k in j + 1 until capped.size) {
                        if (getValidTradeTargets(board, color, listOf(capped[i], capped[j], capped[k])).isNotEmpty()) {
                            return true
                        }
                    }
                }
            }
        }

        return false
    }

    /**
     * Legal moves available for a Hijack card on a specific opponent square:
     * standard legal moves for opponentColor that do NOT put activeColor's own King in check.
     */
    fun getHijackMovesForSquare(
        board: List<List<ChessPiece?>>,
        from: Square,
        activeColor: PieceColor,
        castlingRights: CastlingRights,
        enPassantTarget: Square?,
        enPassantOwner: PieceColor?,
        excludedPieceIds: Set<Int> = emptySet(),
        protectedPieceIds: Set<Int> = emptySet()
    ): List<ChessMove> {
        val opponentColor = activeColor.opponent()
        val piece = board.getOrNull(from.row)?.getOrNull(from.col) ?: return emptyList()
        if (piece.color != opponentColor || piece.id in excludedPieceIds) return emptyList()

        return getLegalMovesForSquare(
            board = board,
            from = from,
            castlingRights = castlingRights,
            enPassantTarget = enPassantTarget,
            enPassantOwner = enPassantOwner,
            excludedPieceIds = excludedPieceIds,
            protectedPieceIds = protectedPieceIds
        ).filter { move ->
            val nextBoard = applyMoveToBoardOnly(board, move)
            !isKingInCheck(nextBoard, activeColor)
        }
    }

    fun getAllHijackMoves(
        board: List<List<ChessPiece?>>,
        activeColor: PieceColor,
        castlingRights: CastlingRights,
        enPassantTarget: Square?,
        enPassantOwner: PieceColor?,
        excludedPieceIds: Set<Int> = emptySet(),
        protectedPieceIds: Set<Int> = emptySet()
    ): List<ChessMove> {
        val opponentColor = activeColor.opponent()
        val moves = mutableListOf<ChessMove>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == opponentColor && p.id !in excludedPieceIds) {
                    moves.addAll(
                        getHijackMovesForSquare(
                            board = board,
                            from = Square(r, c),
                            activeColor = activeColor,
                            castlingRights = castlingRights,
                            enPassantTarget = enPassantTarget,
                            enPassantOwner = enPassantOwner,
                            excludedPieceIds = excludedPieceIds,
                            protectedPieceIds = protectedPieceIds
                        )
                    )
                }
            }
        }
        return moves
    }

    /**
     * Returns which minor pieces (Bishop / Knight) a specific own Pawn can safely promote into
     * via the Promote card (cannot leave own King in check or immediately check opponent's King).
     */
    fun getValidPawnPromoteTargets(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        pawnSquare: Square
    ): List<PieceType> {
        val piece = board.getOrNull(pawnSquare.row)?.getOrNull(pawnSquare.col) ?: return emptyList()
        if (piece.color != color || piece.type != PieceType.PAWN) return emptyList()

        val opponent = color.opponent()
        return listOf(PieceType.BISHOP, PieceType.KNIGHT).filter { targetType ->
            val upgraded = piece.copy(type = targetType)
            val nextBoard = placePiece(board, pawnSquare, upgraded)
            !isKingInCheck(nextBoard, color) && !isKingInCheck(nextBoard, opponent)
        }
    }

    fun getValidPawnPromoteSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        excludedPieceIds: Set<Int> = emptySet()
    ): Set<Square> {
        val squares = mutableSetOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == color && p.type == PieceType.PAWN && p.id !in excludedPieceIds) {
                    val sq = Square(r, c)
                    if (getValidPawnPromoteTargets(board, color, sq).isNotEmpty()) {
                        squares.add(sq)
                    }
                }
            }
        }
        return squares
    }

    /**
     * Equal-value split recipes for the Sacrifice card (P=1, N/B=3, R=5, Q=9).
     */
    fun getSacrificeRecipes(pieceType: PieceType): List<com.example.model.SacrificeRecipe> = when (pieceType) {
        PieceType.KNIGHT, PieceType.BISHOP -> listOf(
            com.example.model.SacrificeRecipe("3p", "3 Pawns", listOf(PieceType.PAWN, PieceType.PAWN, PieceType.PAWN))
        )
        PieceType.ROOK -> listOf(
            com.example.model.SacrificeRecipe("b2p", "Bishop + 2P", listOf(PieceType.BISHOP, PieceType.PAWN, PieceType.PAWN)),
            com.example.model.SacrificeRecipe("n2p", "Knight + 2P", listOf(PieceType.KNIGHT, PieceType.PAWN, PieceType.PAWN)),
            com.example.model.SacrificeRecipe("5p", "5 Pawns", List(5) { PieceType.PAWN })
        )
        PieceType.QUEEN -> listOf(
            com.example.model.SacrificeRecipe("rbp", "Rook + B + P", listOf(PieceType.ROOK, PieceType.BISHOP, PieceType.PAWN)),
            com.example.model.SacrificeRecipe("rnp", "Rook + N + P", listOf(PieceType.ROOK, PieceType.KNIGHT, PieceType.PAWN)),
            com.example.model.SacrificeRecipe("2bn", "2 Bishops + N", listOf(PieceType.BISHOP, PieceType.BISHOP, PieceType.KNIGHT)),
            com.example.model.SacrificeRecipe("2nb", "2 Knights + B", listOf(PieceType.KNIGHT, PieceType.KNIGHT, PieceType.BISHOP))
        )
        PieceType.PAWN, PieceType.KING -> emptyList()
    }

    /**
     * Computes deterministic, check-safe board placements for sacrificing [sacrificeSquare] into [recipe].
     * First eligible spawned piece lands on [sacrificeSquare]; remaining spawned pieces fill nearest
     * empty squares (preferring player's half of the board, never placing Pawns on rank 1 or 8).
     * Returns null if not enough valid squares exist or if the result leaves own King in check
     * or immediately checks the opponent's King.
     */
    fun computeSacrificePlacement(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        sacrificeSquare: Square,
        recipe: com.example.model.SacrificeRecipe
    ): List<Pair<Square, PieceType>>? {
        val piece = board[sacrificeSquare.row][sacrificeSquare.col] ?: return null
        if (piece.color != color || piece.type == PieceType.KING) return null
        if (recipe.pieces.sumOf { it.materialValue } != piece.type.materialValue) return null

        val working = board.map { it.toMutableList() }.toMutableList()
        working[sacrificeSquare.row][sacrificeSquare.col] = null

        val ownHalfRows = if (color == PieceColor.WHITE) 4..7 else 0..3
        val candidateSquares = mutableListOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                if (working[r][c] == null) {
                    candidateSquares.add(Square(r, c))
                }
            }
        }
        candidateSquares.sortWith(
            compareBy<Square>(
                { if (it == sacrificeSquare) 0 else 1 },
                { if (it.row in ownHalfRows) 0 else 1 },
                { kotlin.math.abs(it.row - sacrificeSquare.row) + kotlin.math.abs(it.col - sacrificeSquare.col) },
                { kotlin.math.abs(it.col - 3) }
            )
        )

        val placements = mutableListOf<Pair<Square, PieceType>>()
        val used = mutableSetOf<Square>()
        var tempId = -1000
        for (spawnType in recipe.pieces) {
            val chosen = candidateSquares.firstOrNull { sq ->
                sq !in used && !(spawnType == PieceType.PAWN && (sq.row == 0 || sq.row == 7))
            } ?: return null
            used.add(chosen)
            placements.add(chosen to spawnType)
            working[chosen.row][chosen.col] = ChessPiece(id = tempId--, type = spawnType, color = color)
        }

        val finalBoard = working.map { it.toList() }
        if (isKingInCheck(finalBoard, color) || isKingInCheck(finalBoard, color.opponent())) {
            return null
        }
        return placements
    }

    fun getValidSacrificeRecipes(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        sacrificeSquare: Square
    ): List<com.example.model.SacrificeRecipe> {
        val piece = board[sacrificeSquare.row][sacrificeSquare.col] ?: return emptyList()
        if (piece.color != color) return emptyList()
        return getSacrificeRecipes(piece.type).filter { recipe ->
            computeSacrificePlacement(board, color, sacrificeSquare, recipe) != null
        }
    }

    fun getValidSacrificeSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        excludedPieceIds: Set<Int> = emptySet()
    ): Set<Square> {
        val result = mutableSetOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == color && p.type != PieceType.KING && p.id !in excludedPieceIds) {
                    val sq = Square(r, c)
                    if (getValidSacrificeRecipes(board, color, sq).isNotEmpty()) {
                        result.add(sq)
                    }
                }
            }
        }
        return result
    }

    fun applySacrifice(
        board: List<List<ChessPiece?>>,
        sacrificeSquare: Square,
        placements: List<Pair<Square, PieceType>>,
        color: PieceColor,
        startingPieceId: Int,
        castlingRights: CastlingRights
    ): Pair<List<List<ChessPiece?>>, CastlingRights> {
        val mutableBoard = board.map { it.toMutableList() }.toMutableList()
        mutableBoard[sacrificeSquare.row][sacrificeSquare.col] = null

        var nextId = startingPieceId
        for ((sq, type) in placements) {
            mutableBoard[sq.row][sq.col] = ChessPiece(id = nextId++, type = type, color = color)
        }

        var wk = castlingRights.whiteKingside
        var wq = castlingRights.whiteQueenside
        var bk = castlingRights.blackKingside
        var bq = castlingRights.blackQueenside
        if (sacrificeSquare == Square(7, 7)) wk = false
        if (sacrificeSquare == Square(7, 0)) wq = false
        if (sacrificeSquare == Square(0, 7)) bk = false
        if (sacrificeSquare == Square(0, 0)) bq = false

        return mutableBoard.map { it.toList() } to CastlingRights(wk, wq, bk, bq)
    }

    /**
     * Returns all non-King squares belonging to [color] on the board.
     */
    fun getOwnNonKingSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor
    ): Set<Square> {
        val result = mutableSetOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == color && p.type != PieceType.KING) {
                    result.add(Square(r, c))
                }
            }
        }
        return result
    }

    /**
     * Valid Shield targets: any own non-King piece currently on the board.
     */
    fun getValidShieldSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor
    ): Set<Square> = getOwnNonKingSquares(board, color)

    /**
     * Valid partner squares that can safely swap positions with [firstSquare].
     * Cannot place a Pawn on rank 1 or 8, leave own King in check, or immediately check opponent's King.
     */
    fun getValidSwapPartners(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        firstSquare: Square,
        excludedPieceIds: Set<Int> = emptySet()
    ): Set<Square> {
        val firstPiece = board.getOrNull(firstSquare.row)?.getOrNull(firstSquare.col) ?: return emptySet()
        if (firstPiece.color != color || firstPiece.id in excludedPieceIds) return emptySet()

        val opponent = color.opponent()
        val partners = mutableSetOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val secondSquare = Square(r, c)
                if (secondSquare == firstSquare) continue
                val secondPiece = board[r][c] ?: continue
                if (secondPiece.color != color || secondPiece.id in excludedPieceIds) continue

                if (firstPiece.type == PieceType.PAWN && (r == 0 || r == 7)) continue
                if (secondPiece.type == PieceType.PAWN && (firstSquare.row == 0 || firstSquare.row == 7)) continue

                val swapped = swapBoardOnly(board, firstSquare, secondSquare)
                if (!isKingInCheck(swapped, color) && !isKingInCheck(swapped, opponent)) {
                    partners.add(secondSquare)
                }
            }
        }
        return partners
    }

    fun getValidSwapSourceSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        excludedPieceIds: Set<Int> = emptySet()
    ): Set<Square> {
        val result = mutableSetOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (p != null && p.color == color && p.id !in excludedPieceIds) {
                    val sq = Square(r, c)
                    if (getValidSwapPartners(board, color, sq, excludedPieceIds).isNotEmpty()) {
                        result.add(sq)
                    }
                }
            }
        }
        return result
    }

    private fun swapBoardOnly(
        board: List<List<ChessPiece?>>,
        first: Square,
        second: Square
    ): List<List<ChessPiece?>> {
        val mutable = board.map { it.toMutableList() }.toMutableList()
        val p1 = mutable[first.row][first.col]
        val p2 = mutable[second.row][second.col]
        mutable[first.row][first.col] = p2
        mutable[second.row][second.col] = p1
        return mutable.map { it.toList() }
    }

    fun applySwap(
        board: List<List<ChessPiece?>>,
        first: Square,
        second: Square,
        castlingRights: CastlingRights
    ): Pair<List<List<ChessPiece?>>, CastlingRights> {
        val nextBoard = swapBoardOnly(board, first, second)
        var wk = castlingRights.whiteKingside
        var wq = castlingRights.whiteQueenside
        var bk = castlingRights.blackKingside
        var bq = castlingRights.blackQueenside

        listOf(first, second).forEach { sq ->
            val orig = board[sq.row][sq.col]
            if (orig?.type == PieceType.KING) {
                if (orig.color == PieceColor.WHITE) {
                    wk = false
                    wq = false
                } else {
                    bk = false
                    bq = false
                }
            }
            if (sq == Square(7, 7)) wk = false
            if (sq == Square(7, 0)) wq = false
            if (sq == Square(0, 7)) bk = false
            if (sq == Square(0, 0)) bq = false
        }

        return nextBoard to CastlingRights(wk, wq, bk, bq)
    }

    /**
     * Valid empty destination squares to spawn a Clone of the Pawn, Knight, or Bishop on [sourceSquare].
     * Prefers the 8 adjacent squares around [sourceSquare]; if none of those are legally open,
     * falls back to empty squares on the player's own half of the board.
     * Never places a Pawn on rank 1 or 8, leaves own King in check, or immediately checks opponent's King.
     */
    fun getValidCloneTargetSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        sourceSquare: Square
    ): Set<Square> {
        val sourcePiece = board.getOrNull(sourceSquare.row)?.getOrNull(sourceSquare.col) ?: return emptySet()
        if (sourcePiece.color != color) return emptySet()
        if (sourcePiece.type != PieceType.PAWN && sourcePiece.type != PieceType.KNIGHT && sourcePiece.type != PieceType.BISHOP) {
            return emptySet()
        }

        val opponent = color.opponent()
        val probe = ChessPiece(id = -777, type = sourcePiece.type, color = color)

        fun isLegalCloneTarget(sq: Square): Boolean {
            if (!sq.isValid() || board[sq.row][sq.col] != null) return false
            if (sourcePiece.type == PieceType.PAWN && (sq.row == 0 || sq.row == 7)) return false
            val nextBoard = placePiece(board, sq, probe)
            return !isKingInCheck(nextBoard, color) && !isKingInCheck(nextBoard, opponent)
        }

        val adjacent = mutableSetOf<Square>()
        for (dr in -1..1) {
            for (dc in -1..1) {
                if (dr == 0 && dc == 0) continue
                val sq = Square(sourceSquare.row + dr, sourceSquare.col + dc)
                if (isLegalCloneTarget(sq)) adjacent.add(sq)
            }
        }
        if (adjacent.isNotEmpty()) return adjacent

        // Fallback if surrounded: empty legal squares on player's own half
        val ownHalfRows = if (color == PieceColor.WHITE) 4..7 else 0..3
        val fallback = mutableSetOf<Square>()
        for (r in ownHalfRows) {
            for (c in 0..7) {
                val sq = Square(r, c)
                if (isLegalCloneTarget(sq)) fallback.add(sq)
            }
        }
        return fallback
    }

    fun getValidCloneSourceSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        excludedPieceIds: Set<Int> = emptySet()
    ): Set<Square> {
        val result = mutableSetOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c]
                if (
                    p != null &&
                    p.color == color &&
                    p.id !in excludedPieceIds &&
                    (p.type == PieceType.PAWN || p.type == PieceType.KNIGHT || p.type == PieceType.BISHOP)
                ) {
                    val sq = Square(r, c)
                    if (getValidCloneTargetSquares(board, color, sq).isNotEmpty()) {
                        result.add(sq)
                    }
                }
            }
        }
        return result
    }

    /**
     * Valid Suicide targets: own non-King pieces whose elimination does not leave own King in check.
     */
    fun getValidSuicideSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor
    ): Set<Square> {
        return getOwnNonKingSquares(board, color).filterTo(mutableSetOf()) { sq ->
            val after = removePiece(board, sq)
            !isKingInCheck(after, color)
        }
    }

    fun removePiece(
        board: List<List<ChessPiece?>>,
        square: Square
    ): List<List<ChessPiece?>> {
        return board.mapIndexed { r, rowList ->
            if (r != square.row) {
                rowList
            } else {
                rowList.mapIndexed { c, existing ->
                    if (c == square.col) null else existing
                }
            }
        }
    }

    fun applySuicide(
        board: List<List<ChessPiece?>>,
        square: Square,
        castlingRights: CastlingRights
    ): Pair<List<List<ChessPiece?>>, CastlingRights> {
        val newBoard = removePiece(board, square)
        var wk = castlingRights.whiteKingside
        var wq = castlingRights.whiteQueenside
        var bk = castlingRights.blackKingside
        var bq = castlingRights.blackQueenside
        if (square == Square(7, 7)) wk = false
        if (square == Square(7, 0)) wq = false
        if (square == Square(0, 7)) bk = false
        if (square == Square(0, 0)) bq = false
        return newBoard to CastlingRights(wk, wq, bk, bq)
    }

    fun isPromotionMove(board: List<List<ChessPiece?>>, from: Square, to: Square): Boolean {
        val piece = board[from.row][from.col] ?: return false
        if (piece.type != PieceType.PAWN) return false
        return (piece.color == PieceColor.WHITE && to.row == 0) ||
            (piece.color == PieceColor.BLACK && to.row == 7)
    }

    fun applyMove(
        board: List<List<ChessPiece?>>,
        move: ChessMove,
        castlingRights: CastlingRights
    ): MoveApplicationResult {
        val movingPiece = requireNotNull(board[move.from.row][move.from.col]) {
            "No piece at ${move.from.toAlgebraic()}"
        }

        val capturedPiece: ChessPiece? = if (move.isEnPassant) {
            board[move.from.row][move.to.col]
        } else {
            board[move.to.row][move.to.col]
        }

        val newBoard = applyMoveToBoardOnly(board, move)

        // Update castling rights when king/rook moves or a corner rook is captured
        var wk = castlingRights.whiteKingside
        var wq = castlingRights.whiteQueenside
        var bk = castlingRights.blackKingside
        var bq = castlingRights.blackQueenside

        if (movingPiece.type == PieceType.KING) {
            if (movingPiece.color == PieceColor.WHITE) {
                wk = false
                wq = false
            } else {
                bk = false
                bq = false
            }
        }
        if (move.from == Square(7, 7) || move.to == Square(7, 7)) wk = false
        if (move.from == Square(7, 0) || move.to == Square(7, 0)) wq = false
        if (move.from == Square(0, 7) || move.to == Square(0, 7)) bk = false
        if (move.from == Square(0, 0) || move.to == Square(0, 0)) bq = false

        val newCastlingRights = CastlingRights(
            whiteKingside = wk,
            whiteQueenside = wq,
            blackKingside = bk,
            blackQueenside = bq
        )

        // Set en passant square only if a pawn double-stepped from its home rank
        var newEnPassantTarget: Square? = null
        var newEnPassantOwner: PieceColor? = null
        if (movingPiece.type == PieceType.PAWN && kotlin.math.abs(move.to.row - move.from.row) == 2) {
            val midRow = (move.from.row + move.to.row) / 2
            newEnPassantTarget = Square(midRow, move.from.col)
            newEnPassantOwner = movingPiece.color
        }

        val notation = buildMoveNotation(movingPiece, move, capturedPiece != null)

        return MoveApplicationResult(
            board = newBoard,
            movedPiece = movingPiece,
            capturedPiece = capturedPiece,
            castlingRights = newCastlingRights,
            enPassantTarget = newEnPassantTarget,
            enPassantOwner = newEnPassantOwner,
            notation = notation
        )
    }

    /**
     * Reworked Revive (Mode A — when player has captured pieces):
     * Pick one of your own unfrozen Pawns on the board and transform it into [piece].
     * Must NOT leave own King in check or immediately put the opponent's King in check.
     */
    fun getValidReviveSquares(
        board: List<List<ChessPiece?>>,
        piece: ChessPiece,
        color: PieceColor,
        excludedPieceIds: Set<Int> = emptySet()
    ): Set<Square> {
        val opponentColor = color.opponent()
        val result = mutableSetOf<Square>()

        for (r in 0..7) {
            for (c in 0..7) {
                val existing = board[r][c]
                if (
                    existing != null &&
                    existing.color == color &&
                    existing.type == PieceType.PAWN &&
                    existing.id !in excludedPieceIds
                ) {
                    val candidateBoard = placePiece(board, Square(r, c), piece)
                    if (!isKingInCheck(candidateBoard, color) && !isKingInCheck(candidateBoard, opponentColor)) {
                        result.add(Square(r, c))
                    }
                }
            }
        }
        return result
    }

    /**
     * Reworked Revive (Mode B — when player has NO captured pieces to revive):
     * Place a new Pawn onto an empty square on ranks 2–3 (White: rows 5..6) or ranks 6–7 (Black: rows 1..2).
     * Must NOT immediately put the opponent's King in check.
     */
    fun getValidSpawnPawnSquares(
        board: List<List<ChessPiece?>>,
        color: PieceColor
    ): Set<Square> {
        val spawnRows = if (color == PieceColor.WHITE) 5..6 else 1..2
        val opponentColor = color.opponent()
        val probePawn = ChessPiece(id = -999, type = PieceType.PAWN, color = color)
        val result = mutableSetOf<Square>()

        for (r in spawnRows) {
            for (c in 0..7) {
                if (board[r][c] == null) {
                    val candidateBoard = placePiece(board, Square(r, c), probePawn)
                    if (!isKingInCheck(candidateBoard, opponentColor)) {
                        result.add(Square(r, c))
                    }
                }
            }
        }
        return result
    }

    fun placePiece(
        board: List<List<ChessPiece?>>,
        square: Square,
        piece: ChessPiece
    ): List<List<ChessPiece?>> {
        return board.mapIndexed { r, rowList ->
            if (r != square.row) {
                rowList
            } else {
                rowList.mapIndexed { c, existing ->
                    if (c == square.col) piece else existing
                }
            }
        }
    }

    private fun applyMoveToBoardOnly(
        board: List<List<ChessPiece?>>,
        move: ChessMove
    ): List<List<ChessPiece?>> {
        val mutableBoard = board.map { it.toMutableList() }.toMutableList()
        val piece = mutableBoard[move.from.row][move.from.col] ?: return board

        mutableBoard[move.from.row][move.from.col] = null

        if (move.isEnPassant) {
            mutableBoard[move.from.row][move.to.col] = null
        }

        val placedPiece = if (move.promotion != null) {
            piece.copy(type = move.promotion)
        } else {
            piece
        }
        mutableBoard[move.to.row][move.to.col] = placedPiece

        if (move.isCastlingKingside) {
            val row = move.from.row
            val rook = mutableBoard[row][7]
            mutableBoard[row][7] = null
            mutableBoard[row][5] = rook
        } else if (move.isCastlingQueenside) {
            val row = move.from.row
            val rook = mutableBoard[row][0]
            mutableBoard[row][0] = null
            mutableBoard[row][3] = rook
        }

        return mutableBoard.map { it.toList() }
    }

    private fun generatePseudoLegalMoves(
        board: List<List<ChessPiece?>>,
        from: Square,
        piece: ChessPiece,
        castlingRights: CastlingRights,
        enPassantTarget: Square?,
        enPassantOwner: PieceColor?,
        excludedPieceIds: Set<Int> = emptySet()
    ): List<ChessMove> {
        val moves = mutableListOf<ChessMove>()
        val color = piece.color
        val opponent = color.opponent()

        when (piece.type) {
            PieceType.PAWN -> {
                val dir = if (color == PieceColor.WHITE) -1 else 1
                val startRow = if (color == PieceColor.WHITE) 6 else 1
                val promoRow = if (color == PieceColor.WHITE) 0 else 7

                fun addPawnMove(to: Square, isEnPassant: Boolean = false) {
                    if (to.row == promoRow) {
                        listOf(
                            PieceType.QUEEN,
                            PieceType.ROOK,
                            PieceType.BISHOP,
                            PieceType.KNIGHT
                        ).forEach { promoType ->
                            moves.add(ChessMove(from = from, to = to, promotion = promoType))
                        }
                    } else {
                        moves.add(ChessMove(from = from, to = to, isEnPassant = isEnPassant))
                    }
                }

                // Forward 1
                val oneRow = from.row + dir
                if (oneRow in 0..7 && board[oneRow][from.col] == null) {
                    addPawnMove(Square(oneRow, from.col))

                    // Forward 2 from initial pawn rank
                    val twoRow = from.row + dir * 2
                    if (from.row == startRow && twoRow in 0..7 && board[twoRow][from.col] == null) {
                        moves.add(ChessMove(from = from, to = Square(twoRow, from.col)))
                    }
                }

                // Diagonal captures + En Passant
                for (dc in listOf(-1, 1)) {
                    val tc = from.col + dc
                    if (oneRow in 0..7 && tc in 0..7) {
                        val targetSquare = Square(oneRow, tc)
                        val targetPiece = board[oneRow][tc]
                        if (targetPiece != null && targetPiece.color == opponent) {
                            addPawnMove(targetSquare)
                        } else if (
                            enPassantTarget == targetSquare &&
                            enPassantOwner == opponent
                        ) {
                            val victim = board[from.row][tc]
                            if (victim != null && victim.color == opponent && victim.type == PieceType.PAWN) {
                                addPawnMove(targetSquare, isEnPassant = true)
                            }
                        }
                    }
                }
            }

            PieceType.KNIGHT -> {
                val offsets = arrayOf(
                    -2 to -1, -2 to 1, -1 to -2, -1 to 2,
                    1 to -2, 1 to 2, 2 to -1, 2 to 1
                )
                for ((dr, dc) in offsets) {
                    val r = from.row + dr
                    val c = from.col + dc
                    if (r in 0..7 && c in 0..7) {
                        val target = board[r][c]
                        if (target == null || target.color == opponent) {
                            moves.add(ChessMove(from = from, to = Square(r, c)))
                        }
                    }
                }
            }

            PieceType.BISHOP -> {
                addSlidingMoves(board, from, color, arrayOf(-1 to -1, -1 to 1, 1 to -1, 1 to 1), moves)
            }

            PieceType.ROOK -> {
                addSlidingMoves(board, from, color, arrayOf(-1 to 0, 1 to 0, 0 to -1, 0 to 1), moves)
            }

            PieceType.QUEEN -> {
                addSlidingMoves(
                    board,
                    from,
                    color,
                    arrayOf(
                        -1 to -1, -1 to 1, 1 to -1, 1 to 1,
                        -1 to 0, 1 to 0, 0 to -1, 0 to 1
                    ),
                    moves
                )
            }

            PieceType.KING -> {
                for (dr in -1..1) {
                    for (dc in -1..1) {
                        if (dr == 0 && dc == 0) continue
                        val r = from.row + dr
                        val c = from.col + dc
                        if (r in 0..7 && c in 0..7) {
                            val target = board[r][c]
                            if (target == null || target.color == opponent) {
                                moves.add(ChessMove(from = from, to = Square(r, c)))
                            }
                        }
                    }
                }

                // Castling
                val homeRow = if (color == PieceColor.WHITE) 7 else 0
                if (from == Square(homeRow, 4) && !isSquareAttacked(board, from, opponent)) {
                    val canKingside = if (color == PieceColor.WHITE) {
                        castlingRights.whiteKingside
                    } else {
                        castlingRights.blackKingside
                    }
                    if (canKingside) {
                        val cornerRook = board[homeRow][7]
                        if (
                            cornerRook != null &&
                            cornerRook.color == color &&
                            cornerRook.type == PieceType.ROOK &&
                            cornerRook.id !in excludedPieceIds &&
                            board[homeRow][5] == null &&
                            board[homeRow][6] == null &&
                            !isSquareAttacked(board, Square(homeRow, 5), opponent) &&
                            !isSquareAttacked(board, Square(homeRow, 6), opponent)
                        ) {
                            moves.add(
                                ChessMove(
                                    from = from,
                                    to = Square(homeRow, 6),
                                    isCastlingKingside = true
                                )
                            )
                        }
                    }

                    val canQueenside = if (color == PieceColor.WHITE) {
                        castlingRights.whiteQueenside
                    } else {
                        castlingRights.blackQueenside
                    }
                    if (canQueenside) {
                        val cornerRook = board[homeRow][0]
                        if (
                            cornerRook != null &&
                            cornerRook.color == color &&
                            cornerRook.type == PieceType.ROOK &&
                            cornerRook.id !in excludedPieceIds &&
                            board[homeRow][1] == null &&
                            board[homeRow][2] == null &&
                            board[homeRow][3] == null &&
                            !isSquareAttacked(board, Square(homeRow, 3), opponent) &&
                            !isSquareAttacked(board, Square(homeRow, 2), opponent)
                        ) {
                            moves.add(
                                ChessMove(
                                    from = from,
                                    to = Square(homeRow, 2),
                                    isCastlingQueenside = true
                                )
                            )
                        }
                    }
                }
            }
        }
        return moves
    }

    private fun addSlidingMoves(
        board: List<List<ChessPiece?>>,
        from: Square,
        color: PieceColor,
        directions: Array<Pair<Int, Int>>,
        out: MutableList<ChessMove>
    ) {
        val opponent = color.opponent()
        for ((dr, dc) in directions) {
            var r = from.row + dr
            var c = from.col + dc
            while (r in 0..7 && c in 0..7) {
                val target = board[r][c]
                if (target == null) {
                    out.add(ChessMove(from = from, to = Square(r, c)))
                } else {
                    if (target.color == opponent) {
                        out.add(ChessMove(from = from, to = Square(r, c)))
                    }
                    break
                }
                r += dr
                c += dc
            }
        }
    }

    private fun buildMoveNotation(
        piece: ChessPiece,
        move: ChessMove,
        isCapture: Boolean
    ): String {
        if (move.isCastlingKingside) return "O-O"
        if (move.isCastlingQueenside) return "O-O-O"
        val captureMark = if (isCapture) "×" else "→"
        val prefix = if (piece.type == PieceType.PAWN) "" else piece.type.notation
        val promoSuffix = move.promotion?.let { "=${it.notation}" }.orEmpty()
        return "$prefix${move.from.toAlgebraic()}$captureMark${move.to.toAlgebraic()}$promoSuffix"
    }

    fun getValidSniperTargets(
        board: List<List<ChessPiece?>>,
        sourceSquare: Square,
        targetColor: PieceColor,
        protectedPieceIds: Set<Int>
    ): List<Square> {
        val piece = board[sourceSquare.row][sourceSquare.col] ?: return emptyList()
        val ownColor = piece.color

        val candidateTargets = mutableListOf<Square>()
        val moves = getLegalMovesForSquare(
            board = board,
            from = sourceSquare,
            castlingRights = CastlingRights(false, false, false, false),
            enPassantTarget = null,
            enPassantOwner = null,
            excludedPieceIds = emptySet(),
            protectedPieceIds = protectedPieceIds
        )

        for (m in moves) {
            val destPiece = board[m.to.row][m.to.col] ?: continue
            if (destPiece.color != targetColor) continue
            if (destPiece.type == PieceType.KING) continue
            if (destPiece.id in protectedPieceIds) continue

            val boardAfter = removePiece(board, m.to)
            if (!isKingInCheck(boardAfter, ownColor)) {
                candidateTargets.add(m.to)
            }
        }
        return candidateTargets
    }

    fun getValidSniperSources(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        protectedPieceIds: Set<Int>
    ): List<Square> {
        val oppColor = color.opponent()
        val sources = mutableListOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                val p = board[r][c] ?: continue
                if (p.color != color) continue
                val targets = getValidSniperTargets(board, Square(r, c), oppColor, protectedPieceIds)
                if (targets.isNotEmpty()) {
                    sources.add(Square(r, c))
                }
            }
        }
        return sources
    }

    fun getValidPortalSources(
        board: List<List<ChessPiece?>>,
        color: PieceColor
    ): List<Square> {
        return getOwnNonKingSquares(board, color).toList()
    }

    fun getValidPortalDestinations(
        board: List<List<ChessPiece?>>,
        color: PieceColor,
        sourceSquare: Square
    ): List<Square> {
        val piece = board[sourceSquare.row][sourceSquare.col] ?: return emptyList()
        if (piece.color != color || piece.type == PieceType.KING) return emptyList()

        val destinations = mutableListOf<Square>()
        val withoutPiece = removePiece(board, sourceSquare)

        for (r in 0..7) {
            for (c in 0..7) {
                if (board[r][c] != null) continue
                val dest = Square(r, c)
                val simulated = placePiece(withoutPiece, dest, piece)
                if (!isKingInCheck(simulated, color)) {
                    destinations.add(dest)
                }
            }
        }
        return destinations
    }

    fun getValidTrapSquares(
        board: List<List<ChessPiece?>>,
        traps: List<Square>
    ): List<Square> {
        val valid = mutableListOf<Square>()
        for (r in 0..7) {
            for (c in 0..7) {
                if (board[r][c] != null) continue
                val sq = Square(r, c)
                if (sq in traps) continue
                valid.add(sq)
            }
        }
        return valid
    }
}
