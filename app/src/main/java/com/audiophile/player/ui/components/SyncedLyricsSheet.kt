package com.audiophile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlin.math.roundToInt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.engine.LyricLine
import com.audiophile.player.engine.LyricsProvider
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylOutfitFont
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
    val lyricsProvider by controller.lyricsProvider.collectAsState()
    val isLoadingLyrics by controller.isLoadingLyrics.collectAsState()

    val currentTrack = track
    val curLyrics = lyrics
    val lines = curLyrics?.lines ?: emptyList()

    val positionMs by controller.currentPositionMs.collectAsState()
    val activeIndex = remember(curLyrics, positionMs, offsetMs) {
        if (curLyrics == null || curLyrics.lines.isEmpty()) -1
        else {
            val targetPos = (positionMs + offsetMs).coerceAtLeast(0L)
            curLyrics.findActiveIndex(targetPos.toDouble() / 1000.0, 0L)
        }
    }

    val listState = rememberLazyListState()
    var isUserScrolling by remember { mutableStateOf(false) }
    var showOffsetPanel by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }
    var isSearchingOnline by remember { mutableStateOf(false) }

    // Trigger "Jump to playing" ONLY on manual finger drag, never on programmatic auto-scroll
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect { interaction ->
            if (interaction is DragInteraction.Start) {
                isUserScrolling = true
            }
        }
    }

    // Auto-resume following after 4 seconds of manual scroll idle
    LaunchedEffect(listState.isScrollInProgress, isUserScrolling) {
        if (!listState.isScrollInProgress && isUserScrolling) {
            delay(4000)
            isUserScrolling = false
        }
    }

    // Smoothly auto-scroll to active lyric line when active index changes
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
                    hasArtwork = currentTrack.hasArtwork,
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
            // 2. Top Navigation Bar: Minimal & Compact Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VeylActionButton(
                        icon = VeylIcons.ChevronDown,
                        contentDescription = "Dismiss lyrics",
                        onClick = onDismiss,
                        iconSizeDp = 24
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box {
                            Text(
                                text = if (isLoadingLyrics) "Loading…" else lyricsProvider.label,
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        showProviderMenu = true
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                            DropdownMenu(
                                expanded = showProviderMenu,
                                onDismissRequest = { showProviderMenu = false },
                                containerColor = colors.surfacePanel
                            ) {
                                LyricsProvider.entries.forEach { provider ->
                                    val selected = provider == lyricsProvider
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = provider.label,
                                                color = if (selected) colors.accentSignal else colors.textPrimary,
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                                fontSize = 13.sp
                                            )
                                        },
                                        onClick = {
                                            showProviderMenu = false
                                            controller.setLyricsProvider(provider)
                                        }
                                    )
                                }
                            }
                        }

                        Text(
                            text = if (offsetMs == 0L) "Sync" else "%+d ms".format(offsetMs),
                            style = VeylTypography.BodySmall,
                            color = if (offsetMs != 0L || showOffsetPanel) colors.accentSignal else colors.textSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showOffsetPanel = !showOffsetPanel
                                }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }

                // Centered Track Title & Artist
                Text(
                    text = currentTrack?.title ?: "Unknown Track",
                    style = VeylTypography.HeadlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = currentTrack?.artist ?: "Unknown Artist",
                    style = VeylTypography.BodySmall,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
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
                        contentPadding = PaddingValues(top = 20.dp, bottom = 44.dp, start = 16.dp, end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(
                            items = lines,
                            key = { idx, line -> "${line.timestampMs}_$idx" }
                        ) { index, line ->
                            val isCurrent = index == activeIndex
                            val isUpcoming = activeIndex >= 0 && index == activeIndex + 1
                            val isPast = activeIndex >= 0 && index < activeIndex

                            LyricLineRow(
                                line = line,
                                isCurrent = isCurrent,
                                isUpcoming = isUpcoming,
                                isPast = isPast,
                                controller = controller,
                                offsetMs = offsetMs,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    isUserScrolling = false
                                    val seekTargetSec = (line.timestampMs.toDouble() / 1000.0).coerceAtLeast(0.0)
                                    controller.seekTo(seekTargetSec)
                                }
                            )
                        }
                    }

                    // Subtle top gradient edge fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .align(Alignment.TopCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        colors.background.copy(alpha = 0.90f),
                                        Color.Transparent
                                    )
                                )
                            )
                    )

                    // Subtle bottom gradient edge fade
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        colors.background.copy(alpha = 0.95f)
                                    )
                                )
                            )
                    )

                    // Floating transparent "Jump to playing" button shown ONLY on manual scroll
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isUserScrolling && activeIndex >= 0,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 24.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.42f))
                                .border(0.5.dp, colors.borderHairline.copy(alpha = 0.65f), CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    isUserScrolling = false
                                    scope.launch {
                                        val viewportHeight = listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
                                        val scrollOffset = if (viewportHeight > 0) -(viewportHeight / 3) else 0
                                        listState.animateScrollToItem(activeIndex, scrollOffset)
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 9.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.Play,
                                contentDescription = null,
                                tint = colors.accentSignal,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = "Jump to playing",
                                style = VeylTypography.BodySmall,
                                color = colors.textPrimary,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold
                            )
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

            // 4. Compact Playback Bar at the bottom of the Lyrics Sheet
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val curSec = (positionMs / 1000L).toInt().coerceAtLeast(0)
                val totSec = (currentTrack?.durationSeconds ?: 0.0).toInt().coerceAtLeast(0)
                val curText = "%d:%02d".format(curSec / 60, curSec % 60)
                val totText = "%d:%02d".format(totSec / 60, totSec % 60)

                Text(
                    text = curText,
                    style = VeylTypography.MonoSpec,
                    color = colors.textMuted,
                    fontSize = 11.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.skipPrevious()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Previous,
                            contentDescription = "Previous",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Surface(
                        shape = CircleShape,
                        color = colors.accentSignal,
                        modifier = Modifier
                            .size(48.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                controller.togglePlayPause()
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (status?.state == uniffi.audiophile_core.PlaybackStateEnum.PLAYING) VeylIcons.Pause else VeylIcons.Play,
                                contentDescription = "Play/Pause",
                                tint = Color.Black,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.skipNext()
                        },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Next,
                            contentDescription = "Next",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Text(
                    text = totText,
                    style = VeylTypography.MonoSpec,
                    color = colors.textMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun LyricLineRow(
    line: LyricLine,
    isCurrent: Boolean,
    isUpcoming: Boolean = false,
    isPast: Boolean = false,
    controller: AudioEngineController,
    offsetMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    ProgressiveLyricLineContent(
        line = line,
        isCurrent = isCurrent,
        isUpcoming = isUpcoming,
        isPast = isPast,
        controller = controller,
        offsetMs = offsetMs,
        activeFontSize = 30.sp,
        activeLineHeight = 38.sp,
        inactiveFontSize = 21.sp,
        inactiveLineHeight = 28.sp,
        onClick = onClick,
        modifier = modifier.padding(vertical = 4.dp, horizontal = 4.dp)
    )
}

/**
 * Shared centered lyric renderer used by both Expanded Lyrics and Now Playing Lyrics Card.
 * Uses true word-sync timestamps when present, and smooth synced line transitions otherwise.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ProgressiveLyricLineContent(
    line: LyricLine,
    isCurrent: Boolean,
    isUpcoming: Boolean,
    isPast: Boolean,
    controller: AudioEngineController,
    offsetMs: Long,
    activeFontSize: TextUnit,
    activeLineHeight: TextUnit,
    inactiveFontSize: TextUnit,
    inactiveLineHeight: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current

    val alpha by animateFloatAsState(
        targetValue = when {
            isCurrent -> 1.0f
            isUpcoming -> 0.56f
            isPast -> 0.24f
            else -> 0.36f
        },
        animationSpec = tween(durationMillis = 260),
        label = "lyricAlpha"
    )

    val scale by animateFloatAsState(
        targetValue = if (isCurrent) 1.03f else 0.97f,
        animationSpec = tween(durationMillis = 260),
        label = "lyricScale"
    )

    val lineColor by animateColorAsState(
        targetValue = if (isCurrent) colors.accentSignal else colors.textPrimary,
        animationSpec = tween(durationMillis = 260),
        label = "lyricLineColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                this.alpha = alpha
                this.scaleX = scale
                this.scaleY = scale
            }
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (isCurrent && line.words.isNotEmpty()) {
            // Fill smoothly word-by-word ONLY when true word-sync timestamps exist
            val posMs by controller.currentPositionMs.collectAsState()
            val calibratedPosMs = (posMs + offsetMs).coerceAtLeast(0L)
            val baseDim = Color.White.copy(alpha = 0.42f)

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                line.words.forEach { word ->
                    val span = (word.endMs - word.startMs).coerceAtLeast(80L)
                    val progress = ((calibratedPosMs - word.startMs).toFloat() / span.toFloat()).coerceIn(0f, 1f)
                    val wordStyle = when {
                        progress >= 0.99f -> TextStyle(
                            fontFamily = VeylOutfitFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = activeFontSize,
                            lineHeight = activeLineHeight,
                            letterSpacing = (-0.6).sp,
                            color = colors.accentSignal
                        )
                        progress <= 0.01f -> TextStyle(
                            fontFamily = VeylOutfitFont,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = activeFontSize,
                            lineHeight = activeLineHeight,
                            letterSpacing = (-0.6).sp,
                            color = baseDim
                        )
                        else -> {
                            val s1 = (progress - 0.06f).coerceIn(0.001f, 0.990f)
                            val s2 = (progress + 0.06f).coerceIn(s1 + 0.001f, 0.999f)
                            TextStyle(
                                fontFamily = VeylOutfitFont,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = activeFontSize,
                                lineHeight = activeLineHeight,
                                letterSpacing = (-0.6).sp,
                                brush = Brush.horizontalGradient(
                                    0.0f to colors.accentSignal,
                                    s1 to colors.accentSignal,
                                    s2 to baseDim,
                                    1.0f to baseDim
                                )
                            )
                        }
                    }
                    Text(
                        text = word.text,
                        style = wordStyle
                    )
                }
            }
        } else {
            // Clean smooth synced line display (no artificial word-slicing)
            Text(
                text = line.text,
                style = TextStyle(
                    fontFamily = VeylOutfitFont,
                    fontWeight = if (isCurrent) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = if (isCurrent) activeFontSize else inactiveFontSize,
                    lineHeight = if (isCurrent) activeLineHeight else inactiveLineHeight,
                    letterSpacing = if (isCurrent) (-0.6).sp else (-0.3).sp
                ),
                color = lineColor,
                textAlign = TextAlign.Center
            )
        }

        if (!line.translation.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = line.translation,
                style = VeylTypography.BodySmall,
                color = colors.textMuted.copy(alpha = 0.72f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center
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
