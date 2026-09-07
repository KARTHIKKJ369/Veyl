package com.audiophile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylSpacing
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.PlaybackStateEnum

/**
 * VEYL STICKY MINI DOCK
 * High-contrast, tactile docked mini-player conforming to the Veyl Design System.
 * Recomposition and layout-isolated for zero-jank 120 FPS playback.
 */
@Composable
fun StickyMiniPlayer(
    controller: AudioEngineController,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val track by controller.currentTrack.collectAsState()
    val currentTrack = track
    val playbackState by controller.playbackState.collectAsState()
    val isVisible = currentTrack != null
    val isPlaying = playbackState == PlaybackStateEnum.PLAYING

    val formatBadge = remember(currentTrack) {
        if (currentTrack == null) ""
        else {
            var fmt = currentTrack.formatName.uppercase()
            if (fmt.startsWith("CODECTYPE") || fmt.contains("(")) {
                val uriLower = currentTrack.uri.lowercase()
                fmt = when {
                    uriLower.endsWith(".flac") -> "FLAC"
                    uriLower.endsWith(".mp3") -> "MP3"
                    uriLower.endsWith(".wav") -> "WAV"
                    uriLower.endsWith(".dsf") || uriLower.endsWith(".dff") -> "DSD"
                    uriLower.endsWith(".m4a") || uriLower.endsWith(".aac") -> "AAC"
                    uriLower.endsWith(".ogg") || uriLower.endsWith(".opus") -> "OPUS"
                    else -> "PCM"
                }
            }
            val rateKhz = currentTrack.sampleRate.toDouble() / 1000.0
            if (fmt.contains("DSD")) "DSD" else "$fmt ${"%.0fk".format(rateKhz)}"
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }),
        exit = slideOutVertically(targetOffsetY = { it }),
        modifier = modifier
    ) {
        if (currentTrack != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated)
                    .clickable(onClick = onClick)
                    .semantics {
                        contentDescription = "Mini player: ${currentTrack.title} by ${currentTrack.artist ?: "Unknown"}"
                    }
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top hairline separator
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(colors.borderHairline)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = VeylSpacing.md, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Squircle Album Art & Metadata
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(VeylSpacing.RadiusSm))
                                    .border(1.dp, colors.borderHairline, RoundedCornerShape(VeylSpacing.RadiusSm))
                            ) {
                                AsyncAlbumArt(
                                    uri = currentTrack.uri,
                                    modifier = Modifier.fillMaxSize(),
                                    shape = RoundedCornerShape(VeylSpacing.RadiusSm),
                                    targetSizePx = 128
                                )
                            }

                            Spacer(modifier = Modifier.width(VeylSpacing.sm))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = currentTrack.title,
                                    style = VeylTypography.TitleMedium,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = currentTrack.artist ?: "Unknown Artist",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (formatBadge.isNotEmpty()) {
                                        Text(
                                            text = "• $formatBadge",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.accentSignal,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(VeylSpacing.sm))

                        // Circular Play/Pause Action with Signal Chartreuse
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.accentSignal),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(
                                onClick = { controller.togglePlayPause() },
                                modifier = Modifier.size(40.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) VeylIcons.Pause else VeylIcons.Play,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = colors.background,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Zero-allocation drawBehind progress bar (isolated leaf composable)
                    MiniPlayerProgressBar(
                        controller = controller,
                        fallbackDuration = currentTrack.durationSeconds
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPlayerProgressBar(
    controller: AudioEngineController,
    fallbackDuration: Double,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val status by controller.status.collectAsState()
    val dur = status?.durationSeconds?.takeIf { !it.isNaN() && it > 0.0 }
        ?: fallbackDuration.takeIf { !it.isNaN() && it > 0.0 } ?: 1.0
    val pos = status?.positionSeconds?.takeIf { !it.isNaN() && it >= 0.0 } ?: 0.0
    val progress = (pos / dur).toFloat().coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(colors.surfacePill)
            .drawBehind {
                val fillWidth = size.width * progress
                if (fillWidth > 0f) {
                    drawRect(
                        color = colors.accentSignal,
                        topLeft = Offset.Zero,
                        size = Size(fillWidth, size.height)
                    )
                }
            }
    )
}
