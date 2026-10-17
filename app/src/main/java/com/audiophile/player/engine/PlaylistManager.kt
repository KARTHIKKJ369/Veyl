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
        val cleanFavs = favs.filter { fav ->
            val name = java.io.File(fav).name
            !name.startsWith(".") &&
            !name.contains("trashed", ignoreCase = true) &&
            !name.startsWith("veyl_online_stream_") &&
            !fav.contains("/cache/")
        }.toSet()
        _favoriteUris.value = cleanFavs
        if (cleanFavs.size != favs.size) {
            prefs.edit().putStringSet("favorites_set", cleanFavs).apply()
        }

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
                    val t = trackArr.getString(j)
                    val tName = java.io.File(t).name
                    if (!tName.startsWith(".") && !tName.contains("trashed", ignoreCase = true) && !tName.startsWith("veyl_online_stream_") && !t.contains("/cache/")) {
                        tracks.add(t)
                    }
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
            val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
            val veylDir = java.io.File(musicDir, "Veyl")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val track = jsonToTrackInfo(obj)
                val artUrl = obj.optString("artworkUrl")
                if (artUrl.isNotBlank()) {
                    ArtworkCache.registerOnlineArtwork(track.uri, artUrl)
                    val fn = java.io.File(track.uri).name
                    if (fn.isNotBlank()) {
                        ArtworkCache.registerOnlineArtwork(fn, artUrl)
                        ArtworkCache.registerOnlineArtwork("online://$fn", artUrl)
                    }
                    ArtworkCache.registerOnlineArtwork(track.title.lowercase(), artUrl)
                }
                metaMap[track.uri] = track
                val fName = java.io.File(track.uri).name
                if (fName.isNotBlank()) metaMap[fName] = track

                val sanitized = "${track.title} - ${track.artist ?: ""}".replace(Regex("[^a-zA-Z0-9.\\- ]"), "_").trim()
                val targetPath = java.io.File(veylDir, "$sanitized.m4a").absolutePath
                metaMap[targetPath] = track
                metaMap[java.io.File(targetPath).name] = track
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

    fun addFavorites(uris: Set<String>) {
        if (uris.isEmpty()) return
        val current = _favoriteUris.value.toMutableSet()
        current.addAll(uris)
        _favoriteUris.value = current
        prefs.edit().putStringSet("favorites_set", current).apply()
    }

    fun removeFavorites(uris: Set<String>) {
        if (uris.isEmpty()) return
        val current = _favoriteUris.value.toMutableSet()
        current.removeAll(uris)
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

    fun createPlaylistWithTracks(name: String, trackUris: List<String>): UserPlaylist {
        var finalName = name.trim().ifEmpty { "Imported Playlist" }
        val existingNames = _playlists.value.map { it.name.lowercase() }.toSet()
        var counter = 1
        val baseName = finalName
        while (existingNames.contains(finalName.lowercase())) {
            finalName = "$baseName ($counter)"
            counter++
        }

        val newPlaylist = UserPlaylist(
            id = UUID.randomUUID().toString(),
            name = finalName,
            trackUris = trackUris.distinct()
        )
        val updatedList = _playlists.value + newPlaylist
        savePlaylists(updatedList)
        return newPlaylist
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

    fun renamePlaylist(playlistId: String, newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isBlank()) return
        val updated = _playlists.value.map { pl ->
            if (pl.id == playlistId) pl.copy(name = trimmed) else pl
        }
        savePlaylists(updated)
    }

    /**
     * Exports a playlist's tracks to an Extended M3U8 format stream.
     */
    fun exportPlaylist(
        name: String,
        tracks: List<TrackInfo>,
        outputStream: java.io.OutputStream
    ): Result<Int> {
        return runCatching {
            outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                writer.write("#EXTM3U\n")
                writer.write("#PLAYLIST:$name\n")
                for (track in tracks) {
                    val dur = if (track.durationSeconds > 0) track.durationSeconds.toInt() else -1
                    val artist = if (track.artist.isNotBlank()) track.artist.replace("\n", " ").replace("\r", " ") else "Unknown Artist"
                    val title = if (track.title.isNotBlank()) track.title.replace("\n", " ").replace("\r", " ") else "Unknown Title"
                    writer.write("#EXTINF:$dur,$artist - $title\n")
                    val album = track.album
                    if (!album.isNullOrBlank()) {
                        writer.write("#EXTALB:${album.replace("\n", " ").replace("\r", " ")}\n")
                    }
                    val genre = track.genre
                    if (!genre.isNullOrBlank()) {
                        writer.write("#EXTGENRE:${genre.replace("\n", " ").replace("\r", " ")}\n")
                    }
                    writer.write("${track.uri}\n")
                }
                writer.flush()
            }
            tracks.size
        }
    }

    /**
     * Imports a playlist from an InputStream (supports .m3u, .m3u8, and .json).
     * Resolves track paths against the local library where possible.
     */
    fun importPlaylist(
        inputStream: java.io.InputStream,
        defaultName: String = "Imported Playlist",
        libraryTracks: List<TrackInfo> = emptyList()
    ): Result<UserPlaylist> {
        return runCatching {
            val rawContent = inputStream.bufferedReader(Charsets.UTF_8).readText().trimStart('\uFEFF')
            val trimmed = rawContent.trim()
            if (trimmed.isEmpty()) {
                throw IllegalArgumentException("Playlist file is empty")
            }

            var playlistName = defaultName
            val importedUris = mutableListOf<String>()

            if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
                // JSON Playlist
                if (trimmed.startsWith("{")) {
                    val obj = JSONObject(trimmed)
                    playlistName = obj.optString("name", defaultName)
                    val tracksArr = obj.optJSONArray("tracks")
                    if (tracksArr != null) {
                        for (i in 0 until tracksArr.length()) {
                            val item = tracksArr.get(i)
                            if (item is JSONObject) {
                                val track = jsonToTrackInfo(item)
                                registerOnlineTrack(track)
                                importedUris.add(track.uri)
                            } else if (item is String) {
                                importedUris.add(item)
                            }
                        }
                    }
                } else {
                    val arr = JSONArray(trimmed)
                    for (i in 0 until arr.length()) {
                        val item = arr.get(i)
                        if (item is JSONObject) {
                            val track = jsonToTrackInfo(item)
                            registerOnlineTrack(track)
                            importedUris.add(track.uri)
                        } else if (item is String) {
                            importedUris.add(item)
                        }
                    }
                }
            } else {
                // Extended M3U / M3U8 / Plain text lines
                val lines = rawContent.lines()
                var pendingTitle: String? = null
                var pendingArtist: String? = null
                var pendingAlbum: String? = null
                var pendingDuration: Double = 0.0

                val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
                val veylDir = java.io.File(musicDir, "Veyl")

                for (rawLine in lines) {
                    val line = rawLine.trim()
                    if (line.isEmpty()) continue

                    if (line.startsWith("#")) {
                        if (line.startsWith("#PLAYLIST:", ignoreCase = true)) {
                            val parsed = line.substring("#PLAYLIST:".length).trim()
                            if (parsed.isNotEmpty()) playlistName = parsed
                        } else if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                            val info = line.substring("#EXTINF:".length).trim()
                            val commaIdx = info.indexOf(',')
                            if (commaIdx != -1) {
                                val durStr = info.substring(0, commaIdx).trim()
                                pendingDuration = durStr.toDoubleOrNull() ?: 0.0
                                val titleAndArtist = info.substring(commaIdx + 1).trim()
                                val dashIdx = titleAndArtist.indexOf(" - ")
                                if (dashIdx != -1) {
                                    pendingArtist = titleAndArtist.substring(0, dashIdx).trim()
                                    pendingTitle = titleAndArtist.substring(dashIdx + 3).trim()
                                } else {
                                    pendingTitle = titleAndArtist
                                }
                            }
                        } else if (line.startsWith("#EXTALB:", ignoreCase = true)) {
                            pendingAlbum = line.substring("#EXTALB:".length).trim()
                        } else if (line.startsWith("#EXTART:", ignoreCase = true)) {
                            pendingArtist = line.substring("#EXTART:".length).trim()
                        }
                        continue
                    }

                    val trackUri = line
                    val fName = java.io.File(trackUri).name

                    // Match against library tracks
                    val matchedTrack = libraryTracks.find { lib ->
                        lib.uri.equals(trackUri, ignoreCase = true) ||
                        (fName.isNotBlank() && java.io.File(lib.uri).name.equals(fName, ignoreCase = true)) ||
                        (!pendingTitle.isNullOrBlank() &&
                         lib.title.equals(pendingTitle, ignoreCase = true) &&
                         (pendingArtist.isNullOrBlank() || lib.artist.equals(pendingArtist, ignoreCase = true)))
                    }

                    val finalUri = when {
                        matchedTrack != null -> matchedTrack.uri
                        java.io.File(trackUri).exists() -> trackUri
                        java.io.File(musicDir, fName).exists() -> java.io.File(musicDir, fName).absolutePath
                        java.io.File(veylDir, fName).exists() -> java.io.File(veylDir, fName).absolutePath
                        else -> {
                            if (!pendingTitle.isNullOrBlank()) {
                                val fallbackTrack = TrackInfo(
                                    uri = trackUri,
                                    title = pendingTitle ?: fName.substringBeforeLast("."),
                                    artist = pendingArtist ?: "Unknown Artist",
                                    album = pendingAlbum ?: "",
                                    albumArtist = null,
                                    genre = "Imported",
                                    year = null,
                                    trackNumber = null,
                                    discNumber = null,
                                    durationSeconds = if (pendingDuration > 0) pendingDuration else 0.0,
                                    sampleRate = 44100u,
                                    bitDepth = 16u,
                                    bitrate = 320000u,
                                    channels = 2u,
                                    formatName = "Audio",
                                    hasArtwork = false
                                )
                                registerOnlineTrack(fallbackTrack)
                            }
                            trackUri
                        }
                    }

                    importedUris.add(finalUri)

                    pendingTitle = null
                    pendingArtist = null
                    pendingAlbum = null
                    pendingDuration = 0.0
                }
            }

            var finalName = playlistName
            var counter = 1
            val existingNames = _playlists.value.map { it.name.lowercase() }.toSet()
            while (existingNames.contains(finalName.lowercase())) {
                finalName = "$playlistName ($counter)"
                counter++
            }

            val newPlaylist = UserPlaylist(
                id = UUID.randomUUID().toString(),
                name = finalName,
                trackUris = importedUris.distinct()
            )

            val updatedList = _playlists.value + newPlaylist
            savePlaylists(updatedList)
            newPlaylist
        }
    }

    /**
     * Completely removes a track from Favorites, Playlists, and online metadata mapping.
     */
    fun removeTrackEverywhere(uri: String, title: String? = null, artist: String? = null) {
        val fileName = java.io.File(uri).name
        val metaMap = _onlineTrackMeta.value

        // Find any tracks in onlineTrackMeta matching uri, fileName, or (title + artist)
        val matchingTracks = metaMap.values.filter { t ->
            t.uri == uri ||
            (fileName.isNotBlank() && java.io.File(t.uri).name == fileName) ||
            (!title.isNullOrBlank() && t.title.equals(title, ignoreCase = true) &&
             (artist.isNullOrBlank() || (t.artist ?: "").equals(artist, ignoreCase = true)))
        }

        val allRelatedUris = mutableSetOf(uri)
        if (fileName.isNotBlank()) allRelatedUris.add(fileName)
        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        val veylDir = java.io.File(musicDir, "Veyl")

        for (t in matchingTracks) {
            allRelatedUris.add(t.uri)
            allRelatedUris.add(java.io.File(t.uri).name)
            val sanitized = "${t.title} - ${t.artist ?: ""}".replace(Regex("[^a-zA-Z0-9.\\- ]"), "_").trim()
            val targetPath = java.io.File(veylDir, "$sanitized.m4a").absolutePath
            allRelatedUris.add(targetPath)
            allRelatedUris.add(java.io.File(targetPath).name)
        }

        // 1. Remove from favorites
        val currentFavs = _favoriteUris.value.toMutableSet()
        val toRemoveFavs = currentFavs.filter { fav ->
            val favName = java.io.File(fav).name
            allRelatedUris.contains(fav) ||
            allRelatedUris.contains(favName) ||
            favName.startsWith(".") ||
            favName.contains("trashed", ignoreCase = true) ||
            favName.startsWith("veyl_online_stream_") ||
            fav.contains("/cache/")
        }
        if (toRemoveFavs.isNotEmpty()) {
            currentFavs.removeAll(toRemoveFavs.toSet())
            _favoriteUris.value = currentFavs
            prefs.edit().putStringSet("favorites_set", currentFavs).apply()
        }

        // 2. Remove from playlists
        val updatedPlaylists = _playlists.value.map { pl ->
            pl.copy(trackUris = pl.trackUris.filter { trackUri ->
                val plName = java.io.File(trackUri).name
                !allRelatedUris.contains(trackUri) &&
                !allRelatedUris.contains(plName) &&
                !plName.startsWith(".") &&
                !plName.contains("trashed", ignoreCase = true) &&
                !plName.startsWith("veyl_online_stream_") &&
                !trackUri.contains("/cache/")
            })
        }
        savePlaylists(updatedPlaylists)

        // 3. Remove from metadata mapping
        val currentMeta = _onlineTrackMeta.value.toMutableMap()
        for (k in allRelatedUris) {
            currentMeta.remove(k)
        }
        if (!title.isNullOrBlank()) {
            val toRemoveKeys = currentMeta.filter { (_, v) ->
                v.title.equals(title, ignoreCase = true) &&
                (artist.isNullOrBlank() || (v.artist ?: "").equals(artist, ignoreCase = true))
            }.keys
            for (k in toRemoveKeys) {
                currentMeta.remove(k)
            }
        }
        _onlineTrackMeta.value = currentMeta
        persistOnlineTracks(currentMeta.values.toList())
    }

    /**
     * Replaces an old temporary URI (e.g. streaming cache) with a new permanent local file URI
     * across Favorites and all custom Playlists.
     */
    fun updateTrackUri(oldUri: String, newUri: String) {
        if (oldUri == newUri) return

        // Update favorites: replace oldUri with newUri
        val currentFavs = _favoriteUris.value.toMutableSet()
        if (currentFavs.contains(oldUri) || currentFavs.contains(newUri)) {
            currentFavs.remove(oldUri)
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
        currentMeta.remove(oldUri)
        currentMeta.remove(java.io.File(oldUri).name)
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
        val musicDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC)
        val veylDir = java.io.File(musicDir, "Veyl")
        val sanitized = "${track.title} - ${track.artist ?: ""}".replace(Regex("[^a-zA-Z0-9.\\- ]"), "_").trim()
        val targetPath = java.io.File(veylDir, "$sanitized.m4a").absolutePath
        current[targetPath] = track
        current[java.io.File(targetPath).name] = track

        song?.let {
            if (it.streamUrl.isNotBlank()) current[it.streamUrl] = track
            if (it.id.isNotBlank()) current[it.id] = track
            if (it.videoId.isNotBlank()) current[it.videoId] = track
            if (it.thumbnailUrl.isNotBlank()) {
                ArtworkCache.registerOnlineArtwork(track.uri, it.thumbnailUrl)
                val fn = java.io.File(track.uri).name
                if (fn.isNotBlank()) {
                    ArtworkCache.registerOnlineArtwork(fn, it.thumbnailUrl)
                    ArtworkCache.registerOnlineArtwork("online://$fn", it.thumbnailUrl)
                }
                if (it.id.isNotBlank()) {
                    ArtworkCache.registerOnlineArtwork(it.id, it.thumbnailUrl)
                    ArtworkCache.registerOnlineArtwork("online://${it.id}", it.thumbnailUrl)
                }
                ArtworkCache.registerOnlineArtwork(track.title.lowercase(), it.thumbnailUrl)
            }
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
            val distinctTracks = tracks.distinctBy {
                "${it.title.lowercase()}_${(it.artist ?: "").lowercase()}"
            }
            val arr = JSONArray()
            distinctTracks.forEach { arr.put(trackInfoToJson(it)) }
            prefs.edit().putString("online_tracks_json", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist online track metadata", e)
        }
    }

    private fun trackInfoToJson(t: TrackInfo): JSONObject {
        val art = ArtworkCache.getOnlineArtwork(t.uri) ?: ""
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
            if (art.isNotBlank()) put("artworkUrl", art)
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
