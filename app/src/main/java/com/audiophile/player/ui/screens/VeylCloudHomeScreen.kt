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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.audiophile.player.online.CloudRecommendationService
import com.audiophile.player.online.OnlineSongItem
import com.audiophile.player.online.RecommendationSection
import com.audiophile.player.ui.components.VeylTrackOptionsSheet
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import kotlinx.coroutines.launch
import uniffi.audiophile_core.TrackInfo
import java.util.Calendar

@Composable
fun VeylCloudHomeScreen(
    controller: AudioEngineController,
    onNavigateToSearch: () -> Unit,
    onNavigateToLibrary: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val selectedLanguages by controller.selectedLanguages.collectAsStateWithLifecycle()
    val selectedArtists by controller.selectedArtists.collectAsStateWithLifecycle()
    val playbackHistory by controller.playbackHistory.collectAsStateWithLifecycle()
    val libraryTracks by controller.libraryTracks.collectAsStateWithLifecycle()
    val currentTrack by controller.currentTrack.collectAsStateWithLifecycle()
    val isStreaming by controller.isOnlineStreaming.collectAsStateWithLifecycle()

    var activeLanguageFilter by remember { mutableStateOf("All") }
    var sections by remember { mutableStateOf<List<RecommendationSection>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }

    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Late night vibes"
        }
    }

    suspend fun loadFeed(forceRefresh: Boolean = false) {
        isLoading = true
        if (forceRefresh) CloudRecommendationService.clearCache()
        val result = CloudRecommendationService.getPersonalizedFeed(
            selectedLanguages = selectedLanguages,
            selectedArtists = selectedArtists,
            recentTracks = playbackHistory,
            activeLanguageFilter = if (activeLanguageFilter == "All") null else activeLanguageFilter
        )
        sections = result
        isLoading = false
    }

    LaunchedEffect(selectedLanguages, selectedArtists, activeLanguageFilter) {
        loadFeed(forceRefresh = false)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(top = 12.dp, bottom = 132.dp),
        verticalArrangement = Arrangement.spacedBy(26.dp)
    ) {
        // 1. Top Header Bar
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Discover",
                            style = VeylTypography.DisplayLarge,
                            color = colors.textPrimary,
                            fontSize = 30.sp
                        )
                        Text(
                            text = greeting,
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            coroutineScope.launch { loadFeed(forceRefresh = true) }
                        }) {
                            Icon(
                                VeylIcons.Refresh,
                                contentDescription = "Refresh Feed",
                                tint = colors.textMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        IconButton(onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onNavigateToSearch()
                        }) {
                            Icon(
                                VeylIcons.Search,
                                contentDescription = "Search",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Language Filter Pills
                val languagePills = remember(selectedLanguages) {
                    listOf("All") + selectedLanguages.toList()
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(languagePills) { pill ->
                        val isSelected = activeLanguageFilter == pill
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    activeLanguageFilter = pill
                                }
                                .clip(RoundedCornerShape(16.dp)),
                            shape = RoundedCornerShape(16.dp),
                            color = if (isSelected) colors.accentSignal else colors.surfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) colors.accentSignal else colors.borderSubtle
                            )
                        ) {
                            Text(
                                text = pill,
                                style = VeylTypography.BodySmall,
                                color = if (isSelected) Color.Black else colors.textPrimary,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // 2. Offline Library Banner Card
        item {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToLibrary()
                    },
                shape = RoundedCornerShape(18.dp),
                color = colors.surfacePanel,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(colors.accentSignal.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                VeylIcons.Folder,
                                contentDescription = null,
                                tint = colors.accentSignal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Local Offline Library",
                                style = VeylTypography.TitleMedium,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${libraryTracks.size} tracks stored on device • Bit-perfect DSP",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    Icon(
                        VeylIcons.ChevronRight,
                        contentDescription = "View",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 3. Jump Back In (Recently Played History)
        if (playbackHistory.isNotEmpty()) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Jump Back In",
                        style = VeylTypography.HeadlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(playbackHistory.take(8)) { track ->
                            RecentTrackCard(
                                track = track,
                                isPlaying = currentTrack?.uri == track.uri,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.playTrack(track)
                                },
                                onOpenOptions = {
                                    selectedTrackForOptions = track
                                }
                            )
                        }
                    }
                }
            }
        }

        // 4. Loading State
        if (isLoading && sections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = colors.accentSignal,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Curating your streaming feed…",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }

        // 5. Dynamic Streaming Sections (Trending, Hits, Artists, Mood)
        items(sections) { section ->
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = section.title,
                            style = VeylTypography.HeadlineMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        section.subtitle?.let {
                            Text(
                                text = it,
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted
                            )
                        }
                    }

                    if (section.items.isNotEmpty()) {
                        Surface(
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    val randomSong = section.items.random()
                                    controller.playOnlineSong(
                                        song = randomSong,
                                        contextQueue = section.items,
                                        startShuffled = true
                                    )
                                }
                                .clip(RoundedCornerShape(14.dp)),
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceElevated,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    VeylIcons.Shuffle,
                                    contentDescription = "Shuffle Section",
                                    tint = colors.accentSignal,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "Shuffle",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(section.items) { song ->
                        CloudSongCard(
                            song = song,
                            isPlaying = currentTrack?.title.equals(song.title, ignoreCase = true) && isStreaming,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                controller.playOnlineSong(
                                    song = song,
                                    contextQueue = section.items,
                                    startShuffled = false
                                )
                            },
                            onOpenOptions = {
                                val trackInfo = TrackInfo(
                                    uri = song.streamUrl.ifBlank { "online://${song.id}" },
                                    title = song.title,
                                    artist = song.artist,
                                    album = song.album,
                                    albumArtist = null,
                                    genre = "Online Stream",
                                    year = null,
                                    trackNumber = null,
                                    discNumber = null,
                                    durationSeconds = song.durationSeconds,
                                    sampleRate = 44100u,
                                    bitDepth = 16u,
                                    bitrate = 320000u,
                                    channels = 2u,
                                    formatName = "AAC 320k",
                                    hasArtwork = true
                                )
                                selectedTrackForOptions = trackInfo
                            }
                        )
                    }
                }
            }
        }
    }

    // Modal Track Options Sheet (Delete, Playlist, Favorite, Download)
    if (selectedTrackForOptions != null) {
        VeylTrackOptionsSheet(
            track = selectedTrackForOptions,
            controller = controller,
            onDismiss = { selectedTrackForOptions = null }
        )
    }
}

