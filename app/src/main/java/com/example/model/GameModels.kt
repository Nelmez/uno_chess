package com.example.model

enum class PlayerId(val displayName: String, val shortLabel: String) {
    PLAYER_1("Player 1", "P1"),
    PLAYER_2("Player 2", "P2");

    fun opponent(): PlayerId = if (this == PLAYER_1) PLAYER_2 else PLAYER_1
}

enum class PieceColor(val displayName: String) {
    WHITE("White"),
    BLACK("Black");

    fun opponent(): PieceColor = if (this == WHITE) BLACK else WHITE
}

enum class PieceType(
    val displayName: String,
    val notation: String,
    val materialValue: Int
) {
    KING("King", "K", 100),
    QUEEN("Queen", "Q", 9),
    ROOK("Rook", "R", 5),
    BISHOP("Bishop", "B", 3),
    KNIGHT("Knight", "N", 3),
    PAWN("Pawn", "", 1);

    fun isMajor(): Boolean = this == QUEEN || this == ROOK
}

data class ChessPiece(
    val id: Int,
    val type: PieceType,
    val color: PieceColor
)

data class Square(val row: Int, val col: Int) {
    fun isValid(): Boolean = row in 0..7 && col in 0..7

    fun toAlgebraic(): String {
        val fileChar = ('a' + col)
        val rankNum = 8 - row
        return "$fileChar$rankNum"
    }
}

data class ChessMove(
    val from: Square,
    val to: Square,
    val isCastlingKingside: Boolean = false,
    val isCastlingQueenside: Boolean = false,
    val isEnPassant: Boolean = false,
    val promotion: PieceType? = null
)

data class CastlingRights(
    val whiteKingside: Boolean = true,
    val whiteQueenside: Boolean = true,
    val blackKingside: Boolean = true,
    val blackQueenside: Boolean = true
)

enum class UnoColor(val displayName: String) {
    RED("Red"),
    BLUE("Blue"),
    GREEN("Green"),
    YELLOW("Yellow")
}

enum class UnoCardTheme(val displayName: String, val tagline: String) {
    CLASSIC("Classic UNO", "Clean matte tabletop cards"),
    SPACE("Space Theme", "Orbital rings & deep starfield"),
    FANTASY("Fantasy Theme", "Heraldic crests & parchment"),
    SPIRITS("Top Shelf Spirits", "Whiskey charcoal, amber & vintage bar labels")
}

enum class AiDifficulty(val displayName: String, val subtitle: String) {
    EASY("Easy", "Casual play with occasional blunders"),
    MEDIUM("Medium", "Solid tactics & standard chess"),
    HARD("Hard", "Multi-move planning & card anticipation")
}

sealed interface OpponentMode {
    data object PassAndPlay : OpponentMode
    data class VsAi(val difficulty: AiDifficulty) : OpponentMode
}

enum class SpecialCardKey(
    val displayName: String,
    val shortSymbol: String,
    val deckCopies: Int
) {
    REVIVE("Revive", "+1", 3),
    FREEZE("Freeze", "❄", 2),
    SHIELD("Shield", "🛡", 2),
    MERGE("Merge", "⊕", 2),
    SACRIFICE("Sacrifice", "❖", 2),
    SWAP("Swap", "⇄", 2),
    CLONE("Clone", "⧉", 2),
    HIJACK("Hijack", "⚡", 2),
    PROMOTE("Promote", "↑", 2),
    SUICIDE("Suicide", "✕", 2),
    WILD("Wild", "✦", 1)
}

sealed interface UnoCardType {
    data class NumberCard(val moves: Int) : UnoCardType
    data object RevivePlusTwo : UnoCardType
    data object Freeze : UnoCardType
    data object Merge : UnoCardType
    data object Sacrifice : UnoCardType
    data object Hijack : UnoCardType
    data object PawnPromote : UnoCardType
    data object Suicide : UnoCardType
    data object Shield : UnoCardType
    data object SwapPositions : UnoCardType
    data object Clone : UnoCardType
    data object Wild : UnoCardType

