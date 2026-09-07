package com.audiophile.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
fun VeylSongsScreen(
    controller: AudioEngineController,
    onPickFolder: () -> Unit,
    onNavigateToPlaylist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val tracks by controller.tracks.collectAsState()
    val favoriteUris by controller.favoriteUris.collectAsState()
    val playlists by controller.playlists.collectAsState()
    val currentUri by controller.currentUri.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("All") }
    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }
    var expandedArtist by remember { mutableStateOf<String?>(null) }
    var expandedAlbum by remember { mutableStateOf<String?>(null) }

    val tabs = listOf("All", "Artists", "Albums", "Playlists", "Favorites")

    val filteredTracks = remember(tracks, searchQuery, selectedTab, favoriteUris) {
        tracks.filter { track ->
            val artistName = track.artist ?: ""
            val albumName = track.album ?: ""
            val matchesQuery = searchQuery.isBlank() ||
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    artistName.contains(searchQuery, ignoreCase = true) ||
                    albumName.contains(searchQuery, ignoreCase = true)

            val matchesTab = when (selectedTab) {
                "Favorites" -> favoriteUris.contains(track.uri)
                else -> true
            }

            matchesQuery && matchesTab
        }
    }

    val artistGroups = remember(tracks, searchQuery) {
        tracks
            .groupBy { it.artist?.takeIf { a -> a.isNotBlank() } ?: "Unknown Artist" }
            .map { (artistName, trackList) ->
                val albums = trackList.mapNotNull { it.album?.takeIf { alb -> alb.isNotBlank() } }.distinct()
                Triple(
                    artistName,
                    trackList,
                    albums.size.coerceAtLeast(1)
                )
            }
            .filter { (name, _, _) ->
                searchQuery.isBlank() || name.contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.first.lowercase() }
    }

    val albumGroups = remember(tracks, searchQuery) {
        tracks
            .groupBy { (it.album?.takeIf { a -> a.isNotBlank() } ?: "Unknown Album") to (it.artist?.takeIf { a -> a.isNotBlank() } ?: "Unknown Artist") }
            .map { (key, trackList) ->
                Triple(
                    key.first, // Album title
                    key.second, // Artist name
                    trackList.sortedBy { it.trackNumber ?: 0u }
                )
            }
            .filter { (title, artist, _) ->
                searchQuery.isBlank() ||
                        title.contains(searchQuery, ignoreCase = true) ||
                        artist.contains(searchQuery, ignoreCase = true)
            }
            .sortedBy { it.first.lowercase() }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. Top Header: "Songs" Title & Folder Picker
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Songs",
                        style = VeylTypography.DisplayLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${filteredTracks.size} tracks available",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = colors.surfaceElevated,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = onPickFolder) {
                        Icon(
                            imageVector = VeylIcons.Folder,
                            contentDescription = "Mount Folder",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Search Field
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text(
                            text = "Search tracks, artists, albums...",
                            style = VeylTypography.Body,
                            color = colors.textMuted
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = VeylIcons.Search,
                            contentDescription = "Search",
                            tint = colors.textMuted,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = VeylIcons.Close,
                                    contentDescription = "Clear",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = colors.surfacePanel,
                        unfocusedContainerColor = colors.surfacePanel,
                        focusedBorderColor = colors.accentSignal,
                        unfocusedBorderColor = colors.borderHairline,
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // 3. Category Filter Tabs (Standard clean music categories: All, Artists, Albums, Playlists, Favorites)
        item {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(tabs) { tab ->
                    val isSelected = selectedTab == tab
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = tab
                        },
                        label = {
                            Text(
                                text = tab,
                                style = VeylTypography.BodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.surfacePanel else colors.textPrimary
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.surfaceElevated,
                            selectedContainerColor = colors.accentSignal,
                            labelColor = colors.textPrimary,
                            selectedLabelColor = colors.surfacePanel
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = colors.borderHairline,
                            selectedBorderColor = colors.accentSignal,
                            enabled = true,
                            selected = isSelected
                        ),
                        shape = RoundedCornerShape(12.dp)
                    )
                }
            }
        }

        // 3.5 Action Row: Play All & Shuffle Buttons
        if (selectedTab == "All" || selectedTab == "Favorites") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val activeTrackPool = if (filteredTracks.isNotEmpty()) filteredTracks else tracks
                    val hasTracks = activeTrackPool.isNotEmpty()

                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Play All Button
                        Button(
                            onClick = {
                                if (hasTracks) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.playTrackList(activeTrackPool, startIndex = 0)
                                }
                            },
                            enabled = hasTracks,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.surfaceElevated,
                                contentColor = colors.textPrimary,
                                disabledContainerColor = colors.surfaceElevated.copy(alpha = 0.5f),
                                disabledContentColor = colors.textMuted
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (hasTracks) colors.accentSignal else colors.textMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Play All",
                                style = VeylTypography.TitleMedium,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                        }

                        // Shuffle All Button
                        Button(
                            onClick = {
                                if (hasTracks) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.playShuffled(activeTrackPool)
                                }
                            },
                            enabled = hasTracks,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.accentSignal,
                                contentColor = Color.Black,
                                disabledContainerColor = colors.surfaceElevated.copy(alpha = 0.5f),
                                disabledContentColor = colors.textMuted
                            ),
                            shape = RoundedCornerShape(14.dp),
                            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.Shuffle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (hasTracks) Color.Black else colors.textMuted
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Shuffle",
                                style = VeylTypography.TitleMedium,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (hasTracks) Color.Black else colors.textMuted
                            )
                        }
                    }

                    // Total count pill
                    Text(
                        text = "${activeTrackPool.size} tracks",
                        style = VeylTypography.MonoSpec,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else if (selectedTab == "Artists") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${artistGroups.size} artists",
                        style = VeylTypography.MonoSpec,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        } else if (selectedTab == "Albums") {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${albumGroups.size} albums",
                        style = VeylTypography.MonoSpec,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 4. Content based on tab
        when (selectedTab) {
            "Artists" -> {
                if (artistGroups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching artists found" else "No artists available",
                                style = VeylTypography.Body,
                                color = colors.textMuted
                            )
                        }
                    }
                } else {
                    items(artistGroups, key = { it.first }) { (artistName, artistTracks, albumCount) ->
                        val isExpanded = expandedArtist == artistName
                        val firstUri = artistTracks.firstOrNull()?.uri ?: ""

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        expandedArtist = if (isExpanded) null else artistName
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.surfaceElevated,
                                    modifier = Modifier.size(52.dp)
                                ) {
                                    AsyncAlbumArt(
                                        uri = firstUri,
                                        modifier = Modifier.fillMaxSize(),
                                        shape = CircleShape
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artistName,
                                        style = VeylTypography.TitleMedium,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${artistTracks.size} track${if (artistTracks.size > 1) "s" else ""} • $albumCount album${if (albumCount > 1) "s" else ""}",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textMuted
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.playTrackList(artistTracks, startIndex = 0)
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Play,
                                        contentDescription = "Play Artist",
                                        tint = colors.accentSignal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Icon(
                                    imageVector = if (isExpanded) VeylIcons.ChevronDown else VeylIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.surfacePanel.copy(alpha = 0.5f))
                                        .padding(start = 68.dp, end = 20.dp, bottom = 8.dp)
                                ) {
                                    artistTracks.forEachIndexed { subIdx, subTrack ->
                                        val isPlayingSub = currentUri == subTrack.uri
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    controller.playTrackList(artistTracks, startIndex = subIdx)
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = subTrack.title,
                                                    style = VeylTypography.Body,
                                                    color = if (isPlayingSub) colors.accentSignal else colors.textPrimary,
                                                    fontWeight = if (isPlayingSub) FontWeight.Bold else FontWeight.Normal,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = subTrack.album ?: "Single",
                                                    style = VeylTypography.BodySmall,
                                                    color = colors.textMuted,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            val m = (subTrack.durationSeconds / 60).toInt()
                                            val s = (subTrack.durationSeconds % 60).toInt()
                                            Text(
                                                text = "%d:%02d".format(m, s),
                                                style = VeylTypography.MonoSpec,
                                                color = colors.textMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Albums" -> {
                if (albumGroups.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchQuery.isNotEmpty()) "No matching albums found" else "No albums available",
                                style = VeylTypography.Body,
                                color = colors.textMuted
                            )
                        }
                    }
                } else {
                    items(albumGroups, key = { "${it.first}___${it.second}" }) { (albumTitle, artistName, albumTracks) ->
                        val isExpanded = expandedAlbum == albumTitle
                        val firstUri = albumTracks.firstOrNull()?.uri ?: ""

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        expandedAlbum = if (isExpanded) null else albumTitle
                                    }
                                    .padding(horizontal = 20.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Card(
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.size(54.dp)
                                ) {
                                    AsyncAlbumArt(
                                        uri = firstUri,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = albumTitle,
                                        style = VeylTypography.TitleMedium,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "$artistName • ${albumTracks.size} tracks",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.playTrackList(albumTracks, startIndex = 0)
                                    },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Play,
                                        contentDescription = "Play Album",
                                        tint = colors.accentSignal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Icon(
                                    imageVector = if (isExpanded) VeylIcons.ChevronDown else VeylIcons.ChevronRight,
                                    contentDescription = null,
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.surfacePanel.copy(alpha = 0.5f))
                                        .padding(start = 70.dp, end = 20.dp, bottom = 8.dp)
                                ) {
                                    albumTracks.forEachIndexed { subIdx, subTrack ->
                                        val isPlayingSub = currentUri == subTrack.uri
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    controller.playTrackList(albumTracks, startIndex = subIdx)
                                                }
                                                .padding(vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${subTrack.trackNumber ?: (subIdx + 1)}",
                                                style = VeylTypography.MonoSpec,
                                                color = colors.textMuted,
                                                fontSize = 12.sp,
                                                modifier = Modifier.width(26.dp)
                                            )
                                            Text(
                                                text = subTrack.title,
                                                style = VeylTypography.Body,
                                                color = if (isPlayingSub) colors.accentSignal else colors.textPrimary,
                                                fontWeight = if (isPlayingSub) FontWeight.Bold else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            val m = (subTrack.durationSeconds / 60).toInt()
                                            val s = (subTrack.durationSeconds % 60).toInt()
                                            Text(
                                                text = "%d:%02d".format(m, s),
                                                style = VeylTypography.MonoSpec,
                                                color = colors.textMuted,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            "Playlists" -> {
                if (playlists.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No playlists created yet. Tap 3-dots on any song to create one!",
                                style = VeylTypography.Body,
                                color = colors.textMuted
                            )
                        }
                    }
                } else {
                    items(playlists) { pl ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onNavigateToPlaylist?.invoke(pl.name)
                                }
                                .padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surfaceElevated,
                                modifier = Modifier.size(50.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = VeylIcons.QueueList,
                                        contentDescription = null,
                                        tint = colors.accentSignal,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = pl.name,
                                    style = VeylTypography.TitleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "${pl.trackUris.size} tracks",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textMuted
                                )
                            }

                            Icon(
                                imageVector = VeylIcons.ChevronRight,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
            else -> {
            // Render Track List
            if (filteredTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "No matching tracks found" else "No audio files detected",
                            style = VeylTypography.Body,
                            color = colors.textMuted
                        )
                    }
                }
            } else {
                itemsIndexed(filteredTracks, key = { _, track -> track.uri }) { index, track ->
                    val isPlayingThis = currentUri == track.uri

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                controller.playTrackList(filteredTracks, startIndex = index)
                            }
                            .background(if (isPlayingThis) colors.surfaceElevated.copy(alpha = 0.5f) else Color.Transparent)
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Artwork Thumbnail
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.size(48.dp)
                        ) {
                            AsyncAlbumArt(
                                uri = track.uri,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        // Title & Artist
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                style = VeylTypography.TitleMedium,
                                color = if (isPlayingThis) colors.accentSignal else colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium
                            )
                            Text(
                                text = "${track.artist} • ${track.album}",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Bit-depth / Duration specs
                        val min = (track.durationSeconds / 60).toInt()
                        val sec = (track.durationSeconds % 60).toInt()
                        Text(
                            text = String.format("%d:%02d", min, sec),
                            style = VeylTypography.MonoSpec,
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        // 3-dot options sheet trigger
                        IconButton(
                            onClick = { selectedTrackForOptions = track },
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

    // Modal Track Options Sheet
    selectedTrackForOptions?.let { track ->
        VeylTrackOptionsSheet(
            track = track,
            controller = controller,
            onDismiss = { selectedTrackForOptions = null }
        )
    }
}
