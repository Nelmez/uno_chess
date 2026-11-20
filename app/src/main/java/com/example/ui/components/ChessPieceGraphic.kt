package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.model.PieceColor
import com.example.model.PieceType
import com.example.ui.theme.BlackPieceFill
import com.example.ui.theme.BlackPieceShade
import com.example.ui.theme.BlackPieceStroke
import com.example.ui.theme.WhitePieceFill
import com.example.ui.theme.WhitePieceShade
import com.example.ui.theme.WhitePieceStroke

@Composable
fun ChessPieceGraphic(
    type: PieceType,
    color: PieceColor,
    modifier: Modifier = Modifier
) {
    val fill = if (color == PieceColor.WHITE) WhitePieceFill else BlackPieceFill
    val accent = if (color == PieceColor.WHITE) WhitePieceShade else BlackPieceShade
    val stroke = if (color == PieceColor.WHITE) WhitePieceStroke else BlackPieceStroke

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val s = minOf(w, h)
        val ox = (w - s) / 2f
        val oy = (h - s) / 2f
        val strokeWidth = (s * 0.055f).coerceAtLeast(1.4f)
        val detailStroke = (s * 0.04f).coerceAtLeast(1.1f)

        // Subtle contact shadow under piece
        drawOval(
            color = Color.Black.copy(alpha = 0.24f),
            topLeft = Offset(ox + s * 0.16f, oy + s * 0.84f),
            size = Size(s * 0.68f, s * 0.11f)
        )

        when (type) {
            PieceType.PAWN -> drawPawn(ox, oy, s, fill, accent, stroke, strokeWidth)
            PieceType.ROOK -> drawRook(ox, oy, s, fill, accent, stroke, strokeWidth, detailStroke)
            PieceType.KNIGHT -> drawKnight(ox, oy, s, fill, accent, stroke, strokeWidth, detailStroke)
            PieceType.BISHOP -> drawBishop(ox, oy, s, fill, accent, stroke, strokeWidth, detailStroke)
            PieceType.QUEEN -> drawQueen(ox, oy, s, fill, accent, stroke, strokeWidth, detailStroke)
            PieceType.KING -> drawKing(ox, oy, s, fill, accent, stroke, strokeWidth, detailStroke)
        }
    }
}

private fun DrawScope.drawFilledAndStroked(
    path: Path,
    fill: Color,
    stroke: Color,
    strokeWidth: Float
) {
    drawPath(path = path, color = fill, style = Fill)
    drawPath(
        path = path,
        color = stroke,
        style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
    )
}

