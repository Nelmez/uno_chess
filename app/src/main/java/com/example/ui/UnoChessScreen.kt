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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
import com.example.ui.components.SettingsModalDialog
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

    LaunchedEffect(state.outcome) {
        celebrationDismissed = false
    }

    LaunchedEffect(lanState.isLanConnected) {
        if (lanState.isLanConnected && showLanDialog) {
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
        lanState.isLanConnected && lanState.role == NetworkRole.LAN_HOST -> PlayerId.PLAYER_1
        lanState.isLanConnected && lanState.role == NetworkRole.LAN_GUEST -> PlayerId.PLAYER_2
        !lanState.isLanConnected && opponentMode is OpponentMode.VsAi -> PlayerId.PLAYER_1
        else -> null
    }
    val isLocalTurn = localPlayer == null || state.activePlayer == localPlayer
    val waitingForRemoteLan = lanState.isLanConnected && !isLocalTurn && state.outcome == null

    val effectiveBoardFlipped = if (lanState.isLanConnected && localPlayer != null) {
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
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
                isLanConnected = lanState.isLanConnected,
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
            isLanConnected = lanState.isLanConnected,
            isDarkTheme = isDarkTheme,
            soundEnabled = soundEnabled,
            cardTheme = cardTheme,
            onSelectOpponentMode = { mode -> viewModel.setOpponentMode(mode) },
            onSelectDarkTheme = { dark -> viewModel.setDarkTheme(context, dark) },
            onSelectSoundEnabled = { enabled -> viewModel.setSoundEnabled(context, enabled) },
            onSelectCardTheme = { theme -> viewModel.setCardTheme(context, theme) },
            onDismiss = { showSettingsModal = false }
        )
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

    // Single-screen non-scrolling layout: size the board snugly between player strips
    // and let the UNO card panel + Move Log fill all remaining bottom space with zero dead gaps.
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val availableWidth = maxWidth.coerceAtMost(560.dp)
        // Guarantee room for header + 2 player strips + card UI + move log even on compact screens
        val minChromeHeight = 272.dp
        val boardSize = minOf(availableWidth, (maxHeight - minChromeHeight).coerceAtLeast(220.dp))
        val bottomSpace = (maxHeight - boardSize - 110.dp).coerceAtLeast(150.dp)
        val isSpaciousBottom = bottomSpace >= 220.dp
        val deckCardWidth = if (isSpaciousBottom) 66.dp else 54.dp

        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.spacedBy(if (isSpaciousBottom) 8.dp else 5.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            MinimalHeaderBar(
                isLanConnected = lanState.isLanConnected,
                currentStreak = matchStats.currentStreak,
                onOpenMatchLog = onOpenMatchLog,
                onOpenSettings = onOpenSettings,
                onOpenLan = onOpenLan,
                onOpenRules = onOpenRules,
                onRequestReset = onRequestReset,
                modifier = Modifier.fillMaxWidth()
            )

            PlayerStripCard(
                player = topPlayer,
                displayName = resolvePlayerLabel(topPlayer, opponentMode, lanState.isLanConnected),
                color = topColor,
                isActiveTurn = state.activePlayer == topPlayer && state.outcome == null,
                isInCheck = checkedColor == topColor,
                capturedOwnPieces = state.capturedPieces[topColor].orEmpty(),
                isReviveSelectable = isLocalTurn && state.phase == TurnPhase.REVIVING && state.activeColor == topColor,
                selectedRevivePieceId = state.selectedRevivePieceId,
                onSelectRevivePiece = onSelectRevivePiece,
                showInlineCapturedTray = true,
                isLocalUser = lanState.isLanConnected && localPlayer == topPlayer,
                modifier = Modifier.fillMaxWidth()
            )

            // Snug square chessboard + floating notice / game-over overlays (no vertical dead voids)
            Box(
                modifier = Modifier.size(boardSize),
                contentAlignment = Alignment.Center
            ) {
                ChessBoardView(
                    state = state,
                    onSquareTapped = onSquareTapped,
                    modifier = Modifier.fillMaxSize()
                )

                NoticeBannerHost(
                    notice = state.noticeBanner,
                    onDismiss = onDismissNotice,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(6.dp)
                )

                state.outcome?.let { outcome ->
                    GameOverBannerCard(
                        outcome = outcome,
                        opponentMode = opponentMode,
                        isLanConnected = lanState.isLanConnected,
                        onNewGame = onInstantNewGame,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(6.dp)
                    )
                }
            }

            PlayerStripCard(
                player = bottomPlayer,
                displayName = resolvePlayerLabel(bottomPlayer, opponentMode, lanState.isLanConnected),
                color = bottomColor,
                isActiveTurn = state.activePlayer == bottomPlayer && state.outcome == null,
                isInCheck = checkedColor == bottomColor,
                capturedOwnPieces = state.capturedPieces[bottomColor].orEmpty(),
                isReviveSelectable = isLocalTurn && state.phase == TurnPhase.REVIVING && state.activeColor == bottomColor,
                selectedRevivePieceId = state.selectedRevivePieceId,
                onSelectRevivePiece = onSelectRevivePiece,
                showInlineCapturedTray = true,
                isLocalUser = lanState.isLanConnected && localPlayer == bottomPlayer,
                modifier = Modifier.fillMaxWidth()
            )

            // Generously proportioned UNO Deck + Turn Status + Forfeit card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.hairline)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            horizontal = if (isSpaciousBottom) 12.dp else 8.dp,
                            vertical = if (isSpaciousBottom) 11.dp else 7.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isSpaciousBottom) 12.dp else 10.dp)
                ) {
                    UnoDeckAndActiveCardRow(
                        drawPileCount = state.drawPile.size,
                        canDraw = isLocalTurn && state.phase == TurnPhase.AWAITING_DRAW && state.outcome == null,
                        activeCard = state.activeCard,
                        drawRevision = state.drawRevision,
                        cardTheme = cardTheme,
                        onDrawClick = onDrawCard,
                        cardWidth = deckCardWidth
                    )

                    TurnStatusSummary(
                        state = state,
                        opponentMode = opponentMode,
                        isLanConnected = lanState.isLanConnected,
                        isAiThinking = isAiThinking,
                        waitingForRemotePlayer = waitingForRemoteLan,
                        modifier = Modifier.weight(1f)
                    )

                    ForfeitCompactButton(
                        enabled = state.outcome == null,
                        onClick = onRequestForfeit
                    )
                }
            }

            // Context-sensitive action panel (when active)
            val showActionCard = isLocalTurn && state.outcome == null && (
                state.phase == TurnPhase.MERGING ||
                    state.phase == TurnPhase.SACRIFICING ||
                    state.phase == TurnPhase.PROMOTING_PAWN ||
                    state.phase == TurnPhase.SWAPPING ||
                    state.phase == TurnPhase.CLONING ||
                    state.phase == TurnPhase.WILD_CHOOSING
                )
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

            // Move Log expands to fill ALL remaining bottom space cleanly!
            if (!showActionCard || isSpaciousBottom) {
                GameLogCard(
                    entries = state.gameLog,
                    maxHeight = null,
                    showHeader = !showActionCard,
                    onClick = onOpenMatchLog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
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
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(18.dp)
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
                displayName = resolvePlayerLabel(topPlayer, opponentMode, lanState.isLanConnected),
                color = topColor,
                isActiveTurn = state.activePlayer == topPlayer && state.outcome == null,
                isInCheck = checkedColor == topColor,
                capturedOwnPieces = state.capturedPieces[topColor].orEmpty(),
                isReviveSelectable = isLocalTurn && state.phase == TurnPhase.REVIVING && state.activeColor == topColor,
                selectedRevivePieceId = state.selectedRevivePieceId,
                onSelectRevivePiece = onSelectRevivePiece,
                showInlineCapturedTray = false,
                isLocalUser = lanState.isLanConnected && localPlayer == topPlayer,
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
                displayName = resolvePlayerLabel(bottomPlayer, opponentMode, lanState.isLanConnected),
                color = bottomColor,
                isActiveTurn = state.activePlayer == bottomPlayer && state.outcome == null,
                isInCheck = checkedColor == bottomColor,
                capturedOwnPieces = state.capturedPieces[bottomColor].orEmpty(),
                isReviveSelectable = isLocalTurn && state.phase == TurnPhase.REVIVING && state.activeColor == bottomColor,
                selectedRevivePieceId = state.selectedRevivePieceId,
                onSelectRevivePiece = onSelectRevivePiece,
                showInlineCapturedTray = false,
                isLocalUser = lanState.isLanConnected && localPlayer == bottomPlayer,
                modifier = Modifier.widthIn(max = 560.dp)
            )
        }

        Column(
            modifier = Modifier
                .weight(0.95f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            MinimalHeaderBar(
                isLanConnected = lanState.isLanConnected,
                currentStreak = matchStats.currentStreak,
                onOpenMatchLog = onOpenMatchLog,
                onOpenSettings = onOpenSettings,
                onOpenLan = onOpenLan,
                onOpenRules = onOpenRules,
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
                    isLanConnected = lanState.isLanConnected,
                    onNewGame = onInstantNewGame
                )
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = colors.surface,
                border = BorderStroke(1.dp, colors.hairline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
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
                        cardWidth = 72.dp
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TurnStatusSummary(
                            state = state,
                            opponentMode = opponentMode,
                            isLanConnected = lanState.isLanConnected,
                            isAiThinking = isAiThinking,
                            waitingForRemotePlayer = waitingForRemoteLan,
                            modifier = Modifier.weight(1f)
                        )
                        ForfeitCompactButton(
                            enabled = state.outcome == null,
                            onClick = onRequestForfeit
                        )
                    }
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
                isLanConnected = lanState.isLanConnected,
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
    currentStreak: Int,
    onOpenMatchLog: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenLan: () -> Unit,
    onOpenRules: () -> Unit,
    onRequestReset: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val matchLogCd = stringResource(R.string.cd_match_log_button)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

            // Compact Win Streak & Match Log pill (48dp touch height via vertical click box)
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
                        .clip(RoundedCornerShape(14.dp))
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
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalFireDepartment,
                        contentDescription = null,
                        tint = if (currentStreak > 0) colors.accentTrade else colors.inkMuted,
                        modifier = Modifier.size(14.dp)
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

        // Crisp icon-only actions — zero text wrapping on any screen width (e.g. Samsung portrait)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            IconButton(
                onClick = onOpenLan,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("lan_button")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Rounded.Wifi,
                        contentDescription = stringResource(R.string.cd_lan_button),
                        tint = if (isLanConnected) colors.accentRevive else colors.inkSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                    if (isLanConnected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(colors.accentRevive)
                        )
                    }
                }
            }

            IconButton(
                onClick = onOpenRules,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("rules_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.HelpOutline,
                    contentDescription = stringResource(R.string.cd_rules_button),
                    tint = colors.inkSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onRequestReset,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("new_game_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.cd_new_game_button),
                    tint = colors.inkPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(44.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Tune,
                    contentDescription = stringResource(R.string.cd_settings_button),
                    tint = colors.inkPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun ForfeitCompactButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val cdText = stringResource(R.string.cd_forfeit_button)

    Column(
        modifier = modifier
            .size(width = 50.dp, height = 52.dp)
            .alpha(if (enabled) 1f else 0.35f)
            .clip(RoundedCornerShape(8.dp))
            .background(colors.accentCheck.copy(alpha = 0.08f))
            .border(1.dp, colors.accentCheck.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .testTag("forfeit_button")
            .semantics { contentDescription = cdText }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Flag,
            contentDescription = null,
            tint = colors.accentCheck,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(R.string.action_forfeit),
            style = MaterialTheme.typography.labelSmall.copy(
                color = colors.accentCheck,
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp
            ),
            maxLines = 1,
            softWrap = false
        )
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
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg.copy(alpha = 0.94f))
                    .clickable { onDismiss() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
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
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = fg.copy(alpha = 0.85f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}