    fun shortSymbol(): String = when (this) {
        is NumberCard -> moves.toString()
        RevivePlusTwo -> "+1"
        Freeze -> "❄"
        Merge -> "⊕"
        Sacrifice -> "❖"
        Hijack -> "⚡"
        PawnPromote -> "↑"
        Suicide -> "✕"
        Shield -> "🛡"
        SwapPositions -> "⇄"
        Clone -> "⧉"
        Wild -> "✦"
    }

    fun title(): String = when (this) {
        is NumberCard -> if (moves == 1) "1 Move" else "$moves Moves"
        RevivePlusTwo -> "Revive"
        Freeze -> "Freeze"
        Merge -> "Merge"
        Sacrifice -> "Sacrifice"
        Hijack -> "Hijack"
        PawnPromote -> "Promote"
        Suicide -> "Suicide"
        Shield -> "Shield"
        SwapPositions -> "Swap"
        Clone -> "Clone"
        Wild -> "Wild Card"
    }

    fun subtitle(): String = when (this) {
        is NumberCard -> if (moves == 1) "1 move (1 per piece)" else "$moves moves (max 1 per piece)"
        RevivePlusTwo -> "Turn 1 pawn into a captured piece (or place a pawn)"
        Freeze -> "Freeze 1 enemy piece + 1 move"
        Merge -> "Merge or swap pieces by value"
        Sacrifice -> "Split 1 piece into equal-value pieces"
        Hijack -> "Move 1 opponent piece"
        PawnPromote -> "Promote 1 pawn to Knight or Bishop"
        Suicide -> "Eliminate 1 own piece (King only = Lose)"
        Shield -> "Protect 1 own piece from capture + 1 move"
        SwapPositions -> "Swap positions of 2 of your pieces"
        Clone -> "Duplicate 1 Pawn, Knight, or Bishop"
        Wild -> "Choose any enabled special card effect"
    }
}

data class SacrificeRecipe(
    val id: String,
    val label: String,
    val pieces: List<PieceType>
)

data class UnoCard(
    val id: Int,
    val color: UnoColor,
    val type: UnoCardType
)

enum class TurnPhase {
    AWAITING_DRAW,
    MOVING,
    REVIVING,
    FREEZING,
    MERGING,
    SACRIFICING,
    HIJACKING,
    PROMOTING_PAWN,
    SUICIDING,
    SHIELDING,
    SWAPPING,
    CLONING,
    WILD_CHOOSING,
    GAME_OVER
}

sealed interface GameOutcome {
    data class Checkmate(
        val winnerPlayer: PlayerId,
        val winnerColor: PieceColor
    ) : GameOutcome

    data class Forfeit(
        val forfeitingPlayer: PlayerId,
        val winnerPlayer: PlayerId,
        val winnerColor: PieceColor
    ) : GameOutcome

    data class Suicide(
        val losingPlayer: PlayerId,
        val winnerPlayer: PlayerId,
        val winnerColor: PieceColor
    ) : GameOutcome

    data object Stalemate : GameOutcome
}

enum class MatchEndReason(val label: String) {
    CHECKMATE("Checkmate"),
    FORFEIT("Forfeit"),
    SUICIDE("Suicide"),
    STALEMATE("Stalemate")
}

data class MatchRecord(
    val id: Int,
    val timestampMs: Long,
    val modeLabel: String,
    val winnerLabel: String,
    val winnerPlayer: PlayerId?,
    val winnerColor: PieceColor?,
    val reason: MatchEndReason,
    val totalTurns: Int
)

data class MatchStats(
    val currentStreak: Int = 0,
    val bestStreak: Int = 0,
    val streakHolder: String? = null,
    val totalWinsP1: Int = 0,
    val totalWinsP2: Int = 0,
    val totalDraws: Int = 0,
    val history: List<MatchRecord> = emptyList()
)

enum class LogCategory {
    CARD,
    MOVE,
    CHECK,
    REVIVE,
    FREEZE,
    MERGE,
    SACRIFICE,
    HIJACK,
    PROMOTE,
    SUICIDE,
    SHIELD,
    SWAP,
    CLONE,
    WILD,
    SYSTEM,
    GAME_OVER
}

