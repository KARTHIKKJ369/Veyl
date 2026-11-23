package com.audiophile.player.ui.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import com.audiophile.player.engine.ArtworkCache
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.online.AudioFileTagger
import com.audiophile.player.online.OnlineTrackIdentifier
import com.audiophile.player.online.TrackMetadataCandidate
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.audiophile_core.TrackInfo
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylIdentifyTrackSheet(
    track: TrackInfo,
    controller: AudioEngineController,
    onDismiss: () -> Unit
) {
    val colors = LocalVeylColors.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val targetFile = remember(track.uri) { File(track.uri.removePrefix("file://")) }

    // Start with empty search input as requested by user - let user type clean search query
    var searchQuery by remember { mutableStateOf("") }
    var candidates by remember { mutableStateOf<List<TrackMetadataCandidate>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isSaving by remember { mutableStateOf(false) }
    var savingCandidateTitle by remember { mutableStateOf("") }
    var hasSearchedOnce by remember { mutableStateOf(false) }

    val hasAllFilesAccess = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    fun performSearch(queryToSearch: String) {
        val q = queryToSearch.trim()
        if (q.length < 2) return
        focusManager.clearFocus()
        isSearching = true
        coroutineScope.launch {
            try {
                val results = OnlineTrackIdentifier.searchCandidates(q)
                candidates = results
            } catch (_: Exception) {
                candidates = emptyList()
            } finally {
                isSearching = false
                hasSearchedOnce = true
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = sheetState,
        containerColor = colors.surfacePanel,
        scrimColor = Color.Black.copy(alpha = 0.70f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Identify & Match Track",
                        style = VeylTypography.TitleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary
                    )
                    Text(
                        text = "Renames file and embeds studio metadata & album art",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted,
                        fontSize = 12.sp
                    )
                }

                IconButton(
                    onClick = { if (!isSaving) onDismiss() },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.Close,
                        contentDescription = "Close",
                        tint = colors.textMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // File path and status badge
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceElevated,
                border = BorderStroke(1.dp, colors.borderHairline),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.AudioFile,
                        contentDescription = null,
                        tint = colors.accentSignal,
                        modifier = Modifier.size(16.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = targetFile.name,
                            style = VeylTypography.MonoSpec,
                            fontSize = 11.sp,
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${track.formatName} • ${targetFile.parent ?: ""}",
                            style = VeylTypography.MonoSpec,
                            fontSize = 10.sp,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Storage Permission Warning Banner if on Android 11+ and not yet granted
            if (!hasAllFilesAccess) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFFFF9800).copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    context.startActivity(intent)
                                }
                            }
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Permission Needed to Rename Files",
                                style = VeylTypography.SectionHeader,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFFFB74D)
                            )
                            Text(
                                text = "Tap here to allow 'All files access' in Settings so Veyl can edit tags and rename files on your storage.",
                                style = VeylTypography.BodySmall,
                                fontSize = 11.sp,
                                color = colors.textPrimary
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFF9800),
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Text(
                                text = "Settings",
                                style = VeylTypography.MonoBadge.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                                color = Color.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Search Bar (Starts empty for user input)
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "Type song name, movie or artist to search...",
                        color = colors.textMuted,
                        style = VeylTypography.BodySmall
                    )
                },
                singleLine = true,
                leadingIcon = {
                    Icon(
                        imageVector = VeylIcons.Search,
                        contentDescription = "Search",
                        tint = colors.accentSignal,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                candidates = emptyList()
                                hasSearchedOnce = false
                            }
                        ) {
                            Icon(
                                imageVector = VeylIcons.Close,
                                contentDescription = "Clear",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { performSearch(searchQuery) }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentSignal,
                    unfocusedBorderColor = colors.borderHairline,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentSignal
                )
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Search Trigger Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = { performSearch(searchQuery) },
                    enabled = searchQuery.trim().length >= 2 && !isSearching && !isSaving,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.surfaceElevated,
                        contentColor = colors.accentSignal
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = if (isSearching) "Searching\u2026" else "Search",
                        style = VeylTypography.MonoSpec,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Saving In-Progress Overlay Banner
            AnimatedVisibility(
                visible = isSaving,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = colors.accentSignal.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, colors.accentSignal.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = colors.accentSignal,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                        Column {
                            Text(
                                text = "Tagging and renaming on storage\u2026",
                                style = VeylTypography.SectionHeader,
                                fontSize = 13.sp,
                                color = colors.accentSignal,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Writing metadata & embedding artwork into file",
                                style = VeylTypography.BodySmall,
                                fontSize = 11.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }
            }

            // Results List / Loading / Empty State
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .height(360.dp)
            ) {
                when {
                    isSearching -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                color = colors.accentSignal,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Searching JioSaavn & Apple Music catalogs\u2026",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                    candidates.isEmpty() && hasSearchedOnce -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = VeylIcons.Search,
                                contentDescription = null,
                                tint = colors.textMuted,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No studio matches found",
                                style = VeylTypography.TitleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Try typing the artist or movie name above.",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                    candidates.isEmpty() && !hasSearchedOnce -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = VeylIcons.Search,
                                contentDescription = null,
                                tint = colors.textMuted.copy(alpha = 0.5f),
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Enter song, movie or artist name above",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(candidates, key = { it.id }) { candidate ->
                                CandidateCard(
                                    candidate = candidate,
                                    isSaving = isSaving,
                                    onSelect = {
                                        if (isSaving) return@CandidateCard
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        isSaving = true
                                        savingCandidateTitle = candidate.title

                                        coroutineScope.launch(Dispatchers.IO) {
                                            try {
                                                // 1. Pause briefly and record position if currently playing this exact file
                                                val isPlayingThis = (controller.currentUri.value == track.uri ||
                                                        controller.currentUri.value == targetFile.absolutePath ||
                                                        controller.currentUri.value == "file://${targetFile.absolutePath}")
                                                val currentPlayPos = if (isPlayingThis) controller.currentPositionSec.value else 0.0
                                                if (isPlayingThis) {
                                                    controller.pause()
                                                }

                                                // 2. Download high-res cover art bytes
                                                val coverBytes = candidate.artworkUrl?.let {
                                                    OnlineTrackIdentifier.downloadCoverBytes(it)
                                                }

                                                // 3. Embed metadata and cover art directly into physical audio file
                                                var writeOk = false
                                                var finalFile = targetFile
                                                if (targetFile.exists()) {
                                                    writeOk = AudioFileTagger.embedMetadata(
                                                        context = context,
                                                        file = targetFile,
                                                        title = candidate.title,
                                                        artist = candidate.artist,
                                                        album = candidate.album,
                                                        year = candidate.year,
                                                        coverBytes = coverBytes
                                                    )

                                                    // 4. Physically rename the file on disk to "Title - Artist.ext"
                                                    finalFile = AudioFileTagger.renameFile(
                                                        oldFile = targetFile,
                                                        newTitle = candidate.title,
                                                        newArtist = candidate.artist
                                                    )

                                                    // 5. Clean up any accidental companion image files so the music folder stays clean
                                                    try {
                                                        File(targetFile.parentFile, "${targetFile.nameWithoutExtension}.jpg").takeIf { it.exists() }?.delete()
                                                        File(finalFile.parentFile, "${finalFile.nameWithoutExtension}.jpg").takeIf { it.exists() }?.delete()
                                                        File(finalFile.parentFile, "cover.jpg").takeIf { it.exists() }?.delete()
                                                    } catch (_: Exception) {}

                                                    // 6. Update MediaStore ContentResolver and trigger MediaScanner
                                                    AudioFileTagger.updateMediaStore(
                                                        context = context,
                                                        oldFile = targetFile,
                                                        newFile = finalFile
                                                    )
                                                }

                                                // Invalidate old artwork cache and register new metadata
                                                ArtworkCache.invalidateTrackArtwork(track.uri, targetFile.absolutePath, finalFile.absolutePath)
                                                if (!candidate.artworkUrl.isNullOrBlank()) {
                                                    ArtworkCache.registerOnlineArtwork(finalFile.absolutePath, candidate.artworkUrl)
                                                    ArtworkCache.registerOnlineArtwork("file://${finalFile.absolutePath}", candidate.artworkUrl)
                                                    ArtworkCache.registerOnlineArtwork(finalFile.name, candidate.artworkUrl)
                                                    ArtworkCache.registerOnlineArtwork(candidate.title.lowercase(), candidate.artworkUrl)
                                                }

                                                // 7. Update AudioEngineController in-memory library, queue, currentTrack, and warm cache
                                                controller.updateTrackMetadata(
                                                    oldUri = track.uri,
                                                    newTitle = candidate.title,
                                                    newArtist = candidate.artist,
                                                    newAlbum = candidate.album,
                                                    newYear = candidate.year,
                                                    newArtworkUrl = candidate.artworkUrl,
                                                    newUri = finalFile.absolutePath
                                                )

                                                // 8. Restore full volume and resume playback with the NEW track and position
                                                controller.setVolume(1.0f)
                                                if (isPlayingThis) {
                                                    val updatedTrack = track.copy(
                                                        uri = finalFile.absolutePath,
                                                        title = candidate.title,
                                                        artist = candidate.artist,
                                                        album = candidate.album,
                                                        albumArtist = candidate.artist,
                                                        year = candidate.year,
                                                        hasArtwork = true
                                                    )
                                                    controller.playTrack(updatedTrack)
                                                    if (currentPlayPos > 0.5) {
                                                        controller.seekTo(currentPlayPos)
                                                    }
                                                }

                                                withContext(Dispatchers.Main) {
                                                    val msg = if (writeOk) {
                                                        "Saved & renamed: ${finalFile.name}"
                                                    } else {
                                                        "Updated in Veyl (Storage permission needed to edit original file)"
                                                    }
                                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                                    isSaving = false
                                                    onDismiss()
                                                }
                                            } catch (e: Exception) {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(context, "Error updating file: ${e.message}", Toast.LENGTH_SHORT).show()
                                                    controller.setVolume(1.0f)
                                                    isSaving = false
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun CandidateCard(
    candidate: TrackMetadataCandidate,
    isSaving: Boolean,
    onSelect: () -> Unit
) {
    val colors = LocalVeylColors.current

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = colors.surfaceElevated),
        border = BorderStroke(1.dp, colors.borderHairline),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = !isSaving, onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Artwork Thumbnail
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, colors.borderHairline, RoundedCornerShape(10.dp))
            ) {
                AsyncAlbumArt(
                    uri = candidate.artworkUrl ?: "",
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Metadata info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = candidate.title,
                    style = VeylTypography.SectionHeader,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = candidate.artist,
                    style = VeylTypography.BodySmall,
                    fontSize = 12.sp,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                val details = buildString {
                    if (candidate.album.isNotBlank()) append(candidate.album)
                    if (candidate.year != null) {
                        if (isNotEmpty()) append(" • ")
                        append(candidate.year.toString())
                    }
                    if (candidate.durationMs != null && candidate.durationMs > 0) {
                        if (isNotEmpty()) append(" • ")
                        val sec = candidate.durationMs / 1000
                        append(String.format(java.util.Locale.US, "%d:%02d", sec / 60, sec % 60))
                    }
                }
                if (details.isNotBlank()) {
                    Text(
                        text = details,
                        style = VeylTypography.MonoSpec,
                        fontSize = 10.sp,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Source Pill & Action
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (candidate.source == "Apple Music") Color(0xFFFA233B).copy(alpha = 0.2f) else colors.accentSignal.copy(alpha = 0.2f)
                ) {
                    Text(
                        text = candidate.source,
                        style = VeylTypography.MonoBadge.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
                        color = if (candidate.source == "Apple Music") Color(0xFFFF5E7E) else colors.accentSignal,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = colors.accentSignal,
                    modifier = Modifier.clickable(enabled = !isSaving, onClick = onSelect)
                ) {
                    Text(
                        text = "Match & Save",
                        style = VeylTypography.MonoBadge.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                        color = colors.surfacePanel,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
