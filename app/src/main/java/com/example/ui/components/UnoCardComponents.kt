package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.model.UnoCard
import com.example.model.UnoCardTheme
import com.example.model.UnoCardType
import com.example.model.UnoColor
import com.example.ui.theme.LocalUnoChessColors
import com.example.ui.theme.UnoBlue
import com.example.ui.theme.UnoCardInkDark
import com.example.ui.theme.UnoCardInkLight
import com.example.ui.theme.UnoDeckCharcoal
import com.example.ui.theme.UnoGreen
import com.example.ui.theme.UnoRed
import com.example.ui.theme.UnoYellow
import kotlinx.coroutines.launch

@Composable
fun UnoDeckAndActiveCardRow(
    drawPileCount: Int,
    canDraw: Boolean,
    activeCard: UnoCard?,
    drawRevision: Int,
    cardTheme: UnoCardTheme,
    onDrawClick: () -> Unit,
    pityCount: Int = 0,
    cardWidth: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UnoDrawPileCard(
            cardsLeft = drawPileCount,
            canDraw = canDraw,
            cardTheme = cardTheme,
            onDrawClick = onDrawClick,
            pityCount = pityCount,
            cardWidth = cardWidth
        )
        ActiveUnoCardView(
            card = activeCard,
            drawRevision = drawRevision,
            cardTheme = cardTheme,
            cardWidth = cardWidth
        )
    }
}

@Composable
fun UnoDrawPileCard(
    cardsLeft: Int,
    canDraw: Boolean,
    cardTheme: UnoCardTheme,
    onDrawClick: () -> Unit,
    pityCount: Int = 0,
    cardWidth: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val cardHeight = cardWidth * 1.38f
    val cdText = stringResource(R.string.cd_uno_deck)
    val frameBg = cardOuterFrameColor(cardTheme)
    val stackEdgeColor = when (cardTheme) {
        UnoCardTheme.CLASSIC -> Color(0xFF3A3B40)
        UnoCardTheme.SPACE -> Color(0xFF28345C)
        UnoCardTheme.FANTASY -> Color(0xFF5A3D2E)
        UnoCardTheme.SPIRITS -> Color(0xFF3D2E1E)
    }

    Box(
        modifier = modifier
            .width(cardWidth + 4.dp)
            .height(cardHeight + 4.dp),
        contentAlignment = Alignment.Center
    ) {
        if (cardsLeft > 1) {
            Box(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .offset(x = 2.dp, y = 2.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(stackEdgeColor)
            )
        }

        Box(
            modifier = Modifier
                .width(cardWidth)
                .height(cardHeight)
                .alpha(if (canDraw) 1f else 0.48f)
                .clip(RoundedCornerShape(8.dp))
                .background(frameBg)
                .border(
                    width = if (canDraw) (if (pityCount > 0) 2.dp else 1.5.dp) else 1.dp,
                    color = if (canDraw) (if (pityCount > 0) colors.accentShield else colors.inkPrimary) else colors.hairline,
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(2.5.dp)
                .clip(RoundedCornerShape(6.dp))
                .testTag("uno_draw_deck")
                .semantics { contentDescription = cdText }
                .clickable(enabled = canDraw, onClick = onDrawClick),
            contentAlignment = Alignment.Center
        ) {
            ThemedDeckBackArtwork(theme = cardTheme, modifier = Modifier.fillMaxSize())

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = if (canDraw) "DRAW" else "DECK",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = when (cardTheme) {
                            UnoCardTheme.FANTASY -> Color(0xFFFBF3DF)
                            UnoCardTheme.SPIRITS -> Color(0xFFF7EFE0)
                            else -> Color.White
                        },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 9.sp,
                        letterSpacing = 0.8.sp
                    )
                )
                Text(
                    text = "$cardsLeft",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = when (cardTheme) {
                            UnoCardTheme.FANTASY -> Color(0xFFE6CFA0)
                            UnoCardTheme.SPIRITS -> Color(0xFFD9B26B)
                            else -> Color.White.copy(alpha = 0.84f)
                        },
                        fontSize = 8.sp
                    )
                )
            }

            if (canDraw && pityCount > 0) {
                val pityLabel = when {
                    pityCount == 1 -> "+Move"
                    pityCount == 2 -> "40%"
                    else -> "3-4★"
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(2.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.accentShield)
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = pityLabel,
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = Color.Black,
                            fontWeight = FontWeight.Black,
                            fontSize = 7.sp,
                            lineHeight = 8.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun ActiveUnoCardView(
    card: UnoCard?,
    drawRevision: Int,
    cardTheme: UnoCardTheme,
    cardWidth: Dp = 64.dp,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val cardHeight = cardWidth * 1.38f
    val density = LocalDensity.current.density

    val flipAnim = remember { Animatable(0f) }
    val slideXAnim = remember { Animatable(0f) }

    LaunchedEffect(drawRevision) {
        if (drawRevision > 0 && card != null) {
            flipAnim.snapTo(180f)
            slideXAnim.snapTo(-cardWidth.value * density * 0.7f)

            launch {
                slideXAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f)
                )
            }
            launch {
                flipAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                )
            }
        }
    }

    if (card == null) {
        Box(
            modifier = modifier
                .width(cardWidth)
                .height(cardHeight)
                .clip(RoundedCornerShape(8.dp))
                .background(colors.surfaceSubtle)
                .border(1.dp, colors.hairline, RoundedCornerShape(8.dp))
                .testTag("active_uno_card_empty"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "—",
                style = MaterialTheme.typography.titleMedium.copy(color = colors.inkMuted)
            )
        }
        return
    }

    val showBack = flipAnim.value > 90f

    ThemedUnoCardFace(
        card = card,
        cardTheme = cardTheme,
        showBack = showBack,
        cardWidth = cardWidth,
        modifier = modifier
            .graphicsLayer {
                translationX = slideXAnim.value
                rotationY = flipAnim.value
                cameraDistance = 12f * density
            }
            .testTag("active_uno_card")
            .semantics {
                contentDescription = "${card.color.displayName} ${card.type.title()}"
            }
    )
}

