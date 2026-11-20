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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Public
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.engine.UnoDeck
import com.example.model.SpecialCardKey
import com.example.network.DiscoveredHost
import com.example.network.LanConnectionStatus
import com.example.network.LanSessionState
import com.example.network.NetworkRole
import com.example.network.OnlineLobbySummary
import com.example.ui.theme.LocalUnoChessColors

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

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("lan_modal")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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
                        modifier = Modifier.testTag("lan_close_button")
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
                    LinkModeSwitch(
                        selected = linkMode,
                        onSelect = { linkMode = it }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSubtle)
                            .padding(3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        listOf(
                            stringResource(R.string.lan_tab_host),
                            stringResource(R.string.lan_tab_join)
                        ).forEachIndexed { index, label ->
                            val selected = selectedTab == index
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (selected) colors.surface else colors.surfaceSubtle)
                                    .testTag("lan_tab_$index")
                                    .clickable { selectedTab = index },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        color = if (selected) colors.inkPrimary else colors.inkSecondary
                                    )
                                )
                            }
                        }
                    }

                    if (lanState.errorMessage != null) {
                        Text(
                            text = lanState.errorMessage,
                            style = MaterialTheme.typography.bodySmall.copy(color = colors.accentCheck),
                            modifier = Modifier.testTag("lan_error_text")
                        )
                    }

                    if (linkMode == 0 && selectedTab == 0) {
                        HostTabContent(
                            lanState = lanState,
                            enabledSpecialCards = enabledSpecialCards,
                            onToggleSpecialCard = onToggleSpecialCard,
                            onSetAllSpecialCards = onSetAllSpecialCards,
                            onStartHosting = onStartHosting,
                            onStopHosting = onLeaveMatch
                        )
                    } else if (linkMode == 0) {
                        JoinTabContent(
                            lanState = lanState,
                            manualIp = manualIp,
                            manualPort = manualPort,
                            onManualIpChange = { manualIp = it },
                            onManualPortChange = { manualPort = it.filter { ch -> ch.isDigit() } },
                            onSelectHost = { host -> onConnect(host.hostAddress, host.port) },
                            onManualConnect = {
                                val parsedPort = manualPort.toIntOrNull() ?: 0
                                onConnect(manualIp, parsedPort)
                            }
                        )
                    } else if (selectedTab == 0) {
                        OnlineHostContent(
                            lanState = lanState,
                            displayName = onlineName,
                            onDisplayNameChange = { onlineName = it.take(24) },
                            enabledSpecialCards = enabledSpecialCards,
                            onToggleSpecialCard = onToggleSpecialCard,
                            onSetAllSpecialCards = onSetAllSpecialCards,
                            onCreateLobby = { onCreateOnlineLobby(onlineName) },
                            onCancel = onLeaveMatch
                        )
                    } else {
                        OnlineJoinContent(
                            lanState = lanState,
                            displayName = onlineName,
                            onDisplayNameChange = { onlineName = it.take(24) },
                            code = onlineCode,
                            onCodeChange = { onlineCode = it.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(8) },
                            onJoin = { onJoinOnlineLobby(onlineCode, onlineName) },
                            onJoinLobby = { lobby -> onJoinOnlineLobby(lobby.code, onlineName) }
                        )
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

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.surfaceSubtle)
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(colors.accentRevive)
            )
            Column {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HostTabContent(
    lanState: LanSessionState,
    enabledSpecialCards: Set<SpecialCardKey>,
    onToggleSpecialCard: (SpecialCardKey) -> Unit,
    onSetAllSpecialCards: (Boolean) -> Unit,
    onStartHosting: () -> Unit,
    onStopHosting: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    val isWaiting = lanState.role == NetworkRole.LAN_HOST &&
        lanState.status == LanConnectionStatus.HOSTING_WAITING
    val totalDeckSize = UnoDeck.deckSizeFor(enabledSpecialCards)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isWaiting) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceSubtle)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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

            OutlinedButton(
                onClick = onStopHosting,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("lan_stop_hosting_button")
            ) {
                Text(
                    text = stringResource(R.string.lan_stop_hosting),
                    color = colors.inkPrimary
                )
            }
        } else {
            Text(
                text = "Host a match as Player 1 on your Wi-Fi network. Customize which special cards are shuffled into this match's deck:",
                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
            )

            SpecialCardDeckPicker(
                enabledSpecialCards = enabledSpecialCards,
                onToggleSpecialCard = onToggleSpecialCard,
                onSetAllSpecialCards = onSetAllSpecialCards
            )

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
}

