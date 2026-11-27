package com.audiophile.player.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiophile.player.ui.theme.LocalVeylColors

@Composable
fun VeylSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null,
    trackHeight: Dp = 6.dp,
    expandedTrackHeight: Dp = 10.dp,
    thumbWidth: Dp = 6.dp,
    thumbHeight: Dp = 18.dp,
    activeColor: Color? = null,
    inactiveColor: Color? = null
) {
    val colors = LocalVeylColors.current
    val effectiveActive = activeColor ?: colors.accentSignal
    val effectiveInactive = inactiveColor ?: colors.surfaceElevated

    val safeValue = if (value.isNaN()) 0f else value.coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(safeValue) }

    val currentFraction = if (isDragging) {
        if (dragFraction.isNaN()) 0f else dragFraction.coerceIn(0f, 1f)
    } else safeValue

    val animatedHeight by animateDpAsState(
        targetValue = if (isDragging) expandedTrackHeight else trackHeight,
        animationSpec = tween(120),
        label = "sliderTrackHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val w = size.width.toFloat()
                    if (w > 0f) {
                        isDragging = true
                        dragFraction = (down.position.x / w).coerceIn(0f, 1f)
                        onValueChange(dragFraction)
                    }

                    val pointerId = down.id
                    drag(pointerId) { change ->
                        if (w > 0f) {
                            change.consume()
                            dragFraction = (change.position.x / w).coerceIn(0f, 1f)
                            onValueChange(dragFraction)
                        }
                    }

                    isDragging = false
                    onValueChangeFinished?.invoke()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(animatedHeight)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val cornerRadius = CornerRadius(canvasHeight / 2f, canvasHeight / 2f)

            // 1. Inactive Track Background
            drawRoundRect(
                color = effectiveInactive,
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, canvasHeight),
                cornerRadius = cornerRadius
            )

            // 2. Active Track Fill with Gradient
            val frac = if (currentFraction.isNaN()) 0f else currentFraction.coerceIn(0f, 1f)
            val activeWidth = (canvasWidth * frac).coerceIn(0f, canvasWidth)
            if (activeWidth > 0f) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            effectiveActive.copy(alpha = 0.8f),
                            effectiveActive
                        )
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(activeWidth, canvasHeight),
                    cornerRadius = cornerRadius
                )
            }

            // 3. Precision Thumb Indicator
            val thumbPxW = thumbWidth.toPx()
            val thumbPxH = if (isDragging) (thumbHeight + 4.dp).toPx() else thumbHeight.toPx()
            val thumbX = (activeWidth - thumbPxW / 2f).coerceIn(0f, canvasWidth - thumbPxW)
            val thumbY = (canvasHeight - thumbPxH) / 2f

            val thumbColor = if (com.audiophile.player.ui.theme.isColorLight(colors.background)) colors.textPrimary else Color.White
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbX, thumbY),
                size = Size(thumbPxW, thumbPxH),
                cornerRadius = CornerRadius(thumbPxW / 2f, thumbPxW / 2f)
            )
        }
    }
}

/**
 * Center-zero bipolar precision slider for EQ bands (-12..+12 dB) and Channel Balance (-1..+1).
 * Fills outward from the center 0-line and matches VeylSlider's minimal hardware aesthetic.
 */
@Composable
fun VeylBipolarSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    activeColor: Color? = null,
    inactiveColor: Color? = null
) {
    val colors = LocalVeylColors.current
    val effectiveActive = (activeColor ?: colors.accentSignal).let {
        if (enabled) it else colors.textMuted.copy(alpha = 0.45f)
    }
    val effectiveInactive = inactiveColor ?: colors.surfaceElevated

    val minVal = valueRange.start
    val maxVal = valueRange.endInclusive
    val span = (maxVal - minVal).coerceAtLeast(0.001f)
    val zeroFraction = ((0f - minVal) / span).coerceIn(0f, 1f)
    val safeFraction = ((value.coerceIn(minVal, maxVal) - minVal) / span).coerceIn(0f, 1f)

    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(safeFraction) }

    val currentFraction = if (isDragging) dragFraction else safeFraction

    val animatedHeight by animateDpAsState(
        targetValue = if (isDragging) 6.dp else 4.dp,
        animationSpec = tween(110),
        label = "bipolarTrackHeight"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .pointerInput(enabled, minVal, maxVal) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val w = size.width.toFloat()
                    if (w > 0f) {
                        isDragging = true
                        dragFraction = (down.position.x / w).coerceIn(0f, 1f)
                        onValueChange(minVal + dragFraction * span)
                    }

                    val pointerId = down.id
                    drag(pointerId) { change ->
                        if (w > 0f) {
                            change.consume()
                            dragFraction = (change.position.x / w).coerceIn(0f, 1f)
                            onValueChange(minVal + dragFraction * span)
                        }
                    }

                    isDragging = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(animatedHeight)) {
            val canvasWidth = size.width
            val canvasHeight = size.height
            val cornerRadius = CornerRadius(canvasHeight / 2f, canvasHeight / 2f)

            // 1. Track Background
            drawRoundRect(
                color = effectiveInactive,
                topLeft = Offset(0f, 0f),
                size = Size(canvasWidth, canvasHeight),
                cornerRadius = cornerRadius
            )

            // 2. Center 0-line tick
            val zeroX = canvasWidth * zeroFraction
            drawLine(
                color = colors.textMuted.copy(alpha = 0.45f),
                start = Offset(zeroX, -3.dp.toPx()),
                end = Offset(zeroX, canvasHeight + 3.dp.toPx()),
                strokeWidth = 1.5.dp.toPx()
            )

            // 3. Bipolar Fill from center zeroX to thumbX
            val thumbCenterX = (canvasWidth * currentFraction).coerceIn(0f, canvasWidth)
            val fillLeft = minOf(zeroX, thumbCenterX)
            val fillWidth = kotlin.math.abs(thumbCenterX - zeroX)
            if (fillWidth > 1f) {
                drawRoundRect(
                    color = effectiveActive,
                    topLeft = Offset(fillLeft, 0f),
                    size = Size(fillWidth, canvasHeight),
                    cornerRadius = cornerRadius
                )
            }

            // 4. Precision Thumb Bar
            val thumbW = 4.dp.toPx()
            val thumbH = if (isDragging) 18.dp.toPx() else 14.dp.toPx()
            val thumbX = (thumbCenterX - thumbW / 2f).coerceIn(0f, canvasWidth - thumbW)
            val thumbY = (canvasHeight - thumbH) / 2f

            val thumbColor = if (!enabled) colors.textMuted else if (com.audiophile.player.ui.theme.isColorLight(colors.background)) colors.textPrimary else Color.White
            drawRoundRect(
                color = thumbColor,
                topLeft = Offset(thumbX, thumbY),
                size = Size(thumbW, thumbH),
                cornerRadius = CornerRadius(thumbW / 2f, thumbW / 2f)
            )
        }
    }
}
