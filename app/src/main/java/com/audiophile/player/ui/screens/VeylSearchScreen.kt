package com.audiophile.player.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
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
import kotlinx.coroutines.delay
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

    // Debounced Online Search for official studio tracks
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.length >= 2) {
            delay(250)
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

    // Derive local results from query
    val songResults = remember(query, tracks) {
        if (query.isBlank()) emptyList()
        else tracks.filter { t ->
            t.title.contains(query, ignoreCase = true) ||
                (t.artist ?: "").contains(query, ignoreCase = true) ||
                (t.album ?: "").contains(query, ignoreCase = true)
        }.take(50)
    }

    val albumResults = remember(query, albumGroups) {
        if (query.isBlank()) emptyList()
        else albumGroups.filter { (album, artist, _) ->
            album.contains(query, ignoreCase = true) ||
                artist.contains(query, ignoreCase = true)
        }.take(12)
    }

    val artistResults = remember(query, artistGroups) {
        if (query.isBlank()) emptyList()
        else artistGroups.filter { (artist, _, _) ->
            artist.contains(query, ignoreCase = true)
        }.take(10)
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
                        itemsIndexed(listenNowTracks, key = { index, track -> "${track.uri}_$index" }) { _, track ->
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
                        itemsIndexed(displayedSongs, key = { index, track -> "song_${track.uri}_$index" }) { _, track ->
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
                            SearchAlbumRow(
                                albumName = album,
                                artist = artist,
                                trackCount = tracks.size,
                                firstTrackUri = tracks.firstOrNull()?.uri ?: "",
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
                            SearchArtistRow(
                                artistName = artist,
                                trackCount = tracks.size,
                                albumCount = albumCount,
                                firstTrackUri = tracks.firstOrNull()?.uri ?: "",
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

                        if (onlineResults.isNotEmpty()) {
                            itemsIndexed(onlineResults, key = { index, item -> "saavn_${item.id}_$index" }) { _, item ->
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
                                        controller.playOnlineSong(item)
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
            onDismiss = { selectedTrackForOptions = null }
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
    val colors = LocalVeylColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncAlbumArt(
            uri = track.uri,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = VeylTypography.BodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    track.artist?.let { append(it) }
                    track.album?.let {
                        if (isNotEmpty()) append(" \u00b7 ")
                        append(it)
                    }
                },
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "${track.formatName} ${track.bitDepth ?: 16}b",
            style = VeylTypography.MonoSpec,
            color = colors.accentSignal,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
        if (onOpenOptions != null) {
            Spacer(Modifier.width(4.dp))
            IconButton(
                onClick = onOpenOptions,
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

@Composable
private fun SearchOnlineSongRow(
    item: OnlineSongItem,
    isBuffering: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit
) {
    val colors = LocalVeylColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isPlaying) colors.surfacePanel.copy(alpha = 0.5f) else colors.surfaceElevated.copy(alpha = 0.35f))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail with rounded corners
        AsyncAlbumArt(
            uri = item.thumbnailUrl,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = VeylTypography.BodyMedium,
                color = if (isPlaying) colors.accentSignal else colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${item.artist} \u00b7 ${item.album}",
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = colors.accentSignal.copy(alpha = 0.15f)
                ) {
                    Text(
                        "320k STUDIO",
                        style = VeylTypography.MonoSpec,
                        color = colors.accentSignal,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                Text(
                    item.durationText,
                    style = VeylTypography.MonoSpec,
                    color = colors.textMuted,
                    fontSize = 9.sp
                )
            }
        }
        Spacer(Modifier.width(4.dp))

        // Quick download button to save track directly to device storage
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

        Spacer(Modifier.width(4.dp))

        if (isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.accentSignal
            )
        } else if (isPlaying) {
            Icon(
                VeylIcons.Waveform,
                contentDescription = "Playing",
                tint = colors.accentSignal,
                modifier = Modifier.size(18.dp)
            )
        } else {
            Surface(
                shape = CircleShape,
                color = colors.surfacePanel,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        VeylIcons.Play,
                        contentDescription = "Stream song",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchAlbumRow(
    albumName: String,
    artist: String,
    trackCount: Int,
    firstTrackUri: String,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncAlbumArt(
            uri = firstTrackUri,
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(8.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                albumName,
                style = VeylTypography.BodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "$artist \u00b7 $trackCount tracks",
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            VeylIcons.Play,
            contentDescription = "Play album",
            tint = colors.textMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun SearchArtistRow(
    artistName: String,
    trackCount: Int,
    albumCount: Int,
    firstTrackUri: String,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncAlbumArt(
            uri = firstTrackUri,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                artistName,
                style = VeylTypography.BodyMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append("$albumCount album")
                    if (albumCount != 1) append("s")
                    append(" \u00b7 $trackCount track")
                    if (trackCount != 1) append("s")
                },
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            VeylIcons.Play,
            contentDescription = "Play artist",
            tint = colors.textMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}
