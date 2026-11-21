package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.engine.UnoDeck
import com.example.model.SpecialCardKey
import com.example.network.DiscoveredHost
import com.example.network.LanConnectionStatus
import com.example.network.LanSessionState
import com.example.network.NetworkRole
import com.example.network.OnlineLobbySummary
import com.example.ui.theme.LocalUnoChessColors
import kotlinx.coroutines.delay

@Composable
fun LanMultiplayerDialog(
    lanState: LanSessionState,
    enabledSpecialCards: Set<SpecialCardKey>,
    onToggleSpecialCard: (SpecialCardKey) -> Unit,
    onSetAllSpecialCards: (Boolean) -> Unit,
    onStartHosting: () -> Unit,
    onStartDiscovery: () -> Unit,
    onStopDiscovery: () -> Unit,
    onConnect: (String, Int) -> Unit,
    onCreateOnlineLobby: (String) -> Unit,
    onJoinOnlineLobby: (String, String) -> Unit,
    onWatchOnlineLobbies: () -> Unit,
    onRefreshOnlineLobbies: () -> Unit,
    onStopWatchingOnlineLobbies: () -> Unit,
    onLeaveMatch: () -> Unit,
    onDismiss: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var linkMode by rememberSaveable { mutableIntStateOf(0) }
    var manualIp by rememberSaveable { mutableStateOf("") }
    var manualPort by rememberSaveable { mutableStateOf("") }
    var onlineName by rememberSaveable { mutableStateOf("Player") }
    var onlineCode by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(lanState.role) {
        if (lanState.role.isOnline) linkMode = 1
        if (lanState.role.isLan) linkMode = 0
    }

    LaunchedEffect(linkMode, selectedTab, lanState.isRemoteConnected) {
        val browsing = !lanState.isRemoteConnected && selectedTab == 1
        if (linkMode == 0 && browsing) onStartDiscovery() else onStopDiscovery()
        if (linkMode == 1 && browsing) onWatchOnlineLobbies() else onStopWatchingOnlineLobbies()
    }

    DisposableEffect(Unit) {
        onDispose {
            onStopDiscovery()
            onStopWatchingOnlineLobbies()
        }
    }

    val activeErrorMessage = if (linkMode == 1) {
        lanState.onlineErrorMessage ?: lanState.errorMessage.takeIf { lanState.role.isOnline }
    } else {
        lanState.errorMessage.takeIf { !lanState.role.isOnline }
    }

    val isLanHostWaiting = lanState.role == NetworkRole.LAN_HOST &&
        lanState.status == LanConnectionStatus.HOSTING_WAITING
    val isLanConnecting = lanState.role == NetworkRole.LAN_GUEST &&
        lanState.status == LanConnectionStatus.CONNECTING
    val isOnlineHostWaiting = lanState.role == NetworkRole.ONLINE_HOST &&
        lanState.status == LanConnectionStatus.HOSTING_WAITING
    val isOnlineCreating = lanState.role == NetworkRole.ONLINE_HOST &&
        lanState.status == LanConnectionStatus.CONNECTING
    val isOnlineJoining = lanState.role == NetworkRole.ONLINE_GUEST &&
        lanState.status == LanConnectionStatus.CONNECTING

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier
                .safeDrawingPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .testTag("lan_modal")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Pinned compact header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (linkMode == 1 || lanState.role.isOnline) {
                                Icons.Rounded.Public
                            } else {
                                Icons.Rounded.Wifi
                            },
                            contentDescription = null,
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.lan_modal_title),
                            style = MaterialTheme.typography.titleLarge.copy(color = colors.inkPrimary)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("lan_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colors.inkSecondary
                        )
                    }
                }

                HorizontalDivider(color = colors.hairline)

                if (lanState.isRemoteConnected) {
                    ConnectedLanView(
                        lanState = lanState,
                        onLeaveMatch = {
                            onLeaveMatch()
                            onDismiss()
                        }
                    )
                } else {
                    // Pinned mode + tab selectors
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinkModeSwitch(
                            selected = linkMode,
                            onSelect = { linkMode = it }
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceSubtle)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            listOf(
                                stringResource(R.string.lan_tab_host),
                                stringResource(R.string.lan_tab_join)
                            ).forEachIndexed { index, label ->
                                val selected = selectedTab == index
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(42.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (selected) colors.surface else colors.surfaceSubtle)
                                        .then(
                                            if (selected) {
                                                Modifier.border(
                                                    1.dp,
                                                    colors.hairline,
                                                    RoundedCornerShape(8.dp)
                                                )
                                            } else {
                                                Modifier
                                            }
                                        )
                                        .testTag("lan_tab_$index")
                                        .clickable { selectedTab = index },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelLarge.copy(
                                            color = if (selected) colors.inkPrimary else colors.inkSecondary,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    )
                                }
                            }
                        }

                        if (activeErrorMessage != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.accentCheck.copy(alpha = 0.12f))
                                    .border(1.dp, colors.accentCheck.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                                    .padding(horizontal = 12.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = activeErrorMessage,
                                    style = MaterialTheme.typography.bodySmall.copy(color = colors.accentCheck),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("lan_error_text")
                                )
                                if (linkMode == 1 && selectedTab == 1) {
                                    Box(
                                        modifier = Modifier
                                            .height(34.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(colors.surface)
                                            .border(1.dp, colors.accentCheck.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                                            .clickable { onRefreshOnlineLobbies() }
                                            .padding(horizontal = 10.dp)
                                            .testTag("online_retry_button"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = stringResource(R.string.action_retry),
                                            style = MaterialTheme.typography.labelMedium.copy(
                                                color = colors.accentCheck,
                                                fontWeight = FontWeight.Bold
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Flexible scrollable body so the dialog never clips on any screen height
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                            .padding(start = 20.dp, end = 20.dp, top = 2.dp, bottom = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        when {
                            linkMode == 0 && selectedTab == 0 -> {
                                HostTabBody(
                                    lanState = lanState,
                                    isWaiting = isLanHostWaiting,
                                    enabledSpecialCards = enabledSpecialCards,
                                    onToggleSpecialCard = onToggleSpecialCard,
                                    onSetAllSpecialCards = onSetAllSpecialCards
                                )
                            }
                            linkMode == 0 -> {
                                JoinTabBody(
                                    lanState = lanState,
                                    isConnecting = isLanConnecting,
                                    manualIp = manualIp,
                                    manualPort = manualPort,
                                    onManualIpChange = { manualIp = it },
                                    onManualPortChange = { manualPort = it.filter { ch -> ch.isDigit() } },
                                    onSelectHost = { host -> onConnect(host.hostAddress, host.port) }
                                )
                            }
                            selectedTab == 0 -> {
                                OnlineHostBody(
                                    lanState = lanState,
                                    isWaiting = isOnlineHostWaiting,
                                    isCreating = isOnlineCreating,
                                    displayName = onlineName,
                                    onDisplayNameChange = { onlineName = it.take(24) },
                                    enabledSpecialCards = enabledSpecialCards,
                                    onToggleSpecialCard = onToggleSpecialCard,
                                    onSetAllSpecialCards = onSetAllSpecialCards
                                )
                            }
                            else -> {
                                OnlineJoinBody(
                                    lanState = lanState,
                                    isConnecting = isOnlineJoining,
                                    displayName = onlineName,
                                    onDisplayNameChange = { onlineName = it.take(24) },
                                    code = onlineCode,
                                    onCodeChange = {
                                        onlineCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8)
                                    },
                                    onRefreshLobbies = onRefreshOnlineLobbies,
                                    onJoinLobby = { lobby -> onJoinOnlineLobby(lobby.code, onlineName) }
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = colors.hairline)

                    // Pinned primary action footer (always 100% visible above system nav bar)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        when {
                            linkMode == 0 && selectedTab == 0 -> {
                                if (isLanHostWaiting) {
                                    OutlinedButton(
                                        onClick = onLeaveMatch,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("lan_stop_hosting_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.lan_stop_hosting),
                                            color = colors.inkPrimary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = onStartHosting,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.inkPrimary,
                                            contentColor = colors.surface
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("lan_start_hosting_button")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.lan_start_hosting),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                            linkMode == 0 -> {
                                Button(
                                    onClick = {
                                        val parsedPort = manualPort.toIntOrNull() ?: 0
                                        onConnect(manualIp, parsedPort)
                                    },
                                    enabled = !isLanConnecting && manualIp.isNotBlank() && manualPort.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.inkPrimary,
                                        contentColor = colors.surface
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("lan_connect_button")
                                ) {
                                    if (isLanConnecting) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.surface
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Connecting…", style = MaterialTheme.typography.labelLarge)
                                    } else {
                                        Text(
                                            text = stringResource(R.string.lan_connect),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                            selectedTab == 0 -> {
                                if (isOnlineHostWaiting) {
                                    OutlinedButton(
                                        onClick = onLeaveMatch,
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("online_cancel_lobby")
                                    ) {
                                        Text(
                                            text = stringResource(R.string.lan_stop_hosting),
                                            color = colors.inkPrimary,
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = { onCreateOnlineLobby(onlineName) },
                                        enabled = !isOnlineCreating && onlineName.isNotBlank(),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = colors.inkPrimary,
                                            contentColor = colors.surface
                                        ),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .testTag("online_create_lobby")
                                    ) {
                                        if (isOnlineCreating) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(18.dp),
                                                strokeWidth = 2.dp,
                                                color = colors.surface
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.online_creating_lobby),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        } else {
                                            Text(
                                                text = stringResource(R.string.online_create_lobby),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        }
                                    }
                                }
                            }
                            else -> {
                                Button(
                                    onClick = { onJoinOnlineLobby(onlineCode, onlineName) },
                                    enabled = !isOnlineJoining && onlineCode.length >= 4 && onlineName.isNotBlank(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = colors.inkPrimary,
                                        contentColor = colors.surface
                                    ),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("online_join_button")
                                ) {
                                    if (isOnlineJoining) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.surface
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text("Joining…", style = MaterialTheme.typography.labelLarge)
                                    } else {
                                        Text(
                                            text = stringResource(R.string.online_join_lobby),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectedLanView(
    lanState: LanSessionState,
    onLeaveMatch: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    val roleLabel = if (lanState.role.isHost) {
        "Host · Player 1"
    } else {
        "Guest · Player 2"
    }
    val channelLabel = if (lanState.role.isOnline) "Online" else "Wi-Fi"

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceSubtle)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.accentRevive)
            )
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Connected · $channelLabel ($roleLabel)",
                    style = MaterialTheme.typography.titleSmall.copy(color = colors.inkPrimary)
                )
                if (lanState.peerAddress.isNotEmpty()) {
                    Text(
                        text = "Peer: ${lanState.peerAddress}",
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
                    )
                }
            }
        }

        OutlinedButton(
            onClick = onLeaveMatch,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("lan_leave_button")
        ) {
            Text(
                text = stringResource(
                    if (lanState.role.isOnline) R.string.online_leave_match else R.string.lan_leave_match
                ),
                color = colors.accentCheck,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

@Composable
private fun HostTabBody(
    lanState: LanSessionState,
    isWaiting: Boolean,
    enabledSpecialCards: Set<SpecialCardKey>,
    onToggleSpecialCard: (SpecialCardKey) -> Unit,
    onSetAllSpecialCards: (Boolean) -> Unit
) {
    val colors = LocalUnoChessColors.current
    val totalDeckSize = UnoDeck.deckSizeFor(enabledSpecialCards)

    if (isWaiting) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceSubtle)
                .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.inkPrimary
                )
                Text(
                    text = "Waiting for Player 2…",
                    style = MaterialTheme.typography.titleSmall.copy(color = colors.inkPrimary)
                )
            }
            Text(
                text = "${lanState.localIp}:${lanState.hostPort}",
                style = MaterialTheme.typography.titleLarge.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.Bold
                ),
                modifier = Modifier.testTag("lan_host_address")
            )
            Text(
                text = "Custom Deck: $totalDeckSize cards (${enabledSpecialCards.size}/${SpecialCardKey.entries.size} special types)",
                style = MaterialTheme.typography.labelMedium.copy(
                    color = colors.inkPrimary,
                    fontWeight = FontWeight.SemiBold
                ),
                modifier = Modifier.testTag("lan_host_deck_summary")
            )
            Text(
                text = stringResource(R.string.lan_waiting_guest),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
            )
        }
    } else {
        Text(
            text = "Host a match as Player 1 on your Wi-Fi network. Customize which special cards are shuffled into the deck:",
            style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
        )

        SpecialCardDeckPicker(
            enabledSpecialCards = enabledSpecialCards,
            onToggleSpecialCard = onToggleSpecialCard,
            onSetAllSpecialCards = onSetAllSpecialCards
        )
    }
}

@Composable
private fun JoinTabBody(
    lanState: LanSessionState,
    isConnecting: Boolean,
    manualIp: String,
    manualPort: String,
    onManualIpChange: (String) -> Unit,
    onManualPortChange: (String) -> Unit,
    onSelectHost: (DiscoveredHost) -> Unit
) {
    val colors = LocalUnoChessColors.current

    Text(
        text = stringResource(R.string.lan_nearby_title),
        style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
    )

    if (lanState.discoveredHosts.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceSubtle)
                .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lanState.isDiscovering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.inkSecondary
                )
            }
            Text(
                text = stringResource(R.string.lan_nearby_empty),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkMuted)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lanState.discoveredHosts.forEach { host ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSubtle)
                        .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isConnecting) { onSelectHost(host) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = host.serviceName,
                            style = MaterialTheme.typography.titleSmall.copy(color = colors.inkPrimary)
                        )
                        Text(
                            text = "${host.hostAddress}:${host.port}",
                            style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Join",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.inkPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(color = colors.hairline)

    Text(
        text = stringResource(R.string.lan_manual_title),
        style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = manualIp,
            onValueChange = onManualIpChange,
            label = { Text("IP Address") },
            placeholder = { Text(stringResource(R.string.lan_ip_hint)) },
            singleLine = true,
            modifier = Modifier
                .weight(1.5f)
                .testTag("lan_ip_input")
        )
        OutlinedTextField(
            value = manualPort,
            onValueChange = onManualPortChange,
            label = { Text(stringResource(R.string.lan_port_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier
                .weight(0.9f)
                .testTag("lan_port_input")
        )
    }
}

@Composable
private fun LinkModeSwitch(
    selected: Int,
    onSelect: (Int) -> Unit
) {
    val colors = LocalUnoChessColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceSubtle)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        listOf(
            stringResource(R.string.link_wifi) to Icons.Rounded.Wifi,
            stringResource(R.string.link_online) to Icons.Rounded.Public
        ).forEachIndexed { index, (label, icon) ->
            val isSelected = selected == index
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(42.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.surface else colors.surfaceSubtle)
                    .then(
                        if (isSelected) {
                            Modifier.border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                        } else {
                            Modifier
                        }
                    )
                    .testTag(if (index == 0) "link_wifi" else "link_online")
                    .clickable { onSelect(index) },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) colors.inkPrimary else colors.inkSecondary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = if (isSelected) colors.inkPrimary else colors.inkSecondary,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                    )
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpecialCardDeckPicker(
    enabledSpecialCards: Set<SpecialCardKey>,
    onToggleSpecialCard: (SpecialCardKey) -> Unit,
    onSetAllSpecialCards: (Boolean) -> Unit
) {
    val colors = LocalUnoChessColors.current
    val totalDeckSize = UnoDeck.deckSizeFor(enabledSpecialCards)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceSubtle)
            .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
            .padding(12.dp)
            .testTag("host_special_cards_panel"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Special Cards",
                    style = MaterialTheme.typography.labelLarge.copy(
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = "Deck: $totalDeckSize cards (16 numbers + ${totalDeckSize - 16} special)",
                    style = MaterialTheme.typography.labelSmall.copy(color = colors.inkMuted),
                    modifier = Modifier.testTag("host_deck_size_label")
                )
            }
            Spacer(Modifier.width(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                        .testTag("host_cards_all_button")
                        .clickable { onSetAllSpecialCards(true) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "All",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = colors.inkPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                        .testTag("host_cards_clear_button")
                        .clickable { onSetAllSpecialCards(false) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "None",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = colors.inkSecondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
        }
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SpecialCardKey.entries.forEach { key ->
                val active = key in enabledSpecialCards
                Row(
                    modifier = Modifier
                        .heightIn(min = 38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (active) colors.inkPrimary else colors.surface)
                        .border(
                            width = 1.dp,
                            color = if (active) colors.inkPrimary else colors.hairline,
                            shape = RoundedCornerShape(9.dp)
                        )
                        .testTag("host_card_toggle_${key.name.lowercase()}")
                        .clickable { onToggleSpecialCard(key) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (active) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.surface,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = "${key.shortSymbol} ${key.displayName}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (active) colors.surface else colors.inkSecondary,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                        ),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }
        }
    }
}

@Composable
private fun OnlineHostBody(
    lanState: LanSessionState,
    isWaiting: Boolean,
    isCreating: Boolean,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    enabledSpecialCards: Set<SpecialCardKey>,
    onToggleSpecialCard: (SpecialCardKey) -> Unit,
    onSetAllSpecialCards: (Boolean) -> Unit
) {
    val colors = LocalUnoChessColors.current
    val totalDeckSize = UnoDeck.deckSizeFor(enabledSpecialCards)
    val clipboardManager = LocalClipboardManager.current
    var copiedCode by remember { mutableStateOf(false) }

    LaunchedEffect(copiedCode) {
        if (copiedCode) {
            delay(1800)
            copiedCode = false
        }
    }

    if (isWaiting) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.surfaceSubtle)
                .border(1.dp, colors.hairline, RoundedCornerShape(12.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.inkPrimary
                )
                Text(
                    text = "Waiting for Player 2…",
                    style = MaterialTheme.typography.titleSmall.copy(color = colors.inkPrimary)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = lanState.roomCode,
                    style = MaterialTheme.typography.headlineSmall.copy(
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    ),
                    modifier = Modifier.testTag("online_room_code")
                )
                Row(
                    modifier = Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.surface)
                        .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                        .clickable {
                            clipboardManager.setText(AnnotatedString(lanState.roomCode))
                            copiedCode = true
                        }
                        .padding(horizontal = 10.dp)
                        .testTag("online_copy_code_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Icon(
                        imageVector = if (copiedCode) Icons.Rounded.Check else Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        tint = if (copiedCode) colors.accentRevive else colors.inkPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = stringResource(
                            if (copiedCode) R.string.online_code_copied else R.string.online_copy_code
                        ),
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = if (copiedCode) colors.accentRevive else colors.inkPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                }
            }
            Text(
                text = "Custom Deck: $totalDeckSize cards (${enabledSpecialCards.size}/${SpecialCardKey.entries.size} special types)",
                style = MaterialTheme.typography.labelMedium.copy(color = colors.inkPrimary)
            )
            Text(
                text = stringResource(R.string.online_waiting_guest),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
            )
        }
    } else {
        Text(
            text = stringResource(R.string.online_host_blurb),
            style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
        )
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            enabled = !isCreating,
            label = { Text(stringResource(R.string.online_name_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("online_name_input")
        )
        SpecialCardDeckPicker(
            enabledSpecialCards = enabledSpecialCards,
            onToggleSpecialCard = onToggleSpecialCard,
            onSetAllSpecialCards = onSetAllSpecialCards
        )
    }
}

@Composable
private fun OnlineJoinBody(
    lanState: LanSessionState,
    isConnecting: Boolean,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    onRefreshLobbies: () -> Unit,
    onJoinLobby: (OnlineLobbySummary) -> Unit
) {
    val colors = LocalUnoChessColors.current

    OutlinedTextField(
        value = displayName,
        onValueChange = onDisplayNameChange,
        enabled = !isConnecting,
        label = { Text(stringResource(R.string.online_name_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("online_join_name_input")
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = stringResource(R.string.online_open_lobbies),
            style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
        )
        IconButton(
            onClick = onRefreshLobbies,
            enabled = !lanState.isListingLobbies,
            modifier = Modifier
                .size(36.dp)
                .testTag("online_refresh_lobbies_button")
        ) {
            Icon(
                imageVector = Icons.Rounded.Refresh,
                contentDescription = stringResource(R.string.cd_refresh_lobbies),
                tint = colors.inkSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (lanState.openLobbies.isEmpty()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceSubtle)
                .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (lanState.isListingLobbies) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = colors.inkSecondary
                )
            }
            Text(
                text = stringResource(
                    if (lanState.isListingLobbies) R.string.online_checking_lobbies
                    else R.string.online_no_lobbies
                ),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkMuted)
            )
        }
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            lanState.openLobbies.forEach { lobby ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSubtle)
                        .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isConnecting) { onJoinLobby(lobby) }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .testTag("online_lobby_${lobby.code}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Text(
                            text = lobby.hostName,
                            style = MaterialTheme.typography.titleSmall.copy(color = colors.inkPrimary)
                        )
                        Text(
                            text = lobby.code,
                            style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.surface)
                            .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Join",
                            style = MaterialTheme.typography.labelMedium.copy(
                                color = colors.inkPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                    }
                }
            }
        }
    }

    HorizontalDivider(color = colors.hairline)

    OutlinedTextField(
        value = code,
        onValueChange = onCodeChange,
        enabled = !isConnecting,
        label = { Text(stringResource(R.string.online_code_hint)) },
        singleLine = true,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("online_code_input")
    )
}
