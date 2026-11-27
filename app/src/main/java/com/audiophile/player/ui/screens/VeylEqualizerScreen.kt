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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.EqualizerWaveform
import com.audiophile.player.ui.components.VeylActionButton
import com.audiophile.player.ui.components.VeylBipolarSlider
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylSpacing
import com.audiophile.player.ui.theme.VeylTypography
import java.util.Locale
import kotlin.math.abs

@Composable
fun VeylEqualizerScreen(
    controller: AudioEngineController,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    androidx.activity.compose.BackHandler {
        onNavigateBack()
    }

    val eqState by controller.eqState.collectAsState()
    val currentProfileName by controller.currentEqPreset.collectAsState()
    val customPresets by controller.customEqPresets.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var presetNameInput by remember { mutableStateOf("") }
    var selectedBandForManualInput by remember { mutableStateOf<Int?>(null) }
    var manualGainInput by remember { mutableStateOf("") }

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
            .background(colors.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(horizontal = VeylSpacing.md, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Clean Borderless Header matching VeylQueueScreen
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    VeylActionButton(
                        icon = VeylIcons.ChevronLeft,
                        contentDescription = "Back",
                        onClick = onNavigateBack,
                        iconSizeDp = 22
                    )
                    Column {
                        Text(
                            text = "EQUALIZER",
                            style = VeylTypography.MonoBadge,
                            color = colors.accentSignal
                        )
                        Text(
                            text = "Sound Shaping",
                            style = VeylTypography.TitleLarge,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 22.sp
                        )
                    }
                }

                TextButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.setEqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Reset",
                        style = VeylTypography.MonoSpec,
                        color = colors.textSecondary,
                        fontSize = 12.sp
                    )
                }
            }
        }

        // 2. Master EQ Toggle Row (Flat, no card box)
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.toggleEq(!eqState.enabled)
                        }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "10-Band Parametric EQ",
                            style = VeylTypography.TitleMedium,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (eqState.enabled) "Active • Biquad IIR" else "Bypassed • Bit-Perfect Path",
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
                            checkedThumbColor = colors.background,
                            checkedTrackColor = colors.accentSignal,
                            uncheckedThumbColor = colors.textMuted,
                            uncheckedTrackColor = colors.surfaceElevated
                        )
                    )
                }
                HorizontalDivider(color = colors.borderHairline.copy(alpha = 0.6f))
            }
        }

        // 3. Frequency Response Waveform (Clean hairline frame, no pill overlay)
        item {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surfacePanel,
                border = BorderStroke(1.dp, colors.borderHairline.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
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

                    Text(
                        text = if (eqState.enabled) "RESPONSE CURVE" else "BYPASSED",
                        style = VeylTypography.MonoSpec,
                        fontSize = 10.sp,
                        color = if (eqState.enabled) colors.accentSignal else colors.textMuted,
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
        }

        // 4. Presets (Flat underlined tabs, zero pills)
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "PRESETS",
                        style = VeylTypography.MonoBadge,
                        color = colors.textMuted
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                presetNameInput = ""
                                showSaveDialog = true
                            }
                            .padding(vertical = 4.dp, horizontal = 4.dp)
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
                            fontWeight = FontWeight.SemiBold,
                            color = colors.accentSignal
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(presetProfiles) { (profileName, gains) ->
                        val isSelected = currentProfileName == profileName
                        Column(
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.setEqPreset(profileName, gains)
                                }
                                .padding(vertical = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = profileName,
                                style = VeylTypography.BodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) colors.accentSignal else colors.textSecondary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .width(20.dp)
                                    .height(2.dp)
                                    .background(if (isSelected) colors.accentSignal else colors.background)
                            )
                        }
                    }
                }

                if (customPresets.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 2.dp)
                    ) {
                        items(customPresets, key = { it.id }) { preset ->
                            val isSelected = currentProfileName == preset.name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        controller.setEqPreset(preset.name, preset.gains)
                                    }
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = preset.name,
                                    style = VeylTypography.BodySmall,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) colors.accentSignal else colors.textPrimary
                                )
                                IconButton(
                                    onClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.deleteCustomEqPreset(preset.id)
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Trash,
                                        contentDescription = "Delete Preset",
                                        tint = colors.textMuted,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.borderHairline.copy(alpha = 0.5f))
            }
        }

        // 5. 10-Band Sliders (Unified clean panel with hairline dividers, zero pills)
        item {
            Text(
                text = "BANDS",
                style = VeylTypography.MonoBadge,
                color = colors.textMuted
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = colors.surfacePanel,
                border = BorderStroke(1.dp, colors.borderHairline.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    bandFrequencies.forEachIndexed { index, freqLabel ->
                        val gainValue = eqState.bands.getOrNull(index)?.gainDb ?: 0f

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = freqLabel,
                                    style = VeylTypography.TitleMedium.copy(fontSize = 14.sp),
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    if (gainValue != 0f && eqState.enabled) {
                                        Text(
                                            text = "0 dB",
                                            style = VeylTypography.MonoSpec,
                                            fontSize = 11.sp,
                                            color = colors.textMuted,
                                            modifier = Modifier.clickable {
                                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                controller.setEqBandGain(index, 0f)
                                            }
                                        )
                                    }

                                    Text(
                                        text = String.format(Locale.US, "%+.1f dB", gainValue),
                                        style = VeylTypography.MonoSpec,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = if (gainValue != 0f && eqState.enabled) colors.accentSignal else colors.textMuted,
                                        modifier = Modifier.clickable(enabled = eqState.enabled) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            selectedBandForManualInput = index
                                            manualGainInput = if (gainValue == 0f) "0" else String.format(Locale.US, "%.1f", gainValue)
                                        }
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable(enabled = eqState.enabled && gainValue > -12f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val next = (Math.round((gainValue - 0.1f) * 10) / 10.0f).coerceIn(-12f, 12f)
                                            controller.setEqBandGain(index, next)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "−",
                                        style = VeylTypography.TitleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = if (eqState.enabled && gainValue > -12f) colors.textSecondary else colors.textMuted.copy(alpha = 0.4f)
                                    )
                                }

                                VeylBipolarSlider(
                                    value = gainValue,
                                    valueRange = -12f..12f,
                                    onValueChange = { newGain ->
                                        val rounded = (Math.round(newGain * 10) / 10.0f).coerceIn(-12f, 12f)
                                        if (rounded == 0f && gainValue != 0f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                        controller.setEqBandGain(index, rounded)
                                    },
                                    enabled = eqState.enabled,
                                    modifier = Modifier.weight(1f)
                                )

                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clickable(enabled = eqState.enabled && gainValue < 12f) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            val next = (Math.round((gainValue + 0.1f) * 10) / 10.0f).coerceIn(-12f, 12f)
                                            controller.setEqBandGain(index, next)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "+",
                                        style = VeylTypography.TitleMedium,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 17.sp,
                                        color = if (eqState.enabled && gainValue < 12f) colors.textSecondary else colors.textMuted.copy(alpha = 0.4f)
                                    )
                                }
                            }
                        }

                        if (index < bandFrequencies.lastIndex) {
                            HorizontalDivider(
                                color = colors.borderHairline.copy(alpha = 0.35f),
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )
                        }
                    }
                }
            }
        }

        // 6. Channel Balance (Flat clean section)
        item {
            val balance by controller.channelBalance.collectAsState()
            val isCentered = abs(balance) < 0.01f

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Channel Balance",
                            style = VeylTypography.TitleMedium,
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

                    if (!isCentered) {
                        Text(
                            text = "Center",
                            style = VeylTypography.MonoSpec,
                            color = colors.accentSignal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    controller.setChannelBalance(0.0f)
                                }
                                .padding(4.dp)
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "L",
                        style = VeylTypography.MonoSpec,
                        color = if (balance < -0.05f) colors.accentSignal else colors.textMuted,
                        fontSize = 11.sp,
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
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                VeylBipolarSlider(
                    value = balance,
                    valueRange = -1.0f..1.0f,
                    onValueChange = { newVal ->
                        val snapped = if (abs(newVal) < 0.04f) 0.0f else newVal
                        if (snapped == 0.0f && !isCentered) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        controller.setChannelBalance(snapped)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
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
                        text = "Save current 10-band equalizer settings as a personalized profile.",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted
                    )
                    OutlinedTextField(
                        value = presetNameInput,
                        onValueChange = { presetNameInput = it },
                        placeholder = { Text("Preset Name (e.g. Warm Bass)", color = colors.textMuted) },
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
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            val saved = controller.saveCustomEqPreset(trimmed, currentBands)
                            controller.setEqPreset(saved.name, saved.gains)
                            showSaveDialog = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Save", color = colors.background, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    showSaveDialog = false
                }) {
                    Text("Cancel", color = colors.textMuted)
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
                        text = "Enter a gain between -12.0 dB and +12.0 dB:",
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        listOf(-6f, -3f, 0f, 3f, 6f).forEach { qVal ->
                            Text(
                                text = String.format(Locale.US, "%+.0f dB", qVal),
                                style = VeylTypography.MonoSpec,
                                fontSize = 12.sp,
                                color = if (qVal == 0f) colors.textPrimary else colors.accentSignal,
                                modifier = Modifier
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        manualGainInput = String.format(Locale.US, "%.1f", qVal)
                                    }
                                    .padding(horizontal = 6.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val parsed = manualGainInput.trim().toFloatOrNull()
                        if (parsed != null) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            controller.setEqBandGain(bandIdx, parsed.coerceIn(-12f, 12f))
                            selectedBandForManualInput = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accentSignal),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Apply", color = colors.background, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    selectedBandForManualInput = null
                }) {
                    Text("Cancel", color = colors.textMuted)
                }
            }
        )
    }
}
