package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp

val LocalUnoChessColors = staticCompositionLocalOf { LightUnoChessColors }

private val UnoChessLightColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = PaperSurface,
    primaryContainer = PaperSurfaceSubtle,
    onPrimaryContainer = InkPrimary,
    secondary = UnoBlue,
    onSecondary = UnoCardInkLight,
    secondaryContainer = PaperSurfaceSubtle,
    onSecondaryContainer = InkPrimary,
    tertiary = UnoGreen,
    onTertiary = UnoCardInkLight,
    error = AccentCheck,
    onError = UnoCardInkLight,
    background = PaperBg,
    onBackground = InkPrimary,
    surface = PaperSurface,
    onSurface = InkPrimary,
    surfaceVariant = PaperSurfaceSubtle,
    onSurfaceVariant = InkSecondary,
    outline = HairlineBorder,
    outlineVariant = BoardBorder
)

private val UnoChessDarkColorScheme = darkColorScheme(
    primary = CharcoalInkPrimary,
    onPrimary = CharcoalBg,
    primaryContainer = CharcoalSurfaceSubtle,
    onPrimaryContainer = CharcoalInkPrimary,
    secondary = AccentFreezeDark,
    onSecondary = CharcoalBg,
    secondaryContainer = CharcoalSurfaceSubtle,
    onSecondaryContainer = CharcoalInkPrimary,
    tertiary = AccentReviveDark,
    onTertiary = CharcoalBg,
    error = AccentCheckDark,
    onError = CharcoalBg,
    background = CharcoalBg,
    onBackground = CharcoalInkPrimary,
    surface = CharcoalSurface,
    onSurface = CharcoalInkPrimary,
    surfaceVariant = CharcoalSurfaceSubtle,
    onSurfaceVariant = CharcoalInkSecondary,
    outline = CharcoalHairline,
    outlineVariant = BoardBorderDark
)

val UnoChessShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp)
)

@Composable
fun UnoChessTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val palette = if (darkTheme) DarkUnoChessColors else LightUnoChessColors
    val colorScheme = if (darkTheme) UnoChessDarkColorScheme else UnoChessLightColorScheme

    CompositionLocalProvider(LocalUnoChessColors provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = UnoChessTypography,
            shapes = UnoChessShapes,
            content = content
        )
    }
}
