package com.audiophile.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.TrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylTrackOptionsSheet(
    track: TrackInfo?,
    controller: AudioEngineController,
    onDismiss: () -> Unit,
    onViewAlbum: ((String) -> Unit)? = null,
    onViewArtist: ((String) -> Unit)? = null,
    onOpenEqualizer: (() -> Unit)? = null
) {
    if (track == null) return

    val colors = LocalVeylColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val favoriteUris by controller.favoriteUris.collectAsState()
    val playlists by controller.playlists.collectAsState()

    val isFav = favoriteUris.contains(track.uri)
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfacePanel,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Track Hero Banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp))
                ) {
                    AsyncAlbumArt(
                        uri = track.uri,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        style = VeylTypography.SectionHeader,
                        color = colors.textPrimary,
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
                    Text(
                        text = "${track.formatName} • ${track.sampleRate.toDouble() / 1000.0} kHz / ${track.bitDepth ?: 24}b",
                        style = VeylTypography.MonoSpec,
                        color = colors.accentSignal,
                        fontSize = 11.sp
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.borderHairline)
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 1: Favorite
            OptionItem(
                icon = if (isFav) VeylIcons.FavoriteFilled else VeylIcons.FavoriteBorder,
                title = if (isFav) "Remove from Favorites" else "Add to Favorites",
                tint = if (isFav) Color(0xFFFF6584) else colors.textPrimary,
                onClick = {
                    controller.toggleFavorite(track.uri)
                }
            )

            // Option 2: Add to Playlist
            OptionItem(
                icon = VeylIcons.Playlist,
                title = "Add to Playlist",
                onClick = {
                    showPlaylistDialog = true
                }
            )

            // Option 3: Equalizer
            if (onOpenEqualizer != null) {
                OptionItem(
                    icon = VeylIcons.Equalizer,
                    title = "Audio Equalizer & DSP",
                    onClick = {
                        onDismiss()
                        onOpenEqualizer()
                    }
                )
            }

            // Option 4: View Album
            if (track.album != null && onViewAlbum != null) {
                OptionItem(
                    icon = VeylIcons.Disc,
                    title = "Album: ${track.album}",
                    onClick = {
                        onDismiss()
                        onViewAlbum(track.album!!)
                    }
                )
            }

            // Option 5: View Artist
            if (track.artist != null && onViewArtist != null) {
                OptionItem(
                    icon = VeylIcons.StorageLocal,
                    title = "Artist: ${track.artist}",
                    onClick = {
                        onDismiss()
                        onViewArtist(track.artist!!)
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    // Playlist Picker Dialog
    if (showPlaylistDialog) {
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            containerColor = colors.surfacePanel,
            title = {
                Text(
                    text = "Add to Playlist",
                    style = VeylTypography.SectionHeader,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            showCreatePlaylistDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create New Playlist", color = Color.Black, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (playlists.isEmpty()) {
                        Text(
                            text = "No user playlists created yet",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth()) {
                            items(playlists) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable {
                                            controller.playlistManager.addTrackToPlaylist(pl.id, track.uri)
                                            showPlaylistDialog = false
                                            onDismiss()
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Playlist,
                                        contentDescription = null,
                                        tint = colors.accentSignal,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = pl.name,
                                        style = VeylTypography.Body,
                                        color = colors.textPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPlaylistDialog = false }) {
                    Text("Close", color = colors.textMuted)
                }
            }
        )
    }

    // Create New Playlist Dialog
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
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
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
                        if (newPlaylistName.isNotBlank()) {
                            val newPl = controller.playlistManager.createPlaylist(newPlaylistName.trim())
                            controller.playlistManager.addTrackToPlaylist(newPl.id, track.uri)
                            newPlaylistName = ""
                            showCreatePlaylistDialog = false
                            showPlaylistDialog = false
                            onDismiss()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal)
                ) {
                    Text("Create & Add", color = Color.Black, fontWeight = FontWeight.Bold)
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
private fun OptionItem(
    icon: ImageVector,
    title: String,
    tint: Color = LocalVeylColors.current.textPrimary,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = tint,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            style = VeylTypography.Body,
            color = tint,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
