package com.audiophile.player.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.StickyMiniPlayer
import com.audiophile.player.ui.screens.VeylEqualizerScreen
import com.audiophile.player.ui.screens.VeylHomeScreen
import com.audiophile.player.ui.screens.VeylNowPlayingScreen
import com.audiophile.player.ui.screens.VeylPlaylistDetailScreen
import com.audiophile.player.ui.screens.VeylQueueScreen
import com.audiophile.player.ui.screens.VeylSettingsScreen
import com.audiophile.player.ui.screens.VeylSongsScreen
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTheme
import com.audiophile.player.ui.theme.VeylTypography

sealed class VeylScreen(val route: String, val title: String, val icon: ImageVector) {
    object Home : VeylScreen("home", "Home", VeylIcons.StorageLocal)
    object Songs : VeylScreen("songs", "Songs", VeylIcons.Folder)
    object Favorites : VeylScreen("favorites", "Favorites", VeylIcons.FavoriteFilled)
    object Equalizer : VeylScreen("equalizer", "EQ", VeylIcons.Equalizer)
    object Settings : VeylScreen("settings", "Settings", VeylIcons.Settings)
    object NowPlaying : VeylScreen("now_playing", "Playing", VeylIcons.Play)
    object Queue : VeylScreen("queue", "Queue", VeylIcons.QueueList)
    object TopTracks : VeylScreen("top_tracks", "Top Tracks", VeylIcons.Equalizer)
    object LastAdded : VeylScreen("last_added", "Last Added", VeylIcons.Folder)
    object History : VeylScreen("history", "History", VeylIcons.QueueList)
}

