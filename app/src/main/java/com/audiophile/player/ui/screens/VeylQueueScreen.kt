package com.audiophile.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
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
import uniffi.audiophile_core.TrackInfo

/**
 * SCREEN 3: QUEUE (Enhanced with Mobile App UI/UX Design Standards)
 *
 * UX & UI Architecture:
 * - 60/30/10 Rule: 60% Void Black canvas, 30% Panel Charcoal flight path cards, 10% Phosphor Chartreuse signals.
 * - Dynamic Status Pill: Displays active queue count and remaining sequence duration.
 * - Non-destructive swipe/reordering: Instant tactile tactile control.
 */
@Composable
fun VeylQueueScreen(
    controller: AudioEngineController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    val status by controller.status.collectAsState()
    val queue by controller.queue.collectAsState()
    val isShuffle by controller.isShuffleEnabled.collectAsState()

    val currentTrack = status?.currentTrack
    val isPlaying = status?.state == PlaybackStateEnum.PLAYING
    val positionSec = status?.positionSeconds ?: 0.0
    val durationSec = (status?.durationSeconds ?: 1.0).coerceAtLeast(1.0)
    val progressFraction = (positionSec / durationSec).toFloat().let {
        if (it.isNaN() || it < 0f) 0f else it.coerceIn(0f, 1f)
    }

    // Calculate total remaining queue duration without O(N) recalculation on position ticks
    val restQueueSec = remember(queue, currentTrack?.uri) {
        queue.filter { it.uri != currentTrack?.uri }.sumOf { it.durationSeconds }
    }
    val totalRemainingSeconds = (durationSec - positionSec).coerceAtLeast(0.0) + restQueueSec

    val formattedRemainingTime = remember(totalRemainingSeconds.toInt()) {
        val totalSec = totalRemainingSeconds.toInt()
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        if (h > 0) "%dh %02dm".format(h, m) else "%dm %02ds".format(m, s)
    }

    fun formatDuration(seconds: Double): String {
        if (seconds.isNaN() || seconds < 0.0) return "0:00"
        val totalSec = seconds.toInt().coerceAtLeast(0)
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = VeylSpacing.md),
            verticalArrangement = Arrangement.spacedBy(VeylSpacing.md)
        ) {
            // 1. Header Bar: Back, Sequence Title, Quick Control Cluster
            item {
                Spacer(modifier = Modifier.height(VeylSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(VeylSpacing.sm)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateBack()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(colors.glassButtonBg)
                                .border(1.dp, colors.borderHairline, CircleShape)
                        ) {
                            Icon(
                                imageVector = VeylIcons.ArrowBack,
                                contentDescription = "Back to player",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "PLAYBACK FLIGHT PATH",
                                style = VeylTypography.MonoBadge,
                                color = colors.accentSignal
                            )
                            Text(
                                text = "Queue Sequence",
                                style = VeylTypography.DisplayMedium,
                                color = colors.textPrimary
                            )
                        }
                    }

                    // Action Icons (Shuffle Toggle)
                    Row(horizontalArrangement = Arrangement.spacedBy(VeylSpacing.xs)) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                controller.toggleShuffle()
                            },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(if (isShuffle) colors.surfaceElevated else colors.glassButtonBg)
                                .border(
                                    1.dp,
                                    if (isShuffle) colors.borderActive else colors.borderHairline,
                                    CircleShape
                                )
                        ) {
                            Icon(
                                imageVector = VeylIcons.Shuffle,
                                contentDescription = if (isShuffle) "Shuffle active" else "Shuffle disabled",
                                tint = if (isShuffle) colors.accentSignal else colors.textSecondary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // 2. Queue Telemetry Summary Banner (Peak-End Progress Affirmation)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(VeylSpacing.RadiusMd))
                        .background(colors.surfacePanel)
                        .border(1.dp, colors.borderHairline, RoundedCornerShape(VeylSpacing.RadiusMd))
                        .padding(horizontal = VeylSpacing.md, vertical = VeylSpacing.sm)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(VeylSpacing.xs)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) colors.accentSignal else colors.textMuted)
                            )
                            Text(
                                text = "${queue.size} TRACKS QUEUED",
                                style = VeylTypography.MonoBadge,
                                color = colors.textPrimary
                            )
                        }

                        Text(
                            text = "$formattedRemainingTime REMAINING",
                            style = VeylTypography.MonoSpec,
                            color = colors.accentSignal
                        )
                    }
                }
            }

            // 3. Now Playing Active Transducer Card
            if (currentTrack != null) {
                item {
                    Text(
                        text = "CURRENTLY TRANSDUCING",
                        style = VeylTypography.MonoBadge,
                        color = colors.accentSignal,
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    ActiveTrackQueueCard(
                        track = currentTrack,
                        isPlaying = isPlaying,
                        progressFraction = progressFraction,
                        positionSec = positionSec,
                        durationSec = durationSec,
                        onTogglePlayPause = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.togglePlayPause()
                        }
                    )
                }
            }

            // 4. "Up Next" Section Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "UPCOMING SEQUENCE (${queue.size})",
                        style = VeylTypography.MonoBadge,
                        color = colors.textSecondary
                    )

                    Text(
                        text = "GAPLESS HARDWARE BUFFER",
                        style = VeylTypography.MonoBadge,
                        color = colors.textMono
                    )
                }
            }

            // 5. Reorderable Queue Items
            if (queue.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(VeylSpacing.RadiusLg))
                            .background(colors.surfacePanel)
                            .padding(VeylSpacing.xxl),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tracks in playback sequence",
                            style = VeylTypography.Body,
                            color = colors.textMuted
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = queue,
                    key = { index, track -> "${track.uri}_$index" }
                ) { index, track ->
                    val isCurrent = currentTrack?.uri == track.uri

                    QueueTrackRow(
                        index = index + 1,
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = isCurrent && isPlaying,
                        onPlay = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.playTrack(track)
                        },
                        onRemove = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.removeFromQueue(index)
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(96.dp))
            }
        }
    }
}

