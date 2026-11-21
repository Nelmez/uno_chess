package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.GameUiState
import com.example.model.MatchStats
import com.example.model.NoticeBanner
import com.example.model.OpponentMode
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.model.PlayerId
import com.example.model.SpecialCardKey
import com.example.model.Square
import com.example.model.TurnPhase
import com.example.model.UnoCardTheme
import com.example.network.LanSessionState
import com.example.network.NetworkRole
import com.example.ui.components.ChessBoardView
import com.example.ui.components.CloneActionCard
import com.example.ui.components.ForfeitConfirmDialog
import com.example.ui.components.GameLogCard
import com.example.ui.components.GameOverBannerCard
import com.example.ui.components.LanMultiplayerDialog
import com.example.ui.components.MatchLogModalDialog
import com.example.ui.components.MergeActionCard
import com.example.ui.components.PawnPromoteActionCard
import com.example.ui.components.PlayerStripCard
import com.example.ui.components.PromotionPickerDialog
import com.example.ui.components.ResetConfirmDialog
import com.example.ui.components.RulesModalDialog
import com.example.ui.components.SacrificeActionCard
import android.os.Handler
import android.os.Looper
import com.example.BuildConfig
import com.example.ui.components.AppUpdateDialog
import com.example.ui.components.SettingsModalDialog
import com.example.update.AppUpdateInstaller
import com.example.update.GithubAppUpdateChecker
import com.example.update.GithubRelease
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.components.SidePanelCapturedTraysCard
import com.example.ui.components.SwapActionCard
import com.example.ui.components.TurnStatusSummary
import com.example.ui.components.UnoDeckAndActiveCardRow
import com.example.ui.components.VictoryCelebrationOverlay
import com.example.ui.components.WildCardActionCard
import com.example.ui.components.resolvePlayerLabel
import com.example.ui.theme.LocalUnoChessColors
import com.example.viewmodel.UnoChessViewModel
import kotlinx.coroutines.delay

