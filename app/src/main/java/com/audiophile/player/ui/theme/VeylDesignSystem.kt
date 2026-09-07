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
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    val veylColors = if (isDarkMode) VeylDarkColorScheme else VeylLightColorScheme
    val m3Colors = if (isDarkMode) VeylM3DarkColorScheme else VeylM3LightColorScheme

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
