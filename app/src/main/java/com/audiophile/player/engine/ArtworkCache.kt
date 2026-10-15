package com.audiophile.player.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import uniffi.audiophile_core.LibraryEngine
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

object ArtworkCache {

    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 5).coerceAtLeast(16 * 1024)

    private val memoryCache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return bitmap.byteCount / 1024
        }
    }

    // Negative (cache miss) set: tracks confirmed to have NO artwork to avoid re-probing on scroll
    private val noArtworkSet = ConcurrentHashMap.newKeySet<String>()

    // In-flight deduplication to avoid multiple simultaneous extractions for the same track/album
    private val inFlightLoads = ConcurrentHashMap<String, Deferred<Bitmap?>>()

    // Cache parent folder artwork lookups to prevent disk scanning per track
    private val folderArtCache = ConcurrentHashMap<String, String>()
    // Mapping from track URI to remote thumbnail URL for streaming tracks
    private val onlineArtMapping = ConcurrentHashMap<String, String>()

    private val libraryEngine = LibraryEngine()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile
    private var diskCacheDir: File? = null

    fun init(context: Context) {
        if (diskCacheDir == null) {
            val dir = File(context.cacheDir, "art_thumbs")
            if (!dir.exists()) dir.mkdirs()
            diskCacheDir = dir
        }
    }

    fun registerOnlineArtwork(trackUri: String, imageUrl: String) {
        if (trackUri.isNotBlank() && imageUrl.isNotBlank()) {
            onlineArtMapping[trackUri] = imageUrl
            val clean = trackUri.removePrefix("file://")
            onlineArtMapping[clean] = imageUrl
            onlineArtMapping["file://$clean"] = imageUrl
            val name = File(clean).name
            if (name.isNotBlank()) {
                onlineArtMapping[name] = imageUrl
            }
        }
    }

    fun getOnlineArtwork(trackUri: String): String? {
        val clean = trackUri.removePrefix("file://")
        return onlineArtMapping[trackUri] ?: onlineArtMapping[clean] ?: onlineArtMapping["file://$clean"] ?: onlineArtMapping[File(clean).name]
    }

    fun markNoArtwork(uri: String) {
        if (uri.isNotBlank()) {
            val clean = uri.removePrefix("file://")
            noArtworkSet.add(uri)
            noArtworkSet.add(clean)
            noArtworkSet.add("file://$clean")
        }
    }

    fun getArtwork(uri: String, targetSizePx: Int = 128): Bitmap? {
        if (uri.isBlank()) return null
        val clean = uri.removePrefix("file://")
        if (noArtworkSet.contains(uri) || noArtworkSet.contains(clean)) return null

        val actualUri = onlineArtMapping[uri] ?: onlineArtMapping[clean] ?: onlineArtMapping["file://$clean"] ?: onlineArtMapping[File(clean).name] ?: uri
        val cacheKey = if (targetSizePx > 256) "${actualUri}_lg" else "${actualUri}_sm"
        val mem = memoryCache.get(cacheKey)
        if (mem != null) return mem

        // Persistent disk cache lookup: provides instant album covers on app reopen
        val diskFile = getDiskCacheFile(cacheKey)
        if (diskFile != null && diskFile.exists() && diskFile.length() > 0) {
            try {
                val diskBmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (diskBmp != null) {
                    memoryCache.put(cacheKey, diskBmp)
                    return diskBmp
                }
            } catch (_: Throwable) {
                diskFile.delete()
            }
        }
        return null
    }

    private fun getDiskCacheFile(cacheKey: String): File? {
        val dir = diskCacheDir ?: return null
        val hash = try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(cacheKey.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            cacheKey.hashCode().toString()
        }
        return File(dir, "$hash.webp")
    }

    private fun saveBitmapToDisk(diskFile: File, bitmap: Bitmap) {
        try {
            val tmp = File(diskFile.parentFile, "${diskFile.name}.tmp")
            FileOutputStream(tmp).use { out ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    bitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, out)
                } else {
                    @Suppress("DEPRECATION")
                    bitmap.compress(Bitmap.CompressFormat.WEBP, 85, out)
                }
                out.flush()
            }
            tmp.renameTo(diskFile)
        } catch (e: Exception) {
            Log.w("ArtworkCache", "Failed to cache thumbnail to disk", e)
        }
    }

    suspend fun loadArtwork(uri: String, targetSizePx: Int = 128): Bitmap? {
        if (uri.isBlank()) return null
        val clean = uri.removePrefix("file://")
        if (noArtworkSet.contains(uri) || noArtworkSet.contains(clean)) return null

        val actualUri = onlineArtMapping[uri] ?: onlineArtMapping[clean] ?: onlineArtMapping["file://$clean"] ?: onlineArtMapping[File(clean).name] ?: uri
        val cacheKey = if (targetSizePx > 256) "${actualUri}_lg" else "${actualUri}_sm"

        // 1. Memory Cache fast return (0ms)
        val cachedMem = memoryCache.get(cacheKey)
        if (cachedMem != null) return cachedMem

        // 2. In-flight Deduplication: Await existing load if another row already requested it
        val existingJob = inFlightLoads[cacheKey]
        if (existingJob != null) {
            return existingJob.await()
        }

        // 3. Launch single deduplicated background load
        return coroutineScope {
            val deferred = async(Dispatchers.IO) {
                try {
                    doLoadArtwork(uri, clean, actualUri, cacheKey, targetSizePx)
                } finally {
                    inFlightLoads.remove(cacheKey)
                }
            }
            inFlightLoads[cacheKey] = deferred
            deferred.await()
        }
    }

    private fun doLoadArtwork(
        uri: String,
        clean: String,
        actualUri: String,
        cacheKey: String,
        targetSizePx: Int
    ): Bitmap? {
        // Double check memory cache
        val mem = memoryCache.get(cacheKey)
        if (mem != null) return mem

        // Persistent Disk Thumbnail Cache lookup (<1ms)
        val diskFile = getDiskCacheFile(cacheKey)
        if (diskFile != null && diskFile.exists() && diskFile.length() > 0) {
            try {
                val diskBmp = BitmapFactory.decodeFile(diskFile.absolutePath)
                if (diskBmp != null) {
                    memoryCache.put(cacheKey, diskBmp)
                    return diskBmp
                }
            } catch (_: Throwable) {
                diskFile.delete()
            }
        }

        var bitmap: Bitmap? = null

        // 0. Check if actualUri is a companion image file directly (.jpg/.png)
        val candidateFilePath = when {
            actualUri.startsWith("file://") -> actualUri.removePrefix("file://")
            else -> actualUri
        }
        val candidateImgFile = File(candidateFilePath)
        if (candidateImgFile.exists() && (candidateImgFile.extension.equals("jpg", true) || candidateImgFile.extension.equals("jpeg", true) || candidateImgFile.extension.equals("png", true))) {
            try {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(candidateImgFile.absolutePath, options)
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                bitmap = BitmapFactory.decodeFile(candidateImgFile.absolutePath, options)
            } catch (e: Throwable) {
                Log.w("ArtworkCache", "Failed to decode companion image file $candidateFilePath", e)
            }
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                if (diskFile != null) saveBitmapToDisk(diskFile, bitmap)
                return bitmap
            }
        }

        // 0b. Check if HTTP/HTTPS remote URL - use pooled OkHttp
        if (actualUri.startsWith("http://") || actualUri.startsWith("https://")) {
            try {
                val req = Request.Builder().url(actualUri).build()
                okHttpClient.newCall(req).execute().use { response ->
                    if (response.isSuccessful) {
                        val bytes = response.body?.bytes()
                        if (bytes != null && bytes.isNotEmpty()) {
                            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                            options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                            options.inJustDecodeBounds = false
                            bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                        }
                    }
                }
            } catch (e: Throwable) {
                Log.w("ArtworkCache", "Failed to load remote artwork from $actualUri", e)
            }
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                if (diskFile != null) saveBitmapToDisk(diskFile, bitmap)
                return bitmap
            }
        }

        // 1. Try Rust native LibraryEngine (handles DSF, DFF, FLAC, Lofty tags)
        try {
            val artData = libraryEngine.extractArtwork(uri)
            if (artData != null && artData.data.isNotEmpty()) {
                val bytes = artData.data
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
                options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                options.inJustDecodeBounds = false
                bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
            }
        } catch (_: Throwable) {}

        // 2. Fallback to Android MediaMetadataRetriever
        if (bitmap == null && File(clean).exists()) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(clean)
                val rawBytes = retriever.embeddedPicture
                if (rawBytes != null && rawBytes.isNotEmpty()) {
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                    options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                    options.inJustDecodeBounds = false
                    bitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
                }
            } catch (_: Throwable) {
            } finally {
                try { retriever.release() } catch (_: Throwable) {}
            }
        }

        // 3. Fallback to companion artwork or folder artwork
        if (bitmap == null) {
            try {
                val currentFile = File(clean)
                val parentFile = currentFile.parentFile
                val parentPath = parentFile?.absolutePath
                if (parentPath != null && parentFile.exists()) {
                    val companionArt = File(parentFile, "${currentFile.nameWithoutExtension}.jpg")
                    val artFilePath = if (companionArt.exists()) {
                        companionArt.absolutePath
                    } else {
                        folderArtCache.computeIfAbsent(parentPath) {
                            parentFile.listFiles { _, name ->
                                val n = name.lowercase()
                                n.startsWith("cover.") || n.startsWith("folder.") ||
                                n.startsWith("album.") || n.endsWith(".jpg") || n.endsWith(".png")
                            }?.firstOrNull()?.absolutePath ?: ""
                        }
                    }

                    if (artFilePath.isNotBlank() && File(artFilePath).exists()) {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(artFilePath, options)
                        options.inSampleSize = calculateInSampleSize(options, targetSizePx, targetSizePx)
                        options.inJustDecodeBounds = false
                        bitmap = BitmapFactory.decodeFile(artFilePath, options)
                    }
                }
            } catch (_: Throwable) {}
        }

        if (bitmap != null) {
            memoryCache.put(cacheKey, bitmap)
            if (diskFile != null) saveBitmapToDisk(diskFile, bitmap)
            return bitmap
        } else {
            // Negative caching: mark this URI as having NO artwork so future scrolls never re-probe!
            noArtworkSet.add(uri)
            noArtworkSet.add(clean)
            noArtworkSet.add("file://$clean")
            return null
        }
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