@Composable
fun UnoCardThemePreviewPair(
    theme: UnoCardTheme,
    modifier: Modifier = Modifier
) {
    val previewCard = remember(theme) {
        when (theme) {
            UnoCardTheme.CLASSIC -> UnoCard(id = -1, color = UnoColor.RED, type = UnoCardType.NumberCard(3))
            UnoCardTheme.SPACE -> UnoCard(id = -2, color = UnoColor.BLUE, type = UnoCardType.Freeze)
            UnoCardTheme.FANTASY -> UnoCard(id = -3, color = UnoColor.GREEN, type = UnoCardType.RevivePlusTwo)
            UnoCardTheme.SPIRITS -> UnoCard(id = -4, color = UnoColor.YELLOW, type = UnoCardType.NumberCard(4))
        }
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ThemedUnoCardFace(
            card = previewCard,
            cardTheme = theme,
            showBack = true,
            cardWidth = 34.dp
        )
        ThemedUnoCardFace(
            card = previewCard,
            cardTheme = theme,
            showBack = false,
            cardWidth = 34.dp
        )
    }
}

@Composable
private fun ThemedUnoCardFace(
    card: UnoCard,
    cardTheme: UnoCardTheme,
    showBack: Boolean,
    cardWidth: Dp,
    modifier: Modifier = Modifier
) {
    val colors = LocalUnoChessColors.current
    val cardHeight = cardWidth * 1.38f
    val compact = cardWidth < 48.dp
    val frameBg = cardOuterFrameColor(cardTheme)
    val palette = themedFacePalette(card.color, cardTheme)

    Box(
        modifier = modifier
            .width(cardWidth)
            .height(cardHeight)
            .clip(RoundedCornerShape(if (compact) 6.dp else 8.dp))
            .background(frameBg)
            .border(1.dp, colors.hairline, RoundedCornerShape(if (compact) 6.dp else 8.dp))
            .padding(if (compact) 1.5.dp else 2.5.dp)
            .clip(RoundedCornerShape(if (compact) 4.dp else 6.dp))
    ) {
        if (showBack) {
            ThemedDeckBackArtwork(theme = cardTheme, modifier = Modifier.fillMaxSize())
        } else {
            // Theme-specific card face canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                when (cardTheme) {
                    UnoCardTheme.CLASSIC -> drawClassicCardFace(palette)
                    UnoCardTheme.SPACE -> drawSpaceCardFace(palette)
                    UnoCardTheme.FANTASY -> drawFantasyCardFace(palette)
                    UnoCardTheme.SPIRITS -> drawSpiritsCardFace(palette)
                }
            }

            // Top-left corner pip
            Text(
                text = card.type.shortSymbol(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.cornerInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 7.sp else 10.sp
                ),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        start = if (compact) 3.dp else 5.dp,
                        top = if (compact) 1.5.dp else 3.dp
                    )
            )

            // Center numeral / icon
            Box(modifier = Modifier.align(Alignment.Center)) {
                when (val type = card.type) {
                    is UnoCardType.NumberCard -> {
                        Text(
                            text = type.moves.toString(),
                            style = MaterialTheme.typography.headlineSmall.copy(
                                color = palette.centerInk,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 15.sp else 26.sp
                            )
                        )
                    }

                    UnoCardType.RevivePlusTwo -> {
                        Text(
                            text = "+1",
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = palette.centerInk,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = if (compact) 12.sp else 19.sp
                            )
                        )
                    }

                    UnoCardType.Freeze -> {
                        Icon(
                            imageVector = Icons.Rounded.AcUnit,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 13.dp else 20.dp)
                        )
                    }

                    UnoCardType.Merge -> {
                        Icon(
                            imageVector = Icons.Rounded.CallMerge,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 14.dp else 22.dp)
                        )
                    }

                    UnoCardType.Sacrifice -> {
                        Icon(
                            imageVector = Icons.Rounded.CallSplit,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 14.dp else 22.dp)
                        )
                    }

                    UnoCardType.Hijack -> {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 14.dp else 22.dp)
                        )
                    }

                    UnoCardType.PawnPromote -> {
                        Icon(
                            imageVector = Icons.Rounded.ArrowUpward,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 14.dp else 22.dp)
                        )
                    }

                    UnoCardType.Suicide -> {
                        Icon(
                            imageVector = Icons.Rounded.Dangerous,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 14.dp else 22.dp)
                        )
                    }

                    UnoCardType.Shield -> {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 14.dp else 21.dp)
                        )
                    }

                    UnoCardType.SwapPositions -> {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 14.dp else 22.dp)
                        )
                    }

                    UnoCardType.Clone -> {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 13.dp else 20.dp)
                        )
                    }

                    UnoCardType.Wild -> {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            tint = palette.centerInk,
                            modifier = Modifier.size(if (compact) 14.dp else 22.dp)
                        )
                    }
                }
            }

            // Bottom-right corner pip
            Text(
                text = card.type.shortSymbol(),
                style = MaterialTheme.typography.labelSmall.copy(
                    color = palette.cornerInk,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (compact) 7.sp else 10.sp
                ),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(
                        end = if (compact) 3.dp else 5.dp,
                        bottom = if (compact) 1.5.dp else 3.dp
                    )
            )
        }
    }
}

