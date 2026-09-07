package com.audiophile.player.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// ==========================================
// Base Color Palette (Lime Neon & Obsidian Glass)
// ==========================================

// Primary Signature Accent
val LimeAccent = Color(0xFFD2E823)
val LimeAccentBright = Color(0xFFDAEE30)
val LimeAccentGlow = Color(0x33D2E823)
val LimeAccentDim = Color(0xFF7A8A0E)

// Dark Surfaces (Obsidian & Glass)
val DarkBgObsidian = Color(0xFF0E0E12)
val DarkBgSurface = Color(0xFF14151C)
val DarkSurfaceCard = Color(0xFF1B1C25)
val DarkSurfacePill = Color(0xFF222430)
val DarkSurfaceHighlight = Color(0xFF2C2E3D)

// Glass & Translucent Elements
val GlassWhite10 = Color(0x1AFFFFFF)
val GlassWhite15 = Color(0x26FFFFFF)
val GlassWhite20 = Color(0x33FFFFFF)
val GlassBorder = Color(0x22FFFFFF)

// Text & Readouts
val DarkTextPrimary = Color(0xFFF6F6F8)
val DarkTextSecondary = Color(0xFFA5A7B4)
val DarkTextTertiary = Color(0xFF6B6D7C)

// Accents & Signals
val FavoritePink = Color(0xFFFF4B72)
val SignalAmber = Color(0xFFFFB300)
val CyberCyan = Color(0xFF00E5FF)
val SpectralGreen = Color(0xFF10B981)
val DangerRed = Color(0xFFEF4444)

// Legacy alias definitions
val ObsidianBlack = DarkBgObsidian
val SurfaceDark = DarkBgSurface
val SurfaceElevated = DarkSurfaceCard
val SurfaceHighlight = DarkSurfaceHighlight
val BorderSubtle = GlassBorder
val BorderActive = Color(0xFF3B3D4F)
val TextPrimary = DarkTextPrimary
val TextSecondary = DarkTextSecondary
val TextTertiary = DarkTextTertiary
val TextMonospace = LimeAccent
val ChampagneGold = LimeAccent

@Immutable
data class SoundwaveColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val surfaceHighlight: Color,
    val borderSubtle: Color,
    val borderActive: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accentPrimary: Color,
    val accentSecondary: Color,
    val glassButtonBg: Color,
    val pillActiveBg: Color,
    val pillActiveText: Color,
    val pillInactiveBg: Color,
    val pillInactiveText: Color,
    val miniPlayerBg: Color,
    val miniPlayerText: Color,
    val chipSelectedBg: Color,
    val chipSelectedText: Color,
    val chipUnselectedBg: Color,
    val chipUnselectedText: Color,
    val heroCardBg: Color = DarkSurfaceCard,
    val heroCardText: Color = DarkTextPrimary,
    val favoriteColor: Color = FavoritePink,
)

val DarkSoundwaveColors = SoundwaveColors(
    isDark = true,
    background = DarkBgObsidian,
    surface = DarkBgSurface,
    surfaceElevated = DarkSurfaceCard,
    surfaceHighlight = DarkSurfaceHighlight,
    borderSubtle = GlassBorder,
    borderActive = BorderActive,
    textPrimary = DarkTextPrimary,
    textSecondary = DarkTextSecondary,
    textTertiary = DarkTextTertiary,
    accentPrimary = LimeAccent,
    accentSecondary = LimeAccent,
    glassButtonBg = GlassWhite15,
    pillActiveBg = LimeAccent,
    pillActiveText = Color(0xFF0E0E12),
    pillInactiveBg = DarkSurfacePill,
    pillInactiveText = DarkTextSecondary,
    miniPlayerBg = Color(0xEA161722),
    miniPlayerText = DarkTextPrimary,
    chipSelectedBg = LimeAccent,
    chipSelectedText = Color(0xFF0E0E12),
    chipUnselectedBg = DarkSurfacePill,
    chipUnselectedText = DarkTextSecondary,
    heroCardBg = DarkSurfaceCard,
    heroCardText = DarkTextPrimary
)

val LightSoundwaveColors = DarkSoundwaveColors.copy(
    isDark = false,
    background = Color(0xFFF7F5F0),
    surface = Color(0xFFFFFFFF),
    surfaceElevated = Color(0xFFECE7DE),
    surfaceHighlight = Color(0xFFE2DDD3),
    borderSubtle = Color(0xFFE4DFD5),
    borderActive = Color(0xFFD0C9BD),
    textPrimary = Color(0xFF141312),
    textSecondary = Color(0xFF6B6763),
    textTertiary = Color(0xFF9E9993),
    glassButtonBg = Color(0x1A000000),
    pillActiveBg = Color(0xFF141312),
    pillActiveText = Color(0xFFFFFFFF),
    pillInactiveBg = Color(0xFFEBE6DD),
    pillInactiveText = Color(0xFF5E5A56),
    chipSelectedBg = Color(0xFF141312),
    chipSelectedText = Color(0xFFFFFFFF),
    chipUnselectedBg = Color(0xFFEBE6DD),
    chipUnselectedText = Color(0xFF5E5A56)
)