/**
 * Zero-jank album art composable.
 * - Instantly displays cached in-memory bitmaps on composition (no Crossfade delay).
 * - Bypasses I/O completely if [hasArtwork] is known to be false.
 * - Renders a sleek gradient placeholder with no subcomposition penalty.
 */
@Composable
fun AsyncAlbumArt(
    uri: String,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(cornerRadius),
    targetSizePx: Int = 128,
    placeholderHueSeed: String = uri,
    hasArtwork: Boolean? = null
) {
    // Synchronously check memory and disk cache during composition for instant display
    val cached = remember(uri, targetSizePx) {
        ArtworkCache.getArtwork(uri, targetSizePx)
    }

    var bitmapState by remember(uri, targetSizePx) {
        mutableStateOf(cached)
    }

    LaunchedEffect(uri, targetSizePx) {
        if (bitmapState == null && uri.isNotEmpty()) {
            val bmp = ArtworkCache.loadArtwork(uri, targetSizePx)
            if (bmp != null) {
                bitmapState = bmp
            }
        }
    }

    val currentBmp = bitmapState
    if (currentBmp != null && !currentBmp.isRecycled) {
        Image(
            bitmap = currentBmp.asImageBitmap(),
            contentDescription = contentDescription,
            modifier = modifier.clip(shape),
            contentScale = ContentScale.Crop
        )
    } else {
        ArtworkPlaceholder(shape = shape, modifier = modifier)
    }
}

/**
 * Audiophile matte-dark placeholder matching Veyl's obsidian luxury design system.
 * Replaces jarring bright multicolor gradients and file/folder icons with a sleek dark aesthetic.
 */
@Composable
fun ArtworkPlaceholder(
    placeholderHueSeed: String = "",
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.radialGradient(
                    listOf(
                        Color(0xFF222227),
                        Color(0xFF141417)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = VeylIcons.MusicNote,
            contentDescription = null,
            tint = Color(0xFF7A7A88).copy(alpha = 0.55f),
            modifier = Modifier.fillMaxSize(0.40f)
        )
    }
}
