package com.audiophile.player.ui.theme

import android.app.Activity
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/**
 * Veyl Material 3 Expressive Design System
 * Fluid native Android audiophile experience
 */

// -------------------------------------------------------------------------
// 1. Color Tokens (Stitch Resonate Audiophile Obsidian & Warm Coral Palette)
// -------------------------------------------------------------------------
val VeylBackgroundDark = Color(0xFF1B0906)
val VeylSurfaceDark = Color(0xFF1B0906)
val VeylSurfaceContainerLowest = Color(0xFF000000)
val VeylSurfaceContainerLow = Color(0xFF230D09)
val VeylSurfaceContainer = Color(0xFF2C120D)
val VeylSurfaceContainerHigh = Color(0xFF351711)
val VeylSurfaceContainerHighest = Color(0xFF3E1C15)

val VeylPrimary = Color(0xFFFFB7B4)
val VeylOnPrimary = Color(0xFF6D2E2E)
val VeylPrimaryContainer = Color(0xFFFDA4A1)
val VeylOnPrimaryContainer = Color(0xFF612525)

val VeylSecondary = Color(0xFFB2CAD3)
val VeylSecondaryContainer = Color(0xFF122930)
val VeylOnSecondaryContainer = Color(0xFF90A8B0)

val VeylTertiary = Color(0xFF92EAFF)
val VeylTertiaryContainer = Color(0xFF5EDFFB)

val VeylFavorite = Color(0xFFFFB7B4)
val VeylError = Color(0xFFFE7453)

val VeylTextPrimary = Color(0xFFFFDED8)
val VeylTextSecondary = Color(0xFFD59E93)
val VeylTextMuted = Color(0xFF9A6A60)

val VeylBorderHairline = Color(0x33663D35)
val VeylBorderActive = Color(0x66FFB7B4)
val VeylGlassButtonBg = Color(0x33351711)

// -------------------------------------------------------------------------
// Theme Presets & Color Scheme Architecture
// -------------------------------------------------------------------------

data class ThemePreset(
    val id: String,
    val name: String,
    val description: String,
    val primary: Color,
    val secondary: Color,
    val tertiary: Color,
    val background: Color,
    val isDark: Boolean = true
)

val CuratedPresets = listOf(
    ThemePreset(
        id = "resonate_obsidian",
        name = "Resonate Obsidian",
        description = "Warm studio dusk with coral blush & soft slate",
        primary = Color(0xFFFFB7B4),
        secondary = Color(0xFFB2CAD3),
        tertiary = Color(0xFF92EAFF),
        background = Color(0xFF1B0906)
    ),
    ThemePreset(
        id = "oled_pure_black",
        name = "OLED True Black",
        description = "Zero-pixel black with electric cyan & ice blue",
        primary = Color(0xFF00F0FF),
        secondary = Color(0xFF70A5FF),
        tertiary = Color(0xFF39FF14),
        background = Color(0xFF000000)
    ),
    ThemePreset(
        id = "cyberpunk_amber",
        name = "Cyberpunk Amber",
        description = "Warm vacuum tube glow with radiant amber & brass",
        primary = Color(0xFFFF9E00),
        secondary = Color(0xFFFFD166),
        tertiary = Color(0xFFFF5400),
        background = Color(0xFF120C06)
    ),
    ThemePreset(
        id = "nordic_slate",
        name = "Nordic Slate",
        description = "Deep midnight with arctic glacier blue & mint",
        primary = Color(0xFF58A6FF),
        secondary = Color(0xFF7EE787),
        tertiary = Color(0xFFBC8CFF),
        background = Color(0xFF0D1117)
    ),
    ThemePreset(
        id = "emerald_studio",
        name = "Emerald Studio",
        description = "Vintage console meters with analog emerald & sage",
        primary = Color(0xFF2ECC71),
        secondary = Color(0xFF50E3C2),
        tertiary = Color(0xFFF1C40F),
        background = Color(0xFF08140E)
    ),
    ThemePreset(
        id = "monochrome_carbon",
        name = "Monochrome Carbon",
        description = "Mastering minimalism with crisp studio white & platinum",
        primary = Color(0xFFF8FAFC),
        secondary = Color(0xFF94A3B8),
        tertiary = Color(0xFF38BDF8),
        background = Color(0xFF121212)
    ),
    ThemePreset(
        id = "royal_violet",
        name = "Royal Velvet",
        description = "Rich twilight atmosphere with electric lavender & rose",
        primary = Color(0xFFD8B4FE),
        secondary = Color(0xFFF472B6),
        tertiary = Color(0xFF818CF8),
        background = Color(0xFF13091B)
    )
)

fun Color.toHex(): String {
    val argb = this.toArgb()
    return String.format("#%06X", 0xFFFFFF and argb)
}

fun parseColorFromHex(hex: String, fallback: Color): Color {
    return try {
        val clean = hex.trim().removePrefix("#")
        val parsed = clean.toLong(16)
        if (clean.length == 6) {
            Color((0xFF000000 or parsed).toInt())
        } else if (clean.length == 8) {
            Color(parsed.toInt())
        } else {
            fallback
        }
    } catch (e: Exception) {
        fallback
    }
}

