package com.audiophile.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.audiophile.player.ui.theme.LocalVeylColors

@Composable
fun EqualizerWaveform(
    bandsProvider: () -> List<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(100.dp),
    enabled: Boolean = true,
    color: Color = LocalVeylColors.current.accentSignal,
    fillColor: Color = color.copy(alpha = 0.22f),
    strokeWidth: Float = 5f,
    smoothingTension: Float = 0.24f
) {
    val colors = LocalVeylColors.current
    val contentAlpha = if (enabled) 1f else 0.35f
    val actualColor = color.copy(alpha = color.alpha * contentAlpha)
    val actualFillColor = fillColor.copy(alpha = fillColor.alpha * contentAlpha)
    val gridColor = colors.borderHairline.copy(alpha = 0.35f)

    val minGain = valueRange.start
    val maxGain = valueRange.endInclusive
    val rangeSize = (maxGain - minGain).coerceAtLeast(0.001f)

    Canvas(modifier = modifier) {
        val bands = bandsProvider()
        if (bands.isEmpty()) return@Canvas

        val width = size.width
        val height = size.height
        val padY = 6.dp.toPx()
        val usableH = (height - padY * 2f).coerceAtLeast(1f)

        fun gainToY(g: Float): Float {
            val norm = ((g.coerceIn(minGain, maxGain) - minGain) / rangeSize)
            return padY + (1f - norm) * usableH
        }

        // Subtle horizontal dB reference lines (+6, 0, -6)
        listOf(6f, -6f).forEach { refDb ->
            if (refDb in valueRange) {
                val y = gainToY(refDb)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
            }
        }

        val baselineY = if (0f in valueRange) gainToY(0f) else height / 2f
        drawLine(
            color = colors.textMuted.copy(alpha = 0.28f),
            start = Offset(0f, baselineY),
            end = Offset(width, baselineY),
            strokeWidth = 1.5f
        )

        val points = bands.mapIndexed { index, bandValue ->
            val x = if (bands.size > 1) {
                (index.toFloat() / (bands.size - 1)) * width
            } else {
                width / 2f
            }
            // Subtle vertical octave grid line
            drawLine(
                color = gridColor.copy(alpha = 0.22f),
                start = Offset(x, padY),
                end = Offset(x, height - padY),
                strokeWidth = 1f
            )
            Offset(x, gainToY(bandValue))
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

            // Fill relative to 0 dB baseline (boosts up, cuts down)
            val fillPath = Path().apply {
                addPath(strokePath)
                lineTo(points.last().x, baselineY)
                lineTo(points.first().x, baselineY)
                close()
            }

            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(actualFillColor, actualFillColor.copy(alpha = 0.04f), actualFillColor),
                    startY = padY,
                    endY = height - padY
                )
            )

            // Crisp Response Curve Line
            drawPath(
                path = strokePath,
                color = actualColor,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Minimal Band Node Indicators
            val dotRadius = 3.dp.toPx()
            points.forEach { point ->
                drawCircle(
                    color = colors.surfacePanel,
                    radius = dotRadius + 1.5f,
                    center = point
                )
                drawCircle(
                    color = actualColor,
                    radius = dotRadius,
                    center = point
                )
            }
        }
    }
}

@Composable
fun EqualizerWaveform(
    bands: List<Float>,
    valueRange: ClosedFloatingPointRange<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(100.dp),
    enabled: Boolean = true,
    color: Color = LocalVeylColors.current.accentSignal,
    fillColor: Color = color.copy(alpha = 0.22f),
    strokeWidth: Float = 5f,
    smoothingTension: Float = 0.24f
) {
    EqualizerWaveform(
        bandsProvider = { bands },
        valueRange = valueRange,
        modifier = modifier,
        enabled = enabled,
        color = color,
        fillColor = fillColor,
        strokeWidth = strokeWidth,
        smoothingTension = smoothingTension
    )
}
