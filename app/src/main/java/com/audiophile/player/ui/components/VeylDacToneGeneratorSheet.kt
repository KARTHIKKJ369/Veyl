package com.audiophile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.engine.DacToneType
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylSpacing
import com.audiophile.player.ui.theme.VeylTypography
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylDacToneGeneratorSheet(
    controller: AudioEngineController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val isPlaying by controller.toneGenerator.isPlaying.collectAsState()
    val activeTone by controller.toneGenerator.activeTone.collectAsState()
    val liveFreqHz by controller.toneGenerator.liveFrequencyHz.collectAsState()

    // Safety: Automatically stop tone when user dismisses the sheet
    DisposableEffect(Unit) {
        onDispose {
            controller.stopDiagnosticTone()
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
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row
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
                            .background(
                                if (isPlaying) colors.accentSignal else colors.surfaceElevated
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = VeylIcons.PulseSine,
                            contentDescription = null,
                            tint = if (isPlaying) colors.background else colors.accentSignal,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Column {
                        Text(
                            text = "DAC Diagnostic Generator",
                            style = VeylTypography.HeadlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 17.sp
                        )
                        Text(
                            text = "Calibrated bit-accurate audio test signals",
                            style = VeylTypography.BodySmall,
                            color = colors.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        controller.stopDiagnosticTone()
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

            // Safety Warning Callout
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF2A1C12),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8C531B)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF9500))
                    )
                    Text(
                        text = "Safety Warning: Set DAC and headphone volume to a safe, low level before triggering 0 dBFS signals.",
                        style = VeylTypography.BodySmall,
                        color = Color(0xFFFFD199),
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            // Live Test Signal Monitor HUD
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isPlaying) colors.accentSignal.copy(alpha = 0.12f) else colors.surfaceElevated,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (isPlaying) colors.accentSignal.copy(alpha = 0.4f) else colors.borderHairline
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) colors.accentSignal else colors.textMuted)
                            )
                            Text(
                                text = if (isPlaying) "SIGNAL ACTIVE (48 kHz 32-bit Float)" else "STANDBY • NO ACTIVE SIGNAL",
                                style = VeylTypography.MonoBadge,
                                color = if (isPlaying) colors.accentSignal else colors.textMuted,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Text(
                            text = if (isPlaying) {
                                when (activeTone) {
                                    DacToneType.PINK_NOISE -> "20 Hz – 20 kHz (Pink Noise)"
                                    DacToneType.LOG_SWEEP_20HZ_20KHZ -> String.format(Locale.US, "%.1f Hz (Log Sweep)", liveFreqHz)
                                    else -> "1,000.0 Hz (Continuous Sine)"
                                }
                            } else "Select a reference signal below",
                            style = VeylTypography.MonoSpec,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 14.sp
                        )
                    }

                    if (isPlaying) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.accentSignal,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.stopDiagnosticTone()
                                }
                        ) {
                            Text(
                                text = "STOP",
                                style = VeylTypography.BodySmall,
                                fontWeight = FontWeight.Bold,
                                color = colors.background,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                }
            }

            // Signal List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                items(DacToneType.values()) { type ->
                    val isThisActive = isPlaying && activeTone == type

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isThisActive) colors.surfaceElevated else colors.surfaceElevated.copy(alpha = 0.6f),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (isThisActive) colors.accentSignal else colors.borderHairline
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (isThisActive) {
                                    controller.stopDiagnosticTone()
                                } else {
                                    controller.startDiagnosticTone(type)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (isThisActive) colors.accentSignal else colors.surfacePanel
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isThisActive) VeylIcons.Pause else VeylIcons.Play,
                                    contentDescription = if (isThisActive) "Stop" else "Play",
                                    tint = if (isThisActive) colors.background else colors.accentSignal,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = type.title,
                                        style = VeylTypography.TitleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isThisActive) colors.accentSignal else colors.textPrimary,
                                        fontSize = 14.sp
                                    )
                                }

                                Text(
                                    text = type.subtitle,
                                    style = VeylTypography.MonoBadge,
                                    color = if (isThisActive) colors.accentSignal.copy(alpha = 0.8f) else colors.textMuted,
                                    fontSize = 10.sp
                                )

                                Spacer(modifier = Modifier.height(2.dp))

                                Text(
                                    text = type.description,
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp,
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
