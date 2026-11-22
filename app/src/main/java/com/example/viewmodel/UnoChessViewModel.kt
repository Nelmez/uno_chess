package com.example.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SoundEffect
import com.example.audio.SoundEventDetector
import com.example.audio.SoundManager
import com.example.audio.SoundPlayer
import com.example.engine.ChessEngine
import com.example.engine.MatchStatsManager
import com.example.engine.UnoChessAi
import com.example.engine.UnoDeck
import com.example.model.AiDifficulty
import com.example.model.CastlingRights
import com.example.model.ChessMove
import com.example.model.ChessPiece
import com.example.model.GameOutcome
import com.example.model.GameUiState
import com.example.model.LogCategory
import com.example.model.LogEntry
import com.example.model.MatchStats
import com.example.model.MoveAnimationEvent
import com.example.model.NoticeBanner
import com.example.model.OpponentMode
import com.example.model.PendingPromotion
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.PlayerId
import com.example.model.ReviveAnimationEvent
import com.example.model.SpecialCardKey
import com.example.model.Square
import com.example.model.Trap
import com.example.model.TurnPhase
import com.example.model.UnoCard
import com.example.model.UnoCardTheme
import com.example.model.UnoCardType
import com.example.model.grantsMove
import com.example.model.isNumberCard
import com.example.network.FirestoreOnlineSession
import com.example.network.InMemoryOnlineSession
import com.example.network.LanAction
import com.example.network.LanSessionManager
import com.example.network.LanSessionState
import com.example.network.LanStateCodec
import com.example.network.NetworkRole
import com.example.network.OnlineMatchSession
import com.example.network.OnlineRoomRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class UnoChessViewModel(
    private val random: Random = Random.Default,
    private val aiStepDelayMs: Long = 460L,
    onlineRooms: OnlineRoomRegistry? = null,
    private val initialDeckProvider: (() -> List<UnoCard>)? = null
) : ViewModel() {

    private companion object {
        const val PREFS_NAME = "uno_chess_prefs"
        const val KEY_DARK_THEME = "dark_theme"
        const val KEY_CARD_THEME = "card_theme"
        const val KEY_SOUND_ENABLED = "sound_enabled"
    }

    private val stateLock = Any()
    private var appContext: Context? = null
    private var soundPlayer: SoundPlayer? = null
    private var nextLogId = 1
    private var nextNoticeId = 1
    private var nextAnimId = 1
    private var matchRecorded = false
    private var turnsPlayedInMatch = 0
    private var aiTurnJob: Job? = null

    private val rawUiState = MutableStateFlow(createInitialState())
    private val _uiState: MutableStateFlow<GameUiState> = ObservingMutableStateFlow(rawUiState) { prev, next ->
        dispatchSoundEffects(prev, next)
    }
    val uiState: StateFlow<GameUiState> = rawUiState.asStateFlow()

    private val _opponentMode = MutableStateFlow<OpponentMode>(OpponentMode.PassAndPlay)
    val opponentMode: StateFlow<OpponentMode> = _opponentMode.asStateFlow()

    private val _isAiThinking = MutableStateFlow(false)
    val isAiThinking: StateFlow<Boolean> = _isAiThinking.asStateFlow()

    private val _cardTheme = MutableStateFlow(UnoCardTheme.CLASSIC)
    val cardTheme: StateFlow<UnoCardTheme> = _cardTheme.asStateFlow()

    private val _isDarkTheme = MutableStateFlow<Boolean?>(null)
    val isDarkTheme: StateFlow<Boolean?> = _isDarkTheme.asStateFlow()

    private val _soundEnabled = MutableStateFlow(true)
    val soundEnabled: StateFlow<Boolean> = _soundEnabled.asStateFlow()

    private val _matchStats = MutableStateFlow(MatchStats())
    val matchStats: StateFlow<MatchStats> = _matchStats.asStateFlow()

    private val _hostEnabledSpecialCards = MutableStateFlow(SpecialCardKey.entries.toSet())
    val hostEnabledSpecialCards: StateFlow<Set<SpecialCardKey>> = _hostEnabledSpecialCards.asStateFlow()

    private val lanManager = LanSessionManager(
        onMessageReceived = ::handleIncomingLanMessage,
        onGuestConnected = ::handleGuestConnected
    )
    private val onlineSession: OnlineMatchSession = if (onlineRooms != null) {
        InMemoryOnlineSession(
            registry = onlineRooms,
            onMessageReceived = ::handleIncomingLanMessage,
            onGuestConnected = ::handleGuestConnected
        )
    } else {
        FirestoreOnlineSession(
            onMessageReceived = ::handleIncomingLanMessage,
            onGuestConnected = ::handleGuestConnected,
            contextProvider = { appContext }
        )
    }
    val lanState: StateFlow<LanSessionState> = MergedSessionState(
        lan = lanManager.sessionState,
        online = onlineSession.sessionState
    )

    fun toggleHostSpecialCard(key: SpecialCardKey) {
        val current = _hostEnabledSpecialCards.value
        _hostEnabledSpecialCards.value = if (key in current) current - key else current + key
    }

    fun setAllHostSpecialCards(enabled: Boolean) {
        _hostEnabledSpecialCards.value = if (enabled) SpecialCardKey.entries.toSet() else emptySet()
    }

    fun loadPreferences(context: Context) {
        val applicationContext = context.applicationContext
        appContext = applicationContext
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_DARK_THEME)) {
            _isDarkTheme.value = prefs.getBoolean(KEY_DARK_THEME, false)
        }
        val savedTheme = prefs.getString(KEY_CARD_THEME, null)
        if (savedTheme != null) {
            runCatching { UnoCardTheme.valueOf(savedTheme) }
                .onSuccess { _cardTheme.value = it }
        }
        val enabled = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        _soundEnabled.value = enabled
        if (soundPlayer == null) {
            soundPlayer = SoundManager(applicationContext).apply {
                isEnabled = enabled
                initialize()
            }
        } else {
            soundPlayer?.isEnabled = enabled
        }
        _matchStats.value = MatchStatsManager.load(context)
    }

    internal fun setSoundPlayerForTest(player: SoundPlayer) {
        soundPlayer = player.apply { isEnabled = _soundEnabled.value }
    }

    private fun dispatchSoundEffects(prev: GameUiState, next: GameUiState) {
        val player = soundPlayer ?: return
        if (!_soundEnabled.value) return
        val effects = SoundEventDetector.detect(prev, next)
        if (effects.isNotEmpty()) {
            player.playSequence(effects)
        }
    }

    fun clearMatchHistory() {
        val empty = MatchStats()
        _matchStats.value = empty
        appContext?.let { MatchStatsManager.save(it, empty) }
    }

    private fun recordMatchOutcomeIfNeeded(outcome: GameOutcome) {
        if (matchRecorded) return
        matchRecorded = true
        val updated = MatchStatsManager.recordOutcome(
            current = _matchStats.value,
            outcome = outcome,
            opponentMode = _opponentMode.value,
            isLanConnected = snapshotSession().isLanConnected,
            isOnline = snapshotSession().role.isOnline,
            totalTurns = turnsPlayedInMatch.coerceAtLeast(1)
        )
        _matchStats.value = updated
        appContext?.let { MatchStatsManager.save(it, updated) }
    }

    fun setDarkTheme(context: Context, isDark: Boolean) {
        _isDarkTheme.value = isDark
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK_THEME, isDark)
            .apply()
    }

    fun setCardTheme(context: Context, theme: UnoCardTheme) {
        _cardTheme.value = theme
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CARD_THEME, theme.name)
            .apply()
    }

    fun setSoundEnabled(context: Context, enabled: Boolean) {
        _soundEnabled.value = enabled
        soundPlayer?.isEnabled = enabled
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SOUND_ENABLED, enabled)
            .apply()
        if (enabled) {
            soundPlayer?.play(SoundEffect.CARD_DRAW)
        }
    }

    fun setOpponentMode(mode: OpponentMode) {
        cancelAiTurn()
        if (mode is OpponentMode.VsAi && snapshotSession().role != NetworkRole.PASS_AND_PLAY) {
            leaveLanMatch()
        }
        _opponentMode.value = mode
        synchronized(stateLock) {
            val msg = when (mode) {
                OpponentMode.PassAndPlay ->
                    "Pass & Play started — Player 1 (White) draws first."
                is OpponentMode.VsAi ->
                    "Match vs AI (${mode.difficulty.displayName}) — Player 1 (White) draws first."
            }
            _uiState.value = createInitialState(initialMessage = msg)
            broadcastStateIfHost()
        }
    }

    fun refreshLocalIp() {
        lanManager.refreshLocalIp()
    }

    fun startLanHost(context: Context? = null, preferredPort: Int = 0) {
        cancelAiTurn()
        _opponentMode.value = OpponentMode.PassAndPlay
        onlineSession.leave()
        lanManager.startHosting(context = context, preferredPort = preferredPort)
    }

    fun startLanDiscovery(context: Context) {
        lanManager.startDiscovery(context)
    }

    fun stopLanDiscovery() {
        lanManager.stopNsdDiscovery()
    }

    fun joinLanHost(hostIp: String, port: Int) {
        cancelAiTurn()
        _opponentMode.value = OpponentMode.PassAndPlay
        onlineSession.leave()
        lanManager.connectToHost(hostIp, port)
    }

    fun createOnlineLobby(displayName: String) {
        cancelAiTurn()
        _opponentMode.value = OpponentMode.PassAndPlay
        lanManager.disconnectToPassAndPlay()
        onlineSession.createLobby(displayName)
    }

    fun joinOnlineLobby(code: String, displayName: String) {
        cancelAiTurn()
        _opponentMode.value = OpponentMode.PassAndPlay
        lanManager.disconnectToPassAndPlay()
        onlineSession.joinLobby(code, displayName)
    }

    fun startOnlineLobbyListing() {
        onlineSession.startWatchingLobbies()
    }

    fun refreshOnlineLobbies() {
        onlineSession.refreshLobbies()
    }

    fun stopOnlineLobbyListing() {
        onlineSession.stopWatchingLobbies()
    }

    fun leaveLanMatch() {
        lanManager.disconnectToPassAndPlay()
        onlineSession.leave()
    }

    override fun onCleared() {
        cancelAiTurn()
        lanManager.shutdown()
        onlineSession.shutdown()
        soundPlayer?.release()
        soundPlayer = null
        super.onCleared()
    }

    private fun snapshotSession(): LanSessionState {
        val online = onlineSession.sessionState.value
        val lan = lanManager.sessionState.value
        val active = if (online.role.isOnline) online else lan
        return active.copy(
            openLobbies = online.openLobbies,
            isListingLobbies = online.isListingLobbies,
            onlineErrorMessage = online.onlineErrorMessage ?: online.errorMessage.takeIf { online.role.isOnline },
            isOnlineServerReachable = online.isOnlineServerReachable
        )
    }

    private fun linkName(): String = if (snapshotSession().role.isOnline) "Online" else "LAN"

    private fun sendActiveLine(line: String) {
        if (onlineSession.sessionState.value.role.isOnline) {
            onlineSession.sendLine(line)
        } else {
            lanManager.sendLine(line)
        }
    }

    private fun cancelAiTurn() {
        aiTurnJob?.cancel()
        aiTurnJob = null
        _isAiThinking.value = false
    }

    private fun actorName(player: PlayerId): String {
        val mode = _opponentMode.value
        return if (
            player == PlayerId.PLAYER_2 &&
            mode is OpponentMode.VsAi &&
            snapshotSession().role == NetworkRole.PASS_AND_PLAY
        ) {
            "AI (${mode.difficulty.displayName})"
        } else {
            player.displayName
        }
    }

    private fun isAiTurnNow(): Boolean {
        val mode = _opponentMode.value
        val state = _uiState.value
        return snapshotSession().role == NetworkRole.PASS_AND_PLAY &&
            mode is OpponentMode.VsAi &&
            state.activePlayer == PlayerId.PLAYER_2 &&
            state.outcome == null
    }

    private fun scheduleAiTurnIfNeeded() {
        if (!isAiTurnNow()) return
        if (aiTurnJob?.isActive == true) return
        val mode = _opponentMode.value as? OpponentMode.VsAi ?: return

        aiTurnJob = viewModelScope.launch {
            _isAiThinking.value = true
            try {
                var safety = 0
                while (safety < 16 && isAiTurnNow()) {
                    safety++
                    delay(aiStepDelayMs)
                    if (!isAiTurnNow()) break
                    val progressed = withContext(Dispatchers.Default) {
                        performSingleAiStep(mode.difficulty)
                    }
                    if (!progressed) break
                }
            } finally {
                _isAiThinking.value = false
                aiTurnJob = null
            }
        }
    }

    /**
     * Deterministic synchronous AI turn execution for unit tests.
     */
    internal fun runAiTurnSynchronouslyForTest(): Int {
        val mode = _opponentMode.value as? OpponentMode.VsAi ?: return 0
        var steps = 0
        while (steps < 16 && isAiTurnNow()) {
            if (!performSingleAiStep(mode.difficulty)) break
            steps++
        }
        return steps
    }

    private fun performSingleAiStep(difficulty: AiDifficulty): Boolean {
        synchronized(stateLock) {
            val current = _uiState.value
            if (current.outcome != null || current.activePlayer != PlayerId.PLAYER_2) return false

            return when (current.phase) {
                TurnPhase.AWAITING_DRAW -> {
                    performDrawCard()
                    true
                }

                TurnPhase.FREEZING -> {
                    val target = UnoChessAi.chooseFreezeTarget(current, difficulty, random)
                        ?: return false
                    handleFreezingSquareTap(current, target)
                    true
                }

                TurnPhase.REVIVING -> {
                    val decision = UnoChessAi.chooseRevive(current, difficulty, random)
                        ?: return false
                    if (decision.pieceId != -1) {
                        performCapturedPieceSelected(decision.pieceId)
                    }
                    val refreshed = _uiState.value
                    handleRevivingSquareTap(refreshed, decision.square)
                    true
                }

                TurnPhase.MERGING -> {
                    val decision = UnoChessAi.chooseTrade(current, difficulty, random)
                    if (decision != null) {
                        _uiState.value = current.copy(selectedMergeSquares = decision.selectedSquares)
                        performConfirmMerge(decision.targetType)
                    } else {
                        performSkipMerge()
                    }
                    true
                }

                TurnPhase.SACRIFICING -> {
                    val decision = UnoChessAi.chooseSacrifice(current, difficulty, random)
                    if (decision != null) {
                        _uiState.value = current.copy(selectedSacrificeSquare = decision.square)
                        performConfirmSacrifice(decision.recipeId)
                    } else {
                        performSkipSacrifice()
                    }
                    true
                }

                TurnPhase.HIJACKING -> {
                    val chosen = UnoChessAi.chooseHijackMove(current, difficulty, random)
                        ?: return false
                    executeHijackMove(current, chosen)
                    true
                }

                TurnPhase.PROMOTING_PAWN -> {
                    val decision = UnoChessAi.choosePawnPromote(current, difficulty, random)
                        ?: return false
                    _uiState.value = current.copy(selectedPromotePawnSquare = decision.pawnSquare)
                    performConfirmPawnPromote(decision.targetType)
                    true
                }

                TurnPhase.SUICIDING -> {
                    val target = UnoChessAi.chooseSuicideTarget(current, difficulty, random)
                        ?: return false
                    handleSuicidingSquareTap(current, target)
                    true
                }

                TurnPhase.SHIELDING -> {
                    val target = UnoChessAi.chooseShieldTarget(current, difficulty, random)
                        ?: return false
                    handleShieldingSquareTap(current, target)
                    true
                }

                TurnPhase.SWAPPING -> {
                    val decision = UnoChessAi.chooseSwap(current, difficulty, random)
                    if (decision != null) {
                        executeSwap(current, decision.first, decision.second)
                    } else {
                        performSkipSwap()
                    }
                    true
                }

                TurnPhase.CLONING -> {
                    val decision = UnoChessAi.chooseClone(current, difficulty, random)
                    if (decision != null) {
                        executeClone(current, decision.source, decision.target)
                    } else {
                        performSkipClone()
                    }
                    true
                }

                TurnPhase.WILD_CHOOSING -> {
                    val effect = UnoChessAi.chooseWildEffect(current, difficulty, random)
                        ?: return false
                    performSelectWildEffect(effect)
                    true
                }

                TurnPhase.SNIPING -> {
                    val decision = UnoChessAi.chooseSniperAction(current, difficulty, random)
                    if (decision != null) {
                        handleSniperSquareTap(_uiState.value, decision.sniperSquare)
                        handleSniperSquareTap(_uiState.value, decision.targetSquare)
                    } else {
                        performSkipSniper()
                    }
                    true
                }

                TurnPhase.PORTALING -> {
                    val decision = UnoChessAi.choosePortalAction(current, difficulty, random)
                    if (decision != null) {
                        handlePortalSquareTap(_uiState.value, decision.sourceSquare)
                        handlePortalSquareTap(_uiState.value, decision.destSquare)
                    } else {
                        performSkipPortal()
                    }
                    true
                }

                TurnPhase.TRAPPING -> {
                    val sq = UnoChessAi.chooseTrapSquare(current, difficulty, random)
                    if (sq != null) {
                        handleTrapSquareTap(_uiState.value, sq)
                    } else {
                        performSkipTrap()
                    }
                    true
                }

                TurnPhase.DRAW_TWO_CHOOSING -> {
                    val choice = UnoChessAi.chooseDrawTwoCard(current, difficulty, random)
                    performChooseDrawTwoCard(choice)
                    true
                }

                TurnPhase.MOVING -> {
                    val chosen = UnoChessAi.chooseMove(current, difficulty, random)
                        ?: return false
                    executeChessMove(current, chosen)
                    true
                }

                TurnPhase.GAME_OVER -> false
            }
        }
    }

    private fun handleGuestConnected() {
        cancelAiTurn()
        _opponentMode.value = OpponentMode.PassAndPlay
        synchronized(stateLock) {
            val enabledSpecials = _hostEnabledSpecialCards.value
            val deckCount = UnoDeck.deckSizeFor(enabledSpecials)
            val fresh = createInitialState(
                initialMessage = "${linkName()} match connected ($deckCount-card deck) — Player 1 (White) draws first.",
                enabledSpecialCards = enabledSpecials
            )
            _uiState.value = fresh
            broadcastStateIfHost()
        }
    }

    private fun handleIncomingLanMessage(line: String) {
        val role = snapshotSession().role
        if (role.isGuest) {
            val decoded = LanStateCodec.decodeState(line) ?: return
            synchronized(stateLock) {
                val prevOutcome = _uiState.value.outcome
                _uiState.value = decoded
                if (decoded.outcome == null) {
                    matchRecorded = false
                } else if (prevOutcome == null) {
                    recordMatchOutcomeIfNeeded(decoded.outcome)
                }
            }
        } else if (role.isHost) {
            val action = LanStateCodec.decodeAction(line) ?: return
            synchronized(stateLock) {
                val active = _uiState.value.activePlayer
                when (action) {
                    LanAction.ResetGame -> {
                        val enabledSpecials = _hostEnabledSpecialCards.value
                        _uiState.value = createInitialState(
                            initialMessage = "New ${linkName()} match started — Player 1 (White) draws first.",
                            enabledSpecialCards = enabledSpecials
                        )
                        broadcastStateIfHost()
                    }
                    LanAction.Forfeit -> {
                        performForfeit(PlayerId.PLAYER_2)
                        broadcastStateIfHost()
                    }
                    LanAction.DrawCard -> if (active == PlayerId.PLAYER_2) {
                        performDrawCard()
                        broadcastStateIfHost()
                    }
                    is LanAction.TapSquare -> if (active == PlayerId.PLAYER_2) {
                        performSquareTap(action.square)
                        broadcastStateIfHost()
                    }
                    is LanAction.SelectPromotion -> if (active == PlayerId.PLAYER_2) {
                        performPromotionSelected(action.pieceType)
                        broadcastStateIfHost()
                    }
                    LanAction.CancelPromotion -> if (active == PlayerId.PLAYER_2) {
                        performPromotionCancelled()
                        broadcastStateIfHost()
                    }
                    is LanAction.SelectRevivePiece -> if (active == PlayerId.PLAYER_2) {
                        performCapturedPieceSelected(action.pieceId)
                        broadcastStateIfHost()
                    }
                    is LanAction.ConfirmMerge -> if (active == PlayerId.PLAYER_2) {
                        performConfirmMerge(action.targetType)
                        broadcastStateIfHost()
                    }
                    LanAction.ClearMergeSelection -> if (active == PlayerId.PLAYER_2) {
                        performClearMergeSelection()
                        broadcastStateIfHost()
                    }
                    LanAction.SkipMerge -> if (active == PlayerId.PLAYER_2) {
                        performSkipMerge()
                        broadcastStateIfHost()
                    }
                    is LanAction.ConfirmSacrifice -> if (active == PlayerId.PLAYER_2) {
                        performConfirmSacrifice(action.recipeId)
                        broadcastStateIfHost()
                    }
                    LanAction.SkipSacrifice -> if (active == PlayerId.PLAYER_2) {
                        performSkipSacrifice()
                        broadcastStateIfHost()
                    }
                    is LanAction.ConfirmPawnPromote -> if (active == PlayerId.PLAYER_2) {
                        performConfirmPawnPromote(action.targetType)
                        broadcastStateIfHost()
                    }
                    LanAction.ClearSwapSelection -> if (active == PlayerId.PLAYER_2) {
                        performClearSwapSelection()
                        broadcastStateIfHost()
                    }
                    LanAction.SkipSwap -> if (active == PlayerId.PLAYER_2) {
                        performSkipSwap()
                        broadcastStateIfHost()
                    }
                    LanAction.ClearCloneSelection -> if (active == PlayerId.PLAYER_2) {
                        performClearCloneSelection()
                        broadcastStateIfHost()
                    }
                    LanAction.SkipClone -> if (active == PlayerId.PLAYER_2) {
                        performSkipClone()
                        broadcastStateIfHost()
                    }
                    is LanAction.SelectWildEffect -> if (active == PlayerId.PLAYER_2) {
                        performSelectWildEffect(action.effect)
                        broadcastStateIfHost()
                    }
                    is LanAction.ChooseDrawTwo -> if (active == PlayerId.PLAYER_2) {
                        performChooseDrawTwoCard(action.index)
                        broadcastStateIfHost()
                    }
                    LanAction.SkipSniper -> if (active == PlayerId.PLAYER_2) {
                        performSkipSniper()
                        broadcastStateIfHost()
                    }
                    LanAction.SkipPortal -> if (active == PlayerId.PLAYER_2) {
                        performSkipPortal()
                        broadcastStateIfHost()
                    }
                    LanAction.SkipTrap -> if (active == PlayerId.PLAYER_2) {
                        performSkipTrap()
                        broadcastStateIfHost()
                    }
                }
            }
        }
    }

    private fun broadcastStateIfHost() {
        val lan = snapshotSession()
        if (lan.role.isHost && lan.isRemoteConnected) {
            sendActiveLine(LanStateCodec.encodeState(_uiState.value))
        }
    }

    private fun isLocalPlayersTurn(): Boolean {
        val lan = snapshotSession()
        return when (lan.role) {
            NetworkRole.PASS_AND_PLAY -> {
                if (_opponentMode.value is OpponentMode.VsAi) {
                    _uiState.value.activePlayer == PlayerId.PLAYER_1
                } else {
                    true
                }
            }
            NetworkRole.LAN_HOST, NetworkRole.ONLINE_HOST ->
                lan.isRemoteConnected && _uiState.value.activePlayer == PlayerId.PLAYER_1
            NetworkRole.LAN_GUEST, NetworkRole.ONLINE_GUEST ->
                lan.isRemoteConnected && _uiState.value.activePlayer == PlayerId.PLAYER_2
        }
    }

    private fun createInitialState(
        initialMessage: String = "Match started — Player 1 (White) draws first.",
        enabledSpecialCards: Set<SpecialCardKey> = SpecialCardKey.entries.toSet()
    ): GameUiState {
        nextLogId = 1
        nextNoticeId = 1
        nextAnimId = 1
        matchRecorded = false
        turnsPlayedInMatch = 0
        val deck = initialDeckProvider?.invoke() ?: UnoDeck.createShuffledDeck(random, enabledSpecialCards)
        return GameUiState(
            board = ChessEngine.createInitialBoard(),
            playerColors = mapOf(
                PlayerId.PLAYER_1 to PieceColor.WHITE,
                PlayerId.PLAYER_2 to PieceColor.BLACK
            ),
            activePlayer = PlayerId.PLAYER_1,
            boardFlipped = false,
            phase = TurnPhase.AWAITING_DRAW,
            drawPile = deck,
            discardPile = emptyList(),
            activeCard = null,
            drawRevision = 0,
            totalMovesForCard = 0,
            movesRemaining = 0,
            revivesTotalForCard = 0,
            revivesRemaining = 0,
            majorRevivedThisTurn = false,
            movedPieceIdsThisTurn = emptySet(),
            frozenPieceId = null,
            frozenOwner = null,
            validFreezeSquares = emptySet(),
            shieldedPieceId = null,
            shieldedOwner = null,
            validShieldSquares = emptySet(),
            selectedSwapSquare = null,
            validSwapSquares = emptySet(),
            selectedCloneSourceSquare = null,
            validCloneSquares = emptySet(),
            availableWildEffects = emptyList(),
            enabledSpecialCards = enabledSpecialCards,
            selectedMergeSquares = emptyList(),
            selectedSacrificeSquare = null,
            validSacrificeSquares = emptySet(),
            selectedPromotePawnSquare = null,
            validPromotePawnSquares = emptySet(),
            validSuicideSquares = emptySet(),
            selectedSquare = null,
            legalMovesForSelected = emptyList(),
            selectedRevivePieceId = null,
            validReviveSquares = emptySet(),
            capturedPieces = mapOf(
                PieceColor.WHITE to emptyList(),
                PieceColor.BLACK to emptyList()
            ),
            lastMoveFrom = null,
            lastMoveTo = null,
            lastRevivedSquare = null,
            checkedKingSquare = null,
            pendingPromotion = null,
            noticeBanner = null,
            moveAnimationEvent = null,
            reviveAnimationEvent = null,
            gameLog = listOf(
                LogEntry(
                    id = nextLogId++,
                    message = initialMessage,
                    category = LogCategory.SYSTEM
                )
            ),
            outcome = null,
            castlingRights = CastlingRights(),
            enPassantTarget = null,
            enPassantOwner = null,
            movePityCount = mapOf(
                PlayerId.PLAYER_1 to 0,
                PlayerId.PLAYER_2 to 0
            )
        )
    }

    fun forfeitGame() {
        val lan = snapshotSession()
        val current = _uiState.value
        if (current.outcome != null) return

        if (lan.role.isGuest && lan.isRemoteConnected) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.Forfeit))
            return
        }

        cancelAiTurn()
        synchronized(stateLock) {
            val forfeiter = when {
                lan.role.isHost && lan.isRemoteConnected -> PlayerId.PLAYER_1
                _opponentMode.value is OpponentMode.VsAi -> PlayerId.PLAYER_1
                else -> _uiState.value.activePlayer
            }
            performForfeit(forfeiter)
            broadcastStateIfHost()
        }
    }

    private fun performForfeit(forfeitingPlayer: PlayerId) {
        val current = _uiState.value
        if (current.outcome != null) return

        val winnerPlayer = forfeitingPlayer.opponent()
        val winnerColor = current.playerColors.getValue(winnerPlayer)
        val outcome = GameOutcome.Forfeit(
            forfeitingPlayer = forfeitingPlayer,
            winnerPlayer = winnerPlayer,
            winnerColor = winnerColor
        )
        val msg = "${actorName(forfeitingPlayer)} forfeited — ${actorName(winnerPlayer)} (${winnerColor.displayName}) wins!"
        val logs = prependLog(current.gameLog, msg, LogCategory.GAME_OVER)
        _uiState.value = current.copy(
            phase = TurnPhase.GAME_OVER,
            movesRemaining = 0,
            revivesRemaining = 0,
            movedPieceIdsThisTurn = emptySet(),
            frozenPieceId = null,
            frozenOwner = null,
            validFreezeSquares = emptySet(),
            shieldedPieceId = null,
            shieldedOwner = null,
            validShieldSquares = emptySet(),
            selectedSwapSquare = null,
            validSwapSquares = emptySet(),
            selectedCloneSourceSquare = null,
            validCloneSquares = emptySet(),
            availableWildEffects = emptyList(),
            selectedMergeSquares = emptyList(),
            selectedSacrificeSquare = null,
            validSacrificeSquares = emptySet(),
            selectedPromotePawnSquare = null,
            validPromotePawnSquares = emptySet(),
            validSuicideSquares = emptySet(),
            selectedSquare = null,
            legalMovesForSelected = emptyList(),
            selectedRevivePieceId = null,
            validReviveSquares = emptySet(),
            pendingPromotion = null,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = true),
            gameLog = logs,
            outcome = outcome
        )
        recordMatchOutcomeIfNeeded(outcome)
    }

    fun resetGame() {
        val lan = snapshotSession()
        if (lan.role.isGuest && lan.isRemoteConnected) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.ResetGame))
            return
        }
        cancelAiTurn()
        synchronized(stateLock) {
            val mode = _opponentMode.value
            val isHost = lan.role.isHost
            val msg = when {
                isHost ->
                    "New ${linkName()} match started — Player 1 (White) draws first."
                mode is OpponentMode.VsAi ->
                    "Match vs AI (${mode.difficulty.displayName}) — Player 1 (White) draws first."
                else ->
                    "Match started — Player 1 (White) draws first."
            }
            _uiState.value = createInitialState(
                initialMessage = msg,
                enabledSpecialCards = if (isHost) _hostEnabledSpecialCards.value else SpecialCardKey.entries.toSet()
            )
            broadcastStateIfHost()
        }
    }

    fun dismissNotice() {
        synchronized(stateLock) {
            val current = _uiState.value
            if (current.noticeBanner != null) {
                _uiState.value = current.copy(noticeBanner = null)
            }
        }
    }

    internal fun setStateForTest(state: GameUiState) {
        synchronized(stateLock) {
            rawUiState.value = state
            broadcastStateIfHost()
        }
    }

    fun drawCard() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.DrawCard))
            return
        }
        synchronized(stateLock) {
            performDrawCard()
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performDrawCard(forcedCard: UnoCard? = null) {
        val current = _uiState.value
        if (current.phase != TurnPhase.AWAITING_DRAW || current.outcome != null) return

        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val inCheckAtStart = ChessEngine.isKingInCheck(current.board, activeColor)

        var workingFrozenId = current.frozenPieceId
        var workingFrozenOwner = current.frozenOwner
        var logs = current.gameLog
        var notice: NoticeBanner? = null

        // Auto-thaw Freeze if the frozen player starts their turn in check
        if (inCheckAtStart && workingFrozenId != null && workingFrozenOwner == activeColor) {
            workingFrozenId = null
            workingFrozenOwner = null
            logs = prependLog(
                logs,
                "$activeLabel is in check — Freeze thawed!",
                LogCategory.FREEZE
            )
            notice = NoticeBanner(
                id = nextNoticeId++,
                message = "In check! Frozen piece thawed so you can defend.",
                isAlert = true
            )
        }

        val freezeExcluded = if (workingFrozenId != null && workingFrozenOwner == activeColor) {
            setOf(workingFrozenId)
        } else {
            emptySet()
        }

        val protectedEnemyIds = protectedPieceIdsForOwner(
            shieldedPieceId = current.shieldedPieceId,
            shieldedOwner = current.shieldedOwner,
            ownerColor = activeColor.opponent()
        )

        // Check if active player has any legal move at start of turn
        var activeHasMoves = ChessEngine.hasAnyLegalMove(
            board = current.board,
            color = activeColor,
            castlingRights = current.castlingRights,
            enPassantTarget = current.enPassantTarget,
            enPassantOwner = current.enPassantOwner,
            excludedPieceIds = freezeExcluded,
            protectedPieceIds = protectedEnemyIds
        )
        // Never allow Freeze to cause an artificial stalemate if thawing frees a move
        if (!activeHasMoves && freezeExcluded.isNotEmpty()) {
            val hasMovesIfThawed = ChessEngine.hasAnyLegalMove(
                board = current.board,
                color = activeColor,
                castlingRights = current.castlingRights,
                enPassantTarget = current.enPassantTarget,
                enPassantOwner = current.enPassantOwner,
                excludedPieceIds = emptySet(),
                protectedPieceIds = protectedEnemyIds
            )
            if (hasMovesIfThawed) {
                workingFrozenId = null
                workingFrozenOwner = null
                activeHasMoves = true
                logs = prependLog(
                    logs,
                    "Freeze thawed so $activeLabel has a legal move.",
                    LogCategory.FREEZE
                )
            }
        }

        if (!activeHasMoves) {
            val outcome = if (inCheckAtStart) {
                GameOutcome.Checkmate(
                    winnerPlayer = activePlayer.opponent(),
                    winnerColor = activeColor.opponent()
                )
            } else {
                GameOutcome.Stalemate
            }
            val msg = when (outcome) {
                is GameOutcome.Checkmate ->
                    "Checkmate! ${actorName(outcome.winnerPlayer)} (${outcome.winnerColor.displayName}) wins!"
                else -> "Stalemate! The game ends in a draw."
            }
            _uiState.value = current.copy(
                phase = TurnPhase.GAME_OVER,
                outcome = outcome,
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                gameLog = prependLog(logs, msg, LogCategory.GAME_OVER)
            )
            recordMatchOutcomeIfNeeded(outcome)
            return
        }

        if (forcedCard == null) {
            turnsPlayedInMatch++
        }

        val playerPity = current.movePityCount[activePlayer] ?: 0

        var drawPile = current.drawPile.toMutableList()
        var discardPile = current.discardPile.toMutableList()
        current.activeCard?.let { prev ->
            if (discardPile.none { it.id == prev.id } && drawPile.none { it.id == prev.id }) {
                discardPile.add(prev)
            }
        }

        var safetyCounter = 0

        while (safetyCounter < 64) {
            safetyCounter++
            if (forcedCard == null && drawPile.isEmpty()) {
                if (discardPile.isEmpty()) {
                    drawPile = UnoDeck.createShuffledDeck(random, current.enabledSpecialCards).toMutableList()
                } else {
                    drawPile = discardPile.shuffled(random).toMutableList()
                    discardPile.clear()
                }
                logs = prependLog(logs, "UNO deck reshuffled.", LogCategory.SYSTEM)
            }

            val drawn = forcedCard ?: UnoDeck.drawCardWithPity(
                drawPile = drawPile,
                discardPile = discardPile,
                pityCount = playerPity,
                enabledSpecials = current.enabledSpecialCards,
                random = random
            )

            // Edge case 1: Starting a turn in check — discard action cards and redraw until Number card
            if (forcedCard == null && inCheckAtStart && drawn.type !is UnoCardType.NumberCard) {
                discardPile.add(drawn)
                val cardName = drawn.type.title()
                logs = prependLog(
                    logs,
                    "$activeLabel is in check — $cardName discarded, redrawing.",
                    LogCategory.CHECK
                )
                notice = NoticeBanner(
                    id = nextNoticeId++,
                    message = "In check! $cardName discarded — auto-redrew a Number card.",
                    isAlert = true
                )
                continue
            }

            val nextPityMap = if (drawn.grantsMove) {
                current.movePityCount + (activePlayer to 0)
            } else {
                current.movePityCount + (activePlayer to playerPity + 1)
            }

            when (val type = drawn.type) {
                is UnoCardType.NumberCard -> {
                    val moveWord = if (type.moves == 1) "1 move" else "${type.moves} moves"
                    val pityNote = if (playerPity > 0) " (Pity reset)" else ""
                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew ${type.moves} ($moveWord)$pityNote.",
                        LogCategory.CARD
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.MOVING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = type.moves,
                        movesRemaining = type.moves,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        checkedKingSquare = if (inCheckAtStart) ChessEngine.findKing(current.board, activeColor) else null,
                        noticeBanner = notice,
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.RevivePlusTwo -> {
                    val ownCaptured = current.capturedPieces[activeColor].orEmpty()
                    if (ownCaptured.isEmpty()) {
                        // Mode B: No captured pieces -> place a new Pawn on an empty square (ranks 2-3 / 6-7)
                        val spawnSquares = ChessEngine.getValidSpawnPawnSquares(current.board, activeColor)
                        if (spawnSquares.isEmpty()) {
                            discardPile.add(drawn)
                            logs = prependLog(
                                logs,
                                "$activeLabel drew Revive — no empty square to place a Pawn. Redrawing!",
                                LogCategory.REVIVE
                            )
                            notice = NoticeBanner(
                                id = nextNoticeId++,
                                message = "No empty square to place a Pawn — drew another card!",
                                isAlert = false
                            )
                            continue
                        }
                        logs = prependLog(
                            logs,
                            "$activeLabel (${activeColor.displayName}) drew Revive — no captured pieces, place 1 Pawn.",
                            LogCategory.REVIVE
                        )
                        _uiState.value = current.copy(
                            phase = TurnPhase.REVIVING,
                            drawPile = drawPile.toList(),
                            discardPile = discardPile.toList(),
                            activeCard = drawn,
                            drawRevision = current.drawRevision + 1,
                            totalMovesForCard = 0,
                            movesRemaining = 0,
                            revivesTotalForCard = 1,
                            revivesRemaining = 1,
                            majorRevivedThisTurn = false,
                            movedPieceIdsThisTurn = emptySet(),
                            frozenPieceId = workingFrozenId,
                            frozenOwner = workingFrozenOwner,
                            validFreezeSquares = emptySet(),
                            selectedMergeSquares = emptyList(),
                            selectedSacrificeSquare = null,
                            validSacrificeSquares = emptySet(),
                            selectedPromotePawnSquare = null,
                            validPromotePawnSquares = emptySet(),
                            validSuicideSquares = emptySet(),
                            selectedSquare = null,
                            legalMovesForSelected = emptyList(),
                            selectedRevivePieceId = null,
                            validReviveSquares = spawnSquares,
                            noticeBanner = notice ?: NoticeBanner(
                                id = nextNoticeId++,
                                message = "Revive! No captured pieces — tap a green square to place a Pawn.",
                                isAlert = false
                            ),
                            gameLog = logs,
                            movePityCount = nextPityMap
                        )
                        return
                    } else {
                        // Mode A: Requires an unfrozen own Pawn on the board to transform into 1 captured piece
                        val prioritizedCaptured = ownCaptured.sortedByDescending { it.type.materialValue }
                        val firstRevivable = prioritizedCaptured.firstOrNull { piece ->
                            ChessEngine.getValidReviveSquares(current.board, piece, activeColor, freezeExcluded).isNotEmpty()
                        }
                        if (firstRevivable == null) {
                            discardPile.add(drawn)
                            logs = prependLog(
                                logs,
                                "$activeLabel drew Revive — requires an eligible Pawn on the board. Redrawing!",
                                LogCategory.REVIVE
                            )
                            notice = NoticeBanner(
                                id = nextNoticeId++,
                                message = "Revive requires a Pawn on the board — drew another card!",
                                isAlert = false
                            )
                            continue
                        }
                        val validPawnSquares = ChessEngine.getValidReviveSquares(
                            current.board,
                            firstRevivable,
                            activeColor,
                            freezeExcluded
                        )
                        logs = prependLog(
                            logs,
                            "$activeLabel (${activeColor.displayName}) drew Revive — turn 1 Pawn into a captured piece.",
                            LogCategory.REVIVE
                        )
                        _uiState.value = current.copy(
                            phase = TurnPhase.REVIVING,
                            drawPile = drawPile.toList(),
                            discardPile = discardPile.toList(),
                            activeCard = drawn,
                            drawRevision = current.drawRevision + 1,
                            totalMovesForCard = 0,
                            movesRemaining = 0,
                            revivesTotalForCard = 1,
                            revivesRemaining = 1,
                            majorRevivedThisTurn = false,
                            movedPieceIdsThisTurn = emptySet(),
                            frozenPieceId = workingFrozenId,
                            frozenOwner = workingFrozenOwner,
                            validFreezeSquares = emptySet(),
                            selectedMergeSquares = emptyList(),
                            selectedSacrificeSquare = null,
                            validSacrificeSquares = emptySet(),
                            selectedPromotePawnSquare = null,
                            validPromotePawnSquares = emptySet(),
                            validSuicideSquares = emptySet(),
                            selectedSquare = null,
                            legalMovesForSelected = emptyList(),
                            selectedRevivePieceId = firstRevivable.id,
                            validReviveSquares = validPawnSquares,
                            noticeBanner = notice ?: NoticeBanner(
                                id = nextNoticeId++,
                                message = "Revive! Pick a captured piece & tap a green Pawn to transform it.",
                                isAlert = false
                            ),
                            gameLog = logs,
                            movePityCount = nextPityMap
                        )
                        return
                    }
                }

                UnoCardType.Freeze -> {
                    val opponentColor = activeColor.opponent()
                    val validTargets = ChessEngine.getValidFreezeSquares(current.board, opponentColor)
                    if (validTargets.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Freeze — only enemy King remains. Redrawing!",
                            LogCategory.FREEZE
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No enemy piece to freeze — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Freeze — pick an enemy piece to lock + 1 move.",
                        LogCategory.FREEZE
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.FREEZING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 1,
                        movesRemaining = 1,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = validTargets,
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Freeze! Tap a blue-outlined enemy piece to lock it, then make 1 move.",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Merge -> {
                    val hasMerge = ChessEngine.hasAnyValidTrade(
                        board = current.board,
                        color = activeColor,
                        excludedPieceIds = freezeExcluded
                    )
                    if (!hasMerge) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Merge — no valid pieces to merge. Redrawing!",
                            LogCategory.MERGE
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No valid pieces to merge — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Merge — combine or swap pieces by value.",
                        LogCategory.MERGE
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.MERGING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Merge! Tap 1+ of your pieces to swap or combine.",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Sacrifice -> {
                    val validSacSquares = ChessEngine.getValidSacrificeSquares(
                        board = current.board,
                        color = activeColor,
                        excludedPieceIds = freezeExcluded
                    )
                    if (validSacSquares.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Sacrifice — no piece available to split. Redrawing!",
                            LogCategory.SACRIFICE
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No piece can be sacrificed — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    val initialSac = validSacSquares.first()
                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Sacrifice — split 1 piece into equal-value pieces.",
                        LogCategory.SACRIFICE
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.SACRIFICING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = initialSac,
                        validSacrificeSquares = validSacSquares,
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Sacrifice! Pick a split below (or tap another highlighted piece).",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Hijack -> {
                    val opponentColor = activeColor.opponent()
                    val enemyExcluded = if (workingFrozenId != null && workingFrozenOwner == opponentColor) {
                        setOf(workingFrozenId)
                    } else {
                        emptySet()
                    }
                    val protectedOwnIds = protectedPieceIdsForOwner(
                        shieldedPieceId = current.shieldedPieceId,
                        shieldedOwner = current.shieldedOwner,
                        ownerColor = activeColor
                    )
                    val hijackMoves = ChessEngine.getAllHijackMoves(
                        board = current.board,
                        activeColor = activeColor,
                        castlingRights = current.castlingRights,
                        enPassantTarget = current.enPassantTarget,
                        enPassantOwner = current.enPassantOwner,
                        excludedPieceIds = enemyExcluded,
                        protectedPieceIds = protectedOwnIds
                    )
                    if (hijackMoves.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Hijack — no opponent piece can safely move. Redrawing!",
                            LogCategory.HIJACK
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No opponent piece can be hijacked — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Hijack — move 1 ${opponentColor.displayName} piece.",
                        LogCategory.HIJACK
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.HIJACKING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 1,
                        movesRemaining = 1,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Hijack! Tap an opponent piece and make 1 move with it.",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.PawnPromote -> {
                    val validPawns = ChessEngine.getValidPawnPromoteSquares(
                        board = current.board,
                        color = activeColor,
                        excludedPieceIds = freezeExcluded
                    )
                    if (validPawns.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Promote — no eligible pawn to promote. Redrawing!",
                            LogCategory.PROMOTE
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No eligible pawn to promote — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    val initialPawn = validPawns.first()
                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Promote — upgrade 1 pawn to Knight or Bishop.",
                        LogCategory.PROMOTE
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.PROMOTING_PAWN,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = initialPawn,
                        validPromotePawnSquares = validPawns,
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Promote! Pick Knight or Bishop below (or tap another pawn).",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Suicide -> {
                    val ownNonKing = ChessEngine.getOwnNonKingSquares(current.board, activeColor)
                    if (ownNonKing.isEmpty()) {
                        // Down to only the King -> instant defeat!
                        val opponentPlayer = activePlayer.opponent()
                        val opponentColor = activeColor.opponent()
                        val outcome = GameOutcome.Suicide(
                            losingPlayer = activePlayer,
                            winnerPlayer = opponentPlayer,
                            winnerColor = opponentColor
                        )
                        val msg = "$activeLabel drew Suicide with only their King left — ${actorName(opponentPlayer)} (${opponentColor.displayName}) wins!"
                        logs = prependLog(logs, msg, LogCategory.GAME_OVER)
                        _uiState.value = current.copy(
                            phase = TurnPhase.GAME_OVER,
                            drawPile = drawPile.toList(),
                            discardPile = discardPile.toList(),
                            activeCard = drawn,
                            drawRevision = current.drawRevision + 1,
                            movesRemaining = 0,
                            revivesRemaining = 0,
                            frozenPieceId = null,
                            frozenOwner = null,
                            validFreezeSquares = emptySet(),
                            selectedMergeSquares = emptyList(),
                            selectedSacrificeSquare = null,
                            validSacrificeSquares = emptySet(),
                            selectedPromotePawnSquare = null,
                            validPromotePawnSquares = emptySet(),
                            validSuicideSquares = emptySet(),
                            selectedSquare = null,
                            legalMovesForSelected = emptyList(),
                            noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = true),
                            gameLog = logs,
                            outcome = outcome,
                            movePityCount = nextPityMap
                        )
                        recordMatchOutcomeIfNeeded(outcome)
                        return
                    }

                    val validSuicide = ChessEngine.getValidSuicideSquares(current.board, activeColor)
                    if (validSuicide.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Suicide — all remaining pieces are pinned to the King. Redrawing!",
                            LogCategory.SUICIDE
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "All pieces pinned to King — Suicide redrawn!",
                            isAlert = false
                        )
                        continue
                    }

                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Suicide — eliminate 1 of your pieces.",
                        LogCategory.SUICIDE
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.SUICIDING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = validSuicide,
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Suicide! Tap 1 of your highlighted pieces to remove it.",
                            isAlert = true
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Shield -> {
                    val validShields = ChessEngine.getValidShieldSquares(current.board, activeColor)
                    if (validShields.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Shield — only King remains. Redrawing!",
                            LogCategory.SHIELD
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No non-King piece to shield — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Shield — protect 1 piece + 1 move.",
                        LogCategory.SHIELD
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.SHIELDING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 1,
                        movesRemaining = 1,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        validShieldSquares = validShields,
                        selectedSwapSquare = null,
                        validSwapSquares = emptySet(),
                        selectedCloneSourceSquare = null,
                        validCloneSquares = emptySet(),
                        availableWildEffects = emptyList(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Shield! Tap 1 of your highlighted pieces to protect it, then make 1 move.",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.SwapPositions -> {
                    val validSources = ChessEngine.getValidSwapSourceSquares(
                        board = current.board,
                        color = activeColor,
                        excludedPieceIds = freezeExcluded
                    )
                    if (validSources.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Swap Positions — no legal pair to swap. Redrawing!",
                            LogCategory.SWAP
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No pieces can safely swap — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Swap Positions — swap 2 of your pieces.",
                        LogCategory.SWAP
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.SWAPPING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        validShieldSquares = emptySet(),
                        selectedSwapSquare = null,
                        validSwapSquares = validSources,
                        selectedCloneSourceSquare = null,
                        validCloneSquares = emptySet(),
                        availableWildEffects = emptyList(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Swap! Tap 2 of your highlighted pieces to exchange their squares.",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Clone -> {
                    val validSources = ChessEngine.getValidCloneSourceSquares(
                        board = current.board,
                        color = activeColor,
                        excludedPieceIds = freezeExcluded
                    )
                    if (validSources.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Clone — no eligible Pawn, Knight, or Bishop to clone. Redrawing!",
                            LogCategory.CLONE
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No Pawn, Knight, or Bishop can be cloned — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Clone — duplicate 1 Pawn, Knight, or Bishop.",
                        LogCategory.CLONE
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.CLONING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        validShieldSquares = emptySet(),
                        selectedSwapSquare = null,
                        validSwapSquares = emptySet(),
                        selectedCloneSourceSquare = null,
                        validCloneSquares = validSources,
                        availableWildEffects = emptyList(),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Clone! Tap a highlighted Pawn, Knight, or Bishop, then an open square.",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Wild -> {
                    val wildOptions = computePlayableWildEffects(
                        state = current.copy(
                            frozenPieceId = workingFrozenId,
                            frozenOwner = workingFrozenOwner
                        ),
                        activeColor = activeColor,
                        freezeExcluded = freezeExcluded
                    )
                    if (wildOptions.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(
                            logs,
                            "$activeLabel drew Wild Card — no special card effects available. Redrawing!",
                            LogCategory.WILD
                        )
                        notice = NoticeBanner(
                            id = nextNoticeId++,
                            message = "No special effects available for Wild Card — drew another card!",
                            isAlert = false
                        )
                        continue
                    }

                    logs = prependLog(
                        logs,
                        "$activeLabel (${activeColor.displayName}) drew Wild Card — choose any special effect!",
                        LogCategory.WILD
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.WILD_CHOOSING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        validShieldSquares = emptySet(),
                        selectedSwapSquare = null,
                        validSwapSquares = emptySet(),
                        selectedCloneSourceSquare = null,
                        validCloneSquares = emptySet(),
                        availableWildEffects = wildOptions,
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(
                            id = nextNoticeId++,
                            message = "Wild Card! Pick any special card effect below.",
                            isAlert = false
                        ),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Sniper -> {
                    val sources = ChessEngine.getValidSniperSources(current.board, activeColor, protectedEnemyIds)
                    if (sources.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(logs, "$activeLabel drew Sniper — no enemy pieces in attack range. Redrawing!", LogCategory.SNIPER)
                        notice = NoticeBanner(id = nextNoticeId++, message = "No enemy pieces in sniper range — drew another card!", isAlert = false)
                        continue
                    }
                    logs = prependLog(logs, "$activeLabel (${activeColor.displayName}) drew Sniper — take aim at an enemy piece.", LogCategory.SNIPER)
                    _uiState.value = current.copy(
                        phase = TurnPhase.SNIPING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedSniperSourceSquare = null,
                        validSniperSourceSquares = sources.toSet(),
                        validSniperTargetSquares = emptySet(),
                        selectedPortalSourceSquare = null,
                        validPortalSourceSquares = emptySet(),
                        validPortalDestinationSquares = emptySet(),
                        validTrapSquares = emptySet(),
                        drawTwoPendingCards = null,
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(id = nextNoticeId++, message = "Sniper! Tap 1 of your highlighted pieces to take aim.", isAlert = false),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.Portal -> {
                    val sources = ChessEngine.getValidPortalSources(current.board, activeColor)
                    if (sources.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(logs, "$activeLabel drew Portal — only King remains. Redrawing!", LogCategory.PORTAL)
                        notice = NoticeBanner(id = nextNoticeId++, message = "No non-King piece for Portal — drew another card!", isAlert = false)
                        continue
                    }
                    logs = prependLog(logs, "$activeLabel (${activeColor.displayName}) drew Portal — teleport 1 piece to any safe empty square.", LogCategory.PORTAL)
                    _uiState.value = current.copy(
                        phase = TurnPhase.PORTALING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedSniperSourceSquare = null,
                        validSniperSourceSquares = emptySet(),
                        validSniperTargetSquares = emptySet(),
                        selectedPortalSourceSquare = null,
                        validPortalSourceSquares = sources.toSet(),
                        validPortalDestinationSquares = emptySet(),
                        validTrapSquares = emptySet(),
                        drawTwoPendingCards = null,
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(id = nextNoticeId++, message = "Portal! Tap 1 of your non-King pieces, then tap an empty square.", isAlert = false),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.TrapCard -> {
                    val validTraps = ChessEngine.getValidTrapSquares(current.board, current.traps.map { it.square })
                    if (validTraps.isEmpty()) {
                        discardPile.add(drawn)
                        logs = prependLog(logs, "$activeLabel drew Trap — no empty square available. Redrawing!", LogCategory.TRAP)
                        notice = NoticeBanner(id = nextNoticeId++, message = "No empty square for Trap — drew another card!", isAlert = false)
                        continue
                    }
                    logs = prependLog(logs, "$activeLabel (${activeColor.displayName}) drew Trap — arm a hidden landmine on an empty square.", LogCategory.TRAP)
                    _uiState.value = current.copy(
                        phase = TurnPhase.TRAPPING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedSniperSourceSquare = null,
                        validSniperSourceSquares = emptySet(),
                        validSniperTargetSquares = emptySet(),
                        selectedPortalSourceSquare = null,
                        validPortalSourceSquares = emptySet(),
                        validPortalDestinationSquares = emptySet(),
                        validTrapSquares = validTraps.toSet(),
                        drawTwoPendingCards = null,
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(id = nextNoticeId++, message = "Trap! Tap any empty square to arm a hidden landmine.", isAlert = false),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }

                UnoCardType.DrawTwo -> {
                    if (drawPile.size < 2) {
                        drawPile.addAll(discardPile.shuffled(random))
                        discardPile.clear()
                    }
                    if (drawPile.size < 2) {
                        discardPile.add(drawn)
                        logs = prependLog(logs, "$activeLabel drew Draw Two — not enough cards in deck. Redrawing!", LogCategory.DRAW_TWO)
                        notice = NoticeBanner(id = nextNoticeId++, message = "Not enough cards for Draw Two — drew another card!", isAlert = false)
                        continue
                    }
                    val card1 = drawPile.removeAt(0)
                    val card2 = drawPile.removeAt(0)
                    logs = prependLog(logs, "$activeLabel (${activeColor.displayName}) drew Draw Two — pick 1 of 2 cards to play!", LogCategory.DRAW_TWO)
                    _uiState.value = current.copy(
                        phase = TurnPhase.DRAW_TWO_CHOOSING,
                        drawPile = drawPile.toList(),
                        discardPile = discardPile.toList(),
                        activeCard = drawn,
                        drawRevision = current.drawRevision + 1,
                        totalMovesForCard = 0,
                        movesRemaining = 0,
                        revivesTotalForCard = 0,
                        revivesRemaining = 0,
                        majorRevivedThisTurn = false,
                        movedPieceIdsThisTurn = emptySet(),
                        frozenPieceId = workingFrozenId,
                        frozenOwner = workingFrozenOwner,
                        validFreezeSquares = emptySet(),
                        selectedSniperSourceSquare = null,
                        validSniperSourceSquares = emptySet(),
                        validSniperTargetSquares = emptySet(),
                        selectedPortalSourceSquare = null,
                        validPortalSourceSquares = emptySet(),
                        validPortalDestinationSquares = emptySet(),
                        validTrapSquares = emptySet(),
                        drawTwoPendingCards = Pair(card1, card2),
                        selectedMergeSquares = emptyList(),
                        selectedSacrificeSquare = null,
                        validSacrificeSquares = emptySet(),
                        selectedPromotePawnSquare = null,
                        validPromotePawnSquares = emptySet(),
                        validSuicideSquares = emptySet(),
                        selectedSquare = null,
                        legalMovesForSelected = emptyList(),
                        selectedRevivePieceId = null,
                        validReviveSquares = emptySet(),
                        noticeBanner = notice ?: NoticeBanner(id = nextNoticeId++, message = "Draw Two! Pick 1 of the 2 cards below to play this turn.", isAlert = false),
                        gameLog = logs,
                        movePityCount = nextPityMap
                    )
                    return
                }
            }
        }
    }

    fun onSquareTapped(square: Square) {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.TapSquare(square)))
            return
        }
        synchronized(stateLock) {
            performSquareTap(square)
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performSquareTap(square: Square) {
        val current = _uiState.value
        if (current.outcome != null || current.pendingPromotion != null) return

        when (current.phase) {
            TurnPhase.MOVING -> handleMovingSquareTap(current, square)
            TurnPhase.REVIVING -> handleRevivingSquareTap(current, square)
            TurnPhase.FREEZING -> handleFreezingSquareTap(current, square)
            TurnPhase.MERGING -> handleMergingSquareTap(current, square)
            TurnPhase.SACRIFICING -> handleSacrificingSquareTap(current, square)
            TurnPhase.HIJACKING -> handleHijackingSquareTap(current, square)
            TurnPhase.PROMOTING_PAWN -> handlePromotingPawnSquareTap(current, square)
            TurnPhase.SUICIDING -> handleSuicidingSquareTap(current, square)
            TurnPhase.SHIELDING -> handleShieldingSquareTap(current, square)
            TurnPhase.SWAPPING -> handleSwappingSquareTap(current, square)
            TurnPhase.CLONING -> handleCloningSquareTap(current, square)
            TurnPhase.SNIPING -> handleSniperSquareTap(current, square)
            TurnPhase.PORTALING -> handlePortalSquareTap(current, square)
            TurnPhase.TRAPPING -> handleTrapSquareTap(current, square)
            TurnPhase.DRAW_TWO_CHOOSING -> {
                _uiState.value = current.copy(
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Draw Two! Pick 1 of the 2 cards shown below.",
                        isAlert = false
                    )
                )
            }
            TurnPhase.WILD_CHOOSING -> {
                _uiState.value = current.copy(
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild Card! Pick a special card effect below.",
                        isAlert = false
                    )
                )
            }
            TurnPhase.AWAITING_DRAW -> {
                _uiState.value = current.copy(
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "${actorName(current.activePlayer)}: Draw a UNO card first!",
                        isAlert = false
                    )
                )
            }
            TurnPhase.GAME_OVER -> Unit
        }
    }

    private fun handleHijackingSquareTap(current: GameUiState, square: Square) {
        val activeColor = current.activeColor
        val opponentColor = activeColor.opponent()
        val selected = current.selectedSquare

        // 1. Destination square tapped for currently selected opponent piece
        if (selected != null) {
            val matchingMoves = current.legalMovesForSelected.filter { it.to == square }
            if (matchingMoves.isNotEmpty()) {
                if (matchingMoves.any { it.promotion != null }) {
                    _uiState.value = current.copy(
                        pendingPromotion = PendingPromotion(from = selected, to = square)
                    )
                } else {
                    executeHijackMove(current, matchingMoves.first())
                }
                return
            }
        }

        // 2. Select an opponent piece to hijack
        val tappedPiece = current.board[square.row][square.col]
        if (tappedPiece != null && tappedPiece.color == opponentColor) {
            if (current.isPieceFrozen(tappedPiece)) {
                _uiState.value = current.copy(
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "${tappedPiece.type.displayName} is frozen and cannot be hijacked.",
                        isAlert = true
                    )
                )
                return
            }
            if (selected == square) {
                _uiState.value = current.copy(
                    selectedSquare = null,
                    legalMovesForSelected = emptyList()
                )
            } else {
                val enemyExcluded = if (current.frozenPieceId != null && current.frozenOwner == opponentColor) {
                    setOf(current.frozenPieceId)
                } else {
                    emptySet()
                }
                val protectedOwnIds = protectedPieceIdsForOwner(
                    shieldedPieceId = current.shieldedPieceId,
                    shieldedOwner = current.shieldedOwner,
                    ownerColor = activeColor
                )
                val legal = ChessEngine.getHijackMovesForSquare(
                    board = current.board,
                    from = square,
                    activeColor = activeColor,
                    castlingRights = current.castlingRights,
                    enPassantTarget = current.enPassantTarget,
                    enPassantOwner = current.enPassantOwner,
                    excludedPieceIds = enemyExcluded,
                    protectedPieceIds = protectedOwnIds
                )
                _uiState.value = current.copy(
                    selectedSquare = square,
                    legalMovesForSelected = legal,
                    noticeBanner = if (legal.isEmpty()) {
                        NoticeBanner(
                            id = nextNoticeId++,
                            message = "That ${tappedPiece.type.displayName} has no safe hijack moves — pick another.",
                            isAlert = false
                        )
                    } else {
                        null
                    }
                )
            }
        } else {
            _uiState.value = current.copy(
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                noticeBanner = if (tappedPiece != null && tappedPiece.color == activeColor) {
                    NoticeBanner(
                        id = nextNoticeId++,
                        message = "Hijack active! Tap an opponent (${opponentColor.displayName}) piece to move.",
                        isAlert = false
                    )
                } else {
                    current.noticeBanner
                }
            )
        }
    }

    private fun executeHijackMove(current: GameUiState, move: ChessMove) {
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()

        val result = ChessEngine.applyMove(
            board = current.board,
            move = move,
            castlingRights = current.castlingRights
        )

        val animatedPiece = if (move.promotion != null) {
            result.movedPiece.copy(type = move.promotion)
        } else {
            result.movedPiece
        }
        val (rookPiece, rookFrom, rookTo) = when {
            move.isCastlingKingside -> Triple(
                current.board[move.from.row][7],
                Square(move.from.row, 7),
                Square(move.from.row, 5)
            )
            move.isCastlingQueenside -> Triple(
                current.board[move.from.row][0],
                Square(move.from.row, 0),
                Square(move.from.row, 3)
            )
            else -> Triple(null, null, null)
        }
        val moveAnim = MoveAnimationEvent(
            id = nextAnimId++,
            piece = animatedPiece,
            from = move.from,
            to = move.to,
            isCapture = result.capturedPiece != null,
            secondaryPiece = rookPiece,
            secondaryFrom = rookFrom,
            secondaryTo = rookTo
        )

        val capturedFrozen = result.capturedPiece != null && result.capturedPiece.id == current.frozenPieceId
        val keptFrozenId = if (capturedFrozen) null else current.frozenPieceId
        val keptFrozenOwner = if (capturedFrozen) null else current.frozenOwner
        val nextTurnFrozenId = if (keptFrozenOwner == activeColor) null else keptFrozenId
        val nextTurnFrozenOwner = if (keptFrozenOwner == activeColor) null else keptFrozenOwner

        val updatedCaptured = current.capturedPieces.toMutableMap()
        if (result.capturedPiece != null) {
            val capturedColor = result.capturedPiece.color
            val list = updatedCaptured[capturedColor].orEmpty() + result.capturedPiece
            updatedCaptured[capturedColor] = list.sortedByDescending { it.type.materialValue }
        }

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor,
            removedPieceIds = setOfNotNull(result.capturedPiece?.id)
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentInCheck = ChessEngine.isKingInCheck(result.board, opponentColor)
        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = result.board,
            color = opponentColor,
            castlingRights = result.castlingRights,
            enPassantTarget = result.enPassantTarget,
            enPassantOwner = result.enPassantOwner,
            protectedPieceIds = opponentProtectedIds
        )

        var logs = prependLog(
            current.gameLog,
            "$activeLabel hijacked ${opponentColor.displayName}: ${result.notation}",
            LogCategory.HIJACK
        )

        if (!opponentHasLegalMove) {
            val outcome = if (opponentInCheck) {
                GameOutcome.Checkmate(
                    winnerPlayer = activePlayer,
                    winnerColor = activeColor
                )
            } else {
                GameOutcome.Stalemate
            }
            val endMessage = when (outcome) {
                is GameOutcome.Checkmate ->
                    "Checkmate! $activeLabel (${activeColor.displayName}) wins!"
                else ->
                    "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            }
            logs = prependLog(logs, endMessage, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = result.board,
                phase = TurnPhase.GAME_OVER,
                movesRemaining = 0,
                movedPieceIdsThisTurn = emptySet(),
                frozenPieceId = null,
                frozenOwner = null,
                validFreezeSquares = emptySet(),
                shieldedPieceId = null,
                shieldedOwner = null,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                capturedPieces = updatedCaptured.toMap(),
                lastMoveFrom = move.from,
                lastMoveTo = move.to,
                lastRevivedSquare = null,
                checkedKingSquare = if (opponentInCheck) ChessEngine.findKing(result.board, opponentColor) else null,
                pendingPromotion = null,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = endMessage, isAlert = opponentInCheck),
                moveAnimationEvent = moveAnim,
                reviveAnimationEvent = null,
                gameLog = logs,
                outcome = outcome,
                castlingRights = result.castlingRights,
                enPassantTarget = result.enPassantTarget,
                enPassantOwner = result.enPassantOwner
            )
            recordMatchOutcomeIfNeeded(outcome)
            return
        }

        if (opponentInCheck) {
            logs = prependLog(logs, "Check on ${opponentColor.displayName}!", LogCategory.CHECK)
        }

        _uiState.value = current.copy(
            board = result.board,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            movesRemaining = 0,
            movedPieceIdsThisTurn = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            validFreezeSquares = emptySet(),
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            selectedSquare = null,
            legalMovesForSelected = emptyList(),
            capturedPieces = updatedCaptured.toMap(),
            lastMoveFrom = move.from,
            lastMoveTo = move.to,
            lastRevivedSquare = null,
            checkedKingSquare = if (opponentInCheck) ChessEngine.findKing(result.board, opponentColor) else null,
            pendingPromotion = null,
            noticeBanner = if (opponentInCheck) {
                NoticeBanner(id = nextNoticeId++, message = "Check on ${opponentColor.displayName}!", isAlert = true)
            } else {
                null
            },
            moveAnimationEvent = moveAnim,
            reviveAnimationEvent = null,
            gameLog = logs,
            castlingRights = result.castlingRights,
            enPassantTarget = result.enPassantTarget,
            enPassantOwner = result.enPassantOwner
        )
    }

    private fun handlePromotingPawnSquareTap(current: GameUiState, square: Square) {
        if (square in current.validPromotePawnSquares) {
            _uiState.value = current.copy(
                selectedPromotePawnSquare = square,
                noticeBanner = null
            )
            return
        }
        val piece = current.board[square.row][square.col]
        val msg = when {
            piece != null && piece.color == current.activeColor && piece.type == PieceType.PAWN && current.isPieceFrozen(piece) ->
                "That pawn is frozen and cannot be promoted this turn."
            piece != null && piece.color == current.activeColor && piece.type == PieceType.PAWN ->
                "Promoting that pawn would immediately give check — pick a highlighted pawn."
            else ->
                "Tap a highlighted pawn, then choose Knight or Bishop."
        }
        _uiState.value = current.copy(
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false)
        )
    }

    fun onConfirmPawnPromote(targetType: PieceType) {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.ConfirmPawnPromote(targetType)))
            return
        }
        synchronized(stateLock) {
            performConfirmPawnPromote(targetType)
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performConfirmPawnPromote(targetType: PieceType) {
        val current = _uiState.value
        if (current.phase != TurnPhase.PROMOTING_PAWN || current.outcome != null) return

        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()
        val pawnSquare = current.selectedPromotePawnSquare ?: return

        val validTargets = ChessEngine.getValidPawnPromoteTargets(
            board = current.board,
            color = activeColor,
            pawnSquare = pawnSquare
        )
        if (targetType !in validTargets) {
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = "Cannot promote to ${targetType.displayName} here (would immediately give check).",
                    isAlert = true
                )
            )
            return
        }

        val pawn = current.board[pawnSquare.row][pawnSquare.col] ?: return
        val promotedPiece = pawn.copy(type = targetType)
        val newBoard = ChessEngine.placePiece(current.board, pawnSquare, promotedPiece)
        val popAnim = ReviveAnimationEvent(
            id = nextAnimId++,
            piece = promotedPiece,
            square = pawnSquare
        )

        val summary = "$activeLabel promoted pawn on ${pawnSquare.toAlgebraic()} → ${targetType.displayName}."
        var logs = prependLog(current.gameLog, summary, LogCategory.PROMOTE)

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = current.castlingRights,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )
        if (!opponentHasLegalMove) {
            val msg = "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            logs = prependLog(logs, msg, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                selectedPromotePawnSquare = null,
                validPromotePawnSquares = emptySet(),
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                lastRevivedSquare = pawnSquare,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false),
                moveAnimationEvent = null,
                reviveAnimationEvent = popAnim,
                gameLog = logs,
                outcome = GameOutcome.Stalemate,
                enPassantTarget = null,
                enPassantOwner = null
            )
            recordMatchOutcomeIfNeeded(GameOutcome.Stalemate)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedPromotePawnSquare = null,
            validPromotePawnSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            lastRevivedSquare = pawnSquare,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = summary, isAlert = false),
            moveAnimationEvent = null,
            reviveAnimationEvent = popAnim,
            gameLog = logs,
            enPassantTarget = null,
            enPassantOwner = null
        )
    }

    private fun handleSacrificingSquareTap(current: GameUiState, square: Square) {
        if (square in current.validSacrificeSquares) {
            _uiState.value = current.copy(
                selectedSacrificeSquare = square,
                noticeBanner = null
            )
            return
        }
        val piece = current.board[square.row][square.col]
        val msg = when {
            piece != null && piece.color == current.activeColor && piece.type == PieceType.PAWN ->
                "Pawns (1 pt) cannot be split — pick a Knight, Bishop, Rook, or Queen."
            piece != null && piece.color == current.activeColor && piece.type == PieceType.KING ->
                "You cannot sacrifice your King."
            piece != null && piece.color == current.activeColor && current.isPieceFrozen(piece) ->
                "${piece.type.displayName} is frozen and cannot be sacrificed this turn."
            else ->
                "Tap a highlighted piece to choose what to sacrifice."
        }
        _uiState.value = current.copy(
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false)
        )
    }

    fun onConfirmSacrifice(recipeId: String) {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.ConfirmSacrifice(recipeId)))
            return
        }
        synchronized(stateLock) {
            performConfirmSacrifice(recipeId)
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    fun onSkipSacrifice() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SkipSacrifice))
            return
        }
        synchronized(stateLock) {
            performSkipSacrifice()
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performSkipSacrifice() {
        val current = _uiState.value
        if (current.phase != TurnPhase.SACRIFICING || current.outcome != null) return
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val opponentPlayer = activePlayer.opponent()
        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner
        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} skipped Sacrifice.",
            LogCategory.SACRIFICE
        )
        _uiState.value = current.copy(
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedSacrificeSquare = null,
            validSacrificeSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = "${actorName(activePlayer)} skipped Sacrifice.", isAlert = false),
            gameLog = logs
        )
    }

    private fun performConfirmSacrifice(recipeId: String) {
        val current = _uiState.value
        if (current.phase != TurnPhase.SACRIFICING || current.outcome != null) return

        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()
        val sacSquare = current.selectedSacrificeSquare ?: return
        val piece = current.board[sacSquare.row][sacSquare.col] ?: return

        val recipe = ChessEngine.getValidSacrificeRecipes(current.board, activeColor, sacSquare)
            .firstOrNull { it.id == recipeId }
        val placements = recipe?.let {
            ChessEngine.computeSacrificePlacement(current.board, activeColor, sacSquare, it)
        }
        if (recipe == null || placements == null) {
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = "Cannot split ${piece.type.displayName} that way right now.",
                    isAlert = true
                )
            )
            return
        }

        val startId = computeNextPieceId(current)
        val (newBoard, updatedCastling) = ChessEngine.applySacrifice(
            board = current.board,
            sacrificeSquare = sacSquare,
            placements = placements,
            color = activeColor,
            startingPieceId = startId,
            castlingRights = current.castlingRights
        )
        val firstSpawn = placements.first()
        val popAnim = ReviveAnimationEvent(
            id = nextAnimId++,
            piece = ChessPiece(id = startId, type = firstSpawn.second, color = activeColor),
            square = firstSpawn.first
        )

        val summary = "$activeLabel sacrificed ${piece.type.displayName} on ${sacSquare.toAlgebraic()} → ${recipe.label}."
        var logs = prependLog(current.gameLog, summary, LogCategory.SACRIFICE)

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor,
            removedPieceIds = setOf(piece.id)
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = updatedCastling,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )
        if (!opponentHasLegalMove) {
            val msg = "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            logs = prependLog(logs, msg, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                selectedSacrificeSquare = null,
                validSacrificeSquares = emptySet(),
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                lastRevivedSquare = firstSpawn.first,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false),
                moveAnimationEvent = null,
                reviveAnimationEvent = popAnim,
                gameLog = logs,
                outcome = GameOutcome.Stalemate,
                castlingRights = updatedCastling,
                enPassantTarget = null,
                enPassantOwner = null
            )
            recordMatchOutcomeIfNeeded(GameOutcome.Stalemate)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedSacrificeSquare = null,
            validSacrificeSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            lastRevivedSquare = firstSpawn.first,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = summary, isAlert = false),
            moveAnimationEvent = null,
            reviveAnimationEvent = popAnim,
            gameLog = logs,
            castlingRights = updatedCastling,
            enPassantTarget = null,
            enPassantOwner = null
        )
    }

    private fun handleSuicidingSquareTap(current: GameUiState, square: Square) {
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()

        if (square !in current.validSuicideSquares) {
            val piece = current.board[square.row][square.col]
            val msg = when {
                piece != null && piece.color == activeColor && piece.type == PieceType.KING ->
                    "You must eliminate a non-King piece."
                piece != null && piece.color == activeColor ->
                    "That piece is pinned to your King — pick another highlighted piece."
                else ->
                    "Suicide! Tap one of your red-outlined pieces to eliminate it."
            }
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = true)
            )
            return
        }

        val victim = current.board[square.row][square.col] ?: return
        val (newBoard, updatedCastling) = ChessEngine.applySuicide(
            board = current.board,
            square = square,
            castlingRights = current.castlingRights
        )

        val updatedCaptured = current.capturedPieces.toMutableMap()
        val ownList = updatedCaptured[activeColor].orEmpty() + victim
        updatedCaptured[activeColor] = ownList.sortedByDescending { it.type.materialValue }

        val killedFrozen = victim.id == current.frozenPieceId
        val keptFrozenId = if (killedFrozen) null else current.frozenPieceId
        val keptFrozenOwner = if (killedFrozen) null else current.frozenOwner
        val nextTurnFrozenId = if (keptFrozenOwner == activeColor) null else keptFrozenId
        val nextTurnFrozenOwner = if (keptFrozenOwner == activeColor) null else keptFrozenOwner

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor,
            removedPieceIds = setOf(victim.id)
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val summary = "$activeLabel eliminated own ${victim.type.displayName} on ${square.toAlgebraic()} (Suicide)."
        var logs = prependLog(current.gameLog, summary, LogCategory.SUICIDE)

        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = updatedCastling,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )
        if (!opponentHasLegalMove) {
            val msg = "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            logs = prependLog(logs, msg, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                validSuicideSquares = emptySet(),
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                capturedPieces = updatedCaptured.toMap(),
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false),
                gameLog = logs,
                outcome = GameOutcome.Stalemate,
                castlingRights = updatedCastling,
                enPassantTarget = null,
                enPassantOwner = null
            )
            recordMatchOutcomeIfNeeded(GameOutcome.Stalemate)
            return
        }

        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            validSuicideSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            capturedPieces = updatedCaptured.toMap(),
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = summary, isAlert = true),
            moveAnimationEvent = null,
            reviveAnimationEvent = null,
            gameLog = logs,
            castlingRights = updatedCastling,
            enPassantTarget = null,
            enPassantOwner = null
        )
    }

    private fun handleMergingSquareTap(current: GameUiState, square: Square) {
        val activeColor = current.activeColor
        val piece = current.board[square.row][square.col]

        if (square in current.selectedMergeSquares) {
            _uiState.value = current.copy(
                selectedMergeSquares = current.selectedMergeSquares.filterNot { it == square },
                noticeBanner = null
            )
            return
        }

        if (piece == null || piece.color != activeColor) {
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = "Tap your own pieces to select them for Merge.",
                    isAlert = false
                )
            )
            return
        }

        if (piece.type == PieceType.KING) {
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = "You cannot merge your King.",
                    isAlert = true
                )
            )
            return
        }

        if (current.isPieceFrozen(piece)) {
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = "${piece.type.displayName} is frozen and cannot be merged this turn.",
                    isAlert = true
                )
            )
            return
        }

        _uiState.value = current.copy(
            selectedMergeSquares = current.selectedMergeSquares + square,
            noticeBanner = null
        )
    }

    fun onConfirmMerge(targetType: PieceType) {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.ConfirmMerge(targetType)))
            return
        }
        synchronized(stateLock) {
            performConfirmMerge(targetType)
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    fun onClearMergeSelection() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.ClearMergeSelection))
            return
        }
        synchronized(stateLock) {
            performClearMergeSelection()
            broadcastStateIfHost()
        }
    }

    fun onSkipMerge() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SkipMerge))
            return
        }
        synchronized(stateLock) {
            performSkipMerge()
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performClearMergeSelection() {
        val current = _uiState.value
        if (current.phase == TurnPhase.MERGING) {
            _uiState.value = current.copy(selectedMergeSquares = emptyList())
        }
    }

    private fun performSkipMerge() {
        val current = _uiState.value
        if (current.phase != TurnPhase.MERGING || current.outcome != null) return

        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val opponentPlayer = activePlayer.opponent()
        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )

        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} skipped Merge.",
            LogCategory.MERGE
        )
        _uiState.value = current.copy(
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedMergeSquares = emptyList(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            noticeBanner = NoticeBanner(
                id = nextNoticeId++,
                message = "${actorName(activePlayer)} skipped Merge.",
                isAlert = false
            ),
            gameLog = logs
        )
    }

    private fun performConfirmMerge(targetType: PieceType) {
        val current = _uiState.value
        if (current.phase != TurnPhase.MERGING || current.outcome != null) return

        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()
        val selected = current.selectedMergeSquares

        val validTargets = ChessEngine.getValidTradeTargets(current.board, activeColor, selected)
        if (targetType !in validTargets) {
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = "Cannot merge into ${targetType.displayName} here (value, rank, or check restriction).",
                    isAlert = true
                )
            )
            return
        }

        val freshId = computeNextPieceId(current)
        val newPiece = ChessPiece(id = freshId, type = targetType, color = activeColor)
        val anchor = selected.first()
        val (newBoard, updatedCastling) = ChessEngine.applyTrade(
            board = current.board,
            selectedSquares = selected,
            newPiece = newPiece,
            castlingRights = current.castlingRights
        )
        val popAnim = ReviveAnimationEvent(
            id = nextAnimId++,
            piece = newPiece,
            square = anchor
        )

        val summaryText = if (selected.size == 1) {
            val oldPiece = current.board[anchor.row][anchor.col]
            "$activeLabel swapped ${oldPiece?.type?.displayName ?: "piece"} → ${targetType.displayName} on ${anchor.toAlgebraic()}."
        } else {
            "$activeLabel merged ${selected.size} pieces → ${targetType.displayName} on ${anchor.toAlgebraic()}."
        }
        var logs = prependLog(current.gameLog, summaryText, LogCategory.MERGE)

        val removedMergeIds = selected.mapNotNull { sq -> current.board[sq.row][sq.col]?.id }.toSet()
        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor,
            removedPieceIds = removedMergeIds
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = updatedCastling,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )
        if (!opponentHasLegalMove) {
            val msg = "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            logs = prependLog(logs, msg, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                selectedMergeSquares = emptyList(),
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                lastRevivedSquare = anchor,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false),
                moveAnimationEvent = null,
                reviveAnimationEvent = popAnim,
                gameLog = logs,
                outcome = GameOutcome.Stalemate,
                castlingRights = updatedCastling,
                enPassantTarget = null,
                enPassantOwner = null
            )
            recordMatchOutcomeIfNeeded(GameOutcome.Stalemate)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedMergeSquares = emptyList(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            lastRevivedSquare = anchor,
            noticeBanner = NoticeBanner(
                id = nextNoticeId++,
                message = summaryText,
                isAlert = false
            ),
            moveAnimationEvent = null,
            reviveAnimationEvent = popAnim,
            gameLog = logs,
            castlingRights = updatedCastling,
            enPassantTarget = null,
            enPassantOwner = null
        )
    }

    private fun computeNextPieceId(state: GameUiState): Int {
        var maxId = 64
        state.board.forEach { row ->
            row.forEach { piece ->
                if (piece != null && piece.id > maxId) maxId = piece.id
            }
        }
        state.capturedPieces.values.forEach { list ->
            list.forEach { piece ->
                if (piece.id > maxId) maxId = piece.id
            }
        }
        return maxId + 1
    }

    private fun handleFreezingSquareTap(current: GameUiState, square: Square) {
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val opponentColor = activeColor.opponent()

        if (square !in current.validFreezeSquares) {
            val tapped = current.board[square.row][square.col]
            val msg = when {
                tapped != null && tapped.color == opponentColor && tapped.type == PieceType.KING ->
                    "You cannot freeze the opponent's King."
                else ->
                    "Tap a highlighted enemy piece to freeze it."
            }
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = msg,
                    isAlert = true
                )
            )
            return
        }

        val targetPiece = current.board[square.row][square.col] ?: return
        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} froze ${opponentColor.displayName}'s ${targetPiece.type.displayName} on ${square.toAlgebraic()}.",
            LogCategory.FREEZE
        )

        // Transition into 1 follow-up chess move
        _uiState.value = current.copy(
            phase = TurnPhase.MOVING,
            totalMovesForCard = 1,
            movesRemaining = 1,
            movedPieceIdsThisTurn = emptySet(),
            frozenPieceId = targetPiece.id,
            frozenOwner = opponentColor,
            validFreezeSquares = emptySet(),
            selectedSquare = null,
            legalMovesForSelected = emptyList(),
            noticeBanner = NoticeBanner(
                id = nextNoticeId++,
                message = "${targetPiece.type.displayName} on ${square.toAlgebraic().uppercase()} frozen! Now make 1 move.",
                isAlert = false
            ),
            gameLog = logs
        )
    }

    private fun activeExcludedPieceIds(state: GameUiState): Set<Int> {
        val set = state.movedPieceIdsThisTurn.toMutableSet()
        if (state.frozenPieceId != null && state.frozenOwner == state.activeColor) {
            set.add(state.frozenPieceId)
        }
        return set
    }

    private fun handleMovingSquareTap(current: GameUiState, square: Square) {
        val activeColor = current.activeColor
        val selected = current.selectedSquare

        // 1. Check if tapping a legal move destination for currently selected piece
        if (selected != null) {
            val matchingMoves = current.legalMovesForSelected.filter { it.to == square }
            if (matchingMoves.isNotEmpty()) {
                if (matchingMoves.any { it.promotion != null }) {
                    _uiState.value = current.copy(
                        pendingPromotion = PendingPromotion(from = selected, to = square)
                    )
                } else {
                    executeChessMove(current, matchingMoves.first())
                }
                return
            }
        }

        // 2. If tapping own piece, verify it isn't frozen or already moved this turn
        val tappedPiece = current.board[square.row][square.col]
        if (tappedPiece != null && tappedPiece.color == activeColor) {
            if (current.isPieceFrozen(tappedPiece)) {
                _uiState.value = current.copy(
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "${tappedPiece.type.displayName} is frozen until next turn!",
                        isAlert = true
                    )
                )
                return
            }
            if (tappedPiece.id in current.movedPieceIdsThisTurn) {
                _uiState.value = current.copy(
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Max 1 move per piece per turn — pick another piece.",
                        isAlert = false
                    )
                )
                return
            }

            if (selected == square) {
                _uiState.value = current.copy(
                    selectedSquare = null,
                    legalMovesForSelected = emptyList()
                )
            } else {
                val protectedEnemyIds = protectedPieceIdsForOwner(
                    shieldedPieceId = current.shieldedPieceId,
                    shieldedOwner = current.shieldedOwner,
                    ownerColor = activeColor.opponent()
                )
                val legal = ChessEngine.getLegalMovesForSquare(
                    board = current.board,
                    from = square,
                    castlingRights = current.castlingRights,
                    enPassantTarget = current.enPassantTarget,
                    enPassantOwner = current.enPassantOwner,
                    excludedPieceIds = activeExcludedPieceIds(current),
                    protectedPieceIds = protectedEnemyIds
                )
                _uiState.value = current.copy(
                    selectedSquare = square,
                    legalMovesForSelected = legal
                )
            }
        } else {
            if (selected != null) {
                _uiState.value = current.copy(
                    selectedSquare = null,
                    legalMovesForSelected = emptyList()
                )
            }
        }
    }

    fun onPromotionSelected(pieceType: PieceType) {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SelectPromotion(pieceType)))
            return
        }
        synchronized(stateLock) {
            performPromotionSelected(pieceType)
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performPromotionSelected(pieceType: PieceType) {
        val current = _uiState.value
        val pending = current.pendingPromotion ?: return
        if (current.phase == TurnPhase.HIJACKING) {
            val opponentColor = current.activeColor.opponent()
            val enemyExcluded = if (current.frozenPieceId != null && current.frozenOwner == opponentColor) {
                setOf(current.frozenPieceId)
            } else {
                emptySet()
            }
            val protectedOwnIds = protectedPieceIdsForOwner(
                shieldedPieceId = current.shieldedPieceId,
                shieldedOwner = current.shieldedOwner,
                ownerColor = current.activeColor
            )
            val hijackPromo = ChessEngine.getHijackMovesForSquare(
                board = current.board,
                from = pending.from,
                activeColor = current.activeColor,
                castlingRights = current.castlingRights,
                enPassantTarget = current.enPassantTarget,
                enPassantOwner = current.enPassantOwner,
                excludedPieceIds = enemyExcluded,
                protectedPieceIds = protectedOwnIds
            ).firstOrNull { it.to == pending.to && it.promotion == pieceType } ?: return
            executeHijackMove(current.copy(pendingPromotion = null), hijackPromo)
            return
        }

        val protectedEnemyIds = protectedPieceIdsForOwner(
            shieldedPieceId = current.shieldedPieceId,
            shieldedOwner = current.shieldedOwner,
            ownerColor = current.activeColor.opponent()
        )
        val promoMove = ChessEngine.getLegalMovesForSquare(
            board = current.board,
            from = pending.from,
            castlingRights = current.castlingRights,
            enPassantTarget = current.enPassantTarget,
            enPassantOwner = current.enPassantOwner,
            excludedPieceIds = activeExcludedPieceIds(current),
            protectedPieceIds = protectedEnemyIds
        ).firstOrNull { it.to == pending.to && it.promotion == pieceType } ?: return

        executeChessMove(current.copy(pendingPromotion = null), promoMove)
    }

    fun onPromotionCancelled() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.CancelPromotion))
            return
        }
        synchronized(stateLock) {
            performPromotionCancelled()
            broadcastStateIfHost()
        }
    }

    private fun performPromotionCancelled() {
        val current = _uiState.value
        if (current.pendingPromotion != null) {
            _uiState.value = current.copy(pendingPromotion = null)
        }
    }

    private fun executeChessMove(current: GameUiState, move: ChessMove) {
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()

        val result = ChessEngine.applyMove(
            board = current.board,
            move = move,
            castlingRights = current.castlingRights
        )

        val animatedPiece = if (move.promotion != null) {
            result.movedPiece.copy(type = move.promotion)
        } else {
            result.movedPiece
        }
        val (rookPiece, rookFrom, rookTo) = when {
            move.isCastlingKingside -> Triple(
                current.board[move.from.row][7],
                Square(move.from.row, 7),
                Square(move.from.row, 5)
            )
            move.isCastlingQueenside -> Triple(
                current.board[move.from.row][0],
                Square(move.from.row, 0),
                Square(move.from.row, 3)
            )
            else -> Triple(null, null, null)
        }
        val moveAnim = MoveAnimationEvent(
            id = nextAnimId++,
            piece = animatedPiece,
            from = move.from,
            to = move.to,
            isCapture = result.capturedPiece != null,
            secondaryPiece = rookPiece,
            secondaryFrom = rookFrom,
            secondaryTo = rookTo
        )

        // Track pieces moved this turn (max 1 move per piece; castling uses both King and Rook)
        val updatedMovedIds = buildSet {
            addAll(current.movedPieceIdsThisTurn)
            add(result.movedPiece.id)
            rookPiece?.id?.let(::add)
        }

        // If the frozen piece was captured, clear the freeze
        val capturedFrozen = result.capturedPiece != null && result.capturedPiece.id == current.frozenPieceId
        val keptFrozenId = if (capturedFrozen) null else current.frozenPieceId
        val keptFrozenOwner = if (capturedFrozen) null else current.frozenOwner

        // When turn passes to opponent: clear freeze if it belonged to activeColor (expired after their turn)
        val nextTurnFrozenId = if (keptFrozenOwner == activeColor) null else keptFrozenId
        val nextTurnFrozenOwner = if (keptFrozenOwner == activeColor) null else keptFrozenOwner

        // Update captured pieces map
        val updatedCaptured = current.capturedPieces.toMutableMap()
        if (result.capturedPiece != null) {
            val capturedColor = result.capturedPiece.color
            val list = updatedCaptured[capturedColor].orEmpty() + result.capturedPiece
            updatedCaptured[capturedColor] = list.sortedByDescending { it.type.materialValue }
        }

        // Shield tracking: if turn passes to opponentColor, keep only activeColor's own shield (opponent's expired)
        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor,
            removedPieceIds = setOfNotNull(result.capturedPiece?.id)
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val steppedTrap = current.traps.firstOrNull { it.square == move.to }
        if (steppedTrap != null) {
            val updatedTraps = current.traps - steppedTrap
            val trapBoard = ChessEngine.removePiece(result.board, move.to)
            val updatedCaptured = current.capturedPieces.toMutableMap()
            val list = updatedCaptured[animatedPiece.color].orEmpty() + animatedPiece
            updatedCaptured[animatedPiece.color] = list.sortedByDescending { it.type.materialValue }

            val trapMsg = "💣 Trap triggered on ${move.to.toAlgebraic().uppercase()}! $activeLabel's ${animatedPiece.type.displayName} was destroyed!"
            var trapLogs = prependLog(current.gameLog, "$activeLabel (${activeColor.displayName}): ${result.notation}", LogCategory.MOVE)
            trapLogs = prependLog(trapLogs, trapMsg, LogCategory.TRAP)

            val oppInCheck = ChessEngine.isKingInCheck(trapBoard, opponentColor)
            val oppHasMove = ChessEngine.hasAnyLegalMove(
                board = trapBoard,
                color = opponentColor,
                castlingRights = result.castlingRights,
                enPassantTarget = null,
                enPassantOwner = null,
                protectedPieceIds = opponentProtectedIds
            )
            val outcome = if (!oppHasMove) {
                if (oppInCheck) GameOutcome.Checkmate(activePlayer, activeColor) else GameOutcome.Stalemate
            } else null

            if (outcome != null) {
                val endMsg = if (outcome is GameOutcome.Checkmate) "Checkmate! $activeLabel wins!" else "Stalemate! Match drawn."
                trapLogs = prependLog(trapLogs, endMsg, LogCategory.GAME_OVER)
                recordMatchOutcomeIfNeeded(outcome)
            }

            _uiState.value = current.copy(
                board = trapBoard,
                phase = if (outcome != null) TurnPhase.GAME_OVER else TurnPhase.AWAITING_DRAW,
                activePlayer = if (outcome != null) activePlayer else opponentPlayer,
                boardFlipped = if (outcome == null && snapshotSession().role == NetworkRole.PASS_AND_PLAY && _opponentMode.value is OpponentMode.PassAndPlay) !current.boardFlipped else current.boardFlipped,
                movesRemaining = 0,
                movedPieceIdsThisTurn = emptySet(),
                frozenPieceId = nextTurnFrozenId,
                frozenOwner = nextTurnFrozenOwner,
                shieldedPieceId = nextTurnShieldId,
                shieldedOwner = nextTurnShieldOwner,
                traps = updatedTraps,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                capturedPieces = updatedCaptured,
                lastMoveFrom = move.from,
                lastMoveTo = move.to,
                lastRevivedSquare = null,
                checkedKingSquare = if (oppInCheck) ChessEngine.findKing(trapBoard, opponentColor) else null,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = trapMsg, isAlert = true),
                gameLog = trapLogs,
                outcome = outcome,
                moveAnimationEvent = moveAnim,
                movePityCount = current.movePityCount + (activePlayer to 0)
            )
            return
        }

        val remainingAfterMove = (current.movesRemaining - 1).coerceAtLeast(0)
        val opponentInCheck = ChessEngine.isKingInCheck(result.board, opponentColor)
        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = result.board,
            color = opponentColor,
            castlingRights = result.castlingRights,
            enPassantTarget = result.enPassantTarget,
            enPassantOwner = result.enPassantOwner,
            protectedPieceIds = opponentProtectedIds
        )

        var logs = prependLog(
            current.gameLog,
            "$activeLabel (${activeColor.displayName}): ${result.notation}",
            LogCategory.MOVE
        )

        // 1. Win / Draw check after EVERY individual sub-move
        if (!opponentHasLegalMove) {
            val outcome = if (opponentInCheck) {
                GameOutcome.Checkmate(
                    winnerPlayer = activePlayer,
                    winnerColor = activeColor
                )
            } else {
                GameOutcome.Stalemate
            }
            val endMessage = when (outcome) {
                is GameOutcome.Checkmate ->
                    "Checkmate! $activeLabel (${activeColor.displayName}) wins!"
                else ->
                    "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            }
            logs = prependLog(logs, endMessage, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = result.board,
                phase = TurnPhase.GAME_OVER,
                movesRemaining = 0,
                movedPieceIdsThisTurn = emptySet(),
                frozenPieceId = null,
                frozenOwner = null,
                validFreezeSquares = emptySet(),
                shieldedPieceId = null,
                shieldedOwner = null,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                capturedPieces = updatedCaptured.toMap(),
                lastMoveFrom = move.from,
                lastMoveTo = move.to,
                lastRevivedSquare = null,
                checkedKingSquare = if (opponentInCheck) ChessEngine.findKing(result.board, opponentColor) else null,
                pendingPromotion = null,
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = endMessage,
                    isAlert = opponentInCheck
                ),
                moveAnimationEvent = moveAnim,
                reviveAnimationEvent = null,
                gameLog = logs,
                outcome = outcome,
                castlingRights = result.castlingRights,
                enPassantTarget = result.enPassantTarget,
                enPassantOwner = result.enPassantOwner
            )
            recordMatchOutcomeIfNeeded(outcome)
            return
        }

        // 2. Check ends the turn immediately — forfeit any remaining moves on the card
        if (opponentInCheck) {
            val checkNoticeText = if (remainingAfterMove > 0) {
                "Check! $remainingAfterMove remaining ${if (remainingAfterMove == 1) "move" else "moves"} forfeited."
            } else {
                "Check on ${opponentColor.displayName}!"
            }
            logs = prependLog(logs, checkNoticeText, LogCategory.CHECK)

            _uiState.value = current.copy(
                board = result.board,
                activePlayer = opponentPlayer,
                phase = TurnPhase.AWAITING_DRAW,
                movesRemaining = 0,
                movedPieceIdsThisTurn = emptySet(),
                frozenPieceId = nextTurnFrozenId,
                frozenOwner = nextTurnFrozenOwner,
                validFreezeSquares = emptySet(),
                shieldedPieceId = nextTurnShieldId,
                shieldedOwner = nextTurnShieldOwner,
                selectedSquare = null,
                legalMovesForSelected = emptyList(),
                capturedPieces = updatedCaptured.toMap(),
                lastMoveFrom = move.from,
                lastMoveTo = move.to,
                lastRevivedSquare = null,
                checkedKingSquare = ChessEngine.findKing(result.board, opponentColor),
                pendingPromotion = null,
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = checkNoticeText,
                    isAlert = true
                ),
                moveAnimationEvent = moveAnim,
                reviveAnimationEvent = null,
                gameLog = logs,
                castlingRights = result.castlingRights,
                enPassantTarget = result.enPassantTarget,
                enPassantOwner = result.enPassantOwner
            )
            return
        }

        // 3. Check if more moves remain on this card (with distinct unmoved, unfrozen pieces)
        if (remainingAfterMove > 0) {
            val nextExcludedIds = buildSet {
                addAll(updatedMovedIds)
                if (keptFrozenId != null && keptFrozenOwner == activeColor) {
                    add(keptFrozenId)
                }
            }
            val activeProtectedEnemyIds = protectedPieceIdsForOwner(
                shieldedPieceId = current.shieldedPieceId,
                shieldedOwner = current.shieldedOwner,
                ownerColor = opponentColor
            )
            val activeStillHasMove = ChessEngine.hasAnyLegalMove(
                board = result.board,
                color = activeColor,
                castlingRights = result.castlingRights,
                enPassantTarget = null,
                enPassantOwner = null,
                excludedPieceIds = nextExcludedIds,
                protectedPieceIds = activeProtectedEnemyIds
            )

            if (activeStillHasMove) {
                _uiState.value = current.copy(
                    board = result.board,
                    phase = TurnPhase.MOVING,
                    movesRemaining = remainingAfterMove,
                    movedPieceIdsThisTurn = updatedMovedIds,
                    frozenPieceId = keptFrozenId,
                    frozenOwner = keptFrozenOwner,
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    capturedPieces = updatedCaptured.toMap(),
                    lastMoveFrom = move.from,
                    lastMoveTo = move.to,
                    lastRevivedSquare = null,
                    checkedKingSquare = null,
                    pendingPromotion = null,
                    noticeBanner = null,
                    moveAnimationEvent = moveAnim,
                    reviveAnimationEvent = null,
                    gameLog = logs,
                    castlingRights = result.castlingRights,
                    enPassantTarget = null,
                    enPassantOwner = null
                )
                return
            } else {
                // No unmoved pieces left with legal moves — auto-end turn cleanly
                logs = prependLog(
                    logs,
                    "No other pieces can move — turn passed.",
                    LogCategory.SYSTEM
                )
                _uiState.value = current.copy(
                    board = result.board,
                    activePlayer = opponentPlayer,
                    phase = TurnPhase.AWAITING_DRAW,
                    movesRemaining = 0,
                    movedPieceIdsThisTurn = emptySet(),
                    frozenPieceId = nextTurnFrozenId,
                    frozenOwner = nextTurnFrozenOwner,
                    validFreezeSquares = emptySet(),
                    shieldedPieceId = nextTurnShieldId,
                    shieldedOwner = nextTurnShieldOwner,
                    selectedSquare = null,
                    legalMovesForSelected = emptyList(),
                    capturedPieces = updatedCaptured.toMap(),
                    lastMoveFrom = move.from,
                    lastMoveTo = move.to,
                    lastRevivedSquare = null,
                    checkedKingSquare = null,
                    pendingPromotion = null,
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "No other pieces can move — turn passed.",
                        isAlert = false
                    ),
                    moveAnimationEvent = moveAnim,
                    reviveAnimationEvent = null,
                    gameLog = logs,
                    castlingRights = result.castlingRights,
                    enPassantTarget = result.enPassantTarget,
                    enPassantOwner = result.enPassantOwner
                )
                return
            }
        }

        // 4. Turn complete — pass to the other player
        _uiState.value = current.copy(
            board = result.board,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            movesRemaining = 0,
            movedPieceIdsThisTurn = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            validFreezeSquares = emptySet(),
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            selectedSquare = null,
            legalMovesForSelected = emptyList(),
            capturedPieces = updatedCaptured.toMap(),
            lastMoveFrom = move.from,
            lastMoveTo = move.to,
            lastRevivedSquare = null,
            checkedKingSquare = null,
            pendingPromotion = null,
            noticeBanner = null,
            moveAnimationEvent = moveAnim,
            reviveAnimationEvent = null,
            gameLog = logs,
            castlingRights = result.castlingRights,
            enPassantTarget = result.enPassantTarget,
            enPassantOwner = result.enPassantOwner
        )
    }

    fun onCapturedPieceSelected(pieceId: Int) {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SelectRevivePiece(pieceId)))
            return
        }
        synchronized(stateLock) {
            performCapturedPieceSelected(pieceId)
            broadcastStateIfHost()
        }
    }

    private fun performCapturedPieceSelected(pieceId: Int) {
        val current = _uiState.value
        if (current.phase != TurnPhase.REVIVING || current.outcome != null) return

        val activeColor = current.activeColor
        val ownCaptured = current.capturedPieces[activeColor].orEmpty()
        val piece = ownCaptured.firstOrNull { it.id == pieceId } ?: return
        val freezeExcluded = if (current.frozenPieceId != null && current.frozenOwner == activeColor) {
            setOf(current.frozenPieceId)
        } else {
            emptySet()
        }

        val validSquares = ChessEngine.getValidReviveSquares(current.board, piece, activeColor, freezeExcluded)
        val notice = if (validSquares.isEmpty()) {
            NoticeBanner(
                id = nextNoticeId++,
                message = "${piece.type.displayName} cannot safely replace any of your Pawns right now. Pick another piece.",
                isAlert = true
            )
        } else {
            null
        }

        _uiState.value = current.copy(
            selectedRevivePieceId = piece.id,
            validReviveSquares = validSquares,
            noticeBanner = notice
        )
    }

    private fun handleRevivingSquareTap(current: GameUiState, square: Square) {
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()

        val ownCaptured = current.capturedPieces[activeColor].orEmpty()
        val isSpawnPawnMode = ownCaptured.isEmpty() || current.selectedRevivePieceId == null

        if (square !in current.validReviveSquares) {
            val message = if (isSpawnPawnMode) {
                "Tap a green empty square to place your new Pawn."
            } else {
                "Tap one of your green-highlighted Pawns to turn it into your revived piece."
            }
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = message,
                    isAlert = true
                )
            )
            return
        }

        val newPiece: ChessPiece
        val updatedCaptured = current.capturedPieces.toMutableMap()
        val logText: String

        if (isSpawnPawnMode) {
            val freshId = computeNextPieceId(current)
            newPiece = ChessPiece(id = freshId, type = PieceType.PAWN, color = activeColor)
            logText = "$activeLabel placed a new Pawn on ${square.toAlgebraic()} (Revive)."
        } else {
            val selectedId = current.selectedRevivePieceId
            val pieceToRevive = ownCaptured.firstOrNull { it.id == selectedId } ?: return
            newPiece = pieceToRevive
            updatedCaptured[activeColor] = ownCaptured.filterNot { it.id == pieceToRevive.id }
            logText = "$activeLabel turned Pawn on ${square.toAlgebraic()} into revived ${pieceToRevive.type.displayName}."
        }

        val newBoard = ChessEngine.placePiece(current.board, square, newPiece)
        val reviveAnim = ReviveAnimationEvent(
            id = nextAnimId++,
            piece = newPiece,
            square = square
        )
        var logs = prependLog(current.gameLog, logText, LogCategory.REVIVE)

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = current.castlingRights,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )
        if (!opponentHasLegalMove) {
            val msg = "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            logs = prependLog(logs, msg, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                revivesRemaining = 0,
                majorRevivedThisTurn = false,
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                selectedRevivePieceId = null,
                validReviveSquares = emptySet(),
                capturedPieces = updatedCaptured.toMap(),
                lastRevivedSquare = square,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false),
                moveAnimationEvent = null,
                reviveAnimationEvent = reviveAnim,
                gameLog = logs,
                outcome = GameOutcome.Stalemate,
                enPassantTarget = null,
                enPassantOwner = null
            )
            recordMatchOutcomeIfNeeded(GameOutcome.Stalemate)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        // Reworked Revive is capped to 1 piece -> pass turn cleanly!
        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            revivesRemaining = 0,
            majorRevivedThisTurn = false,
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            selectedRevivePieceId = null,
            validReviveSquares = emptySet(),
            capturedPieces = updatedCaptured.toMap(),
            lastRevivedSquare = square,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = logText, isAlert = false),
            moveAnimationEvent = null,
            reviveAnimationEvent = reviveAnim,
            gameLog = logs,
            enPassantTarget = null,
            enPassantOwner = null
        )
    }

    // ---- Shield, Swap Positions, Clone & Wild Card Handlers ----

    private fun protectedPieceIdsForOwner(
        shieldedPieceId: Int?,
        shieldedOwner: PieceColor?,
        ownerColor: PieceColor
    ): Set<Int> {
        return if (shieldedPieceId != null && shieldedOwner == ownerColor) {
            setOf(shieldedPieceId)
        } else {
            emptySet()
        }
    }

    /**
     * When [endingTurnColor] finishes their turn and control passes to the opponent:
     * - A shield belonging to the opponent has survived [endingTurnColor]'s full turn and now expires.
     * - A shield belonging to [endingTurnColor] (applied this turn) remains active for the opponent's upcoming turn,
     *   unless that piece was removed from the board.
     */
    private fun nextTurnShield(
        currentShieldId: Int?,
        currentShieldOwner: PieceColor?,
        endingTurnColor: PieceColor,
        removedPieceIds: Set<Int> = emptySet()
    ): Pair<Int?, PieceColor?> {
        if (currentShieldId == null || currentShieldOwner == null) return null to null
        if (currentShieldId in removedPieceIds) return null to null
        return if (currentShieldOwner == endingTurnColor) {
            currentShieldId to currentShieldOwner
        } else {
            null to null
        }
    }

    private fun handleShieldingSquareTap(current: GameUiState, square: Square) {
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor

        if (square !in current.validShieldSquares) {
            val tapped = current.board[square.row][square.col]
            val msg = when {
                tapped != null && tapped.color == activeColor && tapped.type == PieceType.KING ->
                    "You cannot shield your King."
                else ->
                    "Tap one of your gold-outlined pieces to shield it."
            }
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = msg,
                    isAlert = true
                )
            )
            return
        }

        val targetPiece = current.board[square.row][square.col] ?: return
        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} shielded ${activeColor.displayName}'s ${targetPiece.type.displayName} on ${square.toAlgebraic()}.",
            LogCategory.SHIELD
        )

        // Transition into 1 follow-up chess move (mirroring Freeze)
        _uiState.value = current.copy(
            phase = TurnPhase.MOVING,
            totalMovesForCard = 1,
            movesRemaining = 1,
            movedPieceIdsThisTurn = emptySet(),
            shieldedPieceId = targetPiece.id,
            shieldedOwner = activeColor,
            validShieldSquares = emptySet(),
            selectedSquare = null,
            legalMovesForSelected = emptyList(),
            noticeBanner = NoticeBanner(
                id = nextNoticeId++,
                message = "${targetPiece.type.displayName} on ${square.toAlgebraic().uppercase()} shielded! Now make 1 move.",
                isAlert = false
            ),
            gameLog = logs
        )
    }

    private fun handleSwappingSquareTap(current: GameUiState, square: Square) {
        val activeColor = current.activeColor
        val freezeExcluded = if (current.frozenPieceId != null && current.frozenOwner == activeColor) {
            setOf(current.frozenPieceId)
        } else {
            emptySet()
        }
        val first = current.selectedSwapSquare

        if (first == null) {
            if (square in current.validSwapSquares) {
                val partners = ChessEngine.getValidSwapPartners(current.board, activeColor, square, freezeExcluded)
                _uiState.value = current.copy(
                    selectedSwapSquare = square,
                    validSwapSquares = partners,
                    noticeBanner = null
                )
                return
            }
            val tapped = current.board[square.row][square.col]
            val msg = when {
                tapped != null && tapped.color == activeColor && current.isPieceFrozen(tapped) ->
                    "${tapped.type.displayName} is frozen and cannot be swapped this turn."
                tapped != null && tapped.color == activeColor ->
                    "That piece has no safe swap partner right now — pick a highlighted piece."
                else ->
                    "Tap one of your highlighted pieces to start swapping."
            }
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false)
            )
            return
        }

        // Deselect if tapping the already selected piece
        if (square == first) {
            performClearSwapSelection()
            return
        }

        // Complete swap if tapping a valid partner
        if (square in current.validSwapSquares) {
            executeSwap(current, first, square)
            return
        }

        // Allow switching first selection directly if tapping another own swappable piece
        val tapped = current.board[square.row][square.col]
        if (tapped != null && tapped.color == activeColor && tapped.id !in freezeExcluded) {
            val partners = ChessEngine.getValidSwapPartners(current.board, activeColor, square, freezeExcluded)
            if (partners.isNotEmpty()) {
                _uiState.value = current.copy(
                    selectedSwapSquare = square,
                    validSwapSquares = partners,
                    noticeBanner = null
                )
                return
            }
        }

        _uiState.value = current.copy(
            noticeBanner = NoticeBanner(
                id = nextNoticeId++,
                message = "Tap a highlighted partner piece to swap, or tap Clear.",
                isAlert = false
            )
        )
    }

    private fun executeSwap(current: GameUiState, first: Square, second: Square) {
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()

        val p1 = current.board[first.row][first.col] ?: return
        val p2 = current.board[second.row][second.col] ?: return

        val (newBoard, updatedCastling) = ChessEngine.applySwap(
            board = current.board,
            first = first,
            second = second,
            castlingRights = current.castlingRights
        )

        val swapAnim = MoveAnimationEvent(
            id = nextAnimId++,
            piece = p1,
            from = first,
            to = second,
            isCapture = false,
            secondaryPiece = p2,
            secondaryFrom = second,
            secondaryTo = first
        )

        val summary = "$activeLabel swapped ${p1.type.displayName} (${first.toAlgebraic()}) ⇄ ${p2.type.displayName} (${second.toAlgebraic()})."
        var logs = prependLog(current.gameLog, summary, LogCategory.SWAP)

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = updatedCastling,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )
        if (!opponentHasLegalMove) {
            val msg = "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            logs = prependLog(logs, msg, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                selectedSwapSquare = null,
                validSwapSquares = emptySet(),
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                lastMoveFrom = first,
                lastMoveTo = second,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false),
                moveAnimationEvent = swapAnim,
                reviveAnimationEvent = null,
                gameLog = logs,
                outcome = GameOutcome.Stalemate,
                castlingRights = updatedCastling,
                enPassantTarget = null,
                enPassantOwner = null
            )
            recordMatchOutcomeIfNeeded(GameOutcome.Stalemate)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedSwapSquare = null,
            validSwapSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            lastMoveFrom = first,
            lastMoveTo = second,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = summary, isAlert = false),
            moveAnimationEvent = swapAnim,
            reviveAnimationEvent = null,
            gameLog = logs,
            castlingRights = updatedCastling,
            enPassantTarget = null,
            enPassantOwner = null
        )
    }

    fun onClearSwapSelection() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.ClearSwapSelection))
            return
        }
        synchronized(stateLock) {
            performClearSwapSelection()
            broadcastStateIfHost()
        }
    }

    fun onSkipSwap() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SkipSwap))
            return
        }
        synchronized(stateLock) {
            performSkipSwap()
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performClearSwapSelection() {
        val current = _uiState.value
        if (current.phase == TurnPhase.SWAPPING) {
            val activeColor = current.activeColor
            val freezeExcluded = if (current.frozenPieceId != null && current.frozenOwner == activeColor) {
                setOf(current.frozenPieceId)
            } else {
                emptySet()
            }
            val validSources = ChessEngine.getValidSwapSourceSquares(current.board, activeColor, freezeExcluded)
            _uiState.value = current.copy(
                selectedSwapSquare = null,
                validSwapSquares = validSources,
                noticeBanner = null
            )
        }
    }

    private fun performSkipSwap() {
        val current = _uiState.value
        if (current.phase != TurnPhase.SWAPPING || current.outcome != null) return

        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val opponentPlayer = activePlayer.opponent()
        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner
        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} skipped Swap Positions.",
            LogCategory.SWAP
        )
        _uiState.value = current.copy(
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedSwapSquare = null,
            validSwapSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            noticeBanner = NoticeBanner(
                id = nextNoticeId++,
                message = "${actorName(activePlayer)} skipped Swap Positions.",
                isAlert = false
            ),
            gameLog = logs
        )
    }

    private fun handleCloningSquareTap(current: GameUiState, square: Square) {
        val activeColor = current.activeColor
        val freezeExcluded = if (current.frozenPieceId != null && current.frozenOwner == activeColor) {
            setOf(current.frozenPieceId)
        } else {
            emptySet()
        }
        val source = current.selectedCloneSourceSquare

        if (source == null) {
            if (square in current.validCloneSquares) {
                val targets = ChessEngine.getValidCloneTargetSquares(current.board, activeColor, square)
                _uiState.value = current.copy(
                    selectedCloneSourceSquare = square,
                    validCloneSquares = targets,
                    noticeBanner = null
                )
                return
            }
            val tapped = current.board[square.row][square.col]
            val msg = when {
                tapped != null && tapped.color == activeColor && current.isPieceFrozen(tapped) ->
                    "${tapped.type.displayName} is frozen and cannot be cloned this turn."
                tapped != null && tapped.color == activeColor &&
                    tapped.type != PieceType.PAWN && tapped.type != PieceType.KNIGHT && tapped.type != PieceType.BISHOP ->
                    "Clone only duplicates a Pawn, Knight, or Bishop."
                else ->
                    "Tap a highlighted Pawn, Knight, or Bishop to clone."
            }
            _uiState.value = current.copy(
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false)
            )
            return
        }

        // Deselect if tapping the selected source piece again
        if (square == source) {
            performClearCloneSelection()
            return
        }

        // Complete clone if tapping a valid destination square
        if (square in current.validCloneSquares) {
            executeClone(current, source, square)
            return
        }

        // Allow switching source selection directly if tapping another own cloneable piece
        val tapped = current.board[square.row][square.col]
        if (
            tapped != null &&
            tapped.color == activeColor &&
            tapped.id !in freezeExcluded &&
            (tapped.type == PieceType.PAWN || tapped.type == PieceType.KNIGHT || tapped.type == PieceType.BISHOP)
        ) {
            val targets = ChessEngine.getValidCloneTargetSquares(current.board, activeColor, square)
            if (targets.isNotEmpty()) {
                _uiState.value = current.copy(
                    selectedCloneSourceSquare = square,
                    validCloneSquares = targets,
                    noticeBanner = null
                )
                return
            }
        }

        _uiState.value = current.copy(
            noticeBanner = NoticeBanner(
                id = nextNoticeId++,
                message = "Tap a highlighted empty square to place the clone, or tap Clear.",
                isAlert = false
            )
        )
    }

    private fun executeClone(current: GameUiState, source: Square, target: Square) {
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()

        val sourcePiece = current.board[source.row][source.col] ?: return
        val clonePiece = ChessPiece(
            id = computeNextPieceId(current),
            type = sourcePiece.type,
            color = activeColor
        )
        val newBoard = ChessEngine.placePiece(current.board, target, clonePiece)
        val popAnim = ReviveAnimationEvent(
            id = nextAnimId++,
            piece = clonePiece,
            square = target
        )

        val summary = "$activeLabel cloned ${sourcePiece.type.displayName} (${source.toAlgebraic()}) → ${target.toAlgebraic()}."
        var logs = prependLog(current.gameLog, summary, LogCategory.CLONE)

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = current.castlingRights,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )
        if (!opponentHasLegalMove) {
            val msg = "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            logs = prependLog(logs, msg, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                selectedCloneSourceSquare = null,
                validCloneSquares = emptySet(),
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                lastRevivedSquare = target,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = msg, isAlert = false),
                moveAnimationEvent = null,
                reviveAnimationEvent = popAnim,
                gameLog = logs,
                outcome = GameOutcome.Stalemate,
                enPassantTarget = null,
                enPassantOwner = null
            )
            recordMatchOutcomeIfNeeded(GameOutcome.Stalemate)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedCloneSourceSquare = null,
            validCloneSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            lastRevivedSquare = target,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = summary, isAlert = false),
            moveAnimationEvent = null,
            reviveAnimationEvent = popAnim,
            gameLog = logs,
            enPassantTarget = null,
            enPassantOwner = null
        )
    }

    fun onClearCloneSelection() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.ClearCloneSelection))
            return
        }
        synchronized(stateLock) {
            performClearCloneSelection()
            broadcastStateIfHost()
        }
    }

    fun onSkipClone() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SkipClone))
            return
        }
        synchronized(stateLock) {
            performSkipClone()
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performClearCloneSelection() {
        val current = _uiState.value
        if (current.phase == TurnPhase.CLONING) {
            val activeColor = current.activeColor
            val freezeExcluded = if (current.frozenPieceId != null && current.frozenOwner == activeColor) {
                setOf(current.frozenPieceId)
            } else {
                emptySet()
            }
            val validSources = ChessEngine.getValidCloneSourceSquares(current.board, activeColor, freezeExcluded)
            _uiState.value = current.copy(
                selectedCloneSourceSquare = null,
                validCloneSquares = validSources,
                noticeBanner = null
            )
        }
    }

    private fun performSkipClone() {
        val current = _uiState.value
        if (current.phase != TurnPhase.CLONING || current.outcome != null) return

        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val opponentPlayer = activePlayer.opponent()
        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner
        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} skipped Clone.",
            LogCategory.CLONE
        )
        _uiState.value = current.copy(
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedCloneSourceSquare = null,
            validCloneSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            noticeBanner = NoticeBanner(
                id = nextNoticeId++,
                message = "${actorName(activePlayer)} skipped Clone.",
                isAlert = false
            ),
            gameLog = logs
        )
    }

    private fun computePlayableWildEffects(
        state: GameUiState,
        activeColor: PieceColor,
        freezeExcluded: Set<Int>
    ): List<SpecialCardKey> {
        val opponentColor = activeColor.opponent()
        val enemyExcluded = if (state.frozenPieceId != null && state.frozenOwner == opponentColor) {
            setOf(state.frozenPieceId)
        } else {
            emptySet()
        }
        val protectedOwnIds = protectedPieceIdsForOwner(
            shieldedPieceId = state.shieldedPieceId,
            shieldedOwner = state.shieldedOwner,
            ownerColor = activeColor
        )

        val candidates = listOf(
            SpecialCardKey.SHIELD,
            SpecialCardKey.FREEZE,
            SpecialCardKey.REVIVE,
            SpecialCardKey.CLONE,
            SpecialCardKey.SWAP,
            SpecialCardKey.PROMOTE,
            SpecialCardKey.HIJACK,
            SpecialCardKey.MERGE,
            SpecialCardKey.SACRIFICE,
            SpecialCardKey.SNIPER,
            SpecialCardKey.PORTAL,
            SpecialCardKey.TRAP
        ).filter { it in state.enabledSpecialCards }

        return candidates.filter { key ->
            when (key) {
                SpecialCardKey.SHIELD ->
                    ChessEngine.getValidShieldSquares(state.board, activeColor).isNotEmpty()
                SpecialCardKey.FREEZE ->
                    ChessEngine.getValidFreezeSquares(state.board, opponentColor).isNotEmpty()
                SpecialCardKey.REVIVE -> {
                    val ownCaptured = state.capturedPieces[activeColor].orEmpty()
                    if (ownCaptured.isEmpty()) {
                        ChessEngine.getValidSpawnPawnSquares(state.board, activeColor).isNotEmpty()
                    } else {
                        ownCaptured.any { piece ->
                            ChessEngine.getValidReviveSquares(state.board, piece, activeColor, freezeExcluded).isNotEmpty()
                        }
                    }
                }
                SpecialCardKey.CLONE ->
                    ChessEngine.getValidCloneSourceSquares(state.board, activeColor, freezeExcluded).isNotEmpty()
                SpecialCardKey.SWAP ->
                    ChessEngine.getValidSwapSourceSquares(state.board, activeColor, freezeExcluded).isNotEmpty()
                SpecialCardKey.PROMOTE ->
                    ChessEngine.getValidPawnPromoteSquares(state.board, activeColor, freezeExcluded).isNotEmpty()
                SpecialCardKey.HIJACK ->
                    ChessEngine.getAllHijackMoves(
                        board = state.board,
                        activeColor = activeColor,
                        castlingRights = state.castlingRights,
                        enPassantTarget = state.enPassantTarget,
                        enPassantOwner = state.enPassantOwner,
                        excludedPieceIds = enemyExcluded,
                        protectedPieceIds = protectedOwnIds
                    ).isNotEmpty()
                SpecialCardKey.MERGE ->
                    ChessEngine.hasAnyValidTrade(state.board, activeColor, freezeExcluded)
                SpecialCardKey.SACRIFICE ->
                    ChessEngine.getValidSacrificeSquares(state.board, activeColor, freezeExcluded).isNotEmpty()
                SpecialCardKey.SNIPER -> {
                    val oppProtected = protectedPieceIdsForOwner(state.shieldedPieceId, state.shieldedOwner, opponentColor)
                    ChessEngine.getValidSniperSources(state.board, activeColor, oppProtected).isNotEmpty()
                }
                SpecialCardKey.PORTAL ->
                    ChessEngine.getValidPortalSources(state.board, activeColor).isNotEmpty()
                SpecialCardKey.TRAP ->
                    ChessEngine.getValidTrapSquares(state.board, state.traps.map { it.square }).isNotEmpty()
                SpecialCardKey.DRAW_TWO, SpecialCardKey.SUICIDE, SpecialCardKey.WILD -> false
            }
        }
    }

    fun onSelectWildEffect(effect: SpecialCardKey) {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SelectWildEffect(effect)))
            return
        }
        synchronized(stateLock) {
            performSelectWildEffect(effect)
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performSelectWildEffect(effect: SpecialCardKey) {
        val current = _uiState.value
        if (current.phase != TurnPhase.WILD_CHOOSING || current.outcome != null) return
        if (effect !in current.availableWildEffects) return

        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentColor = activeColor.opponent()
        val freezeExcluded = if (current.frozenPieceId != null && current.frozenOwner == activeColor) {
            setOf(current.frozenPieceId)
        } else {
            emptySet()
        }

        val logs = prependLog(
            current.gameLog,
            "$activeLabel chose ${effect.displayName} from Wild Card!",
            LogCategory.WILD
        )

        when (effect) {
            SpecialCardKey.SHIELD -> {
                val validShields = ChessEngine.getValidShieldSquares(current.board, activeColor)
                _uiState.value = current.copy(
                    phase = TurnPhase.SHIELDING,
                    totalMovesForCard = 1,
                    movesRemaining = 1,
                    validShieldSquares = validShields,
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Shield! Tap 1 of your highlighted pieces to protect it, then make 1 move.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.FREEZE -> {
                val validFreeze = ChessEngine.getValidFreezeSquares(current.board, opponentColor)
                _uiState.value = current.copy(
                    phase = TurnPhase.FREEZING,
                    totalMovesForCard = 1,
                    movesRemaining = 1,
                    validFreezeSquares = validFreeze,
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Freeze! Tap a blue-outlined enemy piece, then make 1 move.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.REVIVE -> {
                val ownCaptured = current.capturedPieces[activeColor].orEmpty()
                if (ownCaptured.isEmpty()) {
                    val spawnSquares = ChessEngine.getValidSpawnPawnSquares(current.board, activeColor)
                    _uiState.value = current.copy(
                        phase = TurnPhase.REVIVING,
                        revivesTotalForCard = 1,
                        revivesRemaining = 1,
                        selectedRevivePieceId = null,
                        validReviveSquares = spawnSquares,
                        availableWildEffects = emptyList(),
                        noticeBanner = NoticeBanner(
                            id = nextNoticeId++,
                            message = "Wild → Revive! Tap a green square to place a Pawn.",
                            isAlert = false
                        ),
                        gameLog = logs
                    )
                } else {
                    val firstRevivable = ownCaptured
                        .sortedByDescending { it.type.materialValue }
                        .firstOrNull { piece ->
                            ChessEngine.getValidReviveSquares(current.board, piece, activeColor, freezeExcluded).isNotEmpty()
                        } ?: return
                    val validPawnSquares = ChessEngine.getValidReviveSquares(
                        current.board,
                        firstRevivable,
                        activeColor,
                        freezeExcluded
                    )
                    _uiState.value = current.copy(
                        phase = TurnPhase.REVIVING,
                        revivesTotalForCard = 1,
                        revivesRemaining = 1,
                        selectedRevivePieceId = firstRevivable.id,
                        validReviveSquares = validPawnSquares,
                        availableWildEffects = emptyList(),
                        noticeBanner = NoticeBanner(
                            id = nextNoticeId++,
                            message = "Wild → Revive! Pick a captured piece & tap a green Pawn to transform it.",
                            isAlert = false
                        ),
                        gameLog = logs
                    )
                }
            }

            SpecialCardKey.SWAP -> {
                val validSources = ChessEngine.getValidSwapSourceSquares(current.board, activeColor, freezeExcluded)
                _uiState.value = current.copy(
                    phase = TurnPhase.SWAPPING,
                    selectedSwapSquare = null,
                    validSwapSquares = validSources,
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Swap! Tap 2 of your highlighted pieces to swap their positions.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.CLONE -> {
                val validSources = ChessEngine.getValidCloneSourceSquares(current.board, activeColor, freezeExcluded)
                _uiState.value = current.copy(
                    phase = TurnPhase.CLONING,
                    selectedCloneSourceSquare = null,
                    validCloneSquares = validSources,
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Clone! Tap a highlighted Pawn, Knight, or Bishop, then an open square.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.MERGE -> {
                _uiState.value = current.copy(
                    phase = TurnPhase.MERGING,
                    selectedMergeSquares = emptyList(),
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Merge! Tap 1+ of your pieces to swap or combine.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.SACRIFICE -> {
                val validSac = ChessEngine.getValidSacrificeSquares(current.board, activeColor, freezeExcluded)
                _uiState.value = current.copy(
                    phase = TurnPhase.SACRIFICING,
                    selectedSacrificeSquare = validSac.firstOrNull(),
                    validSacrificeSquares = validSac,
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Sacrifice! Pick a split below or tap another highlighted piece.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.HIJACK -> {
                _uiState.value = current.copy(
                    phase = TurnPhase.HIJACKING,
                    totalMovesForCard = 1,
                    movesRemaining = 1,
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Hijack! Tap an opponent piece and make 1 move with it.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.PROMOTE -> {
                val validPawns = ChessEngine.getValidPawnPromoteSquares(current.board, activeColor, freezeExcluded)
                _uiState.value = current.copy(
                    phase = TurnPhase.PROMOTING_PAWN,
                    selectedPromotePawnSquare = validPawns.firstOrNull(),
                    validPromotePawnSquares = validPawns,
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Promote! Pick Knight or Bishop below (or tap another pawn).",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.SNIPER -> {
                val oppProtected = protectedPieceIdsForOwner(current.shieldedPieceId, current.shieldedOwner, opponentColor)
                val sources = ChessEngine.getValidSniperSources(current.board, activeColor, oppProtected)
                _uiState.value = current.copy(
                    phase = TurnPhase.SNIPING,
                    validSniperSourceSquares = sources.toSet(),
                    selectedSniperSourceSquare = null,
                    validSniperTargetSquares = emptySet(),
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Sniper! Tap 1 of your highlighted pieces to take aim.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.PORTAL -> {
                val sources = ChessEngine.getValidPortalSources(current.board, activeColor)
                _uiState.value = current.copy(
                    phase = TurnPhase.PORTALING,
                    validPortalSourceSquares = sources.toSet(),
                    selectedPortalSourceSquare = null,
                    validPortalDestinationSquares = emptySet(),
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Portal! Tap 1 of your non-King pieces to teleport.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.TRAP -> {
                val valids = ChessEngine.getValidTrapSquares(current.board, current.traps.map { it.square })
                _uiState.value = current.copy(
                    phase = TurnPhase.TRAPPING,
                    validTrapSquares = valids.toSet(),
                    availableWildEffects = emptyList(),
                    noticeBanner = NoticeBanner(
                        id = nextNoticeId++,
                        message = "Wild → Trap! Tap any empty square to arm a landmine.",
                        isAlert = false
                    ),
                    gameLog = logs
                )
            }

            SpecialCardKey.DRAW_TWO, SpecialCardKey.SUICIDE, SpecialCardKey.WILD -> Unit
        }
    }

    // -------------------------------------------------------------------------
    // Sniper Action
    // -------------------------------------------------------------------------

    private fun handleSniperSquareTap(current: GameUiState, square: Square) {
        val activeColor = current.activeColor
        val activeLabel = actorName(current.activePlayer)
        val opponentPlayer = current.activePlayer.opponent()
        val opponentColor = activeColor.opponent()
        val oppProtected = protectedPieceIdsForOwner(current.shieldedPieceId, current.shieldedOwner, opponentColor)

        val selectedSource = current.selectedSniperSourceSquare
        if (selectedSource == null) {
            if (square !in current.validSniperSourceSquares) return
            val targets = ChessEngine.getValidSniperTargets(current.board, square, opponentColor, oppProtected)
            _uiState.value = current.copy(
                selectedSniperSourceSquare = square,
                validSniperTargetSquares = targets.toSet(),
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = "Sniper locked! Tap a highlighted enemy piece to eliminate it.",
                    isAlert = false
                )
            )
            return
        }

        if (selectedSource == square) {
            _uiState.value = current.copy(
                selectedSniperSourceSquare = null,
                validSniperTargetSquares = emptySet(),
                noticeBanner = null
            )
            return
        }

        if (square in current.validSniperSourceSquares) {
            val targets = ChessEngine.getValidSniperTargets(current.board, square, opponentColor, oppProtected)
            _uiState.value = current.copy(
                selectedSniperSourceSquare = square,
                validSniperTargetSquares = targets.toSet()
            )
            return
        }

        if (square !in current.validSniperTargetSquares) return

        val sniperPiece = current.board[selectedSource.row][selectedSource.col] ?: return
        val targetPiece = current.board[square.row][square.col] ?: return

        val newBoard = ChessEngine.removePiece(current.board, square)
        val updatedCaptured = current.capturedPieces + (targetPiece.color to (current.capturedPieces[targetPiece.color].orEmpty() + targetPiece))

        val summary = "$activeLabel's ${sniperPiece.type.displayName} sniped ${targetPiece.color.displayName} ${targetPiece.type.displayName} on ${square.toAlgebraic().uppercase()}!"
        var logs = prependLog(current.gameLog, summary, LogCategory.SNIPER)

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor,
            removedPieceIds = setOf(targetPiece.id)
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentInCheck = ChessEngine.isKingInCheck(newBoard, opponentColor)
        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = current.castlingRights,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )

        if (!opponentHasLegalMove) {
            val outcome = if (opponentInCheck) {
                GameOutcome.Checkmate(winnerPlayer = current.activePlayer, winnerColor = activeColor)
            } else {
                GameOutcome.Stalemate
            }
            val endMessage = when (outcome) {
                is GameOutcome.Checkmate -> "Checkmate! $activeLabel (${activeColor.displayName}) wins!"
                else -> "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            }
            logs = prependLog(logs, endMessage, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                selectedSniperSourceSquare = null,
                validSniperSourceSquares = emptySet(),
                validSniperTargetSquares = emptySet(),
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                capturedPieces = updatedCaptured,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = endMessage, isAlert = false),
                gameLog = logs,
                outcome = outcome
            )
            recordMatchOutcomeIfNeeded(outcome)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedSniperSourceSquare = null,
            validSniperSourceSquares = emptySet(),
            validSniperTargetSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            capturedPieces = updatedCaptured,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = summary, isAlert = false),
            gameLog = logs
        )
    }

    fun onSkipSniper() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SkipSniper))
            return
        }
        synchronized(stateLock) {
            performSkipSniper()
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performSkipSniper() {
        val current = _uiState.value
        if (current.phase != TurnPhase.SNIPING || current.outcome != null) return
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val opponentPlayer = activePlayer.opponent()
        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner
        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} skipped Sniper.",
            LogCategory.SNIPER
        )
        _uiState.value = current.copy(
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedSniperSourceSquare = null,
            validSniperSourceSquares = emptySet(),
            validSniperTargetSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = "${actorName(activePlayer)} skipped Sniper.", isAlert = false),
            gameLog = logs
        )
    }

    // -------------------------------------------------------------------------
    // Portal Action
    // -------------------------------------------------------------------------

    private fun handlePortalSquareTap(current: GameUiState, square: Square) {
        val activeColor = current.activeColor
        val activeLabel = actorName(current.activePlayer)
        val opponentPlayer = current.activePlayer.opponent()
        val opponentColor = activeColor.opponent()

        val selectedSource = current.selectedPortalSourceSquare
        if (selectedSource == null) {
            if (square !in current.validPortalSourceSquares) return
            val destinations = ChessEngine.getValidPortalDestinations(current.board, activeColor, square)
            _uiState.value = current.copy(
                selectedPortalSourceSquare = square,
                validPortalDestinationSquares = destinations.toSet(),
                noticeBanner = NoticeBanner(
                    id = nextNoticeId++,
                    message = "Portal engaged! Tap a highlighted empty square to teleport.",
                    isAlert = false
                )
            )
            return
        }

        if (selectedSource == square) {
            _uiState.value = current.copy(
                selectedPortalSourceSquare = null,
                validPortalDestinationSquares = emptySet(),
                noticeBanner = null
            )
            return
        }

        if (square in current.validPortalSourceSquares) {
            val destinations = ChessEngine.getValidPortalDestinations(current.board, activeColor, square)
            _uiState.value = current.copy(
                selectedPortalSourceSquare = square,
                validPortalDestinationSquares = destinations.toSet()
            )
            return
        }

        if (square !in current.validPortalDestinationSquares) return

        val piece = current.board[selectedSource.row][selectedSource.col] ?: return

        var newBoard = ChessEngine.removePiece(current.board, selectedSource)
        val updatedTraps = current.traps.toMutableList()
        var updatedCaptured = current.capturedPieces
        var summary = "$activeLabel teleported ${piece.type.displayName} from ${selectedSource.toAlgebraic().uppercase()} to ${square.toAlgebraic().uppercase()}!"
        val removedIds = mutableSetOf<Int>()

        val trapIndex = updatedTraps.indexOfFirst { it.square == square }
        val triggeredTrap = if (trapIndex >= 0) updatedTraps.removeAt(trapIndex) else null

        if (triggeredTrap != null) {
            removedIds.add(piece.id)
            updatedCaptured = updatedCaptured + (piece.color to (updatedCaptured[piece.color].orEmpty() + piece))
            summary += " 💣 Trap triggered! The piece was destroyed!"
        } else {
            newBoard = ChessEngine.placePiece(newBoard, square, piece)
        }

        var logs = prependLog(current.gameLog, summary, LogCategory.PORTAL)

        val moveAnim = MoveAnimationEvent(
            id = nextAnimId++,
            from = selectedSource,
            to = square,
            piece = piece,
            isCapture = triggeredTrap != null
        )

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor,
            removedPieceIds = removedIds
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentInCheck = ChessEngine.isKingInCheck(newBoard, opponentColor)
        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = newBoard,
            color = opponentColor,
            castlingRights = current.castlingRights,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )

        if (!opponentHasLegalMove) {
            val outcome = if (opponentInCheck) {
                GameOutcome.Checkmate(winnerPlayer = current.activePlayer, winnerColor = activeColor)
            } else {
                GameOutcome.Stalemate
            }
            val endMessage = when (outcome) {
                is GameOutcome.Checkmate -> "Checkmate! $activeLabel (${activeColor.displayName}) wins!"
                else -> "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            }
            logs = prependLog(logs, endMessage, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                board = newBoard,
                phase = TurnPhase.GAME_OVER,
                selectedPortalSourceSquare = null,
                validPortalSourceSquares = emptySet(),
                validPortalDestinationSquares = emptySet(),
                traps = updatedTraps,
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                capturedPieces = updatedCaptured,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = endMessage, isAlert = false),
                moveAnimationEvent = moveAnim,
                gameLog = logs,
                outcome = outcome
            )
            recordMatchOutcomeIfNeeded(outcome)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        _uiState.value = current.copy(
            board = newBoard,
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedPortalSourceSquare = null,
            validPortalSourceSquares = emptySet(),
            validPortalDestinationSquares = emptySet(),
            traps = updatedTraps,
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            capturedPieces = updatedCaptured,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = summary, isAlert = false),
            moveAnimationEvent = moveAnim,
            gameLog = logs
        )
    }

    fun onSkipPortal() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SkipPortal))
            return
        }
        synchronized(stateLock) {
            performSkipPortal()
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performSkipPortal() {
        val current = _uiState.value
        if (current.phase != TurnPhase.PORTALING || current.outcome != null) return
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val opponentPlayer = activePlayer.opponent()
        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner
        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} skipped Portal.",
            LogCategory.PORTAL
        )
        _uiState.value = current.copy(
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            selectedPortalSourceSquare = null,
            validPortalSourceSquares = emptySet(),
            validPortalDestinationSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = "${actorName(activePlayer)} skipped Portal.", isAlert = false),
            gameLog = logs
        )
    }

    // -------------------------------------------------------------------------
    // Trap Action
    // -------------------------------------------------------------------------

    private fun handleTrapSquareTap(current: GameUiState, square: Square) {
        if (square !in current.validTrapSquares) return
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val activeLabel = actorName(activePlayer)
        val opponentPlayer = activePlayer.opponent()
        val opponentColor = activeColor.opponent()

        val newTrap = Trap(square = square, placedBy = activePlayer)
        val updatedTraps = current.traps + newTrap
        val summary = "$activeLabel armed a hidden trap on ${square.toAlgebraic().uppercase()}!"
        var logs = prependLog(current.gameLog, summary, LogCategory.TRAP)

        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val opponentProtectedIds = protectedPieceIdsForOwner(nextTurnShieldId, nextTurnShieldOwner, activeColor)

        val opponentInCheck = ChessEngine.isKingInCheck(current.board, opponentColor)
        val opponentHasLegalMove = ChessEngine.hasAnyLegalMove(
            board = current.board,
            color = opponentColor,
            castlingRights = current.castlingRights,
            enPassantTarget = null,
            enPassantOwner = null,
            protectedPieceIds = opponentProtectedIds
        )

        if (!opponentHasLegalMove) {
            val outcome = if (opponentInCheck) {
                GameOutcome.Checkmate(winnerPlayer = activePlayer, winnerColor = activeColor)
            } else {
                GameOutcome.Stalemate
            }
            val endMessage = when (outcome) {
                is GameOutcome.Checkmate -> "Checkmate! $activeLabel (${activeColor.displayName}) wins!"
                else -> "Stalemate! ${actorName(opponentPlayer)} (${opponentColor.displayName}) has no legal moves."
            }
            logs = prependLog(logs, endMessage, LogCategory.GAME_OVER)
            _uiState.value = current.copy(
                phase = TurnPhase.GAME_OVER,
                validTrapSquares = emptySet(),
                traps = updatedTraps,
                frozenPieceId = null,
                frozenOwner = null,
                shieldedPieceId = null,
                shieldedOwner = null,
                noticeBanner = NoticeBanner(id = nextNoticeId++, message = endMessage, isAlert = false),
                gameLog = logs,
                outcome = outcome
            )
            recordMatchOutcomeIfNeeded(outcome)
            return
        }

        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner

        _uiState.value = current.copy(
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            validTrapSquares = emptySet(),
            traps = updatedTraps,
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = summary, isAlert = false),
            gameLog = logs
        )
    }

    fun onSkipTrap() {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.SkipTrap))
            return
        }
        synchronized(stateLock) {
            performSkipTrap()
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performSkipTrap() {
        val current = _uiState.value
        if (current.phase != TurnPhase.TRAPPING || current.outcome != null) return
        val activePlayer = current.activePlayer
        val activeColor = current.activeColor
        val opponentPlayer = activePlayer.opponent()
        val nextTurnFrozenId = if (current.frozenOwner == activeColor) null else current.frozenPieceId
        val nextTurnFrozenOwner = if (current.frozenOwner == activeColor) null else current.frozenOwner
        val (nextTurnShieldId, nextTurnShieldOwner) = nextTurnShield(
            currentShieldId = current.shieldedPieceId,
            currentShieldOwner = current.shieldedOwner,
            endingTurnColor = activeColor
        )
        val logs = prependLog(
            current.gameLog,
            "${actorName(activePlayer)} skipped Trap.",
            LogCategory.TRAP
        )
        _uiState.value = current.copy(
            activePlayer = opponentPlayer,
            phase = TurnPhase.AWAITING_DRAW,
            validTrapSquares = emptySet(),
            frozenPieceId = nextTurnFrozenId,
            frozenOwner = nextTurnFrozenOwner,
            shieldedPieceId = nextTurnShieldId,
            shieldedOwner = nextTurnShieldOwner,
            noticeBanner = NoticeBanner(id = nextNoticeId++, message = "${actorName(activePlayer)} skipped Trap.", isAlert = false),
            gameLog = logs
        )
    }

    // -------------------------------------------------------------------------
    // Draw Two Action
    // -------------------------------------------------------------------------

    fun onChooseDrawTwo(index: Int) {
        val lan = snapshotSession()
        if (!isLocalPlayersTurn()) return
        if (lan.role.isGuest) {
            sendActiveLine(LanStateCodec.encodeAction(LanAction.ChooseDrawTwo(index)))
            return
        }
        synchronized(stateLock) {
            performChooseDrawTwoCard(index)
            broadcastStateIfHost()
        }
        scheduleAiTurnIfNeeded()
    }

    private fun performChooseDrawTwoCard(index: Int) {
        val current = _uiState.value
        if (current.phase != TurnPhase.DRAW_TWO_CHOOSING || current.drawTwoPendingCards == null) return
        val (c1, c2) = current.drawTwoPendingCards ?: return
        val chosenCard = if (index == 0) c1 else c2
        val discardedCard = if (index == 0) c2 else c1
        val newDiscard = current.discardPile + discardedCard
        val activeLabel = actorName(current.activePlayer)
        val logs = prependLog(
            current.gameLog,
            "$activeLabel chose ${chosenCard.type.title()} (${chosenCard.type.subtitle()}) from Draw Two!",
            LogCategory.DRAW_TWO
        )
        _uiState.value = current.copy(
            phase = TurnPhase.AWAITING_DRAW,
            discardPile = newDiscard,
            drawTwoPendingCards = null,
            gameLog = logs
        )
        performDrawCard(forcedCard = chosenCard)
    }

    private fun prependLog(
        existing: List<LogEntry>,
        message: String,
        category: LogCategory
    ): List<LogEntry> {
        val entry = LogEntry(id = nextLogId++, message = message, category = category)
        return (listOf(entry) + existing).take(60)
    }
}

private class MergedSessionState(
    private val lan: StateFlow<LanSessionState>,
    private val online: StateFlow<LanSessionState>
) : StateFlow<LanSessionState> {
    private fun merge(lanState: LanSessionState, onlineState: LanSessionState): LanSessionState {
        val active = if (onlineState.role.isOnline) onlineState else lanState
        return active.copy(
            openLobbies = onlineState.openLobbies,
            isListingLobbies = onlineState.isListingLobbies,
            onlineErrorMessage = onlineState.onlineErrorMessage ?: onlineState.errorMessage.takeIf { onlineState.role.isOnline },
            isOnlineServerReachable = onlineState.isOnlineServerReachable
        )
    }

    override val value: LanSessionState
        get() = merge(lan.value, online.value)

    override val replayCache: List<LanSessionState>
        get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<LanSessionState>): Nothing {
        combine(lan, online) { lanState, onlineState -> merge(lanState, onlineState) }
            .collect(collector)
        error("Session flow completed")
    }
}

private class ObservingMutableStateFlow<T>(
    private val delegate: MutableStateFlow<T>,
    private val onTransition: (prev: T, next: T) -> Unit
) : MutableStateFlow<T> by delegate {
    override var value: T
        get() = delegate.value
        set(newValue) {
            val prev = delegate.value
            delegate.value = newValue
            if (prev !== newValue) {
                onTransition(prev, newValue)
            }
        }
}