private fun DrawScope.drawPawn(
    ox: Float,
    oy: Float,
    s: Float,
    fill: Color,
    accent: Color,
    stroke: Color,
    strokeWidth: Float
) {
    val body = Path().apply {
        moveTo(ox + s * 0.23f, oy + s * 0.86f)
        lineTo(ox + s * 0.77f, oy + s * 0.86f)
        cubicTo(
            ox + s * 0.77f, oy + s * 0.74f,
            ox + s * 0.65f, oy + s * 0.68f,
            ox + s * 0.60f, oy + s * 0.56f
        )
        lineTo(ox + s * 0.57f, oy + s * 0.44f)
        lineTo(ox + s * 0.43f, oy + s * 0.44f)
        lineTo(ox + s * 0.40f, oy + s * 0.56f)
        cubicTo(
            ox + s * 0.35f, oy + s * 0.68f,
            ox + s * 0.23f, oy + s * 0.74f,
            ox + s * 0.23f, oy + s * 0.86f
        )
        close()
    }
    drawFilledAndStroked(body, fill, stroke, strokeWidth)

    // Collar ring
    drawRoundRect(
        color = accent,
        topLeft = Offset(ox + s * 0.34f, oy + s * 0.42f),
        size = Size(s * 0.32f, s * 0.08f),
        cornerRadius = CornerRadius(s * 0.04f, s * 0.04f)
    )
    drawRoundRect(
        color = stroke,
        topLeft = Offset(ox + s * 0.34f, oy + s * 0.42f),
        size = Size(s * 0.32f, s * 0.08f),
        cornerRadius = CornerRadius(s * 0.04f, s * 0.04f),
        style = Stroke(width = strokeWidth * 0.85f)
    )

    // Head
    drawCircle(
        color = fill,
        radius = s * 0.145f,
        center = Offset(ox + s * 0.50f, oy + s * 0.30f)
    )
    drawCircle(
        color = stroke,
        radius = s * 0.145f,
        center = Offset(ox + s * 0.50f, oy + s * 0.30f),
        style = Stroke(width = strokeWidth)
    )

    // Base line
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.27f, oy + s * 0.78f),
        end = Offset(ox + s * 0.73f, oy + s * 0.78f),
        strokeWidth = strokeWidth * 0.75f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawRook(
    ox: Float,
    oy: Float,
    s: Float,
    fill: Color,
    accent: Color,
    stroke: Color,
    strokeWidth: Float,
    detailStroke: Float
) {
    val tower = Path().apply {
        moveTo(ox + s * 0.20f, oy + s * 0.86f)
        lineTo(ox + s * 0.80f, oy + s * 0.86f)
        lineTo(ox + s * 0.76f, oy + s * 0.74f)
        lineTo(ox + s * 0.67f, oy + s * 0.70f)
        lineTo(ox + s * 0.65f, oy + s * 0.38f)
        lineTo(ox + s * 0.74f, oy + s * 0.31f)
        lineTo(ox + s * 0.74f, oy + s * 0.16f)
        lineTo(ox + s * 0.62f, oy + s * 0.16f)
        lineTo(ox + s * 0.62f, oy + s * 0.24f)
        lineTo(ox + s * 0.55f, oy + s * 0.24f)
        lineTo(ox + s * 0.55f, oy + s * 0.16f)
        lineTo(ox + s * 0.45f, oy + s * 0.16f)
        lineTo(ox + s * 0.45f, oy + s * 0.24f)
        lineTo(ox + s * 0.38f, oy + s * 0.24f)
        lineTo(ox + s * 0.38f, oy + s * 0.16f)
        lineTo(ox + s * 0.26f, oy + s * 0.16f)
        lineTo(ox + s * 0.26f, oy + s * 0.31f)
        lineTo(ox + s * 0.35f, oy + s * 0.38f)
        lineTo(ox + s * 0.33f, oy + s * 0.70f)
        lineTo(ox + s * 0.24f, oy + s * 0.74f)
        close()
    }
    drawFilledAndStroked(tower, fill, stroke, strokeWidth)

    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.34f, oy + s * 0.38f),
        end = Offset(ox + s * 0.66f, oy + s * 0.38f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.32f, oy + s * 0.70f),
        end = Offset(ox + s * 0.68f, oy + s * 0.70f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.25f, oy + s * 0.78f),
        end = Offset(ox + s * 0.75f, oy + s * 0.78f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    // Subtle embrasure shading
    drawLine(
        color = accent,
        start = Offset(ox + s * 0.50f, oy + s * 0.46f),
        end = Offset(ox + s * 0.50f, oy + s * 0.62f),
        strokeWidth = detailStroke * 1.4f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawKnight(
    ox: Float,
    oy: Float,
    s: Float,
    fill: Color,
    accent: Color,
    stroke: Color,
    strokeWidth: Float,
    detailStroke: Float
) {
    val horse = Path().apply {
        moveTo(ox + s * 0.22f, oy + s * 0.86f)
        lineTo(ox + s * 0.80f, oy + s * 0.86f)
        lineTo(ox + s * 0.77f, oy + s * 0.75f)
        // Curved back mane
        cubicTo(
            ox + s * 0.80f, oy + s * 0.48f,
            ox + s * 0.74f, oy + s * 0.22f,
            ox + s * 0.48f, oy + s * 0.16f
        )
        // Ear tip
        lineTo(ox + s * 0.43f, oy + s * 0.12f)
        lineTo(ox + s * 0.40f, oy + s * 0.21f)
        // Forehead to muzzle
        cubicTo(
            ox + s * 0.29f, oy + s * 0.28f,
            ox + s * 0.20f, oy + s * 0.39f,
            ox + s * 0.20f, oy + s * 0.48f
        )
        // Nose & jaw
        cubicTo(
            ox + s * 0.21f, oy + s * 0.54f,
            ox + s * 0.29f, oy + s * 0.56f,
            ox + s * 0.36f, oy + s * 0.51f
        )
        lineTo(ox + s * 0.46f, oy + s * 0.47f)
        // Chest sweep down to base
        cubicTo(
            ox + s * 0.42f, oy + s * 0.58f,
            ox + s * 0.28f, oy + s * 0.65f,
            ox + s * 0.25f, oy + s * 0.75f
        )
        close()
    }
    drawFilledAndStroked(horse, fill, stroke, strokeWidth)

    // Eye dot
    drawCircle(
        color = stroke,
        radius = s * 0.032f,
        center = Offset(ox + s * 0.37f, oy + s * 0.32f)
    )

    // Mane contour accent
    val mane = Path().apply {
        moveTo(ox + s * 0.54f, oy + s * 0.25f)
        cubicTo(
            ox + s * 0.66f, oy + s * 0.35f,
            ox + s * 0.68f, oy + s * 0.55f,
            ox + s * 0.64f, oy + s * 0.72f
        )
    }
    drawPath(
        path = mane,
        color = stroke,
        style = Stroke(width = detailStroke, cap = StrokeCap.Round)
    )

    // Base plinth divider
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.25f, oy + s * 0.76f),
        end = Offset(ox + s * 0.77f, oy + s * 0.76f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    // Nostril accent
    drawCircle(
        color = accent,
        radius = s * 0.02f,
        center = Offset(ox + s * 0.26f, oy + s * 0.46f)
    )
}

private fun DrawScope.drawBishop(
    ox: Float,
    oy: Float,
    s: Float,
    fill: Color,
    accent: Color,
    stroke: Color,
    strokeWidth: Float,
    detailStroke: Float
) {
    val mitre = Path().apply {
        moveTo(ox + s * 0.22f, oy + s * 0.86f)
        lineTo(ox + s * 0.78f, oy + s * 0.86f)
        cubicTo(
            ox + s * 0.76f, oy + s * 0.74f,
            ox + s * 0.65f, oy + s * 0.69f,
            ox + s * 0.60f, oy + s * 0.58f
        )
        cubicTo(
            ox + s * 0.69f, oy + s * 0.45f,
            ox + s * 0.64f, oy + s * 0.28f,
            ox + s * 0.50f, oy + s * 0.20f
        )
        cubicTo(
            ox + s * 0.36f, oy + s * 0.28f,
            ox + s * 0.31f, oy + s * 0.45f,
            ox + s * 0.40f, oy + s * 0.58f
        )
        cubicTo(
            ox + s * 0.35f, oy + s * 0.69f,
            ox + s * 0.24f, oy + s * 0.74f,
            ox + s * 0.22f, oy + s * 0.86f
        )
        close()
    }
    drawFilledAndStroked(mitre, fill, stroke, strokeWidth)

    // Top finial orb
    drawCircle(
        color = fill,
        radius = s * 0.065f,
        center = Offset(ox + s * 0.50f, oy + s * 0.145f)
    )
    drawCircle(
        color = stroke,
        radius = s * 0.065f,
        center = Offset(ox + s * 0.50f, oy + s * 0.145f),
        style = Stroke(width = strokeWidth * 0.85f)
    )

    // Diagonal mitre sash
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.44f, oy + s * 0.33f),
        end = Offset(ox + s * 0.58f, oy + s * 0.46f),
        strokeWidth = detailStroke * 1.15f,
        cap = StrokeCap.Round
    )
    // Collar & base lines
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.38f, oy + s * 0.60f),
        end = Offset(ox + s * 0.62f, oy + s * 0.60f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = accent,
        start = Offset(ox + s * 0.27f, oy + s * 0.78f),
        end = Offset(ox + s * 0.73f, oy + s * 0.78f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawQueen(
    ox: Float,
    oy: Float,
    s: Float,
    fill: Color,
    accent: Color,
    stroke: Color,
    strokeWidth: Float,
    detailStroke: Float
) {
    val crown = Path().apply {
        moveTo(ox + s * 0.22f, oy + s * 0.86f)
        lineTo(ox + s * 0.78f, oy + s * 0.86f)
        lineTo(ox + s * 0.73f, oy + s * 0.72f)
        lineTo(ox + s * 0.84f, oy + s * 0.30f)
        lineTo(ox + s * 0.66f, oy + s * 0.52f)
        lineTo(ox + s * 0.64f, oy + s * 0.22f)
        lineTo(ox + s * 0.50f, oy + s * 0.48f)
        lineTo(ox + s * 0.36f, oy + s * 0.22f)
        lineTo(ox + s * 0.34f, oy + s * 0.52f)
        lineTo(ox + s * 0.16f, oy + s * 0.30f)
        lineTo(ox + s * 0.27f, oy + s * 0.72f)
        close()
    }
    drawFilledAndStroked(crown, fill, stroke, strokeWidth)

    // Crown jewels
    val pearls = listOf(
        Offset(ox + s * 0.16f, oy + s * 0.26f),
        Offset(ox + s * 0.36f, oy + s * 0.18f),
        Offset(ox + s * 0.50f, oy + s * 0.16f),
        Offset(ox + s * 0.64f, oy + s * 0.18f),
        Offset(ox + s * 0.84f, oy + s * 0.26f)
    )
    pearls.forEach { pos ->
        drawCircle(color = fill, radius = s * 0.048f, center = pos)
        drawCircle(
            color = stroke,
            radius = s * 0.048f,
            center = pos,
            style = Stroke(width = detailStroke)
        )
    }

    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.27f, oy + s * 0.72f),
        end = Offset(ox + s * 0.73f, oy + s * 0.72f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = accent,
        start = Offset(ox + s * 0.24f, oy + s * 0.79f),
        end = Offset(ox + s * 0.76f, oy + s * 0.79f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawKing(
    ox: Float,
    oy: Float,
    s: Float,
    fill: Color,
    accent: Color,
    stroke: Color,
    strokeWidth: Float,
    detailStroke: Float
) {
    // Cross atop the King
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.50f, oy + s * 0.09f),
        end = Offset(ox + s * 0.50f, oy + s * 0.29f),
        strokeWidth = strokeWidth * 1.15f,
        cap = StrokeCap.Round
    )
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.41f, oy + s * 0.17f),
        end = Offset(ox + s * 0.59f, oy + s * 0.17f),
        strokeWidth = strokeWidth * 1.15f,
        cap = StrokeCap.Round
    )

    val body = Path().apply {
        moveTo(ox + s * 0.22f, oy + s * 0.86f)
        lineTo(ox + s * 0.78f, oy + s * 0.86f)
        lineTo(ox + s * 0.73f, oy + s * 0.71f)
        // Right lobe
        cubicTo(
            ox + s * 0.86f, oy + s * 0.52f,
            ox + s * 0.80f, oy + s * 0.30f,
            ox + s * 0.60f, oy + s * 0.33f
        )
        // Center peak
        lineTo(ox + s * 0.50f, oy + s * 0.27f)
        lineTo(ox + s * 0.40f, oy + s * 0.33f)
        // Left lobe
        cubicTo(
            ox + s * 0.20f, oy + s * 0.30f,
            ox + s * 0.14f, oy + s * 0.52f,
            ox + s * 0.27f, oy + s * 0.71f
        )
        close()
    }
    drawFilledAndStroked(body, fill, stroke, strokeWidth)

    // Center imperial spine
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.50f, oy + s * 0.30f),
        end = Offset(ox + s * 0.50f, oy + s * 0.69f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = stroke,
        start = Offset(ox + s * 0.27f, oy + s * 0.71f),
        end = Offset(ox + s * 0.73f, oy + s * 0.71f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
    drawLine(
        color = accent,
        start = Offset(ox + s * 0.25f, oy + s * 0.79f),
        end = Offset(ox + s * 0.75f, oy + s * 0.79f),
        strokeWidth = detailStroke,
        cap = StrokeCap.Round
    )
}