@Composable
private fun ActiveTrackQueueCard(
    track: TrackInfo,
    isPlaying: Boolean,
    progressFraction: Float,
    positionSec: Double,
    durationSec: Double,
    onTogglePlayPause: () -> Unit
) {
    val colors = LocalVeylColors.current

    val formatBadge = remember(track) {
        val fmt = track.formatName.uppercase()
        val rateKhz = track.sampleRate.toDouble() / 1000.0
        val bits = track.bitDepth?.let { "${it}b" } ?: ""
        if (fmt.contains("DSD")) "NATIVE DSD" else "$fmt ${"%.1fk".format(rateKhz)}/$bits"
    }

    fun formatTime(seconds: Double): String {
        val totalSec = seconds.toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VeylSpacing.RadiusLg))
            .background(colors.surfaceElevated)
            .border(1.5.dp, colors.accentSignal, RoundedCornerShape(VeylSpacing.RadiusLg))
            .clickable(onClick = onTogglePlayPause)
            .padding(VeylSpacing.md)
            .semantics {
                contentDescription = "Active Track: ${track.title} by ${track.artist ?: "Unknown"}, ${formatTime(positionSec)} of ${formatTime(durationSec)}"
            }
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(VeylSpacing.sm)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Album Art
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(VeylSpacing.RadiusSm))
                        .border(1.dp, colors.borderHairline, RoundedCornerShape(VeylSpacing.RadiusSm)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncAlbumArt(
                        uri = track.uri,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(VeylSpacing.RadiusSm)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background.copy(alpha = 0.4f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) VeylIcons.Pause else VeylIcons.Play,
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(VeylSpacing.md))

                // Title + Artist + Badge
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = track.title,
                        style = VeylTypography.TitleMedium,
                        color = colors.accentSignal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = track.artist ?: "Unknown Artist",
                        style = VeylTypography.BodySmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentSignal.copy(alpha = 0.2f))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = formatBadge,
                                style = VeylTypography.MonoBadge,
                                color = colors.accentSignal,
                                fontSize = 9.sp
                            )
                        }

                        // Equalizer animation bars
                        if (isPlaying) {
                            QueueLivePulseEqualizer()
                        }
                    }
                }
            }

            // Real-time progress bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.surfacePill)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progressFraction)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(colors.accentSignal)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(positionSec),
                        style = VeylTypography.MonoSpec,
                        color = colors.textSecondary,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "-${formatTime((durationSec - positionSec).coerceAtLeast(0.0))}",
                        style = VeylTypography.MonoSpec,
                        color = colors.accentSignal,
                        fontSize = 10.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueTrackRow(
    index: Int,
    track: TrackInfo,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val colors = LocalVeylColors.current

    val formatBadge = remember(track) {
        val fmt = track.formatName.uppercase()
        val rateKhz = track.sampleRate.toDouble() / 1000.0
        val bits = track.bitDepth?.let { "${it}b" } ?: ""
        if (fmt.contains("DSD")) "DSD" else "$fmt ${"%.0fk".format(rateKhz)}"
    }

    val durationFormatted = remember(track.durationSeconds) {
        val totalSec = track.durationSeconds.toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        "%d:%02d".format(m, s)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(VeylSpacing.RadiusMd))
            .background(if (isCurrent) colors.surfaceElevated else colors.surfacePanel.copy(alpha = 0.6f))
            .border(
                1.dp,
                if (isCurrent) colors.borderActive else colors.borderHairline,
                RoundedCornerShape(VeylSpacing.RadiusMd)
            )
            .clickable(onClick = onPlay)
            .padding(horizontal = VeylSpacing.md, vertical = VeylSpacing.sm)
            .semantics {
                contentDescription = "Sequence item $index: ${track.title}, Artist: ${track.artist ?: "Unknown"}, $durationFormatted"
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Drag Handle
        Icon(
            imageVector = VeylIcons.DragHandle,
            contentDescription = "Drag to reorder",
            tint = colors.textMuted,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(VeylSpacing.xs))

        // Monospace index (e.g. 01, 02)
        Text(
            text = "%02d".format(index),
            style = VeylTypography.MonoSpec,
            color = if (isCurrent) colors.accentSignal else colors.textMuted,
            modifier = Modifier.width(24.dp)
        )

        Spacer(modifier = Modifier.width(VeylSpacing.xs))

        // Squircle Thumbnail Art
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(VeylSpacing.RadiusSm))
                .border(
                    1.dp,
                    if (isCurrent) colors.accentSignal else colors.borderHairline,
                    RoundedCornerShape(VeylSpacing.RadiusSm)
                )
        ) {
            AsyncAlbumArt(
                uri = track.uri,
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(VeylSpacing.RadiusSm)
            )
        }

        Spacer(modifier = Modifier.width(VeylSpacing.sm))

        // Title + Artist + Format
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = track.title,
                style = VeylTypography.TitleMedium,
                color = if (isCurrent) colors.accentSignal else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artist ?: "Unknown Artist",
                    style = VeylTypography.BodySmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                Text(
                    text = "• $formatBadge",
                    style = VeylTypography.MonoSpec,
                    color = colors.textMuted,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(VeylSpacing.xs))

        // Duration
        Text(
            text = durationFormatted,
            style = VeylTypography.MonoSpec,
            color = colors.textMuted
        )

        Spacer(modifier = Modifier.width(VeylSpacing.xs))

        // Remove from queue
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(44.dp)
        ) {
            Icon(
                imageVector = VeylIcons.Trash,
                contentDescription = "Remove from queue",
                tint = colors.textMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun QueueLivePulseEqualizer() {
    val colors = LocalVeylColors.current
    val infiniteTransition = rememberInfiniteTransition(label = "queue_eq")

    val h1 by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 12f,
        animationSpec = infiniteRepeatable(tween(400), RepeatMode.Reverse),
        label = "h1"
    )
    val h2 by infiniteTransition.animateFloat(
        initialValue = 12f,
        targetValue = 6f,
        animationSpec = infiniteRepeatable(tween(350), RepeatMode.Reverse),
        label = "h2"
    )
    val h3 by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(tween(500), RepeatMode.Reverse),
        label = "h3"
    )

    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        Box(modifier = Modifier.width(2.dp).height(h1.dp).background(colors.accentSignal, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.dp).height(h2.dp).background(colors.accentSignal, RoundedCornerShape(1.dp)))
        Box(modifier = Modifier.width(2.dp).height(h3.dp).background(colors.accentSignal, RoundedCornerShape(1.dp)))
    }
}
