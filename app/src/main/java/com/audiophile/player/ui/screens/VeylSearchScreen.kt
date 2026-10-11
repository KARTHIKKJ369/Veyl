package com.audiophile.player.ui.screens

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.TrackInfo

private const val MAX_RECENT_SEARCHES = 8

/**
 * Dedicated full-screen Search experience.
 *
 * Empty state  — recent search chips + recently-played / recently-added tracks.
 * Active query — results grouped by Songs, Albums, Artists.
 * Back press   — returns to the previous destination (Home).
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
    // Pre-computed off the main thread by updateDerivedTrackLists()
    val artistGroups by controller.artistGroups.collectAsStateWithLifecycle()
    val albumGroups by controller.albumGroups.collectAsStateWithLifecycle()

    val focusRequester = remember { FocusRequester() }
    var query by remember { mutableStateOf("") }
    val recentSearches = remember { mutableStateListOf<String>() }
    var showAllSongs by remember { mutableStateOf(false) }

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

    // Derive results from query
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

    val hasResults = songResults.isNotEmpty() || albumResults.isNotEmpty() || artistResults.isNotEmpty()

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
                .padding(horizontal = 12.dp, vertical = 10.dp),
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
                        "Songs, albums, artists\u2026",
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
                        items(listenNowTracks, key = { it.uri }) { track ->
                            SearchTrackRow(
                                track = track,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.playTrack(track)
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
                    if (!hasResults) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 48.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No results for \u201c$query\u201d",
                                    style = VeylTypography.BodyMedium,
                                    color = colors.textMuted
                                )
                            }
                        }
                    }

                    // Songs
                    if (songResults.isNotEmpty()) {
                        item { SearchSectionHeader(label = "Songs", count = songResults.size) }
                        val displayedSongs = if (showAllSongs) songResults else songResults.take(8)
                        items(displayedSongs, key = { "song_${it.uri}" }) { track ->
                            SearchTrackRow(
                                track = track,
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveRecentSearch(recentSearches, query, prefs)
                                    controller.playTrack(track)
                                }
                            )
                        }
                        if (songResults.size > 8) {
                            item {
                                Text(
                                    if (showAllSongs) "Show fewer songs" else "Show all ${songResults.size} songs",
                                    style = VeylTypography.BodySmall,
                                    color = colors.accentSignal,
                                    modifier = Modifier
                                        .clickable { showAllSongs = !showAllSongs }
                                        .padding(vertical = 6.dp, horizontal = 4.dp)
                                )
                            }
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // Albums
                    if (albumResults.isNotEmpty()) {
                        item { SearchSectionHeader(label = "Albums", count = albumResults.size) }
                        items(albumResults.take(6), key = { "album_${it.first}_${it.second}" }) { (album, artist, albumTracks) ->
                            SearchAlbumRow(
                                albumName = album,
                                artist = artist,
                                trackCount = albumTracks.size,
                                firstTrackUri = albumTracks.firstOrNull()?.uri ?: "",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveRecentSearch(recentSearches, query, prefs)
                                    if (albumTracks.isNotEmpty()) controller.playTrackList(albumTracks)
                                }
                            )
                        }
                        item { Spacer(Modifier.height(8.dp)) }
                    }

                    // Artists
                    if (artistResults.isNotEmpty()) {
                        item { SearchSectionHeader(label = "Artists", count = artistResults.size) }
                        items(artistResults.take(6), key = { "artist_${it.first}" }) { (artist, artistTracks, albumCount) ->
                            SearchArtistRow(
                                artistName = artist,
                                trackCount = artistTracks.size,
                                albumCount = albumCount,
                                firstTrackUri = artistTracks.firstOrNull()?.uri ?: "",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    saveRecentSearch(recentSearches, query, prefs)
                                    if (artistTracks.isNotEmpty()) controller.playTrackList(artistTracks)
                                }
                            )
                        }
                    }

                    item { Spacer(Modifier.navigationBarsPadding()) }
                }
            }
        }
    }
}

private fun saveRecentSearch(
    recentSearches: MutableList<String>,
    query: String,
    prefs: android.content.SharedPreferences? = null
) {
    val term = query.trim()
    if (term.isBlank()) return
    recentSearches.remove(term)
    recentSearches.add(0, term)
    while (recentSearches.size > MAX_RECENT_SEARCHES) {
        recentSearches.removeAt(recentSearches.lastIndex)
    }
    prefs?.edit()?.putString("recent_searches", recentSearches.joinToString("|||"))?.apply()
}

@Composable
private fun SearchSectionHeader(label: String, count: Int) {
    val colors = LocalVeylColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            label.uppercase(),
            style = VeylTypography.MonoBadge,
            color = colors.accentSignal
        )
        Surface(shape = RoundedCornerShape(6.dp), color = colors.surfacePanel) {
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
private fun SearchTrackRow(track: TrackInfo, onClick: () -> Unit) {
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
            "${track.formatName} ${track.bitDepth}b",
            style = VeylTypography.MonoSpec,
            color = colors.accentSignal,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold
        )
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
