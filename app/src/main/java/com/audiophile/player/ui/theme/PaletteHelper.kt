package com.audiophile.player.ui.theme

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import com.audiophile.player.engine.ArtworkCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DynamicThemeColors(
    val primaryAccent: Color = Color(0xFFB6C4FF),
    val secondaryAccent: Color = Color(0xFFEAA33E),
    val surfaceGlow: Color = Color(0x33B6C4FF),
    val backgroundTint: Color = Color(0xFF121318),
    val cardTint: Color = Color(0xFF1E1F25)
)

object PaletteHelper {

    suspend fun extractColors(trackUri: String?): DynamicThemeColors = withContext(Dispatchers.Default) {
        if (trackUri == null) return@withContext DynamicThemeColors()

        val bitmap = ArtworkCache.loadArtwork(trackUri, 128) ?: return@withContext DynamicThemeColors()

        try {
            val palette = Palette.from(bitmap).generate()

            val vibrant = palette.vibrantSwatch?.rgb?.let { Color(it) }
            val lightVibrant = palette.lightVibrantSwatch?.rgb?.let { Color(it) }
            val darkVibrant = palette.darkVibrantSwatch?.rgb?.let { Color(it) }
            val muted = palette.mutedSwatch?.rgb?.let { Color(it) }
            val dominant = palette.dominantSwatch?.rgb?.let { Color(it) }

            val primary = lightVibrant ?: vibrant ?: dominant ?: Color(0xFFB6C4FF)
            val secondary = vibrant ?: muted ?: Color(0xFFEAA33E)
            val bgDark = darkVibrant?.copy(alpha = 0.35f) ?: Color(0xFF121318)

            DynamicThemeColors(
                primaryAccent = primary,
                secondaryAccent = secondary,
                surfaceGlow = primary.copy(alpha = 0.22f),
                backgroundTint = bgDark,
                cardTint = primary.copy(alpha = 0.12f)
            )
        } catch (e: Exception) {
            DynamicThemeColors()
        }
    }
}

@Composable
fun rememberDynamicColors(trackUri: String?): DynamicThemeColors {
    var rawColors by remember { mutableStateOf(DynamicThemeColors()) }

    LaunchedEffect(trackUri) {
        rawColors = PaletteHelper.extractColors(trackUri)
    }

    val animatedPrimary by animateColorAsState(
        targetValue = rawColors.primaryAccent,
        animationSpec = tween(600),
        label = "primary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = rawColors.secondaryAccent,
        animationSpec = tween(600),
        label = "secondary"
    )
    val animatedGlow by animateColorAsState(
        targetValue = rawColors.surfaceGlow,
        animationSpec = tween(600),
        label = "glow"
    )
    val animatedBg by animateColorAsState(
        targetValue = rawColors.backgroundTint,
        animationSpec = tween(600),
        label = "bg"
    )

    return DynamicThemeColors(
        primaryAccent = animatedPrimary,
        secondaryAccent = animatedSecondary,
        surfaceGlow = animatedGlow,
        backgroundTint = animatedBg,
        cardTint = animatedPrimary.copy(alpha = 0.12f)
    )
}
