package com.example.network

import com.example.model.CastlingRights
import com.example.model.ChessPiece
import com.example.model.GameOutcome
import com.example.model.GameUiState
import com.example.model.LogCategory
import com.example.model.LogEntry
import com.example.model.MoveAnimationEvent
import com.example.model.NoticeBanner
import com.example.model.PendingPromotion
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.PlayerId
import com.example.model.ReviveAnimationEvent
import com.example.model.SpecialCardKey
import com.example.model.Square
import com.example.model.TurnPhase
import com.example.model.UnoCard
import com.example.model.UnoCardType
import com.example.model.UnoColor
import org.json.JSONArray
import org.json.JSONObject

sealed interface LanAction {
    data object DrawCard : LanAction
    data class TapSquare(val square: Square) : LanAction
    data class SelectPromotion(val pieceType: PieceType) : LanAction
    data object CancelPromotion : LanAction
    data class SelectRevivePiece(val pieceId: Int) : LanAction
    data class ConfirmMerge(val targetType: PieceType) : LanAction
    data object ClearMergeSelection : LanAction
    data object SkipMerge : LanAction
    data class ConfirmSacrifice(val recipeId: String) : LanAction
    data object SkipSacrifice : LanAction
    data class ConfirmPawnPromote(val targetType: PieceType) : LanAction
    data object ClearSwapSelection : LanAction
    data object SkipSwap : LanAction
    data object ClearCloneSelection : LanAction
    data object SkipClone : LanAction
    data class SelectWildEffect(val effect: SpecialCardKey) : LanAction
    data object Forfeit : LanAction
    data object ResetGame : LanAction
}

object LanStateCodec {

    fun encodeAction(action: LanAction): String {
        val obj = JSONObject()
        when (action) {
            LanAction.DrawCard -> obj.put("kind", "DRAW")
            is LanAction.TapSquare -> {
                obj.put("kind", "TAP")
                obj.put("r", action.square.row)
                obj.put("c", action.square.col)
            }
            is LanAction.SelectPromotion -> {
                obj.put("kind", "PROMOTE")
                obj.put("type", action.pieceType.name)
            }
            LanAction.CancelPromotion -> obj.put("kind", "PROMOTE_CANCEL")
            is LanAction.SelectRevivePiece -> {
                obj.put("kind", "REVIVE_PIECE")
                obj.put("id", action.pieceId)
            }
            is LanAction.ConfirmMerge -> {
                obj.put("kind", "MERGE_CONFIRM")
                obj.put("type", action.targetType.name)
            }
            LanAction.ClearMergeSelection -> obj.put("kind", "MERGE_CLEAR")
            LanAction.SkipMerge -> obj.put("kind", "MERGE_SKIP")
            is LanAction.ConfirmSacrifice -> {
                obj.put("kind", "SACRIFICE_CONFIRM")
                obj.put("recipe", action.recipeId)
            }
            LanAction.SkipSacrifice -> obj.put("kind", "SACRIFICE_SKIP")
            is LanAction.ConfirmPawnPromote -> {
                obj.put("kind", "PAWN_PROMOTE_CONFIRM")
                obj.put("type", action.targetType.name)
            }
            LanAction.ClearSwapSelection -> obj.put("kind", "SWAP_CLEAR")
            LanAction.SkipSwap -> obj.put("kind", "SWAP_SKIP")
            LanAction.ClearCloneSelection -> obj.put("kind", "CLONE_CLEAR")
            LanAction.SkipClone -> obj.put("kind", "CLONE_SKIP")
            is LanAction.SelectWildEffect -> {
                obj.put("kind", "WILD_SELECT")
                obj.put("effect", action.effect.name)
            }
            LanAction.Forfeit -> obj.put("kind", "FORFEIT")
            LanAction.ResetGame -> obj.put("kind", "RESET")
        }
        return "ACT:$obj"
    }

