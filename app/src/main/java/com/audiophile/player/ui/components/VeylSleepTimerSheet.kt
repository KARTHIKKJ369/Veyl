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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun VeylSleepTimerSheet(
    controller: AudioEngineController,
    onDismiss: () -> Unit
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val activeOption by controller.sleepTimerActiveOption.collectAsState()
    val remainingSec by controller.sleepTimerRemainingSeconds.collectAsState()
    val isRunning = activeOption != null && remainingSec != null && (remainingSec ?: 0) > 0

    val remainingFormatted = remember(remainingSec) {
        val total = remainingSec ?: 0
        val mins = total / 60
        val secs = total % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfacePanel,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = colors.accentSignal.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = VeylIcons.Moon,
                                contentDescription = null,
                                tint = colors.accentSignal,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Column {
                        Text(
                            text = "Sleep Timer",
                            style = VeylTypography.SectionHeader,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Gentle fade-out into silence",
                            style = VeylTypography.BodySmall,
                            color = colors.textMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = VeylIcons.Close,
                        contentDescription = "Close",
                        tint = colors.textMuted,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Active Countdown Card (if running)
            AnimatedVisibility(
                visible = isRunning,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = colors.surfaceElevated,
                    border = androidx.compose.foundation.BorderStroke(1.dp, colors.accentSignal.copy(alpha = 0.4f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ACTIVE TIMER",
                                style = VeylTypography.MonoSpec,
                                color = colors.accentSignal,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = remainingFormatted,
                                    style = VeylTypography.HeadlineMedium,
                                    color = colors.textPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp
                                )
                                Text(
                                    text = if (activeOption == "track") "until track ends" else "remaining",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textMuted,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(bottom = 4.dp)
                                )
                            }
                            if ((remainingSec ?: 0) in 1..30) {
                                Text(
                                    text = "Logarithmic volume fade-out in progress\u2026",
                                    style = VeylTypography.MonoSpec,
                                    color = colors.accentSignal,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                controller.cancelSleepTimer()
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFFF5252).copy(alpha = 0.15f),
                                contentColor = Color(0xFFFF5252)
                            )
                        ) {
                            Text(
                                text = "Cancel",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            Text(
                text = "SET DURATION",
                style = VeylTypography.MonoSpec,
                color = colors.textMuted,
                fontSize = 11.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Presets FlowRow
            val timerOptions = listOf(
                15 to "15 min",
                30 to "30 min",
                45 to "45 min",
                60 to "60 min",
                90 to "90 min"
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                timerOptions.forEach { (mins, label) ->
                    val isSelected = activeOption == "${mins}m"
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (isSelected) colors.accentSignal else colors.surfaceElevated,
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (isSelected) {
                                    controller.cancelSleepTimer()
                                } else {
                                    controller.startSleepTimer(mins)
                                }
                            }
                    ) {
                        Text(
                            text = label,
                            style = VeylTypography.BodyMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) colors.surfacePanel else colors.textPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                        )
                    }
                }

                // End of track option
                val isTrackSelected = activeOption == "track"
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = if (isTrackSelected) colors.accentSignal else colors.surfaceElevated,
                    border = if (isTrackSelected) null else androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            if (isTrackSelected) {
                                controller.cancelSleepTimer()
                            } else {
                                controller.startSleepTimerEndOfTrack()
                            }
                        }
                ) {
                    Text(
                        text = "End of Current Song",
                        style = VeylTypography.BodyMedium,
                        fontWeight = if (isTrackSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isTrackSelected) colors.surfacePanel else colors.textPrimary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audiophile fade-out reassurance note
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = colors.surfaceElevated.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
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
                        text = "Veyl applies a smooth 30-second logarithmic volume ramp-down before stopping output to avoid jarring interruptions.",
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}
