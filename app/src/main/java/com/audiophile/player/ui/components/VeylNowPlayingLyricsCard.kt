package com.audiophile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.roundToInt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.audiophile.player.engine.LyricsProvider
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

/**
 * Premium In-Place Synchronized Lyrics Card.
 * Replaces the album cover container in Now Playing with animated, interactive lyrics.
 *
 * Features:
 * - Dynamic Ambient blurred album art backdrop
 * - Smooth spring-animated auto-scroll to active line
 * - Depth of field: Active line illuminated & scaled (1.04x), inactive lines softly dimmed (0.35x)
 * - Progressive word-by-word synchronized lyrics lighting (when word timings exist)
 * - Tap-to-seek: Tap any line to immediately jump to that timestamp with haptic feedback
 * - Seamless top & bottom gradient edge dissolution
 * - Tap card background or toggle button to switch back to album cover
 */
@Composable
fun VeylNowPlayingLyricsCard(
    controller: AudioEngineController,
    onSwitchToCover: () -> Unit,
    onExpandFullscreen: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    val track by controller.currentTrack.collectAsState()
    val lyrics by controller.currentLyrics.collectAsState()
    val positionMs by controller.currentPositionMs.collectAsState()
    val offsetMs by controller.lyricOffsetMs.collectAsState()
    val lyricsProvider by controller.lyricsProvider.collectAsState()
    val isLoadingLyrics by controller.isLoadingLyrics.collectAsState()

    val lines = lyrics?.lines ?: emptyList()
    val activeIndex = remember(lyrics, positionMs, offsetMs) {
        if (lyrics == null || lyrics?.lines.isNullOrEmpty()) -1
        else lyrics?.findActiveIndex((positionMs + offsetMs).coerceAtLeast(0L).toDouble() / 1000.0, 0L) ?: -1
    }

    val listState = rememberLazyListState()
    var isSearchingOnline by remember { mutableStateOf(false) }
    var showProviderMenu by remember { mutableStateOf(false) }

    // Auto-scroll only when activeIndex actually advances to a new valid line
    var lastScrolledIndex by remember { mutableIntStateOf(-1) }
    LaunchedEffect(activeIndex) {
        if (activeIndex in lines.indices && activeIndex != lastScrolledIndex) {
            lastScrolledIndex = activeIndex
            val layoutInfo = listState.layoutInfo
            val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
            val scrollOffset = if (viewportHeight > 0) -(viewportHeight / 3) else 0
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = scrollOffset
            )
        }
    }

    Card(
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.surfacePanel)
        ) {
            // 1. Ambient Blurred Album Art Glow
            if (track != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 0.34f }
                        .blur(64.dp)
                ) {
                    AsyncAlbumArt(
                        uri = track?.uri ?: "",
                        hasArtwork = track?.hasArtwork,
                        modifier = Modifier.fillMaxSize(),
                        targetSizePx = 256
                    )
                }
            }

            // Dark Vignette Gradient Scrim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                colors.surfacePanel.copy(alpha = 0.92f),
                                colors.surfacePanel.copy(alpha = 0.72f),
                                colors.surfacePanel.copy(alpha = 0.94f)
                            )
                        )
                    )
            )

            // 2. Main Lyrics Content
            if (lines.isNotEmpty()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(top = 64.dp, bottom = 68.dp, start = 20.dp, end = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(
                        items = lines,
                        key = { idx, line -> "${line.timestampMs}_$idx" }
                    ) { index, line ->
                        val isCurrent = index == activeIndex
                        val isUpcoming = activeIndex >= 0 && index == activeIndex + 1
                        val isPast = activeIndex >= 0 && index < activeIndex

                        NowPlayingLyricLine(
                            line = line,
                            isCurrent = isCurrent,
                            isUpcoming = isUpcoming,
                            isPast = isPast,
                            controller = controller,
                            offsetMs = offsetMs,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val seekSec = (line.timestampMs.toDouble() / 1000.0).coerceAtLeast(0.0)
                                controller.seekTo(seekSec)
                            }
                        )
                    }
                }

                // Smooth Top Gradient Dissolve Edge (prevents text colliding with LYRICS / Cover bar)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .align(Alignment.TopCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    colors.surfacePanel.copy(alpha = 0.99f),
                                    colors.surfacePanel.copy(alpha = 0.85f),
                                    Color.Transparent
                                )
                            )
                        )
                )

                // Smooth Bottom Gradient Dissolve Edge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    colors.surfacePanel.copy(alpha = 0.72f),
                                    colors.surfacePanel.copy(alpha = 0.99f)
                                )
                            )
                        )
                )
            } else {
                // Empty / Searching State
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(colors.surfaceElevated),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = VeylIcons.Lyrics,
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = if (isSearchingOnline) "Searching LRCLIB..." else "No Synchronized Lyrics",
                        style = VeylTypography.HeadlineMedium,
                        color = colors.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Tap to search online or switch back to cover",
                        style = VeylTypography.BodySmall,
                        color = colors.textSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.accentSignal,
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isSearchingOnline = true
                                scope.launch {
                                    controller.reloadLyrics(allowOnline = true)
                                    delay(1500)
                                    isSearchingOnline = false
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.Search,
                                    contentDescription = "Search Online",
                                    tint = colors.background,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Search Online",
                                    style = VeylTypography.TitleMedium,
                                    color = colors.background,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = colors.surfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSwitchToCover()
                            }
                        ) {
                            Text(
                                text = "Cover Art",
                                style = VeylTypography.TitleMedium,
                                color = colors.textPrimary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Top Header Inside Card: Minimal icon-only controls (Provider, Expand, Cover)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showProviderMenu = true
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoadingLyrics) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 1.5.dp,
                                color = colors.accentSignal
                            )
                        } else {
                            Icon(
                                imageVector = VeylIcons.Lyrics,
                                contentDescription = "Lyrics provider: ${lyricsProvider.label}",
                                tint = colors.accentSignal.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
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
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showProviderMenu = false
                                    controller.setLyricsProvider(provider)
                                }
                            )
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (onExpandFullscreen != null) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onExpandFullscreen()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.ChevronUp,
                                contentDescription = "Expand lyrics",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSwitchToCover()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = VeylIcons.Disc,
                            contentDescription = "Show cover art",
                            tint = colors.textSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingLyricLine(
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
        activeFontSize = 24.sp,
        activeLineHeight = 31.sp,
        inactiveFontSize = 17.sp,
        inactiveLineHeight = 24.sp,
        onClick = onClick,
        modifier = modifier.padding(vertical = 5.dp, horizontal = 4.dp)
    )
}
