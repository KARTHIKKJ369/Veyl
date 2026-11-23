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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylEqualizerScreen(
    controller: AudioEngineController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

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
        // 1. Top Bar: Back Button + "Equalizer" Title
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
