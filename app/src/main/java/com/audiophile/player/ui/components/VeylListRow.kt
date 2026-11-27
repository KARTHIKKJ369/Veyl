package com.audiophile.player.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylIcons
import com.audiophile.player.ui.theme.VeylTypography

/**
 * Unified, zero-lag name-only list row used across all Veyl list screens.
 * Includes Emil-style tactile press scale, animated playing bars, hairline divider, and 44dp+ touch targets.
 */
@Composable
fun VeylListRow(
    title: String,
    subtitle: String? = null,
    isPlaying: Boolean = false,
    isAnimating: Boolean = isPlaying,
    durationSeconds: Double? = null,
    trailingText: String? = null,
    showDivider: Boolean = true,
    onClick: () -> Unit,
    onOptionsClick: (() -> Unit)? = null,
    leadingContent: (@Composable RowScope.() -> Unit)? = null,
    trailingContent: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1f,
        animationSpec = tween(durationMillis = 150, easing = VeylEaseOut),
        label = "rowPressScale"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isPlaying) colors.surfaceElevated.copy(alpha = 0.85f) else Color.Transparent)
                .clickable(
                    interactionSource = interactionSource,
                    indication = androidx.compose.material3.ripple(),
                    onClick = onClick
                )
                .padding(start = if (isPlaying) 12.dp else 16.dp, end = 8.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isPlaying) {
                VeylPlayingBars(
                    color = colors.accentSignal,
                    isAnimating = isAnimating,
                    modifier = Modifier.padding(end = 10.dp)
                )
            }

            if (leadingContent != null) {
                leadingContent()
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = VeylTypography.BodyMedium,
                    color = if (isPlaying) colors.accentSignal else colors.textPrimary,
                    fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = VeylTypography.BodySmall,
                        color = colors.textMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            val resolvedTrailing = when {
                !trailingText.isNullOrBlank() -> trailingText
                durationSeconds != null && durationSeconds.toInt() > 0 -> {
                    val totalSec = durationSeconds.toInt()
                    val m = totalSec / 60
                    val s = totalSec % 60
                    if (s < 10) "$m:0$s" else "$m:$s"
                }
                else -> null
            }

            if (resolvedTrailing != null) {
                Text(
                    text = resolvedTrailing,
                    style = VeylTypography.MonoSpec,
                    color = colors.textMuted,
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }

            if (trailingContent != null) {
                trailingContent()
            }

            if (onOptionsClick != null) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onOptionsClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = VeylIcons.MoreVert,
                        contentDescription = "Options",
                        tint = colors.textMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        if (showDivider) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = colors.borderHairline.copy(alpha = 0.25f)
            )
        }
    }
}
