package com.audiophile.player.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.unit.IntOffset
import com.audiophile.player.ui.components.VeylTrackOptionsSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import android.widget.Toast
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.engine.RepeatMode
import com.audiophile.player.ui.components.SyncedLyricsSheet
import com.audiophile.player.ui.components.VeylNowPlayingLyricsCard
import com.audiophile.player.ui.components.VeylSlider
import com.audiophile.player.ui.components.VeylTrackOptionsSheet
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylSpacing
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.PlaybackStateEnum

@Composable
fun VeylNowPlayingScreen(
    controller: AudioEngineController,
    onNavigateBack: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToDsp: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    BackHandler {
        onNavigateBack()
    }

    val track by controller.currentTrack.collectAsState()
    val currentTrack = track
    val engineStatus by controller.status.collectAsState()
    val playbackState by controller.playbackState.collectAsState()
    val isPlaying = playbackState == PlaybackStateEnum.PLAYING
    val isShuffle by controller.isShuffleEnabled.collectAsState()
    val repeatMode by controller.repeatMode.collectAsState()
    val currentLyrics by controller.currentLyrics.collectAsState()
    val favoriteUris by controller.favoriteUris.collectAsState()
    val downloadingUris by controller.downloadingUris.collectAsState()
    val downloadedUris by controller.downloadedUris.collectAsState()
    val isDownloading = track?.let { controller.isTrackDownloading(it) } ?: false
    val isDownloaded = track?.let { controller.isTrackDownloaded(it) } ?: false
    val isFav = remember(track, favoriteUris) { controller.isTrackFavorite(track, favoriteUris) }
    val isDsd = track?.formatName?.contains("DSD", ignoreCase = true) == true
    val isHiRes = (track?.sampleRate ?: 0u) >= 96000u || (track?.bitDepth ?: 0u) >= 24u
    var isLyricsMode by remember { mutableStateOf(false) }
    var showLyricsSheet by remember { mutableStateOf(false) }

    // Intercept back button when in lyrics or lyrics sheet mode to smoothly return
    BackHandler(enabled = showLyricsSheet || isLyricsMode) {
        if (showLyricsSheet) {
            showLyricsSheet = false
        } else {
            isLyricsMode = false
        }
    }
    var showOptionsSheet by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(0, dragOffsetY.value.roundToInt().coerceAtLeast(0)) }
            .background(
                Brush.verticalGradient(
                    listOf(
                        colors.surfacePanel,
                        colors.background,
                        colors.background
                    )
                )
            )
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY.value > 150f) {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(2000f, tween(180))
                                onNavigateBack()
                            }
                        } else {
                            coroutineScope.launch {
                                dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                            }
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            dragOffsetY.animateTo(0f, spring(stiffness = Spring.StiffnessMediumLow))
                        }
                    },
                    onVerticalDrag = { change, dragAmount ->
                        if (dragAmount > 0 || dragOffsetY.value > 0) {
                            change.consume()
                            coroutineScope.launch {
                                dragOffsetY.snapTo((dragOffsetY.value + dragAmount).coerceAtLeast(0f))
                            }
                        }
                    }
                )
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 6.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Minimal Header Bar matching VeylQueueScreen / Library aesthetic
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.ChevronDown,
                        contentDescription = "Minimize player",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val fmt = engineStatus?.codecName?.takeIf { it.isNotBlank() } ?: track?.formatName ?: "PCM"
                    val srK = ((engineStatus?.sampleRate ?: track?.sampleRate ?: 44100u).toFloat() / 1000f)
                    val srText = if (srK % 1f == 0f) "${srK.toInt()} kHz" else "${"%.1f".format(srK)} kHz"
                    val bitText = (engineStatus?.bitDepth ?: track?.bitDepth)?.let { " / ${it}-bit" } ?: ""
                    Text(
                        text = if (engineStatus?.isBitPerfect == true) "BIT-PERFECT" else "NOW PLAYING",
                        style = VeylTypography.MonoBadge,
                        color = if (engineStatus?.isBitPerfect == true) colors.accentSignal else colors.textMuted,
                        fontSize = 10.sp
                    )
                    Text(
                        text = "$fmt • $srText$bitText",
                        style = VeylTypography.MonoSpec,
                        color = colors.textSecondary,
                        fontSize = 11.sp
                    )
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToDsp()
                    },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.Equalizer,
                        contentDescription = "Equalizer & DSP",
                        tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Center: Album Cover / Synced Lyrics Card with clean hairline frame
            AnimatedContent(
                targetState = isLyricsMode,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(220, delayMillis = 40)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(animationSpec = tween(180)) + scaleOut(targetScale = 0.94f, animationSpec = tween(180)))
                },
                label = "AlbumArtLyricsSwitch"
            ) { inLyricsMode ->
                if (inLyricsMode) {
                    VeylNowPlayingLyricsCard(
                        controller = controller,
                        onSwitchToCover = { isLyricsMode = false },
                        onExpandFullscreen = { showLyricsSheet = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surfaceElevated)
                            .border(
                                width = 1.dp,
                                color = colors.borderHairline.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(20.dp)
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                isLyricsMode = true
                            }
                    ) {
                        AsyncAlbumArt(
                            uri = track?.uri ?: "",
                            hasArtwork = track?.hasArtwork,
                            shape = RoundedCornerShape(20.dp),
                            modifier = Modifier.fillMaxSize(),
                            targetSizePx = 1024
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 3. Track Info Row with Heart Button and 3-Dots Options Menu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentTrack?.title ?: "No track selected",
                        style = VeylTypography.HeadlineLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = currentTrack?.artist?.let { art ->
                            currentTrack.album?.let { alb -> "$art • $alb" } ?: art
                        } ?: "Select a song to play",
                        style = VeylTypography.Body,
                        color = colors.textMuted,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Download Studio Track Button
                    IconButton(
                        onClick = {
                            track?.let { t ->
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isDownloaded) {
                                    Toast.makeText(context, "Already saved to Music/Veyl", Toast.LENGTH_SHORT).show()
                                } else if (!isDownloading) {
                                    Toast.makeText(context, "Downloading \"${t.title}\" to storage\u2026", Toast.LENGTH_SHORT).show()
                                    controller.downloadCurrentTrack(
                                        onCompleted = { file ->
                                            Toast.makeText(context, "Saved to Music/Veyl: ${t.title}", Toast.LENGTH_LONG).show()
                                        },
                                        onError = { err ->
                                            Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                }
                            }
                        }
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.accentSignal
                            )
                        } else {
                            Icon(
                                imageVector = if (isDownloaded) VeylIcons.Check else VeylIcons.Download,
                                contentDescription = if (isDownloaded) "Downloaded" else "Download Track",
                                tint = if (isDownloaded) colors.accentSignal else colors.textMuted,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Favorite Heart Button
                    IconButton(
                        onClick = {
                            track?.let {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                controller.toggleFavorite(it.uri, it)
                            }
                        }
                    ) {
                        Icon(
                            imageVector = if (isFav) VeylIcons.FavoriteFilled else VeylIcons.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (isFav) Color(0xFFFF6584) else colors.textMuted,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 3-Dots Options Menu (Opens VeylTrackOptionsSheet)
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showOptionsSheet = true
                        }
                    ) {
                        Icon(
                            imageVector = VeylIcons.MoreVert,
                            contentDescription = "More Options",
                            tint = colors.textMuted,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // 4. Progress Scrubber with Chartreuse Signal & Timestamps (Isolated from parent recomposition)
            PlaybackProgressSection(
                controller = controller,
                trackDuration = track?.durationSeconds ?: 1.0,
                modifier = Modifier.fillMaxWidth()
            )

            // 5. Main Playback Controls: Clean borderless Previous/Next + Signal Circle Play/Pause
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.skipPrevious()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.Previous,
                        contentDescription = "Previous",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = colors.accentSignal,
                    modifier = Modifier.size(68.dp)
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.togglePlayPause()
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (isPlaying) VeylIcons.Pause else VeylIcons.Play,
                            contentDescription = if (isPlaying) "Pause" else "Play",
                            tint = colors.background,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.skipNext()
                    },
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.Next,
                        contentDescription = "Next",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // 6. Clean Borderless Bottom Action Row (matching Veyl's flat minimal design)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    controller.toggleRepeat()
                }) {
                    Icon(
                        imageVector = when (repeatMode) {
                            RepeatMode.ONE -> VeylIcons.RepeatOne
                            else -> VeylIcons.Repeat
                        },
                        contentDescription = "Repeat",
                        tint = if (repeatMode != RepeatMode.OFF) colors.accentSignal else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    controller.toggleShuffle()
                }) {
                    Icon(
                        imageVector = VeylIcons.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) colors.accentSignal else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onNavigateToQueue()
                }) {
                    Icon(
                        imageVector = VeylIcons.QueueList,
                        contentDescription = "Queue",
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    isLyricsMode = !isLyricsMode
                }) {
                    Icon(
                        imageVector = VeylIcons.Lyrics,
                        contentDescription = "Lyrics",
                        tint = if (isLyricsMode) colors.accentSignal else colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Fullscreen Immersive Synced Lyrics Sheet
        if (showLyricsSheet) {
            SyncedLyricsSheet(
                controller = controller,
                onDismiss = { showLyricsSheet = false }
            )
        }

        // 3-Dots Track Options Sheet
        if (showOptionsSheet && track != null) {
            VeylTrackOptionsSheet(
                track = track,
                controller = controller,
                onDismiss = { showOptionsSheet = false },
                onOpenEqualizer = onNavigateToDsp
            )
        }
    }
}

@Composable
private fun PlaybackProgressSection(
    controller: AudioEngineController,
    trackDuration: Double,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val status by controller.status.collectAsState()
    val positionSec by controller.currentPositionSec.collectAsState()

    val safeDuration = if (status?.durationSeconds == null || status?.durationSeconds!!.isNaN() || status?.durationSeconds!! <= 0.0) {
        if (!trackDuration.isNaN() && trackDuration > 0.0) trackDuration else 1.0
    } else status!!.durationSeconds

    val safePosition = if (positionSec.isNaN() || positionSec < 0.0) {
        0.0
    } else positionSec.coerceIn(0.0, safeDuration)

    // Decouple dragging fraction from audio seek to prevent 60-120Hz seek thrashing
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val currentFraction = if (isScrubbing) scrubFraction else {
        (safePosition / safeDuration).toFloat().coerceIn(0f, 1f)
    }

    val displayPosition = if (isScrubbing) (scrubFraction * safeDuration) else safePosition

    Column(modifier = modifier.fillMaxWidth()) {
        VeylSlider(
            value = currentFraction,
            onValueChange = { frac ->
                isScrubbing = true
                scrubFraction = frac
            },
            onValueChangeFinished = {
                val targetSec = (scrubFraction * safeDuration).coerceIn(0.0, safeDuration)
                controller.seekTo(targetSec)
                isScrubbing = false
            },
            activeColor = colors.accentSignal,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatTime(displayPosition),
                style = VeylTypography.MonoSpec,
                color = if (isScrubbing) colors.accentSignal else colors.textMuted,
                fontSize = 12.sp,
                fontWeight = if (isScrubbing) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = formatTime(safeDuration),
                style = VeylTypography.MonoSpec,
                color = colors.textMuted,
                fontSize = 12.sp
            )
        }
    }
}

private fun formatTime(seconds: Double): String {
    if (seconds.isNaN() || seconds < 0.0) return "0:00"
    val totalSec = seconds.toInt().coerceAtLeast(0)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%d:%02d".format(mins, secs)
}
