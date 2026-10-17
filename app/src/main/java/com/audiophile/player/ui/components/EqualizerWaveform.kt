package com.audiophile.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.audiophile.player.ui.theme.LocalVeylColors
import kotlin.math.roundToInt

@Composable
fun EqualizerWaveform(
    bands: List<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(100.dp),
    enabled: Boolean = true,
    color: Color = LocalVeylColors.current.accentSignal,
    fillColor: Color = color.copy(alpha = 0.25f),
    strokeWidth: Float = 4f,
    smoothingTension: Float = 0.22f,
    onBandGainChange: ((index: Int, gainDb: Float) -> Unit)? = null
) {
    if (bands.isEmpty()) return

    val contentAlpha = if (enabled) 1f else 0.4f
    val actualColor = color.copy(alpha = color.alpha * contentAlpha)
    val actualFillColor = fillColor.copy(alpha = fillColor.alpha * contentAlpha)

    val minGain = valueRange.start
    val maxGain = valueRange.endInclusive
    val rangeSize = (maxGain - minGain).coerceAtLeast(0.001f)

    var activeNodeIndex by remember { mutableStateOf<Int?>(null) }

    val interactionModifier = if (enabled && onBandGainChange != null) {
        modifier
            .pointerInput(bands, valueRange) {
                detectDragGestures(
                    onDragStart = { offset ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        if (width > 0f && bands.isNotEmpty()) {
                            val nearestIdx = if (bands.size > 1) {
                                ((offset.x / width) * (bands.size - 1)).roundToInt().coerceIn(0, bands.size - 1)
                            } else 0
                            activeNodeIndex = nearestIdx
                            val newGain = minGain + (1f - (offset.y / height).coerceIn(0f, 1f)) * rangeSize
                            val roundedGain = (Math.round(newGain * 2) / 2.0f).coerceIn(minGain, maxGain)
                            onBandGainChange(nearestIdx, roundedGain)
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val idx = activeNodeIndex
                        val height = size.height.toFloat()
                        if (idx != null && height > 0f) {
                            val newGain = minGain + (1f - (change.position.y / height).coerceIn(0f, 1f)) * rangeSize
                            val roundedGain = (Math.round(newGain * 2) / 2.0f).coerceIn(minGain, maxGain)
                            onBandGainChange(idx, roundedGain)
                        }
                    },
                    onDragEnd = { activeNodeIndex = null },
                    onDragCancel = { activeNodeIndex = null }
                )
            }
            .pointerInput(bands, valueRange) {
                detectTapGestures { offset ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    if (width > 0f && bands.isNotEmpty()) {
                        val nearestIdx = if (bands.size > 1) {
                            ((offset.x / width) * (bands.size - 1)).roundToInt().coerceIn(0, bands.size - 1)
                        } else 0
                        val newGain = minGain + (1f - (offset.y / height).coerceIn(0f, 1f)) * rangeSize
                        val roundedGain = (Math.round(newGain * 2) / 2.0f).coerceIn(minGain, maxGain)
                        onBandGainChange(nearestIdx, roundedGain)
                    }
                }
            }
    } else {
        modifier
    }

    Canvas(modifier = interactionModifier) {
        val width = size.width
        val height = size.height

        // Draw baseline (0 dB)
        if (0f in valueRange) {
            val baselineY = height - ((0f - minGain) / rangeSize) * height
            drawLine(
                color = actualColor.copy(alpha = 0.15f),
                start = Offset(0f, baselineY),
                end = Offset(width, baselineY),
                strokeWidth = 1.5f
            )
        }

        val points = bands.mapIndexed { index, bandValue ->
            val x = if (bands.size > 1) {
                (index.toFloat() / (bands.size - 1)) * width
            } else {
                width / 2f
            }
            val clampedVal = bandValue.coerceIn(minGain, maxGain)
            val y = height - ((clampedVal - minGain) / rangeSize) * height
            Offset(x, y)
        }

        if (points.size >= 2) {
            val strokePath = Path().apply {
                moveTo(points[0].x, points[0].y)

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]

                    val prev = if (i > 0) points[i - 1] else p0
                    val next = if (i < points.size - 2) points[i + 2] else p1

                    val cp1x = p0.x + (p1.x - prev.x) * smoothingTension
                    val cp1y = p0.y + (p1.y - prev.y) * smoothingTension

                    val cp2x = p1.x - (next.x - p0.x) * smoothingTension
                    val cp2y = p1.y - (next.y - p0.y) * smoothingTension

                    cubicTo(cp1x, cp1y, cp2x, cp2y, p1.x, p1.y)
                }
            }

            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(width, height)
                lineTo(0f, height)
                close()
            }

            // Gradient Fill under curve
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(actualFillColor, Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Smooth Curve Line
            drawPath(
                path = strokePath,
                color = actualColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Dynamic Band Node Dots
            points.forEachIndexed { index, point ->
                val isActive = index == activeNodeIndex
                if (isActive) {
                    drawCircle(
                        color = actualColor.copy(alpha = 0.35f),
                        radius = strokeWidth * 4f,
                        center = point
                    )
                    drawCircle(
                        color = actualColor,
                        radius = strokeWidth * 2.2f,
                        center = point
                    )
                    drawCircle(
                        color = Color.White,
                        radius = strokeWidth * 1.0f,
                        center = point
                    )
                } else {
                    drawCircle(
                        color = actualColor,
                        radius = strokeWidth * 1.6f,
                        center = point
                    )
                    drawCircle(
                        color = Color.White,
                        radius = strokeWidth * 0.7f,
                        center = point
                    )
                }
            }
        }
    }
}
