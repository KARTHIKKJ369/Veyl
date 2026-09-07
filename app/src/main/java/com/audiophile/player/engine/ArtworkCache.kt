package com.audiophile.player.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.audiophile.player.ui.theme.VeylIcons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import uniffi.audiophile_core.LibraryEngine
import java.io.File
import java.util.concurrent.ConcurrentHashMap

object ArtworkCache {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = maxMemory / 6 // 1/6th of available memory for album art cache

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // Cache parent folder artwork lookups to prevent disk scanning per track
    private val folderArtCache = ConcurrentHashMap<String, String>()

    private val libraryEngine = LibraryEngine()

    fun getArtwork(uri: String, targetSizePx: Int = 128): Bitmap? {
        val cacheKey = if (targetSizePx > 256) "${uri}_lg" else "${uri}_sm"
        return memoryCache.get(cacheKey)
    }

    suspend fun loadArtwork(uri: String, targetSizePx: Int = 128): Bitmap? = withContext(Dispatchers.IO) {
        if (uri.isBlank()) return@withContext null
        val cacheKey = if (targetSizePx > 256) "${uri}_lg" else "${uri}_sm"
        val cached = memoryCache.get(cacheKey)
        if (cached != null) return@withContext cached

        var bitmap: Bitmap? = null

        // 1. Try Rust native LibraryEngine (handles DSF, DFF, FLAC, Lofty tags)
        try {
            val artData = libraryEngine.extractArtwork(uri)
            if (artData != null && artData.data.isNotEmpty()) {
                val bytes = artData.data
                val options = BitmapFactory.Options().apply {
                    inJustDecodeBounds = true
                }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (e: Throwable) {
            // Native decode fallback
        }

        // 2. Fallback to Android MediaMetadataRetriever
        if (bitmap == null && File(uri).exists()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(uri)
                val rawBytes = retriever.embeddedPicture
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                    options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                    options.inJustDecodeBounds = false
                    bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                }
            } catch (e: Throwable) {
                // Ignore
            } finally {
                try {
                    retriever.release()
                } catch (e: Throwable) {
                    // Ignore
                }
            }
        }

        // 3. Fallback to folder artwork (cached lookup)
        if (bitmap == null) {
            try {
                val parentFile = File(uri).parentFile
                val parentPath = parentFile?.absolutePath
                if (parentPath != null && parentFile.exists()) {
                    val artFilePath = folderArtCache.computeIfAbsent(parentPath) {
                        parentFile.listFiles { _, name ->
                            val n = name.lowercase()
                            n.startsWith("cover.") || n.startsWith("folder.") ||
                            n.startsWith("album.") || n.endsWith(".jpg") || n.endsWith(".png")
                        }?.firstOrNull()?.absolutePath ?: ""
                    }

                    if (artFilePath.isNotBlank() && File(artFilePath).exists()) {
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeFile(artFilePath, options)
                        options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                        options.inJustDecodeBounds = false
                        bitmap = BitmapFactory.decodeFile(artFilePath, options)
                    }
                }
            } catch (e: Throwable) {
                // Ignore
            }
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
        }

        bitmap
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val (height: Int, width: Int) = options.outHeight to options.outWidth
        var inSampleSize = 1

        if (height > reqHeight || width > reqWidth) {
            val halfHeight: Int = height / 2
            val halfWidth: Int = width / 2

            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize.coerceAtLeast(1)
    }
}

@Composable
fun AsyncAlbumArt(
    uri: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(cornerRadius),
    targetSizePx: Int = 128,
    placeholderHueSeed: String = uri
) {
    val bitmapState = remember(uri, targetSizePx) {
        mutableStateOf<Bitmap?>(ArtworkCache.getArtwork(uri, targetSizePx))
    }

    LaunchedEffect(uri, targetSizePx) {
        if (bitmapState.value == null && uri.isNotEmpty()) {
            val bmp = ArtworkCache.loadArtwork(uri, targetSizePx)
            bitmapState.value = bmp
        }
    }

    val bmp = bitmapState.value
    Crossfade(
        targetState = bmp,
        animationSpec = tween(180),
        modifier = modifier.clip(shape),
        label = "albumArtFade"
    ) { currentBmp ->
        if (currentBmp != null && !currentBmp.isRecycled) {
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            // Aesthetic Placeholder Gradient
            val hash = (placeholderHueSeed.hashCode() and 0x7FFFFFFF)
            val grad1 = when (hash % 5) {
                0 -> Color(0xFFD47A5B)
                1 -> Color(0xFF3D5A80)
                2 -> Color(0xFF588157)
                3 -> Color(0xFF7E52A0)
                else -> Color(0xFFB56576)
            }
            val grad2 = when ((hash / 5) % 5) {
                0 -> Color(0xFFE29578)
                1 -> Color(0xFF98C1D9)
                2 -> Color(0xFFA3B18A)
                3 -> Color(0xFFB892FF)
                else -> Color(0xFFE56B6F)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(listOf(grad1, grad2))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = VeylIcons.Folder,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxSize(0.42f)
                )
            }
        }
    }
}
