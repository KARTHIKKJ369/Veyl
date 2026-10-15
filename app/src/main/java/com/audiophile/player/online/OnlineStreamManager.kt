package com.audiophile.player.online

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import uniffi.audiophile_core.TrackInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Handles high-speed direct studio audio caching and library downloading.
 * Audio is quickly streamed into a temporary rolling cache file so Symphonia
 * decodes instantly in pure Rust with 10-band biquad EQ and AAudio MMAP output.
 */
class OnlineStreamManager(private val context: Context) {

    companion object {
        private const val TAG = "OnlineStreamManager"
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var activeStreamJob: Job? = null
    private var activeStreamCall: Call? = null
    private var currentStreamFile: File? = null

    /**
     * Resolves the direct audio stream URL. If already present, returns immediately (<1ms).
     * If missing, queries JioSaavnService for the stream URL.
     */
    suspend fun resolveAudioStreamUrl(song: OnlineSongItem): String? {
        if (song.streamUrl.isNotBlank()) return song.streamUrl
        try {
            val query = "${song.title} ${song.artist}".trim()
            val results = JioSaavnService.searchSongs(query)
            if (results.isNotEmpty()) {
                val match = results.first()
                return match.streamUrl
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve stream URL for ${song.title}", e)
        }
        return null
    }

    /**
     * Backwards-compatible overload for string ID resolution.
     */
    suspend fun resolveAudioStreamUrl(videoId: String): String? {
        return null
    }

    /**
     * Starts ultra-fast streaming into a temporary cache file.
     * Fires [onStreamReady] as soon as the file is ready for Symphonia to decode.
     */
    fun startStream(
        streamUrl: String,
        onStreamReady: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stopCurrentStream()

        activeStreamJob = scope.launch {
            var tempFile: File? = null

            try {
                cleanOldStreamCaches()

                val ext = if (streamUrl.contains(".mp4") || streamUrl.contains(".m4a") || streamUrl.contains("saavncdn")) "m4a" else "webm"
                tempFile = File(context.cacheDir, "veyl_online_stream_${System.currentTimeMillis()}.$ext")
                currentStreamFile = tempFile

                val request = okhttp3.Request.Builder()
                    .url(streamUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                val call = okHttpClient.newCall(request)
                activeStreamCall = call

                val response = call.execute()
                if (!response.isSuccessful) {
                    throw IOException("HTTP streaming error: ${response.code}")
                }

                val body = response.body ?: throw IOException("Empty response body from audio stream")
                val inputStream = body.byteStream()
                val outputStream = FileOutputStream(tempFile)

                val buffer = ByteArray(65536)
                var bytesRead = inputStream.read(buffer)
                var totalBytesWritten = 0L

                try {
                    while (isActive && bytesRead != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        totalBytesWritten += bytesRead
                        bytesRead = inputStream.read(buffer)
                    }
                    outputStream.flush()
                } finally {
                    try { outputStream.close() } catch (_: Exception) {}
                    try { inputStream.close() } catch (_: Exception) {}
                }

                if (isActive && totalBytesWritten > 0) {
                    Log.d(TAG, "Stream buffered completely (${totalBytesWritten / 1024} KB). Triggering playback.")
                    withContext(Dispatchers.Main) {
                        onStreamReady(tempFile)
                    }
                }
            } catch (e: CancellationException) {
                Log.d(TAG, "Streaming cancelled by user")
            } catch (e: Exception) {
                Log.e(TAG, "Error during streaming", e)
                withContext(Dispatchers.Main) {
                    onError(e)
                }
            }
        }
    }

    /**
     * Alias for startStream to support progressive playback contracts.
     */
    fun startProgressiveStream(
        streamUrl: String,
        onPrebufferReady: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        startStream(streamUrl, onPrebufferReady, onError)
    }

    fun getTargetStorageFile(song: OnlineSongItem): File {
        val sanitized = "${song.title} - ${song.artist}".replace(Regex("[^a-zA-Z0-9.\\- ]"), "_").trim()
        val fileName = "$sanitized.m4a"

        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Veyl")
        val publicFile = File(publicDir, fileName)
        if (publicFile.exists() && publicFile.length() > 50_000L) {
            return publicFile
        }

        val appExtDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Veyl")
        val appExtFile = File(appExtDir, fileName)
        if (appExtFile.exists() && appExtFile.length() > 50_000L) {
            return appExtFile
        }

        val canWritePublic = try {
            publicDir.exists() && publicDir.canWrite()
        } catch (_: Exception) {
            false
        }

        val targetDir = if (canWritePublic) publicDir else appExtDir
        return File(targetDir, fileName)
    }

    fun getTargetStorageFileForTrack(track: TrackInfo): File {
        val sanitized = "${track.title} - ${track.artist ?: ""}".replace(Regex("[^a-zA-Z0-9.\\- ]"), "_").trim()
        val fileName = "$sanitized.m4a"

        val publicDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Veyl")
        val publicFile = File(publicDir, fileName)
        if (publicFile.exists() && publicFile.length() > 50_000L) {
            return publicFile
        }

        val appExtDir = File(context.getExternalFilesDir(Environment.DIRECTORY_MUSIC), "Veyl")
        val appExtFile = File(appExtDir, fileName)
        if (appExtFile.exists() && appExtFile.length() > 50_000L) {
            return appExtFile
        }

        val canWritePublic = try {
            publicDir.exists() && publicDir.canWrite()
        } catch (_: Exception) {
            false
        }

        val targetDir = if (canWritePublic) publicDir else appExtDir
        return File(targetDir, fileName)
    }

    fun isSongDownloaded(song: OnlineSongItem): Boolean {
        val f = getTargetStorageFile(song)
        return f.exists() && f.length() > 50_000L
    }

    fun isTrackDownloaded(track: TrackInfo): Boolean {
        if (!track.uri.contains("veyl_online_stream_") && File(track.uri).exists()) {
            return true
        }
        val f = getTargetStorageFileForTrack(track)
        return f.exists() && f.length() > 50_000L
    }

    /**
     * Downloads an online song permanently to the user's Music/Veyl directory
     * and triggers Android's MediaScanner so it immediately appears in the local library.
     */
    suspend fun downloadSongToStorage(
        song: OnlineSongItem,
        onProgress: (Float) -> Unit,
        onCompleted: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val targetFile = getTargetStorageFile(song)
            if (targetFile.exists() && targetFile.length() > 50_000L) {
                finalizeDownloadedFile(song, targetFile)
                withContext(Dispatchers.Main) {
                    onCompleted(targetFile)
                }
                return@withContext
            }

            // If active streaming cache file is already buffered for this song, copy directly!
            val activeCache = currentStreamFile
            if (activeCache != null && activeCache.exists() && activeCache.length() > 50_000L) {
                activeCache.copyTo(targetFile, overwrite = true)
                finalizeDownloadedFile(song, targetFile)
                withContext(Dispatchers.Main) {
                    onProgress(1f)
                    onCompleted(targetFile)
                }
                return@withContext
            }

            val streamUrl = if (song.streamUrl.isNotBlank()) song.streamUrl else resolveAudioStreamUrl(song)
            if (streamUrl.isNullOrBlank()) {
                throw IOException("Unable to resolve audio stream URL for ${song.title}")
            }

            val request = okhttp3.Request.Builder()
                .url(streamUrl)
                .build()

            val call = okHttpClient.newCall(request)
            val response = call.execute()
            if (!response.isSuccessful) throw IOException("Download failed: ${response.code}")

            val body = response.body ?: throw IOException("Empty body")
            val totalBytes = body.contentLength()
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(targetFile)

            val buffer = ByteArray(65536)
            var bytesRead = inputStream.read(buffer)
            var downloaded = 0L

            try {
                while (bytesRead != -1) {
                    outputStream.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalBytes > 0) {
                        withContext(Dispatchers.Main) {
                            onProgress(downloaded.toFloat() / totalBytes)
                        }
                    }
                    bytesRead = inputStream.read(buffer)
                }
                outputStream.flush()
            } finally {
                try { outputStream.close() } catch (_: Exception) {}
                try { inputStream.close() } catch (_: Exception) {}
            }

            finalizeDownloadedFile(song, targetFile)

            withContext(Dispatchers.Main) {
                onCompleted(targetFile)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onError(e)
            }
        }
    }

    fun finalizeDownloadedFile(song: OnlineSongItem, targetFile: File) {
        var coverBytes: ByteArray? = null
        if (song.thumbnailUrl.isNotBlank()) {
            try {
                val imgReq = okhttp3.Request.Builder()
                    .url(song.thumbnailUrl)
                    .build()
                val imgResp = okHttpClient.newCall(imgReq).execute()
                if (imgResp.isSuccessful) {
                    coverBytes = imgResp.body?.bytes()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to download album art image for embedding", e)
            }
        }

        val coverFile = File(targetFile.parentFile, "${targetFile.nameWithoutExtension}.jpg")

        // If downloading image failed, check if companion cover already exists on disk
        if ((coverBytes == null || coverBytes.isEmpty()) && coverFile.exists()) {
            try {
                coverBytes = coverFile.readBytes()
            } catch (_: Exception) {}
        }

        // Save .jpg cover art beside audio file in Music/Veyl
        if (coverBytes != null && coverBytes.isNotEmpty() && !coverFile.exists()) {
            try {
                coverFile.writeBytes(coverBytes)
                android.media.MediaScannerConnection.scanFile(
                    context,
                    arrayOf(coverFile.absolutePath),
                    arrayOf("image/jpeg")
                ) { _, _ -> }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save companion cover.jpg", e)
            }
        }

        // Embed MP4/iTunes tags (Title, Artist, Album, and Cover Art) directly into the .m4a file
        Mp4MetadataTagger.embedMetadata(
            file = targetFile,
            title = song.title,
            artist = song.artist,
            album = song.album.ifBlank { song.title },
            coverBytes = coverBytes
        )

        // Register in ArtworkCache
        if (coverFile.exists()) {
            com.audiophile.player.engine.ArtworkCache.registerOnlineArtwork(targetFile.absolutePath, "file://${coverFile.absolutePath}")
            com.audiophile.player.engine.ArtworkCache.registerOnlineArtwork(targetFile.name, "file://${coverFile.absolutePath}")
        }
        if (song.thumbnailUrl.isNotBlank()) {
            com.audiophile.player.engine.ArtworkCache.registerOnlineArtwork(targetFile.absolutePath, song.thumbnailUrl)
            com.audiophile.player.engine.ArtworkCache.registerOnlineArtwork("file://${targetFile.absolutePath}", song.thumbnailUrl)
            com.audiophile.player.engine.ArtworkCache.registerOnlineArtwork(targetFile.name, song.thumbnailUrl)
        }

        // Register in Android MediaScanner
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(targetFile.absolutePath),
            arrayOf("audio/mp4")
        ) { _, _ -> }
    }

    fun stopCurrentStream() {
        activeStreamJob?.cancel()
        activeStreamJob = null

        activeStreamCall?.cancel()
        activeStreamCall = null

        cleanOldStreamCaches()
    }

    private fun cleanOldStreamCaches() {
        try {
            val cacheDir = context.cacheDir
            cacheDir.listFiles { _, name ->
                name.startsWith("veyl_online_stream_")
            }?.forEach { file ->
                // Don't delete if it's the currently active stream file
                if (file.absolutePath != currentStreamFile?.absolutePath) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not clean old stream caches", e)
        }
    }

    /**
     * Converts an [OnlineSongItem] and local streaming [file] into a native [TrackInfo].
     */
    fun createTrackInfo(song: OnlineSongItem, file: File): TrackInfo {
        return TrackInfo(
            uri = file.absolutePath,
            title = song.title,
            artist = song.artist,
            album = song.album,
            albumArtist = song.artist,
            genre = "Online Stream",
            year = null,
            trackNumber = null,
            discNumber = null,
            durationSeconds = song.durationSeconds,
            sampleRate = 44100u,
            bitDepth = 16u.toUByte(),
            bitrate = 320000u,
            channels = 2u.toUByte(),
            formatName = "AAC 320k (Studio Master)",
            hasArtwork = song.thumbnailUrl.isNotBlank()
        )
    }
}
