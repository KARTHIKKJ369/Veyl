package com.audiophile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.engine.LyricLine
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import uniffi.audiophile_core.TrackInfo

/**
 * BOOMING MUSIC-GRADE IMMERSIVE SYNCHRONIZED LYRICS
 *
 * Key Highlights:
 * 1. Word-by-word progressive illumination for enhanced LRC & TTML.
 * 2. Apple Music-style dynamic blurred album art ambient mesh background.
 * 3. Smart user-drag detection: pauses auto-scroll with floating "Resume Sync" pill.
 * 4. Microsecond precision sync offset controls (+/- 50ms, 100ms) with per-track persistence.
 * 5. One-tap direct seeking on lyric line tap.
 * 6. LRCLIB reload & auto-fetch integration.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SyncedLyricsSheet(
    controller: AudioEngineController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val track by controller.currentTrack.collectAsState()
    val lyrics by controller.currentLyrics.collectAsState()
    val status by controller.status.collectAsState()
    val offsetMs by controller.lyricOffsetMs.collectAsState()

    val currentTrack = track
    val lines = lyrics?.lines ?: emptyList()
    val hasWordTiming = lyrics?.hasWordTiming == true

    val positionSec = status?.positionSeconds ?: 0.0
    val activeIndex = remember(lyrics, positionSec, offsetMs) {
        lyrics?.findActiveIndex(positionSec, offsetMs) ?: -1
    }

    val listState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }
    var showOffsetPanel by remember { mutableStateOf(false) }
    var isSearchingOnline by remember { mutableStateOf(false) }

    // Detect user manual scroll to prevent fighting the user
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) {
            isUserScrolling = true
        } else if (isUserScrolling) {
            // Give the user 4 seconds of idle time before resuming auto-scroll
            delay(4000)
            isUserScrolling = false
        }
    }

    // Auto-scroll to current lyric line when active index changes and user isn't scrolling
    LaunchedEffect(activeIndex, isUserScrolling) {
        if (!isUserScrolling && activeIndex in lines.indices) {
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val scrollOffset = if (viewportHeight > 0) -(viewportHeight / 3) else 0
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = scrollOffset
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 1. Dynamic Ambient Blurred Album Art Background
        if (currentTrack != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = 0.35f }
                    .blur(64.dp)
            ) {
                AsyncAlbumArt(
                    uri = currentTrack.uri,
                    modifier = Modifier.fillMaxSize(),
                    targetSizePx = 256
                )
            }
        }

        // Dark Vignette Gradient Scrim for supreme text readability
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            colors.background.copy(alpha = 0.85f),
                            colors.background.copy(alpha = 0.70f),
                            colors.background.copy(alpha = 0.95f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 2. Top Navigation Bar: Drag Handle & Meta Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            ) {
                // Top Dismiss Chevron & Action Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onDismiss()
                        },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated.copy(alpha = 0.8f))
                    ) {
                        Icon(
                            imageVector = VeylIcons.ChevronDown,
                            contentDescription = "Dismiss lyrics",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Source & Format Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val sourceLabel = when {
                            hasWordTiming -> "WORD SYNC"
                            lyrics?.source == "lrclib" || lyrics?.source == "lrclib_search" -> "LRCLIB"
                            lyrics?.source == "file" -> "LOCAL LRC"
                            lyrics?.source == "embedded" || lyrics?.source == "id3" -> "ID3 TAG"
                            else -> "SYNCED"
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.accentSignal.copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentSignal.copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = sourceLabel,
                                style = VeylTypography.MonoSpec,
                                color = colors.accentSignal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }

                        // Offset Calibration Toggle Button
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (offsetMs != 0L) colors.accentSignal else colors.surfaceElevated.copy(alpha = 0.8f),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showOffsetPanel = !showOffsetPanel
                            }
                        ) {
                            Text(
                                text = if (offsetMs == 0L) "Sync" else "%+d ms".format(offsetMs),
                                style = VeylTypography.MonoSpec,
                                color = if (offsetMs != 0L) colors.background else colors.textPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Track Title & Artist
                Text(
                    text = currentTrack?.title ?: "Unknown Track",
                    style = VeylTypography.HeadlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = currentTrack?.artist ?: "Unknown Artist",
                    style = VeylTypography.BodySmall,
                    color = colors.textMuted,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Expandable Sync Offset Fine-Tuning Bar
                AnimatedVisibility(
                    visible = showOffsetPanel,
                    enter = slideInVertically() + fadeIn(),
                    exit = slideOutVertically() + fadeOut()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceElevated.copy(alpha = 0.9f))
                            .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Timing Calibration",
                                style = VeylTypography.MonoBadge,
                                color = colors.accentSignal,
                                fontSize = 10.sp
                            )
                            Text(
                                text = if (offsetMs == 0L) "Synchronized" else if (offsetMs > 0) "Delayed by +${offsetMs}ms" else "Advanced by ${offsetMs}ms",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OffsetPill(label = "-100ms", onClick = { controller.adjustLyricOffset(-100L) })
                            OffsetPill(label = "-50ms", onClick = { controller.adjustLyricOffset(-50L) })
                            OffsetPill(label = "Reset", onClick = { controller.setLyricOffset(0L) })
                            OffsetPill(label = "+50ms", onClick = { controller.adjustLyricOffset(50L) })
                            OffsetPill(label = "+100ms", onClick = { controller.adjustLyricOffset(100L) })
                        }
                    }
                }
            }

            // 3. Lyrics Main Content or Empty State
            if (lines.isNotEmpty()) {
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(top = 80.dp, bottom = 140.dp, start = 20.dp, end = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = lines,
                            key = { idx, line -> "${line.timestampMs}_$idx" }
                        ) { index, line ->
                            val isCurrent = index == activeIndex
                            val currentPosMs = ((positionSec * 1000).toLong() + offsetMs).coerceAtLeast(0L)

                            LyricLineRow(
                                line = line,
                                isCurrent = isCurrent,
                                currentPosMs = currentPosMs,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val seekTargetSec = (line.timestampMs.toDouble() / 1000.0).coerceAtLeast(0.0)
                                    controller.seekTo(seekTargetSec)
                                }
                            )
                        }
                    }

                    // Floating "Resume Sync" Pill when user has scrolled away
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isUserScrolling && activeIndex >= 0,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.accentSignal,
                            shadowElevation = 8.dp,
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isUserScrolling = false
                                    scope.launch {
                                        val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                                        val scrollOffset = if (viewportHeight > 0) -(viewportHeight / 3) else 0
                                        listState.animateScrollToItem(activeIndex, scrollOffset)
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.Play,
                                    contentDescription = null,
                                    tint = colors.background,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Jump to playing",
                                    style = VeylTypography.TitleMedium,
                                    color = colors.background,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            } else {
                // Empty State with One-Tap LRCLIB Online Search
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.surfaceElevated,
                            modifier = Modifier.size(68.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = VeylIcons.Lyrics,
                                    contentDescription = null,
                                    tint = colors.accentSignal,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Text(
                            text = "No Synchronized Lyrics Found",
                            style = VeylTypography.HeadlineMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Add a .lrc or .ttml file to the song folder, or search the open LRCLIB database automatically.",
                            style = VeylTypography.Body,
                            color = colors.textMuted,
                            textAlign = TextAlign.Center,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (!isSearchingOnline) {
                                    isSearchingOnline = true
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.reloadLyrics(allowOnline = true)
                                    scope.launch {
                                        delay(2500)
                                        isSearchingOnline = false
                                    }
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal),
                            enabled = !isSearchingOnline
                        ) {
                            if (isSearchingOnline) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.background,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Searching LRCLIB...",
                                    color = colors.background,
                                    style = VeylTypography.TitleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = VeylIcons.Search,
                                    contentDescription = null,
                                    tint = colors.background,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Search Online Lyrics",
                                    color = colors.background,
                                    style = VeylTypography.TitleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LyricLineRow(
    line: LyricLine,
    isCurrent: Boolean,
    currentPosMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.05f else 0.97f,
        animationSpec = tween(220),
        label = "lyricScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (isCurrent) 1.0f else 0.32f,
        animationSpec = tween(220),
        label = "lyricAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 4.dp)
    ) {
        if (line.words.isNotEmpty() && isCurrent) {
            // Word-by-word progressive karaoke illumination (Booming Music headline feature)
            FlowRow(
                horizontalArrangement = Arrangement.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val activeWordIdx = line.findActiveWordIndex(currentPosMs)
                line.words.forEachIndexed { wordIdx, word ->
                    val isPastWord = wordIdx < activeWordIdx
                    val isCurrentWord = wordIdx == activeWordIdx
                    val wordColor = when {
                        isCurrentWord -> colors.accentSignal
                        isPastWord -> colors.textPrimary
                        else -> colors.textMuted.copy(alpha = 0.5f)
                    }
                    val wordWeight = if (isCurrentWord) FontWeight.Black else if (isPastWord) FontWeight.Bold else FontWeight.Medium

                    Text(
                        text = word.text + " ",
                        style = VeylTypography.HeadlineMedium,
                        color = wordColor,
                        fontWeight = wordWeight,
                        fontSize = 23.sp,
                        lineHeight = 32.sp
                    )
                }
            }
        } else {
            // Standard line highlight
            Text(
                text = line.text,
                style = if (isCurrent) VeylTypography.HeadlineMedium else VeylTypography.TitleMedium,
                color = if (isCurrent) colors.accentSignal else colors.textPrimary,
                fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.Medium,
                fontSize = if (isCurrent) 23.sp else 17.sp,
                lineHeight = if (isCurrent) 32.sp else 24.sp
            )
        }

        // Translation display if present
        if (!line.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = line.translation,
                style = VeylTypography.BodySmall,
                color = colors.textMuted.copy(alpha = 0.7f),
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun OffsetPill(
    label: String,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = colors.surfacePanel,
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Text(
            text = label,
            style = VeylTypography.MonoSpec,
            color = colors.textPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
    }
}
