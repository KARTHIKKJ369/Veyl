package com.audiophile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylSpacing
import com.audiophile.player.ui.theme.VeylTypography

data class AutoEqProfile(
    val id: String,
    val name: String,
    val brand: String,
    val type: String,
    val target: String,
    val description: String,
    val gains: List<Float>
)

val CURATED_AUTOEQ_PROFILES = listOf(
    AutoEqProfile(
        id = "tangzu_waner_sg",
        name = "Tangzu Wan'er S.G",
        brand = "Tangzu",
        type = "Single Dynamic Driver IEM",
        target = "Harman In-Ear 2019",
        description = "Sub-bass extension (+1.5 dB) with gentle 2 kHz attenuation (-2.0 dB) to smooth vocal pinna shoutiness and open up airy 8 kHz treble.",
        gains = listOf(1.5f, 1.0f, 0.0f, -0.5f, 0.5f, 0.0f, -2.0f, 2.0f, 3.0f, 1.0f)
    ),
    AutoEqProfile(
        id = "sennheiser_hd600",
        name = "Sennheiser HD 600 / HD 650",
        brand = "Sennheiser",
        type = "Open-Back Dynamic",
        target = "Harman Over-Ear 2018",
        description = "Restores missing sub-bass (+3.5 dB) while preserving world-class midrange timbre and vocal warmth.",
        gains = listOf(3.5f, 2.5f, 0.5f, 0.0f, 0.0f, 0.0f, -0.5f, 1.0f, -1.0f, 1.5f)
    ),
    AutoEqProfile(
        id = "sennheiser_hd800s",
        name = "Sennheiser HD 800 S",
        brand = "Sennheiser",
        type = "Open-Back Reference",
        target = "Harman Over-Ear 2018",
        description = "Deep sub-bass extension (+5.0 dB) and smooth taming of the 6 kHz treble peak for expansive fatigue-free staging.",
        gains = listOf(5.0f, 3.5f, 1.0f, 0.0f, -0.5f, 0.0f, 1.0f, -3.5f, 2.0f, 1.0f)
    ),
    AutoEqProfile(
        id = "sony_wh1000xm4",
        name = "Sony WH-1000XM4 / XM5",
        brand = "Sony",
        type = "Closed Wireless ANC",
        target = "Harman Neutral Target",
        description = "Cuts bloated 125 Hz mid-bass boom (-3.5 dB) to dramatically reveal vocal transparency and airy instrument separation.",
        gains = listOf(-1.0f, -2.5f, -3.5f, -2.0f, 0.0f, 1.5f, 2.5f, 2.0f, 3.0f, 2.5f)
    ),
    AutoEqProfile(
        id = "apple_airpods_pro",
        name = "Apple AirPods Pro (1 & 2)",
        brand = "Apple",
        type = "True Wireless In-Ear",
        target = "Harman In-Ear 2019",
        description = "Deep visceral sub-bass punch (+2.0 dB) with smoothing around 6-8 kHz sibilance for balanced hi-fi listening.",
        gains = listOf(2.0f, 1.5f, 0.0f, -0.5f, 0.0f, 0.5f, 1.0f, -1.5f, -1.0f, 1.0f)
    ),
    AutoEqProfile(
        id = "moondrop_blessing",
        name = "Moondrop Blessing 2 / 3 & Kato",
        brand = "Moondrop",
        type = "Hybrid Multi-Driver IEM",
        target = "Harman Target IEM",
        description = "Adds tactile sub-bass authority while preserving pristine hybrid armature treble detail and pinpoint imaging.",
        gains = listOf(2.0f, 1.5f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f, 1.5f, -0.5f, 1.0f)
    ),
    AutoEqProfile(
        id = "beyer_dt770_990",
        name = "Beyerdynamic DT 770 / 990 Pro",
        brand = "Beyerdynamic",
        type = "Studio Monitor",
        target = "Harman Over-Ear Target",
        description = "Eliminates piercing 6-8 kHz Beyerdynamic treble spike (-4.5 dB) while maintaining tight punchy studio bass.",
        gains = listOf(1.5f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, -4.5f, -2.0f, 1.5f)
    ),
    AutoEqProfile(
        id = "audiotechnica_m50x",
        name = "Audio-Technica ATH-M50x",
        brand = "Audio-Technica",
        type = "Closed Studio Monitor",
        target = "Harman Target Over-Ear",
        description = "Cleans up upper bass resonance (-2.0 dB) and lifts vocal clarity for flat, honest monitoring accuracy.",
        gains = listOf(-1.5f, -2.0f, -1.5f, 0.0f, 0.5f, 0.0f, 1.5f, -1.0f, 0.0f, 1.0f)
    ),
    AutoEqProfile(
        id = "hifiman_sundara",
        name = "Hifiman Sundara / Arya",
        brand = "Hifiman",
        type = "Planar Magnetic",
        target = "Planar Harman Curve",
        description = "Compensates for planar open-back bass roll-off (+4.5 dB sub-bass) delivering thunderous transient slam.",
        gains = listOf(4.5f, 3.0f, 1.0f, 0.0f, 0.0f, 0.0f, 0.0f, 1.5f, -1.5f, 2.0f)
    )
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylAutoEqBottomSheet(
    controller: AudioEngineController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val focusManager = LocalFocusManager.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var searchQuery by remember { mutableStateOf("") }
    var selectedBrand by remember { mutableStateOf("All") }
    var showCustomInput by remember { mutableStateOf(false) }
    var customInputText by remember { mutableStateOf("") }
    var customInputError by remember { mutableStateOf<String?>(null) }

    val currentProfileName by controller.currentEqPreset.collectAsState()
    val isEqEnabled by controller.isEqEnabled.collectAsState()

    val brands = listOf("All", "Tangzu", "Sennheiser", "Sony", "Apple", "Moondrop", "Beyerdynamic", "Audio-Technica", "Hifiman")

    val filteredProfiles = remember(searchQuery, selectedBrand) {
        CURATED_AUTOEQ_PROFILES.filter { profile ->
            val matchesBrand = selectedBrand == "All" || profile.brand.equals(selectedBrand, ignoreCase = true)
            val matchesSearch = searchQuery.isBlank() ||
                    profile.name.contains(searchQuery, ignoreCase = true) ||
                    profile.brand.contains(searchQuery, ignoreCase = true) ||
                    profile.type.contains(searchQuery, ignoreCase = true)
            matchesBrand && matchesSearch
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfacePanel,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(36.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.borderHairline.copy(alpha = 0.8f))
            )
        },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.88f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentSignal.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = VeylIcons.Headphones,
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "AutoEQ Headphone Profiles",
                            style = VeylTypography.HeadlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Acoustic Harman Target compensation",
                            style = VeylTypography.BodySmall,
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onDismiss()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.Close,
                        contentDescription = "Close",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = {
                    Text(
                        text = "Search headphones (e.g. HD 600, XM4, AirPods...)",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = VeylIcons.Search,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = VeylIcons.Close,
                                contentDescription = "Clear",
                                tint = colors.textSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = colors.accentSignal,
                    unfocusedBorderColor = colors.borderHairline,
                    focusedContainerColor = colors.surfaceElevated,
                    unfocusedContainerColor = colors.surfaceElevated,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                modifier = Modifier.fillMaxWidth()
            )

            // Brand Filter Chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(brands) { brand ->
                    val isSelected = selectedBrand == brand
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            selectedBrand = brand
                        },
                        label = {
                            Text(
                                text = brand,
                                style = VeylTypography.BodySmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.surfacePanel else colors.textPrimary,
                                fontSize = 12.sp
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = colors.surfaceElevated,
                            selectedContainerColor = colors.accentSignal,
                            labelColor = colors.textPrimary,
                            selectedLabelColor = colors.surfacePanel
                        ),
                        shape = RoundedCornerShape(10.dp)
                    )
                }
            }

            // Custom Text Import Row Toggle
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showCustomInput = !showCustomInput
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Plus,
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Import Custom AutoEQ Values / Text",
                            style = VeylTypography.TitleMedium,
                            fontWeight = FontWeight.Medium,
                            color = colors.textPrimary,
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = if (showCustomInput) "Hide" else "Paste",
                        style = VeylTypography.MonoSpec,
                        color = colors.accentSignal,
                        fontSize = 11.sp
                    )
                }
            }

            // Custom Input Box (Expandable)
            AnimatedVisibility(
                visible = showCustomInput,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.surfaceElevated)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Paste 10-band comma/space separated dB gains (e.g. 3.5, 2.0, 0, -1.5, ...):",
                        style = VeylTypography.BodySmall,
                        color = colors.textSecondary,
                        fontSize = 11.5.sp
                    )
                    OutlinedTextField(
                        value = customInputText,
                        onValueChange = {
                            customInputText = it
                            customInputError = null
                        },
                        placeholder = {
                            Text(
                                text = "3.5, 2.5, 0.5, 0.0, 0.0, 0.0, -0.5, 1.0, -1.0, 1.5",
                                style = VeylTypography.MonoSpec,
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )
                        },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentSignal,
                            unfocusedBorderColor = colors.borderHairline,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary
                        )
                    )

                    if (customInputError != null) {
                        Text(
                            text = customInputError!!,
                            style = VeylTypography.BodySmall,
                            color = Color(0xFFFF6584),
                            fontSize = 11.sp
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.accentSignal,
                            modifier = Modifier.clickable {
                                val tokens = customInputText
                                    .replace(";", ",")
                                    .split(",", " ", "\n", "\t")
                                    .mapNotNull { it.trim().toFloatOrNull() }

                                if (tokens.size >= 10) {
                                    val gains = tokens.take(10)
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.setEqPreset("AutoEQ Custom", gains)
                                    controller.toggleEq(true)
                                    onDismiss()
                                } else {
                                    customInputError = "Found ${tokens.size} gain values. Exactly 10 required (one per ISO band)."
                                }
                            }
                        ) {
                            Text(
                                text = "Apply Custom AutoEQ",
                                style = VeylTypography.BodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.background,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Headphone List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(filteredProfiles, key = { it.id }) { profile ->
                    val isActive = isEqEnabled && (currentProfileName == profile.name)
                    AutoEqCard(
                        profile = profile,
                        isActive = isActive,
                        onApply = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.setEqPreset(profile.name, profile.gains)
                        }
                    )
                }

                if (filteredProfiles.isEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "No matching headphones found",
                                style = VeylTypography.TitleMedium,
                                color = colors.textPrimary
                            )
                            Text(
                                text = "Try searching for a different model or paste custom AutoEQ parameters above.",
                                style = VeylTypography.BodySmall,
                                color = colors.textMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AutoEqCard(
    profile: AutoEqProfile,
    isActive: Boolean,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) colors.accentSignal.copy(alpha = 0.08f) else colors.surfaceElevated,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (isActive) colors.accentSignal.copy(alpha = 0.6f) else colors.borderHairline
        ),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onApply)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = profile.name,
                        style = VeylTypography.TitleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) colors.accentSignal else colors.textPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.accentSignal.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = profile.target.uppercase(),
                                style = VeylTypography.MonoBadge,
                                color = colors.accentSignal,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(colors.surfacePanel)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = profile.type,
                                style = VeylTypography.MonoBadge,
                                color = colors.textMuted,
                                fontSize = 9.sp
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (isActive) colors.accentSignal else colors.surfacePanel,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isActive) colors.accentSignal else colors.accentSignal.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.clickable(onClick = onApply)
                ) {
                    Text(
                        text = if (isActive) "Active ✓" else "Apply",
                        style = VeylTypography.BodySmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) colors.surfacePanel else colors.accentSignal,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = profile.description,
                style = VeylTypography.BodySmall,
                color = colors.textSecondary,
                fontSize = 11.5.sp,
                lineHeight = 15.sp
            )

            // 10-Band Gain Bar Visualizer (ISO: 31, 63, 125, 250, 500, 1k, 2k, 4k, 8k, 16k)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(26.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(colors.surfacePanel.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                profile.gains.forEach { gain ->
                    val normalizedHeight = ((gain.coerceIn(-6f, 6f) + 6f) / 12f).coerceIn(0.15f, 1.0f)
                    val barColor = when {
                        gain > 0.5f -> colors.accentSignal
                        gain < -0.5f -> Color(0xFF4C8DFF)
                        else -> colors.textMuted
                    }
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .fillMaxHeight(normalizedHeight)
                            .clip(RoundedCornerShape(2.dp))
                            .background(barColor)
                    )
                }
            }
        }
    }
}