@Composable
private fun ThemedDeckBackArtwork(
    theme: UnoCardTheme,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        when (theme) {
            UnoCardTheme.CLASSIC -> {
                drawRect(color = UnoDeckCharcoal)
                rotate(degrees = -26f) {
                    drawOval(
                        color = UnoRed,
                        topLeft = Offset(size.width * 0.14f, size.height * 0.24f),
                        size = Size(size.width * 0.72f, size.height * 0.52f)
                    )
                }
            }

            UnoCardTheme.SPACE -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A1026), Color(0xFF162044), Color(0xFF0B1126))
                    )
                )
                // Distant stars
                drawStarDots(Color(0xFFDCE7FF))
                // Deep cosmic planet + orbital ring
                drawCircle(
                    color = Color(0xFF233569),
                    radius = size.minDimension * 0.31f,
                    center = center
                )
                rotate(degrees = -24f) {
                    drawOval(
                        color = Color(0xFF93C5FD),
                        topLeft = Offset(size.width * 0.08f, size.height * 0.34f),
                        size = Size(size.width * 0.84f, size.height * 0.32f),
                        style = Stroke(width = size.minDimension * 0.036f)
                    )
                }
            }

            UnoCardTheme.FANTASY -> {
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF2D1724), Color(0xFF1F111A))
                    )
                )
                val gold = Color(0xFFD6B25E)
                val inset = size.minDimension * 0.09f
                drawRoundRect(
                    color = gold.copy(alpha = 0.78f),
                    topLeft = Offset(inset, inset),
                    size = Size(size.width - inset * 2f, size.height - inset * 2f),
                    cornerRadius = CornerRadius(6f, 6f),
                    style = Stroke(width = size.minDimension * 0.025f)
                )
                // Central heraldic diamond sigil
                val cx = size.width / 2f
                val cy = size.height / 2f
                val rx = size.width * 0.30f
                val ry = size.height * 0.28f
                val diamond = Path().apply {
                    moveTo(cx, cy - ry)
                    lineTo(cx + rx, cy)
                    lineTo(cx, cy + ry)
                    lineTo(cx - rx, cy)
                    close()
                }
                drawPath(path = diamond, color = Color(0xFF472133))
                drawPath(
                    path = diamond,
                    color = gold,
                    style = Stroke(width = size.minDimension * 0.03f)
                )
            }

            UnoCardTheme.SPIRITS -> {
                // Jack Daniel's-inspired charred charcoal bottle label back
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF151311), Color(0xFF0D0B0A), Color(0xFF1B1713))
                    )
                )
                val ivory = Color(0xFFEFE3CA)
                val amber = Color(0xFFD4A359)
                val outerInset = size.minDimension * 0.08f
                val innerInset = size.minDimension * 0.14f
                // Double vintage label frame
                drawRoundRect(
                    color = ivory.copy(alpha = 0.86f),
                    topLeft = Offset(outerInset, outerInset),
                    size = Size(size.width - outerInset * 2f, size.height - outerInset * 2f),
                    cornerRadius = CornerRadius(4f, 4f),
                    style = Stroke(width = size.minDimension * 0.028f)
                )
                drawRoundRect(
                    color = amber.copy(alpha = 0.72f),
                    topLeft = Offset(innerInset, innerInset),
                    size = Size(size.width - innerInset * 2f, size.height - innerInset * 2f),
                    cornerRadius = CornerRadius(3f, 3f),
                    style = Stroke(width = size.minDimension * 0.016f)
                )
                // Horizontal filigree divider lines
                val lineInset = size.width * 0.22f
                drawLine(
                    color = amber.copy(alpha = 0.75f),
                    start = Offset(lineInset, size.height * 0.26f),
                    end = Offset(size.width - lineInset, size.height * 0.26f),
                    strokeWidth = size.minDimension * 0.018f
                )
                drawLine(
                    color = amber.copy(alpha = 0.75f),
                    start = Offset(lineInset, size.height * 0.74f),
                    end = Offset(size.width - lineInset, size.height * 0.74f),
                    strokeWidth = size.minDimension * 0.018f
                )
                // Central amber distillery cartouche
                drawOval(
                    color = Color(0xFF261E16),
                    topLeft = Offset(size.width * 0.17f, size.height * 0.31f),
                    size = Size(size.width * 0.66f, size.height * 0.38f)
                )
                drawOval(
                    color = amber,
                    topLeft = Offset(size.width * 0.17f, size.height * 0.31f),
                    size = Size(size.width * 0.66f, size.height * 0.38f),
                    style = Stroke(width = size.minDimension * 0.026f)
                )
            }
        }
    }
}

