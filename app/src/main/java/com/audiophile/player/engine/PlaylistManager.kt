package com.audiophile.player.engine

import android.content.Context
import android.content.SharedPreferences
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

    private val prefs: SharedPreferences = context.getSharedPreferences("audiophile_playlists", Context.MODE_PRIVATE)

    private val _favoriteUris = MutableStateFlow<Set<String>>(emptySet())
    val favoriteUris: StateFlow<Set<String>> = _favoriteUris.asStateFlow()

    private val _playlists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    val playlists: StateFlow<List<UserPlaylist>> = _playlists.asStateFlow()

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
            // Ignore error on initial empty json
        }
        _playlists.value = list
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
            // Ignore
        }
    }
}