@Composable
fun AudiophileNavHost(
    controller: AudioEngineController,
    onSelectRootFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isDarkMode by controller.isDarkMode.collectAsState()
    VeylTheme(isDarkMode = isDarkMode) {
        val colors = LocalVeylColors.current
        val navController = rememberNavController()
        val navBackStackEntry by navController.currentBackStackEntryAsState()
        val currentDestination = navBackStackEntry?.destination

        val currentTrack by controller.currentTrack.collectAsState()
        var isNowPlayingOpen by remember { mutableStateOf(false) }

        BackHandler(enabled = isNowPlayingOpen) {
            isNowPlayingOpen = false
        }

        val bottomNavItems = listOf(
            VeylScreen.Home,
            VeylScreen.Songs,
            VeylScreen.Favorites,
            VeylScreen.Equalizer,
            VeylScreen.Settings
        )

        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                containerColor = colors.background,
                contentColor = colors.textPrimary,
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfacePanel)
                    ) {
                        // Docked Sticky Mini Player (Visible when a track is active)
                        if (currentTrack != null) {
                            StickyMiniPlayer(
                                controller = controller,
                                onClick = { isNowPlayingOpen = true }
                            )
                        } else {
                            // Hairline border above nav bar when no song is active
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.borderHairline)
                            )
                        }

                        NavigationBar(
                            containerColor = colors.surfacePanel,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            bottomNavItems.forEach { screen ->
                                val isSelected = currentDestination?.route == screen.route
                                NavigationBarItem(
                                    icon = {
                                        Icon(
                                            imageVector = screen.icon,
                                            contentDescription = screen.title,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = screen.title,
                                            style = if (isSelected) VeylTypography.TitleMedium else VeylTypography.BodySmall,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) colors.accentSignal else colors.textMuted
                                        )
                                    },
                                    selected = isSelected,
                                    onClick = {
                                        if (currentDestination?.route != screen.route) {
                                            navController.navigate(screen.route)
                                        }
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colors.accentSignal,
                                        selectedTextColor = colors.accentSignal,
                                        indicatorColor = colors.surfaceElevated,
                                        unselectedIconColor = colors.textMuted,
                                        unselectedTextColor = colors.textMuted
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = VeylScreen.Home.route,
                modifier = modifier.padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                // Screen 1: Home (Bento Grid, Suggestions, Carousel)
                composable(
                    route = VeylScreen.Home.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylHomeScreen(
                        controller = controller,
                        onNavigateToSearch = {
                            navController.navigate(VeylScreen.Songs.route)
                        },
                        onNavigateToTopTracks = {
                            navController.navigate(VeylScreen.TopTracks.route)
                        },
                        onNavigateToLastAdded = {
                            navController.navigate(VeylScreen.LastAdded.route)
                        },
                        onNavigateToHistory = {
                            navController.navigate(VeylScreen.History.route)
                        },
                        onNavigateToQueue = {
                            navController.navigate(VeylScreen.Queue.route)
                        },
                        onNavigateToNowPlaying = {
                            isNowPlayingOpen = true
                        },
                        onPickFolder = onSelectRootFolder
                    )
                }

                // Screen 2: Songs (Fast searchable library list with tabs)
                composable(
                    route = VeylScreen.Songs.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylSongsScreen(
                        controller = controller,
                        onPickFolder = onSelectRootFolder,
                        onNavigateToPlaylist = { playlistName ->
                            navController.navigate("playlist/$playlistName")
                        }
                    )
                }

                // Screen 3: Favorites Screen
                composable(
                    route = VeylScreen.Favorites.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylPlaylistDetailScreen(
                        controller = controller,
                        title = "Favorites",
                        description = "The tracks I like the most",
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(VeylScreen.Home.route)
                            }
                        }
                    )
                }

                // Screen 4: Top Tracks Screen
                composable(
                    route = VeylScreen.TopTracks.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylPlaylistDetailScreen(
                        controller = controller,
                        title = "Top Tracks",
                        description = "Your most played tracks ranked by play count",
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(VeylScreen.Home.route)
                            }
                        }
                    )
                }

                // Screen 5: Last Added Screen
                composable(
                    route = VeylScreen.LastAdded.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylPlaylistDetailScreen(
                        controller = controller,
                        title = "Last added",
                        description = "Recently imported audio files",
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(VeylScreen.Home.route)
                            }
                        }
                    )
                }

                // Screen 6: History Screen
                composable(
                    route = VeylScreen.History.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylPlaylistDetailScreen(
                        controller = controller,
                        title = "History",
                        description = "Recently played tracks in chronological order",
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(VeylScreen.Home.route)
                            }
                        }
                    )
                }

                // Screen 7: Custom Playlist Detail Screen
                composable(
                    route = "playlist/{name}",
                    arguments = listOf(navArgument("name") { type = NavType.StringType }),
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) { backStackEntry ->
                    val playlistName = backStackEntry.arguments?.getString("name") ?: "Playlist"
                    VeylPlaylistDetailScreen(
                        controller = controller,
                        title = playlistName,
                        description = "Custom Playlist",
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(VeylScreen.Songs.route)
                            }
                        }
                    )
                }

                // Screen 8: Equalizer
                composable(
                    route = VeylScreen.Equalizer.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylEqualizerScreen(
                        controller = controller,
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(VeylScreen.Home.route)
                            }
                        }
                    )
                }

                // Screen 9: Settings
                composable(
                    route = VeylScreen.Settings.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylSettingsScreen(
                        controller = controller,
                        onSelectRootFolder = onSelectRootFolder
                    )
                }

                // Screen 10: Now Playing Screen Route (redirects to overlay)
                composable(route = VeylScreen.NowPlaying.route) {
                    androidx.compose.runtime.LaunchedEffect(Unit) {
                        isNowPlayingOpen = true
                        navController.popBackStack()
                    }
                }

                // Screen 11: Queue Screen
                composable(
                    route = VeylScreen.Queue.route,
                    enterTransition = { fadeIn(animationSpec = tween(180)) },
                    exitTransition = { fadeOut(animationSpec = tween(140)) }
                ) {
                    VeylQueueScreen(
                        controller = controller,
                        onNavigateBack = {
                            if (!navController.popBackStack()) {
                                navController.navigate(VeylScreen.Home.route)
                            }
                        }
                    )
                }
            }
        }

        // Fluid Fullscreen Now Playing Overlay with Zero Scaffold Re-layout
        AnimatedVisibility(
            visible = isNowPlayingOpen,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(240, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(200, easing = FastOutLinearInEasing)
            )
        ) {
            VeylNowPlayingScreen(
                controller = controller,
                onNavigateBack = { isNowPlayingOpen = false },
                onNavigateToQueue = {
                    isNowPlayingOpen = false
                    navController.navigate(VeylScreen.Queue.route)
                },
                onNavigateToDsp = {
                    isNowPlayingOpen = false
                    navController.navigate(VeylScreen.Equalizer.route)
                }
            )
        }
    }
    }
}
