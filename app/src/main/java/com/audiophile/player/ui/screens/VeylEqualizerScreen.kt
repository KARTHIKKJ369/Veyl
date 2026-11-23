package com.audiophile.player.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.abs
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.EqualizerWaveform
import com.audiophile.player.ui.components.VeylAutoEqBottomSheet
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.CrossfeedPresetEnum

/**
 * 3-Tab Segmented Audio DSP Suite:
 * - EQ: Parametric 10-band IIR biquad curve, presets, and band sliders
 * - Enhance: 360° Spatializer, Sub-Bass Exciter, Vocal Clarity, Tube Warmth
 * - Acoustics: Crossfeed, Loudness Normalizer, Peak Limiter, Channel Balance
 */
enum class DspTab(val title: String) {
    EQ("EQ"),
    ENHANCE("Enhance"),
    ACOUSTICS("Acoustics")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylEqualizerScreen(
    controller: AudioEngineController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    var selectedTab by remember { mutableStateOf(DspTab.EQ) }

    // State flows
    val eqState by controller.eqState.collectAsState()
    val currentProfileName by controller.currentEqPreset.collectAsState()
    val customPresets by controller.customEqPresets.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var selectedBandForManualInput by remember { mutableStateOf<Int?>(null) }
    var manualGainInput by remember { mutableStateOf("") }
    var showAutoEqSheet by remember { mutableStateOf(false) }

    val presetProfiles = listOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Harman" to listOf(6f, 4.5f, 3f, 1f, 0f, 0.5f, 2f, 4f, 2f, 0f),
        "Bass Boost" to listOf(9f, 7.5f, 5f, 2.5f, 0f, 0f, 0f, 1f, 2.5f, 3f),
        "Vocal" to listOf(-2f, -1f, 0f, 2.5f, 5f, 6f, 4f, 2f, 0f, -1f),
        "Treble" to listOf(0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 6.5f, 8f, 9.5f),
        "Electronic" to listOf(5f, 4f, 2f, 0f, -1f, 2f, 3f, 5f, 6f, 7f),
        "Acoustic" to listOf(4f, 3f, 1f, 2f, 3f, 3f, 2f, 3f, 4f, 4f)
    )

    val currentBands = eqState.bands.map { it.gainDb }
    val bandFrequencies = listOf(
        "31 Hz", "63 Hz", "125 Hz", "250 Hz", "500 Hz",
        "1 kHz", "2 kHz", "4 kHz", "8 kHz", "16 kHz"
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Top Bar: Back Button + "Audio DSP" Title
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                        text = "Audio DSP",
                        style = VeylTypography.DisplayMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }

                Surface(
                    shape = CircleShape,
                    color = colors.surfaceElevated,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when (selectedTab) {
                                DspTab.EQ -> VeylIcons.Equalizer
                                DspTab.ENHANCE -> VeylIcons.PulseSine
                                DspTab.ACOUSTICS -> VeylIcons.Headphones
                            },
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Segmented 3-Tab Pill Switcher
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                DspTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) colors.surfacePanel else Color.Transparent)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedTab = tab
                            }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.title,
                            style = VeylTypography.TitleMedium.copy(fontSize = 13.sp),
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.accentSignal else colors.textMuted
                        )
                    }
                }
            }
        }

        // 3. Tab Contents
        when (selectedTab) {
            DspTab.EQ -> {
                // Master EQ Switch Card
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (eqState.enabled) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Parametric EQ",
                                    style = VeylTypography.TitleMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (eqState.enabled) "Active • 10-Band Biquad IIR" else "Bypassed • Bit-Perfect Output",
                                    style = VeylTypography.MonoSpec,
                                    color = if (eqState.enabled) colors.accentSignal else colors.textMuted,
                                    fontSize = 11.sp
                                )
                            }

                            Switch(
                                checked = eqState.enabled,
                                onCheckedChange = { isEnabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.toggleEq(isEnabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.surfacePanel,
                                    checkedTrackColor = colors.accentSignal,
                                    uncheckedThumbColor = colors.textMuted,
                                    uncheckedTrackColor = colors.surfaceElevated
                                )
                            )
                        }
                    }
                }

                // Smooth Response Waveform Curve
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp)
                        ) {
                            EqualizerWaveform(
                                bandsProvider = { currentBands },
                                valueRange = -12f..12f,
                                enabled = eqState.enabled,
                                modifier = Modifier.fillMaxSize()
                            )

                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(colors.surfaceElevated.copy(alpha = 0.85f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.Equalizer,
                                    contentDescription = null,
                                    tint = colors.accentSignal,
                                    modifier = Modifier.size(11.dp)
                                )
                                Text(
                                    text = if (eqState.enabled) "Response Curve" else "EQ Bypassed",
                                    style = VeylTypography.MonoSpec,
                                    fontSize = 10.sp,
                                    color = if (eqState.enabled) colors.textPrimary else colors.textMuted
                                )
                            }
                        }
                    }
                }

                // Presets (AutoEQ, Save, Custom, Factory)
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Presets",
                                style = VeylTypography.SectionHeader,
                                color = colors.textPrimary,
                                fontWeight = FontWeight.SemiBold
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.accentSignal.copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, colors.accentSignal.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            showAutoEqSheet = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = VeylIcons.Headphones,
                                            contentDescription = "AutoEQ Headphone Profiles",
                                            tint = colors.accentSignal,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "AutoEQ",
                                            style = VeylTypography.BodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = colors.accentSignal
                                        )
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = colors.surfaceElevated,
                                    border = BorderStroke(1.dp, colors.borderHairline),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            presetNameInput = ""
                                            showSaveDialog = true
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = VeylIcons.Plus,
                                            contentDescription = "Save Preset",
                                            tint = colors.textPrimary,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(
                                            text = "Save",
                                            style = VeylTypography.BodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = colors.textPrimary
                                        )
                                    }
                                }
                            }
                        }

                        if (customPresets.isNotEmpty()) {
                            Text(
                                text = "CUSTOM PRESETS",
                                style = VeylTypography.MonoSpec,
                                color = colors.textMuted,
                                fontSize = 11.sp
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                items(customPresets, key = { it.id }) { preset ->
                                    val isSelected = currentProfileName == preset.name
                                    Surface(
                                        shape = RoundedCornerShape(12.dp),
                                        color = if (isSelected) colors.accentSignal else colors.surfaceElevated,
                                        modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .clickable {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    controller.setEqPreset(preset.name, preset.gains)
                                                }
                                                .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                                        ) {
                                            Text(
                                                text = preset.name,
                                                style = VeylTypography.BodySmall,
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (isSelected) colors.surfacePanel else colors.textPrimary
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            IconButton(
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    controller.deleteCustomEqPreset(preset.id)
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = VeylIcons.Trash,
                                                    contentDescription = "Delete Preset",
                                                    tint = if (isSelected) colors.surfacePanel.copy(alpha = 0.8f) else colors.textMuted,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                        }

                        Text(
                            text = "PROFILES",
                            style = VeylTypography.MonoSpec,
                            color = colors.textMuted,
                            fontSize = 11.sp
                        )

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(presetProfiles) { (profileName, gains) ->
                                val isSelected = currentProfileName == profileName
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        controller.setEqPreset(profileName, gains)
                                    },
                                    label = {
                                        Text(
                                            text = profileName,
                                            style = VeylTypography.BodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) colors.surfacePanel else colors.textPrimary
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        containerColor = colors.surfaceElevated,
                                        selectedContainerColor = colors.accentSignal,
                                        labelColor = colors.textPrimary,
                                        selectedLabelColor = colors.surfacePanel
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                )
                            }
                        }
                    }
                }

                // 10-Band Sliders Header
                item {
                    Text(
                        text = "Bands",
                        style = VeylTypography.SectionHeader,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // 10-Band Sliders
                items(bandFrequencies.size) { index ->
                    val freqLabel = bandFrequencies[index]
                    val gainValue = eqState.bands.getOrNull(index)?.gainDb ?: 0f

                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = freqLabel,
                                        style = VeylTypography.TitleMedium.copy(fontSize = 15.sp),
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (gainValue != 0f && eqState.enabled) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = colors.surfaceElevated,
                                            modifier = Modifier.clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                controller.setEqBandGain(index, 0f)
                                            }
                                        ) {
                                            Text(
                                                text = "0 dB",
                                                style = VeylTypography.MonoSpec,
                                                fontSize = 10.sp,
                                                color = colors.textMuted,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (gainValue != 0f && eqState.enabled) colors.accentSignal.copy(alpha = 0.15f) else colors.surfaceElevated,
                                    border = BorderStroke(
                                        1.dp,
                                        if (gainValue != 0f && eqState.enabled) colors.accentSignal.copy(alpha = 0.5f) else colors.borderHairline
                                    ),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = eqState.enabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedBandForManualInput = index
                                            manualGainInput = if (gainValue == 0f) "0" else String.format(Locale.US, "%.1f", gainValue)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%+.1f dB", gainValue),
                                            style = VeylTypography.MonoSpec,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = if (gainValue != 0f && eqState.enabled) colors.accentSignal else colors.textMuted
                                        )
                                        Icon(
                                            imageVector = VeylIcons.Edit,
                                            contentDescription = "Edit value",
                                            tint = if (gainValue != 0f && eqState.enabled) colors.accentSignal else colors.textMuted,
                                            modifier = Modifier.size(11.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = colors.surfaceElevated,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = eqState.enabled && gainValue > -12f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val next = (Math.round((gainValue - 0.5f) * 2) / 2.0f).coerceIn(-12f, 12f)
                                            controller.setEqBandGain(index, next)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "−",
                                            style = VeylTypography.TitleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (eqState.enabled && gainValue > -12f) colors.textPrimary else colors.textMuted
                                        )
                                    }
                                }

                                Slider(
                                    value = gainValue,
                                    onValueChange = { newGain ->
                                        val rounded = Math.round(newGain * 2) / 2.0f
                                        controller.setEqBandGain(index, rounded)
                                    },
                                    valueRange = -12f..12f,
                                    enabled = eqState.enabled,
                                    modifier = Modifier.weight(1f),
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.accentSignal,
                                        activeTrackColor = colors.accentSignal,
                                        inactiveTrackColor = colors.surfaceElevated,
                                        disabledThumbColor = colors.textMuted,
                                        disabledActiveTrackColor = colors.borderHairline,
                                        disabledInactiveTrackColor = colors.surfaceElevated
                                    )
                                )

                                Surface(
                                    shape = CircleShape,
                                    color = colors.surfaceElevated,
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .clickable(enabled = eqState.enabled && gainValue < 12f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val next = (Math.round((gainValue + 0.5f) * 2) / 2.0f).coerceIn(-12f, 12f)
                                            controller.setEqBandGain(index, next)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "+",
                                            style = VeylTypography.TitleMedium,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = if (eqState.enabled && gainValue < 12f) colors.textPrimary else colors.textMuted
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Reset Button
                item {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.setEqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.surfaceElevated),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Text(
                            text = "Reset to Flat",
                            style = VeylTypography.TitleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            DspTab.ENHANCE -> {
                // Audio DSP Engine Headroom & Co-Processing Status Banner
                item {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = colors.surfaceElevated,
                        border = BorderStroke(1.dp, colors.borderHairline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.PulseSine,
                                contentDescription = null,
                                tint = colors.accentSignal,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "DSP Engine: Dynamic Headroom Protection Active • Zero Hiss & Anti-Clipping",
                                style = VeylTypography.MonoSpec,
                                fontSize = 10.sp,
                                color = colors.textSecondary
                            )
                        }
                    }
                }

                // 1. 360° Spatializer Card (Auto Output Routing: Headphones 3D vs Speaker Panorama)
                item {
                    val spatializerEnabled by controller.spatializerEnabled.collectAsState()
                    val spatializerWidth by controller.spatializerWidth.collectAsState()
                    val isHeadphoneMode by controller.spatializerIsHeadphoneMode.collectAsState()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (spatializerEnabled) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = if (isHeadphoneMode) VeylIcons.Headphones else VeylIcons.VolumeUp,
                                        contentDescription = null,
                                        tint = if (spatializerEnabled) colors.accentSignal else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "360° Spatializer",
                                            style = VeylTypography.SectionHeader,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (spatializerEnabled) {
                                                if (isHeadphoneMode) "Width ${(spatializerWidth * 100).toInt()}% • Binaural 3D Stage"
                                                else "Width ${(spatializerWidth * 100).toInt()}% • Stereo Panorama"
                                            } else "Bypass • Direct Stereo",
                                            style = VeylTypography.MonoSpec,
                                            color = if (spatializerEnabled) colors.accentSignal else colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = spatializerEnabled,
                                    onCheckedChange = { isEnabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.setSpatializerEnabled(isEnabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surfacePanel,
                                        checkedTrackColor = colors.accentSignal,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    )
                                )
                            }

                            // Output Device Routing Indicator
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceElevated,
                                border = BorderStroke(1.dp, if (spatializerEnabled) colors.accentSignal.copy(alpha = 0.35f) else colors.borderHairline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isHeadphoneMode) VeylIcons.Headphones else VeylIcons.VolumeUp,
                                        contentDescription = null,
                                        tint = colors.accentSignal,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (isHeadphoneMode) "AUTO ROUTING: EARPHONES (3D Binaural Crossfeed)" else "AUTO ROUTING: SPEAKER (Blumlein Stereo Shuffler)",
                                        style = VeylTypography.MonoSpec,
                                        fontSize = 10.sp,
                                        color = colors.textPrimary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Text(
                                text = if (isHeadphoneMode) {
                                    "Applies phase-aligned ITD/ILD interaural crossfeed and early 3D reflections calibrated for earphones without phase cancellation."
                                } else {
                                    "Applies Blumlein stereo shuffler matrix expansion calibrated for external speaker acoustics."
                                },
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            if (spatializerEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Stage Width",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${(spatializerWidth * 100).toInt()}%",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.accentSignal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Slider(
                                        value = spatializerWidth,
                                        onValueChange = { controller.setSpatializerWidth(it) },
                                        valueRange = 0.5f..2.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.accentSignal,
                                            activeTrackColor = colors.accentSignal,
                                            inactiveTrackColor = colors.surfaceElevated
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(1.0f to "Studio (100%)", 1.35f to "Wide (135%)", 1.75f to "Concert (175%)").forEach { (presetVal, label) ->
                                            val isSelected = abs(spatializerWidth - presetVal) < 0.1f
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    controller.setSpatializerWidth(presetVal)
                                                },
                                                label = {
                                                    Text(
                                                        text = label,
                                                        style = VeylTypography.BodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) colors.surfacePanel else colors.textPrimary,
                                                        fontSize = 11.sp
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = colors.surfaceElevated,
                                                    selectedContainerColor = colors.accentSignal,
                                                    labelColor = colors.textPrimary,
                                                    selectedLabelColor = colors.surfacePanel
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. Sub-Bass Exciter Card
                item {
                    val subBassEnabled by controller.subBassEnabled.collectAsState()
                    val subBassIntensity by controller.subBassIntensity.collectAsState()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (subBassEnabled) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Waveform,
                                        contentDescription = null,
                                        tint = if (subBassEnabled) colors.accentSignal else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Sub-Bass Exciter",
                                            style = VeylTypography.SectionHeader,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (subBassEnabled) "Depth ${(subBassIntensity * 100).toInt()}% • 2nd-Order Sub Harmonics" else "Bypass • Untouched Bass",
                                            style = VeylTypography.MonoSpec,
                                            color = if (subBassEnabled) colors.accentSignal else colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = subBassEnabled,
                                    onCheckedChange = { isEnabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.setSubBassEnabled(isEnabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surfacePanel,
                                        checkedTrackColor = colors.accentSignal,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    )
                                )
                            }

                            Text(
                                text = "Synthesizes missing-fundamental sub harmonics (20–70 Hz) for deep physical impact with 2nd-order bandpass isolation.",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            if (subBassEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Bass Intensity",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${(subBassIntensity * 100).toInt()}%",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.accentSignal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Slider(
                                        value = subBassIntensity,
                                        onValueChange = { controller.setSubBassIntensity(it) },
                                        valueRange = 0.05f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.accentSignal,
                                            activeTrackColor = colors.accentSignal,
                                            inactiveTrackColor = colors.surfaceElevated
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0.25f to "Tight (25%)", 0.50f to "Deep (50%)", 0.80f to "Slam (80%)").forEach { (presetVal, label) ->
                                            val isSelected = abs(subBassIntensity - presetVal) < 0.08f
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    controller.setSubBassIntensity(presetVal)
                                                },
                                                label = {
                                                    Text(
                                                        text = label,
                                                        style = VeylTypography.BodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) colors.surfacePanel else colors.textPrimary,
                                                        fontSize = 11.sp
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = colors.surfaceElevated,
                                                    selectedContainerColor = colors.accentSignal,
                                                    labelColor = colors.textPrimary,
                                                    selectedLabelColor = colors.surfacePanel
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Vocal Clarity Card
                item {
                    val vocalClarityEnabled by controller.vocalClarityEnabled.collectAsState()
                    val vocalClarityIntensity by controller.vocalClarityIntensity.collectAsState()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (vocalClarityEnabled) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.VolumeUp,
                                        contentDescription = null,
                                        tint = if (vocalClarityEnabled) colors.accentSignal else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Vocal Clarity",
                                            style = VeylTypography.SectionHeader,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (vocalClarityEnabled) "Presence ${(vocalClarityIntensity * 100).toInt()}% • Dynamic Headroom Anti-Hiss" else "Bypass • Neutral Midrange",
                                            style = VeylTypography.MonoSpec,
                                            color = if (vocalClarityEnabled) colors.accentSignal else colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = vocalClarityEnabled,
                                    onCheckedChange = { isEnabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.setVocalClarityEnabled(isEnabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surfacePanel,
                                        checkedTrackColor = colors.accentSignal,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    )
                                )
                            }

                            Text(
                                text = "Brings lead vocals forward with calibrated 2.8 kHz presence boost and anti-hiss headroom compensation.",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            if (vocalClarityEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Vocal Boost",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${(vocalClarityIntensity * 100).toInt()}%",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.accentSignal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Slider(
                                        value = vocalClarityIntensity,
                                        onValueChange = { controller.setVocalClarityIntensity(it) },
                                        valueRange = 0.05f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.accentSignal,
                                            activeTrackColor = colors.accentSignal,
                                            inactiveTrackColor = colors.surfaceElevated
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0.25f to "Natural (25%)", 0.50f to "Crisp (50%)", 0.75f to "Forward (75%)").forEach { (presetVal, label) ->
                                            val isSelected = abs(vocalClarityIntensity - presetVal) < 0.08f
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    controller.setVocalClarityIntensity(presetVal)
                                                },
                                                label = {
                                                    Text(
                                                        text = label,
                                                        style = VeylTypography.BodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) colors.surfacePanel else colors.textPrimary,
                                                        fontSize = 11.sp
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = colors.surfaceElevated,
                                                    selectedContainerColor = colors.accentSignal,
                                                    labelColor = colors.textPrimary,
                                                    selectedLabelColor = colors.surfacePanel
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Triode Tube Warmth Card
                item {
                    val tubeEnabled by controller.tubeWarmthEnabled.collectAsState()
                    val tubeDrive by controller.tubeWarmthDrive.collectAsState()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (tubeEnabled) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.PulseSine,
                                        contentDescription = null,
                                        tint = if (tubeEnabled) colors.accentSignal else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Tube Warmth",
                                            style = VeylTypography.SectionHeader,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (tubeEnabled) "Drive ${(tubeDrive * 100).toInt()}% • DC-Blocked Triode Harmonics" else "Bypass • Clean Digital",
                                            style = VeylTypography.MonoSpec,
                                            color = if (tubeEnabled) colors.accentSignal else colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = tubeEnabled,
                                    onCheckedChange = { isEnabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.setTubeWarmthEnabled(isEnabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surfacePanel,
                                        checkedTrackColor = colors.accentSignal,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    )
                                )
                            }

                            Text(
                                text = "Adds musical even-order harmonics modeled on Class-A triode vacuum tubes with 5 Hz DC-blocking protection.",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            if (tubeEnabled) {
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Drive Intensity",
                                            style = VeylTypography.BodySmall,
                                            color = colors.textSecondary,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = "${(tubeDrive * 100).toInt()}%",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.accentSignal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Slider(
                                        value = tubeDrive,
                                        onValueChange = { controller.setTubeWarmthDrive(it) },
                                        valueRange = 0.05f..1.0f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = colors.accentSignal,
                                            activeTrackColor = colors.accentSignal,
                                            inactiveTrackColor = colors.surfaceElevated
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        listOf(0.15f to "Subtle (15%)", 0.35f to "Warm (35%)", 0.65f to "Vintage (65%)").forEach { (presetVal, label) ->
                                            val isSelected = abs(tubeDrive - presetVal) < 0.08f
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                    controller.setTubeWarmthDrive(presetVal)
                                                },
                                                label = {
                                                    Text(
                                                        text = label,
                                                        style = VeylTypography.BodySmall,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isSelected) colors.surfacePanel else colors.textPrimary,
                                                        fontSize = 11.sp
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    containerColor = colors.surfaceElevated,
                                                    selectedContainerColor = colors.accentSignal,
                                                    labelColor = colors.textPrimary,
                                                    selectedLabelColor = colors.surfacePanel
                                                ),
                                                shape = RoundedCornerShape(12.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            DspTab.ACOUSTICS -> {
                // 1. Headphone Crossfeed Card
                item {
                    val crossfeedPreset by controller.crossfeedPreset.collectAsState()
                    val isCrossfeedActive = crossfeedPreset != CrossfeedPresetEnum.OFF

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCrossfeedActive) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Headphones,
                                        contentDescription = null,
                                        tint = if (isCrossfeedActive) colors.accentSignal else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Crossfeed",
                                            style = VeylTypography.SectionHeader,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (isCrossfeedActive) "${crossfeedPreset.name} • Binaural Matrix" else "Bypass • Isolated Channels",
                                            style = VeylTypography.MonoSpec,
                                            color = if (isCrossfeedActive) colors.accentSignal else colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = isCrossfeedActive,
                                    onCheckedChange = { enable ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.setCrossfeedPreset(
                                            if (enable) CrossfeedPresetEnum.STANDARD else CrossfeedPresetEnum.OFF
                                        )
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surfacePanel,
                                        checkedTrackColor = colors.accentSignal,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    )
                                )
                            }

                            Text(
                                text = "Simulates speaker listening on headphones by blending subtle delayed crosstalk to reduce listening fatigue.",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            val crossfeedOptions = listOf(
                                CrossfeedPresetEnum.SUBTLE to "Subtle (Meier)",
                                CrossfeedPresetEnum.STANDARD to "Standard (Bauer)",
                                CrossfeedPresetEnum.VINTAGE to "Vintage (60s Stereo)"
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                items(crossfeedOptions) { (preset, label) ->
                                    val isSelected = isCrossfeedActive && crossfeedPreset == preset
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            controller.setCrossfeedPreset(preset)
                                        },
                                        label = {
                                            Text(
                                                text = label,
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
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Loudness Normalizer Card
                item {
                    val loudnessEnabled by controller.loudnessNormalizerEnabled.collectAsState()
                    val targetLufs by controller.loudnessTargetLufs.collectAsState()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (loudnessEnabled) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Waveform,
                                        contentDescription = null,
                                        tint = if (loudnessEnabled) colors.accentSignal else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Loudness Normalizer",
                                            style = VeylTypography.SectionHeader,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = if (loudnessEnabled) "${targetLufs.toInt()} LUFS • EBU R128 Leveling" else "Bypass • Dynamic Volume",
                                            style = VeylTypography.MonoSpec,
                                            color = if (loudnessEnabled) colors.accentSignal else colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                Switch(
                                    checked = loudnessEnabled,
                                    onCheckedChange = { isEnabled ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.setLoudnessNormalizerEnabled(isEnabled)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = colors.surfacePanel,
                                        checkedTrackColor = colors.accentSignal,
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    )
                                )
                            }

                            Text(
                                text = "Smoothly levels perceived track volume across albums and sources without dynamic compression pumping.",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(-14.0f to "-14 LUFS (Streaming Standard)", -18.0f to "-18 LUFS (Hi-Res Audiophile)").forEach { (lufs, label) ->
                                    val isSelected = abs(targetLufs - lufs) < 0.1f
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            controller.setLoudnessTargetLufs(lufs)
                                        },
                                        label = {
                                            Text(
                                                text = label,
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
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. Peak Limiter Card
                item {
                    val limiterEnabled by controller.limiterEnabled.collectAsState()

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (limiterEnabled) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Peak Limiter",
                                    style = VeylTypography.SectionHeader,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = if (limiterEnabled) "0.0 dBFS Soft-Knee • Active" else "Bypass • Unprotected",
                                    style = VeylTypography.MonoSpec,
                                    color = if (limiterEnabled) colors.accentSignal else colors.textMuted,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Soft-knee brickwall limiter prevents digital intersample clipping and harmonic distortion on high-gain peaks.",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp,
                                    lineHeight = 15.sp
                                )
                            }

                            Switch(
                                checked = limiterEnabled,
                                onCheckedChange = { isEnabled ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.setLimiterEnabled(isEnabled)
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = colors.surfacePanel,
                                    checkedTrackColor = colors.accentSignal,
                                    uncheckedThumbColor = colors.textMuted,
                                    uncheckedTrackColor = colors.surfaceElevated
                                )
                            )
                        }
                    }
                }

                // 4. Channel Balance Card
                item {
                    val balance by controller.channelBalance.collectAsState()
                    val isCentered = abs(balance) < 0.01f

                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (!isCentered) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Sliders,
                                        contentDescription = null,
                                        tint = if (!isCentered) colors.accentSignal else colors.textMuted,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Text(
                                            text = "Channel Balance",
                                            style = VeylTypography.SectionHeader,
                                            color = colors.textPrimary,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = when {
                                                isCentered -> "Center • 0.0 dB"
                                                balance < 0f -> "Left ${(balance * -100).toInt()}% (+${"%.1f".format(abs(balance) * 6f)} dB)"
                                                else -> "Right ${(balance * 100).toInt()}% (+${"%.1f".format(balance * 6f)} dB)"
                                            },
                                            style = VeylTypography.MonoSpec,
                                            color = if (!isCentered) colors.accentSignal else colors.textMuted,
                                            fontSize = 11.sp
                                        )
                                    }
                                }

                                if (!isCentered) {
                                    TextButton(
                                        onClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            controller.setChannelBalance(0.0f)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "Reset",
                                            style = VeylTypography.MonoSpec,
                                            color = colors.accentSignal,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Text(
                                text = "Fine-tunes stereo centering to compensate for ear hearing asymmetry or headphone driver variance.",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 11.sp,
                                lineHeight = 15.sp
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "L",
                                        style = VeylTypography.MonoSpec,
                                        color = if (balance < -0.05f) colors.accentSignal else colors.textMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "CENTER",
                                        style = VeylTypography.MonoSpec,
                                        color = if (isCentered) colors.accentSignal else colors.textMuted,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "R",
                                        style = VeylTypography.MonoSpec,
                                        color = if (balance > 0.05f) colors.accentSignal else colors.textMuted,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Slider(
                                    value = balance,
                                    onValueChange = { newVal ->
                                        val snapped = if (abs(newVal) < 0.04f) 0.0f else newVal
                                        controller.setChannelBalance(snapped)
                                    },
                                    valueRange = -1.0f..1.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = colors.accentSignal,
                                        activeTrackColor = colors.accentSignal,
                                        inactiveTrackColor = colors.surfaceElevated
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }

    // Save Preset Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = colors.surfacePanel,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary,
            title = {
                Text(
                    text = "Save Custom Preset",
                    style = VeylTypography.TitleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Save current 10-band equalizer settings as a personalized sound profile.",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = {
                            Text(
                                "Preset Name (e.g. Warm Bass)",
                                color = colors.textMuted
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentSignal,
                            unfocusedBorderColor = colors.borderHairline,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentSignal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = presetNameInput.trim()
                        if (trimmed.isNotEmpty()) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val saved = controller.saveCustomEqPreset(trimmed, currentBands)
                            controller.setEqPreset(saved.name, saved.gains)
                            showSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Save",
                        color = colors.surfacePanel,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text(text = "Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // Direct Numeric Band Gain Dialog
    if (selectedBandForManualInput != null) {
        val bandIdx = selectedBandForManualInput!!
        val freqLabel = bandFrequencies.getOrNull(bandIdx) ?: "Band ${bandIdx + 1}"
        AlertDialog(
            onDismissRequest = { selectedBandForManualInput = null },
            containerColor = colors.surfacePanel,
            titleContentColor = colors.textPrimary,
            textContentColor = colors.textPrimary,
            title = {
                Text(
                    text = "Set Gain for $freqLabel",
                    style = VeylTypography.TitleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Enter a precise gain value between -12.0 dB and +12.0 dB:",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )

                    OutlinedTextField(
                        value = manualGainInput,
                        onValueChange = { manualGainInput = it },
                        placeholder = { Text("0.0", color = colors.textMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = colors.accentSignal,
                            unfocusedBorderColor = colors.borderHairline,
                            focusedTextColor = colors.textPrimary,
                            unfocusedTextColor = colors.textPrimary,
                            cursorColor = colors.accentSignal
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "QUICK VALUES",
                        style = VeylTypography.MonoSpec,
                        fontSize = 10.sp,
                        color = colors.textMuted
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(-6f, -3f, 0f, 3f, 6f).forEach { qVal ->
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = colors.surfaceElevated,
                                modifier = Modifier.clickable {
                                    manualGainInput = String.format(Locale.US, "%.1f", qVal)
                                }
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%+.0f dB", qVal),
                                    style = VeylTypography.MonoSpec,
                                    fontSize = 11.sp,
                                    color = if (qVal == 0f) colors.textPrimary else colors.accentSignal,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = manualGainInput.trim().toFloatOrNull()
                        if (parsed != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            val clamped = parsed.coerceIn(-12f, 12f)
                            controller.setEqBandGain(bandIdx, clamped)
                            selectedBandForManualInput = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "Apply",
                        color = colors.surfacePanel,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedBandForManualInput = null }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }

    // AutoEQ Bottom Sheet
    if (showAutoEqSheet) {
        VeylAutoEqBottomSheet(
            controller = controller,
            onDismiss = { showAutoEqSheet = false }
        )
    }
}