    fun decodeAction(line: String): LanAction? {
        if (!line.startsWith("ACT:")) return null
        return runCatching {
            val obj = JSONObject(line.removePrefix("ACT:"))
            when (obj.getString("kind")) {
                "DRAW" -> LanAction.DrawCard
                "TAP" -> LanAction.TapSquare(Square(obj.getInt("r"), obj.getInt("c")))
                "PROMOTE" -> LanAction.SelectPromotion(PieceType.valueOf(obj.getString("type")))
                "PROMOTE_CANCEL" -> LanAction.CancelPromotion
                "REVIVE_PIECE" -> LanAction.SelectRevivePiece(obj.getInt("id"))
                "MERGE_CONFIRM" -> LanAction.ConfirmMerge(PieceType.valueOf(obj.getString("type")))
                "MERGE_CLEAR" -> LanAction.ClearMergeSelection
                "MERGE_SKIP" -> LanAction.SkipMerge
                "SACRIFICE_CONFIRM" -> LanAction.ConfirmSacrifice(obj.getString("recipe"))
                "SACRIFICE_SKIP" -> LanAction.SkipSacrifice
                "PAWN_PROMOTE_CONFIRM" -> LanAction.ConfirmPawnPromote(PieceType.valueOf(obj.getString("type")))
                "SWAP_CLEAR" -> LanAction.ClearSwapSelection
                "SWAP_SKIP" -> LanAction.SkipSwap
                "CLONE_CLEAR" -> LanAction.ClearCloneSelection
                "CLONE_SKIP" -> LanAction.SkipClone
                "WILD_SELECT" -> LanAction.SelectWildEffect(SpecialCardKey.valueOf(obj.getString("effect")))
                "FORFEIT" -> LanAction.Forfeit
                "RESET" -> LanAction.ResetGame
                else -> null
            }
        }.getOrNull()
    }

