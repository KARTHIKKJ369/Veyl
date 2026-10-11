package com.audiophile.player.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AppExperienceMode
import com.audiophile.player.engine.AsyncAlbumArt
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.online.CloudRecommendationService
import com.audiophile.player.online.JioSaavnService
import com.audiophile.player.online.OnlineArtistItem
import com.audiophile.player.online.PopularArtist
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import com.audiophile.player.ui.theme.isColorLight
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VeylOnboardingScreen(
    controller: AudioEngineController,
    onSelectRootFolder: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    var step by remember { mutableStateOf(1) } // 1: Choose Mode, 2: Setup (Cloud taste or Local dir)
    var selectedMode by remember { mutableStateOf(AppExperienceMode.CLOUD_FIRST) }

    // Cloud taste state
    val selectedLanguages = remember { mutableStateListOf("Malayalam", "Tamil", "English", "Hindi") }
    val selectedArtists = remember {
        mutableStateListOf("A.R. Rahman", "Sushin Shyam", "Anirudh Ravichander", "Arijit Singh")
    }

    // Live artist search state
    var artistSearchQuery by remember { mutableStateOf("") }
    var artistSearchResults by remember { mutableStateOf<List<OnlineArtistItem>>(emptyList()) }
    var isSearchingArtists by remember { mutableStateOf(false) }

    // Debounced artist search effect
    LaunchedEffect(artistSearchQuery) {
        val trimmed = artistSearchQuery.trim()
        if (trimmed.length < 2) {
            artistSearchResults = emptyList()
            isSearchingArtists = false
            return@LaunchedEffect
        }
        isSearchingArtists = true
        delay(220) // debounce typing
        val results = JioSaavnService.searchArtists(trimmed)
        artistSearchResults = results
        isSearchingArtists = false
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        AnimatedContent(
            targetState = step,
            transitionSpec = {
                if (targetState > initialState) {
                    (slideInHorizontally { width -> width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> -width } + fadeOut()
                    )
                } else {
                    (slideInHorizontally { width -> -width } + fadeIn()).togetherWith(
                        slideOutHorizontally { width -> width } + fadeOut()
                    )
                }
            },
            label = "OnboardingStepTransition"
        ) { currentStep ->
            when (currentStep) {
                1 -> {
                    // STEP 1: Experience Choice
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 24.dp),
                            contentPadding = PaddingValues(top = 28.dp, bottom = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(20.dp)
                        ) {
                            item {
                                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                                    Text(
                                        text = "Veyl",
                                        style = VeylTypography.DisplayLarge,
                                        color = colors.accentSignal,
                                        fontSize = 36.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        text = "Choose your music experience",
                                        style = VeylTypography.HeadlineMedium,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 22.sp
                                    )
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Select how you'd like to use Veyl. You can freely switch anytime in Settings.",
                                        style = VeylTypography.Body,
                                        color = colors.textMuted,
                                        lineHeight = 20.sp
                                    )
                                }
                            }

                            item {
                                ExperienceCard(
                                    title = "Cloud First (Streaming)",
                                    tag = "Recommended",
                                    description = "Spotify-style dynamic feed. Discover trending hits, personalized mixes & curated recommendations tailored to your taste.",
                                    badge = "Free • 320k Studio Master",
                                    isSelected = selectedMode == AppExperienceMode.CLOUD_FIRST,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedMode = AppExperienceMode.CLOUD_FIRST
                                    }
                                )
                            }

                            item {
                                ExperienceCard(
                                    title = "Local First (Audiophile)",
                                    tag = "Offline Storage",
                                    description = "Folder & file-based player for your downloaded music. Pure bit-perfect audio directly from your device storage.",
                                    badge = "Bit-Perfect • Lossless & DSD",
                                    isSelected = selectedMode == AppExperienceMode.LOCAL_FIRST,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        selectedMode = AppExperienceMode.LOCAL_FIRST
                                    }
                                )
                            }
                        }

                        // Fixed Bottom Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 20.dp)
                        ) {
                            VeylPrimaryButton(
                                text = "Continue",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    step = 2
                                }
                            )
                        }
                    }
                }

                2 -> {
                    // STEP 2: Questionnaire (Cloud) or Directory (Local)
                    if (selectedMode == AppExperienceMode.CLOUD_FIRST) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .statusBarsPadding(),
                                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 100.dp),
                                verticalArrangement = Arrangement.spacedBy(24.dp)
                            ) {
                                // Top Header
                                item {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IconButton(
                                                onClick = { step = 1 },
                                                modifier = Modifier
                                                    .size(40.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.surfaceElevated)
                                            ) {
                                                Icon(
                                                    VeylIcons.ArrowBack,
                                                    contentDescription = "Back",
                                                    tint = colors.textPrimary,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column {
                                                Text(
                                                    text = "STEP 2 OF 2",
                                                    style = VeylTypography.MonoBadge,
                                                    color = colors.accentSignal,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = "Tailor Your Taste",
                                                    style = VeylTypography.HeadlineMedium,
                                                    color = colors.textPrimary,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 22.sp
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = "Pick the languages and artists you enjoy. We'll use this to build your daily recommendation mixes and trending radar.",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textMuted,
                                            lineHeight = 18.sp,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }

                                // Section 1: Languages
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "What languages do you listen to?",
                                                style = VeylTypography.TitleMedium,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "${selectedLanguages.size} chosen",
                                                style = VeylTypography.MonoSpec,
                                                color = colors.accentSignal,
                                                fontSize = 11.sp
                                            )
                                        }

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            CloudRecommendationService.curatedLanguages.forEach { lang ->
                                                val isSelected = selectedLanguages.contains(lang.code)
                                                LanguageCard(
                                                    name = lang.name,
                                                    nativeScript = lang.nativeScript,
                                                    isSelected = isSelected,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        if (isSelected) {
                                                            if (selectedLanguages.size > 1) selectedLanguages.remove(lang.code)
                                                        } else {
                                                            selectedLanguages.add(lang.code)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                // Section 2: Artist Search & Recommendations
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "Favorite Artists & Seeds",
                                                style = VeylTypography.TitleMedium,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            if (selectedArtists.isNotEmpty()) {
                                                Text(
                                                    text = "${selectedArtists.size} selected",
                                                    style = VeylTypography.MonoSpec,
                                                    color = colors.accentSignal,
                                                    fontSize = 11.sp
                                                )
                                            }
                                        }

                                        // Live Search Bar
                                        OutlinedTextField(
                                            value = artistSearchQuery,
                                            onValueChange = { artistSearchQuery = it },
                                            placeholder = {
                                                Text(
                                                    "Search any artist (e.g. Taylor Swift, Rahman)...",
                                                    style = VeylTypography.BodySmall,
                                                    color = colors.textMuted
                                                )
                                            },
                                            leadingIcon = {
                                                Icon(
                                                    VeylIcons.Search,
                                                    contentDescription = "Search",
                                                    tint = if (artistSearchQuery.isNotBlank()) colors.accentSignal else colors.textMuted,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            },
                                            trailingIcon = {
                                                if (isSearchingArtists) {
                                                    CircularProgressIndicator(
                                                        modifier = Modifier.size(18.dp),
                                                        strokeWidth = 2.dp,
                                                        color = colors.accentSignal
                                                    )
                                                } else if (artistSearchQuery.isNotBlank()) {
                                                    IconButton(onClick = { artistSearchQuery = "" }) {
                                                        Icon(
                                                            VeylIcons.Close,
                                                            contentDescription = "Clear",
                                                            tint = colors.textMuted,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = RoundedCornerShape(14.dp),
                                            colors = OutlinedTextFieldDefaults.colors(
                                                focusedBorderColor = colors.accentSignal,
                                                unfocusedBorderColor = colors.borderSubtle,
                                                focusedTextColor = colors.textPrimary,
                                                unfocusedTextColor = colors.textPrimary,
                                                focusedContainerColor = colors.surfaceElevated.copy(alpha = 0.5f),
                                                unfocusedContainerColor = colors.surfaceElevated.copy(alpha = 0.3f),
                                                cursorColor = colors.accentSignal
                                            )
                                        )

                                        // Live Search Recommendations Dropdown Panel
                                        AnimatedVisibility(
                                            visible = artistSearchQuery.trim().length >= 2,
                                            enter = fadeIn() + slideInVertically(),
                                            exit = fadeOut() + slideOutVertically()
                                        ) {
                                            Surface(
                                                modifier = Modifier.fillMaxWidth(),
                                                shape = RoundedCornerShape(16.dp),
                                                color = colors.surfaceElevated,
                                                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle)
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(12.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Text(
                                                        text = "SEARCH RESULTS",
                                                        style = VeylTypography.MonoBadge,
                                                        color = colors.accentSignal,
                                                        fontSize = 10.sp,
                                                        modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                                                    )

                                                    if (artistSearchResults.isEmpty() && !isSearchingArtists) {
                                                        // Option to add directly as custom
                                                        Row(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .clickable {
                                                                    val q = artistSearchQuery.trim()
                                                                    if (q.isNotBlank() && !selectedArtists.contains(q)) {
                                                                        selectedArtists.add(q)
                                                                        artistSearchQuery = ""
                                                                    }
                                                                }
                                                                .padding(vertical = 10.dp, horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                text = "Add \"${artistSearchQuery.trim()}\"",
                                                                style = VeylTypography.TitleMedium,
                                                                color = colors.textPrimary
                                                            )
                                                            Text(
                                                                text = "＋ Add",
                                                                style = VeylTypography.TitleMedium,
                                                                color = colors.accentSignal,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                        }
                                                    } else {
                                                        artistSearchResults.take(6).forEach { item ->
                                                            val isAlreadySelected = selectedArtists.contains(item.name)
                                                            ArtistSearchRow(
                                                                artist = item,
                                                                isSelected = isAlreadySelected,
                                                                onToggle = {
                                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                    if (isAlreadySelected) {
                                                                        selectedArtists.remove(item.name)
                                                                    } else {
                                                                        selectedArtists.add(item.name)
                                                                    }
                                                                }
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        // Selected Artists Horizontal Strip
                                        if (selectedArtists.isNotEmpty()) {
                                            Column(modifier = Modifier.padding(top = 4.dp)) {
                                                LazyRow(
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                    modifier = Modifier.fillMaxWidth()
                                                ) {
                                                    items(selectedArtists, key = { it }) { artistName ->
                                                        SelectedArtistChip(
                                                            name = artistName,
                                                            onRemove = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                                selectedArtists.remove(artistName)
                                                            }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                // Section 3: Curated Visual Artists Grid
                                item {
                                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                                        Text(
                                            text = "Popular For Your Languages",
                                            style = VeylTypography.TitleMedium,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp
                                        )

                                        // Prioritize artists matching selected languages
                                        val sortedPopular = remember(selectedLanguages.toList()) {
                                            CloudRecommendationService.popularArtists.sortedByDescending { artist ->
                                                selectedLanguages.contains(artist.language)
                                            }
                                        }

                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalArrangement = Arrangement.spacedBy(16.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            sortedPopular.take(24).forEach { artist ->
                                                val isSelected = selectedArtists.contains(artist.name)
                                                ArtistAvatarCard(
                                                    artist = artist,
                                                    isSelected = isSelected,
                                                    onClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        if (isSelected) {
                                                            selectedArtists.remove(artist.name)
                                                        } else {
                                                            selectedArtists.add(artist.name)
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Floating Bottom Action Bar with Scrim
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                colors.background.copy(alpha = 0.85f),
                                                colors.background
                                            )
                                        )
                                    )
                                    .navigationBarsPadding()
                                    .padding(horizontal = 20.dp, vertical = 16.dp)
                            ) {
                                VeylPrimaryButton(
                                    text = if (selectedArtists.isNotEmpty()) "Start Listening (${selectedArtists.size} artists)" else "Start Listening",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.completeOnboarding(
                                            mode = AppExperienceMode.CLOUD_FIRST,
                                            languages = selectedLanguages.toSet(),
                                            artists = selectedArtists.toSet()
                                        )
                                    }
                                )
                            }
                        }
                    } else {
                        // LOCAL FIRST STEP 2: Setup directory
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .navigationBarsPadding()
                        ) {
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 24.dp),
                                contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                item {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        IconButton(
                                            onClick = { step = 1 },
                                            modifier = Modifier
                                                .size(40.dp)
                                                .clip(CircleShape)
                                                .background(colors.surfaceElevated)
                                        ) {
                                            Icon(
                                                VeylIcons.ArrowBack,
                                                contentDescription = "Back",
                                                tint = colors.textPrimary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        Spacer(Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = "STEP 2 OF 2",
                                                style = VeylTypography.MonoBadge,
                                                color = colors.accentSignal,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = "Select Music Directory",
                                                style = VeylTypography.HeadlineMedium,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 22.sp
                                            )
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = "Veyl will scan your high-resolution audio files (FLAC, DSD, DSF, WAV, MP3) and build your bit-perfect offline library.",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textMuted,
                                        lineHeight = 18.sp
                                    )
                                }

                                item {
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable(onClick = onSelectRootFolder),
                                        shape = RoundedCornerShape(20.dp),
                                        color = colors.surfaceElevated,
                                        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentSignal.copy(alpha = 0.4f))
                                    ) {
                                        Column(
                                            modifier = Modifier.padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(14.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(60.dp)
                                                    .clip(CircleShape)
                                                    .background(colors.accentSignal.copy(alpha = 0.15f)),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    VeylIcons.Folder,
                                                    contentDescription = null,
                                                    tint = colors.accentSignal,
                                                    modifier = Modifier.size(28.dp)
                                                )
                                            }

                                            Text(
                                                text = "Choose Storage Folder",
                                                style = VeylTypography.TitleMedium,
                                                color = colors.textPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 16.sp
                                            )

                                            Text(
                                                text = "Tap to open system file picker and grant permission to your Music folder.",
                                                style = VeylTypography.BodySmall,
                                                color = colors.textMuted,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp, vertical = 20.dp)
                            ) {
                                VeylPrimaryButton(
                                    text = "Enter Veyl",
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.completeOnboarding(
                                            mode = AppExperienceMode.LOCAL_FIRST,
                                            languages = emptySet(),
                                            artists = emptySet()
                                        )
                                        controller.rescanLibrary()
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * High-contrast, theme-aware primary button.
 * Guarantees readable dark text on white/accent containers.
 */
@Composable
fun VeylPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val isLight = isColorLight(colors.accentSignal)
    val contentColor = if (isLight) Color(0xFF0F0F0F) else Color.White

    Button(
        onClick = {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            onClick()
        },
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.accentSignal,
            contentColor = contentColor,
            disabledContainerColor = colors.surfaceElevated,
            disabledContentColor = colors.textMuted
        )
    ) {
        Text(
            text = text,
            style = VeylTypography.TitleMedium,
            fontWeight = FontWeight.Bold,
            color = if (enabled) contentColor else colors.textMuted,
            fontSize = 15.sp
        )
    }
}

@Composable
private fun ExperienceCard(
    title: String,
    tag: String,
    description: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = if (isSelected) colors.surfaceElevated else colors.surfacePanel,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) colors.accentSignal else colors.borderSubtle
        )
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tag.uppercase(),
                    style = VeylTypography.MonoSpec,
                    color = if (isSelected) colors.accentSignal else colors.textMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(colors.accentSignal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            VeylIcons.Check,
                            contentDescription = null,
                            tint = if (isColorLight(colors.accentSignal)) Color(0xFF0F0F0F) else Color.White,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }

            Text(
                text = title,
                style = VeylTypography.HeadlineMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Text(
                text = description,
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                lineHeight = 18.sp
            )

            Spacer(Modifier.height(4.dp))
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = colors.surfacePanel.copy(alpha = 0.8f)
            ) {
                Text(
                    text = badge,
                    style = VeylTypography.BodySmall,
                    color = colors.accentSignal,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun LanguageCard(
    name: String,
    nativeScript: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current

    Surface(
        modifier = Modifier
            .clickable(onClick = onClick)
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = if (isSelected) colors.accentSignal.copy(alpha = 0.18f) else colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) colors.accentSignal else colors.borderSubtle
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
        ) {
            if (isSelected) {
                Icon(
                    VeylIcons.Check,
                    contentDescription = null,
                    tint = colors.accentSignal,
                    modifier = Modifier.size(13.dp)
                )
            }
            Column {
                Text(
                    text = name,
                    style = VeylTypography.BodySmall,
                    color = if (isSelected) colors.accentSignal else colors.textPrimary,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = nativeScript,
                    style = VeylTypography.BodySmall,
                    color = if (isSelected) colors.accentSignal.copy(alpha = 0.8f) else colors.textMuted,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun ArtistSearchRow(
    artist: OnlineArtistItem,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val colors = LocalVeylColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Mini Avatar
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(colors.surfacePanel),
            contentAlignment = Alignment.Center
        ) {
            if (artist.imageUrl.isNotBlank()) {
                AsyncAlbumArt(
                    uri = artist.imageUrl,
                    contentDescription = artist.name,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Text(
                    text = artist.name.take(1).uppercase(),
                    style = VeylTypography.TitleMedium,
                    color = colors.accentSignal,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = VeylTypography.TitleMedium,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = artist.role.ifBlank { "Artist" },
                style = VeylTypography.BodySmall,
                color = colors.textMuted,
                fontSize = 11.sp
            )
        }

        // Toggle Pill
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isSelected) colors.accentSignal else colors.surfacePanel,
            border = if (!isSelected) androidx.compose.foundation.BorderStroke(1.dp, colors.borderSubtle) else null
        ) {
            val textColor = if (isSelected) {
                if (isColorLight(colors.accentSignal)) Color(0xFF0F0F0F) else Color.White
            } else {
                colors.textPrimary
            }
            Text(
                text = if (isSelected) "✓ Added" else "＋ Add",
                style = VeylTypography.BodySmall,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun SelectedArtistChip(
    name: String,
    onRemove: () -> Unit
) {
    val colors = LocalVeylColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.accentSignal.copy(alpha = 0.16f),
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentSignal.copy(alpha = 0.5f))
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp)
        ) {
            Text(
                text = name,
                style = VeylTypography.BodySmall,
                color = colors.accentSignal,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(18.dp)
            ) {
                Icon(
                    VeylIcons.Close,
                    contentDescription = "Remove",
                    tint = colors.accentSignal,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun ArtistAvatarCard(
    artist: PopularArtist,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current
    var resolvedImage by remember(artist.name) { mutableStateOf(artist.imageUrl) }

    LaunchedEffect(artist.name) {
        if (resolvedImage.isBlank()) {
            val img = CloudRecommendationService.getArtistPortrait(artist.name)
            if (img.isNotBlank()) {
                resolvedImage = img
            }
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(colors.surfaceElevated)
                .then(
                    if (isSelected) {
                        Modifier.border(2.5.dp, colors.accentSignal, CircleShape)
                    } else {
                        Modifier.border(1.dp, colors.borderSubtle, CircleShape)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (resolvedImage.isNotBlank()) {
                AsyncAlbumArt(
                    uri = resolvedImage,
                    contentDescription = artist.name,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(colors.surfaceElevated, colors.surfacePanel)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = artist.name.take(1).uppercase(),
                        style = VeylTypography.TitleLarge,
                        color = colors.accentSignal,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                }
            }

            // Selected Checkmark Overlay
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(colors.accentSignal),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            VeylIcons.Check,
                            contentDescription = null,
                            tint = if (isColorLight(colors.accentSignal)) Color(0xFF0F0F0F) else Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = artist.name,
            style = VeylTypography.BodySmall,
            color = if (isSelected) colors.accentSignal else colors.textPrimary,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp
        )
    }
}
