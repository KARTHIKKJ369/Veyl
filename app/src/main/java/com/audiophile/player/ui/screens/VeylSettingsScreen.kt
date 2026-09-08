package com.audiophile.player.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.components.VeylColorWheelPickerSheet
import com.audiophile.player.ui.theme.CuratedPresets
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylSpacing
import com.audiophile.player.ui.theme.VeylTypography
import uniffi.audiophile_core.DsdModeEnum

enum class ThemeModeOption {
    LIGHT,
    DARK,
    SYSTEM
}

/**
 * SCREEN 5: SETTINGS & HARDWARE ENGINE
 * Precision-crafted audiophile master control matching high-end hardware ergonomics.
 */
@Composable
fun VeylSettingsScreen(
    controller: AudioEngineController,
    onSelectRootFolder: () -> Unit,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current

    val rootPath by controller.selectedRootPath.collectAsState()
    val isScanning by controller.isScanning.collectAsState()
    val libraryTracks by controller.libraryTracks.collectAsState()
    val connectedDac by controller.connectedDac.collectAsState()
    val dsdMode by controller.dsdMode.collectAsState()
    val bitPerfectEnabled by controller.bitPerfectEnabled.collectAsState()
    val bufferFrameSize by controller.bufferFrameSize.collectAsState()
    val gaplessEnabled by controller.gaplessEnabled.collectAsState()
    val autoFetchLyrics by controller.autoFetchLyrics.collectAsState()
    val selectedThemeId by controller.selectedThemeId.collectAsState()
    val isDarkMode by controller.isDarkMode.collectAsState()

    var themeMode by remember(isDarkMode) {
        mutableStateOf(if (isDarkMode) ThemeModeOption.DARK else ThemeModeOption.LIGHT)
    }

    var mmapExclusiveEnabled by remember { mutableStateOf(bitPerfectEnabled) }
    var cachedLyricsCount by remember { mutableIntStateOf(com.audiophile.player.engine.LyricsManager.getCachedLyricsCount()) }
    var showColorWheelSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Top Header: Chevron Left, "Engine Settings", "Fine-tune your sound.", "Reset", and Refresh Button
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onBack()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = VeylIcons.ChevronLeft,
                                contentDescription = "Back",
                                tint = colors.textPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Engine Settings",
                                style = VeylTypography.HeadlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontSize = 21.sp
                            )
                            Text(
                                text = "Fine-tune your sound.",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 13.sp
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Reset",
                            style = VeylTypography.BodySmall,
                            color = colors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    controller.resetSettingsToDefault()
                                    themeMode = ThemeModeOption.DARK
                                    mmapExclusiveEnabled = true
                                }
                                .padding(horizontal = 4.dp, vertical = 6.dp)
                        )

                        IconButton(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectRootFolder()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            if (isScanning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = colors.accentSignal,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = VeylIcons.Refresh,
                                    contentDescription = "Rescan",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Luxury Hero Banner: "Pure Audio / Deeper Feeling" with Concentric Aperture Art & "LISTEN / TUNE / EXPERIENCE"
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    Color(0xFF191B22),
                                    Color(0xFF13151A),
                                    Color(0xFF0F1014)
                                )
                            )
                        )
                        .border(1.dp, colors.borderHairline.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 20.dp, vertical = 18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                text = "Pure Audio",
                                style = VeylTypography.HeadlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 21.sp,
                                letterSpacing = (-0.3).sp
                            )
                            Text(
                                text = "Deeper Feeling",
                                style = VeylTypography.HeadlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 21.sp,
                                letterSpacing = (-0.3).sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Precision. Performance. Peace.",
                                style = VeylTypography.BodySmall,
                                color = Color(0xFF9E9E9E),
                                fontSize = 11.sp
                            )
                        }

                        // Concentric Vinyl / Aperture Lens Art + Typographic Spec
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Canvas(modifier = Modifier.size(80.dp)) {
                                val center = Offset(size.width / 2f, size.height / 2f)
                                val maxR = size.width / 2f

                                // Outer subtle glow ring
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        listOf(Color(0xFF2C3240), Color(0xFF13161C)),
                                        center = center,
                                        radius = maxR
                                    ),
                                    radius = maxR,
                                    center = center
                                )

                                // Inscribed concentric groove rings
                                val ringFractions = listOf(0.92f, 0.82f, 0.72f, 0.62f, 0.52f, 0.42f, 0.32f, 0.20f)
                                ringFractions.forEachIndexed { idx, frac ->
                                    drawCircle(
                                        color = if (idx % 2 == 0) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.08f),
                                        radius = maxR * frac,
                                        center = center,
                                        style = Stroke(width = if (idx == 0 || idx == 3) 1.5f else 1f)
                                    )
                                }

                                // Center spindle core
                                drawCircle(
                                    color = Color(0xFF38BDF8).copy(alpha = 0.8f),
                                    radius = maxR * 0.12f,
                                    center = center
                                )
                                drawCircle(
                                    color = Color.White,
                                    radius = maxR * 0.05f,
                                    center = center
                                )
                            }

                            // Typographic vertical motto
                            Column(
                                verticalArrangement = Arrangement.spacedBy(3.dp),
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = "LISTEN",
                                    style = VeylTypography.MonoBadge,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 8.5.sp,
                                    letterSpacing = 1.6.sp
                                )
                                Text(
                                    text = "TUNE",
                                    style = VeylTypography.MonoBadge,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 8.5.sp,
                                    letterSpacing = 1.6.sp
                                )
                                Text(
                                    text = "EXPERIENCE",
                                    style = VeylTypography.MonoBadge,
                                    color = Color.White.copy(alpha = 0.75f),
                                    fontSize = 8.5.sp,
                                    letterSpacing = 1.6.sp
                                )
                            }
                        }
                    }
                }
            }

            // 3. Section: "Appearance" ("Make it yours.") + Segmented Pill + Curated Presets
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Appearance",
                                style = VeylTypography.HeadlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = colors.textPrimary,
                                fontSize = 18.sp
                            )
                            Text(
                                text = "Make it yours.",
                                style = VeylTypography.BodySmall,
                                color = colors.textSecondary,
                                fontSize = 12.sp
                            )
                        }

                        // Segmented 3-way Theme Pill: [ Light | Dark | System ]
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(colors.surfacePanel)
                                .border(1.dp, colors.borderHairline, RoundedCornerShape(20.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Light Option
                            val isLightSelected = themeMode == ThemeModeOption.LIGHT
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isLightSelected) colors.surfaceElevated else Color.Transparent)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        themeMode = ThemeModeOption.LIGHT
                                        controller.setDarkMode(false)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.Sun,
                                    contentDescription = "Light Mode",
                                    tint = if (isLightSelected) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Light",
                                    style = VeylTypography.BodySmall,
                                    fontWeight = if (isLightSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isLightSelected) colors.textPrimary else colors.textMuted,
                                    fontSize = 11.5.sp
                                )
                            }

                            // Dark Option
                            val isDarkSelected = themeMode == ThemeModeOption.DARK
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isDarkSelected) colors.surfaceElevated else Color.Transparent)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        themeMode = ThemeModeOption.DARK
                                        controller.setDarkMode(true)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.Moon,
                                    contentDescription = "Dark Mode",
                                    tint = if (isDarkSelected) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "Dark",
                                    style = VeylTypography.BodySmall,
                                    fontWeight = if (isDarkSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isDarkSelected) colors.textPrimary else colors.textMuted,
                                    fontSize = 11.5.sp
                                )
                            }

                            // System Option
                            val isSystemSelected = themeMode == ThemeModeOption.SYSTEM
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSystemSelected) colors.surfaceElevated else Color.Transparent)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        themeMode = ThemeModeOption.SYSTEM
                                        controller.setDarkMode(true)
                                    }
                                    .padding(horizontal = 9.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.MonitorSystem,
                                    contentDescription = "System Mode",
                                    tint = if (isSystemSelected) colors.textPrimary else colors.textMuted,
                                    modifier = Modifier.size(13.dp)
                                )
                                Text(
                                    text = "System",
                                    style = VeylTypography.BodySmall,
                                    fontWeight = if (isSystemSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSystemSelected) colors.textPrimary else colors.textMuted,
                                    fontSize = 11.5.sp
                                )
                            }
                        }
                    }

                    // Horizontal Row of Theme Preset Cards: Monochrome Carbon, Braun Dieter Rams, Macintosh Lab, etc.
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CuratedPresets.forEach { preset ->
                            val isSelected = selectedThemeId == preset.id
                            val displayName = when (preset.id) {
                                "monochrome_carbon" -> "Monochrome\nCarbon"
                                "mcintosh_blue" -> "Macintosh Lab"
                                "braun_rams" -> "Braun Dieter Rams"
                                else -> preset.name
                            }

                            Box(
                                modifier = Modifier
                                    .width(136.dp)
                                    .height(96.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) colors.surfaceElevated else colors.surfacePanel)
                                    .border(
                                        width = if (isSelected) 1.5.dp else 1.dp,
                                        color = if (isSelected) Color.White else colors.borderHairline,
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        controller.applyThemePreset(preset.id)
                                    }
                                    .padding(12.dp)
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    // Three Swatches & Checkmark
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(13.dp)
                                                    .clip(CircleShape)
                                                    .background(preset.primary)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(11.dp)
                                                    .clip(CircleShape)
                                                    .background(preset.secondary)
                                            )
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(preset.background)
                                                    .border(0.5.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                                            )
                                        }

                                        if (isSelected) {
                                            Icon(
                                                imageVector = VeylIcons.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }

                                    Text(
                                        text = displayName,
                                        style = VeylTypography.BodySmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) Color.White else colors.textSecondary,
                                        fontSize = 12.sp,
                                        lineHeight = 15.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Custom Palette Button Trigger
                        Box(
                            modifier = Modifier
                                .width(80.dp)
                                .height(96.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfacePanel)
                                .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp))
                                .clickable {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showColorWheelSheet = true
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = VeylIcons.Palette,
                                    contentDescription = "Color Wheel",
                                    tint = colors.accentSignal,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = "Custom",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }

            // 4. Section: "Audio Hardware" (Rescan pill + USB DAC Hardware Interface card)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Audio Hardware",
                            style = VeylTypography.HeadlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                            fontSize = 18.sp
                        )

                        // Rescan button pill
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surfacePanel,
                            border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline),
                            modifier = Modifier.clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectRootFolder()
                            }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Rescan",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 11.5.sp
                                )
                                Icon(
                                    imageVector = VeylIcons.Refresh,
                                    contentDescription = "Rescan hardware",
                                    tint = colors.textSecondary,
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                        }
                    }

                    // USB DAC Hardware Interface Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfacePanel)
                            .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Squircle Icon Container
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.surfaceElevated),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = VeylIcons.UsbDac,
                                    contentDescription = null,
                                    tint = if (connectedDac != null) colors.accentSignal else colors.textMuted,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Center Info
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "USB DAC Hardware Interface",
                                        style = VeylTypography.TitleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        fontSize = 14.sp
                                    )

                                    // Badge
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF1E2838))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (connectedDac != null) "HARDWARE ATTACHED" else "INTERNAL HAL",
                                            style = VeylTypography.MonoBadge,
                                            color = Color(0xFF4C8DFF),
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                Text(
                                    text = connectedDac?.name ?: "Built-in High-Definition Audio HAL / AudioFlinger",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 12.sp
                                )

                                Text(
                                    text = if (connectedDac != null) {
                                        "Supported PCM: ${connectedDac?.supportedSampleRates?.joinToString(", ") { "${it / 1000}kHz" }} • Direct USB Async I/O"
                                    } else {
                                        "Direct hardware access ready when external USB OTG DAC is connected."
                                    },
                                    style = VeylTypography.BodySmall,
                                    color = Color(0xFF4C8DFF),
                                    fontSize = 11.sp
                                )
                            }

                            Icon(
                                imageVector = VeylIcons.ChevronRight,
                                contentDescription = "Open Hardware Options",
                                tint = colors.textMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // 5. Section: "Output Driver & Bit-Perfect Pipeline"
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Output Driver & Bit-Perfect Pipeline",
                        style = VeylTypography.HeadlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )

                    // Unified Pipeline Card
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfacePanel)
                            .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp))
                    ) {
                        Column {
                            // Row 1: AAudio MMAP Exclusive Mode + Switch
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(colors.surfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Waveform,
                                        contentDescription = null,
                                        tint = colors.accentSignal,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "AAudio MMAP Exclusive Mode",
                                        style = VeylTypography.TitleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "Bypasses Android AudioFlinger mixer for 100% bit-perfect output directly to audio hardware buffers.",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Switch(
                                    checked = mmapExclusiveEnabled,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        mmapExclusiveEnabled = it
                                        controller.setBitPerfectEnabled(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2E333D),
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    )
                                )
                            }

                            HorizontalDivider(
                                color = colors.borderHairline.copy(alpha = 0.5f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )

                            // Row 2: Hardware Buffer Frame Size
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        val nextSize = when (bufferFrameSize) {
                                            64 -> 128
                                            128 -> 256
                                            256 -> 512
                                            else -> 64
                                        }
                                        controller.setBufferFrameSize(nextSize)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.LayersBuffer,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Text(
                                    text = "Hardware Buffer Frame Size",
                                    style = VeylTypography.Body,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    fontSize = 13.5.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                val ms = String.format(java.util.Locale.US, "%.1f", bufferFrameSize * 1000.0 / 44100.0)
                                Text(
                                    text = "$bufferFrameSize frames ($ms ms) >",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp
                                )
                            }

                            HorizontalDivider(
                                color = colors.borderHairline.copy(alpha = 0.5f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )

                            // Row 3: Sample Rate
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.PulseSine,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Text(
                                    text = "Sample Rate",
                                    style = VeylTypography.Body,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    fontSize = 13.5.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = "Automatic (Best Match) >",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp
                                )
                            }

                            HorizontalDivider(
                                color = colors.borderHairline.copy(alpha = 0.5f),
                                thickness = 0.8.dp,
                                modifier = Modifier.padding(horizontal = 14.dp)
                            )

                            // Row 4: DSD Output Mode
                            val isDop = dsdMode == DsdModeEnum.DO_P
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        controller.setDsdMode(if (isDop) DsdModeEnum.PCM_DECIMATION else DsdModeEnum.DO_P)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 13.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(colors.surfaceElevated),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Dsd,
                                        contentDescription = null,
                                        tint = colors.textSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                Text(
                                    text = "DSD Output Mode",
                                    style = VeylTypography.Body,
                                    fontWeight = FontWeight.Medium,
                                    color = colors.textPrimary,
                                    fontSize = 13.5.sp,
                                    modifier = Modifier.weight(1f)
                                )

                                Text(
                                    text = if (isDop) "DoP v1.1 (Bit-Perfect) >" else "Sinc-FIR Decimation >",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 12.5.sp
                                )
                            }
                        }
                    }
                }
            }

            // 6. Section: Local Storage & SD Card Roots
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Local Storage & Repository Roots",
                        style = VeylTypography.HeadlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfacePanel)
                            .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = "Active Music Directory",
                                    style = VeylTypography.TitleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colors.textPrimary,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = rootPath ?: "Default Storage (/storage/emulated/0/Music)",
                                    style = VeylTypography.MonoSpec,
                                    color = colors.accentSignal,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${libraryTracks.size} lossless tracks indexed in local database",
                                    style = VeylTypography.BodySmall,
                                    color = colors.textSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = colors.surfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSelectRootFolder()
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 10.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = VeylIcons.Folder,
                                        contentDescription = null,
                                        tint = colors.accentSignal,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Change Audio Directory / SD Card Root",
                                        style = VeylTypography.BodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        fontSize = 12.5.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 7. Section: Synced Lyrics & Metadata Cache
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Synced Lyrics & Metadata Cache",
                        style = VeylTypography.HeadlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.textPrimary,
                        fontSize = 18.sp
                    )

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfacePanel)
                            .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp))
                            .padding(14.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Auto-Fetch Online Lyrics (LRCLIB)",
                                        style = VeylTypography.TitleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = "Automatically queries LRCLIB for synchronized and word-by-word lyrics",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Switch(
                                    checked = autoFetchLyrics,
                                    onCheckedChange = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        controller.setAutoFetchLyrics(it)
                                    },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = Color(0xFF2E333D),
                                        uncheckedThumbColor = colors.textMuted,
                                        uncheckedTrackColor = colors.surfaceElevated
                                    )
                                )
                            }

                            HorizontalDivider(
                                color = colors.borderHairline.copy(alpha = 0.5f),
                                thickness = 0.8.dp
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                    Text(
                                        text = "Offline Lyrics Cache",
                                        style = VeylTypography.TitleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = colors.textPrimary,
                                        fontSize = 13.5.sp
                                    )
                                    Text(
                                        text = "$cachedLyricsCount tracks cached offline in app storage",
                                        style = VeylTypography.BodySmall,
                                        color = colors.textSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = colors.surfaceElevated,
                                    modifier = Modifier.clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        com.audiophile.player.engine.LyricsManager.clearCache()
                                        cachedLyricsCount = 0
                                    }
                                ) {
                                    Text(
                                        text = "Clear Cache",
                                        style = VeylTypography.MonoSpec,
                                        color = colors.accentSignal,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 8. Engine Telemetry Diagnostic Card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated)
                        .border(1.dp, colors.borderHairline, RoundedCornerShape(16.dp))
                        .padding(14.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ENGINE TELEMETRY & BUILD MANIFEST",
                            style = VeylTypography.MonoBadge,
                            color = colors.accentSignal
                        )
                        Text(
                            text = "Veyl Core: Rust 1.80.0 • AAudio Native MMAP Direct • UniFFI Binding v0.28.3",
                            style = VeylTypography.MonoSpec,
                            color = colors.textSecondary,
                            fontSize = 10.sp
                        )
                        Text(
                            text = "Bit-Perfect Status: ACTIVE • Zero Software Re-Sampling • Pure Float32 DSP",
                            style = VeylTypography.MonoSpec,
                            color = colors.accentSignal,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        if (showColorWheelSheet) {
            VeylColorWheelPickerSheet(
                controller = controller,
                onDismiss = { showColorWheelSheet = false }
            )
        }
    }
}