    fun encodeState(state: GameUiState): String {
        val obj = JSONObject()

        // Board (8x8)
        val boardArr = JSONArray()
        for (r in 0..7) {
            val rowArr = JSONArray()
            for (c in 0..7) {
                val p = state.board[r][c]
                if (p == null) {
                    rowArr.put(JSONObject.NULL)
                } else {
                    rowArr.put(encodePiece(p))
                }
            }
            boardArr.put(rowArr)
        }
        obj.put("board", boardArr)

        obj.put("p1Color", state.playerColors.getValue(PlayerId.PLAYER_1).name)
        obj.put("p2Color", state.playerColors.getValue(PlayerId.PLAYER_2).name)
        obj.put("activePlayer", state.activePlayer.name)
        obj.put("boardFlipped", state.boardFlipped)
        obj.put("phase", state.phase.name)

        obj.put("drawPile", encodeCards(state.drawPile))
        obj.put("discardPile", encodeCards(state.discardPile))
        obj.put("activeCard", state.activeCard?.let(::encodeCard) ?: JSONObject.NULL)
        obj.put("drawRevision", state.drawRevision)
        obj.put("totalMovesForCard", state.totalMovesForCard)
        obj.put("movesRemaining", state.movesRemaining)
        obj.put("revivesTotalForCard", state.revivesTotalForCard)
        obj.put("revivesRemaining", state.revivesRemaining)
        obj.put("majorRevivedThisTurn", state.majorRevivedThisTurn)

        val movedIdsArr = JSONArray()
        state.movedPieceIdsThisTurn.forEach { movedIdsArr.put(it) }
        obj.put("movedPieceIds", movedIdsArr)

        obj.put("frozenPieceId", state.frozenPieceId ?: JSONObject.NULL)
        obj.put("frozenOwner", state.frozenOwner?.name ?: JSONObject.NULL)

        val validFreezeArr = JSONArray()
        state.validFreezeSquares.forEach { validFreezeArr.put(encodeSquare(it)) }
        obj.put("validFreezeSquares", validFreezeArr)

        val mergeSquaresArr = JSONArray()
        state.selectedMergeSquares.forEach { mergeSquaresArr.put(encodeSquare(it)) }
        obj.put("selectedMergeSquares", mergeSquaresArr)

        obj.put("selectedSacrificeSquare", state.selectedSacrificeSquare?.let(::encodeSquare) ?: JSONObject.NULL)
        val sacrificeSquaresArr = JSONArray()
        state.validSacrificeSquares.forEach { sacrificeSquaresArr.put(encodeSquare(it)) }
        obj.put("validSacrificeSquares", sacrificeSquaresArr)

        obj.put("selectedPromotePawnSquare", state.selectedPromotePawnSquare?.let(::encodeSquare) ?: JSONObject.NULL)
        val promoteSquaresArr = JSONArray()
        state.validPromotePawnSquares.forEach { promoteSquaresArr.put(encodeSquare(it)) }
        obj.put("validPromotePawnSquares", promoteSquaresArr)

        val suicideSquaresArr = JSONArray()
        state.validSuicideSquares.forEach { suicideSquaresArr.put(encodeSquare(it)) }
        obj.put("validSuicideSquares", suicideSquaresArr)

        obj.put("shieldedPieceId", state.shieldedPieceId ?: JSONObject.NULL)
        obj.put("shieldedOwner", state.shieldedOwner?.name ?: JSONObject.NULL)
        val shieldSquaresArr = JSONArray()
        state.validShieldSquares.forEach { shieldSquaresArr.put(encodeSquare(it)) }
        obj.put("validShieldSquares", shieldSquaresArr)

        obj.put("selectedSwapSquare", state.selectedSwapSquare?.let(::encodeSquare) ?: JSONObject.NULL)
        val swapSquaresArr = JSONArray()
        state.validSwapSquares.forEach { swapSquaresArr.put(encodeSquare(it)) }
        obj.put("validSwapSquares", swapSquaresArr)

        obj.put("selectedCloneSourceSquare", state.selectedCloneSourceSquare?.let(::encodeSquare) ?: JSONObject.NULL)
        val cloneSquaresArr = JSONArray()
        state.validCloneSquares.forEach { cloneSquaresArr.put(encodeSquare(it)) }
        obj.put("validCloneSquares", cloneSquaresArr)

        val wildArr = JSONArray()
        state.availableWildEffects.forEach { wildArr.put(it.name) }
        obj.put("availableWildEffects", wildArr)

        val enabledArr = JSONArray()
        state.enabledSpecialCards.forEach { enabledArr.put(it.name) }
        obj.put("enabledSpecialCards", enabledArr)

        obj.put("selectedSquare", state.selectedSquare?.let(::encodeSquare) ?: JSONObject.NULL)
        obj.put("selectedRevivePieceId", state.selectedRevivePieceId ?: JSONObject.NULL)

        val validReviveArr = JSONArray()
        state.validReviveSquares.forEach { validReviveArr.put(encodeSquare(it)) }
        obj.put("validReviveSquares", validReviveArr)

        obj.put("capturedWhite", encodePieces(state.capturedPieces[PieceColor.WHITE].orEmpty()))
        obj.put("capturedBlack", encodePieces(state.capturedPieces[PieceColor.BLACK].orEmpty()))

        obj.put("lastMoveFrom", state.lastMoveFrom?.let(::encodeSquare) ?: JSONObject.NULL)
        obj.put("lastMoveTo", state.lastMoveTo?.let(::encodeSquare) ?: JSONObject.NULL)
        obj.put("lastRevivedSquare", state.lastRevivedSquare?.let(::encodeSquare) ?: JSONObject.NULL)
        obj.put("checkedKingSquare", state.checkedKingSquare?.let(::encodeSquare) ?: JSONObject.NULL)

        obj.put(
            "pendingPromotion",
            state.pendingPromotion?.let { promo ->
                JSONObject().apply {
                    put("from", encodeSquare(promo.from))
                    put("to", encodeSquare(promo.to))
                }
            } ?: JSONObject.NULL
        )

        obj.put(
            "noticeBanner",
            state.noticeBanner?.let { banner ->
                JSONObject().apply {
                    put("id", banner.id)
                    put("message", banner.message)
                    put("isAlert", banner.isAlert)
                }
            } ?: JSONObject.NULL
        )

        obj.put(
            "moveAnim",
            state.moveAnimationEvent?.let { ev ->
                JSONObject().apply {
                    put("id", ev.id)
                    put("piece", encodePiece(ev.piece))
                    put("from", encodeSquare(ev.from))
                    put("to", encodeSquare(ev.to))
                    put("isCapture", ev.isCapture)
                    put("secPiece", ev.secondaryPiece?.let(::encodePiece) ?: JSONObject.NULL)
                    put("secFrom", ev.secondaryFrom?.let(::encodeSquare) ?: JSONObject.NULL)
                    put("secTo", ev.secondaryTo?.let(::encodeSquare) ?: JSONObject.NULL)
                }
            } ?: JSONObject.NULL
        )

        obj.put(
            "reviveAnim",
            state.reviveAnimationEvent?.let { ev ->
                JSONObject().apply {
                    put("id", ev.id)
                    put("piece", encodePiece(ev.piece))
                    put("square", encodeSquare(ev.square))
                }
            } ?: JSONObject.NULL
        )

        val logArr = JSONArray()
        state.gameLog.take(30).forEach { entry ->
            logArr.put(
                JSONObject().apply {
                    put("id", entry.id)
                    put("msg", entry.message)
                    put("cat", entry.category.name)
                }
            )
        }
        obj.put("log", logArr)

        obj.put(
            "outcome",
            when (val o = state.outcome) {
                null -> JSONObject.NULL
                is GameOutcome.Checkmate -> JSONObject().apply {
                    put("kind", "CHECKMATE")
                    put("player", o.winnerPlayer.name)
                    put("color", o.winnerColor.name)
                }
                is GameOutcome.Forfeit -> JSONObject().apply {
                    put("kind", "FORFEIT")
                    put("forfeiter", o.forfeitingPlayer.name)
                    put("player", o.winnerPlayer.name)
                    put("color", o.winnerColor.name)
                }
                is GameOutcome.Suicide -> JSONObject().apply {
                    put("kind", "SUICIDE")
                    put("loser", o.losingPlayer.name)
                    put("player", o.winnerPlayer.name)
                    put("color", o.winnerColor.name)
                }
                GameOutcome.Stalemate -> JSONObject().apply {
                    put("kind", "STALEMATE")
                }
            }
        )

        obj.put(
            "castling",
            JSONObject().apply {
                put("wk", state.castlingRights.whiteKingside)
                put("wq", state.castlingRights.whiteQueenside)
                put("bk", state.castlingRights.blackKingside)
                put("bq", state.castlingRights.blackQueenside)
            }
        )
        obj.put("epTarget", state.enPassantTarget?.let(::encodeSquare) ?: JSONObject.NULL)
        obj.put("epOwner", state.enPassantOwner?.name ?: JSONObject.NULL)
        obj.put("pityP1", state.movePityCount[PlayerId.PLAYER_1] ?: 0)
        obj.put("pityP2", state.movePityCount[PlayerId.PLAYER_2] ?: 0)

        return "STATE:$obj"
    }

