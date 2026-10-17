package com.audiophile.player.online

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class SpotifyTrack(
    val title: String,
    val artist: String,
    val durationSeconds: Double,
    val previewUrl: String? = null
)

data class SpotifyPlaylist(
    val title: String,
    val tracks: List<SpotifyTrack>
)

/**
 * Universal, zero-credential Spotify playlist and album importer.
 * Extracts playlist titles and tracks directly from Spotify's open web embeds.
 */
object SpotifyPlaylistImporter {
    private const val TAG = "SpotifyImporter"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val SPOTIFY_ID_REGEX = Pattern.compile("""(?:playlist|album)[/:]([a-zA-Z0-9]+)""")

    fun extractIdAndType(input: String): Pair<String, String>? {
        val trimmed = input.trim()
        val matcher = SPOTIFY_ID_REGEX.matcher(trimmed)
        if (matcher.find()) {
            val id = matcher.group(1) ?: return null
            val isAlbum = trimmed.contains("album")
            return id to (if (isAlbum) "album" else "playlist")
        }
        // If user just pasted a 22-char Spotify alphanumeric ID
        if (trimmed.matches(Regex("^[a-zA-Z0-9]{20,24}$"))) {
            return trimmed to "playlist"
        }
        return null
    }

    suspend fun fetchPlaylist(url: String): Result<SpotifyPlaylist> = withContext(Dispatchers.IO) {
        runCatching {
            val (id, type) = extractIdAndType(url)
                ?: throw IllegalArgumentException("Invalid Spotify link. Please paste a link to a Spotify playlist or album (e.g. https://open.spotify.com/playlist/...).")

            val embedUrl = "https://open.spotify.com/embed/$type/$id"
            val request = Request.Builder()
                .url(embedUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                throw IllegalStateException("Spotify returned status ${response.code}. Please ensure the playlist is public.")
            }

            val html = response.body?.string() ?: throw IllegalStateException("Empty response from Spotify")

            // Locate script containing NEXT_DATA or initial-state
            val scriptPattern = Pattern.compile("""<script id="(?:__NEXT_DATA__|initial-state)"[^>]*>(.*?)</script>""", Pattern.DOTALL)
            val scriptMatcher = scriptPattern.matcher(html)

            if (!scriptMatcher.find()) {
                throw IllegalStateException("Could not extract tracks from Spotify. Please check that the playlist is public.")
            }

            val jsonContent = scriptMatcher.group(1) ?: throw IllegalStateException("Could not extract JSON payload from Spotify.")
            val root = JSONObject(jsonContent)
            val pageProps = root.optJSONObject("props")?.optJSONObject("pageProps")
                ?: throw IllegalStateException("Unexpected Spotify metadata format.")

            val state = pageProps.optJSONObject("state")
            val dataObj = state?.optJSONObject("data")
            val entity = dataObj?.optJSONObject("entity") ?: pageProps.optJSONObject("entity")
                ?: throw IllegalStateException("Could not locate playlist entity.")

            val rawName = entity.optString("name").ifBlank {
                entity.optString("title", if (type == "album") "Imported Spotify Album" else "Imported Spotify Playlist")
            }
            val playlistName = rawName.replace("&amp;", "&").replace("&quot;", "\"").trim()

            val trackListArray = entity.optJSONArray("trackList")
            val parsedTracks = mutableListOf<SpotifyTrack>()

            if (trackListArray != null) {
                for (i in 0 until trackListArray.length()) {
                    val item = trackListArray.optJSONObject(i) ?: continue
                    val title = item.optString("title").replace('\u00A0', ' ').replace("&amp;", "&").trim()
                    if (title.isBlank()) continue

                    val artist = item.optString("subtitle").replace('\u00A0', ' ').replace("&amp;", "&").trim()
                    val durationMs = item.optLong("duration", 0L)
                    val durationSec = if (durationMs > 0) durationMs / 1000.0 else 0.0
                    val previewUrl = item.optJSONObject("audioPreview")?.optString("url")

                    parsedTracks.add(
                        SpotifyTrack(
                            title = title,
                            artist = if (artist.isNotBlank()) artist else "Unknown Artist",
                            durationSeconds = durationSec,
                            previewUrl = previewUrl
                        )
                    )
                }
            }

            if (parsedTracks.isEmpty()) {
                throw IllegalStateException("No tracks found in this Spotify playlist.")
            }

            Log.i(TAG, "Successfully extracted Spotify playlist '$playlistName' with ${parsedTracks.size} tracks")
            SpotifyPlaylist(
                title = playlistName,
                tracks = parsedTracks
            )
        }
    }
}
