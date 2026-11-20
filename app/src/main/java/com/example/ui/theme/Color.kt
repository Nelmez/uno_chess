package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Clean Warm Paper & Ink Minimalist Palette (Light Defaults)
val PaperBg = Color(0xFFF6F5F1)
val PaperSurface = Color(0xFFFFFFFF)
val PaperSurfaceSubtle = Color(0xFFECEAE3)
val HairlineBorder = Color(0xFFE0DDD4)
val InkPrimary = Color(0xFF1A1A1A)
val InkSecondary = Color(0xFF686660)
val InkMuted = Color(0xFF96938B)

// Matte Neutral Charcoal Palette (Dark Mode — Zero Neon Slop)
val CharcoalBg = Color(0xFF141517)
val CharcoalSurface = Color(0xFF1C1D20)
val CharcoalSurfaceSubtle = Color(0xFF25272B)
val CharcoalHairline = Color(0xFF2F3238)
val CharcoalInkPrimary = Color(0xFFF0EFEA)
val CharcoalInkSecondary = Color(0xFFA5A39C)
val CharcoalInkMuted = Color(0xFF72716B)

// Chessboard Light Palette (Warm Ivory + Muted Sage)
val BoardLightSquare = Color(0xFFF0ECE1)
val BoardDarkSquare = Color(0xFF7B967D)
val BoardBorder = Color(0xFFCFCAC0)
val BoardCoordLight = Color(0xFF6B856D)
val BoardCoordDark = Color(0xFFEAE5D7)

// Chessboard Dark Palette (Muted Warm Sand + Deep Pine)
val BoardLightSquareDark = Color(0xFFB7B1A2)
val BoardDarkSquareDark = Color(0xFF536957)
val BoardBorderDark = Color(0xFF34383F)
val BoardCoordLightDark = Color(0xFF425446)
val BoardCoordDarkDark = Color(0xFFD6D1C5)

// Chessboard Semantic Highlights (Restrained, High-Clarity)
val HighlightSelected = Color(0x88EAB308)
val HighlightLastMove = Color(0x52EAB308)
val HighlightCheckRed = Color(0xB8DC2626)
val HighlightReviveSquare = Color(0x661F9D55)
val HighlightReviveBorder = Color(0xFF1F9D55)
val HighlightFreezeTarget = Color(0x523B82F6)
val HighlightFreezeBorder = Color(0xFF2563EB)
val HighlightFrozenPiece = Color(0x6660A5FA)
val LegalMoveDot = Color(0x4D1A1A1A)
val LegalCaptureRing = Color(0x801A1A1A)
val LegalMoveDotDark = Color(0x66141517)
val LegalCaptureRingDark = Color(0x99141517)

// Chess Pieces Colors (High-Contrast on both Light & Dark Boards)
val WhitePieceFill = Color(0xFFFFFFFF)
val WhitePieceShade = Color(0xFFE6E2D8)
val WhitePieceStroke = Color(0xFF232323)
val BlackPieceFill = Color(0xFF252629)
val BlackPieceShade = Color(0xFF141517)
val BlackPieceStroke = Color(0xFFDFDCD3)

// Clean Matte Swiss-Poster UNO Colors (Classic Theme)
val UnoRed = Color(0xFFD9383A)
val UnoBlue = Color(0xFF2563EB)
val UnoGreen = Color(0xFF1F9D55)
val UnoYellow = Color(0xFFEAB308)
val UnoDeckCharcoal = Color(0xFF1E1F22)
val UnoCardInkLight = Color(0xFFFFFFFF)
val UnoCardInkDark = Color(0xFF1A1A1A)

// Quiet Semantic Accents
val AccentCheck = Color(0xFFDC2626)
val AccentCheckDark = Color(0xFFEF5350)
val AccentRevive = Color(0xFF1F9D55)
val AccentReviveDark = Color(0xFF34B368)
val AccentFreeze = Color(0xFF2563EB)
val AccentFreezeDark = Color(0xFF60A5FA)
val AccentTrade = Color(0xFFB45309)
val AccentTradeDark = Color(0xFFF59E0B)
val AccentHijack = Color(0xFF7C3AED)
val AccentHijackDark = Color(0xFFA78BFA)
val AccentPromote = Color(0xFF0D9488)
val AccentPromoteDark = Color(0xFF2DD4BF)
val AccentShield = Color(0xFF0284C7)
val AccentShieldDark = Color(0xFF38BDF8)
val AccentSwap = Color(0xFF4F46E5)
val AccentSwapDark = Color(0xFF818CF8)
val AccentClone = Color(0xFF059669)
val AccentCloneDark = Color(0xFF34D399)
val AccentWild = Color(0xFFD97706)
val AccentWildDark = Color(0xFFFBBF24)