    fun decodeState(line: String): GameUiState? {
        if (!line.startsWith("STATE:")) return null
        return runCatching {
            val obj = JSONObject(line.removePrefix("STATE:"))
            val boardArr = obj.getJSONArray("board")
            val board = List(8) { r ->
                val rowArr = boardArr.getJSONArray(r)
                List(8) { c ->
                    if (rowArr.isNull(c)) null else decodePiece(rowArr.getJSONObject(c))
                }
            }

            val playerColors = mapOf(
                PlayerId.PLAYER_1 to PieceColor.valueOf(obj.getString("p1Color")),
                PlayerId.PLAYER_2 to PieceColor.valueOf(obj.getString("p2Color"))
            )
            val activePlayer = PlayerId.valueOf(obj.getString("activePlayer"))
            val castlingObj = obj.getJSONObject("castling")
            val castlingRights = CastlingRights(
                whiteKingside = castlingObj.getBoolean("wk"),
                whiteQueenside = castlingObj.getBoolean("wq"),
                blackKingside = castlingObj.getBoolean("bk"),
                blackQueenside = castlingObj.getBoolean("bq")
            )
            val epTarget = obj.optJSONObject("epTarget")?.let(::decodeSquare)
            val epOwner = if (obj.isNull("epOwner")) null else PieceColor.valueOf(obj.getString("epOwner"))

            val majorRevivedThisTurn = obj.optBoolean("majorRevivedThisTurn", false)
            val movedPieceIds = mutableSetOf<Int>()
            val movedIdsArr = obj.optJSONArray("movedPieceIds")
            if (movedIdsArr != null) {
                for (i in 0 until movedIdsArr.length()) {
                    movedPieceIds.add(movedIdsArr.getInt(i))
                }
            }
            val frozenPieceId = if (obj.isNull("frozenPieceId") || !obj.has("frozenPieceId")) null else obj.getInt("frozenPieceId")
            val frozenOwner = if (obj.isNull("frozenOwner") || !obj.has("frozenOwner")) {
                null
            } else {
                PieceColor.valueOf(obj.getString("frozenOwner"))
            }
            val validFreezeSet = mutableSetOf<Square>()
            val freezeArr = obj.optJSONArray("validFreezeSquares")
            if (freezeArr != null) {
                for (i in 0 until freezeArr.length()) {
                    validFreezeSet.add(decodeSquare(freezeArr.getJSONObject(i)))
                }
            }

            val selectedMergeList = mutableListOf<Square>()
            val mergeArr = obj.optJSONArray("selectedMergeSquares")
            if (mergeArr != null) {
                for (i in 0 until mergeArr.length()) {
                    selectedMergeList.add(decodeSquare(mergeArr.getJSONObject(i)))
                }
            }

            val selectedSacrificeSquare = obj.optJSONObject("selectedSacrificeSquare")?.let(::decodeSquare)
            val validSacrificeSet = mutableSetOf<Square>()
            val sacArr = obj.optJSONArray("validSacrificeSquares")
            if (sacArr != null) {
                for (i in 0 until sacArr.length()) {
                    validSacrificeSet.add(decodeSquare(sacArr.getJSONObject(i)))
                }
            }

            val selectedPromotePawnSquare = obj.optJSONObject("selectedPromotePawnSquare")?.let(::decodeSquare)
            val validPromotePawnSet = mutableSetOf<Square>()
            val promoteArr = obj.optJSONArray("validPromotePawnSquares")
            if (promoteArr != null) {
                for (i in 0 until promoteArr.length()) {
                    validPromotePawnSet.add(decodeSquare(promoteArr.getJSONObject(i)))
                }
            }

            val validSuicideSet = mutableSetOf<Square>()
            val suicideArr = obj.optJSONArray("validSuicideSquares")
            if (suicideArr != null) {
                for (i in 0 until suicideArr.length()) {
                    validSuicideSet.add(decodeSquare(suicideArr.getJSONObject(i)))
                }
            }

            val shieldedPieceId = if (obj.isNull("shieldedPieceId") || !obj.has("shieldedPieceId")) null else obj.getInt("shieldedPieceId")
            val shieldedOwner = if (obj.isNull("shieldedOwner") || !obj.has("shieldedOwner")) {
                null
            } else {
                PieceColor.valueOf(obj.getString("shieldedOwner"))
            }
            val validShieldSet = mutableSetOf<Square>()
            val shieldArr = obj.optJSONArray("validShieldSquares")
            if (shieldArr != null) {
                for (i in 0 until shieldArr.length()) {
                    validShieldSet.add(decodeSquare(shieldArr.getJSONObject(i)))
                }
            }

            val selectedSwapSquare = obj.optJSONObject("selectedSwapSquare")?.let(::decodeSquare)
            val validSwapSet = mutableSetOf<Square>()
            val swapArr = obj.optJSONArray("validSwapSquares")
            if (swapArr != null) {
                for (i in 0 until swapArr.length()) {
                    validSwapSet.add(decodeSquare(swapArr.getJSONObject(i)))
                }
            }

            val selectedCloneSourceSquare = obj.optJSONObject("selectedCloneSourceSquare")?.let(::decodeSquare)
            val validCloneSet = mutableSetOf<Square>()
            val cloneArr = obj.optJSONArray("validCloneSquares")
            if (cloneArr != null) {
                for (i in 0 until cloneArr.length()) {
                    validCloneSet.add(decodeSquare(cloneArr.getJSONObject(i)))
                }
            }

            val availableWildEffects = mutableListOf<SpecialCardKey>()
            val wildArr = obj.optJSONArray("availableWildEffects")
            if (wildArr != null) {
                for (i in 0 until wildArr.length()) {
                    runCatching { SpecialCardKey.valueOf(wildArr.getString(i)) }
                        .onSuccess { availableWildEffects.add(it) }
                }
            }

            val enabledSpecialCards = if (obj.has("enabledSpecialCards")) {
                val arr = obj.getJSONArray("enabledSpecialCards")
                val set = mutableSetOf<SpecialCardKey>()
                for (i in 0 until arr.length()) {
                    runCatching { SpecialCardKey.valueOf(arr.getString(i)) }
                        .onSuccess { set.add(it) }
                }
                set
            } else {
                SpecialCardKey.entries.toSet()
            }

            val phase = TurnPhase.valueOf(obj.getString("phase"))
            val activeColor = playerColors.getValue(activePlayer)
            val excludedForSelected = buildSet {
                addAll(movedPieceIds)
                if (frozenPieceId != null && frozenOwner == activeColor) {
                    add(frozenPieceId)
                }
            }
            val protectedOpponentIds = if (shieldedPieceId != null && shieldedOwner == activeColor.opponent()) {
                setOf(shieldedPieceId)
            } else {
                emptySet()
            }
            val protectedOwnIds = if (shieldedPieceId != null && shieldedOwner == activeColor) {
                setOf(shieldedPieceId)
            } else {
                emptySet()
            }

            val selectedSquare = obj.optJSONObject("selectedSquare")?.let(::decodeSquare)
            val legalMoves = if (selectedSquare != null) {
                if (phase == TurnPhase.HIJACKING) {
                    val oppExcluded = if (frozenPieceId != null && frozenOwner == activeColor.opponent()) {
                        setOf(frozenPieceId)
                    } else {
                        emptySet()
                    }
                    com.example.engine.ChessEngine.getHijackMovesForSquare(
                        board = board,
                        from = selectedSquare,
                        activeColor = activeColor,
                        castlingRights = castlingRights,
                        enPassantTarget = epTarget,
                        enPassantOwner = epOwner,
                        excludedPieceIds = oppExcluded,
                        protectedPieceIds = protectedOwnIds
                    )
                } else {
                    com.example.engine.ChessEngine.getLegalMovesForSquare(
                        board = board,
                        from = selectedSquare,
                        castlingRights = castlingRights,
                        enPassantTarget = epTarget,
                        enPassantOwner = epOwner,
                        excludedPieceIds = excludedForSelected,
                        protectedPieceIds = protectedOpponentIds
                    )
                }
            } else {
                emptyList()
            }

            val validReviveSet = mutableSetOf<Square>()
            val reviveArr = obj.getJSONArray("validReviveSquares")
            for (i in 0 until reviveArr.length()) {
                validReviveSet.add(decodeSquare(reviveArr.getJSONObject(i)))
            }

            val logArr = obj.getJSONArray("log")
            val logs = List(logArr.length()) { i ->
                val e = logArr.getJSONObject(i)
                LogEntry(
                    id = e.getInt("id"),
                    message = e.getString("msg"),
                    category = LogCategory.valueOf(e.getString("cat"))
                )
            }

            val outcomeObj = obj.optJSONObject("outcome")
            val outcome: GameOutcome? = when (outcomeObj?.optString("kind")) {
                "CHECKMATE" -> GameOutcome.Checkmate(
                    winnerPlayer = PlayerId.valueOf(outcomeObj.getString("player")),
                    winnerColor = PieceColor.valueOf(outcomeObj.getString("color"))
                )
                "FORFEIT" -> GameOutcome.Forfeit(
                    forfeitingPlayer = PlayerId.valueOf(outcomeObj.getString("forfeiter")),
                    winnerPlayer = PlayerId.valueOf(outcomeObj.getString("player")),
                    winnerColor = PieceColor.valueOf(outcomeObj.getString("color"))
                )
                "SUICIDE" -> GameOutcome.Suicide(
                    losingPlayer = PlayerId.valueOf(outcomeObj.getString("loser")),
                    winnerPlayer = PlayerId.valueOf(outcomeObj.getString("player")),
                    winnerColor = PieceColor.valueOf(outcomeObj.getString("color"))
                )
                "STALEMATE" -> GameOutcome.Stalemate
                else -> null
            }

            val promoObj = obj.optJSONObject("pendingPromotion")
            val pendingPromotion = promoObj?.let {
                PendingPromotion(
                    from = decodeSquare(it.getJSONObject("from")),
                    to = decodeSquare(it.getJSONObject("to"))
                )
            }

            val noticeObj = obj.optJSONObject("noticeBanner")
            val noticeBanner = noticeObj?.let {
                NoticeBanner(
                    id = it.getInt("id"),
                    message = it.getString("message"),
                    isAlert = it.getBoolean("isAlert")
                )
            }

            val moveAnimObj = obj.optJSONObject("moveAnim")
            val moveAnim = moveAnimObj?.let {
                MoveAnimationEvent(
                    id = it.getInt("id"),
                    piece = decodePiece(it.getJSONObject("piece")),
                    from = decodeSquare(it.getJSONObject("from")),
                    to = decodeSquare(it.getJSONObject("to")),
                    isCapture = it.getBoolean("isCapture"),
                    secondaryPiece = it.optJSONObject("secPiece")?.let(::decodePiece),
                    secondaryFrom = it.optJSONObject("secFrom")?.let(::decodeSquare),
                    secondaryTo = it.optJSONObject("secTo")?.let(::decodeSquare)
                )
            }

            val reviveAnimObj = obj.optJSONObject("reviveAnim")
            val reviveAnim = reviveAnimObj?.let {
                ReviveAnimationEvent(
                    id = it.getInt("id"),
                    piece = decodePiece(it.getJSONObject("piece")),
                    square = decodeSquare(it.getJSONObject("square"))
                )
            }

            GameUiState(
                board = board,
                playerColors = playerColors,
                activePlayer = activePlayer,
                boardFlipped = obj.getBoolean("boardFlipped"),
                phase = TurnPhase.valueOf(obj.getString("phase")),
                drawPile = decodeCards(obj.getJSONArray("drawPile")),
                discardPile = decodeCards(obj.getJSONArray("discardPile")),
                activeCard = obj.optJSONObject("activeCard")?.let(::decodeCard),
                drawRevision = obj.getInt("drawRevision"),
                totalMovesForCard = obj.getInt("totalMovesForCard"),
                movesRemaining = obj.getInt("movesRemaining"),
                revivesTotalForCard = obj.getInt("revivesTotalForCard"),
                revivesRemaining = obj.getInt("revivesRemaining"),
                majorRevivedThisTurn = majorRevivedThisTurn,
                movedPieceIdsThisTurn = movedPieceIds,
                frozenPieceId = frozenPieceId,
                frozenOwner = frozenOwner,
                validFreezeSquares = validFreezeSet,
                shieldedPieceId = shieldedPieceId,
                shieldedOwner = shieldedOwner,
                validShieldSquares = validShieldSet,
                selectedMergeSquares = selectedMergeList,
                selectedSacrificeSquare = selectedSacrificeSquare,
                validSacrificeSquares = validSacrificeSet,
                selectedPromotePawnSquare = selectedPromotePawnSquare,
                validPromotePawnSquares = validPromotePawnSet,
                validSuicideSquares = validSuicideSet,
                selectedSwapSquare = selectedSwapSquare,
                validSwapSquares = validSwapSet,
                selectedCloneSourceSquare = selectedCloneSourceSquare,
                validCloneSquares = validCloneSet,
                availableWildEffects = availableWildEffects,
                enabledSpecialCards = enabledSpecialCards,
                selectedSquare = selectedSquare,
                legalMovesForSelected = legalMoves,
                selectedRevivePieceId = if (obj.isNull("selectedRevivePieceId")) null else obj.getInt("selectedRevivePieceId"),
                validReviveSquares = validReviveSet,
                capturedPieces = mapOf(
                    PieceColor.WHITE to decodePieces(obj.getJSONArray("capturedWhite")),
                    PieceColor.BLACK to decodePieces(obj.getJSONArray("capturedBlack"))
                ),
                lastMoveFrom = obj.optJSONObject("lastMoveFrom")?.let(::decodeSquare),
                lastMoveTo = obj.optJSONObject("lastMoveTo")?.let(::decodeSquare),
                lastRevivedSquare = obj.optJSONObject("lastRevivedSquare")?.let(::decodeSquare),
                checkedKingSquare = obj.optJSONObject("checkedKingSquare")?.let(::decodeSquare),
                pendingPromotion = pendingPromotion,
                noticeBanner = noticeBanner,
                moveAnimationEvent = moveAnim,
                reviveAnimationEvent = reviveAnim,
                gameLog = logs,
                outcome = outcome,
                castlingRights = castlingRights,
                enPassantTarget = epTarget,
                enPassantOwner = epOwner,
                movePityCount = mapOf(
                    PlayerId.PLAYER_1 to obj.optInt("pityP1", 0),
                    PlayerId.PLAYER_2 to obj.optInt("pityP2", 0)
                )
            )
        }.getOrNull()
    }

