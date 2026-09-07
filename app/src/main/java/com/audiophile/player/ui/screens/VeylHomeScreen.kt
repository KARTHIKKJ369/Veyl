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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
    val status by controller.status.collectAsState()
    val currentTrack = status?.currentTrack

    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }
    var masterTrackLayout by remember { mutableStateOf(SongViewLayout.GRID) }

    // Dynamic list for "Mastered & Recent / Listen Now"
    val recentTracks = remember(playbackHistory, lastAddedTracks, tracks) {
        when {
            playbackHistory.isNotEmpty() -> playbackHistory.take(10)
            lastAddedTracks.isNotEmpty() -> lastAddedTracks.take(10)
            else -> tracks.take(10)
        }
    }

    // Dynamic list for "For You / Master Tracks"
    val forYouTracks = remember(topTracks, tracks) {
        when {
            topTracks.isNotEmpty() -> topTracks.take(12)
            else -> tracks.take(12)
        }
    }

    // Dynamic search results for inline search bar
    val searchResults = remember(searchQuery, tracks) {
        if (searchQuery.isBlank()) emptyList()
        else {
            tracks.filter { track ->
                track.title.contains(searchQuery, ignoreCase = true) ||
                        (track.artist?.contains(searchQuery, ignoreCase = true) == true) ||
                        (track.album?.contains(searchQuery, ignoreCase = true) == true)
            }.take(10)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // 1. Clean Modern Audiophile Header: App Title, Search Toggle, and Quick Folder Mount
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Veyl",
                    style = VeylTypography.TitleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 26.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Interactive Inline Search Toggle
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isSearchActive = !isSearchActive
                            if (!isSearchActive) searchQuery = ""
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isSearchActive) colors.accentSignal else colors.surfacePanel)
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) VeylIcons.Close else VeylIcons.Search,
                            contentDescription = if (isSearchActive) "Close Search" else "Search",
                            tint = if (isSearchActive) colors.surfacePanel else colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Storage Folder Picker
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onPickFolder()
                        },
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(colors.surfacePanel)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Folder,
                            contentDescription = "Mount Folder",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Interactive Inline Live Search Field and Instant Results
        if (isSearchActive) {
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search tracks, artists, albums...",
                            style = VeylTypography.BodyMedium,
                            color = colors.textMuted
                        )
                    },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = VeylIcons.Search,
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = VeylIcons.Close,
                                    contentDescription = "Clear",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentSignal,
                        unfocusedBorderColor = colors.borderHairline,
                        focusedContainerColor = colors.surfacePanel,
                        unfocusedContainerColor = colors.surfacePanel,
                        cursorColor = colors.accentSignal,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            if (searchQuery.isNotBlank()) {
                if (searchResults.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching tracks found for \"$searchQuery\"",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                } else {
                    item {
                        Text(
                            text = "SEARCH RESULTS (${searchResults.size})",
                            style = VeylTypography.MonoBadge,
                            color = colors.accentSignal
                        )
                    }

                    items(searchResults, key = { it.uri }) { track ->
                        val isCurrent = currentTrack?.uri == track.uri
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isCurrent) colors.surfaceElevated else colors.surfacePanel)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.playTrack(track)
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(colors.surfaceElevated)
                            ) {
                                AsyncAlbumArt(
                                    uri = track.uri,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
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
                                    text = "${track.artist ?: "Unknown"} • ${track.formatName} ${track.bitDepth}b",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.playTrack(track)
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCurrent) VeylIcons.Pause else VeylIcons.Play,
                                    contentDescription = "Play",
                                    tint = colors.accentSignal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. "Mastered & Recent" / "Listen Now" (Dynamic Horizontal Carousel)
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

                                        // Format Badge (matching active theme accentSignal)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = Color.Black.copy(alpha = 0.75f),
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
                                                color = colors.accentSignal,
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

        // 4. "Master Tracks" (Modern 2-Column Grid / List with Sleek Badges)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Master Tracks",
                        style = VeylTypography.TitleLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.surfacePanel
                    ) {
                        Text(
                            text = "${tracks.size}",
                            style = VeylTypography.MonoSpec,
                            color = colors.accentSignal,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }

                // Grid / List View Toggle
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfacePanel)
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            masterTrackLayout = SongViewLayout.GRID
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (masterTrackLayout == SongViewLayout.GRID) colors.surfaceElevated else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = VeylIcons.StorageLocal,
                            contentDescription = "Grid View",
                            tint = if (masterTrackLayout == SongViewLayout.GRID) colors.accentSignal else colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            masterTrackLayout = SongViewLayout.LIST
                        },
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (masterTrackLayout == SongViewLayout.LIST) colors.surfaceElevated else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = VeylIcons.QueueList,
                            contentDescription = "List View",
                            tint = if (masterTrackLayout == SongViewLayout.LIST) colors.accentSignal else colors.textMuted,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        // Render Master Tracks based on selected layout (Grid vs List)
        if (masterTrackLayout == SongViewLayout.GRID) {
            // Modern 2-Column Grid
            items(forYouTracks.chunked(2), key = { pair -> pair.first().uri }) { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    rowItems.forEach { track ->
                        val isPlayingThis = currentTrack?.uri == track.uri
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isPlayingThis) colors.accentSignal else colors.borderHairline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.playTrack(track)
                                }
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(colors.surfaceElevated)
                                ) {
                                    AsyncAlbumArt(
                                        uri = track.uri,
                                        modifier = Modifier.fillMaxSize()
                                    )

                                    // Frosted Theme Badge
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = Color.Black.copy(alpha = 0.75f),
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "${track.formatName} ${track.bitDepth}b",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.accentSignal,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }

                                    // Play FAB
                                    Box(
                                        modifier = Modifier
                                            .padding(6.dp)
                                            .size(32.dp)
                                            .align(Alignment.BottomEnd)
                                            .clip(CircleShape)
                                            .background(if (isPlayingThis) colors.accentSignal else colors.surfaceElevated)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                controller.playTrack(track)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (isPlayingThis) VeylIcons.Pause else VeylIcons.Play,
                                            contentDescription = "Play",
                                            tint = if (isPlayingThis) colors.surfacePanel else colors.textPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))

                                Text(
                                    text = track.title,
                                    style = VeylTypography.BodyMedium,
                                    color = if (isPlayingThis) colors.accentSignal else colors.textPrimary,
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
                            }
                        }
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
            // Sleek List View
            itemsIndexed(forYouTracks, key = { _, track -> track.uri }) { index, track ->
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
                                    color = colors.accentSignal,
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
