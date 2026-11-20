package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.R
import com.example.ui.theme.LocalUnoChessColors

@Composable
fun AppUpdateDialog(
    versionName: String,
    notes: String,
    downloading: Boolean,
    progress: Float,
    readyToInstall: Boolean,
    statusMessage: String?,
    onInstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalUnoChessColors.current
    Dialog(onDismissRequest = { if (!downloading) onDismiss() }) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = colors.surface,
            border = BorderStroke(1.dp, colors.hairline),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.update_available_title),
                    style = MaterialTheme.typography.titleLarge.copy(
                        color = colors.inkPrimary,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = stringResource(R.string.update_available_message, versionName),
                    style = MaterialTheme.typography.bodyMedium.copy(color = colors.inkSecondary)
                )
                if (notes.isNotBlank()) {
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.inkMuted),
                        modifier = Modifier
                            .heightIn(max = 160.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
                if (downloading) {
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = colors.inkPrimary,
                        trackColor = colors.surfaceSubtle
                    )
                    Text(
                        text = stringResource(R.string.update_downloading, (progress * 100).toInt()),
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary)
                    )
                }
                if (!statusMessage.isNullOrBlank()) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall.copy(color = colors.inkPrimary)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(
                        onClick = onDismiss,
                        enabled = !downloading,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = stringResource(R.string.action_later),
                            color = colors.inkSecondary
                        )
                    }
                    Button(
                        onClick = onInstall,
                        enabled = !downloading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = colors.inkPrimary,
                            contentColor = colors.surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1.4f)
                            .height(44.dp)
                    ) {
                        Text(
                            text = stringResource(
                                if (readyToInstall) R.string.action_install_update
                                else R.string.action_download_update
                            ),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }
        }
    }
}
