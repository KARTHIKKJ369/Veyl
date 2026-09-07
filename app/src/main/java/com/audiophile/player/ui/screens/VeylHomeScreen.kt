package com.audiophile.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.VeylTrackOptionsSheet
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.TrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylHomeScreen(
    controller: AudioEngineController,
    onNavigateToSearch: () -> Unit,
    onNavigateToTopTracks: () -> Unit,
    onNavigateToLastAdded: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onPickFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    val tracks by controller.tracks.collectAsState()
    val topTracks by controller.topTracks.collectAsState()
    val lastAddedTracks by controller.lastAddedTracks.collectAsState()
    val playbackHistory by controller.playbackHistory.collectAsState()
    val connectedDac by controller.connectedDac.collectAsState()
    val bitPerfectEnabled by controller.bitPerfectEnabled.collectAsState()
    val currentEqPreset by controller.currentEqPreset.collectAsState()
    val isEqEnabled by controller.isEqEnabled.collectAsState()
    val status by controller.status.collectAsState()
    val currentTrack = status?.currentTrack

    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }

    // Preset DSP Curves from Stitch design
    val dspCurves = listOf(
        "Pure Direct" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Harman Target" to listOf(4.5f, 4.0f, 2.5f, 0.5f, 0.0f, 0.5f, 1.5f, 3.0f, 1.5f, 0.5f),
        "Acoustic Flat" to listOf(2.0f, 1.5f, 0.5f, -0.5f, 0.5f, 1.0f, 2.5f, 3.5f, 2.0f, 1.0f),
        "Sub-Bass +2.5dB" to listOf(6.0f, 4.5f, 2.5f, 0.5f, 0.0f, 0.0f, 0.5f, 1.0f, 1.5f, 1.5f),
        "Vocal Presence" to listOf(-2f, -1f, 0f, 2.5f, 5f, 6f, 4f, 2f, 0f, -1f)
    )

    // Dynamic list for "Mastered & Recent / Listen Now"
    val recentTracks = remember(playbackHistory, lastAddedTracks, tracks) {
        when {
            playbackHistory.isNotEmpty() -> playbackHistory.take(10)
            lastAddedTracks.isNotEmpty() -> lastAddedTracks.take(10)
            else -> tracks.take(10)
        }
    }

    // Dynamic list for "For You / Indexed Master Tracks"
    val forYouTracks = remember(topTracks, tracks) {
        when {
            topTracks.isNotEmpty() -> topTracks.take(8)
            else -> tracks.take(8)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Lossless Hub Header: App Title, Bit-Perfect Badge & Search Action
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "Resonate",
                        style = VeylTypography.TitleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                    Text(
                        text = "/",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                    Text(
                        text = "Lossless Hub",
                        style = VeylTypography.Body,
                        color = colors.textSecondary,
                        fontSize = 15.sp
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Bit-Perfect Indicator Pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfacePanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (bitPerfectEnabled) colors.accentCyan else colors.textMuted)
                            )
                            Text(
                                text = "BIT-PERFECT",
                                style = VeylTypography.MonoSpec,
                                color = if (bitPerfectEnabled) colors.accentCyan else colors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Search Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToSearch()
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Search,
                            contentDescription = "Search",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // 2. Audio Engine & Direct Sink Card (Stitch Resonate Hero Card)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // Title Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(colors.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = VeylIcons.Equalizer,
                                    contentDescription = null,
                                    tint = colors.accentCyan,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "Rust Audio Engine",
                                        style = VeylTypography.TitleMedium,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.surfaceElevated
                                    ) {
                                        Text(
                                            text = "v2.4",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.textSecondary,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = if (connectedDac != null) "ALSA Direct Sink • USB DAC Exclusive" else "AAudio MMAP Sink • Direct Output",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToNowPlaying()
                            },
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceElevated)
                        ) {
                            Icon(
                                imageVector = VeylIcons.Play,
                                contentDescription = "Now Playing",
                                tint = colors.accentSignal,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // 3 Metric Blocks (Bitstream, Catalog, Latency)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Bitstream
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceElevated)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "BITSTREAM",
                                    style = VeylTypography.MonoSpec,
                                    color = colors.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (bitPerfectEnabled) "Bit-Perfect" else "Processed",
                                    style = VeylTypography.BodyMedium,
                                    color = colors.accentCyan,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Catalog
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceElevated)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "CATALOG",
                                    style = VeylTypography.MonoSpec,
                                    color = colors.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${tracks.size} Master",
                                    style = VeylTypography.BodyMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Latency
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfaceElevated)
                                .padding(10.dp)
                        ) {
                            Column {
                                Text(
                                    text = "LATENCY",
                                    style = VeylTypography.MonoSpec,
                                    color = colors.textMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (connectedDac != null) "1.4 ms" else "1.8 ms",
                                    style = VeylTypography.BodyMedium,
                                    color = colors.accentSignal,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. DSP & Target Curves Quick Selector (Stitch Resonate Segmented Controls)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "DSP & Target Curves",
                        style = VeylTypography.TitleMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Custom EQ →",
                        style = VeylTypography.MonoSpec,
                        color = colors.accentSignal,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onNavigateToTopTracks()
                            }
                            .padding(vertical = 4.dp)
                    )
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(dspCurves) { (curveName, gains) ->
                        val isSelected = isEqEnabled && currentEqPreset == curveName
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = if (isSelected) colors.primaryContainer else colors.surfacePanel,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) colors.borderActive else colors.borderHairline
                            ),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (curveName == "Pure Direct") {
                                    controller.setEqEnabled(false)
                                    controller.setEqPreset("Pure Direct", gains)
                                } else {
                                    controller.setEqEnabled(true)
                                    controller.setEqPreset(curveName, gains)
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = VeylIcons.Check,
                                        contentDescription = null,
                                        tint = colors.surfacePanel,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                                Text(
                                    text = curveName,
                                    style = VeylTypography.BodySmall,
                                    color = if (isSelected) colors.surfacePanel else colors.textPrimary,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // 4. "Mastered & Recent" / "Listen Now" (Dynamic Horizontal Carousel)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Mastered & Recent",
                            style = VeylTypography.TitleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Direct DSD streams and Studio Masters",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }

                    // Shuffle All Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (tracks.isNotEmpty()) {
                                controller.playShuffled(tracks)
                            }
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(colors.surfacePanel)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Shuffle,
                            contentDescription = "Shuffle",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                if (recentTracks.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(recentTracks) { track ->
                            Card(
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                                modifier = Modifier
                                    .width(168.dp)
                                    .clickable {
                                        controller.playTrack(track)
                                    }
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    // Artwork with overlay format badge & play button
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(colors.surfaceElevated)
                                    ) {
                                        AsyncAlbumArt(
                                            uri = track.uri,
                                            modifier = Modifier.fillMaxSize()
                                        )

                                        // Format Badge (e.g. DSD 5.6M or FLAC 24/96)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = Color.Black.copy(alpha = 0.7f),
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .align(Alignment.TopStart)
                                        ) {
                                            val badgeText = when {
                                                track.formatName.contains("DSD", true) -> "DSD %.1fM".format(track.sampleRate.toDouble() / 1000000.0)
                                                track.sampleRate >= 192000u -> "FLAC 192/${track.bitDepth}"
                                                track.sampleRate >= 96000u -> "FLAC 96/${track.bitDepth}"
                                                else -> track.formatName.take(8)
                                            }
                                            Text(
                                                text = badgeText,
                                                style = VeylTypography.MonoSpec,
                                                color = colors.accentCyan,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }

                                        // Play FAB
                                        Box(
                                            modifier = Modifier
                                                .padding(6.dp)
                                                .size(34.dp)
                                                .align(Alignment.BottomEnd)
                                                .clip(CircleShape)
                                                .background(colors.accentSignal)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    controller.playTrack(track)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = VeylIcons.Play,
                                                contentDescription = "Play",
                                                tint = colors.surfacePanel,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    // Track Info
                                    Column {
                                        Text(
                                            text = track.title,
                                            style = VeylTypography.BodyMedium,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = track.artist ?: "Unknown Artist",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textMuted,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "${track.bitDepth}-bit / ${track.sampleRate / 1000u}kHz",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.textSecondary,
                                            fontSize = 10.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfacePanel),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Import tracks or select a music folder to start listening",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // 5. "Indexed Lossless Master Tracks" / "For You" (Dynamic List of Real Master Recordings)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Indexed Lossless Master Tracks",
                            style = VeylTypography.TitleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Storage: Internal NVMe / Lossless_Direct",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }

                    Text(
                        text = "See All (${tracks.size})",
                        style = VeylTypography.MonoSpec,
                        color = colors.accentCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onNavigateToSearch() }
                    )
                }

                // Dynamic Track Rows
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    forYouTracks.forEach { track ->
                        val isCurrent = currentTrack?.uri == track.uri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isCurrent) colors.surfaceElevated else colors.surfacePanel)
                                .clickable {
                                    controller.playTrack(track)
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // 48dp Squircle Art
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceElevated)
                                ) {
                                    AsyncAlbumArt(
                                        uri = track.uri,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    if (isCurrent) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.Black.copy(alpha = 0.45f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = VeylIcons.Equalizer,
                                                contentDescription = null,
                                                tint = colors.accentSignal,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }

                                // Title & Format Specs
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        style = VeylTypography.BodyMedium,
                                        color = if (isCurrent) colors.accentSignal else colors.textPrimary,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = track.artist ?: "Unknown Artist",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = "${track.formatName} ${track.bitDepth}-bit/${track.sampleRate / 1000u}kHz",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.accentCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // Row Action Buttons
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        selectedTrackForOptions = track
                                    },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.MoreVert,
                                        contentDescription = "Options",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6. Connected Hardware Direct DAC Status Card (Stitch Resonate Footer)
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(colors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.StorageLocal,
                                contentDescription = null,
                                tint = colors.accentCyan,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = connectedDac?.name ?: "AAudio Direct Hardware Sink",
                                style = VeylTypography.BodyMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Hardware Volume Direct • 384kHz / DSD Native",
                                style = VeylTypography.MonoSpec,
                                color = colors.textMuted,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceElevated
                    ) {
                        Text(
                            text = "Synchronized",
                            style = VeylTypography.MonoSpec,
                            color = colors.accentCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Modal Track Options Sheet
    if (selectedTrackForOptions != null) {
        VeylTrackOptionsSheet(
            track = selectedTrackForOptions,
            controller = controller,
            onDismiss = { selectedTrackForOptions = null },
            onOpenEqualizer = onNavigateToTopTracks
        )
    }
}
