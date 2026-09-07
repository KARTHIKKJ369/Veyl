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
                    val w = size.width.toFloat()
                    if (w > 0f) {
                        isDragging = true
                        dragFraction = (down.position.x / w).coerceIn(0f, 1f)
                        onValueChange(dragFraction)
                    }

                    var pointerId = down.id
                    val isDrag = drag(pointerId) { change ->
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

            drawRoundRect(
                color = Color.White,
                topLeft = Offset(thumbX, thumbY),
                size = Size(thumbPxW, thumbPxH),
                cornerRadius = CornerRadius(thumbPxW / 2f, thumbPxW / 2f)
            )
        }
    }
}