private fun DrawScope.drawClassicCardFace(palette: CardFacePalette) {
    drawRect(color = palette.primaryFill)
    rotate(degrees = -26f) {
        drawOval(
            color = palette.medallionFill,
            topLeft = Offset(size.width * 0.12f, size.height * 0.22f),
            size = Size(size.width * 0.76f, size.height * 0.56f)
        )
    }
}

private fun DrawScope.drawSpaceCardFace(palette: CardFacePalette) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.primaryFill, palette.secondaryFill)
        )
    )
    // Subtle corner starlight
    drawStarDots(Color.White.copy(alpha = 0.72f))
    // Dark nebula center disk
    drawCircle(
        color = palette.medallionFill,
        radius = size.minDimension * 0.34f,
        center = center
    )
    // Tilted silver-tinted orbital ring
    rotate(degrees = -25f) {
        drawOval(
            color = palette.accentLine,
            topLeft = Offset(size.width * 0.08f, size.height * 0.33f),
            size = Size(size.width * 0.84f, size.height * 0.34f),
            style = Stroke(width = size.minDimension * 0.036f)
        )
    }
}

private fun DrawScope.drawFantasyCardFace(palette: CardFacePalette) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.primaryFill, palette.secondaryFill)
        )
    )
    val inset = size.minDimension * 0.08f
    // Gilded heraldic border frame
    drawRoundRect(
        color = palette.accentLine,
        topLeft = Offset(inset, inset),
        size = Size(size.width - inset * 2f, size.height - inset * 2f),
        cornerRadius = CornerRadius(6f, 6f),
        style = Stroke(width = size.minDimension * 0.028f)
    )
    // Warm parchment shield/medallion
    val cx = size.width / 2f
    val cy = size.height / 2f
    val rx = size.width * 0.34f
    val ry = size.height * 0.27f
    val crest = Path().apply {
        moveTo(cx, cy - ry)
        lineTo(cx + rx, cy - ry * 0.42f)
        lineTo(cx + rx * 0.84f, cy + ry * 0.52f)
        lineTo(cx, cy + ry)
        lineTo(cx - rx * 0.84f, cy + ry * 0.52f)
        lineTo(cx - rx, cy - ry * 0.42f)
        close()
    }
    drawPath(path = crest, color = palette.medallionFill)
    drawPath(
        path = crest,
        color = palette.accentLine,
        style = Stroke(width = size.minDimension * 0.03f)
    )
}