fun isColorLight(color: Color): Boolean {
    val r = color.red
    val g = color.green
    val b = color.blue
    val luminance = 0.299 * r + 0.587 * g + 0.114 * b
    return luminance > 0.55
}

fun shiftColorLightness(base: Color, deltaL: Float): Color {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(base.toArgb(), hsl)
    hsl[2] = (hsl[2] + deltaL).coerceIn(0.04f, 0.96f)
    return Color(androidx.core.graphics.ColorUtils.HSLToColor(hsl))
}

fun buildVeylColorScheme(
    primary: Color,
    secondary: Color,
    tertiary: Color = Color(0xFF92EAFF),
    background: Color,
    isDark: Boolean = true
): VeylColorScheme {
    if (!isDark) {
        return VeylLightColorScheme
    }

    val isBlackBg = background.red < 0.03f && background.green < 0.03f && background.blue < 0.03f
    val surfacePanel = if (isBlackBg) Color(0xFF111111) else shiftColorLightness(background, 0.05f)
    val surfaceElevated = if (isBlackBg) Color(0xFF1A1A1A) else shiftColorLightness(background, 0.09f)
    val surfacePill = if (isBlackBg) Color(0xFF242424) else shiftColorLightness(background, 0.14f)
    
    val textPrimary = if (isColorLight(background)) Color(0xFF1A1A1A) else Color(0xFFF1F5F9)
    val textSecondary = if (isColorLight(background)) Color(0xFF4A4A4A) else Color(0xFF94A3B8)
    val textMuted = if (isColorLight(background)) Color(0xFF888888) else Color(0xFF64748B)

    return VeylColorScheme(
        background = background,
        surfacePanel = surfacePanel,
        surfaceElevated = surfaceElevated,
        surfacePill = surfacePill,
        borderHairline = primary.copy(alpha = 0.18f),
        borderActive = primary.copy(alpha = 0.55f),
        accentSignal = primary,
        accentCyan = tertiary,
        accentPeak = Color(0xFFFE7453),
        accentFavorite = primary,
        primaryContainer = primary.copy(alpha = 0.22f),
        textPrimary = textPrimary,
        textSecondary = textSecondary,
        textMuted = textMuted,
        textMono = secondary,
        glassButtonBg = surfaceElevated.copy(alpha = 0.45f)
    )
}

fun buildM3ColorScheme(
    veylColors: VeylColorScheme,
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
    isDark: Boolean = true
): androidx.compose.material3.ColorScheme {
    val onPrimary = if (isColorLight(primary)) Color.Black else Color.White
    val onSecondary = if (isColorLight(secondary)) Color.Black else Color.White

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = veylColors.primaryContainer,
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = onSecondary,
        secondaryContainer = secondary.copy(alpha = 0.20f),
        onSecondaryContainer = secondary,
        tertiary = tertiary,
        tertiaryContainer = tertiary.copy(alpha = 0.20f),
        background = background,
        onBackground = veylColors.textPrimary,
        surface = background,
        onSurface = veylColors.textPrimary,
        surfaceVariant = veylColors.surfacePanel,
        onSurfaceVariant = veylColors.textSecondary,
        outline = veylColors.borderHairline,
        outlineVariant = veylColors.borderActive,
        error = VeylError,
        onError = Color.White
    )
}

@Immutable
data class VeylColorScheme(
    val background: Color = VeylBackgroundDark,
    val surfacePanel: Color = VeylSurfaceContainer,
    val surfaceElevated: Color = VeylSurfaceContainerHigh,
    val surfacePill: Color = VeylSurfaceContainerHighest,
    val borderHairline: Color = VeylBorderHairline,
    val borderActive: Color = VeylBorderActive,
    val accentSignal: Color = VeylPrimary,
    val accentCyan: Color = VeylTertiary,
    val accentPeak: Color = Color(0xFFFE7453),
    val accentFavorite: Color = VeylFavorite,
    val primaryContainer: Color = VeylPrimaryContainer,
    val textPrimary: Color = VeylTextPrimary,
    val textSecondary: Color = VeylTextSecondary,
    val textMuted: Color = VeylTextMuted,
    val textMono: Color = VeylSecondary,
    val glassButtonBg: Color = VeylGlassButtonBg
)

val VeylDarkColorScheme = VeylColorScheme(
    background = VeylBackgroundDark,
    surfacePanel = VeylSurfaceContainer,
    surfaceElevated = VeylSurfaceContainerHigh,
    surfacePill = VeylSurfaceContainerHighest,
    borderHairline = VeylBorderHairline,
    borderActive = VeylBorderActive,
    accentSignal = VeylPrimary,
    accentCyan = VeylTertiary,
    accentPeak = Color(0xFFFE7453),
    accentFavorite = VeylFavorite,
    primaryContainer = VeylPrimaryContainer,
    textPrimary = VeylTextPrimary,
    textSecondary = VeylTextSecondary,
    textMuted = VeylTextMuted,
    textMono = VeylSecondary,
    glassButtonBg = VeylGlassButtonBg
)