    private fun encodePiece(piece: ChessPiece): JSONObject = JSONObject().apply {
        put("id", piece.id)
        put("t", piece.type.name)
        put("c", piece.color.name)
    }

    private fun decodePiece(obj: JSONObject): ChessPiece = ChessPiece(
        id = obj.getInt("id"),
        type = PieceType.valueOf(obj.getString("t")),
        color = PieceColor.valueOf(obj.getString("c"))
    )

    private fun encodePieces(list: List<ChessPiece>): JSONArray = JSONArray().apply {
        list.forEach { put(encodePiece(it)) }
    }

    private fun decodePieces(arr: JSONArray): List<ChessPiece> =
        List(arr.length()) { decodePiece(arr.getJSONObject(it)) }

    private fun encodeSquare(sq: Square): JSONObject = JSONObject().apply {
        put("r", sq.row)
        put("c", sq.col)
    }

    private fun decodeSquare(obj: JSONObject): Square = Square(obj.getInt("r"), obj.getInt("c"))

    private fun encodeCard(card: UnoCard): JSONObject = JSONObject().apply {
        put("id", card.id)
        put("col", card.color.name)
        when (val t = card.type) {
            is UnoCardType.NumberCard -> {
                put("tk", "NUM")
                put("m", t.moves)
            }
            UnoCardType.RevivePlusTwo -> put("tk", "REVIVE")
            UnoCardType.Freeze -> put("tk", "FREEZE")
            UnoCardType.Merge -> put("tk", "MERGE")
            UnoCardType.Sacrifice -> put("tk", "SACRIFICE")
            UnoCardType.Hijack -> put("tk", "HIJACK")
            UnoCardType.PawnPromote -> put("tk", "PAWN_PROMOTE")
            UnoCardType.Suicide -> put("tk", "SUICIDE")
            UnoCardType.Shield -> put("tk", "SHIELD")
            UnoCardType.SwapPositions -> put("tk", "SWAP")
            UnoCardType.Clone -> put("tk", "CLONE")
            UnoCardType.Wild -> put("tk", "WILD")
        }
    }

