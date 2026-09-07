package com.audiophile.player.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylSpacing
import com.audiophile.player.ui.theme.VeylTypography
import com.audiophile.player.ui.theme.buildVeylColorScheme
import com.audiophile.player.ui.theme.toHex
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

enum class PaletteTarget {
    PRIMARY,
    SECONDARY,
    BACKGROUND
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VeylColorWheelPickerSheet(
    controller: AudioEngineController,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = LocalHapticFeedback.current
    val currentColors = LocalVeylColors.current

    val savedPrimary by controller.customPrimary.collectAsState()
    val savedSecondary by controller.customSecondary.collectAsState()
    val savedBackground by controller.customBackground.collectAsState()

    var activeTarget by remember { mutableStateOf(PaletteTarget.PRIMARY) }

    var primaryColor by remember { mutableStateOf(savedPrimary) }
    var secondaryColor by remember { mutableStateOf(savedSecondary) }
    var backgroundColor by remember { mutableStateOf(savedBackground) }

    // Separate HSV state for each target to ensure zero state corruption
    var primaryHsv by remember {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(savedPrimary.toArgb(), arr)
        if (arr[2] < 0.4f) arr[2] = 0.90f
        mutableStateOf(arr)
    }

    var secondaryHsv by remember {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(savedSecondary.toArgb(), arr)
        if (arr[2] < 0.4f) arr[2] = 0.85f
        mutableStateOf(arr)
    }

    var backgroundHsv by remember {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(savedBackground.toArgb(), arr)
        mutableStateOf(arr)
    }

    val activeHsv = when (activeTarget) {
        PaletteTarget.PRIMARY -> primaryHsv
        PaletteTarget.SECONDARY -> secondaryHsv
        PaletteTarget.BACKGROUND -> backgroundHsv
    }

    val activeColor = when (activeTarget) {
        PaletteTarget.PRIMARY -> primaryColor
        PaletteTarget.SECONDARY -> secondaryColor
        PaletteTarget.BACKGROUND -> backgroundColor
    }

    fun onWheelColorChange(newHue: Float, newSat: Float) {
        val minB = if (activeTarget == PaletteTarget.BACKGROUND) 0.08f else 0.45f
        val v = activeHsv[2].coerceAtLeast(minB)
        val newHsv = floatArrayOf(newHue, newSat, v)
        val newColor = Color(AndroidColor.HSVToColor(newHsv))

        when (activeTarget) {
            PaletteTarget.PRIMARY -> {
                primaryHsv = newHsv
                primaryColor = newColor
            }
            PaletteTarget.SECONDARY -> {
                secondaryHsv = newHsv
                secondaryColor = newColor
            }
            PaletteTarget.BACKGROUND -> {
                backgroundHsv = newHsv
                backgroundColor = newColor
            }
        }
    }

    fun onBrightnessChange(newV: Float) {
        val newHsv = floatArrayOf(activeHsv[0], activeHsv[1], newV)
        val newColor = Color(AndroidColor.HSVToColor(newHsv))

        when (activeTarget) {
            PaletteTarget.PRIMARY -> {
                primaryHsv = newHsv
                primaryColor = newColor
            }
            PaletteTarget.SECONDARY -> {
                secondaryHsv = newHsv
                secondaryColor = newColor
            }
            PaletteTarget.BACKGROUND -> {
                backgroundHsv = newHsv
                backgroundColor = newColor
            }
        }
    }

    fun onSelectSwatch(swatch: Color) {
        val arr = FloatArray(3)
        AndroidColor.colorToHSV(swatch.toArgb(), arr)
        when (activeTarget) {
            PaletteTarget.PRIMARY -> {
                if (arr[2] < 0.4f) arr[2] = 0.90f
                primaryHsv = arr
                primaryColor = swatch
            }
            PaletteTarget.SECONDARY -> {
                if (arr[2] < 0.4f) arr[2] = 0.85f
                secondaryHsv = arr
                secondaryColor = swatch
            }
            PaletteTarget.BACKGROUND -> {
                backgroundHsv = arr
                backgroundColor = swatch
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = currentColors.surfacePanel,
        contentColor = currentColors.textPrimary,
        scrimColor = Color.Black.copy(alpha = 0.65f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = VeylSpacing.md)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(VeylSpacing.md)
        ) {
            // 1. Header Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "STUDIO PALETTE LAB",
                        style = VeylTypography.MonoBadge,
                        color = currentColors.accentSignal
                    )
                    Text(
                        text = "Custom Palette & Wheel",
                        style = VeylTypography.HeadlineMedium,
                        color = currentColors.textPrimary
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Reset to default
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(currentColors.glassButtonBg)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val defP = Color(0xFFFFB7B4)
                                val defS = Color(0xFFB2CAD3)
                                val defBg = Color(0xFF1B0906)
                                primaryColor = defP
                                secondaryColor = defS
                                backgroundColor = defBg
                                val arrP = FloatArray(3)
                                AndroidColor.colorToHSV(defP.toArgb(), arrP)
                                primaryHsv = arrP
                                val arrS = FloatArray(3)
                                AndroidColor.colorToHSV(defS.toArgb(), arrS)
                                secondaryHsv = arrS
                                val arrBg = FloatArray(3)
                                AndroidColor.colorToHSV(defBg.toArgb(), arrBg)
                                backgroundHsv = arrBg
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Reset",
                            style = VeylTypography.MonoBadge,
                            color = currentColors.textSecondary,
                            fontSize = 11.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(currentColors.glassButtonBg)
                    ) {
                        Icon(
                            imageVector = VeylIcons.Close,
                            contentDescription = "Close",
                            tint = currentColors.textSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // 2. Palette Target Segmented Tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VeylSpacing.RadiusLg))
                    .background(currentColors.surfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PaletteTab(
                    label = "Primary",
                    color = primaryColor,
                    selected = activeTarget == PaletteTarget.PRIMARY,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        activeTarget = PaletteTarget.PRIMARY
                    }
                )

                PaletteTab(
                    label = "Secondary",
                    color = secondaryColor,
                    selected = activeTarget == PaletteTarget.SECONDARY,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        activeTarget = PaletteTarget.SECONDARY
                    }
                )

                PaletteTab(
                    label = "Background",
                    color = backgroundColor,
                    selected = activeTarget == PaletteTarget.BACKGROUND,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        activeTarget = PaletteTarget.BACKGROUND
                    }
                )
            }

            // 3. Interactive Color Wheel Canvas with Scroll-Isolation
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(230.dp),
                contentAlignment = Alignment.Center
            ) {
                ColorWheelCanvas(
                    hue = activeHsv[0],
                    saturation = activeHsv[1],
                    onColorChange = { newHue, newSat ->
                        onWheelColorChange(newHue, newSat)
                    }
                )
            }

            // 4. Brightness & Lightness Slider
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (activeTarget == PaletteTarget.BACKGROUND) "Canvas Dark Depth" else "Brightness & Luminance",
                        style = VeylTypography.TitleMedium,
                        color = currentColors.textPrimary,
                        fontSize = 13.sp
                    )
                    Text(
                        text = "${(activeHsv[2] * 100).toInt()}%",
                        style = VeylTypography.MonoSpec,
                        color = currentColors.accentSignal
                    )
                }

                Slider(
                    value = activeHsv[2],
                    onValueChange = {
                        onBrightnessChange(it)
                    },
                    valueRange = if (activeTarget == PaletteTarget.BACKGROUND) 0.00f..0.30f else 0.15f..1.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = activeColor,
                        activeTrackColor = activeColor,
                        inactiveTrackColor = currentColors.surfaceElevated
                    )
                )
            }

            // 5. Hex Code Display & Current Target
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(activeColor)
                            .border(1.5.dp, Color.White.copy(alpha = 0.5f), CircleShape)
                    )
                    Text(
                        text = activeColor.toHex(),
                        style = VeylTypography.MonoBadge,
                        color = currentColors.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Tinting: ${activeTarget.name}",
                    style = VeylTypography.MonoSpec,
                    color = currentColors.accentSignal,
                    fontSize = 11.sp
                )
            }

            // Quick Swatches for Current Target
            Text(
                text = if (activeTarget == PaletteTarget.BACKGROUND) "CANVAS TONE PRESETS" else "AUDIOPHILE ACCENT PRESETS",
                style = VeylTypography.MonoBadge,
                color = currentColors.textSecondary,
                fontSize = 10.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val swatches = if (activeTarget == PaletteTarget.BACKGROUND) {
                    listOf(
                        Color(0xFF1B0906), // Warm Obsidian (Default)
                        Color(0xFF000000), // OLED True Black
                        Color(0xFF0D1117), // Deep Slate Navy
                        Color(0xFF121212), // Neutral Studio Carbon
                        Color(0xFF08140E), // Analogue Console Forest
                        Color(0xFF13091B)  // Royal Velvet Plum
                    )
                } else {
                    listOf(
                        Color(0xFFFFB7B4), // Coral Blush
                        Color(0xFF00F0FF), // Electric Cyan
                        Color(0xFFFF9E00), // Radiant Amber
                        Color(0xFF58A6FF), // Arctic Glacier
                        Color(0xFF2ECC71), // Console Emerald
                        Color(0xFFD8B4FE), // Electric Lavender
                        Color(0xFFFFFFFF), // Crisp White
                        Color(0xFFB2CAD3)  // Soft Slate
                    )
                }

                swatches.forEach { swatch ->
                    val isSelected = activeColor == swatch
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(swatch)
                            .border(
                                width = if (isSelected) 2.5.dp else 1.dp,
                                color = if (isSelected) currentColors.textPrimary else Color.White.copy(alpha = 0.2f),
                                shape = CircleShape
                            )
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onSelectSwatch(swatch)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(if (swatch.red > 0.6f && swatch.green > 0.6f) Color.Black else Color.White)
                            )
                        }
                    }
                }
            }

            // 6. Live Swatch Mini Preview Matrix
            Text(
                text = "LIVE THEME PREVIEW",
                style = VeylTypography.MonoBadge,
                color = currentColors.textSecondary,
                fontSize = 10.sp
            )

            val previewScheme = remember(primaryColor, secondaryColor, backgroundColor) {
                buildVeylColorScheme(
                    primary = primaryColor,
                    secondary = secondaryColor,
                    tertiary = Color(0xFF92EAFF),
                    background = backgroundColor,
                    isDark = true
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VeylSpacing.RadiusMd))
                    .background(previewScheme.background)
                    .border(1.dp, previewScheme.borderHairline, RoundedCornerShape(VeylSpacing.RadiusMd))
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(previewScheme.surfaceElevated)
                                    .border(1.dp, previewScheme.borderHairline, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = VeylIcons.AudioFile,
                                    contentDescription = null,
                                    tint = previewScheme.accentSignal,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Column {
                                Text(
                                    text = "Giorgio by Moroder",
                                    style = VeylTypography.TitleMedium,
                                    color = previewScheme.textPrimary,
                                    fontSize = 13.sp
                                )
                                Text(
                                    text = "Daft Punk • Random Access Memories",
                                    style = VeylTypography.BodySmall,
                                    color = previewScheme.textSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        // Play FAB preview
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(previewScheme.accentSignal),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = VeylIcons.Play,
                                contentDescription = null,
                                tint = if (previewScheme.accentSignal.red > 0.6f && previewScheme.accentSignal.green > 0.6f) Color.Black else Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Bottom telemetry line in preview
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(previewScheme.surfacePill)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "DSD 5.6M • BIT-PERFECT",
                                style = VeylTypography.MonoBadge,
                                color = previewScheme.textMono,
                                fontSize = 9.sp
                            )
                        }

                        Text(
                            text = "1.4 ms latency",
                            style = VeylTypography.MonoSpec,
                            color = previewScheme.textSecondary,
                            fontSize = 9.sp
                        )
                    }
                }
            }

            // 7. Apply Theme Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(VeylSpacing.RadiusLg))
                    .background(primaryColor)
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        controller.applyCustomPalette(primaryColor, secondaryColor, backgroundColor)
                        onDismiss()
                    }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Apply Custom Palette",
                    style = VeylTypography.TitleMedium,
                    color = if (primaryColor.red > 0.6f && primaryColor.green > 0.6f) Color.Black else Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun PaletteTab(
    label: String,
    color: Color,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = LocalVeylColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(VeylSpacing.RadiusMd))
            .background(if (selected) colors.primaryContainer else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, if (selected) colors.accentSignal else colors.borderHairline, CircleShape)
            )
            Text(
                text = label,
                style = VeylTypography.TitleMedium,
                color = if (selected) colors.textPrimary else colors.textSecondary,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
private fun ColorWheelCanvas(
    hue: Float,
    saturation: Float,
    onColorChange: (hue: Float, saturation: Float) -> Unit
) {
    Canvas(
        modifier = Modifier
            .size(220.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val centerX = size.width / 2f
                    val centerY = size.height / 2f
                    val radius = minOf(centerX, centerY) * 0.88f

                    fun processPosition(pos: Offset) {
                        val dx = pos.x - centerX
                        val dy = pos.y - centerY
                        val dist = hypot(dx, dy)
                        val newSat = (dist / radius).coerceIn(0f, 1f)
                        var angle = (atan2(dy, dx) * 180f / PI).toFloat()
                        if (angle < 0) angle += 360f
                        onColorChange(angle, newSat)
                    }

                    processPosition(down.position)
                    down.consume()

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            processPosition(change.position)
                            change.consume()
                        } else {
                            break
                        }
                    }
                }
            }
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val radius = minOf(centerX, centerY) * 0.88f

        // 1. Sweep Gradient for 360-degree Hue
        val sweepBrush = Brush.sweepGradient(
            listOf(
                Color.Red,
                Color.Yellow,
                Color.Green,
                Color.Cyan,
                Color.Blue,
                Color.Magenta,
                Color.Red
            ),
            center = Offset(centerX, centerY)
        )

        drawCircle(
            brush = sweepBrush,
            radius = radius,
            center = Offset(centerX, centerY)
        )

        // 2. Radial Gradient for Saturation (Center White to Outer Transparent)
        val whiteBrush = Brush.radialGradient(
            listOf(Color.White, Color.Transparent),
            center = Offset(centerX, centerY),
            radius = radius
        )

        drawCircle(
            brush = whiteBrush,
            radius = radius,
            center = Offset(centerX, centerY)
        )

        // 3. Subtle Outer Ring Border
        drawCircle(
            color = Color.White.copy(alpha = 0.35f),
            radius = radius,
            center = Offset(centerX, centerY),
            style = Stroke(width = 2.dp.toPx())
        )

        // 4. Selector Thumb Indicator
        val rad = (hue * PI / 180f).toFloat()
        val thumbDist = saturation.coerceIn(0f, 1f) * radius
        val thumbX = centerX + thumbDist * cos(rad)
        val thumbY = centerY + thumbDist * sin(rad)

        // Thumb Outer Glow / Shadow Ring
        drawCircle(
            color = Color.Black.copy(alpha = 0.55f),
            radius = 13.dp.toPx(),
            center = Offset(thumbX, thumbY)
        )

        // Thumb White Outer Ring
        drawCircle(
            color = Color.White,
            radius = 11.dp.toPx(),
            center = Offset(thumbX, thumbY),
            style = Stroke(width = 2.5.dp.toPx())
        )

        // Thumb Inner Fill
        val selectedRgb = AndroidColor.HSVToColor(floatArrayOf(hue, saturation, 1f))
        drawCircle(
            color = Color(selectedRgb),
            radius = 8.dp.toPx(),
            center = Offset(thumbX, thumbY)
        )
    }
}
