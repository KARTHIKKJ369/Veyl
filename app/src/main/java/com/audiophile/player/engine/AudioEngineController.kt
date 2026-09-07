package com.audiophile.player.engine

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.audiophile_core.AudiophileEngineHandle
import uniffi.audiophile_core.DsdModeEnum
import uniffi.audiophile_core.EngineStatus
import uniffi.audiophile_core.EqBandInfo
import uniffi.audiophile_core.FrequencyPoint
import uniffi.audiophile_core.LibraryEngine
import uniffi.audiophile_core.PlaybackStateEnum
import uniffi.audiophile_core.TrackInfo
import java.io.File
import java.util.Collections
import java.util.Random
import androidx.compose.ui.graphics.Color
import com.audiophile.player.ui.theme.CuratedPresets
import com.audiophile.player.ui.theme.ThemePreset
import com.audiophile.player.ui.theme.VeylColorScheme
import com.audiophile.player.ui.theme.VeylDarkColorScheme
import com.audiophile.player.ui.theme.buildVeylColorScheme
import com.audiophile.player.ui.theme.parseColorFromHex
import com.audiophile.player.ui.theme.toHex

data class SavedCustomTheme(
    val id: String,
    val name: String,
    val primaryHex: String,
    val secondaryHex: String,
    val backgroundHex: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}

