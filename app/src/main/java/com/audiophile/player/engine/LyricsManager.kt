package com.audiophile.player.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.Xml
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.StringReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

@Immutable
data class LyricWord(
    val text: String,
    val startMs: Long,
    val endMs: Long
)

@Immutable
data class LyricLine(
    val timestampMs: Long,
    val endTimestampMs: Long? = null,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null
) {
    fun findActiveWordIndex(posMs: Long): Int {
        if (words.isEmpty()) return -1
        for (i in words.indices) {
            val w = words[i]
            if (posMs in w.startMs..w.endMs) {
                return i
            }
            if (posMs < w.startMs && i > 0 && posMs >= words[i - 1].endMs) {
                return i - 1
            }
        }
        if (posMs > (words.lastOrNull()?.endMs ?: 0L)) {
            return words.size
        }
        return -1
    }
}

@Immutable
data class TrackLyrics(
    val uri: String,
    val lines: List<LyricLine>,
    val source: String = "local"
) {
    val isSynced: Boolean = lines.any { it.timestampMs > 0 }
    val hasWordTiming: Boolean = lines.any { it.words.isNotEmpty() }

    fun findActiveIndex(positionSeconds: Double, offsetMs: Long = 0L): Int {
        if (lines.isEmpty()) return -1
        val posMs = ((positionSeconds * 1000).toLong() + offsetMs).coerceAtLeast(0L)
        var activeIdx = -1
        for (i in lines.indices) {
            if (lines[i].timestampMs <= posMs) {
                activeIdx = i
            } else {
                break
            }
        }
        return activeIdx
    }
}

object LyricsManager {

    private const val TAG = "LyricsManager"
    private val lineTimeTagPattern = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
    private val wordTimeTagPattern = Pattern.compile("<(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?>")
    private var cacheDirectory: File? = null

    fun init(context: Context) {
        cacheDirectory = File(context.cacheDir, "lyrics_cache").apply { mkdirs() }
    }

