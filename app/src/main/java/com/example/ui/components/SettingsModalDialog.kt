package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.R
import com.example.model.AiDifficulty
import com.example.model.OpponentMode
import com.example.model.UnoCardTheme
import com.example.ui.theme.LocalUnoChessColors

@Composable
fun SettingsModalDialog(
    opponentMode: OpponentMode,
    isLanConnected: Boolean,
    isOnlineMatch: Boolean = false,
    isDarkTheme: Boolean,
    soundEnabled: Boolean,
    cardTheme: UnoCardTheme,
    onSelectOpponentMode: (OpponentMode) -> Unit,
    onSelectDarkTheme: (Boolean) -> Unit,
    onSelectSoundEnabled: (Boolean) -> Unit,
    onSelectCardTheme: (UnoCardTheme) -> Unit,
    appVersionName: String = "",
    updateStatus: String? = null,
    checkingForUpdate: Boolean = false,
    onCheckForUpdate: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val colors = LocalUnoChessColors.current

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
                .testTag("settings_modal")
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = colors.inkPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.settings_modal_title),
                            style = MaterialTheme.typography.titleLarge.copy(color = colors.inkPrimary)
                        )
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("settings_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.action_close),
                            tint = colors.inkSecondary
                        )
                    }
                }

                HorizontalDivider(color = colors.hairline)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Opponent / AI Mode
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_opponent_section),
                            style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
                        )

                        val modeOptions = listOf(
                            "Pass & Play" to OpponentMode.PassAndPlay,
                            "AI Easy" to OpponentMode.VsAi(AiDifficulty.EASY),
                            "AI Medium" to OpponentMode.VsAi(AiDifficulty.MEDIUM),
                            "AI Hard" to OpponentMode.VsAi(AiDifficulty.HARD)
                        )

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            modeOptions.chunked(2).forEach { rowItems ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    rowItems.forEach { (label, mode) ->
                                        val isSelected = opponentMode == mode && !isLanConnected
                                        val tagKey = when (mode) {
                                            OpponentMode.PassAndPlay -> "pass_and_play"
                                            is OpponentMode.VsAi -> "ai_${mode.difficulty.name.lowercase()}"
                                        }
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) colors.inkPrimary else colors.surfaceSubtle
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) colors.inkPrimary else colors.hairline,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .testTag("mode_option_$tagKey")
                                                .clickable { onSelectOpponentMode(mode) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                style = MaterialTheme.typography.labelLarge.copy(
                                                    color = if (isSelected) colors.surface else colors.inkPrimary,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        val modeDescription = when {
                            isOnlineMatch -> "Currently in an online match. Picking a mode above starts a fresh local game."
                            isLanConnected -> "Currently in a Wi-Fi LAN match. Picking a mode above starts a fresh local game."
                            opponentMode is OpponentMode.PassAndPlay -> "2 players sharing this device. Switching starts a fresh match."
                            opponentMode is OpponentMode.VsAi -> "${opponentMode.difficulty.displayName}: ${opponentMode.difficulty.subtitle}. You play White."
                            else -> ""
                        }
                        Text(
                            text = modeDescription,
                            style = MaterialTheme.typography.bodySmall.copy(color = colors.inkMuted)
                        )
                    }

                    HorizontalDivider(color = colors.hairline)

                    // 2. Appearance (Light / Dark)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_appearance_section),
                            style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceSubtle)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppearanceToggleOption(
                                label = stringResource(R.string.theme_light),
                                icon = Icons.Rounded.LightMode,
                                selected = !isDarkTheme,
                                onClick = { onSelectDarkTheme(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("appearance_light_button")
                            )
                            AppearanceToggleOption(
                                label = stringResource(R.string.theme_dark),
                                icon = Icons.Rounded.DarkMode,
                                selected = isDarkTheme,
                                onClick = { onSelectDarkTheme(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("appearance_dark_button")
                            )
                        }
                    }

                    HorizontalDivider(color = colors.hairline)

                    // 3. Sound Effects (On / Muted)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.settings_sound_section),
                            style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surfaceSubtle)
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            AppearanceToggleOption(
                                label = stringResource(R.string.sound_on),
                                icon = Icons.AutoMirrored.Rounded.VolumeUp,
                                selected = soundEnabled,
                                onClick = { onSelectSoundEnabled(true) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sound_on_button")
                            )
                            AppearanceToggleOption(
                                label = stringResource(R.string.sound_off),
                                icon = Icons.AutoMirrored.Rounded.VolumeOff,
                                selected = !soundEnabled,
                                onClick = { onSelectSoundEnabled(false) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sound_off_button")
                            )
                        }
                    }

                    HorizontalDivider(color = colors.hairline)

                    // 4. UNO Card Visual Theme
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = stringResource(R.string.settings_card_theme_section),
                            style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
                        )

                        UnoCardTheme.entries.forEach { theme ->
                            val selected = cardTheme == theme
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (selected) colors.surfaceSubtle else colors.surface)
                                    .border(
                                        width = if (selected) 1.5.dp else 1.dp,
                                        color = if (selected) colors.inkPrimary else colors.hairline,
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .testTag("card_theme_${theme.name.lowercase()}")
                                    .clickable { onSelectCardTheme(theme) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                UnoCardThemePreviewPair(theme = theme)

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = theme.displayName,
                                        style = MaterialTheme.typography.titleSmall.copy(
                                            color = colors.inkPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                    )
                                    Text(
                                        text = theme.tagline,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = colors.inkSecondary
                                        )
                                    )
                                }

                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Rounded.Check,
                                        contentDescription = null,
                                        tint = colors.inkPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (appVersionName.isNotEmpty()) {
                        HorizontalDivider(color = colors.hairline)

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = stringResource(R.string.settings_updates_section),
                                style = MaterialTheme.typography.labelMedium.copy(color = colors.inkSecondary)
                            )
                            Text(
                                text = stringResource(R.string.settings_version, appVersionName),
                                style = MaterialTheme.typography.bodyMedium.copy(color = colors.inkPrimary)
                            )
                            if (!updateStatus.isNullOrBlank()) {
                                Text(
                                    text = updateStatus,
                                    style = MaterialTheme.typography.bodySmall.copy(color = colors.inkMuted)
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceSubtle)
                                    .border(1.dp, colors.hairline, RoundedCornerShape(10.dp))
                                    .testTag("check_updates_button")
                                    .clickable(enabled = !checkingForUpdate, onClick = onCheckForUpdate),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.action_check_updates),
                                    style = MaterialTheme.typography.labelLarge.copy(color = colors.inkPrimary)
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.hairline)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                ) {
                    Button(
                        onClick = onDismiss,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.inkPrimary,
                            contentColor = colors.surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("settings_done_button")
                    ) {
                        Text(
                            text = stringResource(R.string.action_done),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceToggleOption(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) colors.surface else colors.surfaceSubtle)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) colors.inkPrimary else colors.inkSecondary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge.copy(
                color = if (selected) colors.inkPrimary else colors.inkSecondary
            )
        )
    }
}
