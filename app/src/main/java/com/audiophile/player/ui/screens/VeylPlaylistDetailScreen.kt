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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.VeylStackedArtworkDeck
import com.audiophile.player.ui.components.VeylTrackOptionsSheet
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.TrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylPlaylistDetailScreen(
    controller: AudioEngineController,
    title: String = "Favorites",
    description: String = "The tracks I like the most",
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val allTracks by controller.tracks.collectAsState()
    val favoriteUris by controller.favoriteUris.collectAsState()
    val topTracks by controller.topTracks.collectAsState()
    val lastAddedTracks by controller.lastAddedTracks.collectAsState()
    val historyTracks by controller.playbackHistory.collectAsState()
    val playlists by controller.playlists.collectAsState()
    val currentUri by controller.currentUri.collectAsState()

    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }

    val playlistTracks = when (title) {
        "Favorites" -> allTracks.filter { favoriteUris.contains(it.uri) }
        "Top Tracks" -> if (topTracks.isNotEmpty()) topTracks else allTracks
        "Last added" -> if (lastAddedTracks.isNotEmpty()) lastAddedTracks else allTracks
        "History" -> historyTracks
        else -> {
            val custom = playlists.find { it.name.equals(title, ignoreCase = true) }
            if (custom != null) {
                val uriSet = custom.trackUris.toSet()
                allTracks.filter { uriSet.contains(it.uri) }
            } else {
                allTracks
            }
        }
    }

    val totalDurationSec = playlistTracks.sumOf { it.durationSeconds }
    val totalMin = (totalDurationSec / 60).toInt()
    val totalSec = (totalDurationSec % 60).toInt()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. Top Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    },
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.ChevronLeft,
                        contentDescription = "Back",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }

                Text(
                    text = title,
                    style = VeylTypography.TitleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = {
                        if (playlistTracks.isNotEmpty()) {
                            selectedTrackForOptions = playlistTracks.first()
                        }
                    }
                ) {
                    Icon(
                        imageVector = VeylIcons.MoreVert,
                        contentDescription = "Options",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }

        // 2. 3-Card Stacked Deck Artwork (Changes smoothly over time automatically)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                VeylStackedArtworkDeck(
                    tracks = playlistTracks,
                    size = 210.dp,
                    autoCycleIntervalMs = 3500L,
                    onClick = {
                        if (playlistTracks.isNotEmpty()) {
                            controller.playTrackList(playlistTracks, startIndex = 0)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Playlist Name
                Text(
                    text = title,
                    style = VeylTypography.DisplayLarge,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Description
                Text(
                    text = description,
                    style = VeylTypography.Body,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Stats: Total Tracks & Time
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "${playlistTracks.size} tracks",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                    Text(
                        text = "•",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                    Text(
                        text = if (totalMin > 60) "${totalMin / 60} hr ${totalMin % 60} min" else "$totalMin min $totalSec sec",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Play / Shuffle Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Large Pill Play Button
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.accentSignal),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playlistTracks.isNotEmpty()) {
                                    controller.playTrackList(playlistTracks, startIndex = 0)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.Play,
                                contentDescription = "Play",
                                tint = colors.surfacePanel,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Play All",
                                style = VeylTypography.TitleMedium,
                                color = colors.surfacePanel,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Shuffle Button
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = colors.surfaceElevated,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playlistTracks.isNotEmpty()) {
                                    controller.toggleShuffle()
                                    controller.playTrackList(playlistTracks.shuffled(), startIndex = 0)
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.Shuffle,
                                contentDescription = "Shuffle",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // 3. Track List
        if (playlistTracks.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No tracks in this list yet",
                        style = VeylTypography.Body,
                        color = colors.textMuted
                    )
                }
            }
        } else {
            itemsIndexed(playlistTracks, key = { _, track -> track.uri }) { index, track ->
                val isPlayingThis = currentUri == track.uri

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            controller.playTrackList(playlistTracks, startIndex = index)
                        }
                        .background(if (isPlayingThis) colors.surfaceElevated.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Index number or playing animation indicator
                    Box(
                        modifier = Modifier.width(28.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (isPlayingThis) {
                            Icon(
                                imageVector = VeylIcons.Equalizer,
                                contentDescription = "Playing",
                                tint = colors.accentSignal,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "${index + 1}",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Album Artwork
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.size(46.dp)
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
                            text = track.artist,
                            style = VeylTypography.BodySmall,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Duration
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

    // Modal Track Options Sheet
    selectedTrackForOptions?.let { track ->
        VeylTrackOptionsSheet(
            track = track,
            controller = controller,
            onDismiss = { selectedTrackForOptions = null }
        )
    }
}
