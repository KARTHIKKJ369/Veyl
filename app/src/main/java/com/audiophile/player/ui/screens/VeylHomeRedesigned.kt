package com.audiophile.player.ui.screens

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import androidx.compose.ui.graphics.Color
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import com.audiophile.player.ui.theme.isColorLight
import uniffi.audiophile_core.TrackInfo
import java.util.Locale

/** A quiet listening-first home screen; technical controls stay in their contextual screens. */
@Composable
fun VeylHomeRedesigned(
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
    val tracks by controller.tracks.collectAsStateWithLifecycle()
    val recent by controller.lastAddedTracks.collectAsStateWithLifecycle()
    val history by controller.playbackHistory.collectAsStateWithLifecycle()
    val currentTrack by controller.currentTrack.collectAsStateWithLifecycle()
    val isScanning by controller.isScanning.collectAsStateWithLifecycle()
    val recentTracks = recent.ifEmpty { tracks }.take(8)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Veyl", style = VeylTypography.DisplayLarge, color = colors.textPrimary)
                    Text("Your music, uninterrupted.", style = VeylTypography.BodySmall, color = colors.textMuted)
                }
                IconButton(onClick = onNavigateToSearch) {
                    Icon(VeylIcons.Search, "Search library", tint = colors.textPrimary)
                }
            }
        }

        if (isScanning) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Loading your library", style = VeylTypography.HeadlineMedium, color = colors.textPrimary)
                    Text("Finding your music…", style = VeylTypography.BodySmall, color = colors.textMuted)
                }
            }
        } else if (tracks.isEmpty()) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(VeylIcons.Folder, null, tint = colors.accentSignal, modifier = Modifier.size(34.dp))
                    Text("Start with your music", style = VeylTypography.HeadlineMedium, color = colors.textPrimary)
                    Text(
                        "Choose a folder to build your private library.",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                    val isLight = isColorLight(colors.accentSignal)
                    val btnTextColor = if (isLight) Color(0xFF0F0F0F) else Color.White
                    Button(
                        onClick = onPickFolder,
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal, contentColor = btnTextColor)
                    ) { Text("Choose music folder", color = btnTextColor, fontWeight = FontWeight.Bold) }
                }
            }
        } else {
            if (history.isNotEmpty()) {
                item { SectionHeader("Continue listening", "See all", onNavigateToHistory) }
                item {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        itemsIndexed(history.take(8), key = { index, track -> "${track.uri}_$index" }) { _, track ->
                            AlbumTile(track = track, onClick = { controller.playTrack(track) })
                        }
                    }
                }
            }
            item {
                SectionHeader(
                    title = "Recently added",
                    action = "See all",
                    onAction = onNavigateToLastAdded
                )
            }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    itemsIndexed(recentTracks, key = { index, track -> "${track.uri}_$index" }) { _, track ->
                        AlbumTile(track = track, onClick = { controller.playTrack(track) })
                    }
                }
            }
            item { SectionHeader("From your library", "Shuffle", onAction = { controller.playShuffled(tracks) }) }
            itemsIndexed(tracks.take(6), key = { index, track -> "${track.uri}_$index" }) { _, track ->
                LibraryRow(track = track, isCurrent = track.uri == currentTrack?.uri, onClick = { controller.playTrack(track) })
            }
            item {
                Text(
                    text = "Browse all ${tracks.size} tracks",
                    style = VeylTypography.BodyMedium,
                    color = colors.accentSignal,
                    modifier = Modifier.clickable(onClick = onNavigateToSearch).padding(vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, action: String, onAction: () -> Unit) {
    val colors = LocalVeylColors.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = VeylTypography.HeadlineMedium, color = colors.textPrimary)
        Text(action, style = VeylTypography.BodySmall, color = colors.accentSignal, modifier = Modifier.clickable(onClick = onAction))
    }
}

@Composable
private fun AlbumTile(track: TrackInfo, onClick: () -> Unit) {
    val colors = LocalVeylColors.current
    Column(modifier = Modifier.width(142.dp).clickable(onClick = onClick)) {
        AsyncAlbumArt(uri = track.uri, modifier = Modifier.size(142.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.height(9.dp))
        Text(track.title, style = VeylTypography.BodyMedium, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist ?: "Unknown artist", style = VeylTypography.BodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun LibraryRow(track: TrackInfo, isCurrent: Boolean, onClick: () -> Unit) {
    val colors = LocalVeylColors.current
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncAlbumArt(uri = track.uri, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, style = VeylTypography.BodyMedium, color = if (isCurrent) colors.accentSignal else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.artist ?: "Unknown artist", style = VeylTypography.BodySmall, color = colors.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatDuration(track.durationSeconds), style = VeylTypography.BodySmall, color = colors.textMuted)
    }
}

private fun formatDuration(seconds: Double): String {
    val value = seconds.toInt().coerceAtLeast(0)
    return String.format(Locale.getDefault(), "%d:%02d", value / 60, value % 60)
}
