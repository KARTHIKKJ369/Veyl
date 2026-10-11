package com.audiophile.player.online

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Service to search YouTube Music official library.
 * Uses YouTube Music WEB_REMIX client with the strict "Songs Only" parameter.
 * Filters out all regular user videos, vlogs, reactions, and non-music content.
 */
object YouTubeMusicService {

    private const val TAG = "YouTubeMusicService"
    private const val YTM_SEARCH_URL = "https://music.youtube.com/youtubei/v1/search"
    // Params filter for Songs ONLY in YouTube Music catalog
    private const val SONGS_ONLY_PARAMS = "EgWKAQIIAWoKEAkQBRAKEAMQBBAK"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    suspend fun searchSongs(query: String): List<OnlineSongItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.length < 2) return@withContext emptyList()

        try {
            val payload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("query", trimmed)
                put("params", SONGS_ONLY_PARAMS)
            }

            val request = Request.Builder()
                .url(YTM_SEARCH_URL)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Referer", "https://music.youtube.com/")
                .header("Content-Type", "application/json")
                .header("X-YouTube-Client-Name", "67") // WEB_REMIX
                .header("X-YouTube-Client-Version", "1.20231211.01.00")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Search request failed with code: ${response.code}")
                    return@withContext emptyList()
                }

                val responseBody = response.body?.string() ?: return@withContext emptyList()
                val json = JSONObject(responseBody)
                return@withContext parseMusicSongs(json)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing YouTube Music search for '$trimmed'", e)
            emptyList()
        }
    }

    private fun parseMusicSongs(root: JSONObject): List<OnlineSongItem> {
        val results = mutableListOf<OnlineSongItem>()

        fun findRenderers(obj: Any) {
            when (obj) {
                is JSONObject -> {
                    val keys = obj.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key == "musicResponsiveListItemRenderer") {
                            obj.optJSONObject(key)?.let { renderer ->
                                parseSingleRenderer(renderer)?.let { results.add(it) }
                            }
                        } else {
                            obj.opt(key)?.let { findRenderers(it) }
                        }
                    }
                }
                is JSONArray -> {
                    for (i in 0 until obj.length()) {
                        obj.opt(i)?.let { findRenderers(it) }
                    }
                }
            }
        }

        findRenderers(root)
        return results.distinctBy { it.videoId }
    }

    private fun parseSingleRenderer(renderer: JSONObject): OnlineSongItem? {
        try {
            val flexColumns = renderer.optJSONArray("flexColumns") ?: return null
            if (flexColumns.length() == 0) return null

            // Column 0: Track Title & Video ID
            val col0 = flexColumns.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")
                ?.optJSONArray("runs") ?: return null

            val titleRun = col0.optJSONObject(0) ?: return null
            val title = titleRun.optString("text", "").trim()
            if (title.isBlank()) return null

            var videoId: String? = null
            // Check playNavigationEndpoint first
            val overlay = renderer.optJSONObject("overlay")
                ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("musicPlayButtonRenderer")
                ?.optJSONObject("playNavigationEndpoint")
                ?.optJSONObject("watchEndpoint")
            videoId = overlay?.optString("videoId")

            // Fallback: check navigationEndpoint on title or columns
            if (videoId.isNullOrBlank()) {
                videoId = titleRun.optJSONObject("navigationEndpoint")
                    ?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")
            }

            if (videoId.isNullOrBlank()) {
                for (c in 0 until flexColumns.length()) {
                    val runs = flexColumns.optJSONObject(c)
                        ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs") ?: continue
                    for (r in 0 until runs.length()) {
                        val v = runs.optJSONObject(r)
                            ?.optJSONObject("navigationEndpoint")
                            ?.optJSONObject("watchEndpoint")
                            ?.optString("videoId")
                        if (!v.isNullOrBlank()) {
                            videoId = v
                            break
                        }
                    }
                    if (!videoId.isNullOrBlank()) break
                }
            }

            if (videoId.isNullOrBlank()) return null

            // Column 1: Artist, Album, Duration
            var artist = "Unknown Artist"
            var album = "Single"
            var durationText = "0:00"
            var durationSec = 0.0

            if (flexColumns.length() > 1) {
                val col1Runs = flexColumns.optJSONObject(1)
                    ?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs")

                if (col1Runs != null && col1Runs.length() > 0) {
                    val nonBulletRuns = mutableListOf<String>()
                    for (i in 0 until col1Runs.length()) {
                        val text = col1Runs.optJSONObject(i)?.optString("text", "")?.trim() ?: ""
                        if (text.isNotEmpty() && text != "•" && text != "&" && text != ",") {
                            nonBulletRuns.add(text)
                        }
                    }

                    if (nonBulletRuns.isNotEmpty()) {
                        artist = nonBulletRuns[0]
                    }
                    if (nonBulletRuns.size >= 2) {
                        // Check if last element is duration like "3:51"
                        val last = nonBulletRuns.last()
                        if (last.contains(":") && last.replace(":", "").all { it.isDigit() }) {
                            durationText = last
                            durationSec = parseDurationToSeconds(durationText)
                            if (nonBulletRuns.size >= 3) {
                                album = nonBulletRuns[1]
                            }
                        } else {
                            album = nonBulletRuns[1]
                        }
                    }
                }
            }

            // Thumbnail extraction
            var thumbUrl = ""
            val thumbnails = renderer.optJSONObject("thumbnail")
                ?.optJSONObject("musicThumbnailRenderer")
                ?.optJSONObject("thumbnail")
                ?.optJSONArray("thumbnails")

            if (thumbnails != null && thumbnails.length() > 0) {
                val lastThumb = thumbnails.optJSONObject(thumbnails.length() - 1)
                thumbUrl = lastThumb?.optString("url", "") ?: ""
                // Upgrade thumbnail resolution if it's a sizing URL like =w60-h60-l90-rj
                if (thumbUrl.contains("=w") && thumbUrl.contains("-h")) {
                    thumbUrl = thumbUrl.replace(Regex("=w\\d+-h\\d+"), "=w544-h544")
                }
            }

            return OnlineSongItem(
                videoId = videoId,
                title = title,
                artist = artist,
                album = album,
                durationSeconds = durationSec,
                durationText = durationText,
                thumbnailUrl = thumbUrl
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing item renderer", e)
            return null
        }
    }

    private fun parseDurationToSeconds(durationText: String): Double {
        return try {
            val parts = durationText.split(":").map { it.trim().toLongOrNull() ?: 0L }
            when (parts.size) {
                2 -> (parts[0] * 60 + parts[1]).toDouble()
                3 -> (parts[0] * 3600 + parts[1] * 60 + parts[2]).toDouble()
                else -> 0.0
            }
        } catch (e: Exception) {
            0.0
        }
    }
}