@Composable
fun UnoChessScreen(
    viewModel: UnoChessViewModel,
    isDarkTheme: Boolean = false,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val lanState by viewModel.lanState.collectAsStateWithLifecycle()
    val opponentMode by viewModel.opponentMode.collectAsStateWithLifecycle()
    val isAiThinking by viewModel.isAiThinking.collectAsStateWithLifecycle()
    val cardTheme by viewModel.cardTheme.collectAsStateWithLifecycle()
    val soundEnabled by viewModel.soundEnabled.collectAsStateWithLifecycle()
    val matchStats by viewModel.matchStats.collectAsStateWithLifecycle()
    val hostEnabledSpecialCards by viewModel.hostEnabledSpecialCards.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colors = LocalUnoChessColors.current

    var showRulesModal by rememberSaveable { mutableStateOf(false) }
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var showForfeitDialog by rememberSaveable { mutableStateOf(false) }
    var showLanDialog by rememberSaveable { mutableStateOf(false) }
    var showSettingsModal by rememberSaveable { mutableStateOf(false) }
    var showMatchLogModal by rememberSaveable { mutableStateOf(false) }
    var celebrationDismissed by rememberSaveable { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<GithubRelease?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }
    var downloadedApk by remember { mutableStateOf<File?>(null) }
    var downloadingUpdate by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var checkingForUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var updateDialogMessage by remember { mutableStateOf<String?>(null) }
    val updateScope = rememberCoroutineScope()
    val updateChecker = remember { GithubAppUpdateChecker() }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val latestVersionText = stringResource(R.string.update_latest)
    val checkFailedText = stringResource(R.string.update_check_failed)
    val downloadFailedText = stringResource(R.string.update_download_failed)
    val installPermissionText = stringResource(R.string.update_install_permission)

    fun checkForUpdate(manual: Boolean) {
        if (checkingForUpdate || downloadingUpdate) return
        updateScope.launch {
            checkingForUpdate = true
            if (manual) updateStatus = null
            val result = runCatching {
                withContext(Dispatchers.IO) { updateChecker.check(BuildConfig.VERSION_CODE) }
            }
            checkingForUpdate = false
            val release = result.getOrNull()
            when {
                release != null -> {
                    pendingUpdate = release
                    downloadedApk = null
                    downloadProgress = 0f
                    updateDialogMessage = null
                    updateStatus = null
                    showUpdateDialog = true
                }
                manual && result.isFailure -> updateStatus = checkFailedText
                manual -> updateStatus = latestVersionText
            }
        }
    }

    fun installPendingUpdate() {
        val release = pendingUpdate ?: return
        val ready = downloadedApk
        if (ready != null) {
            if (!AppUpdateInstaller.canInstall(context)) {
                updateDialogMessage = installPermissionText
                AppUpdateInstaller.openInstallPermissionSettings(context)
                return
            }
            AppUpdateInstaller.install(context, ready)
            return
        }
        updateScope.launch {
            downloadingUpdate = true
            updateDialogMessage = null
            downloadProgress = 0f
            val file = runCatching {
                withContext(Dispatchers.IO) {
                    AppUpdateInstaller.download(context, release.apkUrl) { progress ->
                        mainHandler.post { downloadProgress = progress }
                    }
                }
            }
            downloadingUpdate = false
            file.onSuccess { downloadedApk = it }
            file.onFailure { updateDialogMessage = downloadFailedText }
            val apk = file.getOrNull() ?: return@launch
            if (!AppUpdateInstaller.canInstall(context)) {
                updateDialogMessage = installPermissionText
                AppUpdateInstaller.openInstallPermissionSettings(context)
                return@launch
            }
            AppUpdateInstaller.install(context, apk)
        }
    }

    LaunchedEffect(Unit) {
        checkForUpdate(manual = false)
    }

    LaunchedEffect(state.outcome) {
        celebrationDismissed = false
    }

    LaunchedEffect(lanState.isRemoteConnected) {
        if (lanState.isRemoteConnected && showLanDialog) {
            showLanDialog = false
        }
    }

    LaunchedEffect(state.noticeBanner?.id) {
        if (state.noticeBanner != null && state.outcome == null) {
            delay(3800)
            viewModel.dismissNotice()
        }
    }

    val localPlayer: PlayerId? = when {
        lanState.isRemoteConnected && lanState.role.isHost -> PlayerId.PLAYER_1
        lanState.isRemoteConnected && lanState.role.isGuest -> PlayerId.PLAYER_2
        !lanState.isRemoteConnected && opponentMode is OpponentMode.VsAi -> PlayerId.PLAYER_1
        else -> null
    }
    val isLocalTurn = localPlayer == null || state.activePlayer == localPlayer
    val waitingForRemoteLan = lanState.isRemoteConnected && !isLocalTurn && state.outcome == null

    val effectiveBoardFlipped = if (lanState.isRemoteConnected && localPlayer != null) {
        state.playerColors.getValue(localPlayer) == PieceColor.BLACK
    } else {
        state.boardFlipped
    }
    val displayState = state.copy(boardFlipped = effectiveBoardFlipped)

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.bg)
                .padding(innerPadding)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize()
            ) {
                if (maxWidth >= 680.dp) {
                    ExpandedTabletopLayout(
                        state = displayState,
                        lanState = lanState,
                        opponentMode = opponentMode,
                        isAiThinking = isAiThinking,
                        cardTheme = cardTheme,
                        matchStats = matchStats,
                        localPlayer = localPlayer,
                        isLocalTurn = isLocalTurn,
                        waitingForRemoteLan = waitingForRemoteLan,
                        onDrawCard = viewModel::drawCard,
                        onSquareTapped = viewModel::onSquareTapped,
                        onSelectRevivePiece = viewModel::onCapturedPieceSelected,
                        onConfirmMerge = viewModel::onConfirmMerge,
                        onClearMergeSelection = viewModel::onClearMergeSelection,
                        onSkipMerge = viewModel::onSkipMerge,
                        onConfirmSacrifice = viewModel::onConfirmSacrifice,
                        onSkipSacrifice = viewModel::onSkipSacrifice,
                        onConfirmPawnPromote = viewModel::onConfirmPawnPromote,
                        onClearSwapSelection = viewModel::onClearSwapSelection,
                        onSkipSwap = viewModel::onSkipSwap,
                        onClearCloneSelection = viewModel::onClearCloneSelection,
                        onSkipClone = viewModel::onSkipClone,
                        onSelectWildEffect = viewModel::onSelectWildEffect,
                        onDismissNotice = viewModel::dismissNotice,
                        onOpenSettings = { showSettingsModal = true },
                        onOpenLan = {
                            viewModel.refreshLocalIp()
                            showLanDialog = true
                        },
                        onOpenRules = { showRulesModal = true },
                        onOpenMatchLog = { showMatchLogModal = true },
                        onRequestForfeit = { showForfeitDialog = true },
                        onRequestReset = { showResetDialog = true },
                        onInstantNewGame = viewModel::resetGame
                    )
                } else {
                    CompactPortraitLayout(
                        state = displayState,
                        lanState = lanState,
                        opponentMode = opponentMode,
                        isAiThinking = isAiThinking,
                        cardTheme = cardTheme,
                        matchStats = matchStats,
                        localPlayer = localPlayer,
                        isLocalTurn = isLocalTurn,
                        waitingForRemoteLan = waitingForRemoteLan,
                        onDrawCard = viewModel::drawCard,
                        onSquareTapped = viewModel::onSquareTapped,
                        onSelectRevivePiece = viewModel::onCapturedPieceSelected,
                        onConfirmMerge = viewModel::onConfirmMerge,
                        onClearMergeSelection = viewModel::onClearMergeSelection,
                        onSkipMerge = viewModel::onSkipMerge,
                        onConfirmSacrifice = viewModel::onConfirmSacrifice,
                        onSkipSacrifice = viewModel::onSkipSacrifice,
                        onConfirmPawnPromote = viewModel::onConfirmPawnPromote,
                        onClearSwapSelection = viewModel::onClearSwapSelection,
                        onSkipSwap = viewModel::onSkipSwap,
                        onClearCloneSelection = viewModel::onClearCloneSelection,
                        onSkipClone = viewModel::onSkipClone,
                        onSelectWildEffect = viewModel::onSelectWildEffect,
                        onDismissNotice = viewModel::dismissNotice,
                        onOpenSettings = { showSettingsModal = true },
                        onOpenLan = {
                            viewModel.refreshLocalIp()
                            showLanDialog = true
                        },
                        onOpenRules = { showRulesModal = true },
                        onOpenMatchLog = { showMatchLogModal = true },
                        onRequestForfeit = { showForfeitDialog = true },
                        onRequestReset = { showResetDialog = true },
                        onInstantNewGame = viewModel::resetGame
                    )
                }
            }

            VictoryCelebrationOverlay(
                outcome = state.outcome,
                opponentMode = opponentMode,
                isLanConnected = lanState.isRemoteConnected,
                visible = !celebrationDismissed,
                onDismiss = { celebrationDismissed = true },
                onNewGame = {
                    celebrationDismissed = true
                    viewModel.resetGame()
                }
            )
        }
    }

    if (isLocalTurn) {
        state.pendingPromotion?.let { pending ->
            val promoColor = if (state.phase == TurnPhase.HIJACKING) {
                state.activeColor.opponent()
            } else {
                state.activeColor
            }
            PromotionPickerDialog(
                pieceColor = promoColor,
                targetSquare = pending.to,
                onSelect = { pieceType: PieceType -> viewModel.onPromotionSelected(pieceType) },
                onDismiss = viewModel::onPromotionCancelled
            )
        }
    }

    if (showSettingsModal) {
        SettingsModalDialog(
            opponentMode = opponentMode,
            isLanConnected = lanState.isRemoteConnected,
            isOnlineMatch = lanState.role.isOnline,
            isDarkTheme = isDarkTheme,
            soundEnabled = soundEnabled,
            cardTheme = cardTheme,
            onSelectOpponentMode = { mode -> viewModel.setOpponentMode(mode) },
            onSelectDarkTheme = { dark -> viewModel.setDarkTheme(context, dark) },
            onSelectSoundEnabled = { enabled -> viewModel.setSoundEnabled(context, enabled) },
            onSelectCardTheme = { theme -> viewModel.setCardTheme(context, theme) },
            appVersionName = BuildConfig.VERSION_NAME,
            updateStatus = updateStatus,
            checkingForUpdate = checkingForUpdate,
            onCheckForUpdate = { checkForUpdate(manual = true) },
            onDismiss = { showSettingsModal = false }
        )
    }

    if (showUpdateDialog) {
        pendingUpdate?.let { release ->
            AppUpdateDialog(
                versionName = release.versionName,
                notes = release.notes,
                downloading = downloadingUpdate,
                progress = downloadProgress,
                readyToInstall = downloadedApk != null,
                statusMessage = updateDialogMessage,
                onInstall = ::installPendingUpdate,
                onDismiss = { showUpdateDialog = false }
            )
        }
    }

    if (showLanDialog) {
        LanMultiplayerDialog(
            lanState = lanState,
            enabledSpecialCards = hostEnabledSpecialCards,
            onToggleSpecialCard = viewModel::toggleHostSpecialCard,
            onSetAllSpecialCards = viewModel::setAllHostSpecialCards,
            onStartHosting = { viewModel.startLanHost(context) },
            onStartDiscovery = { viewModel.startLanDiscovery(context) },
            onStopDiscovery = viewModel::stopLanDiscovery,
            onConnect = { ip, port -> viewModel.joinLanHost(ip, port) },
            onCreateOnlineLobby = viewModel::createOnlineLobby,
            onJoinOnlineLobby = viewModel::joinOnlineLobby,
            onWatchOnlineLobbies = viewModel::startOnlineLobbyListing,
            onRefreshOnlineLobbies = viewModel::refreshOnlineLobbies,
            onStopWatchingOnlineLobbies = viewModel::stopOnlineLobbyListing,
            onLeaveMatch = viewModel::leaveLanMatch,
            onDismiss = { showLanDialog = false }
        )
    }

    if (showRulesModal) {
        RulesModalDialog(onDismiss = { showRulesModal = false })
    }

    if (showMatchLogModal) {
        MatchLogModalDialog(
            matchStats = matchStats,
            gameLog = state.gameLog,
            onClearHistory = viewModel::clearMatchHistory,
            onDismiss = { showMatchLogModal = false }
        )
    }

    if (showResetDialog) {
        ResetConfirmDialog(
            onConfirm = {
                showResetDialog = false
                viewModel.resetGame()
            },
            onDismiss = { showResetDialog = false }
        )
    }

    if (showForfeitDialog) {
        ForfeitConfirmDialog(
            onConfirm = {
                showForfeitDialog = false
                viewModel.forfeitGame()
            },
            onDismiss = { showForfeitDialog = false }
        )
    }
}

