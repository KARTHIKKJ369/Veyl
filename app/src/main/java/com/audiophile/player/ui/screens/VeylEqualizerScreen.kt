package com.audiophile.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.EqualizerWaveform
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.EqBandInfo

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylEqualizerScreen(
    controller: AudioEngineController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val eqState by controller.eqState.collectAsState()

    val currentProfileName by controller.currentEqPreset.collectAsState()
    val customPresets by controller.customEqPresets.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }

    var selectedBandForManualInput by remember { mutableStateOf<Int?>(null) }
    var manualGainInput by remember { mutableStateOf("") }

    val presetProfiles = listOf(
        "Flat" to listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
        "Bass Boost" to listOf(9f, 7.5f, 5f, 2.5f, 0f, 0f, 0f, 1f, 2.5f, 3f),
        "Vocal Presence" to listOf(-2f, -1f, 0f, 2.5f, 5f, 6f, 4f, 2f, 0f, -1f),
        "Harman Curve" to listOf(6f, 4.5f, 3f, 1f, 0f, 0.5f, 2f, 4f, 2f, 0f),
        "Treble Boost" to listOf(0f, 0f, 0f, 0f, 1f, 2.5f, 4f, 6.5f, 8f, 9.5f),
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
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Top Bar: Back Button + "Equalizer" Title + Waveform Icon
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
                        text = "Equalizer",
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
                            imageVector = VeylIcons.Equalizer,
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 2. Master Switch Card
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (eqState.enabled) colors.surfacePanel else colors.surfaceElevated.copy(alpha = 0.5f)
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = if (eqState.enabled) "Parametric EQ Active" else "EQ Bypassed (Bit-Perfect)",
                            style = VeylTypography.TitleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (eqState.enabled) "DSP processing with 10-band IIR biquad filters" else "Direct bit-perfect hardware output without processing",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted
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

        // 3. Preset Profiles Filter Chips
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

                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = colors.accentSignal.copy(alpha = 0.15f),
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
                                tint = colors.accentSignal,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Save Current",
                                style = VeylTypography.BodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.accentSignal
                            )
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

                    Text(
                        text = "FACTORY PRESETS",
                        style = VeylTypography.MonoSpec,
                        color = colors.textMuted,
                        fontSize = 11.sp
                    )
                }

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

        // 4. Smooth Parametric EQ Response Waveform Curve (Interactive)
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    EqualizerWaveform(
                        bands = currentBands,
                        valueRange = -12f..12f,
                        enabled = eqState.enabled,
                        onBandGainChange = { bandIndex, newGain ->
                            controller.setEqBandGain(bandIndex, newGain)
                        },
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
                            imageVector = VeylIcons.Edit,
                            contentDescription = null,
                            tint = colors.accentSignal,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = if (eqState.enabled) "Drag points on curve to shape" else "EQ Bypassed",
                            style = VeylTypography.MonoSpec,
                            fontSize = 10.sp,
                            color = if (eqState.enabled) colors.textPrimary else colors.textMuted
                        )
                    }
                }
            }
        }

        // 5. 10-Band Sliders with Precision Steppers & Direct Numeric Entry
        item {
            Text(
                text = "10-Band Frequency Adjustments",
                style = VeylTypography.SectionHeader,
                color = colors.textPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }

        items(bandFrequencies.size) { index ->
            val freqLabel = bandFrequencies[index]
            val gainValue = eqState.bands.getOrNull(index)?.gainDb ?: 0f

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                style = VeylTypography.TitleMedium,
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
                                        text = "Reset 0dB",
                                        style = VeylTypography.MonoSpec,
                                        fontSize = 10.sp,
                                        color = colors.textMuted,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }

                        // Interactive Gain Readout Pill -> Tap to manually enter numeric value
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
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
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

                    // Stepper minus [-0.5] + Slider + Stepper plus [+0.5]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = colors.surfaceElevated,
                            modifier = Modifier
                                .size(34.dp)
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
                                .size(34.dp)
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
                    text = "Reset All Bands to 0 dB",
                    style = VeylTypography.TitleMedium,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        item { Spacer(modifier = Modifier.height(100.dp)) }
    }

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
                TextButton(
                    onClick = { showSaveDialog = false }
                ) {
                    Text(
                        text = "Cancel",
                        color = colors.textMuted
                    )
                }
            }
        )
    }

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
}