private fun DrawScope.drawStarDots(color: Color) {
    val stars = listOf(
        Offset(size.width * 0.22f, size.height * 0.20f) to 0.022f,
        Offset(size.width * 0.78f, size.height * 0.17f) to 0.028f,
        Offset(size.width * 0.18f, size.height * 0.78f) to 0.024f,
        Offset(size.width * 0.82f, size.height * 0.72f) to 0.018f,
        Offset(size.width * 0.50f, size.height * 0.12f) to 0.018f
    )
    stars.forEach { (pt, rFrac) ->
        drawCircle(color = color, radius = size.minDimension * rFrac, center = pt)
    }
}

private fun DrawScope.drawSpiritsCardFace(palette: CardFacePalette) {
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(palette.primaryFill, palette.secondaryFill)
        )
    )
    val outerInset = size.minDimension * 0.075f
    val innerInset = size.minDimension * 0.125f
    // Double vintage distillery label border
    drawRoundRect(
        color = palette.accentLine,
        topLeft = Offset(outerInset, outerInset),
        size = Size(size.width - outerInset * 2f, size.height - outerInset * 2f),
        cornerRadius = CornerRadius(4f, 4f),
        style = Stroke(width = size.minDimension * 0.028f)
    )
    drawRoundRect(
        color = palette.accentLine.copy(alpha = 0.55f),
        topLeft = Offset(innerInset, innerInset),
        size = Size(size.width - innerInset * 2f, size.height - innerInset * 2f),
        cornerRadius = CornerRadius(3f, 3f),
        style = Stroke(width = size.minDimension * 0.014f)
    )
    // Filigree top & bottom accent bars
    val barX1 = size.width * 0.24f
    val barX2 = size.width * 0.76f
    drawLine(
        color = palette.accentLine.copy(alpha = 0.72f),
        start = Offset(barX1, size.height * 0.23f),
        end = Offset(barX2, size.height * 0.23f),
        strokeWidth = size.minDimension * 0.018f
    )
    drawLine(
        color = palette.accentLine.copy(alpha = 0.72f),
        start = Offset(barX1, size.height * 0.77f),
        end = Offset(barX2, size.height * 0.77f),
        strokeWidth = size.minDimension * 0.018f
    )
    // Cream vintage bottle cartouche medallion
    drawOval(
        color = palette.medallionFill,
        topLeft = Offset(size.width * 0.16f, size.height * 0.28f),
        size = Size(size.width * 0.68f, size.height * 0.44f)
    )
    drawOval(
        color = palette.accentLine,
        topLeft = Offset(size.width * 0.16f, size.height * 0.28f),
        size = Size(size.width * 0.68f, size.height * 0.44f),
        style = Stroke(width = size.minDimension * 0.028f)
    )
}

