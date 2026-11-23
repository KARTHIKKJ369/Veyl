package com.audiophile.player.online

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import uniffi.audiophile_core.TrackInfo
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class TrackMetadataCandidate(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val year: UInt?,
    val artworkUrl: String?,
    val durationMs: Long?,
    val source: String
)

object OnlineTrackIdentifier {
    private const val TAG = "OnlineTrackIdentifier"
    private const val ITUNES_ENDPOINT = "https://itunes.apple.com/search?media=music&entity=song&limit=15&term="

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Cleans an unorganized filename or existing track metadata to generate
     * a clean, effective search query (like Jellyfin's Identify auto-fill).
     *
     * Example: "01 - Anirudh - Badass (Leo) [320kbps].m4a" -> "Anirudh Badass Leo"
     */
    fun cleanInitialQuery(track: TrackInfo): String {
        val title = track.title.trim()
        val isGenericTitle = title.isBlank() ||
                title.equals("Unknown", ignoreCase = true) ||
                title.equals("Unknown Track", ignoreCase = true) ||
                title.equals("Unknown Title", ignoreCase = true) ||
                title.matches(Regex("^(track|song|audio|file|untitled|audiotrack)[_\\-\\s]*\\d*$", RegexOption.IGNORE_CASE))

        val raw = if (!isGenericTitle) {
            val artist = track.artist.takeIf {
                !it.equals("Unknown Artist", ignoreCase = true) &&
                !it.equals("<unknown>", ignoreCase = true) &&
                it.isNotBlank()
            }
            if (artist != null) "$title $artist" else title
        } else {
            val fName = File(track.uri).nameWithoutExtension
            fName
        }

        return raw
            .replace(Regex("^\\d+[._\\-\\s]+"), "") // leading "01 - " or "02. "
            .replace(Regex("(?i)\\b(128kbps|320kbps|320k|128k|kbps|hq|flac|mp3|m4a|aac|opus|wav|dts|remastered)\\b"), "")
            .replace(Regex("(?i)\\b(official\\s*(video|audio|music\\s*video|lyric\\s*video)?)\\b"), "")
            .replace(Regex("[\\[\\(].*?[\\]\\)]"), " ") // remove text inside brackets
            .replace(Regex("[_\\-]+"), " ") // underscores and dashes to space
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Searches both JioSaavn and Apple Music simultaneously and returns a merged,
     * de-duplicated list of candidates with high-resolution artwork and studio metadata.
     */
    suspend fun searchCandidates(query: String): List<TrackMetadataCandidate> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        coroutineScope {
            val saavnDeferred = async { searchJioSaavn(trimmed) }
            val itunesDeferred = async { searchITunes(trimmed) }

            val saavnResults = try { saavnDeferred.await() } catch (e: Exception) {
                Log.w(TAG, "JioSaavn search failed", e)
                emptyList()
            }
            val itunesResults = try { itunesDeferred.await() } catch (e: Exception) {
                Log.w(TAG, "iTunes search failed", e)
                emptyList()
            }

            // Merge and de-duplicate by normalized title + artist
            val merged = mutableListOf<TrackMetadataCandidate>()
            val seenKeys = mutableSetOf<String>()

            fun addCandidate(item: TrackMetadataCandidate) {
                val normTitle = item.title.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                val normArtist = item.artist.replace(Regex("[^a-zA-Z0-9]"), "").lowercase().take(8)
                val key = "$normTitle|$normArtist"
                if (normTitle.length >= 3 && seenKeys.add(key)) {
                    merged.add(item)
                } else if (normTitle.length < 3 && seenKeys.add(item.id)) {
                    merged.add(item)
                }
            }

            // Add JioSaavn results first (rich regional and Bollywood metadata)
            for (item in saavnResults) {
                addCandidate(item)
            }

            // Add Apple Music / iTunes results (studio worldwide catalog with high-res artwork)
            for (item in itunesResults) {
                addCandidate(item)
            }

            merged
        }
    }

    private suspend fun searchJioSaavn(query: String): List<TrackMetadataCandidate> {
        val songs = JioSaavnService.searchSongs(query)
        return songs.map { song ->
            val highResArt = song.thumbnailUrl
                .replace("150x150", "500x500")
                .replace("50x50", "500x500")

            TrackMetadataCandidate(
                id = "saavn_${song.id}",
                title = song.title,
                artist = song.artist,
                album = song.album.ifBlank { song.title },
                year = null,
                artworkUrl = highResArt.takeIf { it.isNotBlank() },
                durationMs = (song.durationSeconds * 1000.0).toLong(),
                source = "JioSaavn"
            )
        }
    }

    private fun searchITunes(query: String): List<TrackMetadataCandidate> {
        return try {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val request = Request.Builder()
                .url(ITUNES_ENDPOINT + encoded)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val body = response.body?.string() ?: return emptyList()
                val json = JSONObject(body)
                val resultsArr = json.optJSONArray("results") ?: return emptyList()

                val list = mutableListOf<TrackMetadataCandidate>()
                for (i in 0 until resultsArr.length()) {
                    val obj = resultsArr.optJSONObject(i) ?: continue
                    val trackName = obj.optString("trackName")
                    val artistName = obj.optString("artistName")
                    if (trackName.isBlank() || artistName.isBlank()) continue

                    val collectionName = obj.optString("collectionName", trackName)
                    val releaseDate = obj.optString("releaseDate") // e.g. "2017-01-06T08:00:00Z"
                    val year = if (releaseDate.length >= 4) releaseDate.substring(0, 4).toUIntOrNull() else null
                    val rawArt = obj.optString("artworkUrl100")
                    val highResArt = rawArt.replace("100x100bb.jpg", "600x600bb.jpg")
                    val durationMs = obj.optLong("trackTimeMillis", 0L).takeIf { it > 0 }

                    list.add(
                        TrackMetadataCandidate(
                            id = "itunes_${obj.optLong("trackId", i.toLong())}",
                            title = trackName,
                            artist = artistName,
                            album = collectionName,
                            year = year,
                            artworkUrl = highResArt.takeIf { it.isNotBlank() },
                            durationMs = durationMs,
                            source = "Apple Music"
                        )
                    )
                }
                list
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error in iTunes search for '$query'", e)
            emptyList()
        }
    }

    /**
     * Downloads raw JPEG or PNG cover art bytes from the chosen artwork URL.
     */
    suspend fun downloadCoverBytes(artworkUrl: String): ByteArray? = withContext(Dispatchers.IO) {
        if (artworkUrl.isBlank()) return@withContext null
        try {
            val request = Request.Builder()
                .url(artworkUrl)
                .header("User-Agent", "Mozilla/5.0")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    response.body?.bytes()
                } else null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed downloading artwork from $artworkUrl", e)
            null
        }
    }
}
