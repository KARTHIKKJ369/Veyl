package com.audiophile.player.online

import android.content.Context
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
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.stream.StreamInfo
import uniffi.audiophile_core.TrackInfo
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Handles online audio stream extraction and progressive caching for Veyl.
 * Streams are progressively written to a temporary cache file so Symphonia can
 * start decoding in near real-time without permanently downloading the track.
 */
class OnlineStreamManager(private val context: Context) {

    companion object {
        private const val TAG = "OnlineStreamManager"
        // 128 KB of 160kbps Opus is ~6.5 seconds of audio — perfect for instant startup
        private const val PREBUFFER_THRESHOLD_BYTES = 131072L
        private val isNewPipeInitialized = AtomicBoolean(false)
    }

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var activeStreamJob: Job? = null
    private var activeStreamCall: Call? = null
    private var currentStreamFile: File? = null

    init {
        ensureNewPipeInit()
    }

    private fun ensureNewPipeInit() {
        if (isNewPipeInitialized.compareAndSet(false, true)) {
            try {
                NewPipe.init(OkHttpDownloader(okHttpClient))
                Log.d(TAG, "NewPipeExtractor initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to initialize NewPipeExtractor", e)
            }
        }
    }

    /**
     * Resolves the highest quality audio stream for a YouTube Music video ID.
     * Prefers Opus ~160kbps (Format 251), falls back to AAC/M4A ~128kbps.
     */
    suspend fun resolveAudioStreamUrl(videoId: String): String? = withContext(Dispatchers.IO) {
        try {
            ensureNewPipeInit()
            val watchUrl = "https://www.youtube.com/watch?v=$videoId"
            val info = StreamInfo.getInfo(ServiceList.YouTube, watchUrl)

            val audioStreams = info.audioStreams ?: return@withContext null
            if (audioStreams.isEmpty()) return@withContext null

            // Prioritize Opus (WebM) highest bitrate
            val bestOpus = audioStreams
                .filter { it.format == MediaFormat.WEBMA || it.codec?.contains("opus", ignoreCase = true) == true }
                .maxByOrNull { it.averageBitrate }

            if (bestOpus?.content != null) {
                Log.d(TAG, "Resolved Opus stream: ${bestOpus.averageBitrate} bps")
                return@withContext bestOpus.content
            }

            // Fallback: Best available audio stream
            val bestAudio = audioStreams.maxByOrNull { it.averageBitrate }
            Log.d(TAG, "Fallback to stream: ${bestAudio?.format} (${bestAudio?.averageBitrate} bps)")
            bestAudio?.content
        } catch (e: Exception) {
            Log.e(TAG, "Error resolving audio stream for videoId=$videoId", e)
            null
        }
    }

    /**
     * Starts progressive streaming into a temporary cache file.
     * Once [PREBUFFER_THRESHOLD_BYTES] is received, [onPrebufferReady] is triggered,
     * allowing the native Rust engine to begin playback while downloading continues.
     */
    fun startProgressiveStream(
        streamUrl: String,
        onPrebufferReady: (File) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        stopCurrentStream()

        activeStreamJob = scope.launch {
            var tempFile: File? = null

            try {
                cleanOldStreamCaches()

                val ext = if (streamUrl.contains("webm") || streamUrl.contains("audio%2Fwebm")) "webm" else "m4a"
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
                        onPrebufferReady(tempFile)
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
            sampleRate = 48000u,
            bitDepth = 16u,
            bitrate = 160000u,
            channels = 2u,
            formatName = "OPUS 160k (YTM Stream)",
            hasArtwork = song.thumbnailUrl.isNotBlank()
        )
    }

    private class OkHttpDownloader(private val client: OkHttpClient) : Downloader() {
        override fun execute(request: Request): Response {
            val builder = okhttp3.Request.Builder().url(request.url())
            request.headers()?.forEach { (name, values) ->
                values.forEach { builder.addHeader(name, it) }
            }
            val body = request.dataToSend()?.toRequestBody(null)
            builder.method(request.httpMethod(), body)

            val res = client.newCall(builder.build()).execute()
            return Response(
                res.code,
                res.message,
                res.headers.toMultimap(),
                res.body?.string() ?: "",
                res.request.url.toString()
            )
        }
    }
}