private fun cardOuterFrameColor(theme: UnoCardTheme): Color = when (theme) {
    UnoCardTheme.CLASSIC -> Color.White
    UnoCardTheme.SPACE -> Color(0xFF1F2A48)
    UnoCardTheme.FANTASY -> Color(0xFFDEC383)
    UnoCardTheme.SPIRITS -> Color(0xFFEFE3CA)
}

private data class CardFacePalette(
    val primaryFill: Color,
    val secondaryFill: Color,
    val medallionFill: Color,
    val accentLine: Color,
    val centerInk: Color,
    val cornerInk: Color
)

private fun themedFacePalette(color: UnoColor, theme: UnoCardTheme): CardFacePalette {
    return when (theme) {
        UnoCardTheme.CLASSIC -> when (color) {
            UnoColor.RED -> CardFacePalette(UnoRed, UnoRed, Color.White, Color.White, UnoRed, UnoCardInkLight)
            UnoColor.BLUE -> CardFacePalette(UnoBlue, UnoBlue, Color.White, Color.White, UnoBlue, UnoCardInkLight)
            UnoColor.GREEN -> CardFacePalette(UnoGreen, UnoGreen, Color.White, Color.White, UnoGreen, UnoCardInkLight)
            UnoColor.YELLOW -> CardFacePalette(UnoYellow, UnoYellow, Color.White, Color.White, UnoCardInkDark, UnoCardInkDark)
        }

        UnoCardTheme.SPACE -> when (color) {
            UnoColor.RED -> CardFacePalette(
                primaryFill = Color(0xFFB91C4A),
                secondaryFill = Color(0xFF4C0F29),
                medallionFill = Color(0xFF121629),
                accentLine = Color(0xFFFDA4AF),
                centerInk = Color(0xFFFECDD3),
                cornerInk = Color(0xFFFFF1F2)
            )
            UnoColor.BLUE -> CardFacePalette(
                primaryFill = Color(0xFF1D4ED8),
                secondaryFill = Color(0xFF121F52),
                medallionFill = Color(0xFF0C142B),
                accentLine = Color(0xFF93C5FD),
                centerInk = Color(0xFFDBEAFE),
                cornerInk = Color(0xFFEFF6FF)
            )
            UnoColor.GREEN -> CardFacePalette(
                primaryFill = Color(0xFF0F766E),
                secondaryFill = Color(0xFF0A2E33),
                medallionFill = Color(0xFF091B20),
                accentLine = Color(0xFF5EEAD4),
                centerInk = Color(0xFFCCFBF1),
                cornerInk = Color(0xFFF0FDFA)
            )
            UnoColor.YELLOW -> CardFacePalette(
                primaryFill = Color(0xFFB45309),
                secondaryFill = Color(0xFF422006),
                medallionFill = Color(0xFF1B140C),
                accentLine = Color(0xFFFDE68A),
                centerInk = Color(0xFFFEF3C7),
                cornerInk = Color(0xFFFFFBEB)
            )
        }

        UnoCardTheme.FANTASY -> when (color) {
            UnoColor.RED -> CardFacePalette(
                primaryFill = Color(0xFF8C1D2A),
                secondaryFill = Color(0xFF5A111A),
                medallionFill = Color(0xFFFAF2DF),
                accentLine = Color(0xFFD6B25E),
                centerInk = Color(0xFF7A1723),
                cornerInk = Color(0xFFFAF2DF)
            )
            UnoColor.BLUE -> CardFacePalette(
                primaryFill = Color(0xFF1E3A6E),
                secondaryFill = Color(0xFF112345),
                medallionFill = Color(0xFFFAF2DF),
                accentLine = Color(0xFFD6B25E),
                centerInk = Color(0xFF1A325F),
                cornerInk = Color(0xFFFAF2DF)
            )
            UnoColor.GREEN -> CardFacePalette(
                primaryFill = Color(0xFF1D5B3C),
                secondaryFill = Color(0xFF113825),
                medallionFill = Color(0xFFFAF2DF),
                accentLine = Color(0xFFD6B25E),
                centerInk = Color(0xFF174A31),
                cornerInk = Color(0xFFFAF2DF)
            )
            UnoColor.YELLOW -> CardFacePalette(
                primaryFill = Color(0xFFA67916),
                secondaryFill = Color(0xFF6B4C0C),
                medallionFill = Color(0xFFFAF2DF),
                accentLine = Color(0xFFF3DC9B),
                centerInk = Color(0xFF5E4108),
                cornerInk = Color(0xFFFAF2DF)
            )
        }

        UnoCardTheme.SPIRITS -> when (color) {
            // Spiced Ruby Rum / Campari Crimson
            UnoColor.RED -> CardFacePalette(
                primaryFill = Color(0xFF7E1622),
                secondaryFill = Color(0xFF4C0C14),
                medallionFill = Color(0xFFF7EFE0),
                accentLine = Color(0xFFE6C282),
                centerInk = Color(0xFF63111B),
                cornerInk = Color(0xFFF7EFE0)
            )
            // Sapphire Botanical Gin Navy
            UnoColor.BLUE -> CardFacePalette(
                primaryFill = Color(0xFF143463),
                secondaryFill = Color(0xFF0B1D3A),
                medallionFill = Color(0xFFF7EFE0),
                accentLine = Color(0xFFC5D8F0),
                centerInk = Color(0xFF11294E),
                cornerInk = Color(0xFFF7EFE0)
            )
            // Botanical Absinthe / Irish Whiskey Emerald
            UnoColor.GREEN -> CardFacePalette(
                primaryFill = Color(0xFF134E34),
                secondaryFill = Color(0xFF0A2F1F),
                medallionFill = Color(0xFFF7EFE0),
                accentLine = Color(0xFFD8B774),
                centerInk = Color(0xFF0F3D29),
                cornerInk = Color(0xFFF7EFE0)
            )
            // Jack Daniel's Tennessee Whiskey Charcoal & Amber Gold
            UnoColor.YELLOW -> CardFacePalette(
                primaryFill = Color(0xFF1B1612),
                secondaryFill = Color(0xFF0F0D0B),
                medallionFill = Color(0xFFF7EFE0),
                accentLine = Color(0xFFD99B26),
                centerInk = Color(0xFF1A1511),
                cornerInk = Color(0xFFF3D082)
            )
        }
    }
}
