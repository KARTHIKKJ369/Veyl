package com.audiophile.player.engine

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import android.util.LruCache
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
import kotlin.math.abs

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
    /**
     * Determines active word index using deterministic player clock position.
     * Returns:
     * - -1 if playback is before the first word
     * - Index of the word currently being vocalized
     * - words.size if all words have finished vocalizing
     */
    fun findActiveWordIndex(posMs: Long): Int {
        if (words.isEmpty()) return -1
        if (posMs < words.first().startMs) return -1
        for (i in words.indices) {
            val w = words[i]
            val isLast = i == words.size - 1
            if (posMs >= w.startMs && (posMs < w.endMs || (isLast && posMs <= w.endMs))) {
                return i
            }
        }
        if (posMs > (words.lastOrNull()?.endMs ?: 0L)) {
            return words.size - 1
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

    /**
     * High-performance O(log N) binary search for the active lyric line index.
     * Returns the index of the line that should be active at the given position,
     * or -1 if the position is before the first line.
     */
    fun findActiveIndex(positionSeconds: Double, offsetMs: Long = 0L): Int {
        if (lines.isEmpty()) return -1
        val posMs = ((positionSeconds * 1000).toLong() + offsetMs).coerceAtLeast(0L)

        // Quick boundary checks
        if (posMs < lines.first().timestampMs) return -1
        if (posMs >= lines.last().timestampMs) return lines.size - 1

        var low = 0
        var high = lines.size - 1
        var bestIdx = -1

        while (low <= high) {
            val mid = (low + high) ushr 1
            val midTime = lines[mid].timestampMs
            if (midTime <= posMs) {
                bestIdx = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return bestIdx
    }

    fun getActiveLine(positionSeconds: Double, offsetMs: Long = 0L): LyricLine? {
        val idx = findActiveIndex(positionSeconds, offsetMs)
        return if (idx in lines.indices) lines[idx] else null
    }
}

object LyricsManager {

    private const val TAG = "LyricsManager"
    private val lineTimeTagPattern = Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?\\]")
    private val wordTimeTagPattern = Pattern.compile("<(\\d{1,3}):(\\d{2})(?:\\.(\\d{1,3}))?>")
    private val offsetTagPattern = Pattern.compile("\\[offset:\\s*([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE)

    private var cacheDirectory: File? = null

    // In-memory LRU cache to prevent disk I/O and re-parsing during rapid track switches
    private val memoryLyricsCache = object : LruCache<String, TrackLyrics>(50) {}

    fun init(context: Context) {
        cacheDirectory = File(context.cacheDir, "lyrics_cache").apply { mkdirs() }
    }

    fun clearCache() {
        try {
            memoryLyricsCache.evictAll()
            cacheDirectory?.listFiles()?.forEach { it.delete() }
            Log.i(TAG, "Lyrics memory and disk cache cleared")
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
        // Check in-memory cache first
        val memCached = memoryLyricsCache.get(trackUri)
        if (memCached != null) {
            return@withContext memCached
        }

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
                            val result = TrackLyrics(trackUri, lines, source = "file")
                            memoryLyricsCache.put(trackUri, result)
                            return@withContext result
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
                    val result = TrackLyrics(trackUri, lines, source = "cache")
                    memoryLyricsCache.put(trackUri, result)
                    return@withContext result
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
                    retriever.extractMetadata(1000) // Some OEM metadata keys
                } catch (e: Exception) {
                    null
                }
                retriever.release()

                if (!embeddedLyrics.isNullOrBlank()) {
                    val lines = parseLrc(fixMojibake(embeddedLyrics))
                    if (lines.isNotEmpty()) {
                        Log.i(TAG, "Loaded ${lines.size} lyric lines from embedded tag")
                        val result = TrackLyrics(trackUri, lines, source = "embedded")
                        memoryLyricsCache.put(trackUri, result)
                        return@withContext result
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
                            val result = TrackLyrics(trackUri, lines, source = "id3")
                            memoryLyricsCache.put(trackUri, result)
                            return@withContext result
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore header scan failure
            }
        }

        // 5. Automatic Online Fetch from LRCLIB API with confidence validation
        if (allowOnlineFetch && !title.isNullOrBlank()) {
            val fetched = fetchLyricsFromLrcLib(
                title = title,
                artist = artist ?: "",
                album = album,
                durationSeconds = durationSeconds
            )
            if (fetched != null && fetched.lines.isNotEmpty()) {
                // Save to disk cache for offline use
                cachedFile?.let {
                    try {
                        val rawContent = buildLrcText(fetched.lines)
                        it.writeText(rawContent, Charsets.UTF_8)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed caching lyrics to disk", e)
                    }
                }
                val result = fetched.copy(uri = trackUri, source = "lrclib")
                memoryLyricsCache.put(trackUri, result)
                return@withContext result
            }
        }

        val emptyResult = TrackLyrics(trackUri, emptyList())
        return@withContext emptyResult
    }

    private fun getCacheKey(title: String, artist: String): String {
        val clean = "${cleanTitle(title).lowercase()}_${cleanArtist(artist).lowercase()}"
            .replace(Regex("[^a-z0-9_]"), "_")
        return clean.take(80)
    }

    fun cleanTitle(title: String): String {
        return title
            .replace(Regex("\\.(mp3|flac|wav|m4a|aac|ogg|opus|dsf|dff)$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[\\[(].*?(feat|ft|remix|remaster|live|official|audio|mono|stereo|bonus|edit|explicit).*?[\\])]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*-\\s*(remaster|remix|live|official|bonus|edit|explicit).*?$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*[\\[(]\\d{4}[\\])]"), "") // [2024]
            .replace(Regex("^\\d{1,3}\\s*[-._]\\s*"), "") // "01 - " or "01. "
            .trim()
    }

    fun cleanArtist(artist: String): String {
        return artist
            .replace(Regex("\\s*[\\[(].*?(feat|ft).*?[\\])]", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+(feat\\.?|ft\\.?)\\s+.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex(",\\s*.*$"), "") // take primary artist before comma
            .trim()
    }

    /**
     * Queries the open-source LRCLIB API with high-confidence candidate matching.
     */
    suspend fun fetchLyricsFromLrcLib(
        title: String,
        artist: String,
        album: String?,
        durationSeconds: Double
    ): TrackLyrics? = withContext(Dispatchers.IO) {
        try {
            val cTitle = cleanTitle(title)
            val cArtist = cleanArtist(artist)
            if (cTitle.isBlank()) return@withContext null

            // 1. Direct GET request with exact parameters
            val queryParams = buildString {
                append("track_name=").append(URLEncoder.encode(cTitle, "UTF-8"))
                append("&artist_name=").append(URLEncoder.encode(cArtist, "UTF-8"))
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
                setRequestProperty("User-Agent", "Veyl-Audiophile-Player/2.0")
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

            // 2. Search Fallback with confidence scoring and duration validation
            val searchQuery = "$cTitle $cArtist".trim()
            val searchUrl = URL("https://lrclib.net/api/search?q=${URLEncoder.encode(searchQuery, "UTF-8")}")
            val searchConn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4000
                readTimeout = 4000
                setRequestProperty("User-Agent", "Veyl-Audiophile-Player/2.0")
                setRequestProperty("Accept", "application/json")
            }

            if (searchConn.responseCode == 200) {
                val responseJson = searchConn.inputStream.bufferedReader().use { it.readText() }
                searchConn.disconnect()
                val array = JSONArray(responseJson)

                if (array.length() > 0) {
                    var bestScore = -1.0
                    var bestLyrics: TrackLyrics? = null

                    for (i in 0 until array.length()) {
                        val item = array.getJSONObject(i)
                        val syncedLyrics = item.optString("syncedLyrics")
                        val plainLyrics = item.optString("plainLyrics")
                        val itemDuration = item.optDouble("duration", 0.0)
                        val itemTrack = item.optString("trackName", "")
                        val itemArtist = item.optString("artistName", "")

                        // Duration tolerance check: must be within ±5.5 seconds if duration is known
                        if (durationSeconds > 0.0 && itemDuration > 0.0) {
                            val diff = abs(itemDuration - durationSeconds)
                            if (diff > 5.5) continue // Exclude wrong version / remix / extended cut
                        }

                        var score = 0.0
                        if (!syncedLyrics.isNullOrBlank()) score += 50.0
                        if (itemTrack.equals(cTitle, ignoreCase = true)) score += 30.0
                        else if (itemTrack.contains(cTitle, ignoreCase = true)) score += 15.0

                        if (itemArtist.equals(cArtist, ignoreCase = true)) score += 20.0
                        else if (itemArtist.contains(cArtist, ignoreCase = true)) score += 10.0

                        if (score > bestScore) {
                            val candidateLyrics = if (!syncedLyrics.isNullOrBlank()) {
                                parseLrc(syncedLyrics)
                            } else if (!plainLyrics.isNullOrBlank()) {
                                parseLrc(plainLyrics)
                            } else emptyList()

                            if (candidateLyrics.isNotEmpty()) {
                                bestScore = score
                                bestLyrics = TrackLyrics(cTitle, candidateLyrics, source = "lrclib_search")
                            }
                        }
                    }

                    if (bestLyrics != null) {
                        Log.i(TAG, "LRCLIB search fallback selected best candidate with ${bestLyrics.lines.size} lines")
                        return@withContext bestLyrics
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
        var fileOffsetMs = 0L

        // First pass: extract [offset:xxx] if present in headers
        for (line in rawLines) {
            val trimmed = line.trim()
            val offsetMatcher = offsetTagPattern.matcher(trimmed)
            if (offsetMatcher.find()) {
                fileOffsetMs = offsetMatcher.group(1)?.toLongOrNull() ?: 0L
                break
            }
        }

        rawLines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            // Skip metadata headers
            if (trimmed.startsWith("[ti:") || trimmed.startsWith("[ar:") ||
                trimmed.startsWith("[al:") || trimmed.startsWith("[by:") ||
                trimmed.startsWith("[offset:") || trimmed.startsWith("[re:") ||
                trimmed.startsWith("[ve:")
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
                // Parse word-by-word timestamps inside the line if present: "<00:12.34>word<00:13.10>"
                val words = parseEnhancedLrcWords(lineBody, timestamps.first())

                // Clean text: strip word time tags while preserving natural syllable spacing
                val cleanText = if (words.isNotEmpty()) {
                    lineBody.replace(Regex("<\\d{1,3}:\\d{2}(?:\\.\\d{1,3})?>"), "").trim()
                } else {
                    fixMojibake(lineBody)
                }

                if (cleanText.isNotBlank()) {
                    val firstTs = timestamps.first()
                    for (ts in timestamps) {
                        val finalTs = (ts + fileOffsetMs).coerceAtLeast(0L)
                        val adjustedWords = if (words.isNotEmpty()) {
                            val timeDelta = (ts - firstTs) + fileOffsetMs
                            words.map {
                                it.copy(
                                    startMs = (it.startMs + timeDelta).coerceAtLeast(0L),
                                    endMs = (it.endMs + timeDelta).coerceAtLeast(0L)
                                )
                            }
                        } else emptyList()

                        parsedLines.add(
                            LyricLine(
                                timestampMs = finalTs,
                                text = cleanText,
                                words = adjustedWords
                            )
                        )
                    }
                }
            }
        }

        // If no timestamps at all, synthesize linear pacing (3.5s intervals)
        if (!hasTimestamp && parsedLines.isEmpty()) {
            rawLines.filter { it.isNotBlank() && !it.startsWith("[") }.forEachIndexed { idx, text ->
                parsedLines.add(LyricLine(idx * 3500L, text = fixMojibake(text.trim())))
            }
        }

        // Calculate end timestamps between consecutive lines for smooth word and line transitions
        val sorted = parsedLines.sortedBy { it.timestampMs }
        val withEndTimes = ArrayList<LyricLine>(sorted.size)
        for (i in sorted.indices) {
            val cur = sorted[i]
            val nextTime = if (i + 1 < sorted.size) sorted[i + 1].timestampMs else cur.timestampMs + 4000L
            val lineEnd = if (cur.words.isNotEmpty()) {
                cur.words.last().endMs.coerceAtLeast(cur.timestampMs + 400L).coerceAtMost(nextTime)
            } else {
                nextTime
            }
            withEndTimes.add(cur.copy(endTimestampMs = lineEnd))
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
            val rawWord = text.substring(curTag.second, nextStartIdx)
            val wordStr = rawWord.trim()
            val startMs = curTag.first
            val endMs = if (i + 1 < tags.size) tags[i + 1].first else startMs + 600L

            if (wordStr.isNotEmpty()) {
                words.add(LyricWord(wordStr, startMs, endMs))
            }
        }
        return words
    }

    /**
     * TTML Parser supporting word-level `<span begin="..." end="...">` tags.
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
            val currentLineText = StringBuilder()
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
                                currentLineText.setLength(0)
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
                            if (currentLineText.isNotEmpty() && !currentLineText.endsWith(" ")) {
                                currentLineText.append(" ")
                            }
                            currentLineText.append(text)
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
