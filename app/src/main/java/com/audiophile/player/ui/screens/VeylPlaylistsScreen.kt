package com.audiophile.player.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.ArtworkCache
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.engine.UserPlaylist
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylPlaylistsScreen(
    controller: AudioEngineController,
    onNavigateToPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val tracks by controller.tracks.collectAsStateWithLifecycle()
    val playlists by controller.playlists.collectAsStateWithLifecycle()
    val favoriteUris by controller.favoriteUris.collectAsStateWithLifecycle()
    val topTracks by controller.topTracks.collectAsStateWithLifecycle()
    val lastAddedTracks by controller.lastAddedTracks.collectAsStateWithLifecycle()
    val historyTracks by controller.playbackHistory.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistNameInput by remember { mutableStateOf("") }

    var playlistToRename by remember { mutableStateOf<UserPlaylist?>(null) }
    var renamePlaylistInput by remember { mutableStateOf("") }
    var playlistToDelete by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistToExport by remember { mutableStateOf<UserPlaylist?>(null) }

    var showSpotifyImportDialog by remember { mutableStateOf(false) }
    var spotifyUrlInput by remember { mutableStateOf("") }
    var isSpotifyImporting by remember { mutableStateOf(false) }
    var spotifyImportError by remember { mutableStateOf<String?>(null) }

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

    val filteredPlaylists = remember(playlists, searchQuery) {
        if (searchQuery.isBlank()) playlists
        else playlists.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // Header
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Playlists",
                            style = VeylTypography.DisplayLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${playlists.size + 4} collections",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }

                    // Create New Playlist quick button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.accentSignal,
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                newPlaylistNameInput = ""
                                showCreatePlaylistDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = VeylIcons.Plus,
                                contentDescription = "New Playlist",
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "New",
                                style = VeylTypography.BodySmall,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Actions row: File / M3U Import & Spotify Import
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Import M3U File
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfacePanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                importPlaylistLauncher.launch(arrayOf("audio/x-mpegurl", "audio/mpegurl", "*/*"))
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.Upload,
                                contentDescription = "Import File",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Import M3U",
                                style = VeylTypography.BodySmall,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Import Spotify Playlist
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfacePanel,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF1ED760).copy(alpha = 0.45f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                spotifyUrlInput = ""
                                spotifyImportError = null
                                showSpotifyImportDialog = true
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.Spotify,
                                contentDescription = "Import Spotify",
                                tint = Color(0xFF1ED760),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Spotify",
                                style = VeylTypography.BodySmall,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Section 1: Hero Featured Playlist - FAVORITES
        item {
            val favTracks = remember(favoriteUris, tracks) {
                favoriteUris.mapNotNull { controller.resolveTrackInfo(it) }.filter {
                    val fName = File(it.uri).name
                    !fName.startsWith(".") &&
                    !fName.contains("trashed", ignoreCase = true) &&
                    !it.title.contains("trashed", ignoreCase = true) &&
                    !fName.startsWith("veyl_online_stream_") &&
                    !it.uri.contains("/cache/")
                }
            }
            val favCoverUri = remember(favTracks) { favTracks.firstOrNull()?.uri ?: "" }

            Surface(
                shape = RoundedCornerShape(18.dp),
                color = colors.surfacePanel,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentSignal.copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable {
                        onNavigateToPlaylist("Favorites")
                    }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.size(62.dp)) {
                        if (favCoverUri.isNotBlank()) {
                            AsyncAlbumArt(
                                uri = favCoverUri,
                                contentDescription = "Favorites",
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 14.dp,
                                targetSizePx = 150
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        Brush.linearGradient(
                                            listOf(colors.accentSignal, colors.accentSignal.copy(alpha = 0.65f))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = VeylIcons.FavoriteFilled,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }
                        // Red heart badge on cover
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFE91E63),
                            modifier = Modifier
                                .size(22.dp)
                                .align(Alignment.BottomEnd)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = VeylIcons.FavoriteFilled,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Favorites",
                                style = VeylTypography.TitleMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = colors.accentSignal.copy(alpha = 0.15f),
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "PINNED",
                                    style = VeylTypography.MonoSpec,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = colors.accentSignal,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "${favTracks.size} loved tracks • Auto-updated",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }

                    // Direct 1-tap Play Favorites
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (favTracks.isNotEmpty()) {
                                controller.playTrackList(favTracks, startIndex = 0)
                            }
                        },
                        modifier = Modifier.size(42.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.accentSignal,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = VeylIcons.Play,
                                    contentDescription = "Play Favorites",
                                    tint = Color.Black,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section 2: Smart Collections Row (Top Tracks, Recently Added, History)
        item {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    text = "SMART COLLECTIONS",
                    style = VeylTypography.MonoBadge,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Card 1: Top Tracks
                    val topTracksCover = remember(topTracks) { topTracks.firstOrNull()?.uri ?: "" }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfacePanel,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToPlaylist("Top Tracks") }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                if (topTracksCover.isNotBlank()) {
                                    AsyncAlbumArt(
                                        uri = topTracksCover,
                                        contentDescription = "Top Tracks",
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 10.dp,
                                        targetSizePx = 140
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colors.surfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(VeylIcons.Equalizer, null, tint = colors.accentSignal, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Top Tracks",
                                style = VeylTypography.BodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${topTracks.size} tracks",
                                style = VeylTypography.MonoSpec,
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                        }
                    }

                    // Card 2: Recently Added
                    val lastAddedCover = remember(lastAddedTracks) { lastAddedTracks.firstOrNull()?.uri ?: "" }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfacePanel,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToPlaylist("Last added") }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                if (lastAddedCover.isNotBlank()) {
                                    AsyncAlbumArt(
                                        uri = lastAddedCover,
                                        contentDescription = "Recently Added",
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 10.dp,
                                        targetSizePx = 140
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colors.surfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(VeylIcons.QueueList, null, tint = colors.accentSignal, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "Recent Added",
                                style = VeylTypography.BodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${lastAddedTracks.size} tracks",
                                style = VeylTypography.MonoSpec,
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                        }
                    }

                    // Card 3: History
                    val historyCover = remember(historyTracks) { historyTracks.firstOrNull()?.uri ?: "" }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = colors.surfacePanel,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onNavigateToPlaylist("History") }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(68.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            ) {
                                if (historyCover.isNotBlank()) {
                                    AsyncAlbumArt(
                                        uri = historyCover,
                                        contentDescription = "History",
                                        modifier = Modifier.fillMaxSize(),
                                        cornerRadius = 10.dp,
                                        targetSizePx = 140
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(colors.surfaceElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(VeylIcons.Shuffle, null, tint = colors.accentSignal, modifier = Modifier.size(24.dp))
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "History",
                                style = VeylTypography.BodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${historyTracks.size} tracks",
                                style = VeylTypography.MonoSpec,
                                fontSize = 10.sp,
                                color = colors.textMuted
                            )
                        }
                    }
                }
            }
        }

        // Section 3: Custom Playlists Header
        item {
            Text(
                text = "MY PLAYLISTS (${filteredPlaylists.size})",
                style = VeylTypography.MonoBadge,
                color = colors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (filteredPlaylists.isEmpty()) {
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfacePanel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = VeylIcons.QueueList,
                            contentDescription = null,
                            tint = colors.textMuted,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "No custom playlists yet",
                            style = VeylTypography.TitleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Create a new playlist or import one from Spotify above!",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
        } else {
            items(filteredPlaylists, key = { it.id }) { pl ->
                var showPlaylistOptions by remember { mutableStateOf(false) }

                val playlistCoverUri: String = remember(pl, tracks) {
                    if (!pl.coverUrl.isNullOrBlank()) {
                        pl.coverUrl!!
                    } else {
                        val online = ArtworkCache.getOnlineArtwork(pl.id) ?: ArtworkCache.getOnlineArtwork(pl.name)
                        if (!online.isNullOrBlank()) {
                            online
                        } else {
                            val firstWithArt = pl.trackUris.firstOrNull { uri ->
                                val t = controller.resolveTrackInfo(uri)
                                t?.hasArtwork == true || ArtworkCache.getOnlineArtwork(uri) != null
                            } ?: pl.trackUris.firstOrNull() ?: ""
                            firstWithArt
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfacePanel)
                        .clickable {
                            onNavigateToPlaylist(pl.name)
                        }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Real Playlist Cover Artwork
                    Box(modifier = Modifier.size(54.dp)) {
                        if (playlistCoverUri.isNotBlank()) {
                            AsyncAlbumArt(
                                uri = playlistCoverUri,
                                contentDescription = pl.name,
                                modifier = Modifier.fillMaxSize(),
                                cornerRadius = 12.dp,
                                targetSizePx = 140
                            )
                        } else {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = colors.surfaceElevated,
                                modifier = Modifier.fillMaxSize(),
                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = VeylIcons.QueueList,
                                        contentDescription = null,
                                        tint = colors.accentSignal.copy(alpha = 0.7f),
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = pl.name,
                            style = VeylTypography.TitleMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${pl.trackUris.size} tracks",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }

                    // Quick Play Button
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val plTracks = pl.trackUris.mapNotNull { controller.resolveTrackInfo(it) }
                            if (plTracks.isNotEmpty()) {
                                controller.playTrackList(plTracks, startIndex = 0)
                            }
                        },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Play,
                            contentDescription = "Play Playlist",
                            tint = colors.textMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Box {
                        IconButton(
                            onClick = { showPlaylistOptions = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.MoreVert,
                                contentDescription = "Playlist Options",
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showPlaylistOptions,
                            onDismissRequest = { showPlaylistOptions = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                onClick = {
                                    showPlaylistOptions = false
                                    playlistToRename = pl
                                    renamePlaylistInput = pl.name
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Export (.m3u8)") },
                                onClick = {
                                    showPlaylistOptions = false
                                    playlistToExport = pl
                                    exportPlaylistLauncher.launch("${pl.name}.m3u8")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete", color = Color(0xFFEF4444)) },
                                onClick = {
                                    showPlaylistOptions = false
                                    playlistToDelete = pl
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialog: Create New Playlist
    if (showCreatePlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showCreatePlaylistDialog = false },
            title = { Text("Create Playlist", style = VeylTypography.TitleMedium) },
            text = {
                OutlinedTextField(
                    value = newPlaylistNameInput,
                    onValueChange = { newPlaylistNameInput = it },
                    placeholder = { Text("Playlist Name") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
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
                        if (trimmed.isNotEmpty()) {
                            controller.createPlaylist(trimmed)
                            showCreatePlaylistDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal)
                ) {
                    Text("Create", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreatePlaylistDialog = false }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Dialog: Rename Playlist
    if (playlistToRename != null) {
        val pl = playlistToRename!!
        AlertDialog(
            onDismissRequest = { playlistToRename = null },
            title = { Text("Rename Playlist", style = VeylTypography.TitleMedium) },
            text = {
                OutlinedTextField(
                    value = renamePlaylistInput,
                    onValueChange = { renamePlaylistInput = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = colors.accentSignal,
                        unfocusedBorderColor = colors.borderHairline
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = renamePlaylistInput.trim()
                        if (trimmed.isNotEmpty() && trimmed != pl.name) {
                            controller.renamePlaylist(pl.id, trimmed)
                        }
                        playlistToRename = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal)
                ) {
                    Text("Save", color = Color.Black)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToRename = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Dialog: Delete Playlist
    if (playlistToDelete != null) {
        val pl = playlistToDelete!!
        AlertDialog(
            onDismissRequest = { playlistToDelete = null },
            title = { Text("Delete Playlist", style = VeylTypography.TitleMedium) },
            text = { Text("Are you sure you want to delete \"${pl.name}\"? The songs will stay in your library.") },
            confirmButton = {
                Button(
                    onClick = {
                        controller.deletePlaylist(pl.id)
                        playlistToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444))
                ) {
                    Text("Delete", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { playlistToDelete = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Dialog: Spotify Import
    if (showSpotifyImportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isSpotifyImporting) {
                    showSpotifyImportDialog = false
                    spotifyImportError = null
                }
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = VeylIcons.Spotify,
                        contentDescription = null,
                        tint = Color(0xFF1ED760),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Import Spotify Playlist", style = VeylTypography.TitleMedium)
                }
            },
            text = {
                Column {
                    Text(
                        text = "Paste a Spotify playlist or album link. Any matching local files will be grouped immediately, and remaining tracks will stream online seamlessly.",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = spotifyUrlInput,
                        onValueChange = {
                            spotifyUrlInput = it
                            spotifyImportError = null
                        },
                        placeholder = { Text("https://open.spotify.com/playlist/...") },
                        singleLine = true,
                        enabled = !isSpotifyImporting,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFF1ED760),
                            unfocusedBorderColor = colors.borderHairline
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (isSpotifyImporting) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = "Fetching metadata and matching tracks...",
                            style = VeylTypography.BodySmall,
                            color = colors.accentSignal
                        )
                    }
                    if (spotifyImportError != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = spotifyImportError!!,
                            style = VeylTypography.BodySmall,
                            color = Color(0xFFEF4444)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = spotifyUrlInput.trim()
                        if (url.isNotBlank()) {
                            isSpotifyImporting = true
                            spotifyImportError = null
                            coroutineScope.launch {
                                val result = controller.importSpotifyPlaylist(url)
                                isSpotifyImporting = false
                                result.onSuccess { stats ->
                                    showSpotifyImportDialog = false
                                    Toast.makeText(
                                        context,
                                        "Imported \"${stats.playlist.name}\" (${stats.playlist.trackUris.size} tracks)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }.onFailure { err ->
                                    spotifyImportError = err.message ?: "Failed to import playlist"
                                }
                            }
                        }
                    },
                    enabled = !isSpotifyImporting && spotifyUrlInput.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1ED760))
                ) {
                    Text("Import", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showSpotifyImportDialog = false
                        spotifyImportError = null
                    },
                    enabled = !isSpotifyImporting
                ) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}