data class LogEntry(
    val id: Int,
    val message: String,
    val category: LogCategory
)

data class PendingPromotion(
    val from: Square,
    val to: Square
)

data class NoticeBanner(
    val id: Int,
    val message: String,
    val isAlert: Boolean = false
)

data class MoveAnimationEvent(
    val id: Int,
    val piece: ChessPiece,
    val from: Square,
    val to: Square,
    val isCapture: Boolean,
    val secondaryPiece: ChessPiece? = null,
    val secondaryFrom: Square? = null,
    val secondaryTo: Square? = null
)

data class ReviveAnimationEvent(
    val id: Int,
    val piece: ChessPiece,
    val square: Square
)

data class GameUiState(
    val board: List<List<ChessPiece?>>,
    val playerColors: Map<PlayerId, PieceColor>,
    val activePlayer: PlayerId,
    val boardFlipped: Boolean,
    val phase: TurnPhase,
    val drawPile: List<UnoCard>,
    val discardPile: List<UnoCard>,
    val activeCard: UnoCard?,
    val drawRevision: Int,
    val totalMovesForCard: Int,
    val movesRemaining: Int,
    val revivesTotalForCard: Int,
    val revivesRemaining: Int,
    val majorRevivedThisTurn: Boolean,
    val movedPieceIdsThisTurn: Set<Int>,
    val frozenPieceId: Int?,
    val frozenOwner: PieceColor?,
    val validFreezeSquares: Set<Square>,
    val shieldedPieceId: Int? = null,
    val shieldedOwner: PieceColor? = null,
    val validShieldSquares: Set<Square> = emptySet(),
    val selectedMergeSquares: List<Square> = emptyList(),
    val selectedSacrificeSquare: Square? = null,
    val validSacrificeSquares: Set<Square> = emptySet(),
    val selectedPromotePawnSquare: Square? = null,
    val validPromotePawnSquares: Set<Square> = emptySet(),
    val validSuicideSquares: Set<Square> = emptySet(),
    val selectedSwapSquare: Square? = null,
    val validSwapSquares: Set<Square> = emptySet(),
    val selectedCloneSourceSquare: Square? = null,
    val validCloneSquares: Set<Square> = emptySet(),
    val availableWildEffects: List<SpecialCardKey> = emptyList(),
    val enabledSpecialCards: Set<SpecialCardKey> = SpecialCardKey.entries.toSet(),
    val selectedSquare: Square?,
    val legalMovesForSelected: List<ChessMove>,
    val selectedRevivePieceId: Int?,
    val validReviveSquares: Set<Square>,
    val capturedPieces: Map<PieceColor, List<ChessPiece>>,
    val lastMoveFrom: Square?,
    val lastMoveTo: Square?,
    val lastRevivedSquare: Square?,
    val checkedKingSquare: Square?,
    val pendingPromotion: PendingPromotion?,
    val noticeBanner: NoticeBanner?,
    val moveAnimationEvent: MoveAnimationEvent?,
    val reviveAnimationEvent: ReviveAnimationEvent?,
    val gameLog: List<LogEntry>,
    val outcome: GameOutcome?,
    val castlingRights: CastlingRights,
    val enPassantTarget: Square?,
    val enPassantOwner: PieceColor?
) {
    val activeColor: PieceColor
        get() = playerColors.getValue(activePlayer)

    fun playerControlling(color: PieceColor): PlayerId {
        return if (playerColors.getValue(PlayerId.PLAYER_1) == color) {
            PlayerId.PLAYER_1
        } else {
            PlayerId.PLAYER_2
        }
    }

    fun isPieceFrozen(piece: ChessPiece): Boolean {
        return frozenPieceId != null && piece.id == frozenPieceId && piece.color == frozenOwner
    }

    fun isPieceShielded(piece: ChessPiece): Boolean {
        return shieldedPieceId != null && piece.id == shieldedPieceId && piece.color == shieldedOwner
    }
}
