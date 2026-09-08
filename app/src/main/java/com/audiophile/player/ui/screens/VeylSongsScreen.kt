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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import kotlinx.coroutines.launch
import uniffi.audiophile_core.TrackInfo

enum class SongViewLayout {
    LIST,
    GRID,
    COMPACT
}

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
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val tracks by controller.tracks.collectAsState()
    val favoriteUris by controller.favoriteUris.collectAsState()
    val playlists by controller.playlists.collectAsState()
    val currentUri by controller.currentUri.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf("All") }
    var viewLayout by remember { mutableStateOf(SongViewLayout.GRID) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }
    var expandedArtist by remember { mutableStateOf<String?>(null) }
    var expandedAlbum by remember { mutableStateOf<String?>(null) }

    val tabs = listOf("All", "Artists", "Albums", "Playlists", "Favorites")

    // Filter tracks by category and search query
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
                    key.first,
                    key.second,
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

    // Alphabet Scrubber Indexer
    val alphabet = remember { listOf("#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            // 1. Top Header: "Veyl / Music, without limits." & Action Controls
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Veyl",
                            style = VeylTypography.TitleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 28.sp
                        )
                        Text(
                            text = "Music, without limits.",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted,
                            fontSize = 13.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Search Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.surfacePanel),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.Search,
                                contentDescription = "Search",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Mount Storage Folder Button
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(colors.surfacePanel)
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onPickFolder()
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.Folder,
                                contentDescription = "Mount Storage",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        // Stylized Aesthetic Profile Avatar
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(Color(0xFF5B3E7A), Color(0xFFCE608D), Color(0xFF65A0D4))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "V",
                                style = VeylTypography.MonoSpec,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
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
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = {
                            Text(
                                text = "Search songs, albums, artists, genres...",
                                style = VeylTypography.Body,
                                color = colors.textMuted,
                                fontSize = 14.sp
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
                            } else {
                                Icon(
                                    imageVector = VeylIcons.Waveform,
                                    contentDescription = "Audio Waveform",
                                    tint = colors.textMuted.copy(alpha = 0.55f),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        },
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfacePanel,
                            unfocusedContainerColor = colors.surfacePanel,
                            focusedBorderColor = colors.accentSignal,
                            unfocusedBorderColor = colors.borderHairline.copy(alpha = 0.4f),
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 3. Category Filter Tabs (All, Artists, Albums, Playlists, Favorites)
            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(tabs) { tab ->
                        val isSelected = selectedTab == tab
                        Surface(
                            shape = CircleShape,
                            color = if (isSelected) Color.White else colors.surfaceElevated,
                            border = if (isSelected) null else androidx.compose.foundation.BorderStroke(
                                1.dp,
                                colors.borderHairline.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTab = tab
                            }
                        ) {
                            Text(
                                text = tab,
                                style = VeylTypography.BodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.Black else colors.textSecondary,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }

            // 4. Subheader with View Layout Switcher (Grid vs List vs Sort)
            if (selectedTab in listOf("All", "Favorites", "Artists", "Albums")) {
                item {
                    val sectionTitle = when (selectedTab) {
                        "Artists" -> "Artists"
                        "Albums" -> "Albums"
                        "Favorites" -> "Favorites"
                        else -> "Master Recordings"
                    }
                    val countBadge = when (selectedTab) {
                        "Artists" -> "${artistGroups.size}"
                        "Albums" -> "${albumGroups.size}"
                        else -> "${filteredTracks.size}"
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = sectionTitle,
                                style = VeylTypography.TitleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfacePanel
                            ) {
                                Text(
                                    text = countBadge,
                                    style = VeylTypography.MonoSpec,
                                    color = colors.textMuted,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        // View Layout Switcher (Grid, List, Sliders)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(colors.surfacePanel)
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Grid View Button
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewLayout = SongViewLayout.GRID
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (viewLayout == SongViewLayout.GRID) colors.surfaceElevated else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.GridView,
                                    contentDescription = "Grid View",
                                    tint = if (viewLayout == SongViewLayout.GRID) Color.White else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // List View Button
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    viewLayout = SongViewLayout.LIST
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (viewLayout == SongViewLayout.LIST) colors.surfaceElevated else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.ListView,
                                    contentDescription = "List View",
                                    tint = if (viewLayout == SongViewLayout.LIST) Color.White else colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Sliders / Shuffle Action
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.playShuffled(filteredTracks)
                                },
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                Icon(
                                    imageVector = VeylIcons.Sliders,
                                    contentDescription = "Shuffle / Filter",
                                    tint = colors.textMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 7. Dynamic Content based on selected category & view layout
            when (selectedTab) {
                "Artists" -> {
                    if (artistGroups.isEmpty()) {
                        item {
                            EmptyStateNotice(message = if (searchQuery.isNotEmpty()) "No matching artists found" else "No artists available")
                        }
                    } else if (viewLayout == SongViewLayout.GRID) {
                        // 2-Column Grid View for Artists
                        items(artistGroups.chunked(2), key = { pair -> pair.first().first }) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { (artistName, artistTracks, albumCount) ->
                                    val firstUri = artistTracks.firstOrNull()?.uri ?: ""
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                controller.playTrackList(artistTracks, startIndex = 0)
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(14.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(72.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.surfaceElevated)
                                            ) {
                                                AsyncAlbumArt(
                                                    uri = firstUri,
                                                    modifier = Modifier.fillMaxSize(),
                                                    shape = CircleShape
                                                )
                                            }

                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = artistName,
                                                    style = VeylTypography.BodyMedium,
                                                    color = colors.textPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "${artistTracks.size} tracks • $albumCount albums",
                                                    style = VeylTypography.BodySmall,
                                                    color = colors.textMuted,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }

                                            Surface(
                                                shape = CircleShape,
                                                color = colors.accentSignal,
                                                modifier = Modifier
                                                    .size(32.dp)
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        controller.playTrackList(artistTracks, startIndex = 0)
                                                    }
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Icon(
                                                        imageVector = VeylIcons.Play,
                                                        contentDescription = "Play Artist",
                                                        tint = colors.surfacePanel,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                if (rowItems.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    } else {
                        items(artistGroups, key = { it.first }) { (artistName, artistTracks, albumCount) ->
                            val isExpanded = expandedArtist == artistName
                            val firstUri = artistTracks.firstOrNull()?.uri ?: ""

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surfacePanel)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            expandedArtist = if (isExpanded) null else artistName
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Surface(
                                        shape = CircleShape,
                                        color = colors.surfaceElevated,
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        AsyncAlbumArt(
                                            uri = firstUri,
                                            modifier = Modifier.fillMaxSize(),
                                            shape = CircleShape
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

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
                                            text = "${artistTracks.size} tracks • $albumCount albums",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textMuted
                                        )
                                    }

                                    IconButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            controller.playTrackList(artistTracks, startIndex = 0)
                                        },
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = VeylIcons.Play,
                                            contentDescription = "Play Artist",
                                            tint = colors.accentSignal,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                "Albums" -> {
                    if (albumGroups.isEmpty()) {
                        item {
                            EmptyStateNotice(message = if (searchQuery.isNotEmpty()) "No matching albums found" else "No albums available")
                        }
                    } else if (viewLayout == SongViewLayout.GRID) {
                        // 2-Column Grid View for Albums
                        items(albumGroups.chunked(2), key = { pair -> pair.first().first + pair.first().second }) { rowItems ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                rowItems.forEach { (albumTitle, artistName, albumTracks) ->
                                    val firstUri = albumTracks.firstOrNull()?.uri ?: ""
                                    Card(
                                        shape = RoundedCornerShape(18.dp),
                                        colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                controller.playTrackList(albumTracks, startIndex = 0)
                                            }
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .aspectRatio(1f)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .background(colors.surfaceElevated)
                                            ) {
                                                AsyncAlbumArt(
                                                    uri = firstUri,
                                                    modifier = Modifier.fillMaxSize()
                                                )

                                                // Play FAB
                                                Box(
                                                    modifier = Modifier
                                                        .padding(6.dp)
                                                        .size(32.dp)
                                                        .align(Alignment.BottomEnd)
                                                        .clip(CircleShape)
                                                        .background(colors.accentSignal)
                                                        .clickable {
                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                            controller.playTrackList(albumTracks, startIndex = 0)
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = VeylIcons.Play,
                                                        contentDescription = "Play Album",
                                                        tint = colors.surfacePanel,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                }
                                            }

                                            Text(
                                                text = albumTitle,
                                                style = VeylTypography.BodyMedium,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$artistName • ${albumTracks.size} tracks",
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
                        items(albumGroups, key = { it.first + it.second }) { (albumTitle, artistName, albumTracks) ->
                            val isExpanded = expandedAlbum == albumTitle
                            val firstUri = albumTracks.firstOrNull()?.uri ?: ""

                            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(colors.surfacePanel)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            expandedAlbum = if (isExpanded) null else albumTitle
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.size(48.dp)
                                    ) {
                                        AsyncAlbumArt(
                                            uri = firstUri,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

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
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Icon(
                                            imageVector = VeylIcons.Play,
                                            contentDescription = "Play Album",
                                            tint = colors.accentSignal,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                "Playlists" -> {
                    if (playlists.isEmpty()) {
                        item {
                            EmptyStateNotice(message = "No playlists created yet. Tap 3-dots on any song to create one!")
                        }
                    } else {
                        items(playlists) { pl ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 4.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(colors.surfacePanel)
                                    .clickable {
                                        onNavigateToPlaylist?.invoke(pl.name)
                                    }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = colors.surfaceElevated,
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = VeylIcons.QueueList,
                                            contentDescription = null,
                                            tint = colors.accentSignal,
                                            modifier = Modifier.size(22.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

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
                    // TRACKS: Render either GRID, COMPACT, or LIST
                    if (filteredTracks.isEmpty()) {
                        item {
                            EmptyStateNotice(message = if (searchQuery.isNotEmpty()) "No matching tracks found" else "No audio files detected in folder")
                        }
                    } else {
                        when (viewLayout) {
                            SongViewLayout.GRID -> {
                                // 2-Column Modern Grid matching the reference audiophile design
                                items(filteredTracks.chunked(2), key = { pair -> pair.first().uri }) { rowItems ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        rowItems.forEach { track ->
                                            val isPlayingThis = currentUri == track.uri
                                            Card(
                                                shape = RoundedCornerShape(18.dp),
                                                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                                                border = androidx.compose.foundation.BorderStroke(
                                                    1.dp,
                                                    if (isPlayingThis) colors.accentSignal else colors.borderHairline.copy(alpha = 0.35f)
                                                ),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        controller.playTrack(track)
                                                    }
                                            ) {
                                                Column(modifier = Modifier.padding(7.dp)) {
                                                    // Square artwork with overlay format badge, 3-dots menu, and play FAB
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

                                                        // Format Badge (e.g. FLAC 24-bit, FLAC 16-bit, DSD 64)
                                                        val depth = track.bitDepth ?: 16u
                                                        val formatLabel = when {
                                                            track.formatName.contains("FLAC", true) -> if (depth >= 24u) "FLAC 24-bit" else "FLAC 16-bit"
                                                            track.formatName.contains("DSD", true) -> "DSD 64"
                                                            track.formatName.contains("WAV", true) -> if (depth >= 24u) "WAV 24-bit" else "WAV 16-bit"
                                                            track.formatName.contains("MP3", true) -> "MP3 320k"
                                                            track.bitDepth != null && track.bitDepth!! > 0u -> "${track.formatName} ${track.bitDepth}-bit"
                                                            else -> track.formatName
                                                        }
                                                        Surface(
                                                            shape = RoundedCornerShape(6.dp),
                                                            color = Color.Black.copy(alpha = 0.72f),
                                                            modifier = Modifier
                                                                .padding(7.dp)
                                                                .align(Alignment.TopStart)
                                                        ) {
                                                            Text(
                                                                text = formatLabel,
                                                                style = VeylTypography.MonoSpec,
                                                                color = Color.White,
                                                                fontSize = 9.5.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                                            )
                                                        }

                                                        // 3-Dots More Options Menu Button
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(6.dp)
                                                                .size(28.dp)
                                                                .align(Alignment.TopEnd)
                                                                .clip(CircleShape)
                                                                .background(Color.Black.copy(alpha = 0.55f))
                                                                .clickable {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    selectedTrackForOptions = track
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = VeylIcons.MoreVert,
                                                                contentDescription = "Options",
                                                                tint = Color.White,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }

                                                        // Floating Circular Play FAB
                                                        Box(
                                                            modifier = Modifier
                                                                .padding(7.dp)
                                                                .size(34.dp)
                                                                .align(Alignment.BottomEnd)
                                                                .clip(CircleShape)
                                                                .background(if (isPlayingThis) colors.accentSignal else Color.Black.copy(alpha = 0.75f))
                                                                .clickable {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                    controller.playTrack(track)
                                                                },
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = if (isPlayingThis) VeylIcons.Pause else VeylIcons.Play,
                                                                contentDescription = if (isPlayingThis) "Pause" else "Play",
                                                                tint = if (isPlayingThis) colors.surfacePanel else Color.White,
                                                                modifier = Modifier.size(15.dp)
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(8.dp))

                                                    Text(
                                                        text = track.title,
                                                        style = VeylTypography.BodyMedium,
                                                        color = if (isPlayingThis) colors.accentSignal else colors.textPrimary,
                                                        fontWeight = FontWeight.SemiBold,
                                                        fontSize = 13.5.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Spacer(modifier = Modifier.height(2.dp))
                                                    Text(
                                                        text = track.artist ?: "Unknown Artist",
                                                        style = VeylTypography.BodySmall,
                                                        color = colors.textMuted,
                                                        fontSize = 11.5.sp,
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
                            }
                            SongViewLayout.COMPACT -> {
                                // Compact Engineering Row
                                itemsIndexed(filteredTracks, key = { _, track -> track.uri }) { index, track ->
                                    val isPlayingThis = currentUri == track.uri
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 2.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isPlayingThis) colors.surfaceElevated else Color.Transparent)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                controller.playTrackList(filteredTracks, startIndex = index)
                                            }
                                            .padding(horizontal = 8.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "%02d".format(index + 1),
                                            style = VeylTypography.MonoSpec,
                                            color = colors.textMuted,
                                            fontSize = 11.sp,
                                            modifier = Modifier.width(28.dp)
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = VeylTypography.BodyMedium,
                                                color = if (isPlayingThis) colors.accentSignal else colors.textPrimary,
                                                fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Medium,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${track.artist ?: "Unknown"} • ${track.formatName} ${track.bitDepth}b/${track.sampleRate / 1000u}k",
                                                style = VeylTypography.BodySmall,
                                                color = colors.textMuted,
                                                fontSize = 10.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        val m = (track.durationSeconds / 60).toInt()
                                        val s = (track.durationSeconds % 60).toInt()
                                        Text(
                                            text = "%d:%02d".format(m, s),
                                            style = VeylTypography.MonoSpec,
                                            color = colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                        IconButton(
                                            onClick = { selectedTrackForOptions = track },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                imageVector = VeylIcons.MoreVert,
                                                contentDescription = "Options",
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                            SongViewLayout.LIST -> {
                                // Detailed Modern Audio List (Stitch Resonate List Row)
                                itemsIndexed(filteredTracks, key = { _, track -> track.uri }) { index, track ->
                                    val isPlayingThis = currentUri == track.uri
                                    val isFav = favoriteUris.contains(track.uri)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 4.dp)
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(if (isPlayingThis) colors.surfaceElevated else colors.surfacePanel)
                                            .clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                controller.playTrackList(filteredTracks, startIndex = index)
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Index number
                                        Text(
                                            text = "%02d".format(index + 1),
                                            style = VeylTypography.MonoSpec,
                                            color = if (isPlayingThis) colors.accentSignal else colors.textMuted,
                                            fontSize = 11.sp,
                                            fontWeight = if (isPlayingThis) FontWeight.Bold else FontWeight.Normal,
                                            modifier = Modifier.width(26.dp)
                                        )

                                        // 44dp Artwork Thumbnail
                                        Card(
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            AsyncAlbumArt(
                                                uri = track.uri,
                                                modifier = Modifier.fillMaxSize()
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(12.dp))

                                        // Track Info
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = VeylTypography.BodyMedium,
                                                color = if (isPlayingThis) colors.accentSignal else colors.textPrimary,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                val m = (track.durationSeconds / 60).toInt()
                                                val s = (track.durationSeconds % 60).toInt()
                                                Text(
                                                    text = "${track.artist ?: "Unknown"} • %d:%02d".format(m, s),
                                                    style = VeylTypography.BodySmall,
                                                    color = colors.textMuted,
                                                    fontSize = 11.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = colors.surfaceElevated
                                                ) {
                                                    Text(
                                                        text = "${track.formatName} ${track.bitDepth}b",
                                                        style = VeylTypography.MonoSpec,
                                                        color = colors.accentSignal,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }

                                        // Heart Favorite Button
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                controller.toggleFavorite(track.uri)
                                            },
                                            modifier = Modifier.size(34.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isFav) VeylIcons.FavoriteFilled else VeylIcons.FavoriteBorder,
                                                contentDescription = "Favorite",
                                                tint = if (isFav) colors.accentSignal else colors.textMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        // More Options
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                selectedTrackForOptions = track
                                            },
                                            modifier = Modifier.size(34.dp)
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
            }
        }

        // 8. Alphabet Fast Scrubber Bar (Floating on the right edge)
        if (selectedTab == "All" && filteredTracks.size > 8 && viewLayout != SongViewLayout.GRID) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 4.dp)
                    .fillMaxHeight(0.7f),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                alphabet.forEach { letter ->
                    Text(
                        text = letter,
                        style = VeylTypography.MonoSpec,
                        color = colors.textMuted,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val targetIdx = if (letter == "#") 0 else {
                                    filteredTracks.indexOfFirst {
                                        it.title.firstOrNull()?.uppercaseChar() == letter[0]
                                    }
                                }
                                if (targetIdx >= 0) {
                                    coroutineScope.launch {
                                        listState.scrollToItem(targetIdx + 5) // Offset for header items
                                    }
                                }
                            }
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }

    // Modal Track Options Sheet
    if (selectedTrackForOptions != null) {
        VeylTrackOptionsSheet(
            track = selectedTrackForOptions,
            controller = controller,
            onDismiss = { selectedTrackForOptions = null }
        )
    }
}

@Composable
private fun EmptyStateNotice(message: String) {
    val colors = LocalVeylColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(40.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = VeylTypography.Body,
            color = colors.textMuted
        )
    }
}
