package com.audiophile.player.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.ExpressiveCookieShape
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylSpacing
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.TrackInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylHomeScreen(
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
    val haptic = LocalHapticFeedback.current
    val tracks by controller.tracks.collectAsState()
    val favoriteUris by controller.favoriteUris.collectAsState()
    val status by controller.status.collectAsState()

    val currentTrack = status?.currentTrack

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 120.dp)
    ) {
        // 1. Top Header: Veyl branding + Search Action Icon (clean, no redundant settings button)
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Veyl",
                        style = VeylTypography.DisplayLarge,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 32.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = colors.accentSignal.copy(alpha = 0.15f),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(
                            text = "BIT-PERFECT",
                            style = VeylTypography.MonoSpec,
                            color = colors.accentSignal,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = colors.surfaceElevated,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateToSearch()
                    }) {
                        Icon(
                            imageVector = VeylIcons.Search,
                            contentDescription = "Search",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Top 3 Action Circles: Top Tracks, Last added, History
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                VeylActionCircle(
                    icon = VeylIcons.Equalizer,
                    label = "Top Tracks",
                    onClick = onNavigateToTopTracks
                )
                VeylActionCircle(
                    icon = VeylIcons.Folder,
                    label = "Last added",
                    onClick = onNavigateToLastAdded
                )
                VeylActionCircle(
                    icon = VeylIcons.QueueList,
                    label = "History",
                    onClick = onNavigateToHistory
                )
            }
        }

        item { Spacer(modifier = Modifier.height(16.dp)) }

        // 3. "For you" Section Title
        item {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) {
                Text(
                    text = "For you",
                    style = VeylTypography.HeadlineMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Your daily dose of musical inspiration",
                    style = VeylTypography.BodySmall,
                    color = colors.textMuted
                )
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // 4. "For You" Bento Grid
        item {
            val sampleTracks = remember(tracks) { tracks.take(6) }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Row 1: Left Slate Card ("Shuffle your music") + Right 2-column square tiles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Left Large Slate Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2E3B5B)),
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (tracks.isNotEmpty()) {
                                    controller.playShuffled(tracks)
                                }
                            }
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.Shuffle,
                                contentDescription = "Shuffle",
                                tint = Color.White.copy(alpha = 0.9f),
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(26.dp)
                            )
                            Text(
                                text = "Shuffle your\nmusic",
                                style = VeylTypography.SectionHeader,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 20.sp,
                                modifier = Modifier.align(Alignment.BottomStart)
                            )
                        }
                    }

                    // Right 2 mini album cards
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .height(180.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            val rightTrack1 = sampleTracks.getOrNull(1)
                            val rightTrack2 = sampleTracks.getOrNull(2)

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colors.surfaceElevated)
                                    .clickable {
                                        rightTrack1?.let {
                                            controller.playTrack(it)
                                        }
                                    }
                            ) {
                                if (rightTrack1 != null) {
                                    AsyncAlbumArt(
                                        uri = rightTrack1.uri,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(colors.surfaceElevated)
                                    .clickable {
                                        rightTrack2?.let {
                                            controller.playTrack(it)
                                        }
                                    }
                            ) {
                                if (rightTrack2 != null) {
                                    AsyncAlbumArt(
                                        uri = rightTrack2.uri,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }
                }

                // Row 2: Left Large Square Art + Right Landscape Featured Track
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val bottomTrack1 = sampleTracks.getOrNull(3)
                    val bottomTrack2 = sampleTracks.getOrNull(4)

                    // Left Big Square Card
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable {
                                bottomTrack1?.let {
                                    controller.playTrack(it)
                                }
                            }
                    ) {
                        if (bottomTrack1 != null) {
                            AsyncAlbumArt(
                                uri = bottomTrack1.uri,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize().background(colors.surfaceElevated))
                        }
                    }

                    // Right Landscape Card with Title & Artist overlay
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .clickable {
                                bottomTrack2?.let {
                                    controller.playTrack(it)
                                }
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            if (bottomTrack2 != null) {
                                AsyncAlbumArt(
                                    uri = bottomTrack2.uri,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                                            )
                                        )
                                )
                                Column(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(14.dp)
                                ) {
                                    Text(
                                        text = bottomTrack2.title,
                                        style = VeylTypography.Body,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = bottomTrack2.artist ?: "Unknown Artist",
                                        style = VeylTypography.BodySmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }

        // 5. "Listen now" Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Listen now",
                        style = VeylTypography.HeadlineMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Dive into your latest collections",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                }

                Surface(
                    shape = ExpressiveCookieShape(9),
                    color = colors.surfaceElevated,
                    modifier = Modifier.size(38.dp)
                ) {
                    IconButton(onClick = {
                        if (tracks.isNotEmpty()) {
                            controller.playShuffled(tracks)
                        }
                    }) {
                        Icon(
                            imageVector = VeylIcons.Shuffle,
                            contentDescription = "Shuffle all",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(12.dp)) }

        // 6. Horizontal Hero Carousel
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(tracks.take(12)) { track ->
                    Card(
                        shape = RoundedCornerShape(22.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        modifier = Modifier
                            .size(width = 240.dp, height = 150.dp)
                            .clickable {
                                controller.playTrack(track)
                            }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncAlbumArt(
                                uri = track.uri,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                                        )
                                    )
                            )
                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = track.title,
                                    style = VeylTypography.Body,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist ?: "Unknown Artist",
                                    style = VeylTypography.BodySmall,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VeylActionCircle(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        }
    ) {
        Surface(
            shape = CircleShape,
            color = colors.surfaceElevated,
            modifier = Modifier.size(64.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            style = VeylTypography.BodySmall,
            color = colors.textPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp
        )
    }
}