class AudioEngineController private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    private val prefs = context.getSharedPreferences("audiophile_prefs", Context.MODE_PRIVATE)

    // USB DAC Hardware Manager
    val usbDacManager = UsbDacManager(context)
    val connectedDac: StateFlow<UsbDacInfo?> = usbDacManager.connectedDac

    // Playlist & Favorites Manager
    val playlistManager = PlaylistManager(context)
    val favoriteUris: StateFlow<Set<String>> = playlistManager.favoriteUris
    val playlists: StateFlow<List<UserPlaylist>> = playlistManager.playlists

    // Native Rust Engine Handles
    private var nativeEngine: AudiophileEngineHandle? = null
    private val libraryEngine = LibraryEngine()

    private val _status = MutableStateFlow<EngineStatus?>(null)
    val status: StateFlow<EngineStatus?> = _status.asStateFlow()

    val currentTrack: StateFlow<TrackInfo?> = _status.map { it?.currentTrack }.stateIn(scope, SharingStarted.Eagerly, null)
    val playbackState: StateFlow<PlaybackStateEnum> = _status.map { it?.state ?: PlaybackStateEnum.STOPPED }.stateIn(scope, SharingStarted.Eagerly, PlaybackStateEnum.STOPPED)
    val currentUri: StateFlow<String?> = _status.map { it?.currentTrack?.uri }.stateIn(scope, SharingStarted.Eagerly, null)

    private val _eqBands = MutableStateFlow<List<EqBandInfo>>(emptyList())
    val eqBands: StateFlow<List<EqBandInfo>> = _eqBands.asStateFlow()

    private val _eqCurve = MutableStateFlow<List<FrequencyPoint>>(emptyList())
    val eqCurve: StateFlow<List<FrequencyPoint>> = _eqCurve.asStateFlow()

    private val _libraryTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val libraryTracks: StateFlow<List<TrackInfo>> = _libraryTracks.asStateFlow()
    val tracks: StateFlow<List<TrackInfo>> = _libraryTracks.asStateFlow()

    private val _isEqEnabled = MutableStateFlow(prefs.getBoolean("eq_enabled", false))
    val isEqEnabled: StateFlow<Boolean> = _isEqEnabled.asStateFlow()

    private val _currentEqPreset = MutableStateFlow(prefs.getString("eq_preset", "Flat") ?: "Flat")
    val currentEqPreset: StateFlow<String> = _currentEqPreset.asStateFlow()

    data class EqState(val enabled: Boolean = false, val bands: List<EqBandInfo> = emptyList())
    private val _eqState = MutableStateFlow(EqState(enabled = prefs.getBoolean("eq_enabled", false)))
    val eqState: StateFlow<EqState> = _eqState.asStateFlow()

    // Queue & Shuffle Management
    private val _originalQueue = MutableStateFlow<List<TrackInfo>>(emptyList())
    private val _queue = MutableStateFlow<List<TrackInfo>>(emptyList())
    val queue: StateFlow<List<TrackInfo>> = _queue.asStateFlow()

    private val _isShuffleEnabled = MutableStateFlow(prefs.getBoolean("shuffle_enabled", false))
    val isShuffleEnabled: StateFlow<Boolean> = _isShuffleEnabled.asStateFlow()

    private val _repeatMode = MutableStateFlow(
        when (prefs.getString("repeat_mode", "OFF")) {
            "ALL" -> RepeatMode.ALL
            "ONE" -> RepeatMode.ONE
            else -> RepeatMode.OFF
        }
    )
    val repeatMode: StateFlow<RepeatMode> = _repeatMode.asStateFlow()

    // History for non-repeating shuffle
    private val unplayedShufflePool = mutableListOf<TrackInfo>()
    private val playHistory = mutableListOf<TrackInfo>()

    // Top Tracks & Playback History
    private val playCountPrefs = context.getSharedPreferences("veyl_play_counts", Context.MODE_PRIVATE)
    private val _topTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val topTracks: StateFlow<List<TrackInfo>> = _topTracks.asStateFlow()

    private val _lastAddedTracks = MutableStateFlow<List<TrackInfo>>(emptyList())
    val lastAddedTracks: StateFlow<List<TrackInfo>> = _lastAddedTracks.asStateFlow()

    private val _playbackHistory = MutableStateFlow<List<TrackInfo>>(emptyList())
    val playbackHistory: StateFlow<List<TrackInfo>> = _playbackHistory.asStateFlow()

    // High-resolution position flows (30 FPS for buttery smooth lyrics & scrubber)
    private val _currentPositionSec = MutableStateFlow(0.0)
    val currentPositionSec: StateFlow<Double> = _currentPositionSec.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    // Synced Lyrics Flow & Offset
    private val _currentLyrics = MutableStateFlow<TrackLyrics?>(null)
    val currentLyrics: StateFlow<TrackLyrics?> = _currentLyrics.asStateFlow()

    private val _lyricOffsetMs = MutableStateFlow(prefs.getLong("lyric_offset_ms", 0L))
    val lyricOffsetMs: StateFlow<Long> = _lyricOffsetMs.asStateFlow()

    private val _autoFetchLyrics = MutableStateFlow(prefs.getBoolean("auto_fetch_lyrics", true))
    val autoFetchLyrics: StateFlow<Boolean> = _autoFetchLyrics.asStateFlow()

    // Engine Tuning Options (Persisted)
    private val _gaplessEnabled = MutableStateFlow(prefs.getBoolean("gapless_enabled", true))
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(prefs.getFloat("crossfade_seconds", 0f))
    val crossfadeSeconds: StateFlow<Float> = _crossfadeSeconds.asStateFlow()

    private val _bufferFrameSize = MutableStateFlow(prefs.getInt("buffer_frame_size", 128))
    val bufferFrameSize: StateFlow<Int> = _bufferFrameSize.asStateFlow()

    // Dark / Light Theme Mode
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // -------------------------------------------------------------------------
    // Dynamic Color Palette & Theme Engine
    // -------------------------------------------------------------------------
    private val _selectedThemeId = MutableStateFlow(
        prefs.getString("theme_id", "monochrome_carbon") ?: "monochrome_carbon"
    )
    val selectedThemeId: StateFlow<String> = _selectedThemeId.asStateFlow()

    private val _customPrimary = MutableStateFlow(
        parseColorFromHex(prefs.getString("custom_primary_hex", "#FFFFFF") ?: "#FFFFFF", Color.White)
    )
    val customPrimary: StateFlow<Color> = _customPrimary.asStateFlow()

    private val _customSecondary = MutableStateFlow(
        parseColorFromHex(prefs.getString("custom_secondary_hex", "#94A3B8") ?: "#94A3B8", Color(0xFF94A3B8))
    )
    val customSecondary: StateFlow<Color> = _customSecondary.asStateFlow()

    private val _customBackground = MutableStateFlow(
        parseColorFromHex(prefs.getString("custom_bg_hex", "#121212") ?: "#121212", Color(0xFF121212))
    )
    val customBackground: StateFlow<Color> = _customBackground.asStateFlow()

    private fun loadSavedThemes(): List<SavedCustomTheme> {
        val jsonStr = prefs.getString("saved_custom_themes_json", "[]") ?: "[]"
        return try {
            val jsonArray = org.json.JSONArray(jsonStr)
            val list = mutableListOf<SavedCustomTheme>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                list.add(
                    SavedCustomTheme(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        primaryHex = obj.getString("primaryHex"),
                        secondaryHex = obj.getString("secondaryHex"),
                        backgroundHex = obj.getString("backgroundHex"),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistSavedThemes(list: List<SavedCustomTheme>) {
        try {
            val jsonArray = org.json.JSONArray()
            for (t in list) {
                val obj = org.json.JSONObject().apply {
                    put("id", t.id)
                    put("name", t.name)
                    put("primaryHex", t.primaryHex)
                    put("secondaryHex", t.secondaryHex)
                    put("backgroundHex", t.backgroundHex)
                    put("createdAt", t.createdAt)
                }
                jsonArray.put(obj)
            }
            prefs.edit().putString("saved_custom_themes_json", jsonArray.toString()).apply()
        } catch (e: Exception) {
            Log.e("AudioEngineController", "Failed to persist saved themes", e)
        }
    }

    private val _savedCustomThemes = MutableStateFlow(loadSavedThemes())
    val savedCustomThemes: StateFlow<List<SavedCustomTheme>> = _savedCustomThemes.asStateFlow()

    private fun loadInitialColorScheme(): VeylColorScheme {
        val themeId = prefs.getString("theme_id", "monochrome_carbon") ?: "monochrome_carbon"
        val isDark = prefs.getBoolean("is_dark_mode", true)

        val savedMatch = _savedCustomThemes.value.find { it.id == themeId }
        if (savedMatch != null) {
            val p = parseColorFromHex(savedMatch.primaryHex, Color.White)
            val s = parseColorFromHex(savedMatch.secondaryHex, Color.Gray)
            val bg = parseColorFromHex(savedMatch.backgroundHex, Color.Black)
            return buildVeylColorScheme(p, s, s, bg, isDark = isDark)
        }

        if (themeId.startsWith("custom")) {
            val p = parseColorFromHex(prefs.getString("custom_primary_hex", "#FFFFFF") ?: "#FFFFFF", Color.White)
            val s = parseColorFromHex(prefs.getString("custom_secondary_hex", "#94A3B8") ?: "#94A3B8", Color.Gray)
            val bg = parseColorFromHex(prefs.getString("custom_bg_hex", "#121212") ?: "#121212", Color(0xFF121212))
            return buildVeylColorScheme(p, s, s, bg, isDark = isDark)
        }

        val preset = CuratedPresets.find { it.id == themeId } ?: CuratedPresets.first()
        return buildVeylColorScheme(preset.primary, preset.secondary, preset.tertiary, preset.background, isDark = isDark)
    }

    private val _currentVeylColorScheme = MutableStateFlow(loadInitialColorScheme())
    val currentVeylColorScheme: StateFlow<VeylColorScheme> = _currentVeylColorScheme.asStateFlow()

    fun applyThemePreset(presetId: String) {
        val preset = CuratedPresets.find { it.id == presetId } ?: return
        _selectedThemeId.value = presetId
        _customPrimary.value = preset.primary
        _customSecondary.value = preset.secondary
        _customBackground.value = preset.background
        prefs.edit()
            .putString("theme_id", presetId)
            .putString("custom_primary_hex", preset.primary.toHex())
            .putString("custom_secondary_hex", preset.secondary.toHex())
            .putString("custom_bg_hex", preset.background.toHex())
            .apply()
        _currentVeylColorScheme.value = buildVeylColorScheme(
            primary = preset.primary,
            secondary = preset.secondary,
            tertiary = preset.tertiary,
            background = preset.background,
            isDark = _isDarkMode.value
        )
    }

    fun applyCustomPalette(primary: Color, secondary: Color, background: Color, themeId: String = "custom") {
        _selectedThemeId.value = themeId
        _customPrimary.value = primary
        _customSecondary.value = secondary
        _customBackground.value = background
        prefs.edit()
            .putString("theme_id", themeId)
            .putString("custom_primary_hex", primary.toHex())
            .putString("custom_secondary_hex", secondary.toHex())
            .putString("custom_bg_hex", background.toHex())
            .apply()
        _currentVeylColorScheme.value = buildVeylColorScheme(
            primary = primary,
            secondary = secondary,
            tertiary = secondary,
            background = background,
            isDark = _isDarkMode.value
        )
    }

    fun saveCustomTheme(name: String, primary: Color, secondary: Color, background: Color): SavedCustomTheme {
        val id = "custom_" + System.currentTimeMillis()
        val cleanName = if (name.trim().isEmpty()) "Custom Theme ${(_savedCustomThemes.value.size + 1)}" else name.trim()
        val newTheme = SavedCustomTheme(
            id = id,
            name = cleanName,
            primaryHex = primary.toHex(),
            secondaryHex = secondary.toHex(),
            backgroundHex = background.toHex()
        )
        val updated = _savedCustomThemes.value + newTheme
        _savedCustomThemes.value = updated
        persistSavedThemes(updated)
        applyCustomPalette(primary, secondary, background, themeId = id)
        return newTheme
    }

    fun deleteCustomTheme(themeId: String) {
        val updated = _savedCustomThemes.value.filterNot { it.id == themeId }
        _savedCustomThemes.value = updated
        persistSavedThemes(updated)
        if (_selectedThemeId.value == themeId) {
            applyThemePreset("monochrome_carbon")
        }
    }

    fun applySavedCustomTheme(theme: SavedCustomTheme) {
        val p = parseColorFromHex(theme.primaryHex, Color.White)
        val s = parseColorFromHex(theme.secondaryHex, Color.Gray)
        val bg = parseColorFromHex(theme.backgroundHex, Color.Black)
        _selectedThemeId.value = theme.id
        _customPrimary.value = p
        _customSecondary.value = s
        _customBackground.value = bg
        prefs.edit()
            .putString("theme_id", theme.id)
            .putString("custom_primary_hex", theme.primaryHex)
            .putString("custom_secondary_hex", theme.secondaryHex)
            .putString("custom_bg_hex", theme.backgroundHex)
            .apply()
        _currentVeylColorScheme.value = buildVeylColorScheme(
            primary = p,
            secondary = s,
            tertiary = s,
            background = bg,
            isDark = _isDarkMode.value
        )
    }

    private val _dynamicIslandEnabled = MutableStateFlow(prefs.getBoolean("dynamic_island_enabled", true))
    val dynamicIslandEnabled: StateFlow<Boolean> = _dynamicIslandEnabled.asStateFlow()

    fun setDynamicIslandEnabled(enabled: Boolean) {
        _dynamicIslandEnabled.value = enabled
        prefs.edit().putBoolean("dynamic_island_enabled", enabled).apply()
    }

    fun resetThemeToDefault() {
        applyThemePreset("monochrome_carbon")
    }

    private val _selectedRootPath = MutableStateFlow<String?>(null)
    val selectedRootPath: StateFlow<String?> = _selectedRootPath.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // DSD & Bit-Perfect Settings
    private val _dsdMode = MutableStateFlow(
        if (prefs.getString("dsd_mode", "pcm") == "dop") DsdModeEnum.DO_P else DsdModeEnum.PCM_DECIMATION
    )
    val dsdMode: StateFlow<DsdModeEnum> = _dsdMode.asStateFlow()

    private val _bitPerfectEnabled = MutableStateFlow(prefs.getBoolean("bit_perfect_enabled", true))
    val bitPerfectEnabled: StateFlow<Boolean> = _bitPerfectEnabled.asStateFlow()
    val isExclusiveMode: StateFlow<Boolean> = _bitPerfectEnabled.asStateFlow()

    fun toggleExclusiveMode(enabled: Boolean) {
        setBitPerfectEnabled(enabled)
    }

    private var currentOutputSampleRate: UInt = 48000u
    private var pollJob: Job? = null
    private var lastTrackUri: String? = null
    private var isTransitioning = false
    private var eqUpdateJob: Job? = null

    init {
        LyricsManager.init(context)
        initEngine()
        startPolling()
        loadSavedRootDirectory()
        observeUsbDacChanges()
    }

    private fun initEngine() {
        try {
            nativeEngine = AudiophileEngineHandle(48000f, 2u)
            nativeEngine?.setDsdMode(_dsdMode.value)
            currentOutputSampleRate = 48000u
            restoreEqState()
        } catch (e: Throwable) {
            Log.e("AudioEngineController", "Failed to initialize native AudiophileEngineHandle", e)
        }
    }

    private fun restoreEqState() {
        val enabled = _isEqEnabled.value
        nativeEngine?.setEqEnabled(enabled)
        val defaultBands = nativeEngine?.getEqBands() ?: emptyList()
        for (i in defaultBands.indices) {
            val savedGain = prefs.getFloat("eq_band_$i", defaultBands[i].gainDb)
            val b = defaultBands[i]
            nativeEngine?.setEqBand(i.toUInt(), savedGain, b.frequency, b.q)
        }
        refreshEq()
    }

    private fun observeUsbDacChanges() {
        scope.launch {
            connectedDac.collect { dac ->
                if (dac != null) {
                    Log.i("AudioEngineController", "USB DAC attached: ${dac.name} (Device ID: ${dac.id})")
                    if (_bitPerfectEnabled.value) {
                        val targetRate = currentOutputSampleRate.toInt()
                        val optimalRate = usbDacManager.getOptimalSampleRate(targetRate)
                        reconfigureHardwareOutput(optimalRate.toUInt(), dac.id, exclusive = true)
                    }
                } else {
                    Log.i("AudioEngineController", "USB DAC detached, returning to standard output")
                    reconfigureHardwareOutput(48000u, null, exclusive = false)
                }
            }
        }
    }

    private var lastTransitionTimestamp = 0L

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var tickCount = 0
            while (isActive) {
                try {
                    nativeEngine?.let { engine ->
                        val currentStatus = engine.getStatus()
                        val pos = currentStatus.positionSeconds
                        val dur = currentStatus.durationSeconds
                        val isPlaying = currentStatus.state == PlaybackStateEnum.PLAYING

                        _currentPositionSec.value = pos
                        _currentPositionMs.value = (pos * 1000.0).toLong()

                        val currentUri = currentStatus.currentTrack?.uri
                        val lastStatus = _status.value
                        val trackChanged = currentUri != null && currentUri != lastTrackUri

                        if (trackChanged) {
                            lastTrackUri = currentUri
                            isTransitioning = false
                            _currentLyrics.value = null
                            _currentPositionSec.value = 0.0
                            _currentPositionMs.value = 0L

                            // Record play count and history
                            recordTrackPlayed(currentUri)

                            // Load lyrics asynchronously on track change (with online LRCLIB fallback)
                            val curTrack = currentStatus.currentTrack
                            scope.launch {
                                val lyrics = LyricsManager.loadLyricsForTrack(
                                    trackUri = currentUri,
                                    title = curTrack?.title,
                                    artist = curTrack?.artist,
                                    album = curTrack?.album,
                                    durationSeconds = curTrack?.durationSeconds ?: 0.0,
                                    allowOnlineFetch = _autoFetchLyrics.value
                                )
                                if (_status.value?.currentTrack?.uri == currentUri) {
                                    _currentLyrics.value = lyrics
                                }
                            }
                            // Preload artwork into memory
                            ArtworkCache.loadArtwork(currentUri, 512)
                        }

                        // Update _status: on track change, playback state change, or throttled every ~3 ticks (~100ms)
                        val shouldUpdateStatus = trackChanged ||
                                lastStatus == null ||
                                lastStatus.state != currentStatus.state ||
                                lastStatus.sampleRate != currentStatus.sampleRate ||
                                (tickCount % 3 == 0)

                        if (shouldUpdateStatus) {
                            _status.value = currentStatus
                        }
                        tickCount++

                        // Automatic and seamless track progression
                        val now = System.currentTimeMillis()

                        // Reset transition lock once new track is playing at start
                        if (pos < 1.5 && isPlaying) {
                            isTransitioning = false
                        }

                        val isEnded = currentStatus.state == PlaybackStateEnum.ENDED ||
                                (dur > 2.0 && pos >= dur - 0.45 && isPlaying)

                        if (isEnded && !isTransitioning && (now - lastTransitionTimestamp > 1500L)) {
                            isTransitioning = true
                            lastTransitionTimestamp = now
                            Log.i("AudioEngineController", "Track completed ($pos / $dur, state=${currentStatus.state}). Auto-advancing next track.")
                            handleTrackEnded()
                        }

                        val delayTime = if (isPlaying) 33L else 150L
                        delay(delayTime)
                    } ?: run {
                        delay(200)
                    }
                } catch (e: Exception) {
                    Log.w("AudioEngineController", "Polling status error", e)
                    delay(200)
                }
            }
        }
    }

    private fun recordTrackPlayed(uri: String) {
        val currentCount = playCountPrefs.getInt(uri, 0)
        playCountPrefs.edit().putInt(uri, currentCount + 1).apply()

        val all = _libraryTracks.value
        val track = all.firstOrNull { it.uri == uri }
        if (track != null) {
            val updatedHistory = listOf(track) + _playbackHistory.value.filter { it.uri != uri }
            _playbackHistory.value = updatedHistory.take(50)
            updateDerivedTrackLists(all)
        }
    }

    private fun updateDerivedTrackLists(tracks: List<TrackInfo>) {
        if (tracks.isEmpty()) return
        // 1. Top Tracks sorted by play count descending
        val sortedByPlays = tracks.sortedByDescending { track ->
            playCountPrefs.getInt(track.uri, 0)
        }
        _topTracks.value = sortedByPlays.take(50)

        // 2. Last Added sorted by file last modified timestamp
        val sortedByDate = tracks.sortedByDescending { track ->
            try {
                java.io.File(track.uri).lastModified()
            } catch (e: Exception) {
                0L
            }
        }
        _lastAddedTracks.value = sortedByDate.take(50)
    }

    private fun handleTrackEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _status.value?.currentTrack?.let { playTrack(it) }
            }
            RepeatMode.ALL, RepeatMode.OFF -> {
                skipNext()
            }
        }
    }

    fun toggleTheme() {
        val newDark = !_isDarkMode.value
        _isDarkMode.value = newDark
        prefs.edit().putBoolean("is_dark_mode", newDark).apply()
        _currentVeylColorScheme.value = loadInitialColorScheme()
    }

    fun setRepeatMode(mode: RepeatMode) {
        _repeatMode.value = mode
        prefs.edit().putString("repeat_mode", mode.name).apply()
    }

    fun toggleRepeat() {
        val next = when (_repeatMode.value) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        setRepeatMode(next)
    }

    // ==========================================
    // Fisher-Yates Shuffle Engine
    // ==========================================
    fun toggleShuffle() {
        val newShuffle = !_isShuffleEnabled.value
        _isShuffleEnabled.value = newShuffle
        prefs.edit().putBoolean("shuffle_enabled", newShuffle).apply()

        val currentTrack = _status.value?.currentTrack
        if (newShuffle) {
            val orig = _queue.value.toMutableList()
            if (orig.isNotEmpty()) {
                val currentItem = orig.firstOrNull { it.uri == currentTrack?.uri }
                orig.removeAll { it.uri == currentTrack?.uri }
                
                val rng = Random()
                for (i in orig.size - 1 downTo 1) {
                    val j = rng.nextInt(i + 1)
                    Collections.swap(orig, i, j)
                }

                val shuffled = mutableListOf<TrackInfo>()
                if (currentItem != null) shuffled.add(currentItem)
                shuffled.addAll(orig)

                _queue.value = shuffled
                unplayedShufflePool.clear()
                unplayedShufflePool.addAll(orig)
            }
        } else {
            if (_originalQueue.value.isNotEmpty()) {
                _queue.value = _originalQueue.value
            }
        }
    }

    fun skipNext() {
        var q = _queue.value
        if (q.isEmpty()) {
            val lib = _libraryTracks.value
            if (lib.isNotEmpty()) {
                _queue.value = lib
                _originalQueue.value = lib
                q = lib
            } else {
                return
            }
        }

        val current = _status.value?.currentTrack
        val currentIndex = q.indexOfFirst { it.uri == current?.uri }

        val nextTrack = if (_isShuffleEnabled.value) {
            if (unplayedShufflePool.isEmpty()) {
                unplayedShufflePool.addAll(q.filter { it.uri != current?.uri })
            }
            if (unplayedShufflePool.isNotEmpty()) {
                val next = unplayedShufflePool.removeAt(0)
                if (current != null) playHistory.add(current)
                next
            } else {
                q.firstOrNull()
            }
        } else {
            val nextIndex = if (currentIndex != -1) (currentIndex + 1) % q.size else 0
            q[nextIndex]
        }

        nextTrack?.let { playTrack(it) }
    }

    fun skipPrevious() {
        val currentPos = _status.value?.positionSeconds ?: 0.0
        if (currentPos > 3.0) {
            seekTo(0.0)
            return
        }

        val q = _queue.value
        if (q.isEmpty()) return

        val current = _status.value?.currentTrack
        val prevTrack = if (_isShuffleEnabled.value && playHistory.isNotEmpty()) {
            val prev = playHistory.removeAt(playHistory.size - 1)
            if (current != null) unplayedShufflePool.add(0, current)
            prev
        } else {
            val currentIndex = q.indexOfFirst { it.uri == current?.uri }
            val prevIndex = if (currentIndex > 0) currentIndex - 1 else q.size - 1
            q[prevIndex]
        }

        prevTrack?.let { playTrack(it) }
    }

    fun toggleFavorite(uri: String) {
        playlistManager.toggleFavorite(uri)
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (index in currentQueue.indices) {
            currentQueue.removeAt(index)
            _queue.value = currentQueue
            if (!_isShuffleEnabled.value) {
                _originalQueue.value = currentQueue
            }
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices && fromIndex != toIndex) {
            val item = currentQueue.removeAt(fromIndex)
            currentQueue.add(toIndex, item)
            _queue.value = currentQueue
            if (!_isShuffleEnabled.value) {
                _originalQueue.value = currentQueue
            }
        }
    }

    fun playNext(track: TrackInfo) {
        val currentQueue = _queue.value.toMutableList()
        val current = _status.value?.currentTrack
        val insertIndex = if (current != null) {
            val idx = currentQueue.indexOfFirst { it.uri == current.uri }
            if (idx != -1) idx + 1 else 0
        } else {
            0
        }
        currentQueue.add(insertIndex.coerceIn(0, currentQueue.size), track)
        _queue.value = currentQueue
        if (!_isShuffleEnabled.value) {
            _originalQueue.value = currentQueue
        }
    }

    fun addToQueue(track: TrackInfo) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(track)
        _queue.value = currentQueue
        if (!_isShuffleEnabled.value) {
            _originalQueue.value = currentQueue
        }
    }

    fun clearQueue() {
        val current = _status.value?.currentTrack
        if (current != null) {
            _queue.value = listOf(current)
            _originalQueue.value = listOf(current)
        } else {
            _queue.value = emptyList()
            _originalQueue.value = emptyList()
        }
        unplayedShufflePool.clear()
        playHistory.clear()
    }

    fun getInlineLyrics(positionSeconds: Double): Triple<LyricLine?, LyricLine?, LyricLine?> {
        val lyrics = _currentLyrics.value ?: return Triple(null, null, null)
        val activeIdx = lyrics.findActiveIndex(positionSeconds)
        if (activeIdx < 0 || activeIdx >= lyrics.lines.size) {
            return Triple(null, lyrics.lines.firstOrNull(), lyrics.lines.getOrNull(1))
        }

        val prev = if (activeIdx > 0) lyrics.lines[activeIdx - 1] else null
        val active = lyrics.lines[activeIdx]
        val next = if (activeIdx + 1 < lyrics.lines.size) lyrics.lines[activeIdx + 1] else null

        return Triple(prev, active, next)
    }

    private fun loadSavedRootDirectory() {
        val savedPath = prefs.getString("root_audio_dir", null)
        if (savedPath != null && File(savedPath).exists()) {
            _selectedRootPath.value = savedPath
            scanDirectory(savedPath)
        } else {
            val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            val sdcardMusic = File("/storage/emulated/0/Music")
            val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

            val detectedPath = when {
                musicDir != null && musicDir.exists() && musicDir.canRead() -> musicDir.absolutePath
                sdcardMusic.exists() && sdcardMusic.canRead() -> sdcardMusic.absolutePath
                downloadDir != null && downloadDir.exists() && downloadDir.canRead() -> downloadDir.absolutePath
                else -> null
            }

            if (detectedPath != null) {
                _selectedRootPath.value = detectedPath
                prefs.edit().putString("root_audio_dir", detectedPath).apply()
                scanDirectory(detectedPath)
            }
        }
    }

    fun setRootDirectory(path: String) {
        prefs.edit().putString("root_audio_dir", path).apply()
        _selectedRootPath.value = path
        scanDirectory(path)
    }

    fun scanDirectory(path: String) {
        scope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val dir = File(path)
                if (dir.exists()) {
                    val tracks = libraryEngine.scanDirectory(dir.absolutePath)
                    Log.i("AudioEngineController", "Scanned ${tracks.size} tracks from $path")
                    withContext(Dispatchers.Main) {
                        _libraryTracks.value = tracks
                        if (_originalQueue.value.isEmpty() && tracks.isNotEmpty()) {
                            _originalQueue.value = tracks
                            _queue.value = tracks
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioEngineController", "Error scanning directory $path", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanFromTreeUri(treeUri: Uri) {
        scope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val resolvedPath = resolveTreeUriToPath(treeUri)
                val targetPath = if (resolvedPath != null && File(resolvedPath).exists()) {
                    resolvedPath
                } else {
                    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    if (musicDir != null && musicDir.exists()) musicDir.absolutePath else "/storage/emulated/0/Music"
                }

                prefs.edit().putString("root_audio_dir", targetPath).apply()
                _selectedRootPath.value = targetPath

                val tracks = libraryEngine.scanDirectory(targetPath)
                Log.i("AudioEngineController", "Scanned ${tracks.size} tracks from resolved path: $targetPath")
                withContext(Dispatchers.Main) {
                    _libraryTracks.value = tracks
                    updateDerivedTrackLists(tracks)
                    if (_originalQueue.value.isEmpty() && tracks.isNotEmpty()) {
                        _originalQueue.value = tracks
                        _queue.value = tracks
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioEngineController", "Error scanning tree uri: $treeUri", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    private fun resolveTreeUriToPath(uri: Uri): String? {
        try {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(uri) ?: return null
            val split = docId.split(":")
            val type = split[0]
            val relativePath = if (split.size > 1) split[1] else ""
            return if ("primary".equals(type, ignoreCase = true)) {
                if (relativePath.isNotEmpty()) "/storage/emulated/0/$relativePath" else "/storage/emulated/0"
            } else {
                if (relativePath.isNotEmpty()) "/storage/$type/$relativePath" else "/storage/$type"
            }
        } catch (e: Exception) {
            Log.w("AudioEngineController", "Failed to resolve tree URI to path: $uri", e)
            return null
        }
    }

    fun playTrackList(tracks: List<TrackInfo>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        _originalQueue.value = tracks
        _queue.value = tracks

        val startTrack = tracks.getOrNull(startIndex) ?: tracks.first()
        playTrack(startTrack)
    }

    fun playShuffled(tracks: List<TrackInfo>) {
        if (tracks.isEmpty()) return
        _originalQueue.value = tracks
        val shuffled = tracks.shuffled()
        _queue.value = shuffled
        _isShuffleEnabled.value = true
        prefs.edit().putBoolean("shuffle_enabled", true).apply()
        unplayedShufflePool.clear()
        unplayedShufflePool.addAll(shuffled.drop(1))
        playTrack(shuffled.first())
    }

    fun playTrack(track: TrackInfo) {
        scope.launch {
            if (_bitPerfectEnabled.value) {
                val isDsd = track.formatName.contains("DSD", ignoreCase = true) ||
                            track.uri.endsWith(".dsf", ignoreCase = true) ||
                            track.uri.endsWith(".dff", ignoreCase = true)

                val targetRate = if (isDsd) {
                    if (_dsdMode.value == DsdModeEnum.DO_P) {
                        if (track.sampleRate > 2822400u) 352800 else 176400
                    } else {
                        if (track.sampleRate > 2822400u) 176400 else 88200
                    }
                } else {
                    track.sampleRate.toInt()
                }

                val dac = connectedDac.value
                val optimalRate = usbDacManager.getOptimalSampleRate(targetRate).toUInt()
                val dacDeviceId = dac?.id

                if (optimalRate != currentOutputSampleRate) {
                    Log.i("AudioEngineController", "Switching DAC sample rate to $optimalRate Hz for bit-perfect playback")
                    reconfigureHardwareOutput(optimalRate, dacDeviceId, exclusive = true)
                }
            }

            isTransitioning = false
            lastTransitionTimestamp = System.currentTimeMillis()
            _currentLyrics.value = null
            _currentPositionSec.value = 0.0
            _currentPositionMs.value = 0L

            nativeEngine?.playTrack(
                uri = track.uri,
                title = track.title,
                artist = track.artist,
                album = track.album
            )

            // Re-apply user's persistent equalizer state on track transition
            nativeEngine?.setEqEnabled(_isEqEnabled.value)

            // Ensure queue always contains all tracks if queue was empty or single
            val currentQ = _queue.value
            if (currentQ.isEmpty() || currentQ.size <= 1) {
                val lib = _libraryTracks.value
                if (lib.isNotEmpty()) {
                    _queue.value = lib
                    _originalQueue.value = lib
                } else {
                    _queue.value = listOf(track)
                }
            } else if (!currentQ.any { it.uri == track.uri }) {
                _queue.value = currentQ + track
            }
        }
    }

    fun reconfigureHardwareOutput(sampleRate: UInt, deviceId: Int?, exclusive: Boolean): Boolean {
        return try {
            val success = nativeEngine?.reconfigureOutput(sampleRate, 2u, deviceId, exclusive) ?: false
            if (success) {
                currentOutputSampleRate = sampleRate
                nativeEngine?.setEqEnabled(_isEqEnabled.value)
                val bands = _eqBands.value
                for (i in bands.indices) {
                    val b = bands[i]
                    nativeEngine?.setEqBand(i.toUInt(), b.gainDb, b.frequency, b.q)
                }
                refreshEq()
            }
            success
        } catch (e: Exception) {
            Log.e("AudioEngineController", "Failed to reconfigure audio hardware output", e)
            false
        }
    }

    fun setDsdMode(mode: DsdModeEnum) {
        _dsdMode.value = mode
        prefs.edit().putString("dsd_mode", if (mode == DsdModeEnum.DO_P) "dop" else "pcm").apply()
        nativeEngine?.setDsdMode(mode)
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        _bitPerfectEnabled.value = enabled
        prefs.edit().putBoolean("bit_perfect_enabled", enabled).apply()
    }

    fun togglePlayPause() {
        val current = _status.value?.state ?: PlaybackStateEnum.STOPPED
        if (current == PlaybackStateEnum.PLAYING) {
            nativeEngine?.pause()
        } else {
            nativeEngine?.play()
        }
    }

    fun play() {
        nativeEngine?.play()
    }

    fun pause() {
        nativeEngine?.pause()
    }

    fun stop() {
        nativeEngine?.stop()
    }

    fun seekTo(seconds: Double) {
        val safeSeconds = seconds.coerceAtLeast(0.0)
        _currentPositionSec.value = safeSeconds
        _currentPositionMs.value = (safeSeconds * 1000.0).toLong()
        _status.value?.let { current ->
            _status.value = current.copy(positionSeconds = safeSeconds)
        }
        nativeEngine?.seek(safeSeconds)
    }

    fun setVolume(volume: Float) {
        nativeEngine?.setVolume(volume)
    }

    fun toggleEq(enabled: Boolean) {
        setEqEnabled(enabled)
    }

    fun setEqEnabled(enabled: Boolean) {
        _isEqEnabled.value = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        nativeEngine?.setEqEnabled(enabled)
        refreshEq()
    }

    fun setEqBandGain(index: Int, gainDb: Float) {
        prefs.edit().putFloat("eq_band_$index", gainDb).apply()
        _currentEqPreset.value = "Custom"
        prefs.edit().putString("eq_preset", "Custom").apply()
        val bands = _eqBands.value
        if (index in bands.indices) {
            val b = bands[index]
            updateEqBand(index.toUInt(), gainDb, b.frequency, b.q)
        }
    }

    fun setEqBand(index: Int, gainDb: Float) = setEqBandGain(index, gainDb)

    fun setEqPreset(name: String, gains: List<Float>) {
        _currentEqPreset.value = name
        prefs.edit().putString("eq_preset", name).apply()
        val bands = _eqBands.value
        for (i in gains.indices) {
            prefs.edit().putFloat("eq_band_$i", gains[i]).apply()
            if (i in bands.indices) {
                val b = bands[i]
                nativeEngine?.setEqBand(i.toUInt(), gains[i], b.frequency, b.q)
            }
        }
        refreshEq()
    }

    fun setEqPreamp(gainDb: Float) {
        nativeEngine?.setEqPreamp(gainDb)
        refreshEq()
    }

    fun updateEqBand(index: UInt, gainDb: Float, freq: Float, q: Float) {
        nativeEngine?.setEqBand(index, gainDb, freq, q)
        refreshEq()
    }

    fun setLyricOffset(offsetMs: Long) {
        _lyricOffsetMs.value = offsetMs
        prefs.edit().putLong("lyric_offset_ms", offsetMs).apply()
    }

    fun adjustLyricOffset(deltaMs: Long) {
        setLyricOffset(_lyricOffsetMs.value + deltaMs)
    }

    fun setAutoFetchLyrics(enabled: Boolean) {
        _autoFetchLyrics.value = enabled
        prefs.edit().putBoolean("auto_fetch_lyrics", enabled).apply()
    }

    fun reloadLyrics(allowOnline: Boolean = true) {
        val current = _status.value?.currentTrack ?: return
        scope.launch {
            val lyrics = LyricsManager.loadLyricsForTrack(
                trackUri = current.uri,
                title = current.title,
                artist = current.artist,
                album = current.album,
                durationSeconds = current.durationSeconds,
                allowOnlineFetch = allowOnline
            )
            _currentLyrics.value = lyrics
        }
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        prefs.edit().putBoolean("gapless_enabled", enabled).apply()
    }

    fun setCrossfadeSeconds(sec: Float) {
        _crossfadeSeconds.value = sec
        prefs.edit().putFloat("crossfade_seconds", sec).apply()
    }

    fun setBufferFrameSize(size: Int) {
        _bufferFrameSize.value = size
        prefs.edit().putInt("buffer_frame_size", size).apply()
    }

    fun refreshEq() {
        eqUpdateJob?.cancel()
        eqUpdateJob = scope.launch {
            kotlinx.coroutines.delay(20) // Debounce native FFI curve calls during slider drag
            nativeEngine?.let { engine ->
                val bands = engine.getEqBands()
                _eqBands.value = bands
                _eqCurve.value = engine.getEqCurve(100u)
                _eqState.value = EqState(enabled = _isEqEnabled.value, bands = bands)
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AudioEngineController? = null

        fun getInstance(context: Context): AudioEngineController {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioEngineController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