@Immutable
data class UnoChessColors(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val hairline: Color,
    val inkPrimary: Color,
    val inkSecondary: Color,
    val inkMuted: Color,
    val boardLight: Color,
    val boardDark: Color,
    val boardBorder: Color,
    val boardCoordLight: Color,
    val boardCoordDark: Color,
    val legalMoveDot: Color,
    val legalCaptureRing: Color,
    val accentCheck: Color,
    val accentRevive: Color,
    val accentFreeze: Color,
    val accentTrade: Color,
    val accentHijack: Color,
    val accentPromote: Color,
    val accentShield: Color,
    val accentSwap: Color,
    val accentClone: Color,
    val accentWild: Color,
    val highlightFreezeTarget: Color,
    val highlightFreezeBorder: Color,
    val highlightFrozenPiece: Color,
    val highlightShieldTarget: Color,
    val highlightShieldedPiece: Color,
    val highlightShieldBorder: Color,
    val highlightTradeSelected: Color,
    val highlightTradeBorder: Color,
    val highlightSwapTarget: Color,
    val highlightSwapSelected: Color,
    val highlightSwapBorder: Color,
    val highlightCloneTarget: Color,
    val highlightCloneSelected: Color,
    val highlightCloneBorder: Color,
    val highlightHijackSelected: Color,
    val highlightHijackBorder: Color,
    val highlightPromoteTarget: Color,
    val highlightPromoteSelected: Color,
    val highlightPromoteBorder: Color
)

val LightUnoChessColors = UnoChessColors(
    isDark = false,
    bg = PaperBg,
    surface = PaperSurface,
    surfaceSubtle = PaperSurfaceSubtle,
    hairline = HairlineBorder,
    inkPrimary = InkPrimary,
    inkSecondary = InkSecondary,
    inkMuted = InkMuted,
    boardLight = BoardLightSquare,
    boardDark = BoardDarkSquare,
    boardBorder = BoardBorder,
    boardCoordLight = BoardCoordLight,
    boardCoordDark = BoardCoordDark,
    legalMoveDot = LegalMoveDot,
    legalCaptureRing = LegalCaptureRing,
    accentCheck = AccentCheck,
    accentRevive = AccentRevive,
    accentFreeze = AccentFreeze,
    accentTrade = AccentTrade,
    accentHijack = AccentHijack,
    accentPromote = AccentPromote,
    accentShield = AccentShield,
    accentSwap = AccentSwap,
    accentClone = AccentClone,
    accentWild = AccentWild,
    highlightFreezeTarget = HighlightFreezeTarget,
    highlightFreezeBorder = HighlightFreezeBorder,
    highlightFrozenPiece = HighlightFrozenPiece,
    highlightShieldTarget = Color(0x450284C7),
    highlightShieldedPiece = Color(0x660EA5E9),
    highlightShieldBorder = Color(0xFF0284C7),
    highlightTradeSelected = Color(0x66D97706),
    highlightTradeBorder = Color(0xFFB45309),
    highlightSwapTarget = Color(0x3F4F46E5),
    highlightSwapSelected = Color(0x754F46E5),
    highlightSwapBorder = Color(0xFF4338CA),
    highlightCloneTarget = Color(0x45059669),
    highlightCloneSelected = Color(0x75059669),
    highlightCloneBorder = Color(0xFF047857),
    highlightHijackSelected = Color(0x667C3AED),
    highlightHijackBorder = Color(0xFF7C3AED),
    highlightPromoteTarget = Color(0x400D9488),
    highlightPromoteSelected = Color(0x780D9488),
    highlightPromoteBorder = Color(0xFF0F766E)
)

val DarkUnoChessColors = UnoChessColors(
    isDark = true,
    bg = CharcoalBg,
    surface = CharcoalSurface,
    surfaceSubtle = CharcoalSurfaceSubtle,
    hairline = CharcoalHairline,
    inkPrimary = CharcoalInkPrimary,
    inkSecondary = CharcoalInkSecondary,
    inkMuted = CharcoalInkMuted,
    boardLight = BoardLightSquareDark,
    boardDark = BoardDarkSquareDark,
    boardBorder = BoardBorderDark,
    boardCoordLight = BoardCoordLightDark,
    boardCoordDark = BoardCoordDarkDark,
    legalMoveDot = LegalMoveDotDark,
    legalCaptureRing = LegalCaptureRingDark,
    accentCheck = AccentCheckDark,
    accentRevive = AccentReviveDark,
    accentFreeze = AccentFreezeDark,
    accentTrade = AccentTradeDark,
    accentHijack = AccentHijackDark,
    accentPromote = AccentPromoteDark,
    accentShield = AccentShieldDark,
    accentSwap = AccentSwapDark,
    accentClone = AccentCloneDark,
    accentWild = AccentWildDark,
    highlightFreezeTarget = Color(0x5560A5FA),
    highlightFreezeBorder = Color(0xFF93C5FD),
    highlightFrozenPiece = Color(0x7760A5FA),
    highlightShieldTarget = Color(0x4838BDF8),
    highlightShieldedPiece = Color(0x7038BDF8),
    highlightShieldBorder = Color(0xFF7DD3FC),
    highlightTradeSelected = Color(0x66F59E0B),
    highlightTradeBorder = Color(0xFFFBBF24),
    highlightSwapTarget = Color(0x45818CF8),
    highlightSwapSelected = Color(0x7A818CF8),
    highlightSwapBorder = Color(0xFFA5B4FC),
    highlightCloneTarget = Color(0x4834D399),
    highlightCloneSelected = Color(0x7A34D399),
    highlightCloneBorder = Color(0xFF6EE7B7),
    highlightHijackSelected = Color(0x66A78BFA),
    highlightHijackBorder = Color(0xFFC4B5FD),
    highlightPromoteTarget = Color(0x452DD4BF),
    highlightPromoteSelected = Color(0x7A2DD4BF),
    highlightPromoteBorder = Color(0xFF5EEAD4)
)
