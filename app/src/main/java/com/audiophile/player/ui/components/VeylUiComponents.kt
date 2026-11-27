package com.audiophile.player.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.audiophile.player.ui.theme.LocalVeylColors
import com.audiophile.player.ui.theme.VeylTypography
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

/**
 * Standard Emil Kowalski ease-out curve for crisp, snappy tactile micro-interactions.
 */
val VeylEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/**
 * Crafted circular tactile button following mobile-app-ui-design & emil-design-eng:
 * - 44dp accessible hit target with crisp inner-rim highlight and soft background contrast
 * - GPU-only 0.92x scale compression on press (160ms ease-out) + haptic tick
 */
@Composable
fun VeylActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    accentColor: Color? = null,
    sizeDp: Int = 40,
    iconSizeDp: Int = 18,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.90f else 1f,
        animationSpec = tween(durationMillis = 160, easing = VeylEaseOut),
        label = "actionBtnScale"
    )

    val iconTint = when {
        isPrimary -> Color.Black
        accentColor != null -> accentColor
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .size(44.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(bounded = false, radius = 22.dp),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isPrimary) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(colors.accentSignal),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = iconTint,
                    modifier = Modifier.size(iconSizeDp.dp)
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = iconTint,
                modifier = Modifier.size((iconSizeDp + 2).dp)
            )
        }
    }
}

/**
 * Lightweight 3-bar GPU-animated playing indicator for active list rows.
 * Freezes smoothly into resting bars when playback is paused.
 */
