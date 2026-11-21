package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.GameOutcome
import com.example.model.OpponentMode
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.ui.theme.LocalUnoChessColors
import com.example.ui.theme.UnoBlue
import com.example.ui.theme.UnoGreen
import com.example.ui.theme.UnoRed
import com.example.ui.theme.UnoYellow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class ConfettiShape { RIBBON, DIAMOND, STAR }

private data class ConfettiParticle(
    val originXFrac: Float,
    val originYFrac: Float,
    val velocityX: Float,
    val velocityY: Float,
    val gravity: Float,
    val sizeDp: Float,
    val color: Color,
    val initialAngle: Float,
    val spinSpeed: Float,
    val tumbleSpeed: Float,
    val shape: ConfettiShape,
    val delaySec: Float
)

@Composable
fun VictoryCelebrationOverlay(
    outcome: GameOutcome?,
    opponentMode: OpponentMode,
    isLanConnected: Boolean,
    visible: Boolean,
    onDismiss: () -> Unit,
    onNewGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCelebratedOutcome = outcome is GameOutcome.Checkmate || outcome is GameOutcome.Forfeit
    if (!isCelebratedOutcome || outcome == null) return

    val colors = LocalUnoChessColors.current
    val isCheckmate = outcome is GameOutcome.Checkmate
    val winnerPlayer = when (outcome) {
        is GameOutcome.Checkmate -> outcome.winnerPlayer
        is GameOutcome.Forfeit -> outcome.winnerPlayer
        else -> return
    }
    val winnerColor = when (outcome) {
        is GameOutcome.Checkmate -> outcome.winnerColor
        is GameOutcome.Forfeit -> outcome.winnerColor
        else -> return
    }
    val winnerLabel = resolvePlayerLabel(winnerPlayer, opponentMode, isLanConnected)

    // Physics-driven confetti clock (runs smoothly for 3.6 seconds per victory)
    var elapsedSec by remember(outcome) { mutableFloatStateOf(0f) }
    val shockwaveProgress = remember(outcome) { Animatable(0f) }
    val crestScale = remember(outcome) { Animatable(0.45f) }
    val crestAlpha = remember(outcome) { Animatable(0f) }
    val badgeBounce = remember(outcome) { Animatable(0.2f) }

    val particles = remember(outcome) {
        generateVictoryParticles(isCheckmate = isCheckmate)
    }

    LaunchedEffect(outcome) {
        elapsedSec = 0f
        shockwaveProgress.snapTo(0f)
        crestScale.snapTo(0.45f)
        crestAlpha.snapTo(0f)
        badgeBounce.snapTo(0.2f)

        // Shockwave rings
        shockwaveProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 950, easing = FastOutSlowInEasing)
        )
    }

    LaunchedEffect(outcome) {
        crestAlpha.animateTo(1f, tween(durationMillis = 220))
    }

    LaunchedEffect(outcome) {
        crestScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        )
    }

    LaunchedEffect(outcome) {
        badgeBounce.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = 0.45f,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    LaunchedEffect(outcome) {
        var startNanos = 0L
        while (elapsedSec < 3.8f) {
            withFrameNanos { frameNanos ->
                if (startNanos == 0L) startNanos = frameNanos
                elapsedSec = (frameNanos - startNanos) / 1_000_000_000f
            }
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "victory_shimmer")
    val haloPulse by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "halo_pulse"
    )
    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = -0.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_sweep"
    )
    val raysRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rays_rotation"
    )

    val goldAccent = Color(0xFFDFA837)
    val primaryAccent = if (isCheckmate) goldAccent else colors.accentTrade

    Box(
        modifier = modifier
            .fillMaxSize()
            .testTag("victory_celebration_overlay")
    ) {
        // Backdrop scrim (only while centered crest modal is visible; tapping dismisses to view board)
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(240)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (colors.isDark) 0.56f else 0.36f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss
                    )
            )
        }

        // Full-screen physics confetti + shockwave rings (non-blocking visual layer)
        if (elapsedSec < 3.8f) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("victory_confetti_canvas")
            ) {
                val sw = shockwaveProgress.value
                if (sw in 0.001f..0.999f) {
                    val maxRadius = size.maxDimension * 0.62f
                    val alpha1 = (1f - sw) * 0.55f
                    drawCircle(
                        color = primaryAccent.copy(alpha = alpha1),
                        radius = maxRadius * sw,
                        center = Offset(size.width * 0.5f, size.height * 0.44f),
                        style = Stroke(width = (1f - sw) * 14f + 2f)
                    )
                    val sw2 = ((sw - 0.14f) / 0.86f).coerceIn(0f, 1f)
                    if (sw2 > 0f) {
                        val alpha2 = (1f - sw2) * 0.40f
                        drawCircle(
                            color = (if (isCheckmate) UnoGreen else UnoYellow).copy(alpha = alpha2),
                            radius = maxRadius * 0.85f * sw2,
                            center = Offset(size.width * 0.5f, size.height * 0.44f),
                            style = Stroke(width = (1f - sw2) * 10f + 1.5f)
                        )
                    }
                }

                // Confetti particles
                val densityPx = density
                particles.forEach { p ->
                    val t = elapsedSec - p.delaySec
                    if (t > 0f) {
                        val drag = 0.88f
                        val x = size.width * p.originXFrac + p.velocityX * densityPx * t * (1f - (1f - drag) * (t / 4f))
                        val y = size.height * p.originYFrac +
                            p.velocityY * densityPx * t +
                            0.5f * p.gravity * densityPx * t * t

                        if (y < size.height + 60f) {
                            val fadeOutAlpha = (1f - ((t - 2.35f) / 1.15f).coerceIn(0f, 1f))
                            val fadeInAlpha = (t / 0.12f).coerceIn(0f, 1f)
                            val alpha = (fadeInAlpha * fadeOutAlpha).coerceIn(0f, 1f)

                            if (alpha > 0.01f) {
                                val angleDeg = p.initialAngle + p.spinSpeed * t
                                val tumbleScale = cos(t * p.tumbleSpeed).let { c ->
                                    if (c >= 0f) c.coerceAtLeast(0.22f) else c.coerceAtMost(-0.22f)
                                }
                                val sz = p.sizeDp * densityPx

                                rotate(degrees = angleDeg, pivot = Offset(x, y)) {
                                    when (p.shape) {
                                        ConfettiShape.RIBBON -> {
                                            val w = sz * kotlin.math.abs(tumbleScale)
                                            val h = sz * 0.56f
                                            drawRoundRect(
                                                color = p.color.copy(alpha = alpha),
                                                topLeft = Offset(x - w / 2f, y - h / 2f),
                                                size = Size(w, h),
                                                cornerRadius = CornerRadius(2.5f, 2.5f)
                                            )
                                        }

                                        ConfettiShape.DIAMOND -> {
                                            val r = sz * 0.48f
                                            val diamond = Path().apply {
                                                moveTo(x, y - r)
                                                lineTo(x + r * kotlin.math.abs(tumbleScale), y)
                                                lineTo(x, y + r)
                                                lineTo(x - r * kotlin.math.abs(tumbleScale), y)
                                                close()
                                            }
                                            drawPath(path = diamond, color = p.color.copy(alpha = alpha))
                                        }

                                        ConfettiShape.STAR -> {
                                            val outer = sz * 0.55f
                                            val inner = outer * 0.36f
                                            val star = Path().apply {
                                                for (i in 0 until 8) {
                                                    val rad = (i * PI / 4.0).toFloat()
                                                    val radLen = if (i % 2 == 0) outer else inner
                                                    val px = x + cos(rad) * radLen
                                                    val py = y + sin(rad) * radLen
                                                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                                                }
                                                close()
                                            }
                                            drawPath(path = star, color = p.color.copy(alpha = alpha))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Centered Celebratory Victory Crest Card
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(180)) + scaleIn(
                initialScale = 0.78f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ),
            exit = fadeOut(tween(180)) + scaleOut(targetScale = 0.88f),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 24.dp)
        ) {
            val crestDesc = if (isCheckmate) {
                "Checkmate! $winnerLabel wins"
            } else {
                "Victory by Forfeit! $winnerLabel wins"
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = colors.surface,
                border = BorderStroke(1.5.dp, primaryAccent.copy(alpha = 0.75f)),
                shadowElevation = 12.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp)
                    .graphicsLayer {
                        scaleX = crestScale.value
                        scaleY = crestScale.value
                        alpha = crestAlpha.value
                    }
                    .testTag("victory_crest_card")
                    .semantics { contentDescription = crestDesc }
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    // Subtle diagonal gold shimmer sheen sweeping across the crest card
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val sweepX = size.width * shimmerOffset
                        val bandWidth = size.width * 0.42f
                        drawRect(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primaryAccent.copy(alpha = if (colors.isDark) 0.13f else 0.10f),
                                    Color.Transparent
                                ),
                                start = Offset(sweepX - bandWidth, 0f),
                                end = Offset(sweepX + bandWidth, size.height)
                            )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Animated Emblem Medallion (Pulsing King Crown for Checkmate / Trophy & Flag for Forfeit)
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .graphicsLayer {
                                    scaleX = badgeBounce.value
                                    scaleY = badgeBounce.value
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            // Slowly rotating sunburst rays behind emblem
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                rotate(degrees = raysRotation) {
                                    val rayCount = 12
                                    val innerR = size.minDimension * 0.34f
                                    val outerR = size.minDimension * 0.49f * haloPulse
                                    for (i in 0 until rayCount) {
                                        val angle = (i * 2.0 * PI / rayCount).toFloat()
                                        drawLine(
                                            color = primaryAccent.copy(alpha = 0.32f),
                                            start = Offset(
                                                center.x + cos(angle) * innerR,
                                                center.y + sin(angle) * innerR
                                            ),
                                            end = Offset(
                                                center.x + cos(angle) * outerR,
                                                center.y + sin(angle) * outerR
                                            ),
                                            strokeWidth = 3f
                                        )
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .graphicsLayer {
                                        scaleX = haloPulse
                                        scaleY = haloPulse
                                    }
                                    .clip(CircleShape)
                                    .background(primaryAccent.copy(alpha = 0.18f))
                            )

                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.radialGradient(
                                            colors = listOf(
                                                primaryAccent.copy(alpha = 0.34f),
                                                colors.surfaceSubtle
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isCheckmate) {
                                    Box(modifier = Modifier.size(38.dp)) {
                                        ChessPieceGraphic(type = PieceType.KING, color = winnerColor)
                                    }
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.EmojiEvents,
                                        contentDescription = null,
                                        tint = primaryAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            // Tiny corner accent pill
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(primaryAccent),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCheckmate) Icons.Rounded.EmojiEvents else Icons.Rounded.Flag,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }

                        // Headline
                        Text(
                            text = if (isCheckmate) {
                                stringResource(R.string.celebration_checkmate_title)
                            } else {
                                stringResource(R.string.celebration_forfeit_title)
                            },
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = colors.inkPrimary,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.6.sp
                            ),
                            textAlign = TextAlign.Center
                        )

                        // Winner badge + detail subtitle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.surfaceSubtle)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            ColorControlBadge(color = winnerColor)
                            Text(
                                text = "$winnerLabel (${winnerColor.displayName}) Wins!",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    color = colors.inkPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            )
                        }

                        val detailText = when (outcome) {
                            is GameOutcome.Checkmate -> stringResource(R.string.celebration_checkmate_subtitle)
                            is GameOutcome.Forfeit -> {
                                val forfeiter = resolvePlayerLabel(
                                    outcome.forfeitingPlayer,
                                    opponentMode,
                                    isLanConnected
                                )
                                "$forfeiter conceded the match"
                            }
                            else -> ""
                        }
                        Text(
                            text = detailText,
                            style = MaterialTheme.typography.bodySmall.copy(color = colors.inkSecondary),
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onDismiss,
                                border = BorderStroke(1.dp, colors.hairline),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("victory_view_board_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.action_view_board),
                                    color = colors.inkPrimary,
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }

                            Button(
                                onClick = onNewGame,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = colors.inkPrimary,
                                    contentColor = colors.surface
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("victory_new_game_button")
                            ) {
                                Text(
                                    text = stringResource(R.string.action_new_game),
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun generateVictoryParticles(isCheckmate: Boolean): List<ConfettiParticle> {
    val rng = Random(if (isCheckmate) 202601 else 202602)
    val palette = if (isCheckmate) {
        listOf(
            Color(0xFFE8B949), // Champagne Gold
            Color(0xFFF6D97A), // Bright Gold
            UnoRed,
            UnoBlue,
            UnoGreen,
            UnoYellow,
            Color(0xFFFFFFFF)
        )
    } else {
        listOf(
            Color(0xFFE8B949), // Champagne Gold
            Color(0xFFF59E0B), // Amber
            Color(0xFFFDF3D7), // Warm Ivory
            UnoGreen,
            UnoYellow,
            Color(0xFF60A5FA)
        )
    }

    return List(68) { index ->
        // Three launch fountains: center burst + left & right cannons
        val burstGroup = index % 3
        val originX = when (burstGroup) {
            0 -> 0.50f + (rng.nextFloat() - 0.5f) * 0.14f
            1 -> 0.12f + rng.nextFloat() * 0.12f
            else -> 0.76f + rng.nextFloat() * 0.12f
        }
        val originY = when (burstGroup) {
            0 -> 0.36f + (rng.nextFloat() - 0.5f) * 0.08f
            else -> 0.48f + (rng.nextFloat() - 0.5f) * 0.10f
        }

        val vx = when (burstGroup) {
            0 -> (rng.nextFloat() - 0.5f) * 340f
            1 -> 75f + rng.nextFloat() * 210f
            else -> -(75f + rng.nextFloat() * 210f)
        }
        val vy = -(220f + rng.nextFloat() * 340f)
        val gravity = 460f + rng.nextFloat() * 210f

        val shape = when (index % 5) {
            0 -> ConfettiShape.STAR
            1 -> ConfettiShape.DIAMOND
            else -> ConfettiShape.RIBBON
        }

        ConfettiParticle(
            originXFrac = originX,
            originYFrac = originY,
            velocityX = vx,
            velocityY = vy,
            gravity = gravity,
            sizeDp = 7.5f + rng.nextFloat() * 6.5f,
            color = palette[index % palette.size],
            initialAngle = rng.nextFloat() * 360f,
            spinSpeed = (rng.nextFloat() - 0.5f) * 420f,
            tumbleSpeed = 4.5f + rng.nextFloat() * 6.5f,
            shape = shape,
            delaySec = (index / 24) * 0.09f + rng.nextFloat() * 0.06f
        )
    }
}
