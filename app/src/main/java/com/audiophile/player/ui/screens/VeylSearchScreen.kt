package com.audiophile.player.ui.screens

import com.audiophile.player.engine.ArtworkCache

import android.content.Context
import androidx.activity.compose.BackHandler
import com.audiophile.player.ui.components.VeylListRow
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.VeylTrackOptionsSheet
import com.audiophile.player.online.JioSaavnService
import com.audiophile.player.online.OnlineSongItem
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import uniffi.audiophile_core.PlaybackStateEnum
import uniffi.audiophile_core.TrackInfo

private const val MAX_RECENT_SEARCHES = 8

enum class SearchTab(val label: String) {
    ALL("All"),
    LOCAL("Local"),
    ONLINE("Online Music")
}

/**
 * Dedicated full-screen Search experience.
 *
 * Combines zero-latency local library searching with dynamic official
 * YouTube Music streaming. Filters out normal user videos/vlogs so only
 * verified music songs appear.
 */
@Composable
fun VeylSearchScreen(
    controller: AudioEngineController,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val prefs = remember { context.getSharedPreferences("veyl_search_prefs", Context.MODE_PRIVATE) }

    val tracks by controller.tracks.collectAsStateWithLifecycle()
    val playbackHistory by controller.playbackHistory.collectAsStateWithLifecycle()
    val lastAdded by controller.lastAddedTracks.collectAsStateWithLifecycle()
    val artistGroups by controller.artistGroups.collectAsStateWithLifecycle()
    val albumGroups by controller.albumGroups.collectAsStateWithLifecycle()

    val isOnlineBuffering by controller.isOnlineBuffering.collectAsStateWithLifecycle()
    val currentOnlineSong by controller.currentOnlineSong.collectAsStateWithLifecycle()
    val playbackState by controller.playbackState.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    val recentSearches = remember { mutableStateListOf<String>() }
    var showAllSongs by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(SearchTab.ALL) }
    var selectedTrackForOptions by remember { mutableStateOf<TrackInfo?>(null) }
    var selectedArtworkForOptions by remember { mutableStateOf<String?>(null) }

    var onlineResults by remember { mutableStateOf<List<OnlineSongItem>>(emptyList()) }
    var isSearchingOnline by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("recent_searches", null)
        if (!saved.isNullOrBlank()) {
            val list = saved.split("|||").filter { it.isNotBlank() }
            recentSearches.clear()
            recentSearches.addAll(list)
        }
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    BackHandler(onBack = onBack)

    // Asynchronous local search state to eliminate all UI thread lag
    var songResults by remember { mutableStateOf<List<TrackInfo>>(emptyList()) }
    var albumResults by remember { mutableStateOf<List<Triple<String, String, List<TrackInfo>>>>(emptyList()) }
    var artistResults by remember { mutableStateOf<List<Triple<String, List<TrackInfo>, Int>>>(emptyList()) }

    // Fast, non-blocking local search on background worker thread (debounced 80ms)
    LaunchedEffect(query, tracks, albumGroups, artistGroups) {
        val q = query.trim()
        if (q.isBlank()) {
            songResults = emptyList()
            albumResults = emptyList()
            artistResults = emptyList()
            return@LaunchedEffect
        }

        delay(80)
        withContext(Dispatchers.Default) {
            val qLower = q.lowercase()
            val filteredSongs = tracks.filter { t ->
                t.title.contains(qLower, ignoreCase = true) ||
                    (t.artist ?: "").contains(qLower, ignoreCase = true) ||
                    (t.album ?: "").contains(qLower, ignoreCase = true)
            }.take(50)

            val filteredAlbums = albumGroups.filter { (album, artist, _) ->
                album.contains(qLower, ignoreCase = true) ||
                    artist.contains(qLower, ignoreCase = true)
            }.take(12)

            val filteredArtists = artistGroups.filter { (artist, _, _) ->
                artist.contains(qLower, ignoreCase = true)
            }.take(10)

            songResults = filteredSongs
            albumResults = filteredAlbums
            artistResults = filteredArtists
        }
    }

    // Debounced Online Search for official studio tracks (450ms debounce so typing is fluid)
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length >= 2) {
            delay(450)
            isSearchingOnline = true
            try {
                val results = JioSaavnService.searchSongs(q)
                onlineResults = results
            } catch (e: Exception) {
                onlineResults = emptyList()
            } finally {
                isSearchingOnline = false
            }
        } else {
            onlineResults = emptyList()
            isSearchingOnline = false
        }
    }

    val hasLocalResults = songResults.isNotEmpty() || albumResults.isNotEmpty() || artistResults.isNotEmpty()
    val hasOnlineResults = onlineResults.isNotEmpty()

    val listenNowTracks = remember(playbackHistory, lastAdded, tracks) {
        when {
            playbackHistory.isNotEmpty() -> playbackHistory.take(10)
            lastAdded.isNotEmpty()       -> lastAdded.take(10)
            else                         -> tracks.take(10)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Search bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(VeylIcons.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = {
                    Text(
                        "Search local or stream online\u2026",
                        style = VeylTypography.BodyMedium,
                        color = colors.textMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        VeylIcons.Search, contentDescription = null,
                        tint = colors.accentSignal,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(
                                VeylIcons.Close, contentDescription = "Clear",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                } else null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    onSearch = {
                        if (query.isNotBlank()) {
                            saveRecentSearch(recentSearches, query, prefs)
                        }
                        keyboardController?.hide()
                    }
                ),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = colors.surfacePanel,
                    unfocusedContainerColor = colors.surfacePanel,
                    focusedBorderColor = colors.accentSignal,
                    unfocusedBorderColor = colors.borderHairline,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary,
                    cursorColor = colors.accentSignal
                ),
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
            )
        }

        // Tab Filter Bar when a query is present
        if (query.isNotBlank()) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(SearchTab.values()) { tab ->
                    val isSelected = selectedTab == tab
                    val badgeCount = when (tab) {
                        SearchTab.ALL -> songResults.size + onlineResults.size
                        SearchTab.LOCAL -> songResults.size + albumResults.size + artistResults.size
                        SearchTab.ONLINE -> onlineResults.size
                    }

                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = if (isSelected) colors.accentSignal else colors.surfacePanel,
                        modifier = Modifier.clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedTab = tab
                        }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = tab.label,
                                style = VeylTypography.BodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) colors.background else colors.textPrimary
                            )

                            if (tab == SearchTab.ONLINE && isSearchingOnline) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 1.5.dp,
                                    color = if (isSelected) colors.background else colors.accentSignal
                                )
                            } else if (badgeCount > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = if (isSelected) colors.background.copy(alpha = 0.2f) else colors.surfaceElevated
                                ) {
                                    Text(
                                        text = "$badgeCount",
                                        style = VeylTypography.MonoSpec,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isSelected) colors.background else colors.textMuted,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Animated switch between empty-state and results
        AnimatedContent(
            targetState = query.isBlank(),
            transitionSpec = {
                (fadeIn() + slideInVertically()) togetherWith (fadeOut() + slideOutVertically())
            },
            label = "searchContent"
        ) { isBlank ->
            if (isBlank) {
                // Empty-state
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (recentSearches.isNotEmpty()) {
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "RECENT SEARCHES",
                                    style = VeylTypography.MonoBadge,
                                    color = colors.textSecondary
                                )
                                Text(
                                    "Clear",
                                    style = VeylTypography.BodySmall,
                                    color = colors.accentSignal,
                                    modifier = Modifier
                                        .clickable {
                                            recentSearches.clear()
                                            prefs.edit().remove("recent_searches").apply()
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                        item {
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(recentSearches) { term ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = colors.surfacePanel,
                                        modifier = Modifier.clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            query = term
                                        }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                VeylIcons.Search, contentDescription = null,
                                                tint = colors.textMuted,
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Text(
                                                term,
                                                style = VeylTypography.BodySmall,
                                                color = colors.textPrimary,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    if (listenNowTracks.isNotEmpty()) {
                        item {
                            Text(
                                if (playbackHistory.isNotEmpty()) "RECENTLY PLAYED" else "RECENTLY ADDED",
                                style = VeylTypography.MonoBadge,
                                color = colors.textSecondary,
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            )
                        }
                        items(listenNowTracks, key = { track -> track.uri }) { track ->
                            SearchTrackRow(
                                track = track,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.playTrack(track)
                                },
                                onOpenOptions = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTrackForOptions = track
                                }
                            )
                        }
                    }

                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            } else {
                // Active search results
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val showLocal = selectedTab == SearchTab.ALL || selectedTab == SearchTab.LOCAL
                    val showOnline = selectedTab == SearchTab.ALL || selectedTab == SearchTab.ONLINE

                    if (!hasLocalResults && !hasOnlineResults && !isSearchingOnline) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "No results for \u201c$query\u201d",
                                        style = VeylTypography.BodyMedium,
                                        color = colors.textMuted
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        "Check spelling or try another artist name",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textMuted.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }

                    // 1. Local Songs
                    if (showLocal && songResults.isNotEmpty()) {
                        item { SearchSectionHeader(label = "Local Songs", count = songResults.size) }
                        val displayedSongs = if (showAllSongs) songResults else songResults.take(6)
                        items(displayedSongs, key = { track -> track.uri }) { track ->
                            SearchTrackRow(
                                track = track,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveRecentSearch(recentSearches, query, prefs)
                                    controller.playTrack(track)
                                },
                                onOpenOptions = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    selectedTrackForOptions = track
                                }
                            )
                        }
                        if (songResults.size > 6) {
                            item {
                                Text(
                                    if (showAllSongs) "Show fewer local songs" else "Show all ${songResults.size} local songs",
                                    style = VeylTypography.BodySmall,
                                    color = colors.accentSignal,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier
                                        .clickable { showAllSongs = !showAllSongs }
                                        .padding(vertical = 4.dp)
                                )
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // 2. Local Albums
                    if (showLocal && albumResults.isNotEmpty()) {
                        item { SearchSectionHeader(label = "Local Albums", count = albumResults.size) }
                        items(albumResults, key = { "alb_${it.first}_${it.second}" }) { (album, artist, tracks) ->
                            val firstTrack = tracks.firstOrNull()
                            SearchAlbumRow(
                                albumName = album,
                                artist = artist,
                                trackCount = tracks.size,
                                firstTrackUri = firstTrack?.uri ?: "",
                                firstTrackHasArtwork = firstTrack?.hasArtwork,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveRecentSearch(recentSearches, query, prefs)
                                    controller.playTrackList(tracks)
                                }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // 3. Local Artists
                    if (showLocal && artistResults.isNotEmpty()) {
                        item { SearchSectionHeader(label = "Local Artists", count = artistResults.size) }
                        items(artistResults, key = { "art_${it.first}" }) { (artist, tracks, albumCount) ->
                            val firstTrack = tracks.firstOrNull()
                            SearchArtistRow(
                                artistName = artist,
                                trackCount = tracks.size,
                                albumCount = albumCount,
                                firstTrackUri = firstTrack?.uri ?: "",
                                firstTrackHasArtwork = firstTrack?.hasArtwork,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveRecentSearch(recentSearches, query, prefs)
                                    controller.playTrackList(tracks)
                                }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // 4. Online Music Section (Official YouTube Music)
                    if (showOnline) {
                        if (onlineResults.isNotEmpty() || isSearchingOnline) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            "ONLINE MUSIC (STUDIO 320k)",
                                            style = VeylTypography.MonoBadge,
                                            color = colors.textSecondary
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(4.dp),
                                            color = colors.accentSignal.copy(alpha = 0.15f)
                                        ) {
                                            Text(
                                                "320k AAC",
                                                style = VeylTypography.MonoSpec,
                                                color = colors.accentSignal,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (onlineResults.isNotEmpty()) {
                                            Surface(
                                                modifier = Modifier
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        val randomSong = onlineResults.random()
                                                        controller.playOnlineSong(
                                                            song = randomSong,
                                                            contextQueue = onlineResults,
                                                            startShuffled = true
                                                        )
                                                    }
                                                    .clip(RoundedCornerShape(12.dp)),
                                                shape = RoundedCornerShape(12.dp),
                                                color = colors.surfaceElevated,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Icon(
                                                        VeylIcons.Shuffle,
                                                        contentDescription = "Shuffle",
                                                        tint = colors.accentSignal,
                                                        modifier = Modifier.size(12.dp)
                                                    )
                                                    Text(
                                                        text = "Shuffle",
                                                        style = VeylTypography.MonoSpec,
                                                        color = colors.textPrimary,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }

                                        if (isSearchingOnline) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 2.dp,
                                                color = colors.accentSignal
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (onlineResults.isNotEmpty()) {
                            items(onlineResults, key = { item -> "saavn_${item.id}" }) { item ->
                                val isCurrent = currentOnlineSong?.id == item.id
                                val isBufferingThis = isCurrent && isOnlineBuffering
                                val isPlayingThis = isCurrent && playbackState == PlaybackStateEnum.PLAYING

                                SearchOnlineSongRow(
                                    item = item,
                                    isBuffering = isBufferingThis,
                                    isPlaying = isPlayingThis,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        saveRecentSearch(recentSearches, query, prefs)
                                        controller.playOnlineSong(
                                            song = item,
                                            contextQueue = onlineResults,
                                            startShuffled = false
                                        )
                                    },
                                    onDownloadClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        Toast.makeText(context, "Downloading \"${item.title}\" to storage\u2026", Toast.LENGTH_SHORT).show()
                                        controller.downloadOnlineSong(
                                            song = item,
                                            onCompleted = {
                                                Toast.makeText(context, "Saved to Music/Veyl: ${item.title}", Toast.LENGTH_LONG).show()
                                            },
                                            onError = { err ->
                                                Toast.makeText(context, "Download failed: ${err.message}", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    },
                                    onOpenOptions = {
                                        val effectiveUri = item.streamUrl.ifBlank { "online://${item.id}" }
                                        if (item.thumbnailUrl.isNotBlank()) {
                                            ArtworkCache.registerOnlineArtwork(effectiveUri, item.thumbnailUrl)
                                            ArtworkCache.registerOnlineArtwork("online://${item.id}", item.thumbnailUrl)
                                            ArtworkCache.registerOnlineArtwork(item.id, item.thumbnailUrl)
                                            ArtworkCache.registerOnlineArtwork(item.title.lowercase(), item.thumbnailUrl)
                                        }
                                        val trackInfo = TrackInfo(
                                            uri = effectiveUri,
                                            title = item.title,
                                            artist = item.artist,
                                            album = item.album,
                                            albumArtist = null,
                                            genre = "Online Stream",
                                            year = null,
                                            trackNumber = null,
                                            discNumber = null,
                                            durationSeconds = item.durationSeconds,
                                            sampleRate = 44100u,
                                            bitDepth = 16u,
                                            bitrate = 320000u,
                                            channels = 2u,
                                            formatName = "AAC 320k",
                                            hasArtwork = true
                                        )
                                        selectedArtworkForOptions = item.thumbnailUrl
                                        selectedTrackForOptions = trackInfo
                                    }
                                )
                            }
                        } else if (isSearchingOnline && !hasLocalResults) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = colors.accentSignal
                                        )
                                        Text(
                                            "Finding official studio tracks\u2026",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }

    if (selectedTrackForOptions != null) {
        VeylTrackOptionsSheet(
            track = selectedTrackForOptions,
            controller = controller,
            onDismiss = {
                selectedTrackForOptions = null
                selectedArtworkForOptions = null
            },
            artworkUrl = selectedArtworkForOptions
        )
    }
}

private fun saveRecentSearch(
    list: MutableList<String>,
    query: String,
    prefs: android.content.SharedPreferences
) {
    val clean = query.trim()
    if (clean.isBlank()) return
    list.remove(clean)
    list.add(0, clean)
    while (list.size > MAX_RECENT_SEARCHES) {
        list.removeAt(list.lastIndex)
    }
    prefs.edit().putString("recent_searches", list.joinToString("|||")).apply()
}

@Composable
private fun SearchSectionHeader(label: String, count: Int) {
    val colors = LocalVeylColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label.uppercase(), style = VeylTypography.MonoBadge, color = colors.textSecondary)
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = colors.surfacePanel
        ) {
            Text(
                "$count",
                style = VeylTypography.MonoSpec,
                color = colors.textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
            )
        }
    }
}

@Composable
private fun SearchTrackRow(
    track: TrackInfo,
    onClick: () -> Unit,
    onOpenOptions: (() -> Unit)? = null
) {
    val sub = buildString {
        track.artist?.let { append(it) }
        if (track.formatName.isNotBlank()) {
            if (isNotEmpty()) append(" • ")
            append(track.formatName.uppercase())
        }
    }
    VeylListRow(
        title = track.title,
        subtitle = sub,
        durationSeconds = track.durationSeconds,
        onClick = onClick,
        onOptionsClick = onOpenOptions
    )
}

@Composable
private fun SearchOnlineSongRow(
    item: OnlineSongItem,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onOpenOptions: (() -> Unit)? = null
) {
    val colors = LocalVeylColors.current
    VeylListRow(
        title = item.title,
        subtitle = "${item.artist} • 320k",
        isPlaying = isPlaying,
        trailingText = item.durationText,
        onClick = onClick,
        onOptionsClick = onOpenOptions,
        trailingContent = {
            IconButton(
                onClick = onDownloadClick,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    VeylIcons.Download,
                    contentDescription = "Download to device storage",
                    tint = colors.textMuted,
                    modifier = Modifier.size(17.dp)
                )
            }
            if (isBuffering) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.accentSignal
                )
            }
        }
    )
}

@Composable
private fun SearchAlbumRow(
    albumName: String,
    artist: String,
    trackCount: Int,
    firstTrackUri: String,
    firstTrackHasArtwork: Boolean? = null,
    onClick: () -> Unit
) {
    VeylListRow(
        title = albumName,
        subtitle = "$artist • $trackCount tracks",
        onClick = onClick
    )
}

@Composable
private fun SearchArtistRow(
    artistName: String,
    trackCount: Int,
    albumCount: Int,
    firstTrackUri: String,
    firstTrackHasArtwork: Boolean? = null,
    onClick: () -> Unit
) {
    val subtitle = buildString {
        append("$albumCount album")
        if (albumCount != 1) append("s")
        append(" • $trackCount track")
        if (trackCount != 1) append("s")
    }
    VeylListRow(
        title = artistName,
        subtitle = subtitle,
        onClick = onClick
    )
}
