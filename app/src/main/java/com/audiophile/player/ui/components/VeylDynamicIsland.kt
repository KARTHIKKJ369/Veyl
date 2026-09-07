package com.audiophile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
 * Handcrafted Audiophile Dynamic Island Capsule.
 *
 * Sits directly under the camera cutout.
 * Collapsed: Minimal pill with rotating vinyl album art, live bit-perfect format indicator,
 * and pulsating visualizer bars adhering to active theme accentSignal.
 * Expanded: Interactive audiophile card with high-res artwork, bit-perfect telemetry,
 * scrub timeline, and transport controls.
 */
@Composable
fun VeylDynamicIsland(
    controller: AudioEngineController,
    onOpenNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
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

    // Spring animations for morphing capsule pill <-> expanded card
    val islandWidth by animateDpAsState(
        targetValue = if (isExpanded) 348.dp else 200.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandWidth"
    )
    val islandHeight by animateDpAsState(
        targetValue = if (isExpanded) 136.dp else 36.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "islandHeight"
    )
    val islandCornerRadius by animateDpAsState(
        targetValue = if (isExpanded) 26.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioNoBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "islandCornerRadius"
    )

    // Smooth continuous vinyl rotation when playing
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

    // Dancing visualizer bar heights
    val bar1Height by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 450, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar1"
    )
    val bar2Height by infiniteTransition.animateFloat(
        initialValue = 13f,
        targetValue = 5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 360, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bar2"
    )
    val bar3Height by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 15f,
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
        // Dynamic Island Capsule
        Box(
            modifier = Modifier
                .width(islandWidth)
                .height(islandHeight)
                .clip(RoundedCornerShape(islandCornerRadius))
                .background(Color(0xFF000000))
                .border(
                    width = 1.dp,
                    color = if (isExpanded) colors.accentSignal.copy(alpha = 0.45f) else colors.borderHairline.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(islandCornerRadius)
                )
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isExpanded) {
                                onOpenNowPlaying()
                            } else {
                                isExpanded = true
                            }
                        },
                        onLongPress = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (!isExpanded) {
                                isExpanded = true
                            } else {
                                isExpanded = false
                            }
                        }
                    )
                }
        ) {
            if (isExpanded) {
                // Expanded Island Layout
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Row: Artwork + Track Info + Collapse Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Artwork Thumbnail
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceElevated)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onOpenNowPlaying()
                                }
                        ) {
                            AsyncAlbumArt(
                                uri = track?.uri ?: "",
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        // Title, Artist, and Format Pill
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = track?.title ?: "No track loaded",
                                style = VeylTypography.BodyMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = track?.artist ?: "Unknown Artist",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Bit-Perfect Direct • ${track?.formatName ?: "FLAC"} ${track?.bitDepth ?: 24}b",
                                style = VeylTypography.MonoSpec,
                                color = colors.accentSignal,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Collapse icon button
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isExpanded = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.ChevronUp,
                                contentDescription = "Collapse Island",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Middle: Sleek Progress Bar Line
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.surfaceElevated)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progressFraction.coerceIn(0f, 1f))
                                .fillMaxHeight()
                                .background(colors.accentSignal)
                        )
                    }

                    // Bottom Row: Transport Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TAP TO OPEN",
                            style = VeylTypography.MonoSpec,
                            color = colors.textMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.clickable {
                                onOpenNowPlaying()
                            }
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Previous
                            Icon(
                                imageVector = VeylIcons.SkipPrevious,
                                contentDescription = "Previous",
                                tint = colors.textPrimary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        controller.skipPrevious()
                                    }
                            )

                            // Play / Pause
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentSignal)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.togglePlayPause()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) VeylIcons.Pause else VeylIcons.Play,
                                    contentDescription = if (isPlaying) "Pause" else "Play",
                                    tint = colors.surfacePanel,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Next
                            Icon(
                                imageVector = VeylIcons.SkipNext,
                                contentDescription = "Next",
                                tint = colors.textPrimary,
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        controller.skipNext()
                                    }
                            )
                        }
                    }
                }
            } else {
                // Collapsed Pill Layout
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Rotating Vinyl Album Art
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated)
                            .then(if (isPlaying) Modifier.rotate(vinylRotation) else Modifier)
                    ) {
                        AsyncAlbumArt(
                            uri = track?.uri ?: "",
                            modifier = Modifier.fillMaxSize(),
                            shape = CircleShape
                        )
                    }

                    // Center: Format Badge & Bit-Perfect Indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        modifier = Modifier.padding(horizontal = 4.dp)
                    ) {
                        val badgeText = when {
                            track?.formatName?.contains("DSD", true) == true -> "DSD"
                            (track?.sampleRate ?: 0u) >= 192000u -> "192k"
                            (track?.sampleRate ?: 0u) >= 96000u -> "96k"
                            else -> track?.formatName?.take(4) ?: "FLAC"
                        }
                        Text(
                            text = badgeText,
                            style = VeylTypography.MonoSpec,
                            color = colors.accentSignal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(colors.accentSignal)
                        )
                        val titleText = currentTrack?.title?.let { if (it.length > 10) it.take(10) + "…" else it } ?: "Veyl"
                        Text(
                            text = titleText,
                            style = VeylTypography.BodySmall,
                            color = colors.textPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                    }

                    // Right: Dancing Visualizer Bars (Theme Accent Signal)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier
                            .height(18.dp)
                            .padding(end = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(if (isPlaying) bar1Height.dp else 4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(colors.accentSignal)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(if (isPlaying) bar2Height.dp else 4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(colors.accentSignal)
                        )
                        Box(
                            modifier = Modifier
                                .width(2.5.dp)
                                .height(if (isPlaying) bar3Height.dp else 4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(colors.accentSignal)
                        )
                    }
                }
            }
        }
    }
}