@Composable
fun VeylPlayingBars(
    color: Color,
    isAnimating: Boolean = true,
    modifier: Modifier = Modifier
) {
    val barScales = if (isAnimating) {
        val transition = rememberInfiniteTransition(label = "playingBars")
        val bar1 by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(380, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar1"
        )
        val bar2 by transition.animateFloat(
            initialValue = 1.0f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(460, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar2"
        )
        val bar3 by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 0.95f,
            animationSpec = infiniteRepeatable(
                animation = tween(320, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar3"
        )
        listOf(bar1, bar2, bar3)
    } else {
        listOf(0.35f, 0.65f, 0.45f)
    }

    Row(
        modifier = modifier.height(14.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        barScales.forEach { scale ->
            Box(
                modifier = Modifier
                    .width(2.5.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        scaleY = scale
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                    }
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
    }
}

/**
 * Unified top screen header for consistent spacing, typography, and action button sizing.
 */
@Composable
fun VeylScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = LocalVeylColors.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = VeylTypography.DisplayLarge,
                color = colors.textPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = VeylTypography.BodySmall,
                    color = colors.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = actions
        )
    }
}

/**
 * Standardized empty state notice across all library, playlist, and search screens.
 */
@Composable
fun VeylEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = colors.surfacePanel,
        border = androidx.compose.foundation.BorderStroke(1.dp, colors.borderHairline.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Column(
            modifier = Modifier.padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textMuted,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = VeylTypography.TitleMedium,
                fontWeight = FontWeight.Bold,
                color = colors.textPrimary,
                textAlign = TextAlign.Center
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = VeylTypography.BodySmall,
                    color = colors.textMuted,
                    textAlign = TextAlign.Center
                )
            }
            if (!actionLabel.isNullOrBlank() && onAction != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onAction,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accentSignal,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = actionLabel,
                        style = VeylTypography.BodySmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * 120 FPS Invisible-until-Slide Ribbon Alphabet Scroller.
 * - Completely invisible when idle so list content stays 100% uncluttered.
 * - Activates on vertical slide along the right edge (does not block MoreVert taps).
 * - Transparent background blending seamlessly into the screen, with crisp 13sp->22sp letters.
 */
@Composable
fun VeylAlphabetFastScroller(
    items: List<String>,
    listState: LazyListState,
    headerItemCount: Int = 0,
    minItems: Int = 10,
    modifier: Modifier = Modifier
) {
    val colors = LocalVeylColors.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val alphabet = remember { ('A'..'Z').toList() }

    val letterToIndex = remember(items, headerItemCount) {
        val map = mutableMapOf<Char, Int>()
        items.forEachIndexed { idx, name ->
            val first = name.trimStart().firstOrNull()?.uppercaseChar()
            if (first != null && first in 'A'..'Z' && !map.containsKey(first)) {
                map[first] = idx + headerItemCount
            }
        }
        map
    }

    if (items.size < minItems) return

    // Continuous fractional letter index (0f..25f) for pixel-locked 120fps tracking
    var fingerIndex by remember { mutableFloatStateOf(-1f) }
    var isDragging by remember { mutableStateOf(false) }
    var railHeightPx by remember { mutableIntStateOf(1) }

    val selectedIndex = if (isDragging && fingerIndex >= 0f) {
        fingerIndex.roundToInt().coerceIn(0, alphabet.lastIndex)
    } else -1
    val selectedLetter = if (selectedIndex >= 0) alphabet[selectedIndex] else null

    // Single lightweight spring driving ribbon stretch & visibility (0f = invisible, 1f = visible & stretched)
    val stretchProgress by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ribbonStretch"
    )

    // Decoupled list scrolling + haptic on letter change so pointer tracking stays 120fps
    LaunchedEffect(selectedLetter) {
        val letter = selectedLetter ?: return@LaunchedEffect
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        val exact = letterToIndex[letter]
        if (exact != null) {
            listState.scrollToItem(exact)
        } else {
            val startIdx = alphabet.indexOf(letter)
            var fallback: Int? = null
            for (delta in 1..25) {
                val next = alphabet.getOrNull(startIdx + delta)?.let { letterToIndex[it] }
                if (next != null) {
                    fallback = next
                    break
                }
                val prev = alphabet.getOrNull(startIdx - delta)?.let { letterToIndex[it] }
                if (prev != null) {
                    fallback = prev
                    break
                }
            }
            if (fallback != null) {
                listState.scrollToItem(fallback)
            }
        }
    }

    val itemBoxSizePx = with(density) { 24.dp.toPx() }
    val usableHeightPx = (railHeightPx - itemBoxSizePx).coerceAtLeast(1f)
    val maxStretchPx = with(density) { 64.dp.toPx() }
    val selectedExtraPullPx = with(density) { 22.dp.toPx() }
    val baseRestCurvePx = with(density) { 2.dp.toPx() }

    Box(
        modifier = modifier
            .padding(end = 6.dp, top = 12.dp, bottom = 96.dp)
            .width(34.dp)
            .height(456.dp)
            .onSizeChanged { railHeightPx = it.height.coerceAtLeast(1) }
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val startY = down.position.y
                    var activated = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val pointer = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!pointer.pressed) {
                            if (activated) pointer.consume()
                            break
                        }
                        if (!activated) {
                            if (abs(pointer.position.y - startY) > 6f) {
                                activated = true
                                isDragging = true
                                pointer.consume()
                                fingerIndex = ((pointer.position.y / size.height.toFloat()).coerceIn(0f, 1f)) * (alphabet.size - 1)
                            }
                        } else {
                            pointer.consume()
                            fingerIndex = ((pointer.position.y / size.height.toFloat()).coerceIn(0f, 1f)) * (alphabet.size - 1)
                        }
                    }

                    isDragging = false
                }
            }
    ) {
        // Only draw letters while visible/fading out (transparent blend, zero pill container)
        if (stretchProgress > 0.01f) {
            val totalLetters = alphabet.size

            // Subtle transparent right-edge blend gradient (no boxy pill, blends cleanly with background)
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(96.dp)
                    .fillMaxHeight()
                    .graphicsLayer { alpha = stretchProgress * 0.72f }
                    .background(
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                colors.background.copy(alpha = 0.85f)
                            )
                        )
                    )
            )

            alphabet.forEachIndexed { index, letter ->
                val hasItems = letterToIndex.containsKey(letter)
                val isSelected = selectedIndex == index
                val progress = index.toFloat() / (totalLetters - 1)
                val baseY = progress * usableHeightPx
                val normalizedProgress = (progress - 0.5f) * 2f // -1f..1f

                val dist = if (fingerIndex >= 0f) abs(index.toFloat() - fingerIndex) else Float.MAX_VALUE
                val influence = if (stretchProgress > 0.001f && dist <= 7.5f) {
                    val normDist = 1f - (dist / 7.5f)
                    sin(normDist * (PI / 2.0)).toFloat().pow(1.25f) * stretchProgress
                } else {
                    0f
                }

                val curveX = -((1f - abs(normalizedProgress)) * baseRestCurvePx +
                        influence * maxStretchPx +
                        (if (isSelected) selectedExtraPullPx * stretchProgress else 0f))

                val letterScale = when {
                    isSelected -> 1f + (0.85f * stretchProgress)
                    dist <= 4f -> 1f + (maxOf(0f, 1f - dist / 4f) * 0.45f * stretchProgress)
                    else -> 1f
                }

                val letterRotation = if (dist <= 4f) {
                    val rotFactor = maxOf(0f, 1f - dist / 4f)
                    rotFactor * 12f * sign(index.toFloat() - fingerIndex) * stretchProgress
                } else {
                    0f
                }

                val letterColor = when {
                    isSelected -> Color.Black
                    dist <= 2.2f -> colors.accentSignal
                    else -> colors.textPrimary
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .graphicsLayer {
                            translationX = curveX
                            translationY = baseY
                            scaleX = letterScale
                            scaleY = letterScale
                            rotationZ = letterRotation
                            alpha = stretchProgress * when {
                                isSelected -> 1f
                                dist <= 4f -> 0.95f
                                hasItems -> 0.88f
                                else -> 0.55f
                            }
                        }
                        .clip(CircleShape)
                        .background(
                            color = if (isSelected) colors.accentSignal else Color.Transparent,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = letter.toString(),
                        style = VeylTypography.MonoSpec,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected || dist <= 1.5f) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = letterColor
                    )
                }
            }
        }
    }
}
