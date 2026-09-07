package com.audiophile.player.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    var currentProfileName by remember { mutableStateOf("Bass Boost") }

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
                Text(
                    text = "Presets",
                    style = VeylTypography.SectionHeader,
                    color = colors.textPrimary,
                    fontWeight = FontWeight.SemiBold
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
                                currentProfileName = profileName
                                gains.forEachIndexed { index, gain ->
                                    controller.setEqBand(index, gain)
                                }
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

        // 4. Smooth Parametric EQ Response Waveform Curve
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
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
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // 5. 10-Band Sliders
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
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = colors.surfacePanel),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Frequency Label
                    Text(
                        text = freqLabel,
                        style = VeylTypography.MonoSpec,
                        color = colors.textPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(64.dp)
                    )

                    // Continuous Slider (-12 dB to +12 dB)
                    Slider(
                        value = gainValue,
                        onValueChange = { newGain ->
                            currentProfileName = "Custom"
                            controller.setEqBand(index, newGain)
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

                    Spacer(modifier = Modifier.width(10.dp))

                    // Gain readout
                    Text(
                        text = String.format("%+.1f dB", gainValue),
                        style = VeylTypography.MonoSpec,
                        color = if (gainValue != 0f && eqState.enabled) colors.accentSignal else colors.textMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(60.dp)
                    )
                }
            }
        }

        // Reset Button
        item {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    currentProfileName = "Flat"
                    for (i in 0 until 10) {
                        controller.setEqBand(i, 0f)
                    }
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
}
