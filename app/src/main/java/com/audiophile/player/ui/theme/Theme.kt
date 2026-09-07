package com.audiophile.player.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val LocalSoundwaveColors = compositionLocalOf { DarkSoundwaveColors }

private val DarkColorScheme = darkColorScheme(
    primary = LimeAccent,
    onPrimary = DarkBgObsidian,
    primaryContainer = DarkSurfaceCard,
    onPrimaryContainer = LimeAccent,
    secondary = CyberCyan,
    onSecondary = DarkBgObsidian,
    secondaryContainer = DarkSurfaceCard,
    onSecondaryContainer = CyberCyan,
    background = DarkBgObsidian,
    onBackground = DarkTextPrimary,
    surface = DarkBgSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceCard,
    onSurfaceVariant = DarkTextSecondary,
    outline = GlassBorder,
    outlineVariant = BorderActive,
    error = DangerRed,
    onError = DarkBgObsidian
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF141312),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFECE7DE),
    onPrimaryContainer = Color(0xFF141312),
    secondary = LimeAccent,
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFECE7DE),
    onSecondaryContainer = LimeAccent,
    background = Color(0xFFF7F5F0),
    onBackground = Color(0xFF141312),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF141312),
    surfaceVariant = Color(0xFFECE7DE),
    onSurfaceVariant = Color(0xFF6B6763),
    outline = Color(0xFFE4DFD5),
    outlineVariant = Color(0xFFD0C9BD),
    error = DangerRed,
    onError = Color(0xFFFFFFFF)
)

@Composable
fun AudiophileTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val soundwaveColors = if (darkTheme) DarkSoundwaveColors else LightSoundwaveColors
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = soundwaveColors.background.toArgb()
            window.navigationBarColor = soundwaveColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
            WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = !darkTheme
        }
    }

    CompositionLocalProvider(LocalSoundwaveColors provides soundwaveColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AudiophileTypography,
            content = content
        )
    }
}
