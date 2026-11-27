package com.audiophile.player.ui.screens

import androidx.activity.compose.BackHandler
import com.audiophile.player.ui.components.VeylActionButton
import com.audiophile.player.ui.components.VeylAlphabetFastScroller
import com.audiophile.player.ui.components.VeylEmptyState
import com.audiophile.player.ui.components.VeylListRow
import com.audiophile.player.ui.components.VeylScreenHeader
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.graphics.Brush
import com.audiophile.player.engine.ArtworkCache
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.engine.UserPlaylist
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import com.audiophile.player.ui.components.VeylTrackOptionsSheet
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import kotlinx.coroutines.launch
import uniffi.audiophile_core.TrackInfo
import java.io.File

enum class SongViewLayout {
    LIST,
    GRID,
    COMPACT
}

enum class SongSortOption(val label: String) {
    DEFAULT("Default Library Order"),
    TITLE("Song Title (A-Z)"),
    ARTIST("Artist Name"),
    ALBUM("Album"),
    DATE_ADDED("Recently Added"),
    YEAR("Year"),
    DURATION("Duration"),
    SAMPLE_RATE("Hi-Res Sample Rate")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylSongsScreen(
    controller: AudioEngineController,
    onNavigateToSearch: () -> Unit = {},
    onPickFolder: () -> Unit,
    onNavigateToPlaylist: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val tracks by controller.tracks.collectAsStateWithLifecycle()
    val isScanning by controller.isScanning.collectAsStateWithLifecycle()
    val favoriteUris by controller.favoriteUris.collectAsStateWithLifecycle()
    val currentUri by controller.currentUri.collectAsStateWithLifecycle()
    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val isAudioPlaying = playbackState == uniffi.audiophile_core.PlaybackStateEnum.PLAYING

    val uiPrefs = remember { context.getSharedPreferences("veyl_ui_prefs", android.content.Context.MODE_PRIVATE) }

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("All") }
    var viewLayout by remember {
        val saved = uiPrefs.getString("library_view_layout", "LIST")
        mutableStateOf(runCatching { SongViewLayout.valueOf(saved ?: "LIST") }.getOrDefault(SongViewLayout.LIST))
    }
    var sortOption by remember {
        val saved = uiPrefs.getString("library_sort_option", "DEFAULT")
        mutableStateOf(runCatching { SongSortOption.valueOf(saved ?: "DEFAULT") }.getOrDefault(SongSortOption.DEFAULT))
    }
    var sortAscending by remember {
        mutableStateOf(uiPrefs.getBoolean("library_sort_ascending", true))
    }
    var showSortFilterSheet by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }

    BackHandler(enabled = isSearchExpanded || searchQuery.isNotEmpty() || selectedTab != "All") {
        when {
            isSearchExpanded || searchQuery.isNotEmpty() -> {
                isSearchExpanded = false
                searchQuery = ""
            }
            selectedTab != "All" -> selectedTab = "All"
        }
    }

    val tabs = listOf("All", "Favorites", "Artists", "Albums")

    val fileModifiedMap = remember { java.util.concurrent.ConcurrentHashMap<String, Long>() }

    // Filter and sort tracks by category, search query, and sort options
    val filteredTracks = remember(tracks, searchQuery, selectedTab, favoriteUris, sortOption, sortAscending) {
        val sourceTracks = if (selectedTab == "Favorites") {
            favoriteUris.mapNotNull { controller.resolveTrackInfo(it) }.filter {
                val fName = File(it.uri).name
                !fName.startsWith(".") &&
                !fName.contains("trashed", ignoreCase = true) &&
                !it.title.contains("trashed", ignoreCase = true) &&
                !fName.startsWith("veyl_online_stream_") &&
                !it.uri.contains("/cache/")
            }.distinctBy { it.uri }
        } else tracks
        val base = sourceTracks.filter { track ->
            val artistName = track.artist ?: ""
            val albumName = track.album ?: ""
            val matchesQuery = searchQuery.isBlank() ||
                    track.title.contains(searchQuery, ignoreCase = true) ||
                    artistName.contains(searchQuery, ignoreCase = true) ||
                    albumName.contains(searchQuery, ignoreCase = true)

            matchesQuery
        }

        val sorted = when (sortOption) {
            SongSortOption.DEFAULT -> base.sortedBy { it.title.trim().lowercase() }
            SongSortOption.TITLE -> base.sortedBy { it.title.trim().lowercase() }
            SongSortOption.ARTIST -> base.sortedBy { (it.artist ?: "").trim().lowercase() }
            SongSortOption.ALBUM -> base.sortedBy { (it.album ?: "").trim().lowercase() }
            SongSortOption.DATE_ADDED -> {
                val timestamps = base.associate { track ->
                    track.uri to (fileModifiedMap[track.uri] ?: File(track.uri).lastModified().also { fileModifiedMap[track.uri] = it })
                }
                base.sortedByDescending { timestamps[it.uri] ?: 0L }
            }
            SongSortOption.YEAR -> base.sortedByDescending { it.year ?: 0u }
            SongSortOption.DURATION -> base.sortedBy { it.durationSeconds }
            SongSortOption.SAMPLE_RATE -> base.sortedBy { it.sampleRate ?: 0u }
        }

        if (!sortAscending && sortOption != SongSortOption.DEFAULT) sorted.reversed() else sorted
    }

    val preparedArtistGroups by controller.artistGroups.collectAsStateWithLifecycle()
    val preparedAlbumGroups by controller.albumGroups.collectAsStateWithLifecycle()
    val artistGroups = remember(preparedArtistGroups, searchQuery) {
        preparedArtistGroups.filter { (name, _, _) -> searchQuery.isBlank() || name.contains(searchQuery, ignoreCase = true) }
    }
    val albumGroups = remember(preparedAlbumGroups, searchQuery) {
        preparedAlbumGroups.filter { (title, artist, _) ->
            searchQuery.isBlank() || title.contains(searchQuery, ignoreCase = true) || artist.contains(searchQuery, ignoreCase = true)
        }
    }

    // Alphabet Scrubber Indexer
    val alphabet = remember { listOf("#", "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M", "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z") }
    val letterIndexMap = remember(filteredTracks) {
        val map = mutableMapOf<String, Int>()
        map["#"] = 0
        filteredTracks.forEachIndexed { idx, track ->
            val first = track.title.firstOrNull()?.uppercaseChar()?.toString()
            if (first != null && first in "A".."Z" && !map.containsKey(first)) {
                map[first] = idx
            }
        }
        map
    }

    val gridTrackRows = remember(filteredTracks) { filteredTracks.chunked(2) }
    val gridArtistRows = remember(artistGroups) { artistGroups.chunked(2) }
    val gridAlbumRows = remember(albumGroups) { albumGroups.chunked(2) }

    val onPlayTrackAtIndex = remember(filteredTracks) {
        { idx: Int ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            controller.playTrackList(filteredTracks, startIndex = idx)
        }
    }

    val onSelectTrackOptions = remember {
        { t: TrackInfo ->
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            selectedTrackForOptions = t
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // 1. Pinned Top Header: "Library" & Crafted Tactile Action Controls
        VeylScreenHeader(
            title = "Library",
            subtitle = if (isScanning) "Refreshing library…" else "Your local collection"
        ) {
            VeylActionButton(
                icon = VeylIcons.Search,
                contentDescription = "Search Library",
                onClick = onNavigateToSearch
            )

            // Refresh / Rescan Library Button (matches borderless VeylActionButton style)
            if (isScanning) {
                Box(
                    modifier = Modifier.size(44.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.accentSignal,
                        strokeWidth = 2.dp
                    )
                }
            } else {
                VeylActionButton(
                    icon = VeylIcons.Refresh,
                    contentDescription = "Refresh Library",
                    onClick = { controller.rescanLibrary() }
                )
            }

            VeylActionButton(
                icon = VeylIcons.Folder,
                contentDescription = "Choose Music Folder",
                onClick = onPickFolder
            )
        }

        // 2. Clean Text Filter Tabs (Editorial underline indicator, zero capsule pill bloat)
        val tabsScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabsScrollState)
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                val isSelected = selectedTab == tab
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = tab
                        }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab,
                        style = VeylTypography.TitleMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) colors.textPrimary else colors.textMuted,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(5.dp))
                    Box(
                        modifier = Modifier
                            .width(if (isSelected) 20.dp else 0.dp)
                            .height(2.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) colors.accentSignal else Color.Transparent)
                    )
                }
            }
        }

        androidx.compose.material3.HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.borderHairline.copy(alpha = 0.2f)
        )

        // 3. Pinned Subheader with Clean Text Actions & View Switcher
        if (selectedTab in listOf("All", "Favorites", "Artists", "Albums")) {
            val sectionTitle = when (selectedTab) {
                "Artists" -> "Artists"
                "Albums" -> "Albums"
                "Favorites" -> "Favorites"
                else -> "Total"
            }
            val countBadge = when (selectedTab) {
                "Artists" -> "${artistGroups.size}"
                "Albums" -> "${albumGroups.size}"
                else -> "${filteredTracks.size}"
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$sectionTitle · $countBadge",
                    style = VeylTypography.BodySmall,
                    color = colors.textMuted,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Clean Shuffle Text+Icon (matching Home screen style)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val pool = if (filteredTracks.isNotEmpty()) filteredTracks else tracks
                                if (pool.isNotEmpty()) {
                                    controller.playShuffled(pool)
                                }
                            }
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Shuffle,
                            contentDescription = "Shuffle",
                            tint = colors.accentSignal,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Shuffle",
                            style = VeylTypography.BodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colors.textPrimary,
                            fontSize = 13.sp
                        )
                    }

                    // Clean borderless Grid / List / Sort toggles
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val nextLayout = if (viewLayout == SongViewLayout.GRID) SongViewLayout.LIST else SongViewLayout.GRID
                            viewLayout = nextLayout
                            uiPrefs.edit().putString("library_view_layout", nextLayout.name).apply()
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = if (viewLayout == SongViewLayout.GRID) VeylIcons.ListView else VeylIcons.GridView,
                            contentDescription = "Toggle Grid/List",
                            tint = if (viewLayout == SongViewLayout.GRID) colors.accentSignal else colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSortFilterSheet = true
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Sliders,
                            contentDescription = "Sort and Filter",
                            tint = if (sortOption != SongSortOption.DEFAULT) colors.accentSignal else colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 4. Content Area: Dedicated Lazy Layouts & Floating Fast Scrubber
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                when (selectedTab) {
                    "Artists" -> {
                        if (artistGroups.isEmpty()) {
                            item {
                                EmptyStateNotice(message = if (searchQuery.isNotEmpty()) "No matching artists found" else "No artists available")
                            }
                        } else if (viewLayout == SongViewLayout.GRID) {
                            items(gridArtistRows, key = { pair -> pair.first().first }) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { (artistName, artistTracks, albumCount) ->
                                        val firstTrack = artistTracks.firstOrNull()
                                        val firstUri = firstTrack?.uri ?: ""
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onNavigateToPlaylist?.invoke("artist:${Uri.encode(artistName)}")
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
                                                        hasArtwork = firstTrack?.hasArtwork,
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
                                VeylListRow(
                                    title = artistName,
                                    subtitle = "${artistTracks.size} tracks • $albumCount albums",
                                    isPlaying = false,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigateToPlaylist?.invoke("artist:${Uri.encode(artistName)}")
                                    },
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                controller.playTrackList(artistTracks, startIndex = 0)
                                            },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = VeylIcons.Play,
                                                contentDescription = "Play Artist",
                                                tint = colors.accentSignal,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                    "Albums" -> {
                        if (albumGroups.isEmpty()) {
                            item {
                                EmptyStateNotice(message = if (searchQuery.isNotEmpty()) "No matching albums found" else "No albums available")
                            }
                        } else if (viewLayout == SongViewLayout.GRID) {
                            items(gridAlbumRows, key = { pair -> pair.first().first + "_" + pair.first().second }) { rowItems ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    rowItems.forEach { (albumTitle, artistName, albumTracks) ->
                                        val firstTrack = albumTracks.firstOrNull()
                                        val firstUri = firstTrack?.uri ?: ""
                                        Card(
                                            shape = RoundedCornerShape(18.dp),
                                            colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    onNavigateToPlaylist?.invoke("album:${Uri.encode(albumTitle)}")
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
                                                        hasArtwork = firstTrack?.hasArtwork,
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
                            items(albumGroups, key = { it.first + "_" + it.second }) { (albumTitle, artistName, albumTracks) ->
                                VeylListRow(
                                    title = albumTitle,
                                    subtitle = "$artistName • ${albumTracks.size} tracks",
                                    isPlaying = false,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onNavigateToPlaylist?.invoke("album:${Uri.encode(albumTitle)}")
                                    },
                                    trailingContent = {
                                        IconButton(
                                            onClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                controller.playTrackList(albumTracks, startIndex = 0)
                                            },
                                            modifier = Modifier.size(44.dp)
                                        ) {
                                            Icon(
                                                imageVector = VeylIcons.Play,
                                                contentDescription = "Play Album",
                                                tint = colors.accentSignal,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                )
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
                                    items(gridTrackRows, key = { pair -> pair.first().uri }) { rowItems ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 16.dp, vertical = 6.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                                        ) {
                                            rowItems.forEach { track ->
                                                val isPlayingThis = currentUri == track.uri
                                                Column(
                                                    modifier = Modifier.weight(1f).clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        controller.playTrack(track)
                                                    }
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .aspectRatio(1f)
                                                            .clip(RoundedCornerShape(14.dp))
                                                            .background(colors.surfaceElevated)
                                                    ) {
                                                        AsyncAlbumArt(
                                                            uri = track.uri,
                                                            hasArtwork = track.hasArtwork,
                                                            modifier = Modifier.fillMaxSize()
                                                        )
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
                                            if (rowItems.size == 1) {
                                                Spacer(modifier = Modifier.weight(1f))
                                            }
                                        }
                                    }
                                }
                                SongViewLayout.COMPACT -> {
                                    itemsIndexed(
                                        items = filteredTracks,
                                        key = { _, track -> track.uri },
                                        contentType = { _, _ -> "song_compact" }
                                    ) { index, track ->
                                        SongCompactRow(
                                            track = track,
                                            isPlaying = currentUri == track.uri,
                                            index = index,
                                            onPlay = onPlayTrackAtIndex,
                                            onOptions = onSelectTrackOptions
                                        )
                                    }
                                }
                                SongViewLayout.LIST -> {
                                    itemsIndexed(
                                        items = filteredTracks,
                                        key = { _, track -> track.uri },
                                        contentType = { _, _ -> "song_list" }
                                    ) { index, track ->
                                        SongListRow(
                                            track = track,
                                            isPlaying = currentUri == track.uri,
                                            isAnimating = currentUri == track.uri && isAudioPlaying,
                                            index = index,
                                            onPlay = onPlayTrackAtIndex,
                                            onOptions = onSelectTrackOptions
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Invisible-until-slide Transparent Ribbon A-Z Scroller across All, Favorites, Artists, and Albums
            val scrollerKeys = remember(selectedTab, filteredTracks, artistGroups, albumGroups) {
                when (selectedTab) {
                    "Artists" -> artistGroups.map { it.first }
                    "Albums" -> albumGroups.map { it.first }
                    else -> filteredTracks.map { it.title }
                }
            }
            if (viewLayout != SongViewLayout.GRID && scrollerKeys.size >= 10) {
                VeylAlphabetFastScroller(
                    items = scrollerKeys,
                    listState = listState,
                    headerItemCount = 0,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
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

    // Modal Sort & Filter Sheet
    if (showSortFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSortFilterSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = colors.surfacePanel,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(vertical = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(colors.borderHairline)
                )
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "SORT & FILTER",
                            style = VeylTypography.MonoBadge,
                            color = colors.accentSignal
                        )
                        Text(
                            text = "Library Arrangement",
                            style = VeylTypography.TitleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }

                    if (sortOption != SongSortOption.DEFAULT || !sortAscending) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.surfaceElevated,
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sortOption = SongSortOption.DEFAULT
                                sortAscending = true
                                uiPrefs.edit()
                                    .putString("library_sort_option", SongSortOption.DEFAULT.name)
                                    .putBoolean("library_sort_ascending", true)
                                    .apply()
                            }
                        ) {
                            Text(
                                text = "Reset",
                                style = VeylTypography.MonoSpec,
                                color = colors.accentSignal,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Sort Direction Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (sortAscending) colors.surfacePanel else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sortAscending = true
                                uiPrefs.edit().putBoolean("library_sort_ascending", true).apply()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Ascending (A-Z)",
                            style = VeylTypography.BodySmall,
                            fontWeight = if (sortAscending) FontWeight.Bold else FontWeight.Medium,
                            color = if (sortAscending) Color.White else colors.textMuted
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!sortAscending) colors.surfacePanel else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sortAscending = false
                                uiPrefs.edit().putBoolean("library_sort_ascending", false).apply()
                            }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Descending (Z-A)",
                            style = VeylTypography.BodySmall,
                            fontWeight = if (!sortAscending) FontWeight.Bold else FontWeight.Medium,
                            color = if (!sortAscending) Color.White else colors.textMuted
                        )
                    }
                }

                // Sort Options List
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    SongSortOption.values().forEach { option ->
                        val isSelected = sortOption == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) colors.surfaceElevated else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                sortOption = option
                                uiPrefs.edit().putString("library_sort_option", option.name).apply()
                                showSortFilterSheet = false
                            }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = option.label,
                                style = VeylTypography.Body,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.accentSignal else colors.textPrimary,
                                fontSize = 14.sp
                            )
                            if (isSelected) {
                                Icon(
                                    imageVector = VeylIcons.Check,
                                    contentDescription = "Selected",
                                    tint = colors.accentSignal,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun EmptyStateNotice(message: String) {
    VeylEmptyState(
        icon = VeylIcons.MusicNote,
        title = message
    )
}

@Composable
private fun FastAlphabetScrubber(
    alphabet: List<String>,
    letterIndexMap: Map<String, Int>,
    onLetterSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    var activeLetter by remember { mutableStateOf<String?>(null) }
    var barHeightPx by remember { mutableStateOf(1) }

    Box(
        modifier = modifier
            .width(22.dp)
            .fillMaxHeight(0.72f)
            .onGloballyPositioned { coordinates ->
                barHeightPx = coordinates.size.height.coerceAtLeast(1)
            }
            .pointerInput(alphabet, letterIndexMap) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val letterIdx = ((down.position.y / barHeightPx) * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                    val letter = alphabet[letterIdx]
                    activeLetter = letter
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    letterIndexMap[letter]?.let { onLetterSelected(it) }

                    while (true) {
                        val event = awaitPointerEvent()
                        val current = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!current.pressed) break
                        current.consume()
                        val newIdx = ((current.position.y / barHeightPx) * alphabet.size).toInt().coerceIn(0, alphabet.size - 1)
                        val newLetter = alphabet[newIdx]
                        if (newLetter != activeLetter) {
                            activeLetter = newLetter
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            letterIndexMap[newLetter]?.let { onLetterSelected(it) }
                        }
                    }
                    activeLetter = null
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceEvenly,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            alphabet.forEach { letter ->
                val isSelected = activeLetter == letter
                Text(
                    text = letter,
                    style = VeylTypography.MonoSpec,
                    color = if (isSelected) colors.accentSignal else colors.textMuted,
                    fontSize = if (isSelected) 11.sp else 9.sp,
                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Bold
                )
            }
        }

        // Floating letter bubble indicator when actively scrubbing
        if (activeLetter != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(end = 40.dp),
                shape = CircleShape,
                color = colors.accentSignal,
                shadowElevation = 8.dp
            ) {
                Box(
                    modifier = Modifier.size(46.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = activeLetter!!,
                        style = VeylTypography.TitleMedium,
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SongListRow(
    track: TrackInfo,
    isPlaying: Boolean,
    isAnimating: Boolean = isPlaying,
    index: Int,
    onPlay: (Int) -> Unit,
    onOptions: (TrackInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = if (track.formatName.isNotBlank()) {
        "${track.artist} • ${track.formatName.uppercase()}"
    } else {
        track.artist
    }
    VeylListRow(
        title = track.title,
        subtitle = subtitle,
        isPlaying = isPlaying,
        isAnimating = isAnimating,
        durationSeconds = track.durationSeconds,
        onClick = { onPlay(index) },
        onOptionsClick = { onOptions(track) },
        modifier = modifier
    )
}

@Composable
private fun SongCompactRow(
    track: TrackInfo,
    isPlaying: Boolean,
    index: Int,
    onPlay: (Int) -> Unit,
    onOptions: (TrackInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(if (isPlaying) colors.surfaceElevated else Color.Transparent)
            .clickable { onPlay(index) }
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                style = VeylTypography.BodyMedium,
                color = if (isPlaying) colors.accentSignal else colors.textPrimary,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Medium,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = "${track.artist} • ${track.formatName} ${track.bitDepth ?: 16u}b/${(track.sampleRate / 1000u)}k"
            Text(
                text = sub,
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        val totalSec = track.durationSeconds.toInt()
        if (totalSec > 0) {
            val m = totalSec / 60
            val s = totalSec % 60
            val durationText = if (s < 10) "$m:0$s" else "$m:$s"
            Text(
                text = durationText,
                style = VeylTypography.MonoSpec,
                color = colors.textMuted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        Box(
            modifier = Modifier
                .padding(end = 10.dp)
                .size(32.dp)
                .clip(CircleShape)
                .clickable { onOptions(track) },
            contentAlignment = Alignment.Center
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

