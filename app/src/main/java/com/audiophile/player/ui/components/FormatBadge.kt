package com.audiophile.player.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.ui.theme.CyberCyan
import com.audiophile.player.ui.theme.SignalAmber
import com.audiophile.player.ui.theme.SpectralGreen
import com.audiophile.player.ui.theme.SurfaceElevated

@Composable
fun TacticalBadge(
    text: String,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(SurfaceElevated, RoundedCornerShape(4.dp))
            .border(1.dp, accentColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = accentColor,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun FormatBadgeCluster(
    formatName: String,
    sampleRate: UInt,
    bitDepth: UInt?,
    isBitPerfect: Boolean,
    isMmapExclusive: Boolean,
    modifier: Modifier = Modifier
) {
    val rateKhz = sampleRate.toFloat() / 1000f
    val rateStr = if (rateKhz % 1f == 0f) "${rateKhz.toInt()} kHz" else "%.1f kHz".format(rateKhz)
    val depthStr = bitDepth?.let { "$it-BIT" } ?: "32-BIT FP"

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TacticalBadge(
            text = formatName.uppercase(),
            accentColor = SignalAmber
        )
        TacticalBadge(
            text = rateStr,
            accentColor = CyberCyan
        )
        TacticalBadge(
            text = depthStr,
            accentColor = CyberCyan
        )
        if (isBitPerfect) {
            TacticalBadge(
                text = "BIT-PERFECT",
                accentColor = SpectralGreen
            )
        }
        TacticalBadge(
            text = if (isMmapExclusive) "AAUDIO MMAP" else "AAUDIO SHARED",
            accentColor = if (isMmapExclusive) SpectralGreen else SignalAmber
        )
    }
}