@Composable
private fun JoinTabContent(
    lanState: LanSessionState,
    manualIp: String,
    manualPort: String,
    onManualIpChange: (String) -> Unit,
    onManualPortChange: (String) -> Unit,
    onSelectHost: (DiscoveredHost) -> Unit,
    onManualConnect: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    val isConnecting = lanState.status == LanConnectionStatus.CONNECTING

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lanState.isDiscovering) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                lanState.discoveredHosts.forEach { host ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surfaceSubtle)
                            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                            .clickable(enabled = !isConnecting) { onSelectHost(host) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = host.serviceName,
                                style = MaterialTheme.typography.titleSmall.copy(color = colors.inkPrimary)
                            )
                            Text(
                                text = "${host.hostAddress}:${host.port}",
                                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
                            )
                        }
                        Text(
                            text = "Join",
                            style = MaterialTheme.typography.labelLarge.copy(color = colors.inkPrimary)
                        )
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
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

        Button(
            onClick = onManualConnect,
            enabled = !isConnecting && manualIp.isNotBlank() && manualPort.isNotBlank(),
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
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.surface
                )
                Spacer(Modifier.width(8.dp))
                Text("Connecting…")
            } else {
                Text(
                    text = stringResource(R.string.lan_connect),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
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
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        listOf(
            stringResource(R.string.link_wifi) to Icons.Rounded.Wifi,
            stringResource(R.string.link_online) to Icons.Rounded.Public
        ).forEachIndexed { index, (label, icon) ->
            val isSelected = selected == index
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isSelected) colors.surface else colors.surfaceSubtle)
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
                        color = if (isSelected) colors.inkPrimary else colors.inkSecondary
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
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceSubtle)
            .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
            .padding(10.dp)
            .testTag("host_special_cards_panel"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
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
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = { onSetAllSpecialCards(true) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("host_cards_all_button")
                ) {
                    Text("All", style = MaterialTheme.typography.labelSmall, color = colors.inkPrimary)
                }
                TextButton(
                    onClick = { onSetAllSpecialCards(false) },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(32.dp)
                        .testTag("host_cards_clear_button")
                ) {
                    Text("None", style = MaterialTheme.typography.labelSmall, color = colors.inkSecondary)
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
                        .heightIn(min = 34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) colors.inkPrimary else colors.surface)
                        .border(
                            width = 1.dp,
                            color = if (active) colors.inkPrimary else colors.hairline,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .testTag("host_card_toggle_${key.name.lowercase()}")
                        .clickable { onToggleSpecialCard(key) }
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (active) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = colors.surface,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    Text(
                        text = "${key.shortSymbol} ${key.displayName}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (active) colors.surface else colors.inkSecondary,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 11.sp
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
private fun OnlineHostContent(
    lanState: LanSessionState,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    enabledSpecialCards: Set<SpecialCardKey>,
    onToggleSpecialCard: (SpecialCardKey) -> Unit,
    onSetAllSpecialCards: (Boolean) -> Unit,
    onCreateLobby: () -> Unit,
    onCancel: () -> Unit
) {
    val colors = LocalUnoChessColors.current
    val isWaiting = lanState.role == NetworkRole.ONLINE_HOST &&
        lanState.status == LanConnectionStatus.HOSTING_WAITING
    val totalDeckSize = UnoDeck.deckSizeFor(enabledSpecialCards)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isWaiting) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceSubtle)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
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
                    text = lanState.roomCode,
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    ),
                    modifier = Modifier.testTag("online_room_code")
                )
                Text(
                    text = "Custom Deck: $totalDeckSize cards",
                    style = MaterialTheme.typography.labelMedium.copy(color = colors.inkPrimary)
                )
                Text(
                    text = stringResource(R.string.online_waiting_guest),
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
                )
            }
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("online_cancel_lobby")
            ) {
                Text(text = stringResource(R.string.lan_stop_hosting), color = colors.inkPrimary)
            }
        } else {
            Text(
                text = stringResource(R.string.online_host_blurb),
                style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
            )
            OutlinedTextField(
                value = displayName,
                onValueChange = onDisplayNameChange,
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
            Button(
                onClick = onCreateLobby,
                enabled = displayName.isNotBlank(),
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
                Text(
                    text = stringResource(R.string.online_create_lobby),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun OnlineJoinContent(
    lanState: LanSessionState,
    displayName: String,
    onDisplayNameChange: (String) -> Unit,
    code: String,
    onCodeChange: (String) -> Unit,
    onJoin: () -> Unit,
    onJoinLobby: (OnlineLobbySummary) -> Unit
) {
    val colors = LocalUnoChessColors.current
    val isConnecting = lanState.status == LanConnectionStatus.CONNECTING && lanState.role.isOnline
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = displayName,
            onValueChange = onDisplayNameChange,
            label = { Text(stringResource(R.string.online_name_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("online_join_name_input")
        )
        Text(
            text = stringResource(R.string.online_open_lobbies),
            style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
        )
        if (lanState.openLobbies.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceSubtle)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (lanState.isListingLobbies) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = colors.inkSecondary
                    )
                }
                Text(
                    text = stringResource(R.string.online_no_lobbies),
                    style = MaterialTheme.typography.bodySmall.copy(color = colors.inkMuted)
                )
            }
        } else {
            lanState.openLobbies.forEach { lobby ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceSubtle)
                        .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                        .clickable(enabled = !isConnecting) { onJoinLobby(lobby) }
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                        .testTag("online_lobby_${lobby.code}"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = lobby.hostName,
                            style = MaterialTheme.typography.titleSmall.copy(color = colors.inkPrimary)
                        )
                        Text(
                            text = lobby.code,
                            style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
                        )
                    }
                    Text(
                        text = "Join",
                        style = MaterialTheme.typography.labelLarge.copy(color = colors.inkPrimary)
                    )
                }
            }
        }
        HorizontalDivider(color = colors.hairline)
        OutlinedTextField(
            value = code,
            onValueChange = onCodeChange,
            label = { Text(stringResource(R.string.online_code_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("online_code_input")
        )
        Button(
            onClick = onJoin,
            enabled = !isConnecting && code.length >= 4 && displayName.isNotBlank(),
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
            if (isConnecting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.surface
                )
                Spacer(Modifier.width(8.dp))
                Text("Joining…")
            } else {
                Text(
                    text = stringResource(R.string.online_join_lobby),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