    private fun decodeCard(obj: JSONObject): UnoCard {
        val type = when (obj.getString("tk")) {
            "NUM" -> UnoCardType.NumberCard(obj.getInt("m"))
            "REVIVE" -> UnoCardType.RevivePlusTwo
            "FREEZE" -> UnoCardType.Freeze
            "MERGE" -> UnoCardType.Merge
            "SACRIFICE" -> UnoCardType.Sacrifice
            "HIJACK" -> UnoCardType.Hijack
            "PAWN_PROMOTE" -> UnoCardType.PawnPromote
            "SUICIDE" -> UnoCardType.Suicide
            "SHIELD" -> UnoCardType.Shield
            "SWAP" -> UnoCardType.SwapPositions
            "CLONE" -> UnoCardType.Clone
            "WILD" -> UnoCardType.Wild
            else -> UnoCardType.NumberCard(1)
        }
        return UnoCard(
            id = obj.getInt("id"),
            color = UnoColor.valueOf(obj.getString("col")),
            type = type
        )
    }

    private fun encodeCards(list: List<UnoCard>): JSONArray = JSONArray().apply {
        list.forEach { put(encodeCard(it)) }
    }

    private fun decodeCards(arr: JSONArray): List<UnoCard> =
        List(arr.length()) { decodeCard(arr.getJSONObject(it)) }
}
