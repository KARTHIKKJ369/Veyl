package com.audiophile.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import com.audiophile.player.ui.components.VeylListRow
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
import androidx.compose.foundation.gestures.detectDragGestures
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import com.audiophile.player.ui.components.VeylActionButton
import com.audiophile.player.ui.components.VeylPlayingBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.zIndex
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

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    val status by controller.status.collectAsState()
    val positionSec by controller.currentPositionSec.collectAsState()
    val queue by controller.queue.collectAsState()
    val isShuffle by controller.isShuffleEnabled.collectAsState()

    val currentTrack = status?.currentTrack
    val isPlaying = status?.state == PlaybackStateEnum.PLAYING
    val durationSec = (status?.durationSeconds ?: 1.0).coerceAtLeast(1.0)
    val progressFraction = (positionSec / durationSec).toFloat().let {
        if (it.isNaN() || it < 0f) 0f else it.coerceIn(0f, 1f)
    }

    val density = LocalDensity.current
    val itemHeightPx = with(density) { 68.dp.toPx() }

    var draggingItemKey by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableStateOf(0f) }

    // Build upcoming slice starting immediately after currentTrack so #1 in UP NEXT is always the actual next track
    val currentQueueIndex = remember(queue, currentTrack?.uri) {
        if (currentTrack == null) -1
        else queue.indexOfFirst { it.uri == currentTrack.uri || it.title.equals(currentTrack.title, ignoreCase = true) }
    }
    val upcomingItems = remember(queue, currentQueueIndex) {
        if (currentQueueIndex >= 0) {
            (currentQueueIndex + 1 until queue.size).map { idx -> idx to queue[idx] }
        } else {
            queue.mapIndexed { idx, t -> idx to t }
        }
    }

    // Calculate total remaining queue duration without O(N) recalculation on position ticks
    val restQueueSec = remember(upcomingItems) {
        upcomingItems.sumOf { it.second.durationSeconds }
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
            // 1. Header Bar: Clean borderless navigation & action controls matching Playlists/Library
            item {
                Spacer(modifier = Modifier.height(VeylSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        VeylActionButton(
                            icon = VeylIcons.ChevronLeft,
                            contentDescription = "Back to player",
                            onClick = onNavigateBack,
                            iconSizeDp = 22
                        )

                        Column {
                            Text(
                                text = "QUEUE",
                                style = VeylTypography.MonoBadge,
                                color = colors.accentSignal
                            )
                            Text(
                                text = "Now Playing & Queue",
                                style = VeylTypography.TitleLarge,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            )
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (queue.isNotEmpty()) {
                            VeylActionButton(
                                icon = VeylIcons.Trash,
                                contentDescription = "Clear queue",
                                accentColor = colors.textSecondary,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.clearQueue()
                                }
                            )
                        }

                        VeylActionButton(
                            icon = VeylIcons.Shuffle,
                            contentDescription = if (isShuffle) "Shuffle active" else "Shuffle disabled",
                            accentColor = if (isShuffle) colors.accentSignal else colors.textSecondary,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                controller.toggleShuffle()
                            }
                        )
                    }
                }
            }

            // 2. Clean Flat Queue Telemetry Line (No pill/bordered box clutter)
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${queue.size} tracks queued",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )

                        Text(
                            text = "$formattedRemainingTime remaining",
                            style = VeylTypography.MonoSpec,
                            color = colors.accentSignal,
                            fontSize = 11.5.sp
                        )
                    }
                    HorizontalDivider(
                        thickness = 0.5.dp,
                        color = colors.borderHairline.copy(alpha = 0.5f)
                    )
                }
            }

            // 3. Now Playing Card
            if (currentTrack != null) {
                item {
                    Text(
                        text = "NOW PLAYING",
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
                        text = "UP NEXT (${upcomingItems.size})",
                        style = VeylTypography.MonoBadge,
                        color = colors.textSecondary
                    )

                    Text(
                        text = "AUTO-ADVANCE ENABLED",
                        style = VeylTypography.MonoBadge,
                        color = colors.textMono
                    )
                }
            }

            // 5. Reorderable Queue Items
            if (upcomingItems.isEmpty()) {
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
                            text = "No upcoming tracks in queue",
                            style = VeylTypography.Body,
                            color = colors.textMuted
                        )
                    }
                }
            } else {
                itemsIndexed(
                    items = upcomingItems,
                    key = { _, pair -> "${pair.second.uri}_${System.identityHashCode(pair.second)}" }
                ) { displayIdx, (realQueueIndex, track) ->
                    val trackKey = "${track.uri}_${System.identityHashCode(track)}"
                    val isCurrent = false
                    val isDragging = draggingItemKey == trackKey

                    val dragHandleModifier = Modifier.pointerInput(trackKey) {
                        detectDragGestures(
                            onDragStart = {
                                draggingItemKey = trackKey
                                dragOffsetY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragOffsetY += dragAmount.y
                                val threshold = itemHeightPx * 0.55f
                                val curQ = controller.queue.value
                                var cur = curQ.indexOfFirst { "${it.uri}_${System.identityHashCode(it)}" == trackKey }
                                val minIdx = (currentQueueIndex + 1).coerceAtLeast(0)
                                if (cur != -1) {
                                    while (dragOffsetY > threshold && cur < curQ.size - 1) {
                                        controller.moveInQueue(cur, cur + 1)
                                        cur += 1
                                        dragOffsetY -= itemHeightPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    while (dragOffsetY < -threshold && cur > minIdx) {
                                        controller.moveInQueue(cur, cur - 1)
                                        cur -= 1
                                        dragOffsetY += itemHeightPx
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                }
                            },
                            onDragEnd = {
                                draggingItemKey = null
                                dragOffsetY = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            onDragCancel = {
                                draggingItemKey = null
                                dragOffsetY = 0f
                            }
                        )
                    }

                    QueueTrackRow(
                        index = displayIdx + 1,
                        track = track,
                        isCurrent = isCurrent,
                        isPlaying = false,
                        isDragging = isDragging,
                        dragOffsetY = if (isDragging) dragOffsetY else 0f,
                        dragHandleModifier = dragHandleModifier,
                        onPlay = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.playTrack(track)
                        },
                        onRemove = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.removeFromQueue(realQueueIndex)
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
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surfaceElevated.copy(alpha = 0.72f))
            .border(0.5.dp, colors.borderHairline, RoundedCornerShape(14.dp))
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
                        .clip(RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncAlbumArt(
                        uri = track.uri,
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.background.copy(alpha = 0.35f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) VeylIcons.Pause else VeylIcons.Play,
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(VeylSpacing.md))

                // Title + Artist + Clean Telemetry Text (no bordered/colored pill box)
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = formatBadge,
                            style = VeylTypography.MonoBadge,
                            color = colors.textMuted,
                            fontSize = 10.sp
                        )

                        VeylPlayingBars(
                            color = colors.accentSignal,
                            isAnimating = isPlaying
                        )
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
    isDragging: Boolean = false,
    dragOffsetY: Float = 0f,
    dragHandleModifier: Modifier = Modifier,
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

    VeylListRow(
        title = track.title,
        subtitle = "${track.artist ?: "Unknown Artist"} • $formatBadge",
        isPlaying = isDragging || isCurrent,
        isAnimating = isPlaying,
        trailingText = durationFormatted,
        onClick = onPlay,
        modifier = Modifier
            .zIndex(if (isDragging) 10f else 1f)
            .graphicsLayer {
                translationY = if (isDragging) dragOffsetY else 0f
                scaleX = if (isDragging) 1.02f else 1f
                scaleY = if (isDragging) 1.02f else 1f
                shadowElevation = if (isDragging) 24f else 0f
            }
            .semantics {
                contentDescription = "Queue item $index: ${track.title}, Artist: ${track.artist ?: "Unknown"}, $durationFormatted"
            },
        leadingContent = {
            Text(
                text = "%02d".format(index),
                style = VeylTypography.MonoSpec,
                color = if (isDragging || isCurrent) colors.accentSignal else colors.textMuted,
                modifier = Modifier.width(28.dp)
            )
        },
        trailingContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.Trash,
                        contentDescription = "Remove from queue",
                        tint = colors.textMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(VeylSpacing.RadiusSm))
                        .then(dragHandleModifier)
                        .padding(6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = VeylIcons.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = if (isDragging) colors.accentSignal else colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    )
}
