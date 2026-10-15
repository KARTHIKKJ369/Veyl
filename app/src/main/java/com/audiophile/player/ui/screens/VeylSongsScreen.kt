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
    val playlists by controller.playlists.collectAsStateWithLifecycle()
    val currentUri by controller.currentUri.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf("All") }
    var viewLayout by remember { mutableStateOf(SongViewLayout.LIST) }
    var sortOption by remember { mutableStateOf(SongSortOption.DEFAULT) }
    var sortAscending by remember { mutableStateOf(true) }
    var showSortFilterSheet by remember { mutableStateOf(false) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }
    var expandedArtist by remember { mutableStateOf<String?>(null) }
    var expandedAlbum by remember { mutableStateOf<String?>(null) }

    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }
    var playlistToExport by remember { mutableStateOf<UserPlaylist?>(null) }

    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                var fileName: String? = null
                try {
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) fileName = cursor.getString(idx)
                        }
                    }
                } catch (_: Exception) {}

                val defaultName = fileName?.substringBeforeLast(".") ?: "Imported Playlist"
                context.contentResolver.openInputStream(uri)?.use { ins ->
                    val result = controller.importPlaylist(ins, defaultName, tracks)
                    result.onSuccess { pl ->
                        Toast.makeText(context, "Imported playlist \"${pl.name}\" (${pl.trackUris.size} tracks)", Toast.LENGTH_SHORT).show()
                    }.onFailure { err ->
                        Toast.makeText(context, "Import failed: ${err.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Error importing: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val exportPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri: Uri? ->
        if (uri != null && playlistToExport != null) {
            try {
                val pl = playlistToExport!!
                val resolvedTracks = pl.trackUris.mapNotNull { controller.resolveTrackInfo(it) }
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    controller.exportPlaylist(pl.name, resolvedTracks, os)
                }
                Toast.makeText(context, "Exported \"${pl.name}\" (${resolvedTracks.size} tracks)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export playlist: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally {
                playlistToExport = null
            }
        }
    }

    val tabs = listOf("All", "Favorites", "Artists", "Albums", "Playlists")

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
            SongSortOption.DEFAULT -> base
            SongSortOption.TITLE -> base.sortedBy { it.title.lowercase() }
            SongSortOption.ARTIST -> base.sortedBy { (it.artist ?: "").lowercase() }
            SongSortOption.ALBUM -> base.sortedBy { (it.album ?: "").lowercase() }
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
        // 1. Pinned Top Header: "Library" & Action Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Library",
                    style = VeylTypography.TitleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp
                )
                Text(
                    text = if (isScanning) "Refreshing library…" else "Your local collection",
                    style = VeylTypography.BodySmall,
                    color = colors.textMuted,
                    fontSize = 13.sp
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Search Button (1 click to open full search & keyboard)
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surfacePanel)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToSearch()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = VeylIcons.Search,
                        contentDescription = "Search Library",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Refresh / Rescan Library Button
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(colors.surfacePanel)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.rescanLibrary()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (isScanning) {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = colors.accentSignal,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = VeylIcons.Refresh,
                            contentDescription = "Refresh Library",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
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
                        contentDescription = "Choose Music Folder",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 2. Pinned Category Filter Tabs (All, Favorites, Artists, Albums, Playlists)
        val tabsScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabsScrollState)
                .padding(horizontal = 16.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
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

        // 3. Pinned Subheader with View Layout Switcher (Grid vs List vs Sort)
        if (selectedTab in listOf("All", "Favorites", "Artists", "Albums")) {
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
                    .padding(horizontal = 16.dp, vertical = 6.dp),
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

                    // Sliders / Sort & Filter Action
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showSortFilterSheet = true
                        },
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (showSortFilterSheet || sortOption != SongSortOption.DEFAULT) colors.surfaceElevated else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Sliders,
                            contentDescription = "Sort and Filter",
                            tint = if (sortOption != SongSortOption.DEFAULT) colors.accentSignal else colors.textMuted,
                            modifier = Modifier.size(16.dp)
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
                                val isExpanded = expandedArtist == artistName
                                val firstTrack = artistTracks.firstOrNull()
                                val firstUri = firstTrack?.uri ?: ""

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
                                                hasArtwork = firstTrack?.hasArtwork,
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
                                val isExpanded = expandedAlbum == albumTitle
                                val firstTrack = albumTracks.firstOrNull()
                                val firstUri = firstTrack?.uri ?: ""

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
                                                hasArtwork = firstTrack?.hasArtwork,
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
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                // Create New Playlist Button
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.accentSignal,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            newPlaylistNameInput = ""
                                            showCreatePlaylistDialog = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = VeylIcons.Plus,
                                            contentDescription = "New Playlist",
                                            tint = Color.Black,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "New Playlist",
                                            style = VeylTypography.BodySmall,
                                            color = Color.Black,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                // Import Playlist Button
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = colors.surfaceElevated,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(46.dp)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            importPlaylistLauncher.launch(
                                                arrayOf("*/*", "audio/x-mpegurl", "audio/mpegurl", "application/json", "text/plain")
                                            )
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = VeylIcons.Upload,
                                            contentDescription = "Import",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Import Playlist",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }

                        if (playlists.isEmpty()) {
                            item {
                                EmptyStateNotice(message = "No playlists created yet. Create a new one or import an .m3u8 playlist!")
                            }
                        } else {
                            items(playlists) { pl ->
                                var showPlaylistOptions by remember { mutableStateOf(false) }

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

                                    Box {
                                        IconButton(
                                            onClick = { showPlaylistOptions = true },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = VeylIcons.MoreVert,
                                                contentDescription = "Options",
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }

                                        DropdownMenu(
                                            expanded = showPlaylistOptions,
                                            onDismissRequest = { showPlaylistOptions = false },
                                            modifier = Modifier.background(colors.surfacePanel)
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Export (.m3u8)", color = colors.textPrimary) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = VeylIcons.Download,
                                                        contentDescription = null,
                                                        tint = colors.accentSignal,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showPlaylistOptions = false
                                                    playlistToExport = pl
                                                    val safeFilename = "${pl.name.replace(Regex("[^a-zA-Z0-9._ -]"), "_")}.m3u8"
                                                    exportPlaylistLauncher.launch(safeFilename)
                                                }
                                            )

                                            DropdownMenuItem(
                                                text = { Text("Delete", color = colors.accentPeak) },
                                                leadingIcon = {
                                                    Icon(
                                                        imageVector = VeylIcons.Trash,
                                                        contentDescription = null,
                                                        tint = colors.accentPeak,
                                                        modifier = Modifier.size(20.dp)
                                                    )
                                                },
                                                onClick = {
                                                    showPlaylistOptions = false
                                                    controller.deletePlaylist(pl.id)
                                                    Toast.makeText(context, "Deleted \"${pl.name}\"", Toast.LENGTH_SHORT).show()
                                                }
                                            )
                                        }
                                    }
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

            // Floating Fast Alphabet Scrubber Bar (Single non-blocking gesture detector)
            if (selectedTab == "All" && filteredTracks.size > 8 && viewLayout != SongViewLayout.GRID) {
                FastAlphabetScrubber(
                    alphabet = alphabet,
                    letterIndexMap = letterIndexMap,
                    onLetterSelected = { targetIdx ->
                        coroutineScope.launch {
                            listState.scrollToItem(targetIdx)
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 2.dp)
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

    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            containerColor = colors.surfacePanel,
            title = {
                Text(
                    text = "New Playlist",
                    style = VeylTypography.SectionHeader,
                    color = colors.textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = newPlaylistNameInput,
                    onValueChange = { newPlaylistNameInput = it },
                    placeholder = { Text("Playlist Name", color = colors.textMuted) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = colors.textPrimary,
                        unfocusedTextColor = colors.textPrimary,
                        focusedBorderColor = colors.accentSignal,
                        unfocusedBorderColor = colors.borderHairline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPlaylistNameInput.trim()
                        if (trimmed.isNotBlank()) {
                            val pl = controller.createPlaylist(trimmed)
                            showCreatePlaylistDialog = false
                            onNavigateToPlaylist?.invoke(pl.name)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal)
                ) {
                    Text("Create", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
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
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Track Name & Artist (Clean, immediate text - no music icon, no box)
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                style = VeylTypography.BodyMedium,
                color = if (isPlaying) colors.accentSignal else colors.textPrimary,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = if (track.formatName.isNotBlank()) {
                "${track.artist} • ${track.formatName.uppercase()}"
            } else {
                track.artist
            }
            Text(
                text = subtitle,
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Duration telemetry (fast math formatting without String.format overhead)
        val totalSec = track.durationSeconds.toInt()
        if (totalSec > 0) {
            val m = totalSec / 60
            val s = totalSec % 60
            val durationText = if (s < 10) "$m:0$s" else "$m:$s"
            Text(
                text = durationText,
                style = VeylTypography.MonoSpec,
                color = colors.textMuted,
                fontSize = 11.5.sp,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
        }

        // More Options
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable { onOptions(track) },
            contentAlignment = Alignment.Center
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

