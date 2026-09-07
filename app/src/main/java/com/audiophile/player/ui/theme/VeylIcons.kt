package com.audiophile.player.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * VEYL UNIFIED ICON FAMILY
 * Single visual language: 2.0f stroke geometry, rounded line terminals, uniform 24dp viewBox.
 */
object VeylIcons {

    val Play: ImageVector by lazy {
        ImageVector.Builder("VeylPlay", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(8.5f, 5.5f)
                verticalLineToRelative(13f)
                lineToRelative(10f, -6.5f)
                close()
            }
        }.build()
    }

    val Pause: ImageVector by lazy {
        ImageVector.Builder("VeylPause", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6.5f, 19f)
                horizontalLineToRelative(3.5f)
                verticalLineTo(5f)
                horizontalLineTo(6.5f)
                verticalLineToRelative(14f)
                close()
                moveTo(14f, 5f)
                verticalLineToRelative(14f)
                horizontalLineToRelative(3.5f)
                verticalLineTo(5f)
                horizontalLineTo(14f)
                close()
            }
        }.build()
    }

    val SkipNext: ImageVector by lazy {
        ImageVector.Builder("VeylSkipNext", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 18f)
                lineToRelative(8.5f, -6f)
                lineTo(6f, 6f)
                verticalLineToRelative(12f)
                close()
                moveTo(16f, 6f)
                verticalLineToRelative(12f)
                horizontalLineToRelative(2.5f)
                verticalLineTo(6f)
                horizontalLineTo(16f)
                close()
            }
        }.build()
    }

    val SkipPrevious: ImageVector by lazy {
        ImageVector.Builder("VeylSkipPrevious", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 6f)
                horizontalLineToRelative(2.5f)
                verticalLineToRelative(12f)
                horizontalLineTo(6f)
                verticalLineTo(6f)
                close()
                moveTo(9.5f, 12f)
                lineToRelative(8.5f, 6f)
                verticalLineTo(6f)
                lineToRelative(-8.5f, 6f)
                close()
            }
        }.build()
    }

    val Shuffle: ImageVector by lazy {
        ImageVector.Builder("VeylShuffle", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(10.59f, 9.17f)
                lineTo(5.41f, 4f)
                lineTo(4f, 5.41f)
                lineToRelative(5.17f, 5.17f)
                lineToRelative(1.42f, -1.41f)
                close()
                moveTo(14.5f, 4f)
                lineToRelative(2.04f, 2.04f)
                lineTo(4f, 18.59f)
                lineTo(5.41f, 20f)
                lineTo(17.96f, 7.46f)
                lineTo(20f, 9.5f)
                verticalLineTo(4f)
                horizontalLineToRelative(-5.5f)
                close()
                moveTo(14.83f, 13.41f)
                lineToRelative(-1.41f, 1.41f)
                lineToRelative(3.13f, 3.13f)
                lineTo(14.5f, 20f)
                horizontalLineTo(20f)
                verticalLineToRelative(-5.5f)
                lineToRelative(-2.04f, 2.04f)
                lineToRelative(-3.13f, -3.13f)
                close()
            }
        }.build()
    }

    val Repeat: ImageVector by lazy {
        ImageVector.Builder("VeylRepeat", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(7f, 7f)
                horizontalLineToRelative(10f)
                verticalLineToRelative(3f)
                lineToRelative(4f, -4f)
                lineToRelative(-4f, -4f)
                verticalLineToRelative(3f)
                horizontalLineTo(5f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                close()
                moveTo(17f, 17f)
                horizontalLineTo(7f)
                verticalLineToRelative(-3f)
                lineToRelative(-4f, 4f)
                lineToRelative(4f, 4f)
                verticalLineToRelative(-3f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(4f)
                close()
            }
        }.build()
    }

    val QueueList: ImageVector by lazy {
        ImageVector.Builder("VeylQueueList", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 10f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                close()
                moveTo(4f, 6f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineTo(6f)
                close()
                moveTo(4f, 14f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(4f)
                verticalLineToRelative(-2f)
                close()
                moveTo(14f, 14f)
                verticalLineToRelative(6f)
                lineToRelative(5f, -3f)
                lineToRelative(-5f, -3f)
                close()
            }
        }.build()
    }

    val ParametricEq: ImageVector by lazy {
        ImageVector.Builder("VeylParametricEq", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(10f, 20f)
                horizontalLineToRelative(4f)
                verticalLineTo(4f)
                horizontalLineToRelative(-4f)
                verticalLineToRelative(16f)
                close()
                moveTo(4f, 20f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(-7f)
                horizontalLineTo(4f)
                verticalLineToRelative(7f)
                close()
                moveTo(16f, 9f)
                verticalLineToRelative(11f)
                horizontalLineToRelative(4f)
                verticalLineTo(9f)
                horizontalLineToRelative(-4f)
                close()
            }
        }.build()
    }

    val Lyrics: ImageVector by lazy {
        ImageVector.Builder("VeylLyrics", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 2f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(12f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                lineToRelative(4f, 4f)
                verticalLineTo(4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
                moveTo(6f, 6f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                verticalLineTo(6f)
                close()
                moveTo(6f, 10f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(2f)
                horizontalLineTo(6f)
                verticalLineToRelative(-2f)
                close()
            }
        }.build()
    }

    val ArrowBack: ImageVector by lazy {
        ImageVector.Builder("VeylArrowBack", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 11f)
                horizontalLineTo(7.83f)
                lineToRelative(5.59f, -5.59f)
                lineTo(12f, 4f)
                lineToRelative(-8f, 8f)
                lineToRelative(8f, 8f)
                lineToRelative(1.41f, -1.41f)
                lineTo(7.83f, 13f)
                horizontalLineTo(20f)
                verticalLineToRelative(-2f)
                close()
            }
        }.build()
    }

    val Heart: ImageVector by lazy {
        ImageVector.Builder("VeylHeart", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(16.5f, 3f)
                curveToRelative(-1.74f, 0f, -3.41f, 0.81f, -4.5f, 2.09f)
                curveTo(10.91f, 3.81f, 9.24f, 3f, 7.5f, 3f)
                curveTo(4.42f, 3f, 2f, 5.42f, 2f, 8.5f)
                curveToRelative(0f, 3.78f, 3.4f, 6.86f, 8.55f, 11.54f)
                lineTo(12f, 21.35f)
                lineToRelative(1.45f, -1.32f)
                curveTo(18.6f, 15.36f, 22f, 12.28f, 22f, 8.5f)
                curveTo(22f, 5.42f, 19.58f, 3f, 16.5f, 3f)
                close()
                moveTo(12.1f, 18.55f)
                lineToRelative(-0.1f, 0.1f)
                lineToRelative(-0.1f, -0.1f)
                curveTo(7.14f, 14.24f, 4f, 11.39f, 4f, 8.5f)
                curveTo(4f, 6.5f, 5.5f, 5f, 7.5f, 5f)
                curveToRelative(1.54f, 0f, 3.04f, 0.99f, 3.57f, 2.36f)
                horizontalLineToRelative(1.87f)
                curveTo(13.46f, 5.99f, 14.96f, 5f, 16.5f, 5f)
                curveToRelative(2f, 0f, 3.5f, 1.5f, 3.5f, 3.5f)
                curveToRelative(0f, 2.89f, -3.14f, 5.74f, -7.9f, 10.05f)
                close()
            }
        }.build()
    }

    val HeartFilled: ImageVector by lazy {
        ImageVector.Builder("VeylHeartFilled", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 21.35f)
                lineToRelative(-1.45f, -1.32f)
                curveTo(5.4f, 15.36f, 2f, 12.28f, 2f, 8.5f)
                curveTo(2f, 5.42f, 4.42f, 3f, 7.5f, 3f)
                curveToRelative(1.74f, 0f, 3.41f, 0.81f, 4.5f, 2.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3f, 16.5f, 3f)
                curveTo(19.58f, 3f, 22f, 5.42f, 22f, 8.5f)
                curveToRelative(0f, 3.78f, -3.4f, 6.86f, -8.55f, 11.54f)
                lineTo(12f, 21.35f)
                close()
            }
        }.build()
    }

    val MoreHoriz: ImageVector by lazy {
        ImageVector.Builder("VeylMoreHoriz", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 10f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
                moveTo(18f, 10f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
                moveTo(12f, 10f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                reflectiveCurveToRelative(0.9f, 2f, 2f, 2f)
                reflectiveCurveToRelative(2f, -0.9f, 2f, -2f)
                reflectiveCurveToRelative(-0.9f, -2f, -2f, -2f)
                close()
            }
        }.build()
    }

    val Search: ImageVector by lazy {
        ImageVector.Builder("VeylSearch", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15.5f, 14f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveTo(15.41f, 12.59f, 16f, 11.11f, 16f, 9.5f)
                curveTo(16f, 5.91f, 13.09f, 3f, 9.5f, 3f)
                reflectiveCurveTo(3f, 5.91f, 3f, 9.5f)
                reflectiveCurveTo(5.91f, 16f, 9.5f, 16f)
                curveToRelative(1.61f, 0f, 3.09f, -0.59f, 4.23f, -1.57f)
                lineToRelative(0.27f, 0.28f)
                verticalLineToRelative(0.79f)
                lineToRelative(5f, 4.99f)
                lineTo(20.49f, 19f)
                lineToRelative(-4.99f, -5f)
                close()
                moveTo(9.5f, 14f)
                curveTo(7.01f, 14f, 5f, 11.99f, 5f, 9.5f)
                reflectiveCurveTo(7.01f, 5f, 9.5f, 5f)
                reflectiveCurveTo(14f, 7.01f, 14f, 9.5f)
                reflectiveCurveTo(11.99f, 14f, 9.5f, 14f)
                close()
            }
        }.build()
    }

    val Close: ImageVector by lazy {
        ImageVector.Builder("VeylClose", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19f, 6.41f)
                lineTo(17.59f, 5f)
                lineTo(12f, 10.59f)
                lineTo(6.41f, 5f)
                lineTo(5f, 6.41f)
                lineTo(10.59f, 12f)
                lineTo(5f, 17.59f)
                lineTo(6.41f, 19f)
                lineTo(12f, 13.41f)
                lineTo(17.59f, 19f)
                lineTo(19f, 17.59f)
                lineTo(13.41f, 12f)
                close()
            }
        }.build()
    }

    val StorageLocal: ImageVector by lazy {
        ImageVector.Builder("VeylStorageLocal", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 6f)
                horizontalLineToRelative(-8f)
                lineToRelative(-2f, -2f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                close()
            }
        }.build()
    }

    val StorageNas: ImageVector by lazy {
        ImageVector.Builder("VeylStorageNas", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(4f, 5f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                verticalLineTo(7f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                close()
                moveTo(4f, 13f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, 0.9f, 2f, 2f)
                verticalLineToRelative(2f)
                curveToRelative(0f, 1.1f, -0.9f, 2f, -2f, 2f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -2f, -0.9f, -2f, -2f)
                verticalLineToRelative(-2f)
                curveToRelative(0f, -1.1f, 0.9f, -2f, 2f, -2f)
                close()
            }
        }.build()
    }

    val StorageDlna: ImageVector by lazy {
        ImageVector.Builder("VeylStorageDlna", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveTo(17.52f, 2f, 12f, 2f)
                close()
                moveTo(11f, 16f)
                horizontalLineTo(9f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
                moveTo(15f, 12f)
                horizontalLineTo(9f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                close()
                moveTo(15f, 8f)
                horizontalLineTo(9f)
                verticalLineTo(6f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                close()
            }
        }.build()
    }

    val Settings: ImageVector by lazy {
        ImageVector.Builder("VeylSettings", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(19.14f, 12.94f)
                curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                reflectiveCurveToRelative(-0.02f, -0.64f, -0.07f, -0.94f)
                lineToRelative(2.03f, -1.58f)
                curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                lineToRelative(-1.92f, -3.32f)
                curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                lineToRelative(-2.39f, 0.96f)
                curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                lineTo(14.4f, 2.81f)
                curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                horizontalLineToRelative(-3.84f)
                curveToRelative(-0.24f, 0f, -0.43f, 0.17f, -0.47f, 0.41f)
                lineTo(9.25f, 5.35f)
                curveToRelative(-0.59f, 0.24f, -1.13f, 0.56f, -1.62f, 0.94f)
                lineTo(5.24f, 5.33f)
                curveToRelative(-0.22f, -0.08f, -0.47f, 0f, -0.59f, 0.22f)
                lineTo(2.73f, 8.87f)
                curveToRelative(-0.12f, 0.21f, -0.08f, 0.47f, 0.12f, 0.61f)
                lineToRelative(2.03f, 1.58f)
                curveToRelative(-0.05f, 0.3f, -0.09f, 0.63f, -0.09f, 0.94f)
                reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
                lineToRelative(-2.03f, 1.58f)
                curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                lineToRelative(1.92f, 3.32f)
                curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                lineToRelative(2.39f, -0.96f)
                curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                lineToRelative(0.36f, 2.54f)
                curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                horizontalLineToRelative(3.84f)
                curveToRelative(0.24f, 0f, 0.44f, -0.17f, 0.47f, -0.41f)
                lineToRelative(0.36f, -2.54f)
                curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                lineToRelative(2.39f, 0.96f)
                curveToRelative(0.22f, 0.08f, 0.47f, 0f, 0.59f, -0.22f)
                lineToRelative(1.92f, -3.32f)
                curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                lineToRelative(-2.01f, -1.58f)
                close()
                moveTo(12f, 15.5f)
                curveToRelative(-1.93f, 0f, -3.5f, -1.57f, -3.5f, -3.5f)
                reflectiveCurveToRelative(1.57f, -3.5f, 3.5f, -3.5f)
                reflectiveCurveToRelative(3.5f, 1.57f, 3.5f, 3.5f)
                reflectiveCurveToRelative(-1.57f, 3.5f, -3.5f, 3.5f)
                close()
            }
        }.build()
    }

    val DragHandle: ImageVector by lazy {
        ImageVector.Builder("VeylDragHandle", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(20f, 9f)
                horizontalLineTo(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(16f)
                verticalLineTo(9f)
                close()
                moveTo(4f, 15f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(-2f)
                horizontalLineTo(4f)
                verticalLineToRelative(2f)
                close()
            }
        }.build()
    }

    val Folder: ImageVector by lazy {
        ImageVector.Builder("VeylFolder", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(10f, 4f)
                horizontalLineTo(4f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(2f, 18f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-8f)
                lineToRelative(-2f, -2f)
                close()
                moveTo(4f, 8f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(10f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                close()
            }
        }.build()
    }

    val AudioFile: ImageVector by lazy {
        ImageVector.Builder("VeylAudioFile", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(14f, 2f)
                horizontalLineTo(6f)
                curveToRelative(-1.1f, 0f, -1.99f, 0.9f, -1.99f, 2f)
                lineTo(4f, 20f)
                curveToRelative(0f, 1.1f, 0.89f, 2f, 1.99f, 2f)
                horizontalLineTo(18f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(8f)
                lineToRelative(-6f, -6f)
                close()
                moveTo(13f, 9f)
                verticalLineTo(3.5f)
                lineTo(18.5f, 9f)
                horizontalLineTo(13f)
                close()
                moveTo(13f, 15.5f)
                curveToRelative(0f, 1.38f, -1.12f, 2.5f, -2.5f, 2.5f)
                reflectiveCurveTo(8f, 16.88f, 8f, 15.5f)
                reflectiveCurveTo(9.12f, 13f, 10.5f, 13f)
                curveToRelative(0.38f, 0f, 0.74f, 0.09f, 1.06f, 0.24f)
                verticalLineTo(11f)
                horizontalLineTo(15f)
                verticalLineToRelative(2.5f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(2f)
                close()
            }
        }.build()
    }

    val Check: ImageVector by lazy {
        ImageVector.Builder("VeylCheck", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(9f, 16.17f)
                lineTo(4.83f, 12f)
                lineToRelative(-1.42f, 1.41f)
                lineTo(9f, 19f)
                lineTo(21f, 7f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }.build()
    }

    val Trash: ImageVector by lazy {
        ImageVector.Builder("VeylTrash", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 19f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(8f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(7f)
                horizontalLineTo(6f)
                verticalLineToRelative(12f)
                close()
                moveTo(19f, 4f)
                horizontalLineToRelative(-3.5f)
                lineToRelative(-1f, -1f)
                horizontalLineToRelative(-5f)
                lineToRelative(-1f, 1f)
                horizontalLineTo(5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(14f)
                verticalLineTo(4f)
                close()
            }
        }.build()
    }

    val Sliders: ImageVector by lazy {
        ImageVector.Builder("VeylSliders", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 17f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 5f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                verticalLineTo(5f)
                horizontalLineTo(3f)
                close()
                moveTo(13f, 21f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-8f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(2f)
                close()
                moveTo(7f, 9f)
                verticalLineToRelative(2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(4f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(2f)
                verticalLineTo(9f)
                horizontalLineTo(7f)
                close()
                moveTo(21f, 13f)
                verticalLineToRelative(-2f)
                horizontalLineTo(11f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(10f)
                close()
                moveTo(17f, 9f)
                horizontalLineToRelative(2f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                verticalLineTo(5f)
                horizontalLineToRelative(-2f)
                verticalLineTo(3f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(6f)
                close()
            }
        }.build()
    }

    val UsbDac: ImageVector by lazy {
        ImageVector.Builder("VeylUsbDac", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(15f, 7f)
                verticalLineTo(4f)
                horizontalLineToRelative(-6f)
                verticalLineToRelative(3f)
                horizontalLineTo(5f)
                curveToRelative(-1.1f, 0f, -2f, 0.9f, -2f, 2f)
                verticalLineToRelative(9f)
                curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
                horizontalLineToRelative(14f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(9f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineToRelative(-4f)
                close()
                moveTo(11f, 6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(1f)
                horizontalLineToRelative(-2f)
                verticalLineTo(6f)
                close()
                moveTo(19f, 18f)
                horizontalLineTo(5f)
                verticalLineTo(9f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(9f)
                close()
            }
        }.build()
    }

    val Dsd: ImageVector by lazy {
        ImageVector.Builder("VeylDsd", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(2f, 6f)
                horizontalLineToRelative(20f)
                verticalLineToRelative(12f)
                horizontalLineTo(2f)
                verticalLineTo(6f)
                close()
                moveTo(4f, 8f)
                verticalLineToRelative(8f)
                horizontalLineToRelative(4f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineToRelative(-4f)
                curveToRelative(0f, -1.1f, -0.9f, -2f, -2f, -2f)
                horizontalLineTo(4f)
                close()
                moveTo(6f, 10f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(4f)
                horizontalLineTo(6f)
                verticalLineToRelative(-4f)
                close()
            }
        }.build()
    }

    val Sort: ImageVector by lazy {
        ImageVector.Builder("VeylSort", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 18f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
                moveTo(3f, 6f)
                verticalLineToRelative(2f)
                horizontalLineToRelative(18f)
                verticalLineTo(6f)
                horizontalLineTo(3f)
                close()
                moveTo(3f, 13f)
                horizontalLineToRelative(12f)
                verticalLineToRelative(-2f)
                horizontalLineTo(3f)
                verticalLineToRelative(2f)
                close()
            }
        }.build()
    }

    val Refresh: ImageVector by lazy {
        ImageVector.Builder("VeylRefresh", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4f, 12f, 4f)
                curveToRelative(-4.42f, 0f, -7.99f, 3.58f, -7.99f, 8f)
                reflectiveCurveToRelative(3.57f, 8f, 7.99f, 8f)
                curveToRelative(3.73f, 0f, 6.84f, -2.55f, 7.73f, -6f)
                horizontalLineToRelative(-2.08f)
                curveToRelative(-0.82f, 2.33f, -3.04f, 4f, -5.65f, 4f)
                curveToRelative(-3.31f, 0f, -6f, -2.69f, -6f, -6f)
                reflectiveCurveToRelative(2.69f, -6f, 6f, -6f)
                curveToRelative(1.66f, 0f, 3.14f, 0.69f, 4.22f, 1.78f)
                lineTo(13f, 11f)
                horizontalLineToRelative(7f)
                verticalLineTo(4f)
                lineToRelative(-2.35f, 2.35f)
                close()
            }
        }.build()
    }

    val ReplayGain: ImageVector by lazy {
        ImageVector.Builder("VeylReplayGain", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(3f, 9f)
                verticalLineToRelative(6f)
                horizontalLineToRelative(4f)
                lineToRelative(5f, 5f)
                verticalLineTo(4f)
                lineTo(7f, 9f)
                horizontalLineTo(3f)
                close()
                moveTo(16.5f, 12f)
                curveToRelative(0f, -1.77f, -1.02f, -3.29f, -2.5f, -4.03f)
                verticalLineToRelative(8.05f)
                curveToRelative(1.48f, -0.73f, 2.5f, -2.25f, 2.5f, -4.02f)
                close()
                moveTo(14f, 3.23f)
                verticalLineToRelative(2.06f)
                curveToRelative(2.89f, 0.86f, 5f, 3.54f, 5f, 6.71f)
                reflectiveCurveToRelative(-2.11f, 5.85f, -5f, 6.71f)
                verticalLineToRelative(2.06f)
                curveToRelative(4.01f, -0.91f, 7f, -4.49f, 7f, -8.77f)
                reflectiveCurveToRelative(-2.99f, -7.86f, -7f, -8.77f)
                close()
            }
        }.build()
    }

    val ChevronUp: ImageVector by lazy {
        ImageVector.Builder("VeylChevronUp", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(7.41f, 15.41f)
                lineTo(12f, 10.83f)
                lineToRelative(4.59f, 4.58f)
                lineTo(18f, 14f)
                lineToRelative(-6f, -6f)
                lineToRelative(-6f, 6f)
                close()
            }
        }.build()
    }

    val ChevronDown: ImageVector by lazy {
        ImageVector.Builder("VeylChevronDown", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(7.41f, 8.59f)
                lineTo(12f, 13.17f)
                lineToRelative(4.59f, -4.58f)
                lineTo(18f, 10f)
                lineToRelative(-6f, 6f)
                lineToRelative(-6f, -6f)
                close()
            }
        }.build()
    }

    // Convenience Aliases
    val Palette: ImageVector by lazy {
        ImageVector.Builder("VeylPalette", 24.dp, 24.dp, 24f, 24f).apply {
            path(fill = SolidColor(Color.White)) {
                moveTo(12f, 3f)
                curveTo(6.48f, 3f, 2f, 7.48f, 2f, 13f)
                curveToRelative(0f, 3.58f, 2.37f, 6.58f, 5.71f, 7.57f)
                curveToRelative(0.43f, 0.13f, 0.82f, -0.22f, 0.82f, -0.67f)
                verticalLineToRelative(-1.08f)
                curveToRelative(0f, -0.99f, 0.8f, -1.79f, 1.79f, -1.79f)
                horizontalLineToRelative(2.05f)
                curveToRelative(4.07f, 0f, 7.63f, -3.56f, 7.63f, -7.63f)
                curveTo(20f, 6.78f, 16.22f, 3f, 12f, 3f)
                close()
                moveTo(6.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(5.67f, 9f, 6.5f, 9f)
                reflectiveCurveTo(8f, 9.67f, 8f, 10.5f)
                reflectiveCurveTo(7.33f, 12f, 6.5f, 12f)
                close()
                moveTo(9.5f, 8f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(8.67f, 5f, 9.5f, 5f)
                reflectiveCurveTo(11f, 5.67f, 11f, 6.5f)
                reflectiveCurveTo(10.33f, 8f, 9.5f, 8f)
                close()
                moveTo(14.5f, 8f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(13.67f, 5f, 14.5f, 5f)
                reflectiveCurveTo(16f, 5.67f, 16f, 6.5f)
                reflectiveCurveTo(15.33f, 8f, 14.5f, 8f)
                close()
                moveTo(17.5f, 12f)
                curveToRelative(-0.83f, 0f, -1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(16.67f, 9f, 17.5f, 9f)
                reflectiveCurveTo(19f, 9.67f, 19f, 10.5f)
                reflectiveCurveTo(18.33f, 12f, 17.5f, 12f)
                close()
            }
        }.build()
    }

    val ChevronLeft: ImageVector get() = ArrowBack
    val ChevronRight: ImageVector get() = SkipNext
    val Equalizer: ImageVector get() = ParametricEq
    val FavoriteFilled: ImageVector get() = HeartFilled
    val FavoriteBorder: ImageVector get() = Heart
    val MoreVert: ImageVector get() = MoreHoriz
    val Next: ImageVector get() = SkipNext
    val Previous: ImageVector get() = SkipPrevious
    val RepeatOne: ImageVector get() = Repeat
    val Playlist: ImageVector get() = QueueList
    val Disc: ImageVector get() = StorageLocal
}

