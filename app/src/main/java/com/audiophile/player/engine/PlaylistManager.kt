package com.audiophile.player.engine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.audiophile.player.online.OnlineSongItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import uniffi.audiophile_core.TrackInfo
import java.util.UUID

data class UserPlaylist(
    val id: String,
    val name: String,
    val trackUris: List<String>
)

class PlaylistManager(context: Context) {

    companion object {
        private const val TAG = "PlaylistManager"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences("audiophile_playlists", Context.MODE_PRIVATE)

    private val _favoriteUris = MutableStateFlow<Set<String>>(emptySet())
    val favoriteUris: StateFlow<Set<String>> = _favoriteUris.asStateFlow()

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

    // Registry of known online track metadata so they display cleanly in Favorites & Playlists
    private val _onlineTrackMeta = MutableStateFlow<Map<String, TrackInfo>>(emptyMap())
    val onlineTrackMeta: StateFlow<Map<String, TrackInfo>> = _onlineTrackMeta.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        val favs = prefs.getStringSet("favorites_set", emptySet()) ?: emptySet()
        _favoriteUris.value = favs.toSet()

        val playlistsJson = prefs.getString("playlists_json", "[]") ?: "[]"
        val list = mutableListOf<UserPlaylist>()
        try {
            val arr = JSONArray(playlistsJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val trackArr = obj.getJSONArray("tracks")
                val tracks = mutableListOf<String>()
                for (j in 0 until trackArr.length()) {
                    tracks.add(trackArr.getString(j))
                }
                list.add(UserPlaylist(id, name, tracks))
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading playlists", e)
        }
        _playlists.value = list

        // Load persisted online track metadata
        val onlineJson = prefs.getString("online_tracks_json", "[]") ?: "[]"
        val metaMap = mutableMapOf<String, TrackInfo>()
        try {
            val arr = JSONArray(onlineJson)
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val track = jsonToTrackInfo(obj)
                metaMap[track.uri] = track
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error loading online track metadata", e)
        }
        _onlineTrackMeta.value = metaMap
    }

    fun isFavorite(uri: String): Boolean {
        return _favoriteUris.value.contains(uri)
    }

    fun toggleFavorite(uri: String) {
        val current = _favoriteUris.value.toMutableSet()
        if (current.contains(uri)) {
            current.remove(uri)
        } else {
            current.add(uri)
        }
        _favoriteUris.value = current
        prefs.edit().putStringSet("favorites_set", current).apply()
    }

    fun createPlaylist(name: String): UserPlaylist {
        val id = UUID.randomUUID().toString()
        val pl = UserPlaylist(id, name, emptyList())
        val updated = _playlists.value + pl
        savePlaylists(updated)
        return pl
    }

    fun addTrackToPlaylist(playlistId: String, uri: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId && !pl.trackUris.contains(uri)) {
                pl.copy(trackUris = pl.trackUris + uri)
            } else pl
        }
        savePlaylists(updated)
    }

    fun removeTrackFromPlaylist(playlistId: String, uri: String) {
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) {
                pl.copy(trackUris = pl.trackUris - uri)
            } else pl
        }
        savePlaylists(updated)
    }

    fun deletePlaylist(playlistId: String) {
        val updated = _playlists.value.filter { it.id != playlistId }
        savePlaylists(updated)
    }

    /**
     * Replaces an old temporary URI (e.g. streaming cache) with a new permanent local file URI
     * across Favorites and all custom Playlists.
     */
    fun updateTrackUri(oldUri: String, newUri: String) {
        if (oldUri == newUri) return

        // Update favorites
        val currentFavs = _favoriteUris.value.toMutableSet()
        if (currentFavs.remove(oldUri)) {
            currentFavs.add(newUri)
            _favoriteUris.value = currentFavs
            prefs.edit().putStringSet("favorites_set", currentFavs).apply()
        }

        // Update playlists
        val updatedPlaylists = _playlists.value.map { pl ->
            if (pl.trackUris.contains(oldUri)) {
                pl.copy(trackUris = pl.trackUris.map { if (it == oldUri) newUri else it })
            } else pl
        }
        savePlaylists(updatedPlaylists)

        // Update metadata mapping
        val currentMeta = _onlineTrackMeta.value.toMutableMap()
        val oldTrack = currentMeta[oldUri] ?: currentMeta[java.io.File(oldUri).name]
        if (oldTrack != null) {
            val updatedTrack = oldTrack.copy(uri = newUri)
            currentMeta[newUri] = updatedTrack
            currentMeta[java.io.File(newUri).name] = updatedTrack
            _onlineTrackMeta.value = currentMeta
            persistOnlineTracks(currentMeta.values.toList())
        }
    }

    /**
     * Registers and persists metadata for an online track so it can always be displayed
     * and played in Favorites or Playlists.
     */
    fun registerOnlineTrack(track: TrackInfo, song: OnlineSongItem? = null) {
        val current = _onlineTrackMeta.value.toMutableMap()
        current[track.uri] = track
        val fileName = java.io.File(track.uri).name
        if (fileName.isNotBlank()) {
            current[fileName] = track
        }
        song?.let {
            if (it.streamUrl.isNotBlank()) current[it.streamUrl] = track
            if (it.id.isNotBlank()) current[it.id] = track
            if (it.videoId.isNotBlank()) current[it.videoId] = track
        }
        _onlineTrackMeta.value = current
        persistOnlineTracks(current.values.toList())
    }

    fun getTrackMeta(uri: String): TrackInfo? {
        val map = _onlineTrackMeta.value
        return map[uri] ?: map[java.io.File(uri).name]
    }

    private fun persistOnlineTracks(tracks: List<TrackInfo>) {
        try {
            val arr = JSONArray()
            tracks.forEach { arr.put(trackInfoToJson(it)) }
            prefs.edit().putString("online_tracks_json", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist online track metadata", e)
        }
    }

    private fun trackInfoToJson(t: TrackInfo): JSONObject {
        return JSONObject().apply {
            put("uri", t.uri)
            put("title", t.title)
            put("artist", t.artist)
            put("album", t.album)
            put("albumArtist", t.albumArtist ?: "")
            put("genre", t.genre ?: "Online Stream")
            put("durationSeconds", t.durationSeconds)
            put("sampleRate", t.sampleRate.toInt())
            put("bitDepth", t.bitDepth?.toInt() ?: 16)
            put("bitrate", t.bitrate?.toInt() ?: 320000)
            put("channels", t.channels.toInt())
            put("formatName", t.formatName)
            put("hasArtwork", t.hasArtwork)
        }
    }

    private fun jsonToTrackInfo(obj: JSONObject): TrackInfo {
        return TrackInfo(
            uri = obj.getString("uri"),
            title = obj.getString("title"),
            artist = obj.optString("artist", "Unknown Artist"),
            album = obj.optString("album", ""),
            albumArtist = obj.optString("albumArtist").takeIf { it.isNotBlank() },
            genre = obj.optString("genre", "Online Stream"),
            year = null,
            trackNumber = null,
            discNumber = null,
            durationSeconds = obj.optDouble("durationSeconds", 0.0),
            sampleRate = obj.optInt("sampleRate", 44100).toUInt(),
            bitDepth = obj.optInt("bitDepth", 16).toUByte(),
            bitrate = obj.optInt("bitrate", 320000).toUInt(),
            channels = obj.optInt("channels", 2).toUByte(),
            formatName = obj.optString("formatName", "AAC 320k (Studio Master)"),
            hasArtwork = obj.optBoolean("hasArtwork", true)
        )
    }

    private fun savePlaylists(list: List<UserPlaylist>) {
        _playlists.value = list
        try {
            val arr = JSONArray()
            list.forEach { pl ->
                val obj = JSONObject().apply {
                    put("id", pl.id)
                    put("name", pl.name)
                    val tracksArr = JSONArray()
                    pl.trackUris.forEach { tracksArr.put(it) }
                    put("tracks", tracksArr)
                }
                arr.put(obj)
            }
            prefs.edit().putString("playlists_json", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save playlists", e)
        }
    }
}
