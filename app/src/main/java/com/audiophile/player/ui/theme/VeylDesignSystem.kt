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
        id = "monochrome_carbon",
        name = "Monochrome Carbon",
        description = "Mastering studio minimalism with carbon & crisp platinum",
        primary = Color(0xFFFFFFFF),
        secondary = Color(0xFF94A3B8),
        tertiary = Color(0xFF38BDF8),
        background = Color(0xFF121212)
    ),
    ThemePreset(
        id = "braun_rams",
        name = "Braun Dieter Rams",
        description = "Bauhaus functionalism with iconic Braun orange & matte graphite",
        primary = Color(0xFFFF5722),
        secondary = Color(0xFFA1A1AA),
        tertiary = Color(0xFF60A5FA),
        background = Color(0xFF18181B)
    ),
    ThemePreset(
        id = "mcintosh_blue",
        name = "McIntosh Laboratory",
        description = "Legendary audiophile blue VU meters & warm tube glow",
        primary = Color(0xFF00A3E0),
        secondary = Color(0xFFFFB800),
        tertiary = Color(0xFF00E5FF),
        background = Color(0xFF0A0D14)
    ),
    ThemePreset(
        id = "teenage_op1",
        name = "Teenage Engineering",
        description = "Tactile synthesizer lab with punchy yellow & sharp cyan",
        primary = Color(0xFFFEE75C),
        secondary = Color(0xFF00E5FF),
        tertiary = Color(0xFFFF5722),
        background = Color(0xFF161719)
    ),
    ThemePreset(
        id = "sony_signature",
        name = "Sony Walkman Gold",
        description = "WM1ZM2 oxygen-free copper chassis & royal champagne gold",
        primary = Color(0xFFFFD700),
        secondary = Color(0xFFD49A6A),
        tertiary = Color(0xFFFFA726),
        background = Color(0xFF14100C)
    ),
    ThemePreset(
        id = "abbey_road",
        name = "Abbey Road Studio",
        description = "Warm 1970s analogue tape console with vintage VU amber",
        primary = Color(0xFFFF8C00),
        secondary = Color(0xFFD4C7B8),
        tertiary = Color(0xFFE65100),
        background = Color(0xFF1A120E)
    ),
    ThemePreset(
        id = "midnight_studio",
        name = "Midnight Studio",
        description = "Deep midnight void with vivid electric indigo & glacier sky",
        primary = Color(0xFF6366F1),
        secondary = Color(0xFF38BDF8),
        tertiary = Color(0xFFA855F7),
        background = Color(0xFF0B0E14)
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
    val r = base.red
    val g = base.green
    val b = base.blue

    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val d = max - min

    val l = (max + min) / 2f
    val h: Float
    val s: Float

    if (d == 0f) {
        h = 0f
        s = 0f
    } else {
        s = if (l > 0.5f) d / (2f - max - min) else d / (max + min)
        h = when (max) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f)) / 6f
            g -> ((b - r) / d + 2f) / 6f
            else -> ((r - g) / d + 4f) / 6f
        }
    }

    val newL = (l + deltaL).coerceIn(0.04f, 0.96f)

    fun hue2rgb(p: Float, q: Float, t: Float): Float {
        var tc = t
        if (tc < 0f) tc += 1f
        if (tc > 1f) tc -= 1f
        return when {
            tc < 1f / 6f -> p + (q - p) * 6f * tc
            tc < 1f / 2f -> q
            tc < 2f / 3f -> p + (q - p) * (2f / 3f - tc) * 6f
            else -> p
        }
    }

    val q = if (newL < 0.5f) newL * (1f + s) else newL + s - newL * s
    val p = 2f * newL - q

    val finalR = if (s == 0f) newL else hue2rgb(p, q, h + 1f / 3f)
    val finalG = if (s == 0f) newL else hue2rgb(p, q, h)
    val finalB = if (s == 0f) newL else hue2rgb(p, q, h - 1f / 3f)

    return Color(finalR.coerceIn(0f, 1f), finalG.coerceIn(0f, 1f), finalB.coerceIn(0f, 1f), base.alpha)
}

fun buildVeylColorScheme(
    primary: Color,
    secondary: Color,
    tertiary: Color = Color(0xFF92EAFF),
    background: Color,
    isDark: Boolean = true
): VeylColorScheme {
    if (!isDark) {
        // High-contrast, elegant audiophile light mode
        val lightBg = if (isColorLight(background)) background else Color(0xFFFAFAFA)
        val surfacePanel = Color(0xFFFFFFFF)
        val surfaceElevated = Color(0xFFF4F4F5)
        val surfacePill = Color(0xFFE4E4E7)

        // Ensure primary accent has high contrast on light background
        val lightPrimary = if (isColorLight(primary)) {
            if (primary.red > 0.85f && primary.green > 0.85f && primary.blue > 0.85f) {
                Color(0xFF18181B) // Jet Carbon for white
            } else {
                shiftColorLightness(primary, -0.32f)
            }
        } else {
            primary
        }

        val lightSecondary = if (isColorLight(secondary)) {
            shiftColorLightness(secondary, -0.25f)
        } else {
            secondary
        }

        return VeylColorScheme(
            background = lightBg,
            surfacePanel = surfacePanel,
            surfaceElevated = surfaceElevated,
            surfacePill = surfacePill,
            borderHairline = Color(0x1F000000),
            borderActive = lightPrimary.copy(alpha = 0.55f),
            accentSignal = lightPrimary,
            accentCyan = tertiary,
            accentPeak = Color(0xFFDC2626),
            accentFavorite = lightPrimary,
            primaryContainer = lightPrimary.copy(alpha = 0.15f),
            textPrimary = Color(0xFF09090B),
            textSecondary = Color(0xFF52525B),
            textMuted = Color(0xFF71717A),
            textMono = lightSecondary,
            glassButtonBg = Color(0x14000000)
        )
    }

    // Dark Mode
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

    return if (isDark) {
        darkColorScheme(
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
    } else {
        androidx.compose.material3.lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primary.copy(alpha = 0.15f),
            onPrimaryContainer = primary,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondary.copy(alpha = 0.12f),
            onSecondaryContainer = secondary,
            tertiary = tertiary,
            tertiaryContainer = tertiary.copy(alpha = 0.12f),
            background = veylColors.background,
            onBackground = veylColors.textPrimary,
            surface = veylColors.surfacePanel,
            onSurface = veylColors.textPrimary,
            surfaceVariant = veylColors.surfaceElevated,
            onSurfaceVariant = veylColors.textSecondary,
            outline = veylColors.borderHairline,
            outlineVariant = veylColors.borderActive,
            error = VeylError,
            onError = Color.White
        )
    }
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
    val veylColors = colorScheme ?: if (isDarkMode) VeylDarkColorScheme else VeylLightColorScheme
    
    val m3Colors = buildM3ColorScheme(
        veylColors = veylColors,
        primary = veylColors.accentSignal,
        secondary = veylColors.textMono,
        tertiary = veylColors.accentCyan,
        background = veylColors.background,
        isDark = isDarkMode
    )

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

