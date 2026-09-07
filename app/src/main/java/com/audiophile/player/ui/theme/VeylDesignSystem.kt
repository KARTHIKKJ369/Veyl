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
// 1. Color Tokens (Material 3 Expressive Dark & Dynamic Indigo/Periwinkle Palette)
// -------------------------------------------------------------------------
val VeylBackgroundDark = Color(0xFF121318)
val VeylSurfaceDark = Color(0xFF121318)
val VeylSurfaceContainerLowest = Color(0xFF0D0E13)
val VeylSurfaceContainerLow = Color(0xFF1A1B21)
val VeylSurfaceContainer = Color(0xFF1E1F25)
val VeylSurfaceContainerHigh = Color(0xFF292A2F)
val VeylSurfaceContainerHighest = Color(0xFF34343A)

val VeylPrimary = Color(0xFFB6C4FF)
val VeylOnPrimary = Color(0xFF1D2D61)
val VeylPrimaryContainer = Color(0xFF354479)
val VeylOnPrimaryContainer = Color(0xFFDCE1FF)

val VeylSecondary = Color(0xFFC2C5DD)
val VeylSecondaryContainer = Color(0xFF424659)
val VeylOnSecondaryContainer = Color(0xFFDEE1F9)

val VeylTertiary = Color(0xFFE3BADA)
val VeylTertiaryContainer = Color(0xFF5B3D57)

val VeylFavorite = Color(0xFFFFB4AB)
val VeylError = Color(0xFFBA1A1A)

val VeylTextPrimary = Color(0xFFE3E1E9)
val VeylTextSecondary = Color(0xFFC6C5D0)
val VeylTextMuted = Color(0xFF90909A)

val VeylBorderHairline = Color(0x1FFFFFFF)
val VeylBorderActive = Color(0x66B6C4FF)
val VeylGlassButtonBg = Color(0x33292A2F)

@Immutable
data class VeylColorScheme(
    val background: Color = VeylBackgroundDark,
    val surfacePanel: Color = VeylSurfaceContainer,
    val surfaceElevated: Color = VeylSurfaceContainerHigh,
    val surfacePill: Color = VeylSurfaceContainerHighest,
    val borderHairline: Color = VeylBorderHairline,
    val borderActive: Color = VeylBorderActive,
    val accentSignal: Color = VeylPrimary,
    val accentCyan: Color = Color(0xFF00E5FF),
    val accentPeak: Color = Color(0xFFFF453A),
    val accentFavorite: Color = VeylFavorite,
    val textPrimary: Color = VeylTextPrimary,
    val textSecondary: Color = VeylTextSecondary,
    val textMuted: Color = VeylTextMuted,
    val textMono: Color = VeylPrimary,
    val glassButtonBg: Color = VeylGlassButtonBg
)

val LocalVeylColors = compositionLocalOf { VeylColorScheme() }

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

    val TitleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        letterSpacing = (-0.1).sp,
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
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                window.statusBarColor = VeylBackgroundDark.toArgb()
                window.navigationBarColor = VeylBackgroundDark.toArgb()
                WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
                WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
            }
        }
    }

    CompositionLocalProvider(
        LocalVeylColors provides VeylColorScheme()
    ) {
        MaterialTheme(
            colorScheme = VeylM3DarkColorScheme,
            content = content
        )
    }
}
