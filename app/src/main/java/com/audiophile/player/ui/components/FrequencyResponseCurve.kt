package com.audiophile.player.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.ui.theme.BorderSubtle
import com.audiophile.player.ui.theme.CyberCyan
import com.audiophile.player.ui.theme.ObsidianBlack
import com.audiophile.player.ui.theme.SignalAmber
import com.audiophile.player.ui.theme.TextTertiary
import uniffi.audiophile_core.EqBandInfo
import uniffi.audiophile_core.FrequencyPoint
import kotlin.math.log10
import kotlin.math.pow

@Composable
fun FrequencyResponseCanvas(
    curvePoints: List<FrequencyPoint>,
    bands: List<EqBandInfo>,
    onBandDragged: (index: UInt, newGain: Float, newFreq: Float) -> Unit,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(180.dp)
) {
    val minFreq = 20f
    val maxFreq = 20000f
    val minDb = -15f
    val maxDb = +15f

    var activeDraggingBand by remember { mutableStateOf<Int?>(null) }

    Box(
        modifier = modifier
            .background(ObsidianBlack, RoundedCornerShape(8.dp))
            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(bands) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            // Find closest band node
                            val w = size.width.toFloat()
                            val h = size.height.toFloat()
                            var closestIdx: Int? = null
                            var minDistance = 40.dp.toPx()

                            bands.forEachIndexed { i, band ->
                                val logMin = log10(minFreq)
                                val logMax = log10(maxFreq)
                                val xNorm = (log10(band.frequency) - logMin) / (logMax - logMin)
                                val nodeX = xNorm * w
                                val nodeY = h * (1f - (band.gainDb - minDb) / (maxDb - minDb))

                                val dist = kotlin.math.hypot(offset.x - nodeX, offset.y - nodeY)
                                if (dist < minDistance) {
                                    minDistance = dist
                                    closestIdx = i
                                }
                            }
                            activeDraggingBand = closestIdx
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            activeDraggingBand?.let { idx ->
                                if (idx < bands.size) {
                                    val band = bands[idx]
                                    val w = size.width.toFloat()
                                    val h = size.height.toFloat()

                                    // Calculate new gain from Y
                                    val deltaGain = -(dragAmount.y / h) * (maxDb - minDb)
                                    val newGain = (band.gainDb + deltaGain).coerceIn(minDb, maxDb)

                                    // Calculate new frequency from X
                                    val logMin = log10(minFreq)
                                    val logMax = log10(maxFreq)
                                    val currentLog = log10(band.frequency)
                                    val deltaLog = (dragAmount.x / w) * (logMax - logMin)
                                    val newFreq = 10f.pow(currentLog + deltaLog).coerceIn(minFreq, maxFreq)

                                    onBandDragged(idx.toUInt(), newGain, newFreq)
                                }
                            }
                        },
                        onDragEnd = { activeDraggingBand = null },
                        onDragCancel = { activeDraggingBand = null }
                    )
                }
        ) {
            val w = size.width
            val h = size.height
            val logMin = log10(minFreq)
            val logMax = log10(maxFreq)

            // Helper mapping functions
            fun freqToX(f: Float): Float {
                val norm = (log10(f.coerceIn(minFreq, maxFreq)) - logMin) / (logMax - logMin)
                return norm * w
            }

            fun dbToY(db: Float): Float {
                val norm = (db.coerceIn(minDb, maxDb) - minDb) / (maxDb - minDb)
                return h * (1f - norm)
            }

            // 1. Draw Grid Lines (Frequency)
            val gridFreqs = listOf(50f, 100f, 250f, 500f, 1000f, 2500f, 5000f, 10000f, 20000f)
            gridFreqs.forEach { f ->
                val x = freqToX(f)
                drawLine(
                    color = Color(0xFF161B24),
                    start = Offset(x, 0f),
                    end = Offset(x, h),
                    strokeWidth = 1f
                )
            }

            // 2. Draw Grid Lines (dB)
            val gridDbs = listOf(-12f, -6f, 0f, 6f, 12f)
            gridDbs.forEach { db ->
                val y = dbToY(db)
                val isZero = db == 0f
                drawLine(
                    color = if (isZero) Color(0xFF2C3547) else Color(0xFF161B24),
                    start = Offset(0f, y),
                    end = Offset(w, y),
                    strokeWidth = if (isZero) 1.5f else 1f,
                    pathEffect = if (!isZero) PathEffect.dashPathEffect(floatArrayOf(4f, 4f)) else null
                )
            }

            // 3. Draw Continuous Frequency Response Curve
            if (curvePoints.isNotEmpty()) {
                val curvePath = Path()
                val fillPath = Path()

                fillPath.moveTo(0f, dbToY(0f))

                curvePoints.forEachIndexed { i, pt ->
                    val x = freqToX(pt.frequency)
                    val y = dbToY(pt.gainDb)
                    if (i == 0) {
                        curvePath.moveTo(x, y)
                        fillPath.lineTo(x, y)
                    } else {
                        curvePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                }

                fillPath.lineTo(w, dbToY(0f))
                fillPath.close()

                // Area Fill under curve
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(CyberCyan.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = h
                    )
                )

                // Glowing Response Stroke
                drawPath(
                    path = curvePath,
                    color = CyberCyan,
                    style = Stroke(width = 2.5.dp.toPx())
                )
            }

            // 4. Draw Interactive Band Nodes
            bands.forEachIndexed { i, band ->
                val nodeX = freqToX(band.frequency)
                val nodeY = dbToY(band.gainDb)
                val isSelected = activeDraggingBand == i

                // Outer Ring
                drawCircle(
                    color = if (isSelected) SignalAmber else CyberCyan,
                    radius = if (isSelected) 8.dp.toPx() else 5.dp.toPx(),
                    center = Offset(nodeX, nodeY),
                    style = Stroke(width = 2.dp.toPx())
                )
                // Center Core
                drawCircle(
                    color = if (isSelected) SignalAmber else ObsidianBlack,
                    radius = if (isSelected) 4.dp.toPx() else 2.dp.toPx(),
                    center = Offset(nodeX, nodeY)
                )
            }
        }

        // Overlay corner indicators
        Text(
            text = "+12 dB",
            color = TextTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.TopStart)
        )
        Text(
            text = "0 dB",
            color = TextTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.CenterStart)
        )
        Text(
            text = "-12 dB",
            color = TextTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomStart)
        )
        Text(
            text = "20 Hz - 20 kHz TRANSFER FUNCTION",
            color = TextTertiary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomEnd)
        )
    }
}
