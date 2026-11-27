package com.audiophile.player.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import com.audiophile.player.ui.components.VeylListRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import com.audiophile.player.ui.components.VeylActionButton
import com.audiophile.player.ui.components.VeylAlphabetFastScroller
import com.audiophile.player.ui.components.VeylEmptyState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.ArtworkCache
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.VeylTrackOptionsSheet
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.TrackInfo
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylPlaylistDetailScreen(
    controller: AudioEngineController,
    title: String = "Favorites",
    description: String = "The tracks I like the most",
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    BackHandler {
        onNavigateBack()
    }
    val allTracks by controller.tracks.collectAsStateWithLifecycle()
    val favoriteUris by controller.favoriteUris.collectAsStateWithLifecycle()
    val topTracks by controller.topTracks.collectAsStateWithLifecycle()
    val lastAddedTracks by controller.lastAddedTracks.collectAsStateWithLifecycle()
    val historyTracks by controller.playbackHistory.collectAsStateWithLifecycle()
    val playlists by controller.playlists.collectAsStateWithLifecycle()
    val artistGroups by controller.artistGroups.collectAsStateWithLifecycle()
    val albumGroups by controller.albumGroups.collectAsStateWithLifecycle()
    val currentUri by controller.currentUri.collectAsStateWithLifecycle()
    val playbackState by controller.playbackState.collectAsStateWithLifecycle()
    val isAudioPlaying = playbackState == uniffi.audiophile_core.PlaybackStateEnum.PLAYING

    val isArtistRoute = title.startsWith("artist:")
    val isAlbumRoute = title.startsWith("album:")
    val initialDisplayTitle = when {
        isArtistRoute -> title.removePrefix("artist:")
        isAlbumRoute -> title.removePrefix("album:")
        else -> title
    }

    var currentPlaylistTitle by remember(title) { mutableStateOf(initialDisplayTitle) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var renameInput by remember { mutableStateOf("") }

    val customPlaylist = if (!isArtistRoute && !isAlbumRoute) {
        playlists.find { it.name.equals(currentPlaylistTitle, ignoreCase = true) }
    } else null
    val isCustomPlaylist = customPlaylist != null && currentPlaylistTitle !in listOf("Favorites", "Top Tracks", "Last added", "History")

    val playlistTracks = remember(
        title,
        currentPlaylistTitle,
        favoriteUris,
        topTracks,
        lastAddedTracks,
        historyTracks,
        playlists,
        artistGroups,
        albumGroups,
        allTracks
    ) {
        when {
            isArtistRoute -> {
                artistGroups.find { it.first.equals(currentPlaylistTitle, ignoreCase = true) }?.second
                    ?: allTracks.filter { track ->
                        (track.artist ?: "Unknown Artist").equals(currentPlaylistTitle, ignoreCase = true)
                    }
            }
            isAlbumRoute -> {
                albumGroups.find { it.first.equals(currentPlaylistTitle, ignoreCase = true) }?.third
                    ?: allTracks.filter { track ->
                        (track.album?.takeIf { it.isNotBlank() } ?: "Unknown Album").equals(currentPlaylistTitle, ignoreCase = true)
                    }
            }
            currentPlaylistTitle == "Favorites" -> favoriteUris.mapNotNull { controller.resolveTrackInfo(it) }.filter {
                val fName = File(it.uri).name
                !fName.startsWith(".") &&
                !fName.contains("trashed", ignoreCase = true) &&
                !it.title.contains("trashed", ignoreCase = true) &&
                !fName.startsWith("veyl_online_stream_") &&
                !it.uri.contains("/cache/")
            }.distinctBy {
                controller.onlineStreamManager.getTargetStorageFileForTrack(it).absolutePath
            }.sortedBy { it.title.trim().lowercase() }
            currentPlaylistTitle == "Top Tracks" -> if (topTracks.isNotEmpty()) topTracks else allTracks
            currentPlaylistTitle == "Last added" -> if (lastAddedTracks.isNotEmpty()) lastAddedTracks else allTracks
            currentPlaylistTitle == "History" -> historyTracks
            else -> {
                if (customPlaylist != null) {
                    customPlaylist.trackUris.mapNotNull { controller.resolveTrackInfo(it) }.filter {
                        val fName = File(it.uri).name
                        !fName.startsWith(".") &&
                        !fName.contains("trashed", ignoreCase = true) &&
                        !it.title.contains("trashed", ignoreCase = true) &&
                        !fName.startsWith("veyl_online_stream_") &&
                        !it.uri.contains("/cache/")
                    }
                } else {
                    allTracks
                }
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("audio/x-mpegurl")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    controller.exportPlaylist(currentPlaylistTitle, playlistTracks, os)
                }
                Toast.makeText(context, "Exported \"$currentPlaylistTitle\" (${playlistTracks.size} tracks)", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Failed to export playlist: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val (totalMin, totalSec) = remember(playlistTracks) {
        val totalDurationSec = playlistTracks.sumOf { it.durationSeconds }
        (totalDurationSec / 60).toInt() to (totalDurationSec % 60).toInt()
    }

    val listState = rememberLazyListState()
    val durationLabel = if (totalMin >= 60) {
        "${totalMin / 60} hr ${totalMin % 60} min"
    } else if (totalMin > 0) {
        "$totalMin min $totalSec sec"
    } else {
        "$totalSec sec"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Pinned Minimal Header with crafted tactile action buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 10.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            VeylActionButton(
                icon = VeylIcons.ChevronLeft,
                contentDescription = "Back",
                onClick = onNavigateBack,
                iconSizeDp = 22
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    text = currentPlaylistTitle,
                    style = VeylTypography.TitleLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${playlistTracks.size} tracks • $durationLabel",
                    style = VeylTypography.BodySmall,
                    color = colors.textMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                VeylActionButton(
                    icon = VeylIcons.Shuffle,
                    contentDescription = "Shuffle",
                    onClick = {
                        if (playlistTracks.isNotEmpty()) {
                            controller.toggleShuffle()
                            controller.playTrackList(playlistTracks.shuffled(), startIndex = 0)
                        }
                    }
                )

                VeylActionButton(
                    icon = VeylIcons.Play,
                    contentDescription = "Play All",
                    isPrimary = true,
                    onClick = {
                        if (playlistTracks.isNotEmpty()) {
                            controller.playTrackList(playlistTracks, startIndex = 0)
                        }
                    }
                )

                // Overflow options menu (Export .m3u8, Rename, Delete)
                Box {
                    VeylActionButton(
                        icon = VeylIcons.MoreVert,
                        contentDescription = "Options",
                        onClick = { showMenu = true }
                    )

                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        modifier = Modifier.background(colors.surfacePanel)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Export Playlist (.m3u8)", color = colors.textPrimary) },
                            leadingIcon = {
                                Icon(
                                    imageVector = VeylIcons.Download,
                                    contentDescription = null,
                                    tint = colors.accentSignal,
                                    modifier = Modifier.size(20.dp)
                                )
                            },
                            onClick = {
                                showMenu = false
                                val safeFilename = "${currentPlaylistTitle.replace(Regex("[^a-zA-Z0-9._ -]"), "_")}.m3u8"
                                exportLauncher.launch(safeFilename)
                            }
                        )

                        if (isCustomPlaylist) {
                            DropdownMenuItem(
                                text = { Text("Rename Playlist", color = colors.textPrimary) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = VeylIcons.Edit,
                                        contentDescription = null,
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    renameInput = currentPlaylistTitle
                                    showRenameDialog = true
                                }
                            )

                            DropdownMenuItem(
                                text = { Text("Delete Playlist", color = colors.accentPeak) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = VeylIcons.Trash,
                                        contentDescription = null,
                                        tint = colors.accentPeak,
                                        modifier = Modifier.size(20.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showDeleteDialog = true
                                }
                            )
                        }
                    }
                }
            }
        }

        androidx.compose.material3.HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.borderHairline.copy(alpha = 0.25f)
        )

        // Track list container: starts at y=0 directly under the header so the A-Z slider aligns cleanly
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 120.dp)
            ) {
                if (playlistTracks.isEmpty()) {
                    item {
                        VeylEmptyState(
                            icon = VeylIcons.QueueList,
                            title = "No tracks in this list yet",
                            subtitle = "Add songs from your library or import an M3U8 playlist."
                        )
                    }
                } else {
                    itemsIndexed(
                        items = playlistTracks,
                        key = { index, track -> "${track.uri}_$index" },
                        contentType = { _, _ -> "playlist_track_item" }
                    ) { index, track ->
                        val isPlayingThis = currentUri == track.uri

                        PlaylistTrackRow(
                            track = track,
                            index = index,
                            isPlaying = isPlayingThis,
                            isAnimating = isPlayingThis && isAudioPlaying,
                            onPlay = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                controller.playTrackList(playlistTracks, startIndex = index)
                            },
                            onOptions = {
                                selectedTrackForOptions = track
                            },
                            modifier = Modifier.animateItem()
                        )
                    }
                }
            }

            VeylAlphabetFastScroller(
                items = playlistTracks.map { it.title },
                listState = listState,
                headerItemCount = 0,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
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

    // Rename Playlist Dialog
    if (showRenameDialog && customPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = colors.surfacePanel,
            title = {
                Text(
                    text = "Rename Playlist",
                    style = VeylTypography.SectionHeader,
                    color = colors.textPrimary
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInput,
                    onValueChange = { renameInput = it },
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
                        val trimmed = renameInput.trim()
                        if (trimmed.isNotBlank()) {
                            controller.renamePlaylist(customPlaylist.id, trimmed)
                            currentPlaylistTitle = trimmed
                            showRenameDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal)
                ) {
                    Text("Rename", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Delete Playlist Dialog
    if (showDeleteDialog && customPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = colors.surfacePanel,
            title = {
                Text(
                    text = "Delete Playlist",
                    style = VeylTypography.SectionHeader,
                    color = colors.textPrimary
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to delete \"$currentPlaylistTitle\"? The audio files will remain on your device.",
                    style = VeylTypography.Body,
                    color = colors.textSecondary
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        controller.deletePlaylist(customPlaylist.id)
                        showDeleteDialog = false
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentPeak)
                ) {
                    Text("Delete", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}

@Composable
private fun PlaylistTrackRow(
    track: TrackInfo,
    index: Int,
    isPlaying: Boolean,
    isAnimating: Boolean = isPlaying,
    onPlay: () -> Unit,
    onOptions: () -> Unit,
    modifier: Modifier = Modifier
) {
    val subtitle = if (track.formatName.isNotBlank()) {
        "${track.artist ?: "Unknown Artist"} • ${track.formatName.uppercase()}"
    } else {
        track.artist ?: "Unknown Artist"
    }
    VeylListRow(
        title = track.title,
        subtitle = subtitle,
        isPlaying = isPlaying,
        isAnimating = isAnimating,
        durationSeconds = track.durationSeconds,
        onClick = onPlay,
        onOptionsClick = onOptions,
        modifier = modifier
    )
}
