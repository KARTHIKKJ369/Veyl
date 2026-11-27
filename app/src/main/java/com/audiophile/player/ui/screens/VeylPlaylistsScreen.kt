package com.audiophile.player.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import com.audiophile.player.ui.components.VeylActionButton
import com.audiophile.player.ui.components.VeylEmptyState
import com.audiophile.player.ui.components.VeylListRow
import com.audiophile.player.ui.components.VeylScreenHeader
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

    var showM3uScannerDialog by remember { mutableStateOf(false) }
    var isScanningM3u by remember { mutableStateOf(false) }
    var discoveredM3uFiles by remember { mutableStateOf<List<M3uCandidate>>(emptyList()) }

    fun scanDeviceForPlaylists() {
        isScanningM3u = true
        coroutineScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val found = linkedMapOf<String, M3uCandidate>()

            fun addFileCandidate(file: File) {
                val lower = file.name.lowercase()
                if ((lower.endsWith(".m3u8") || lower.endsWith(".m3u")) && file.canRead()) {
                    val key = file.absolutePath.lowercase()
                    found.putIfAbsent(
                        key,
                        M3uCandidate(
                            name = file.name,
                            folder = file.parentFile?.name ?: "Storage",
                            modifiedMs = file.lastModified(),
                            file = file,
                            key = key
                        )
                    )
                }
            }

            fun scanDir(dir: File?, depth: Int) {
                if (dir == null || !dir.exists() || !dir.isDirectory || depth > 5) return
                val name = dir.name
                if (name.startsWith(".") || name == "Android") return
                dir.listFiles()?.forEach { child ->
                    if (child.isDirectory) {
                        scanDir(child, depth + 1)
                    } else if (child.isFile) {
                        addFileCandidate(child)
                    }
                }
            }

            // 1. Query MediaStore (Downloads & Files) so Download/ .m3u8 files appear even under Android 11+ scoped storage
            val mediaUris = buildList {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    add(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI)
                }
                add(android.provider.MediaStore.Files.getContentUri("external"))
            }
            val projection = arrayOf(
                android.provider.MediaStore.MediaColumns._ID,
                android.provider.MediaStore.MediaColumns.DISPLAY_NAME,
                android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                android.provider.MediaStore.MediaColumns.DATE_MODIFIED,
                android.provider.MediaStore.MediaColumns.DATA
            )
            mediaUris.forEach { tableUri ->
                try {
                    context.contentResolver.query(
                        tableUri,
                        projection,
                        "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME} LIKE ?",
                        arrayOf("%.m3u%"),
                        "${android.provider.MediaStore.MediaColumns.DATE_MODIFIED} DESC"
                    )?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.MediaColumns._ID)
                        val nameCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DISPLAY_NAME)
                        val relCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.RELATIVE_PATH)
                        val dateCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATE_MODIFIED)
                        val dataCol = cursor.getColumnIndex(android.provider.MediaStore.MediaColumns.DATA)

                        while (cursor.moveToNext()) {
                            val dispName = if (nameCol != -1) cursor.getString(nameCol) ?: continue else continue
                            val lower = dispName.lowercase()
                            if (!lower.endsWith(".m3u8") && !lower.endsWith(".m3u")) continue

                            val dataPath = if (dataCol != -1) cursor.getString(dataCol) else null
                            val id = cursor.getLong(idCol)
                            val contentUri = android.content.ContentUris.withAppendedId(tableUri, id)
                            val relPath = if (relCol != -1) cursor.getString(relCol)?.trimEnd('/') else null
                            val modMs = if (dateCol != -1) cursor.getLong(dateCol) * 1000L else 0L
                            val fileObj = dataPath?.let(::File)?.takeIf { it.exists() && it.canRead() }
                            val key = (dataPath ?: "$relPath/$dispName").lowercase()
                            val folderLabel = relPath?.substringAfterLast('/')?.ifBlank { "Downloads" }
                                ?: fileObj?.parentFile?.name
                                ?: "Downloads"

                            found.putIfAbsent(
                                key,
                                M3uCandidate(
                                    name = dispName,
                                    folder = folderLabel,
                                    modifiedMs = modMs,
                                    file = fileObj,
                                    contentUri = contentUri,
                                    key = key
                                )
                            )
                        }
                    }
                } catch (_: Exception) {
                }
            }

            // 2. Direct filesystem scan across Download, Music, Documents, Playlists, and root
            val root = android.os.Environment.getExternalStorageDirectory()
            listOf(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS),
                File(root, "Download"),
                File(root, "Downloads"),
                File(root, "Music"),
                File(root, "Documents"),
                File(root, "Playlists"),
                root
            ).forEach { scanDir(it, 0) }

            val list = found.values.sortedByDescending { it.modifiedMs }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                discoveredM3uFiles = list
                isScanningM3u = false
            }
        }
    }

    val importPlaylistLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val uri: Uri? = activityResult.data?.data
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Unified Screen Header with crafted tactile action buttons (no duplicate pills)
        VeylScreenHeader(
            title = "Playlists",
            subtitle = "${playlists.size + 4} collections"
        ) {
            VeylActionButton(
                icon = VeylIcons.Upload,
                contentDescription = "Import M3U Playlist",
                onClick = {
                    showM3uScannerDialog = true
                    scanDeviceForPlaylists()
                }
            )

            VeylActionButton(
                icon = VeylIcons.Spotify,
                contentDescription = "Import Spotify Playlist",
                accentColor = Color(0xFF1ED760),
                onClick = {
                    spotifyUrlInput = ""
                    spotifyImportError = null
                    showSpotifyImportDialog = true
                }
            )

            VeylActionButton(
                icon = VeylIcons.Plus,
                contentDescription = "New Playlist",
                isPrimary = true,
                onClick = {
                    newPlaylistNameInput = ""
                    showCreatePlaylistDialog = true
                }
            )
        }

        androidx.compose.material3.HorizontalDivider(
            thickness = 0.5.dp,
            color = colors.borderHairline.copy(alpha = 0.2f)
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
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
                VeylListRow(
                    title = "Favorites",
                    subtitle = "${favTracks.size} loved tracks • Auto-updated",
                    onClick = { onNavigateToPlaylist("Favorites") },
                    trailingContent = {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (favTracks.isNotEmpty()) {
                                    controller.playTrackList(favTracks, startIndex = 0)
                                }
                            },
                            modifier = Modifier.size(44.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.Play,
                                contentDescription = "Play Favorites",
                                tint = colors.textMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                )
            }

            // Section 2: Smart Collections (Name-Only Unified Rows)
            item {
                Text(
                    text = "SMART COLLECTIONS",
                    style = VeylTypography.MonoBadge,
                    color = colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            item {
                VeylListRow(
                    title = "Top Tracks",
                    subtitle = "Most played • ${topTracks.size} tracks",
                    onClick = { onNavigateToPlaylist("Top Tracks") }
                )
                VeylListRow(
                    title = "Recent Added",
                    subtitle = "Latest additions • ${lastAddedTracks.size} tracks",
                    onClick = { onNavigateToPlaylist("Last added") }
                )
                VeylListRow(
                    title = "History",
                    subtitle = "Recently played • ${historyTracks.size} tracks",
                    onClick = { onNavigateToPlaylist("History") }
                )
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
                    VeylEmptyState(
                        icon = VeylIcons.QueueList,
                        title = "No custom playlists yet",
                        subtitle = "Create a new playlist or import an M3U8 / Spotify list above.",
                        actionLabel = "New Playlist",
                        onAction = {
                            newPlaylistNameInput = ""
                            showCreatePlaylistDialog = true
                        }
                    )
                }
            } else {
                items(filteredPlaylists, key = { it.id }) { pl ->
                    var showPlaylistOptions by remember { mutableStateOf(false) }

                    VeylListRow(
                        title = pl.name,
                        subtitle = "${pl.trackUris.size} tracks",
                        onClick = { onNavigateToPlaylist(pl.name) },
                        modifier = Modifier.animateItem(),
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val plTracks = pl.trackUris.mapNotNull { controller.resolveTrackInfo(it) }
                                    if (plTracks.isNotEmpty()) {
                                        controller.playTrackList(plTracks, startIndex = 0)
                                    }
                                },
                                modifier = Modifier.size(44.dp)
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
                                    modifier = Modifier.size(44.dp)
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
                    )
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

    // Dialog: In-App .m3u8 / .m3u Playlist Scanner + System Picker Fallback
    if (showM3uScannerDialog) {
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner, showM3uScannerDialog) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    scanDeviceForPlaylists()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        val needsAllFilesGrant = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R &&
            !android.os.Environment.isExternalStorageManager()

        AlertDialog(
            onDismissRequest = { showM3uScannerDialog = false },
            containerColor = colors.surfacePanel,
            title = {
                Text(
                    text = "Import Playlist (.m3u8 / .m3u)",
                    style = VeylTypography.TitleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (needsAllFilesGrant) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    try {
                                        val intent = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                        val fallback = android.content.Intent(
                                            android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                        )
                                        context.startActivity(fallback)
                                    }
                                }
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Grant All Files Access to scan Downloads",
                                style = VeylTypography.BodySmall,
                                color = colors.accentSignal,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "Allow",
                                style = VeylTypography.BodySmall,
                                color = colors.accentSignal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    if (isScanningM3u) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(vertical = 12.dp)
                        ) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = colors.accentSignal
                            )
                            Text(
                                text = "Scanning storage for .m3u8 & .m3u files…",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted
                            )
                        }
                    } else if (discoveredM3uFiles.isEmpty()) {
                        Text(
                            text = "No .m3u8 or .m3u files found automatically in Download, Music, or Documents.",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        Text(
                            text = "Tap a playlist file to import:",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            items(discoveredM3uFiles, key = { it.key }) { candidate ->
                                com.audiophile.player.ui.components.VeylListRow(
                                    title = candidate.name,
                                    subtitle = candidate.folder,
                                    onClick = {
                                        try {
                                            val stream = candidate.file?.inputStream()
                                                ?: candidate.contentUri?.let { context.contentResolver.openInputStream(it) }
                                            if (stream == null) {
                                                Toast.makeText(context, "Unable to open file", Toast.LENGTH_SHORT).show()
                                                return@VeylListRow
                                            }
                                            val defaultTitle = candidate.name.substringBeforeLast(".")
                                            stream.use { ins ->
                                                val result = controller.importPlaylist(ins, defaultTitle, tracks)
                                                result.onSuccess { pl ->
                                                    showM3uScannerDialog = false
                                                    Toast.makeText(
                                                        context,
                                                        "Imported \"${pl.name}\" (${pl.trackUris.size} tracks)",
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }.onFailure { err ->
                                                    Toast.makeText(context, "Import failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showM3uScannerDialog = false
                        val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                            addCategory(android.content.Intent.CATEGORY_OPENABLE)
                            type = "*/*"
                        }
                        importPlaylistLauncher.launch(android.content.Intent.createChooser(intent, "Browse Files"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal)
                ) {
                    Text("Browse Files", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showM3uScannerDialog = false }) {
                    Text("Close", color = colors.textMuted)
                }
            }
        )
    }
}

private data class M3uCandidate(
    val name: String,
    val folder: String,
    val modifiedMs: Long,
    val file: File? = null,
    val contentUri: Uri? = null,
    val key: String
)
