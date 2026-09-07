package com.audiophile.player.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import kotlinx.coroutines.delay
import uniffi.audiophile_core.TrackInfo

@Composable
fun VeylStackedArtworkDeck(
    tracks: List<TrackInfo>,
    modifier: Modifier = Modifier,
    size: Dp = 230.dp,
    autoCycleIntervalMs: Long = 4000L,
    accentColor: Color? = null,
    onClick: (() -> Unit)? = null
) {
    val colors = LocalVeylColors.current
    val effectiveAccent = accentColor ?: colors.accentSignal

    if (tracks.isEmpty()) {
        // Empty state deck card with heart
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
            modifier = modifier
                .size(size)
                .border(1.dp, colors.borderHairline, RoundedCornerShape(24.dp))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = VeylIcons.FavoriteFilled,
                    contentDescription = null,
                    tint = effectiveAccent.copy(alpha = 0.5f),
                    modifier = Modifier.size(size / 3)
                )
            }
        }
        return
    }

    var baseIndex by remember { mutableStateOf(0) }

    // Automatic smooth cycling over time if multiple tracks exist
    LaunchedEffect(tracks.size, autoCycleIntervalMs) {
        if (tracks.size > 1 && autoCycleIntervalMs > 0) {
            while (true) {
                delay(autoCycleIntervalMs)
                baseIndex = (baseIndex + 1) % tracks.size
            }
        }
    }

    val track0 = tracks.getOrNull(baseIndex % tracks.size)
    val track1 = tracks.getOrNull((baseIndex + 1) % tracks.size)
    val track2 = tracks.getOrNull((baseIndex + 2) % tracks.size)

    Box(
        modifier = modifier
            .size(size)
            .clickable(enabled = onClick != null) {
                baseIndex = (baseIndex + 1) % tracks.size
                onClick?.invoke()
            },
        contentAlignment = Alignment.Center
    ) {
        // Card 3: Deepest back layer (rotated left -8°, shifted left, scaled down)
        if (tracks.size >= 3 && track2 != null) {
            Card(
                shape = RoundedCornerShape(22.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                modifier = Modifier
                    .size(size * 0.86f)
                    .offset(x = (-20).dp, y = (-8).dp)
                    .rotate(-9f)
                    .clip(RoundedCornerShape(22.dp))
                    .border(1.dp, colors.borderHairline.copy(alpha = 0.3f), RoundedCornerShape(22.dp))
            ) {
                AsyncAlbumArt(
                    uri = track2.uri,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Card 2: Mid layer (rotated right +7°, shifted right, scaled mid)
        if (tracks.size >= 2 && track1 != null) {
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                modifier = Modifier
                    .size(size * 0.92f)
                    .offset(x = 16.dp, y = (-4).dp)
                    .rotate(7f)
                    .clip(RoundedCornerShape(24.dp))
                    .border(1.dp, colors.borderHairline.copy(alpha = 0.5f), RoundedCornerShape(24.dp))
            ) {
                AsyncAlbumArt(
                    uri = track1.uri,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Card 1: Front Hero Layer (0° rotation, elevated with ambient glow and dynamic accent border)
        if (track0 != null) {
            Card(
                shape = RoundedCornerShape(26.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                modifier = Modifier
                    .size(size)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(26.dp),
                        ambientColor = effectiveAccent.copy(alpha = 0.35f),
                        spotColor = effectiveAccent.copy(alpha = 0.5f)
                    )
                    .clip(RoundedCornerShape(26.dp))
                    .border(
                        1.5.dp,
                        Brush.linearGradient(
                            listOf(
                                effectiveAccent.copy(alpha = 0.8f),
                                colors.borderActive.copy(alpha = 0.4f)
                            )
                        ),
                        RoundedCornerShape(26.dp)
                    )
            ) {
                Crossfade(
                    targetState = track0.uri,
                    animationSpec = tween(500, easing = FastOutSlowInEasing),
                    label = "deck_crossfade"
                ) { uri ->
                    AsyncAlbumArt(
                        uri = uri,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}