@Composable
private fun CompactPortraitLayout(
    state: GameUiState,
    lanState: LanSessionState,
    opponentMode: OpponentMode,
    isAiThinking: Boolean,
    cardTheme: UnoCardTheme,
    matchStats: MatchStats,
    localPlayer: PlayerId?,
    isLocalTurn: Boolean,
    waitingForRemoteLan: Boolean,
    onDrawCard: () -> Unit,
    onSquareTapped: (Square) -> Unit,
    onSelectRevivePiece: (Int) -> Unit,
    onConfirmMerge: (PieceType) -> Unit,
    onClearMergeSelection: () -> Unit,
    onSkipMerge: () -> Unit,
    onConfirmSacrifice: (String) -> Unit,
    onSkipSacrifice: () -> Unit,
    onConfirmPawnPromote: (PieceType) -> Unit,
    onClearSwapSelection: () -> Unit,
    onSkipSwap: () -> Unit,
    onClearCloneSelection: () -> Unit,
    onSkipClone: () -> Unit,
    onSelectWildEffect: (SpecialCardKey) -> Unit,
    onDismissNotice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLan: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMatchLog: () -> Unit,
    onRequestForfeit: () -> Unit,
    onRequestReset: () -> Unit,
    onInstantNewGame: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    val topColor = if (state.boardFlipped) PieceColor.WHITE else PieceColor.BLACK
    val bottomColor = if (state.boardFlipped) PieceColor.BLACK else PieceColor.WHITE
    val topPlayer = state.playerControlling(topColor)
    val bottomPlayer = state.playerControlling(bottomColor)
    val checkedColor = state.checkedKingSquare?.let { sq -> state.board[sq.row][sq.col]?.color }

    // Maximized edge-to-edge chessboard with balanced vertical spacers and no inline Move Log.
    // Full Move Log & Match History remain 1 tap away via the top-bar Streak pill.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val showActionCard = isLocalTurn && state.outcome == null && (
            state.phase == TurnPhase.MERGING ||
                state.phase == TurnPhase.SACRIFICING ||
                state.phase == TurnPhase.PROMOTING_PAWN ||
                state.phase == TurnPhase.SWAPPING ||
                state.phase == TurnPhase.CLONING ||
                state.phase == TurnPhase.WILD_CHOOSING
            )
        val chromeHorizontalPadding = if (maxWidth >= 380.dp) 16.dp else 12.dp
        val maxBoardWidth = maxWidth.coerceAtMost(560.dp)
        // Reserve clean vertical space for header + 2 player strips + UNO deck card + active action bar
        val minChromeHeight = if (showActionCard) 324.dp else 244.dp
        val boardSize = minOf(maxBoardWidth, (maxHeight - minChromeHeight).coerceAtLeast(248.dp))
        val isEdgeToEdgeBoard = boardSize >= maxWidth - 2.dp
        val deckCardWidth = if (maxWidth >= 380.dp) 68.dp else 60.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp)
                .padding(top = 6.dp, bottom = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MinimalHeaderBar(
                isLanConnected = lanState.isRemoteConnected,
                isOnline = lanState.role.isOnline,
                canForfeit = state.outcome == null,
                currentStreak = matchStats.currentStreak,
                onOpenMatchLog = onOpenMatchLog,
                onOpenSettings = onOpenSettings,
                onOpenLan = onOpenLan,
                onOpenRules = onOpenRules,
                onRequestForfeit = onRequestForfeit,
                onRequestReset = onRequestReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = chromeHorizontalPadding)
            )

            Spacer(Modifier.weight(0.55f).heightIn(min = 6.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PlayerStripCard(
                    player = topPlayer,
                    displayName = resolvePlayerLabel(topPlayer, opponentMode, lanState.isRemoteConnected),
                    color = topColor,
                    isActiveTurn = state.activePlayer == topPlayer && state.outcome == null,
                    isInCheck = checkedColor == topColor,
                    capturedOwnPieces = state.capturedPieces[topColor].orEmpty(),
                    isReviveSelectable = isLocalTurn && state.phase == TurnPhase.REVIVING && state.activeColor == topColor,
                    selectedRevivePieceId = state.selectedRevivePieceId,
                    onSelectRevivePiece = onSelectRevivePiece,
                    showInlineCapturedTray = true,
                    isLocalUser = lanState.isRemoteConnected && localPlayer == topPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = chromeHorizontalPadding)
                )

                // Full-width edge-to-edge square chessboard + floating overlays
                Box(
                    modifier = Modifier.size(boardSize),
                    contentAlignment = Alignment.Center
                ) {
                    ChessBoardView(
                        state = state,
                        onSquareTapped = onSquareTapped,
                        edgeToEdge = isEdgeToEdgeBoard,
                        modifier = Modifier.fillMaxSize()
                    )

                    NoticeBannerHost(
                        notice = state.noticeBanner,
                        onDismiss = onDismissNotice,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    )

                    state.outcome?.let { outcome ->
                        GameOverBannerCard(
                            outcome = outcome,
                            opponentMode = opponentMode,
                            isLanConnected = lanState.isRemoteConnected,
                            onNewGame = onInstantNewGame,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                PlayerStripCard(
                    player = bottomPlayer,
                    displayName = resolvePlayerLabel(bottomPlayer, opponentMode, lanState.isRemoteConnected),
                    color = bottomColor,
                    isActiveTurn = state.activePlayer == bottomPlayer && state.outcome == null,
                    isInCheck = checkedColor == bottomColor,
                    capturedOwnPieces = state.capturedPieces[bottomColor].orEmpty(),
                    isReviveSelectable = isLocalTurn && state.phase == TurnPhase.REVIVING && state.activeColor == bottomColor,
                    selectedRevivePieceId = state.selectedRevivePieceId,
                    onSelectRevivePiece = onSelectRevivePiece,
                    showInlineCapturedTray = true,
                    isLocalUser = lanState.isRemoteConnected && localPlayer == bottomPlayer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = chromeHorizontalPadding)
                )
            }

            Spacer(Modifier.weight(1f))

            // Spacious UNO Deck + Turn Status card (Forfeit moved to top-bar Match Menu)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = chromeHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = colors.surface,
                    border = BorderStroke(1.dp, colors.hairline)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        UnoDeckAndActiveCardRow(
                            drawPileCount = state.drawPile.size,
                            canDraw = isLocalTurn && state.phase == TurnPhase.AWAITING_DRAW && state.outcome == null,
                            activeCard = state.activeCard,
                            drawRevision = state.drawRevision,
                            cardTheme = cardTheme,
                            onDrawClick = onDrawCard,
                            pityCount = state.movePityCount[state.activePlayer] ?: 0,
                            cardWidth = deckCardWidth
                        )

                        TurnStatusSummary(
                            state = state,
                            opponentMode = opponentMode,
                            isLanConnected = lanState.isRemoteConnected,
                            isAiThinking = isAiThinking,
                            waitingForRemotePlayer = waitingForRemoteLan,
                            remoteChannelLabel = if (lanState.role.isOnline) "the internet" else "Wi-Fi",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (showActionCard) {
                    when (state.phase) {
                        TurnPhase.MERGING -> MergeActionCard(
                            state = state,
                            onConfirmMerge = onConfirmMerge,
                            onClearSelection = onClearMergeSelection,
                            onSkipMerge = onSkipMerge,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TurnPhase.SACRIFICING -> SacrificeActionCard(
                            state = state,
                            onConfirmSacrifice = onConfirmSacrifice,
                            onSkipSacrifice = onSkipSacrifice,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TurnPhase.PROMOTING_PAWN -> PawnPromoteActionCard(
                            state = state,
                            onConfirmPawnPromote = onConfirmPawnPromote,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TurnPhase.SWAPPING -> SwapActionCard(
                            state = state,
                            onClearSelection = onClearSwapSelection,
                            onSkipSwap = onSkipSwap,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TurnPhase.CLONING -> CloneActionCard(
                            state = state,
                            onClearSelection = onClearCloneSelection,
                            onSkipClone = onSkipClone,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TurnPhase.WILD_CHOOSING -> WildCardActionCard(
                            state = state,
                            onSelectWildEffect = onSelectWildEffect,
                            modifier = Modifier.fillMaxWidth()
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun ExpandedTabletopLayout(
    state: GameUiState,
    lanState: LanSessionState,
    opponentMode: OpponentMode,
    isAiThinking: Boolean,
    cardTheme: UnoCardTheme,
    matchStats: MatchStats,
    localPlayer: PlayerId?,
    isLocalTurn: Boolean,
    waitingForRemoteLan: Boolean,
    onDrawCard: () -> Unit,
    onSquareTapped: (Square) -> Unit,
    onSelectRevivePiece: (Int) -> Unit,
    onConfirmMerge: (PieceType) -> Unit,
    onClearMergeSelection: () -> Unit,
    onSkipMerge: () -> Unit,
    onConfirmSacrifice: (String) -> Unit,
    onSkipSacrifice: () -> Unit,
    onConfirmPawnPromote: (PieceType) -> Unit,
    onClearSwapSelection: () -> Unit,
    onSkipSwap: () -> Unit,
    onClearCloneSelection: () -> Unit,
    onSkipClone: () -> Unit,
    onSelectWildEffect: (SpecialCardKey) -> Unit,
    onDismissNotice: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLan: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenMatchLog: () -> Unit,
    onRequestForfeit: () -> Unit,
    onRequestReset: () -> Unit,
    onInstantNewGame: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    val topColor = if (state.boardFlipped) PieceColor.WHITE else PieceColor.BLACK
    val bottomColor = if (state.boardFlipped) PieceColor.BLACK else PieceColor.WHITE
    val topPlayer = state.playerControlling(topColor)
    val bottomPlayer = state.playerControlling(bottomColor)
    val checkedColor = state.checkedKingSquare?.let { sq -> state.board[sq.row][sq.col]?.color }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1.3f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            PlayerStripCard(
                player = topPlayer,
                displayName = resolvePlayerLabel(topPlayer, opponentMode, lanState.isRemoteConnected),
                color = topColor,
                isActiveTurn = state.activePlayer == topPlayer && state.outcome == null,
                isInCheck = checkedColor == topColor,
                capturedOwnPieces = state.capturedPieces[topColor].orEmpty(),
                isReviveSelectable = isLocalTurn && state.phase == TurnPhase.REVIVING && state.activeColor == topColor,
                selectedRevivePieceId = state.selectedRevivePieceId,
                onSelectRevivePiece = onSelectRevivePiece,
                showInlineCapturedTray = false,
                isLocalUser = lanState.isRemoteConnected && localPlayer == topPlayer,
                modifier = Modifier.widthIn(max = 560.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                BoxWithConstraints(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    val boardSize = minOf(maxWidth, maxHeight)
                    ChessBoardView(
                        state = state,
                        onSquareTapped = onSquareTapped,
                        modifier = Modifier.size(boardSize)
                    )
                }
            }

            PlayerStripCard(
                player = bottomPlayer,
                displayName = resolvePlayerLabel(bottomPlayer, opponentMode, lanState.isRemoteConnected),
                color = bottomColor,
                isActiveTurn = state.activePlayer == bottomPlayer && state.outcome == null,
                isInCheck = checkedColor == bottomColor,
                capturedOwnPieces = state.capturedPieces[bottomColor].orEmpty(),
                isReviveSelectable = isLocalTurn && state.phase == TurnPhase.REVIVING && state.activeColor == bottomColor,
                selectedRevivePieceId = state.selectedRevivePieceId,
                onSelectRevivePiece = onSelectRevivePiece,
                showInlineCapturedTray = false,
                isLocalUser = lanState.isRemoteConnected && localPlayer == bottomPlayer,
                modifier = Modifier.widthIn(max = 560.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            MinimalHeaderBar(
                isLanConnected = lanState.isRemoteConnected,
                isOnline = lanState.role.isOnline,
                canForfeit = state.outcome == null,
                currentStreak = matchStats.currentStreak,
                onOpenMatchLog = onOpenMatchLog,
                onOpenSettings = onOpenSettings,
                onOpenLan = onOpenLan,
                onOpenRules = onOpenRules,
                onRequestForfeit = onRequestForfeit,
                onRequestReset = onRequestReset
            )

            NoticeBannerHost(
                notice = state.noticeBanner,
                onDismiss = onDismissNotice
            )

            state.outcome?.let { outcome ->
                GameOverBannerCard(
                    outcome = outcome,
                    opponentMode = opponentMode,
                    isLanConnected = lanState.isRemoteConnected,
                    onNewGame = onInstantNewGame
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.hairline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    UnoDeckAndActiveCardRow(
                        drawPileCount = state.drawPile.size,
                        canDraw = isLocalTurn && state.phase == TurnPhase.AWAITING_DRAW && state.outcome == null,
                        activeCard = state.activeCard,
                        drawRevision = state.drawRevision,
                        cardTheme = cardTheme,
                        onDrawClick = onDrawCard,
                        pityCount = state.movePityCount[state.activePlayer] ?: 0,
                        cardWidth = 72.dp
                    )
                    TurnStatusSummary(
                        state = state,
                        opponentMode = opponentMode,
                        isLanConnected = lanState.isRemoteConnected,
                        isAiThinking = isAiThinking,
                        waitingForRemotePlayer = waitingForRemoteLan,
                        remoteChannelLabel = if (lanState.role.isOnline) "the internet" else "Wi-Fi",
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (isLocalTurn && state.outcome == null) {
                when (state.phase) {
                    TurnPhase.MERGING -> MergeActionCard(
                        state = state,
                        onConfirmMerge = onConfirmMerge,
                        onClearSelection = onClearMergeSelection,
                        onSkipMerge = onSkipMerge
                    )
                    TurnPhase.SACRIFICING -> SacrificeActionCard(
                        state = state,
                        onConfirmSacrifice = onConfirmSacrifice,
                        onSkipSacrifice = onSkipSacrifice
                    )
                    TurnPhase.PROMOTING_PAWN -> PawnPromoteActionCard(
                        state = state,
                        onConfirmPawnPromote = onConfirmPawnPromote
                    )
                    TurnPhase.SWAPPING -> SwapActionCard(
                        state = state,
                        onClearSelection = onClearSwapSelection,
                        onSkipSwap = onSkipSwap
                    )
                    TurnPhase.CLONING -> CloneActionCard(
                        state = state,
                        onClearSelection = onClearCloneSelection,
                        onSkipClone = onSkipClone
                    )
                    TurnPhase.WILD_CHOOSING -> WildCardActionCard(
                        state = state,
                        onSelectWildEffect = onSelectWildEffect
                    )
                    else -> Unit
                }
            }

            SidePanelCapturedTraysCard(
                state = state,
                opponentMode = opponentMode,
                isLanConnected = lanState.isRemoteConnected,
                isLocalTurn = isLocalTurn,
                onSelectRevivePiece = onSelectRevivePiece
            )

            GameLogCard(
                entries = state.gameLog,
                maxHeight = 96.dp,
                onClick = onOpenMatchLog
            )
        }
    }
}

@Composable
private fun MinimalHeaderBar(
    isLanConnected: Boolean,
    isOnline: Boolean = false,
    canForfeit: Boolean = true,
    currentStreak: Int,
    onOpenMatchLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLan: () -> Unit,
    onOpenRules: () -> Unit,
    onRequestForfeit: () -> Unit,
    onRequestReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val matchLogCd = stringResource(R.string.cd_match_log_button)
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleMedium.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.ExtraBold
                ),
                maxLines = 1,
                softWrap = false
            )

            // Tactile Win Streak & Match Log pill (48dp touch height)
            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("match_log_button")
                    .semantics { contentDescription = matchLogCd }
                    .clickable(onClick = onOpenMatchLog),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (currentStreak > 0) {
                                colors.accentTrade.copy(alpha = 0.14f)
                            } else {
                                colors.surfaceSubtle
                            }
                        )
                        .border(
                            width = 1.dp,
                            color = if (currentStreak > 0) {
                                colors.accentTrade.copy(alpha = 0.5f)
                            } else {
                                colors.hairline
                            },
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (currentStreak > 0) colors.accentTrade else colors.inkMuted,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = "$currentStreak",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (currentStreak > 0) colors.accentTrade else colors.inkSecondary,
                            fontWeight = FontWeight.Bold
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }

        // 3 comfortable 48dp touch targets — Multiplayer, Settings, and Match Actions menu
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            IconButton(
                onClick = onOpenLan,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("lan_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isOnline) Icons.Rounded.Public else Icons.Rounded.Wifi,
                        contentDescription = stringResource(R.string.cd_lan_button),
                        tint = if (isLanConnected) colors.accentRevive else colors.inkSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                    if (isLanConnected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.accentRevive)
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.cd_settings_button),
                    tint = colors.inkPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("match_menu_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.cd_match_menu_button),
                        tint = colors.inkPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = colors.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, colors.hairline)
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_rules),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.inkPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                                contentDescription = null,
                                tint = colors.inkSecondary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onOpenRules()
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("rules_button")
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_new_game),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = colors.inkPrimary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = colors.inkPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onRequestReset()
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("new_game_button")
                    )

                    HorizontalDivider(
                        color = colors.hairline,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(R.string.action_forfeit_match),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = if (canForfeit) colors.accentCheck else colors.inkMuted,
                                    fontWeight = FontWeight.SemiBold
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Flag,
                                contentDescription = null,
                                tint = if (canForfeit) colors.accentCheck else colors.inkMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        enabled = canForfeit,
                        onClick = {
                            menuExpanded = false
                            onRequestForfeit()
                        },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("forfeit_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun NoticeBannerHost(
    notice: NoticeBanner?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current

    AnimatedVisibility(
        visible = notice != null,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        if (notice != null) {
            val bg = if (notice.isAlert) colors.accentCheck else colors.inkPrimary
            val fg = if (notice.isAlert) androidx.compose.ui.graphics.Color.White else colors.surface
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg.copy(alpha = 0.95f))
                    .clickable { onDismiss() }
                    .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    .testTag("notice_banner"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = notice.message,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = fg,
                        fontWeight = FontWeight.Medium
                    ),
                    maxLines = 2,
                    modifier = Modifier
                        .weight(1f)
                        .padding(vertical = 4.dp)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = fg.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
