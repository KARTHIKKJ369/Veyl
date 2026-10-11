package com.audiophile.player.online

import android.os.Build
import android.text.Html
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * High-speed studio music provider powered by official direct CDN endpoints.
 * Provides instant 320 kbps AAC/MP4 studio streams with zero signature deciphering overhead.
 */
object JioSaavnService {
    private const val TAG = "JioSaavnService"
    private const val DES_KEY = "38346591"
    private const val SEARCH_ENDPOINT = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=android&n=25&q="
    private const val ARTIST_SEARCH_ENDPOINT = "https://www.jiosaavn.com/api.php?__call=search.getArtistResults&_format=json&_marker=0&n=15&q="
    private const val AUTOCOMPLETE_ENDPOINT = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&_format=json&_marker=0&query="

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    suspend fun searchSongs(query: String): List<OnlineSongItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val request = Request.Builder()
                .url(SEARCH_ENDPOINT + encodedQuery)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Search failed with HTTP ${response.code}")
                    return@withContext emptyList()
                }

                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                return@withContext parseSearchResults(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error for '$trimmed'", e)
            emptyList()
        }
    }

    suspend fun searchArtists(query: String): List<OnlineArtistItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        val results = mutableListOf<OnlineArtistItem>()
        val seenNames = mutableSetOf<String>()

        try {
            val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
            val request = Request.Builder()
                .url(ARTIST_SEARCH_ENDPOINT + encodedQuery)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    if (body.isNotBlank()) {
                        val json = JSONObject(body)
                        val arr = json.optJSONArray("results")
                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val r = arr.optJSONObject(i) ?: continue
                                val rawName = r.optString("name")
                                val cleanName = decodeHtml(rawName).trim()
                                if (cleanName.isNotBlank() && seenNames.add(cleanName.lowercase())) {
                                    val id = r.optString("id")
                                    val role = decodeHtml(r.optString("role", "Artist"))
                                    val rawImg = r.optString("image")
                                    val highResImg = rawImg.replace("50x50", "500x500").replace("150x150", "500x500")
                                    results.add(
                                        OnlineArtistItem(
                                            id = id,
                                            name = cleanName,
                                            role = role,
                                            imageUrl = highResImg
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Artist search error for '$trimmed'", e)
        }

        // Fallback to autocomplete if few results
        if (results.size < 3) {
            try {
                val encodedQuery = URLEncoder.encode(trimmed, "UTF-8")
                val request = Request.Builder()
                    .url(AUTOCOMPLETE_ENDPOINT + encodedQuery)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .get()
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: ""
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            val top = json.optJSONObject("topquery")?.optJSONArray("data")
                            if (top != null) {
                                for (i in 0 until top.length()) {
                                    val item = top.optJSONObject(i) ?: continue
                                    if (item.optString("type") == "artist") {
                                        val name = decodeHtml(item.optString("title")).trim()
                                        if (name.isNotBlank() && seenNames.add(name.lowercase())) {
                                            val img = item.optString("image").replace("50x50", "500x500").replace("150x150", "500x500")
                                            results.add(
                                                OnlineArtistItem(
                                                    id = item.optString("id"),
                                                    name = name,
                                                    role = decodeHtml(item.optString("description", "Artist")),
                                                    imageUrl = img
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                            val artistsArr = json.optJSONObject("artists")?.optJSONArray("data")
                            if (artistsArr != null) {
                                for (i in 0 until artistsArr.length()) {
                                    val item = artistsArr.optJSONObject(i) ?: continue
                                    val name = decodeHtml(item.optString("title")).trim()
                                    if (name.isNotBlank() && seenNames.add(name.lowercase())) {
                                        val img = item.optString("image").replace("50x50", "500x500").replace("150x150", "500x500")
                                        results.add(
                                            OnlineArtistItem(
                                                id = item.optString("id"),
                                                name = name,
                                                role = decodeHtml(item.optString("description", "Artist")),
                                                imageUrl = img
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore fallback error
            }
        }

        results
    }

    suspend fun getPlaylistSongs(listId: String): List<OnlineSongItem> = withContext(Dispatchers.IO) {
        if (listId.isBlank()) return@withContext emptyList()
        try {
            val url = "https://www.jiosaavn.com/api.php?__call=playlist.getDetails&_format=json&_marker=0&api_version=4&listid=$listId"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .get()
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val body = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(body)
                val arr = json.optJSONArray("list") ?: json.optJSONArray("songs") ?: return@withContext emptyList()
                return@withContext parseJsonSongArray(arr)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching playlist $listId", e)
            emptyList()
        }
    }

    private fun parseSearchResults(json: JSONObject): List<OnlineSongItem> {
        val results = json.optJSONArray("results") ?: return emptyList()
        return parseJsonSongArray(results)
    }

    fun parseJsonSongArray(results: org.json.JSONArray): List<OnlineSongItem> {
        val items = mutableListOf<OnlineSongItem>()

        for (i in 0 until results.length()) {
            val r = results.optJSONObject(i) ?: continue
            val id = r.optString("id")
            val rawTitle = r.optString("title").ifBlank { r.optString("song") }
            val cleanTitle = decodeHtml(rawTitle)

            val moreInfo = r.optJSONObject("more_info") ?: r
            val rawAlbum = moreInfo.optString("album").ifBlank { r.optString("album") }
            val cleanAlbum = decodeHtml(rawAlbum)

            // Extract primary artists
            var artist = ""
            val artistMap = moreInfo.optJSONObject("artistMap")
            if (artistMap != null) {
                val primary = artistMap.optJSONArray("primary_artists")
                if (primary != null && primary.length() > 0) {
                    val names = mutableListOf<String>()
                    for (p in 0 until primary.length()) {
                        val a = primary.optJSONObject(p)
                        val name = a?.optString("name")
                        if (!name.isNullOrBlank()) names.add(name)
                    }
                    artist = names.joinToString(", ")
                }
            }
            if (artist.isBlank()) {
                val primaryArtistsStr = moreInfo.optString("primary_artists").ifBlank { r.optString("primary_artists") }
                if (primaryArtistsStr.isNotBlank()) {
                    artist = decodeHtml(primaryArtistsStr)
                }
            }
            if (artist.isBlank()) {
                val music = moreInfo.optString("music")
                artist = if (music.isNotBlank()) decodeHtml(music) else decodeHtml(r.optString("subtitle"))
            }
            if (artist.isBlank()) {
                artist = decodeHtml(r.optString("singers"))
            }
            if (artist.isBlank()) artist = "Unknown Artist"

            val durationSeconds = moreInfo.optDouble("duration", r.optDouble("duration", 0.0))
            val durationText = formatDuration(durationSeconds)

            // High-res album art (convert 150x150 to 500x500)
            val rawImage = r.optString("image")
            val highResImage = rawImage.replace("150x150", "500x500").replace("50x50", "500x500")

            // Decrypt direct stream URL
            val encryptedUrl = moreInfo.optString("encrypted_media_url").ifBlank { r.optString("encrypted_media_url") }
            val streamUrl = decryptMediaUrl(encryptedUrl) ?: continue

            items.add(
                OnlineSongItem(
                    id = id,
                    title = cleanTitle,
                    artist = artist,
                    album = cleanAlbum,
                    durationSeconds = durationSeconds,
                    durationText = durationText,
                    thumbnailUrl = highResImage,
                    streamUrl = streamUrl
                )
            )
        }
        return items
    }

    fun decryptMediaUrl(encryptedUrl: String): String? {
        if (encryptedUrl.isBlank()) return null
        return try {
            val keyBytes = DES_KEY.toByteArray(Charsets.UTF_8)
            val secretKey = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, secretKey)
            val decodedBytes = try {
                Base64.decode(encryptedUrl, Base64.DEFAULT)
            } catch (_: Throwable) {
                java.util.Base64.getDecoder().decode(encryptedUrl.trim())
            }
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val rawUrl = String(decryptedBytes, Charsets.UTF_8).trim()

            // Upgrade to studio 320 kbps stream
            rawUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to decrypt media url", e)
            null
        }
    }

    private fun decodeHtml(input: String): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Html.fromHtml(input, Html.FROM_HTML_MODE_LEGACY).toString()
            } else {
                @Suppress("DEPRECATION")
                Html.fromHtml(input).toString()
            }
        } catch (_: Exception) {
            input
        }
    }

    private fun formatDuration(seconds: Double): String {
        val totalSec = seconds.toInt()
        val m = totalSec / 60
        val s = totalSec % 60
        return "%d:%02d".format(m, s)
    }
}
