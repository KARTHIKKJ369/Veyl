package com.audiophile.player.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.audiophile.player.ui.theme.CyberCyan
import com.audiophile.player.ui.theme.SignalAmber

@Composable
fun SpectrumVisualizer(
    spectrumProvider: () -> List<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
) {
    Canvas(modifier = modifier) {
        val spectrum = spectrumProvider()
        val bandsCount = if (spectrum.isNotEmpty()) spectrum.size else 16
        val width = size.width
        val height = size.height
        val barSpacing = 4.dp.toPx()
        val totalSpacing = barSpacing * (bandsCount - 1)
        val barWidth = (width - totalSpacing) / bandsCount

        val gradient = Brush.verticalGradient(
            colors = listOf(CyberCyan, SignalAmber),
            startY = 0f,
            endY = height
        )

        for (i in 0 until bandsCount) {
            val value = if (i < spectrum.size) spectrum[i].coerceIn(0f, 1f) else 0f
            val barHeight = (value * height).coerceAtLeast(2.dp.toPx())
            val x = i * (barWidth + barSpacing)
            val y = height - barHeight

            drawRoundRect(
                brush = gradient,
                topLeft = Offset(x, y),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        }
    }
}

@Composable
fun SpectrumVisualizer(
    spectrum: List<Float>,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .height(48.dp)
) {
    SpectrumVisualizer(
        spectrumProvider = { spectrum },
        modifier = modifier
    )
}
