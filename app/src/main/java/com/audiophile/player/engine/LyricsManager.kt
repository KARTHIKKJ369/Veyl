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

enum class LyricsProvider(val id: String, val label: String) {
    AUTO("auto", "Auto"),
    LRCLIB("lrclib", "LRCLIB"),
    YOUTUBE_MUSIC("ytmusic", "YouTube Music"),
    NETEASE("netease", "NetEase"),
    JIOSAAVN("jiosaavn", "JioSaavn"),
    LYRICS_OVH("lyrics_ovh", "Lyrics.ovh"),
    EMBEDDED("embedded", "Local / Tag");

    companion object {
        fun fromId(id: String?): LyricsProvider =
            entries.find { it.id.equals(id, ignoreCase = true) } ?: AUTO
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
        allowOnlineFetch: Boolean = true,
        preferredProvider: LyricsProvider = LyricsProvider.AUTO,
        forceRefresh: Boolean = false
    ): TrackLyrics = withContext(Dispatchers.IO) {
        val memCacheKey = "$trackUri|${preferredProvider.id}"
        if (!forceRefresh) {
            val memCached = memoryLyricsCache.get(memCacheKey)
            if (memCached != null) {
                return@withContext memCached
            }
        } else {
            memoryLyricsCache.remove(memCacheKey)
        }

        val file = File(trackUri)
        val allowLocalStages = preferredProvider == LyricsProvider.AUTO || preferredProvider == LyricsProvider.EMBEDDED

        // 1. Search for external sidecar files (.lrc, .ttml, .xml) in track directory
        if (allowLocalStages && file.exists()) {
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
                            memoryLyricsCache.put(memCacheKey, result)
                            return@withContext result
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed reading sidecar lyrics from ${candidate.path}", e)
                    }
                }
            }
        }

        val cacheKey = getCacheKey(title ?: file.nameWithoutExtension, artist ?: "")
        val cachedFile = cacheDirectory?.let {
            val suffix = if (preferredProvider == LyricsProvider.AUTO) "" else "_${preferredProvider.id}"
            File(it, "$cacheKey$suffix.lrc")
        }

        // 2. Check local disk cache (when not explicitly switching provider with forceRefresh)
        if (!forceRefresh && preferredProvider != LyricsProvider.EMBEDDED && cachedFile != null && cachedFile.exists() && cachedFile.canRead()) {
            try {
                val text = decodeBytes(cachedFile.readBytes())
                val lines = parseLrc(text)
                if (lines.isNotEmpty()) {
                    Log.i(TAG, "Loaded ${lines.size} lyric lines from disk cache for $title")
                    val result = TrackLyrics(trackUri, lines, source = "cache")
                    memoryLyricsCache.put(memCacheKey, result)
                    return@withContext result
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error reading cached lyrics", e)
            }
        }

        // 3 & 4. Extract embedded lyrics from audio file
        if (allowLocalStages && file.exists()) {
            try {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val embeddedLyrics = try {
                    retriever.extractMetadata(1000)
                } catch (e: Exception) {
                    null
                }
                retriever.release()

                if (!embeddedLyrics.isNullOrBlank()) {
                    val lines = parseLrc(fixMojibake(embeddedLyrics))
                    if (lines.isNotEmpty()) {
                        Log.i(TAG, "Loaded ${lines.size} lyric lines from embedded tag")
                        val result = TrackLyrics(trackUri, lines, source = "embedded")
                        memoryLyricsCache.put(memCacheKey, result)
                        return@withContext result
                    }
                }
            } catch (_: Exception) {
            }

            try {
                val extracted = extractEmbeddedLyrics(file)
                if (extracted != null) {
                    val (rawText, tagSource) = extracted
                    val lines = parseLrc(fixMojibake(rawText))
                    if (lines.isNotEmpty()) {
                        Log.i(TAG, "Loaded ${lines.size} lyric lines from embedded $tagSource tag")
                        val result = TrackLyrics(trackUri, lines, source = tagSource)
                        memoryLyricsCache.put(memCacheKey, result)
                        return@withContext result
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error extracting embedded lyrics from ${file.name}", e)
            }
        }

        // 5. Online Fetch across selected or fallback providers
        if (allowOnlineFetch && preferredProvider != LyricsProvider.EMBEDDED && !title.isNullOrBlank()) {
            val cleanArtistStr = artist ?: ""
            val fetched: TrackLyrics? = when (preferredProvider) {
                LyricsProvider.LRCLIB -> fetchLyricsFromLrcLib(title, cleanArtistStr, album, durationSeconds)
                LyricsProvider.YOUTUBE_MUSIC -> fetchLyricsFromYouTubeMusic(title, cleanArtistStr)
                LyricsProvider.NETEASE -> fetchLyricsFromNetEase(title, cleanArtistStr, durationSeconds)
                LyricsProvider.JIOSAAVN -> fetchLyricsFromJioSaavn(title, cleanArtistStr)
                LyricsProvider.LYRICS_OVH -> fetchLyricsFromLyricsOvh(title, cleanArtistStr)
                LyricsProvider.AUTO -> {
                    fetchLyricsFromLrcLib(title, cleanArtistStr, album, durationSeconds)
                        ?: fetchLyricsFromYouTubeMusic(title, cleanArtistStr)
                        ?: fetchLyricsFromNetEase(title, cleanArtistStr, durationSeconds)
                        ?: fetchLyricsFromJioSaavn(title, cleanArtistStr)
                        ?: fetchLyricsFromLyricsOvh(title, cleanArtistStr)
                }
                LyricsProvider.EMBEDDED -> null
            }
            if (fetched != null && fetched.lines.isNotEmpty()) {
                cachedFile?.let {
                    try {
                        val rawContent = buildLrcText(fetched.lines)
                        it.writeText(rawContent, Charsets.UTF_8)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed caching lyrics to disk", e)
                    }
                }
                val result = fetched.copy(uri = trackUri)
                memoryLyricsCache.put(memCacheKey, result)
                return@withContext result
            }
        }

        val emptyResult = TrackLyrics(trackUri, emptyList(), source = preferredProvider.id)
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

    /**
     * Extracts embedded lyrics (synced or unsynced) from MP3 (ID3v2 USLT/SYLT/TXXX),
     * FLAC (Vorbis Comment), M4A/MP4 (©lyr atom), and OGG/OPUS containers.
     */
    private fun extractEmbeddedLyrics(file: File): Pair<String, String>? {
        val ext = file.extension.lowercase()
        return when (ext) {
            "flac" -> extractFlacLyrics(file)?.let { it to "embedded" }
            "m4a", "mp4", "aac", "alac" -> extractMp4Lyrics(file)?.let { it to "embedded" }
            "mp3" -> extractId3Lyrics(file)?.let { it to "id3" }
            "ogg", "opus" -> extractOggLyrics(file)?.let { it to "embedded" }
            else -> extractId3Lyrics(file)?.let { it to "id3" }
                ?: extractFlacLyrics(file)?.let { it to "embedded" }
                ?: extractMp4Lyrics(file)?.let { it to "embedded" }
        }
    }

    private fun extractFlacLyrics(file: File): String? {
        java.io.RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(4)
            if (raf.read(magic) != 4) return null
            if (String(magic, Charsets.US_ASCII) != "fLaC") return null

            while (raf.filePointer + 4 <= raf.length()) {
                val b0 = raf.read()
                if (b0 == -1) break
                val isLast = (b0 and 0x80) != 0
                val blockType = b0 and 0x7F
                val b1 = raf.read()
                val b2 = raf.read()
                val b3 = raf.read()
                if (b1 == -1 || b2 == -1 || b3 == -1) break
                val blockSize = ((b1 and 0xFF) shl 16) or ((b2 and 0xFF) shl 8) or (b3 and 0xFF)

                if (blockType == 4 && blockSize > 8 && blockSize < 2_000_000) {
                    val data = ByteArray(blockSize)
                    raf.readFully(data)
                    val parsed = parseVorbisCommentsForLyrics(data)
                    if (parsed != null) return parsed
                } else {
                    raf.seek(raf.filePointer + blockSize)
                }
                if (isLast) break
            }
        }
        return null
    }

    private fun parseVorbisCommentsForLyrics(data: ByteArray): String? {
        if (data.size < 8) return null
        var offset = 0
        fun readLeInt(): Int {
            if (offset + 4 > data.size) return -1
            val v = (data[offset].toInt() and 0xFF) or
                    ((data[offset + 1].toInt() and 0xFF) shl 8) or
                    ((data[offset + 2].toInt() and 0xFF) shl 16) or
                    ((data[offset + 3].toInt() and 0xFF) shl 24)
            offset += 4
            return v
        }

        val vendorLen = readLeInt()
        if (vendorLen < 0 || offset + vendorLen + 4 > data.size) return null
        offset += vendorLen
        val count = readLeInt()
        if (count < 0 || count > 5000) return null

        var fallbackUnsynced: String? = null
        for (i in 0 until count) {
            val len = readLeInt()
            if (len <= 0 || offset + len > data.size) break
            val entry = String(data, offset, len, Charsets.UTF_8)
            offset += len
            val eqIdx = entry.indexOf('=')
            if (eqIdx > 0) {
                val key = entry.substring(0, eqIdx).trim().uppercase()
                val value = entry.substring(eqIdx + 1).trim()
                if (value.isNotBlank()) {
                    if (key == "SYNCEDLYRICS" || key == "LYRICS") return value
                    if (key == "UNSYNCEDLYRICS" || key == "UNSYNCHRONISEDLYRICS") {
                        fallbackUnsynced = value
                    }
                }
            }
        }
        return fallbackUnsynced
    }

    private fun extractOggLyrics(file: File): String? {
        val maxRead = 262144.coerceAtMost(file.length().toInt())
        if (maxRead <= 0) return null
        val bytes = ByteArray(maxRead)
        file.inputStream().use { it.read(bytes) }
        val keys = listOf("SYNCEDLYRICS=", "LYRICS=", "UNSYNCEDLYRICS=")
        for (key in keys) {
            val idx = indexOfSubarray(bytes, key.toByteArray(Charsets.US_ASCII))
            if (idx >= 4) {
                val len = (bytes[idx - 4].toInt() and 0xFF) or
                        ((bytes[idx - 3].toInt() and 0xFF) shl 8) or
                        ((bytes[idx - 2].toInt() and 0xFF) shl 16) or
                        ((bytes[idx - 1].toInt() and 0xFF) shl 24)
                if (len in key.length..500_000 && idx + len <= bytes.size) {
                    val full = String(bytes, idx, len, Charsets.UTF_8)
                    val value = full.substringAfter('=', "").trim()
                    if (value.isNotBlank()) return value
                }
            }
        }
        return null
    }

    private fun extractId3Lyrics(file: File): String? {
        java.io.RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 10) return null
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return null
            }
            val version = header[3].toInt() and 0xFF
            val flags = header[5].toInt() and 0xFF
            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)

            val readLimit = tagSize.coerceAtMost(8_000_000)
            if (readLimit <= 0) return null
            val tagData = ByteArray(readLimit)
            raf.readFully(tagData)

            var pos = 0
            // Skip extended header if present
            if ((flags and 0x40) != 0 && tagData.size >= 4) {
                val extSize = if (version == 4) {
                    ((tagData[0].toInt() and 0x7F) shl 21) or
                            ((tagData[1].toInt() and 0x7F) shl 14) or
                            ((tagData[2].toInt() and 0x7F) shl 7) or
                            (tagData[3].toInt() and 0x7F)
                } else {
                    4 + (((tagData[0].toInt() and 0xFF) shl 24) or
                            ((tagData[1].toInt() and 0xFF) shl 16) or
                            ((tagData[2].toInt() and 0xFF) shl 8) or
                            (tagData[3].toInt() and 0xFF))
                }
                pos = extSize.coerceAtLeast(0)
            }

            var fallbackUslt: String? = null
            while (pos + 10 <= tagData.size) {
                if (tagData[pos] == 0.toByte()) break // Padding
                val frameId = String(tagData, pos, 4, Charsets.US_ASCII)
                val frameSize = if (version == 4) {
                    ((tagData[pos + 4].toInt() and 0x7F) shl 21) or
                            ((tagData[pos + 5].toInt() and 0x7F) shl 14) or
                            ((tagData[pos + 6].toInt() and 0x7F) shl 7) or
                            (tagData[pos + 7].toInt() and 0x7F)
                } else {
                    ((tagData[pos + 4].toInt() and 0xFF) shl 24) or
                            ((tagData[pos + 5].toInt() and 0xFF) shl 16) or
                            ((tagData[pos + 6].toInt() and 0xFF) shl 8) or
                            (tagData[pos + 7].toInt() and 0xFF)
                }
                pos += 10
                if (frameSize <= 0 || pos + frameSize > tagData.size) break

                when (frameId) {
                    "SYLT" -> {
                        val sylt = parseId3Sylt(tagData, pos, frameSize)
                        if (!sylt.isNullOrBlank()) return sylt
                    }
                    "USLT" -> {
                        val uslt = parseId3Uslt(tagData, pos, frameSize)
                        if (!uslt.isNullOrBlank()) fallbackUslt = uslt
                    }
                    "TXXX" -> {
                        val txxx = parseId3TxxxLyrics(tagData, pos, frameSize)
                        if (!txxx.isNullOrBlank() && fallbackUslt == null) fallbackUslt = txxx
                    }
                }
                pos += frameSize
            }
            return fallbackUslt
        }
    }

    private fun id3Charset(encByte: Int): java.nio.charset.Charset = when (encByte) {
        1 -> Charsets.UTF_16
        2 -> Charsets.UTF_16BE
        3 -> Charsets.UTF_8
        else -> Charsets.ISO_8859_1
    }

    private fun findId3StringEnd(data: ByteArray, start: Int, end: Int, encByte: Int): Int {
        val isUtf16 = encByte == 1 || encByte == 2
        var i = start
        if (isUtf16) {
            while (i + 1 < end) {
                if (data[i] == 0.toByte() && data[i + 1] == 0.toByte()) return i
                i += 2
            }
        } else {
            while (i < end) {
                if (data[i] == 0.toByte()) return i
                i++
            }
        }
        return end
    }

    private fun parseId3Uslt(data: ByteArray, offset: Int, size: Int): String? {
        if (size < 5) return null
        val enc = data[offset].toInt() and 0xFF
        val descStart = offset + 4
        val end = offset + size
        val descEnd = findId3StringEnd(data, descStart, end, enc)
        val sepLen = if (enc == 1 || enc == 2) 2 else 1
        val textStart = (descEnd + sepLen).coerceAtMost(end)
        if (textStart >= end) return null
        return String(data, textStart, end - textStart, id3Charset(enc)).trim('\u0000', ' ', '\r', '\n')
    }

    private fun parseId3TxxxLyrics(data: ByteArray, offset: Int, size: Int): String? {
        if (size < 3) return null
        val enc = data[offset].toInt() and 0xFF
        val descStart = offset + 1
        val end = offset + size
        val descEnd = findId3StringEnd(data, descStart, end, enc)
        val charset = id3Charset(enc)
        val desc = String(data, descStart, (descEnd - descStart).coerceAtLeast(0), charset).trim().uppercase()
        if (desc != "LYRICS" && desc != "UNSYNCEDLYRICS" && desc != "SYNCEDLYRICS") return null
        val sepLen = if (enc == 1 || enc == 2) 2 else 1
        val valStart = (descEnd + sepLen).coerceAtMost(end)
        if (valStart >= end) return null
        return String(data, valStart, end - valStart, charset).trim('\u0000', ' ', '\r', '\n')
    }

    private fun parseId3Sylt(data: ByteArray, offset: Int, size: Int): String? {
        if (size < 7) return null
        val enc = data[offset].toInt() and 0xFF
        val tsFormat = data[offset + 4].toInt() and 0xFF
        if (tsFormat != 2) return null // 2 = milliseconds
        val descStart = offset + 6
        val end = offset + size
        val sepLen = if (enc == 1 || enc == 2) 2 else 1
        val descEnd = findId3StringEnd(data, descStart, end, enc)
        var cursor = (descEnd + sepLen).coerceAtMost(end)
        val charset = id3Charset(enc)
        val sb = StringBuilder()

        while (cursor < end) {
            val strEnd = findId3StringEnd(data, cursor, end, enc)
            val text = String(data, cursor, (strEnd - cursor).coerceAtLeast(0), charset).replace("\n", " ").trim()
            cursor = strEnd + sepLen
            if (cursor + 4 > end) break
            val ms = ((data[cursor].toLong() and 0xFF) shl 24) or
                    ((data[cursor + 1].toLong() and 0xFF) shl 16) or
                    ((data[cursor + 2].toLong() and 0xFF) shl 8) or
                    (data[cursor + 3].toLong() and 0xFF)
            cursor += 4
            if (text.isNotEmpty()) {
                val min = ms / 60000
                val sec = (ms % 60000) / 1000
                val cs = (ms % 1000) / 10
                sb.append("[%02d:%02d.%02d]%s\n".format(min, sec, cs, text))
            }
        }
        return sb.toString().takeIf { it.isNotBlank() }
    }

    private fun extractMp4Lyrics(file: File): String? {
        java.io.RandomAccessFile(file, "r").use { raf ->
            fun readUint32BE(): Long {
                val b0 = raf.read()
                val b1 = raf.read()
                val b2 = raf.read()
                val b3 = raf.read()
                if (b0 == -1 || b1 == -1 || b2 == -1 || b3 == -1) return -1L
                return ((b0.toLong() and 0xFF) shl 24) or
                        ((b1.toLong() and 0xFF) shl 16) or
                        ((b2.toLong() and 0xFF) shl 8) or
                        (b3.toLong() and 0xFF)
            }

            fun findAtom(start: Long, end: Long, targetType: ByteArray): Pair<Long, Long>? {
                raf.seek(start)
                while (raf.filePointer + 8 <= end) {
                    val atomStart = raf.filePointer
                    var size = readUint32BE()
                    val type = ByteArray(4)
                    if (raf.read(type) != 4) break
                    var headerSize = 8L
                    if (size == 1L && raf.filePointer + 8 <= end) {
                        val hi = readUint32BE()
                        val lo = readUint32BE()
                        size = (hi shl 32) or lo
                        headerSize = 16L
                    } else if (size == 0L) {
                        size = end - atomStart
                    }
                    if (size < headerSize) break
                    val atomEnd = (atomStart + size).coerceAtMost(end)
                    if (type.contentEquals(targetType)) {
                        return (atomStart + headerSize) to atomEnd
                    }
                    raf.seek(atomEnd)
                }
                return null
            }

            val fileLen = raf.length()
            val moov = findAtom(0L, fileLen, "moov".toByteArray(Charsets.US_ASCII)) ?: return null
            val udta = findAtom(moov.first, moov.second, "udta".toByteArray(Charsets.US_ASCII)) ?: return null
            val meta = findAtom(udta.first, udta.second, "meta".toByteArray(Charsets.US_ASCII)) ?: return null
            // 'meta' is a FullBox with 4 bytes of version/flags before children
            val ilst = findAtom(meta.first + 4, meta.second, "ilst".toByteArray(Charsets.US_ASCII)) ?: return null
            val lyrAtomType = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
            val lyr = findAtom(ilst.first, ilst.second, lyrAtomType) ?: return null
            val dataAtom = findAtom(lyr.first, lyr.second, "data".toByteArray(Charsets.US_ASCII)) ?: return null
            val textStart = dataAtom.first + 8
            val textLen = (dataAtom.second - textStart).toInt()
            if (textLen <= 0 || textLen > 500_000) return null
            raf.seek(textStart)
            val textBytes = ByteArray(textLen)
            raf.readFully(textBytes)
            return String(textBytes, Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
        }
    }

    /**
     * Fetches synchronized or plain lyrics from YouTube Music via public InnerTube API.
     */
    suspend fun fetchLyricsFromYouTubeMusic(
        title: String,
        artist: String
    ): TrackLyrics? = withContext(Dispatchers.IO) {
        try {
            val cTitle = cleanTitle(title)
            val cArtist = cleanArtist(artist)
            if (cTitle.isBlank()) return@withContext null

            fun postInnertube(endpoint: String, payload: JSONObject): String? {
                val clientObj = JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20240918.01.00")
                    put("hl", "en")
                    put("gl", "US")
                }
                payload.put("context", JSONObject().put("client", clientObj))

                val url = URL("https://music.youtube.com/youtubei/v1/$endpoint?prettyPrint=false")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doOutput = true
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                    setRequestProperty("Origin", "https://music.youtube.com")
                    setRequestProperty("Referer", "https://music.youtube.com/")
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/128.0.0.0 Safari/537.36")
                }
                conn.outputStream.use { os ->
                    os.write(payload.toString().toByteArray(Charsets.UTF_8))
                }
                val code = conn.responseCode
                if (code != 200) {
                    conn.disconnect()
                    return null
                }
                return conn.inputStream.bufferedReader().use { it.readText() }.also {
                    conn.disconnect()
                }
            }

            fun findFirstStringByKey(node: Any?, targetKey: String, prefixFilter: String? = null): String? {
                when (node) {
                    is JSONObject -> {
                        if (node.has(targetKey)) {
                            val v = node.optString(targetKey)
                            if (v.isNotBlank() && (prefixFilter == null || v.startsWith(prefixFilter))) {
                                return v
                            }
                        }
                        val keys = node.keys()
                        while (keys.hasNext()) {
                            val hit = findFirstStringByKey(node.opt(keys.next()), targetKey, prefixFilter)
                            if (hit != null) return hit
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until node.length()) {
                            val hit = findFirstStringByKey(node.opt(i), targetKey, prefixFilter)
                            if (hit != null) return hit
                        }
                    }
                }
                return null
            }

            fun findArrayByKey(node: Any?, targetKey: String): JSONArray? {
                when (node) {
                    is JSONObject -> {
                        if (node.has(targetKey)) {
                            node.optJSONArray(targetKey)?.let { return it }
                        }
                        val keys = node.keys()
                        while (keys.hasNext()) {
                            val hit = findArrayByKey(node.opt(keys.next()), targetKey)
                            if (hit != null) return hit
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until node.length()) {
                            val hit = findArrayByKey(node.opt(i), targetKey)
                            if (hit != null) return hit
                        }
                    }
                }
                return null
            }

            fun findObjectByKey(node: Any?, targetKey: String): JSONObject? {
                when (node) {
                    is JSONObject -> {
                        if (node.has(targetKey)) {
                            node.optJSONObject(targetKey)?.let { return it }
                        }
                        val keys = node.keys()
                        while (keys.hasNext()) {
                            val hit = findObjectByKey(node.opt(keys.next()), targetKey)
                            if (hit != null) return hit
                        }
                    }
                    is JSONArray -> {
                        for (i in 0 until node.length()) {
                            val hit = findObjectByKey(node.opt(i), targetKey)
                            if (hit != null) return hit
                        }
                    }
                }
                return null
            }

            // Step 1: Search YouTube Music Songs for videoId
            val searchPayload = JSONObject().apply {
                put("query", "$cTitle $cArtist".trim())
                put("params", "EgWKAQIIAWoMEAMQBBAJEAoQBRAV")
            }
            val searchRaw = postInnertube("search", searchPayload) ?: return@withContext null
            val videoId = findFirstStringByKey(JSONObject(searchRaw), "videoId") ?: return@withContext null

            // Step 2: Fetch Watch / Next tabs to locate Lyrics browseId (MPLYt...)
            val nextPayload = JSONObject().apply {
                put("videoId", videoId)
            }
            val nextRaw = postInnertube("next", nextPayload) ?: return@withContext null
            val browseId = findFirstStringByKey(JSONObject(nextRaw), "browseId", prefixFilter = "MPLYt")
                ?: return@withContext null

            // Step 3: Browse Lyrics endpoint
            val browsePayload = JSONObject().apply {
                put("browseId", browseId)
            }
            val browseRaw = postInnertube("browse", browsePayload) ?: return@withContext null
            val browseRoot = JSONObject(browseRaw)

            // Try synchronized timed lyrics first
            val timedArr = findArrayByKey(browseRoot, "timedLyricsData")
            if (timedArr != null && timedArr.length() > 0) {
                val lines = mutableListOf<LyricLine>()
                for (i in 0 until timedArr.length()) {
                    val item = timedArr.optJSONObject(i) ?: continue
                    val lineText = item.optString("lyricLine").trim()
                    val cue = item.optJSONObject("cueRange")
                    val startMs = cue?.optString("startTimeMilliseconds")?.toLongOrNull() ?: 0L
                    val endMs = cue?.optString("endTimeMilliseconds")?.toLongOrNull()
                    if (lineText.isNotEmpty()) {
                        lines.add(LyricLine(timestampMs = startMs, endTimestampMs = endMs, text = lineText))
                    }
                }
                if (lines.isNotEmpty()) {
                    return@withContext TrackLyrics(cTitle, lines.sortedBy { it.timestampMs }, source = "ytmusic")
                }
            }

            // Fallback to plain YouTube Music description shelf text
            val descShelf = findObjectByKey(browseRoot, "musicDescriptionShelfRenderer")
            val runs = descShelf?.optJSONObject("description")?.optJSONArray("runs")
            if (runs != null && runs.length() > 0) {
                val sb = StringBuilder()
                for (i in 0 until runs.length()) {
                    sb.append(runs.optJSONObject(i)?.optString("text").orEmpty())
                }
                val plainText = sb.toString().trim()
                if (plainText.isNotBlank()) {
                    val lines = parseLrc(plainText)
                    if (lines.isNotEmpty()) {
                        return@withContext TrackLyrics(cTitle, lines, source = "ytmusic")
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.v(TAG, "YouTube Music lyrics fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Fetches synchronized or plain lyrics from NetEase Cloud Music.
     */
    suspend fun fetchLyricsFromNetEase(
        title: String,
        artist: String,
        durationSeconds: Double
    ): TrackLyrics? = withContext(Dispatchers.IO) {
        try {
            val cTitle = cleanTitle(title)
            val cArtist = cleanArtist(artist)
            if (cTitle.isBlank()) return@withContext null

            val q = URLEncoder.encode("$cTitle $cArtist".trim(), "UTF-8")
            val searchUrl = URL("https://music.163.com/api/search/get?type=1&limit=6&s=$q")
            val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("Referer", "https://music.163.com/")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val songs = JSONObject(body).optJSONObject("result")?.optJSONArray("songs") ?: return@withContext null
            var bestSongId = -1L
            var bestScore = -1.0
            for (i in 0 until songs.length()) {
                val song = songs.optJSONObject(i) ?: continue
                val id = song.optLong("id", -1L)
                if (id <= 0L) continue
                val durSec = song.optDouble("duration", 0.0) / 1000.0
                if (durationSeconds > 0.0 && durSec > 0.0 && abs(durSec - durationSeconds) > 8.0) continue

                var score = 0.0
                val name = song.optString("name", "")
                if (name.equals(cTitle, ignoreCase = true)) score += 30.0
                else if (name.contains(cTitle, ignoreCase = true)) score += 15.0
                val artistsArr = song.optJSONArray("artists")
                if (artistsArr != null && cArtist.isNotBlank()) {
                    for (j in 0 until artistsArr.length()) {
                        val aName = artistsArr.optJSONObject(j)?.optString("name", "").orEmpty()
                        if (aName.equals(cArtist, ignoreCase = true)) score += 20.0
                        else if (aName.contains(cArtist, ignoreCase = true)) score += 10.0
                    }
                }
                if (score > bestScore) {
                    bestScore = score
                    bestSongId = id
                }
            }
            if (bestSongId <= 0L) return@withContext null

            val lrcUrl = URL("https://music.163.com/api/song/lyric?id=$bestSongId&lv=1&kv=1&tv=-1")
            val lrcConn = (lrcUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("Referer", "https://music.163.com/")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            }
            if (lrcConn.responseCode != 200) {
                lrcConn.disconnect()
                return@withContext null
            }
            val lrcJson = lrcConn.inputStream.bufferedReader().use { it.readText() }
            lrcConn.disconnect()

            val rawLrc = JSONObject(lrcJson).optJSONObject("lrc")?.optString("lyric").orEmpty()
            if (rawLrc.isBlank()) return@withContext null
            val lines = parseLrc(rawLrc)
            if (lines.isNotEmpty()) TrackLyrics(cTitle, lines, source = "netease") else null
        } catch (e: Exception) {
            Log.v(TAG, "NetEase fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Fetches lyrics from JioSaavn (strong coverage for Tamil, Hindi, Malayalam, Telugu, and Indian catalogs).
     */
    suspend fun fetchLyricsFromJioSaavn(
        title: String,
        artist: String
    ): TrackLyrics? = withContext(Dispatchers.IO) {
        try {
            val cTitle = cleanTitle(title)
            val cArtist = cleanArtist(artist)
            if (cTitle.isBlank()) return@withContext null

            val q = URLEncoder.encode("$cTitle $cArtist".trim(), "UTF-8")
            val searchUrl = URL("https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&cc=in&includeMetaTags=1&query=$q")
            val conn = (searchUrl.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()

            val songsArr = JSONObject(body).optJSONObject("songs")?.optJSONArray("data") ?: return@withContext null
            for (i in 0 until minOf(songsArr.length(), 4)) {
                val songObj = songsArr.optJSONObject(i) ?: continue
                val songId = songObj.optString("id").takeIf { it.isNotBlank() } ?: continue
                val lyrUrl = URL("https://www.jiosaavn.com/api.php?__call=lyrics.get&lyrics_id=$songId&_format=json&_marker=0")
                val lyrConn = (lyrUrl.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 4000
                    readTimeout = 4000
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36")
                }
                if (lyrConn.responseCode == 200) {
                    val lyrJson = lyrConn.inputStream.bufferedReader().use { it.readText() }
                    lyrConn.disconnect()
                    val rawHtml = JSONObject(lyrJson).optString("lyrics")
                    if (rawHtml.isNotBlank()) {
                        val cleaned = rawHtml
                            .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                            .replace(Regex("<[^>]+>"), "")
                            .replace("&amp;", "&")
                            .replace("&quot;", "\"")
                            .replace("&#39;", "'")
                            .trim()
                        val lines = parseLrc(cleaned)
                        if (lines.isNotEmpty()) {
                            return@withContext TrackLyrics(cTitle, lines, source = "jiosaavn")
                        }
                    }
                } else {
                    lyrConn.disconnect()
                }
            }
            null
        } catch (e: Exception) {
            Log.v(TAG, "JioSaavn fetch failed: ${e.message}")
            null
        }
    }

    /**
     * Fetches plain lyrics fallback from Lyrics.ovh public API.
     */
    suspend fun fetchLyricsFromLyricsOvh(
        title: String,
        artist: String
    ): TrackLyrics? = withContext(Dispatchers.IO) {
        try {
            val cTitle = cleanTitle(title)
            val cArtist = cleanArtist(artist)
            if (cTitle.isBlank() || cArtist.isBlank()) return@withContext null

            val encodedArtist = URLEncoder.encode(cArtist, "UTF-8").replace("+", "%20")
            val encodedTitle = URLEncoder.encode(cTitle, "UTF-8").replace("+", "%20")
            val url = URL("https://api.lyrics.ovh/v1/$encodedArtist/$encodedTitle")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 4500
                readTimeout = 4500
                setRequestProperty("User-Agent", "Veyl-Audiophile-Player/2.0")
            }
            if (conn.responseCode != 200) {
                conn.disconnect()
                return@withContext null
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            val rawLyrics = JSONObject(body).optString("lyrics").trim()
            if (rawLyrics.isBlank()) return@withContext null
            val lines = parseLrc(rawLyrics)
            if (lines.isNotEmpty()) TrackLyrics(cTitle, lines, source = "lyrics_ovh") else null
        } catch (e: Exception) {
            Log.v(TAG, "Lyrics.ovh fetch failed: ${e.message}")
            null
        }
    }

    fun hasIndicScript(text: String): Boolean {
        return text.any { ch ->
            val code = ch.code
            code in 0x0900..0x0D7F
        }
    }

    /**
     * On-device Indic script romanizer (Tamil, Malayalam, Devanagari/Hindi, Telugu, Kannada, Bengali, Gujarati)
     * preserving word & line boundaries so non-native readers can sing along to synced lines.
     */
    fun romanizeText(input: String): String {
        if (input.isBlank() || !hasIndicScript(input)) return input
        val normalized = java.text.Normalizer.normalize(input, java.text.Normalizer.Form.NFC)
        val out = StringBuilder(normalized.length * 2)
        var hasInherentA = false

        fun dropInherentA() {
            if (hasInherentA && out.isNotEmpty() && out[out.length - 1] == 'a') {
                out.deleteCharAt(out.length - 1)
            }
            hasInherentA = false
        }

        for (ch in normalized) {
            val code = ch.code
            val blockBase = when {
                code in 0x0900..0x097F -> 0x0900 // Devanagari
                code in 0x0980..0x09FF -> 0x0980 // Bengali
                code in 0x0A80..0x0AFF -> 0x0A80 // Gujarati
                code in 0x0B80..0x0BFF -> 0x0B80 // Tamil
                code in 0x0C00..0x0C7F -> 0x0C00 // Telugu
                code in 0x0C80..0x0CFF -> 0x0C80 // Kannada
                code in 0x0D00..0x0D7F -> 0x0D00 // Malayalam
                else -> -1
            }
            if (blockBase == -1) {
                hasInherentA = false
                out.append(ch)
                continue
            }

            // Malayalam chillu consonants (no inherent 'a')
            when (code) {
                0x0D7A -> { hasInherentA = false; out.append("n"); continue }
                0x0D7B -> { hasInherentA = false; out.append("n"); continue }
                0x0D7C -> { hasInherentA = false; out.append("r"); continue }
                0x0D7D -> { hasInherentA = false; out.append("l"); continue }
                0x0D7E -> { hasInherentA = false; out.append("l"); continue }
                0x0D7F -> { hasInherentA = false; out.append("k"); continue }
            }

            val off = code - blockBase
            // Digits 0x66..0x6F
            if (off in 0x66..0x6F) {
                hasInherentA = false
                out.append(('0'.code + (off - 0x66)).toChar())
                continue
            }

            when (off) {
                0x01, 0x02 -> { hasInherentA = false; out.append("m") } // Chandrabindu / Anusvara
                0x03 -> { hasInherentA = false; out.append(if (blockBase == 0x0B80) "h" else "h") } // Visarga / Aytham
                0x05 -> { hasInherentA = false; out.append("a") }
                0x06 -> { hasInherentA = false; out.append("aa") }
                0x07 -> { hasInherentA = false; out.append("i") }
                0x08 -> { hasInherentA = false; out.append("ee") }
                0x09 -> { hasInherentA = false; out.append("u") }
                0x0A -> { hasInherentA = false; out.append("oo") }
                0x0B -> { hasInherentA = false; out.append("ri") }
                0x0E -> { hasInherentA = false; out.append("e") }
                0x0F -> { hasInherentA = false; out.append("e") }
                0x10 -> { hasInherentA = false; out.append("ai") }
                0x12 -> { hasInherentA = false; out.append("o") }
                0x13 -> { hasInherentA = false; out.append("o") }
                0x14 -> { hasInherentA = false; out.append("au") }

                // Consonants with inherent 'a'
                0x15 -> { out.append("ka"); hasInherentA = true }
                0x16 -> { out.append("kha"); hasInherentA = true }
                0x17 -> { out.append("ga"); hasInherentA = true }
                0x18 -> { out.append("gha"); hasInherentA = true }
                0x19 -> { out.append("nga"); hasInherentA = true }
                0x1A -> { out.append("cha"); hasInherentA = true }
                0x1B -> { out.append("chha"); hasInherentA = true }
                0x1C -> { out.append("ja"); hasInherentA = true }
                0x1D -> { out.append("jha"); hasInherentA = true }
                0x1E -> { out.append("nya"); hasInherentA = true }
                0x1F -> { out.append("ta"); hasInherentA = true }
                0x20 -> { out.append("tha"); hasInherentA = true }
                0x21 -> { out.append("da"); hasInherentA = true }
                0x22 -> { out.append("dha"); hasInherentA = true }
                0x23 -> { out.append("na"); hasInherentA = true }
                0x24 -> { out.append("tha"); hasInherentA = true }
                0x25 -> { out.append("tha"); hasInherentA = true }
                0x26 -> { out.append("da"); hasInherentA = true }
                0x27 -> { out.append("dha"); hasInherentA = true }
                0x28 -> { out.append("na"); hasInherentA = true }
                0x29 -> { out.append("na"); hasInherentA = true }
                0x2A -> { out.append("pa"); hasInherentA = true }
                0x2B -> { out.append("pha"); hasInherentA = true }
                0x2C -> { out.append("ba"); hasInherentA = true }
                0x2D -> { out.append("bha"); hasInherentA = true }
                0x2E -> { out.append("ma"); hasInherentA = true }
                0x2F -> { out.append("ya"); hasInherentA = true }
                0x30 -> { out.append("ra"); hasInherentA = true }
                0x31 -> { out.append("ra"); hasInherentA = true }
                0x32 -> { out.append("la"); hasInherentA = true }
                0x33 -> { out.append("la"); hasInherentA = true }
                0x34 -> { out.append("zha"); hasInherentA = true }
                0x35 -> { out.append("va"); hasInherentA = true }
                0x36 -> { out.append("sha"); hasInherentA = true }
                0x37 -> { out.append("sha"); hasInherentA = true }
                0x38 -> { out.append("sa"); hasInherentA = true }
                0x39 -> { out.append("ha"); hasInherentA = true }

                // Dependent vowel signs (suppress inherent 'a')
                0x3E -> { dropInherentA(); out.append("aa") }
                0x3F -> { dropInherentA(); out.append("i") }
                0x40 -> { dropInherentA(); out.append("ee") }
                0x41 -> { dropInherentA(); out.append("u") }
                0x42 -> { dropInherentA(); out.append("oo") }
                0x43, 0x44 -> { dropInherentA(); out.append("ri") }
                0x46 -> { dropInherentA(); out.append("e") }
                0x47 -> { dropInherentA(); out.append("e") }
                0x48 -> { dropInherentA(); out.append("ai") }
                0x4A -> { dropInherentA(); out.append("o") }
                0x4B -> { dropInherentA(); out.append("o") }
                0x4C -> { dropInherentA(); out.append("au") }
                0x4D -> { dropInherentA() } // Virama / Pulli / Halant
                0x57 -> { dropInherentA(); out.append("au") } // Length mark in Tamil/Malayalam au
                else -> { hasInherentA = false }
            }
        }
        return out.toString()
    }
}
