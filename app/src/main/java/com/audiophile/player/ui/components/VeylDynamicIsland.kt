package com.audiophile.player.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.PlaybackStateEnum

/**
 * Audiophile Dynamic Island Capsule (Matching HyperOS / iOS native island reference).
 *
 * Collapsed: Pure black pill centered at camera cutout. Left: Album art. Right: 3 orange wave bars.
 * Expanded: Native card with scrubber, timestamps, track info, and playback controls.
 */
@Composable
fun VeylDynamicIsland(
    controller: AudioEngineController,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    onExpandChanged: (Boolean) -> Unit = {}
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    val track by controller.currentTrack.collectAsState()
    val currentTrack = track
    val playbackState by controller.playbackState.collectAsState()
    val isPlaying = playbackState == PlaybackStateEnum.PLAYING
    val positionSec by controller.currentPositionSec.collectAsState()
    val dur = currentTrack?.durationSeconds?.takeIf { !it.isNaN() && it > 0.0 } ?: 1.0
    val progressFraction = (positionSec / dur).toFloat().coerceIn(0f, 1f)

    var isExpanded by remember { mutableStateOf(false) }

    val islandWidth by animateDpAsState(
        targetValue = if (isExpanded) 344.dp else 124.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandWidth"
    )
    val islandHeight by animateDpAsState(
        targetValue = if (isExpanded) 144.dp else 34.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandHeight"
    )
    val islandCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 26.dp else 17.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "islandCornerRadius"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
    val vinylRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 13f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar3"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.TopCenter
    ) {
        Box(
            modifier = Modifier
                .width(islandWidth)
                .height(islandHeight)
                .clip(RoundedCornerShape(islandCornerRadius))
                .background(Color(0xFF000000))
                .border(
                    width = 0.5.dp,
                    color = Color(0x33FFFFFF),
                    shape = RoundedCornerShape(islandCornerRadius)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val nextState = !isExpanded
                            isExpanded = nextState
                            onExpandChanged(nextState)
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onOpenNowPlaying()
                        }
                    )
                }
        ) {
            if (isExpanded) {
                // Expanded Island Layout (Matches Screenshot 4 reference)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Row: Artwork + Track Info + Lossless Pill
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF222222))
                                .clickable {
                                    onOpenNowPlaying()
                                }
                        ) {
                            AsyncAlbumArt(
                                uri = track?.uri ?: "",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenNowPlaying() },
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = track?.title ?: "No track loaded",
                                style = VeylTypography.TitleMedium,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track?.artist ?: "Unknown Artist",
                                style = VeylTypography.BodySmall,
                                color = Color(0xFFAAAAAA),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Text(
                            text = track?.formatName ?: "FLAC",
                            style = VeylTypography.MonoBadge,
                            color = Color(0xFFFF9500),
                            fontSize = 9.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0x22FF9500))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Middle: Scrubber bar with timestamps (00:27 / 03:58)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0x44FFFFFF))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(Color.White)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatIslandDuration(positionSec),
                                style = VeylTypography.MonoSpec,
                                color = Color(0xFF888888),
                                fontSize = 9.sp
                            )
                            Text(
                                text = formatIslandDuration(dur),
                                style = VeylTypography.MonoSpec,
                                color = Color(0xFF888888),
                                fontSize = 9.sp
                            )
                        }
                    }

                    // Bottom Row: Transport Controls (⏮ ⏸/▶ ⏭)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(40.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = VeylIcons.SkipPrevious,
                                contentDescription = "Previous",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        controller.skipPrevious()
                                    }
                            )

                            Icon(
                                imageVector = if (isPlaying) VeylIcons.Pause else VeylIcons.Play,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.togglePlayPause()
                                    }
                            )

                            Icon(
                                imageVector = VeylIcons.SkipNext,
                                contentDescription = "Next",
                                tint = Color.White,
                                modifier = Modifier
                                    .size(24.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        controller.skipNext()
                                    }
                            )
                        }
                    }
                }
            } else {
                // Collapsed Pill Layout (Matches Screenshot 3 reference)
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Album Art circular / squircle thumbnail
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF222222))
                            .then(if (isPlaying) Modifier.rotate(vinylRotation) else Modifier)
                    ) {
                        AsyncAlbumArt(
                            uri = track?.uri ?: "",
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape
                        )
                    }

                    // Center: Camera Cutout Spacer
                    Spacer(modifier = Modifier.width(36.dp))

                    // Right: 3 Dancing Visualizer Bars in system orange
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.5.dp),
                        modifier = Modifier
                            .height(18.dp)
                            .padding(end = 4.dp)
                    ) {
                        val barColor = Color(0xFFFF9500)
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(if (isPlaying) bar1Height.dp else 4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(barColor)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(if (isPlaying) bar2Height.dp else 4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(barColor)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(if (isPlaying) bar3Height.dp else 4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(barColor)
                        )
                    }
                }
            }
        }
    }
}

private fun formatIslandDuration(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0) return "00:00"
    val totalSec = seconds.toInt()
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format("%02d:%02d", min, sec)
}