@Composable
private fun CloudSongCard(
    song: OnlineSongItem,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOpenOptions: () -> Unit
) {
    val colors = LocalVeylColors.current

    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceElevated)
        ) {
            AsyncAlbumArt(
                uri = song.thumbnailUrl,
                contentDescription = "${song.title} - ${song.artist}",
                modifier = Modifier.fillMaxSize()
            )

            // Play indicator badge
            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(colors.accentSignal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        VeylIcons.Play,
                        contentDescription = "Playing",
                        tint = Color.Black,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // More options button
            IconButton(
                onClick = onOpenOptions,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(28.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    VeylIcons.MoreVert,
                    contentDescription = "Options",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = song.title,
            style = VeylTypography.TitleMedium,
            color = if (isPlaying) colors.accentSignal else colors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = song.artist,
            style = VeylTypography.BodySmall,
            color = colors.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun RecentTrackCard(
    track: TrackInfo,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onOpenOptions: () -> Unit
) {
    val colors = LocalVeylColors.current

    Column(
        modifier = Modifier
            .width(128.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(128.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceElevated)
        ) {
            AsyncAlbumArt(
                uri = track.uri,
                contentDescription = "${track.title} - ${track.artist}",
                modifier = Modifier.fillMaxSize()
            )

            if (isPlaying) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(colors.accentSignal),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        VeylIcons.Play,
                        contentDescription = "Playing",
                        tint = Color.Black,
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = track.title,
            style = VeylTypography.TitleMedium,
            fontSize = 13.sp,
            color = if (isPlaying) colors.accentSignal else colors.textPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist ?: "Unknown Artist",
            style = VeylTypography.BodySmall,
            color = colors.textMuted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