    fun clearCache() {
        try {
            cacheDirectory?.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "Lyrics cache cleared")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing lyrics cache", e)
        }
    }

    fun getCachedLyricsCount(): Int {
        return try {
            cacheDirectory?.listFiles()?.count { it.isFile && it.name.endsWith(".lrc") } ?: 0
        } catch (e: Exception) {
            0
        }
    }

    suspend fun loadLyricsForTrack(
        trackUri: String,
        title: String? = null,
        artist: String? = null,
        album: String? = null,
        durationSeconds: Double = 0.0,
        allowOnlineFetch: Boolean = true
    ): TrackLyrics = withContext(Dispatchers.IO) {
        val file = File(trackUri)

        // 1. Search for external sidecar files (.lrc, .ttml, .xml) in track directory
        if (file.exists()) {
            val parent = file.parentFile
            val baseName = file.nameWithoutExtension
            val candidateFiles = listOfNotNull(
                parent?.let { File(it, "$baseName.lrc") },
                parent?.let { File(it, "$baseName.LRC") },
                parent?.let { File(it, "$baseName.ttml") },
                parent?.let { File(it, "$baseName.TTML") },
                File("$trackUri.lrc"),
                parent?.let { File(File(it, "lyrics"), "$baseName.lrc") },
                parent?.let { File(File(it, "Lyrics"), "$baseName.lrc") },
                parent?.let { File(File(it, "lyrics"), "$baseName.ttml") }
            )

            for (candidate in candidateFiles) {
                if (candidate.exists() && candidate.canRead()) {
                    try {
                        val text = decodeBytes(candidate.readBytes())
                        val lines = if (candidate.extension.equals("ttml", ignoreCase = true) || text.contains("<tt")) {
                            parseTtml(text)
                        } else {
                            parseLrc(text)
                        }
                        if (lines.isNotEmpty()) {
                            Log.i(TAG, "Loaded ${lines.size} lyric lines from sidecar ${candidate.name}")
                            return@withContext TrackLyrics(trackUri, lines, source = "file")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading sidecar lyrics from ${candidate.path}", e)
                    }
                }
            }
        }

        // 2. Check local disk cache (previously fetched from LRCLIB)
        val cacheKey = getCacheKey(title ?: file.nameWithoutExtension, artist ?: "")
        val cachedFile = cacheDirectory?.let { File(it, "$cacheKey.lrc") }
        if (cachedFile != null && cachedFile.exists() && cachedFile.canRead()) {
            try {
                val text = decodeBytes(cachedFile.readBytes())
                val lines = parseLrc(text)
                if (lines.isNotEmpty()) {
                    Log.i(TAG, "Loaded ${lines.size} lyric lines from disk cache for $title")
                    return@withContext TrackLyrics(trackUri, lines, source = "cache")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading cached lyrics", e)
            }
        }

        // 3. Extract embedded lyrics from audio file via MediaMetadataRetriever
        if (file.exists()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val embeddedLyrics = try {
                    retriever.extractMetadata(1000) // Some OEM keys
                } catch (e: Exception) {
                    null
                }
                retriever.release()

                if (!embeddedLyrics.isNullOrBlank()) {
                    val lines = parseLrc(fixMojibake(embeddedLyrics))
                    if (lines.isNotEmpty()) {
                        Log.i(TAG, "Loaded ${lines.size} lyric lines from embedded tag")
                        return@withContext TrackLyrics(trackUri, lines, source = "embedded")
                    }
                }
            } catch (e: Exception) {
                // Ignore retriever failure
            }

            // 4. Scan audio header bytes for embedded ID3 USLT / SYLT / LRC tag
            try {
                val bufferSize = 65536.coerceAtMost(file.length().toInt())
                val headerBytes = ByteArray(bufferSize)
                file.inputStream().use { it.read(headerBytes) }

                val usltMarker = byteArrayOf('U'.code.toByte(), 'S'.code.toByte(), 'L'.code.toByte(), 'T'.code.toByte())
                val usltIdx = indexOfSubarray(headerBytes, usltMarker)
                if (usltIdx != -1 && usltIdx + 10 < headerBytes.size) {
                    val encByte = headerBytes[usltIdx + 10].toInt() and 0xFF
                    val payloadStart = usltIdx + 14
                    if (payloadStart < headerBytes.size) {
                        val charset = when (encByte) {
                            1 -> Charsets.UTF_16
                            2 -> Charsets.UTF_16BE
                            3 -> Charsets.UTF_8
                            else -> Charsets.ISO_8859_1
                        }
                        val rawPayload = String(headerBytes, payloadStart, (headerBytes.size - payloadStart).coerceAtMost(16384), charset)
                        val lyricText = rawPayload.substringAfter('\u0000', rawPayload)
                        val lines = parseLrc(fixMojibake(lyricText))
                        if (lines.isNotEmpty()) {
                            Log.i(TAG, "Loaded ${lines.size} lyric lines from ID3 USLT frame")
                            return@withContext TrackLyrics(trackUri, lines, source = "id3")
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore header scan failure
            }
        }

        // 5. Automatic Online Fetch from LRCLIB API (Booming Music style)
        if (allowOnlineFetch && !title.isNullOrBlank()) {
            val fetched = fetchLyricsFromLrcLib(
                title = title,
                artist = artist ?: "",
                album = album,
                durationSeconds = durationSeconds
            )
            if (fetched != null && fetched.lines.isNotEmpty()) {
                // Save to cache for offline use
                cachedFile?.let {
                    try {
                        val rawContent = buildLrcText(fetched.lines)
                        it.writeText(rawContent, Charsets.UTF_8)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed caching lyrics to disk", e)
                    }
                }
                return@withContext fetched.copy(uri = trackUri, source = "lrclib")
            }
        }

        TrackLyrics(trackUri, emptyList())
    }

    private fun getCacheKey(title: String, artist: String): String {
        val clean = "${title.trim().lowercase()}_${artist.trim().lowercase()}"
            .replace(Regex("[^a-z0-9_]"), "_")
        return clean.take(80)
    }

    /**
     * Queries the open-source LRCLIB API (used by Booming Music).
     * Endpoint: https://lrclib.net/api/get
     * Fallback: https://lrclib.net/api/search
     */
    suspend fun fetchLyricsFromLrcLib(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Double
    ): TrackLyrics? = withContext(Dispatchers.IO) {
        try {
            // Clean up title (remove "(feat. ...)", "[Remastered]", etc.)
            val cleanTitle = title.replace(Regex("\\s*[\\[(].*?[\\])]"), "").trim()
            val cleanArtist = artist.replace(Regex("\\s*[\\[(].*?[\\])]"), "").trim()

            // 1. Direct GET request
            val queryParams = buildString {
                append("track_name=").append(URLEncoder.encode(cleanTitle, "UTF-8"))
                append("&artist_name=").append(URLEncoder.encode(cleanArtist, "UTF-8"))
                if (!album.isNullOrBlank()) {
                    append("&album_name=").append(URLEncoder.encode(album.trim(), "UTF-8"))
                }
                if (durationSeconds > 0.0) {
                    append("&duration=").append(durationSeconds.toInt())
                }
            }

            val directUrl = URL("https://lrclib.net/api/get?$queryParams")
            val directConn = (directUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Audiophile-Player-Android/1.0")
                setRequestProperty("Accept", "application/json")
            }

            if (directConn.responseCode == 200) {
                val responseJson = directConn.inputStream.bufferedReader().use { it.readText() }
                directConn.disconnect()
                val parsed = parseLrcLibResponse(responseJson)
                if (parsed != null && parsed.lines.isNotEmpty()) {
                    Log.i(TAG, "LRCLIB direct fetch succeeded with ${parsed.lines.size} lines")
                    return@withContext parsed
                }
            } else {
                directConn.disconnect()
            }

            // 2. Search Fallback if direct lookup did not match
            val searchQuery = "$cleanTitle $cleanArtist".trim()
            val searchUrl = URL("https://lrclib.net/api/search?q=${URLEncoder.encode(searchQuery, "UTF-8")}")
            val searchConn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Audiophile-Player-Android/1.0")
                setRequestProperty("Accept", "application/json")
            }

            if (searchConn.responseCode == 200) {
                val responseJson = searchConn.inputStream.bufferedReader().use { it.readText() }
                searchConn.disconnect()
                val array = JSONArray(responseJson)
                if (array.length() > 0) {
                    // Pick the best match with synced lyrics
                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val syncedLyrics = item.optString("syncedLyrics")
                        if (!syncedLyrics.isNullOrBlank()) {
                            val lines = parseLrc(syncedLyrics)
                            if (lines.isNotEmpty()) {
                                Log.i(TAG, "LRCLIB search fallback matched item #$i with ${lines.size} lines")
                                return@withContext TrackLyrics(cleanTitle, lines, source = "lrclib_search")
                            }
                        }
                    }
                }
            } else {
                searchConn.disconnect()
            }
        } catch (e: Exception) {
            Log.v(TAG, "LRCLIB fetch failed: ${e.message}")
        }
        null
    }

    private fun parseLrcLibResponse(jsonStr: String): TrackLyrics? {
        return try {
            val obj = JSONObject(jsonStr)
            val synced = obj.optString("syncedLyrics")
            if (!synced.isNullOrBlank()) {
                val lines = parseLrc(synced)
                if (lines.isNotEmpty()) return TrackLyrics("", lines, source = "lrclib")
            }
            val plain = obj.optString("plainLyrics")
            if (!plain.isNullOrBlank()) {
                val lines = parseLrc(plain)
                if (lines.isNotEmpty()) return TrackLyrics("", lines, source = "lrclib_plain")
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    fun parseLrc(lrcContent: String): List<LyricLine> {
        val parsedLines = mutableListOf<LyricLine>()
        val fixedContent = fixMojibake(lrcContent)
        val rawLines = fixedContent.lineSequence().toList()

        var hasTimestamp = false

        rawLines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            // Check metadata headers like [ti:Title], [ar:Artist], etc.
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:")
            ) {
                return@forEach
            }

            val matcher = lineTimeTagPattern.matcher(trimmed)
            val timestamps = mutableListOf<Long>()
            var lastMatchEnd = 0

            while (matcher.find()) {
                hasTimestamp = true
                val minutes = matcher.group(1)?.toLongOrNull() ?: 0L
                val seconds = matcher.group(2)?.toLongOrNull() ?: 0L
                val fractionStr = matcher.group(3)
                val millis = when {
                    fractionStr == null -> 0L
                    fractionStr.length == 1 -> fractionStr.toLong() * 100
                    fractionStr.length == 2 -> fractionStr.toLong() * 10
                    fractionStr.length >= 3 -> fractionStr.substring(0, 3).toLong()
                    else -> 0L
                }

                val totalMs = (minutes * 60 * 1000) + (seconds * 1000) + millis
                timestamps.add(totalMs)
                lastMatchEnd = matcher.end()
            }

            if (timestamps.isNotEmpty()) {
                val lineBody = trimmed.substring(lastMatchEnd).trim()
                // Parse word-by-word timestamps inside the line if present: "<00:12.34> word <00:13.10>"
                val words = parseEnhancedLrcWords(lineBody, timestamps.first())
                val cleanText = if (words.isNotEmpty()) {
                    words.joinToString(" ") { it.text }
                } else {
                    fixMojibake(lineBody)
                }

                if (cleanText.isNotBlank()) {
                    for (ts in timestamps) {
                        parsedLines.add(
                            LyricLine(
                                timestampMs = ts,
                                text = cleanText,
                                words = words
                            )
                        )
                    }
                }
            }
        }

        // If no timestamps at all, synthesize linear pacing (3s intervals)
        if (!hasTimestamp && parsedLines.isEmpty()) {
            rawLines.filter { it.isNotBlank() && !it.startsWith("[") }.forEachIndexed { idx, text ->
                parsedLines.add(LyricLine(idx * 3000L, text = fixMojibake(text.trim())))
            }
        }

        // Calculate end timestamps between consecutive lines for smooth karaoke transitions
        val sorted = parsedLines.sortedBy { it.timestampMs }
        val withEndTimes = ArrayList<LyricLine>(sorted.size)
        for (i in sorted.indices) {
            val cur = sorted[i]
            val nextTime = if (i + 1 < sorted.size) sorted[i + 1].timestampMs else cur.timestampMs + 4000L
            withEndTimes.add(cur.copy(endTimestampMs = nextTime))
        }
        return withEndTimes
    }

    private fun parseEnhancedLrcWords(text: String, lineStartMs: Long): List<LyricWord> {
        val matcher = wordTimeTagPattern.matcher(text)
        val tags = mutableListOf<Pair<Long, Int>>() // timestamp to end index of tag
        while (matcher.find()) {
            val min = matcher.group(1)?.toLongOrNull() ?: 0L
            val sec = matcher.group(2)?.toLongOrNull() ?: 0L
            val frac = matcher.group(3)
            val ms = when {
                frac == null -> 0L
                frac.length == 1 -> frac.toLong() * 100
                frac.length == 2 -> frac.toLong() * 10
                frac.length >= 3 -> frac.substring(0, 3).toLong()
                else -> 0L
            }
            val wordMs = (min * 60 * 1000) + (sec * 1000) + ms
            tags.add(wordMs to matcher.end())
        }

        if (tags.size < 2) return emptyList()

        val words = mutableListOf<LyricWord>()
        for (i in 0 until tags.size) {
            val curTag = tags[i]
            val nextStartIdx = if (i + 1 < tags.size) {
                // Word text is between curTag.second and nextTag start
                text.indexOf('<', curTag.second).let { if (it == -1) text.length else it }
            } else {
                text.length
            }
            val wordStr = text.substring(curTag.second, nextStartIdx).trim()
            val startMs = curTag.first
            val endMs = if (i + 1 < tags.size) tags[i + 1].first else startMs + 600L

            if (wordStr.isNotEmpty()) {
                words.add(LyricWord(wordStr, startMs, endMs))
            }
        }
        return words
    }

    /**
     * TTML Parser supporting word-level `<span begin="..." end="...">` tags (Booming Music format)
     */
    fun parseTtml(ttmlContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        try {
            val parser = Xml.newPullParser().apply {
                setInput(StringReader(ttmlContent))
            }

            var eventType = parser.eventType
            var currentLineStart = 0L
            var currentLineEnd = 0L
            var currentLineWords = mutableListOf<LyricWord>()
            var currentLineText = StringBuilder()
            var currentSpanBegin = 0L
            var currentSpanEnd = 0L
            var isInsideParagraph = false
            var isInsideSpan = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name.lowercase()) {
                            "p" -> {
                                isInsideParagraph = true
                                currentLineWords = mutableListOf()
                                currentLineText = StringBuilder()
                                val beginAttr = parser.getAttributeValue(null, "begin")
                                val endAttr = parser.getAttributeValue(null, "end")
                                currentLineStart = parseTtmlTime(beginAttr)
                                currentLineEnd = parseTtmlTime(endAttr)
                            }
                            "span" -> {
                                isInsideSpan = true
                                val beginAttr = parser.getAttributeValue(null, "begin")
                                val endAttr = parser.getAttributeValue(null, "end")
                                currentSpanBegin = if (!beginAttr.isNullOrBlank()) parseTtmlTime(beginAttr) else currentLineStart
                                currentSpanEnd = if (!endAttr.isNullOrBlank()) parseTtmlTime(endAttr) else currentSpanBegin + 400L
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        val text = parser.text?.trim() ?: ""
                        if (text.isNotEmpty() && isInsideParagraph) {
                            currentLineText.append(text).append(" ")
                            if (isInsideSpan) {
                                currentLineWords.add(LyricWord(text, currentSpanBegin, currentSpanEnd))
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        when (parser.name.lowercase()) {
                            "span" -> {
                                isInsideSpan = false
                            }
                            "p" -> {
                                isInsideParagraph = false
                                val fullText = currentLineText.toString().trim()
                                if (fullText.isNotEmpty()) {
                                    lines.add(
                                        LyricLine(
                                            timestampMs = currentLineStart,
                                            endTimestampMs = if (currentLineEnd > currentLineStart) currentLineEnd else currentLineStart + 3500L,
                                            text = fullText,
                                            words = currentLineWords
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing TTML lyrics", e)
        }
        return lines.sortedBy { it.timestampMs }
    }

    private fun parseTtmlTime(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        val trimmed = timeStr.trim()
        // Format "00:01:23.456" or "01:23.45" or "83.45s"
        return try {
            if (trimmed.endsWith("s", ignoreCase = true)) {
                (trimmed.dropLast(1).toDouble() * 1000).toLong()
            } else {
                val parts = trimmed.split(":")
                when (parts.size) {
                    3 -> {
                        val h = parts[0].toLong()
                        val m = parts[1].toLong()
                        val s = parts[2].toDouble()
                        (h * 3600 * 1000) + (m * 60 * 1000) + (s * 1000).toLong()
                    }
                    2 -> {
                        val m = parts[0].toLong()
                        val s = parts[1].toDouble()
                        (m * 60 * 1000) + (s * 1000).toLong()
                    }
                    else -> (trimmed.toDouble() * 1000).toLong()
                }
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun buildLrcText(lines: List<LyricLine>): String {
        return lines.joinToString("\n") { line ->
            val totalSec = line.timestampMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            val ms = (line.timestampMs % 1000) / 10
            "[%02d:%02d.%02d] %s".format(min, sec, ms, line.text)
        }
    }

    fun decodeBytes(bytes: ByteArray): String {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        }
        return fixMojibake(String(bytes, Charsets.UTF_8))
    }

    fun fixMojibake(text: String): String {
        if (text.contains("à´") || text.contains("àµ") || text.contains("à®") || text.contains("à¯") ||
            text.contains("à¤") || text.contains("à¥") || text.contains("Ã") || text.contains("â€")
        ) {
            try {
                val rawBytes = text.toByteArray(Charsets.ISO_8859_1)
                val recovered = String(rawBytes, Charsets.UTF_8)
                if (!recovered.contains("\uFFFD")) {
                    return recovered
                }
                return recovered.replace("\uFFFD", "")
            } catch (e: Exception) {
                // Keep original
            }
        }
        return text
    }

    private fun indexOfSubarray(array: ByteArray, sub: ByteArray): Int {
        if (sub.isEmpty() || array.size < sub.size) return -1
        outer@ for (i in 0..array.size - sub.size) {
            for (j in sub.indices) {
                if (array[i + j] != sub[j]) continue@outer
            }
            return i
        }
        return -1
    }
}
