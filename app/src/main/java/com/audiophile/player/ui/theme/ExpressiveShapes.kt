package com.audiophile.player.ui.theme

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Material 3 Expressive Scalloped / Petal Cookie Shape
 * Used for the signature Veyl Play/Pause button, action FABs, and hero badges.
 */
class ScallopedShape(
    private val lobes: Int = 9,
    private val amplitude: Float = 0.08f,
    private val rotationDegrees: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val baseRadius = minOf(centerX, centerY) * (1f - amplitude * 0.5f)
        val steps = 360
        val angleStep = (2 * PI / steps).toFloat()
        val rotRad = (rotationDegrees * PI / 180f).toFloat()

        for (i in 0..steps) {
            val angle = i * angleStep
            val r = baseRadius * (1f + amplitude * cos(lobes * angle))
            val effectiveAngle = angle + rotRad
            val x = centerX + r * cos(effectiveAngle)
            val y = centerY + r * sin(effectiveAngle)
            if (i == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        path.close()
        return Outline.Generic(path)
    }
}

fun ExpressiveCookieShape(lobes: Int = 9, amplitude: Float = 0.09f): ScallopedShape =
    ScallopedShape(lobes = lobes, amplitude = amplitude)

val ExpressiveCookieShape = ScallopedShape(lobes = 9, amplitude = 0.09f)
val ExpressiveFlowerShape = ScallopedShape(lobes = 12, amplitude = 0.07f)
val ExpressiveStar8Shape = ScallopedShape(lobes = 8, amplitude = 0.12f)