val VeylLightColorScheme = VeylColorScheme(
    background = Color(0xFFFFF8F6),
    surfacePanel = Color(0xFFF7E6E2),
    surfaceElevated = Color(0xFFEEDCD7),
    surfacePill = Color(0xFFE5D2CD),
    borderHairline = Color(0x1F663D35),
    borderActive = Color(0x666D2E2E),
    accentSignal = Color(0xFF6D2E2E),
    accentCyan = Color(0xFF005664),
    accentPeak = Color(0xFF881F05),
    accentFavorite = Color(0xFF904A49),
    primaryContainer = Color(0xFFFDA4A1),
    textPrimary = Color(0xFF2C120D),
    textSecondary = Color(0xFF6A4E49),
    textMuted = Color(0xFF9A6A60),
    textMono = Color(0xFF2D434B),
    glassButtonBg = Color(0x22EEDCD7)
)

val LocalVeylColors = compositionLocalOf { VeylDarkColorScheme }

private val VeylM3DarkColorScheme = darkColorScheme(
    primary = VeylPrimary,
    onPrimary = VeylOnPrimary,
    primaryContainer = VeylPrimaryContainer,
    onPrimaryContainer = VeylOnPrimaryContainer,
    secondary = VeylSecondary,
    onSecondary = VeylSurfaceDark,
    secondaryContainer = VeylSecondaryContainer,
    onSecondaryContainer = VeylOnSecondaryContainer,
    tertiary = VeylTertiary,
    tertiaryContainer = VeylTertiaryContainer,
    background = VeylBackgroundDark,
    onBackground = VeylTextPrimary,
    surface = VeylSurfaceDark,
    onSurface = VeylTextPrimary,
    surfaceVariant = VeylSurfaceContainer,
    onSurfaceVariant = VeylTextSecondary,
    outline = VeylBorderHairline,
    outlineVariant = VeylBorderActive,
    error = VeylError,
    onError = Color.White
)

private val VeylM3LightColorScheme = androidx.compose.material3.lightColorScheme(
    primary = Color(0xFF3F51B5),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDEE1F9),
    onPrimaryContainer = Color(0xFF151D36),
    secondary = Color(0xFF5A5D72),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDFE1F9),
    onSecondaryContainer = Color(0xFF171A2C),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF191A20),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF191A20),
    surfaceVariant = Color(0xFFECEEF5),
    onSurfaceVariant = Color(0xFF45464F),
    outline = Color(0x1F000000),
    outlineVariant = Color(0x663F51B5),
    error = VeylError,
    onError = Color.White
)

// -------------------------------------------------------------------------
// 2. Custom Type Scale (Display + Dense Telemetry Monospace)
// -------------------------------------------------------------------------
object VeylTypography {
    val HeadlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp,
        color = VeylTextPrimary
    )

    val HeadlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = (-0.3).sp,
        color = VeylTextPrimary
    )

    val DisplayLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = (-0.5).sp,
        color = VeylTextPrimary
    )

    val DisplayMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = (-0.3).sp,
        color = VeylTextPrimary
    )

    val SectionHeader = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        letterSpacing = (-0.2).sp,
        color = VeylTextPrimary
    )

    val TitleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        letterSpacing = (-0.3).sp,
        color = VeylTextPrimary
    )

    val TitleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = (-0.1).sp,
        color = VeylTextPrimary
    )

    val BodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = VeylTextPrimary
    )

    val Body = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = VeylTextSecondary
    )

    val BodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = VeylTextMuted
    )

    val MonoSpec = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        letterSpacing = 0.5.sp,
        color = VeylPrimary
    )

    val MonoBadge = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        color = VeylPrimary
    )
}

// -------------------------------------------------------------------------
// 3. Motion & Spring Physics Tokens
// -------------------------------------------------------------------------
object VeylMotion {
    val TactileSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val SnappySpring = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMedium
    )

    const val DurationFast = 180
    const val DurationStandard = 240
}

// -------------------------------------------------------------------------
// 4. Spacing & Shape Tokens
// -------------------------------------------------------------------------
object VeylSpacing {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp

    val RadiusSm = 8.dp
    val RadiusMd = 14.dp
    val RadiusLg = 20.dp
    val RadiusXl = 28.dp
}

@Composable
fun VeylTheme(
    isDarkMode: Boolean = true,
    colorScheme: VeylColorScheme? = null,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val veylColors = if (isDarkMode) {
        colorScheme ?: VeylDarkColorScheme
    } else {
        VeylLightColorScheme
    }
    
    val m3Colors = if (isDarkMode) {
        buildM3ColorScheme(
            veylColors = veylColors,
            primary = veylColors.accentSignal,
            secondary = veylColors.textMono,
            tertiary = veylColors.accentCyan,
            background = veylColors.background,
            isDark = true
        )
    } else {
        VeylM3LightColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = veylColors.background.toArgb()
                window.navigationBarColor = veylColors.background.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !isDarkMode
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !isDarkMode
            }
        }
    }

    CompositionLocalProvider(
        LocalVeylColors provides veylColors
    ) {
        MaterialTheme(
            colorScheme = m3Colors,
            content = content
        )
    }
}

