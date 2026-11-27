package com.audiophile.player.engine

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.audiophile.player.online.JioSaavnService
import com.audiophile.player.online.OnlineSongItem
import com.audiophile.player.online.OnlineStreamManager
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
import java.util.concurrent.Executors
import androidx.compose.ui.graphics.Color
import com.audiophile.player.ui.theme.CuratedPresets
import com.audiophile.player.ui.theme.ThemePreset
import com.audiophile.player.ui.theme.VeylColorScheme
import com.audiophile.player.ui.theme.VeylDarkColorScheme
import com.audiophile.player.ui.theme.buildVeylColorScheme
import com.audiophile.player.ui.theme.parseColorFromHex
import com.audiophile.player.ui.theme.toHex
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class CustomEqPreset(
    val id: String,
    val name: String,
    val gains: List<Float>
)

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

enum class AppExperienceMode {
    LOCAL_FIRST,
    CLOUD_FIRST
}

enum class CrossfadeCurve(val id: String, val title: String, val description: String) {
    EQUAL_POWER("equal_power", "Equal Power (-3 dB)", "Constant acoustic power across transition with zero center sag"),
    LINEAR("linear", "Linear", "Direct linear volume slope between tracks"),
    S_CURVE("s_curve", "S-Curve", "Smooth S-shaped curve for ambient & classical transitions")
}


class AudioEngineController private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Default + Job())
    // All frequent FFI commands share one serial dispatcher. This removes UI-thread contention
    // during seek, Bluetooth/call focus events, and the position polling loop.
    private val nativeDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "VeylNativeEngine").apply { isDaemon = true }
    }.asCoroutineDispatcher()
    private val nativeScope = CoroutineScope(nativeDispatcher + SupervisorJob())
    /** UniFFI's engine handle is not safe to call from the UI, service and polling threads at once. */
    private val nativeEngineLock = Any()
    private val prefs = context.getSharedPreferences("audiophile_prefs", Context.MODE_PRIVATE)

    // USB DAC Hardware Manager
    val usbDacManager = UsbDacManager(context)
    val connectedDac: StateFlow<UsbDacInfo?> = usbDacManager.connectedDac

    // DAC Diagnostic Tone Generator
    val toneGenerator = DacDiagnosticToneGenerator()

    // Playlist & Favorites Manager
    val playlistManager = PlaylistManager(context)
    val favoriteUris: StateFlow<Set<String>> = playlistManager.favoriteUris
    val playlists: StateFlow<List<UserPlaylist>> = playlistManager.playlists

    // Native Rust Engine Handles
    private var nativeEngine: AudiophileEngineHandle? = null
    private val libraryEngine = LibraryEngine()
    private var pendingActiveTrack: TrackInfo? = null
    private var playTrackJob: Job? = null

    private inline fun <T> withNativeEngine(block: (AudiophileEngineHandle) -> T): T? =
        synchronized(nativeEngineLock) { nativeEngine?.let(block) }

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

    private val _customEqPresets = MutableStateFlow<List<CustomEqPreset>>(emptyList())
    val customEqPresets: StateFlow<List<CustomEqPreset>> = _customEqPresets.asStateFlow()

    data class EqState(val enabled: Boolean = false, val bands: List<EqBandInfo> = emptyList())
    private val _eqState = MutableStateFlow(EqState(enabled = prefs.getBoolean("eq_enabled", false)))
    val eqState: StateFlow<EqState> = _eqState.asStateFlow()

    // Headphone Crossfeed (Bauer / Meier / Chu Moy binaural model)
    private val _crossfeedPreset = MutableStateFlow(
        when (prefs.getString("crossfeed_preset", "OFF")) {
            "SUBTLE" -> uniffi.audiophile_core.CrossfeedPresetEnum.SUBTLE
            "STANDARD" -> uniffi.audiophile_core.CrossfeedPresetEnum.STANDARD
            "VINTAGE" -> uniffi.audiophile_core.CrossfeedPresetEnum.VINTAGE
            else -> uniffi.audiophile_core.CrossfeedPresetEnum.OFF
        }
    )
    val crossfeedPreset: StateFlow<uniffi.audiophile_core.CrossfeedPresetEnum> = _crossfeedPreset.asStateFlow()

    // Audiophile Sleep Timer with Smooth Logarithmic Fade-Out
    private val _sleepTimerActiveOption = MutableStateFlow<String?>(null)
    val sleepTimerActiveOption: StateFlow<String?> = _sleepTimerActiveOption.asStateFlow()

    private val _sleepTimerRemainingSeconds = MutableStateFlow<Int?>(null)
    val sleepTimerRemainingSeconds: StateFlow<Int?> = _sleepTimerRemainingSeconds.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var sleepTimerPreFadeVolume: Float = 1.0f
    private var isSleepTimerFading: Boolean = false

    // Auto-Pause on Disconnect (Headphones, Bluetooth, USB DAC)
    private val _pauseOnDisconnect = MutableStateFlow(prefs.getBoolean("pause_on_disconnect", true))
    val pauseOnDisconnect: StateFlow<Boolean> = _pauseOnDisconnect.asStateFlow()

    // Loudness Normalizer (EBU R128 / RMS target) & Soft-Knee Peak Limiter
    private val _loudnessNormalizerEnabled = MutableStateFlow(prefs.getBoolean("loudness_normalizer_enabled", false))
    val loudnessNormalizerEnabled: StateFlow<Boolean> = _loudnessNormalizerEnabled.asStateFlow()

    private val _loudnessTargetLufs = MutableStateFlow(prefs.getFloat("loudness_target_lufs", -14.0f))
    val loudnessTargetLufs: StateFlow<Float> = _loudnessTargetLufs.asStateFlow()

    private val _limiterEnabled = MutableStateFlow(prefs.getBoolean("limiter_enabled", true))
    val limiterEnabled: StateFlow<Boolean> = _limiterEnabled.asStateFlow()

    // Triode Tube Warmth & Analog Harmonic Exciter
    private val _tubeWarmthEnabled = MutableStateFlow(prefs.getBoolean("tube_warmth_enabled", false))
    val tubeWarmthEnabled: StateFlow<Boolean> = _tubeWarmthEnabled.asStateFlow()

    private val _tubeWarmthDrive = MutableStateFlow(prefs.getFloat("tube_warmth_drive", 0.30f))
    val tubeWarmthDrive: StateFlow<Float> = _tubeWarmthDrive.asStateFlow()

    // 360° Spatializer (Deprecated - fully bypassed for audiophile fidelity)
    private val _spatializerEnabled = MutableStateFlow(false)
    val spatializerEnabled: StateFlow<Boolean> = _spatializerEnabled.asStateFlow()

    private val _spatializerWidth = MutableStateFlow(1.0f)
    val spatializerWidth: StateFlow<Float> = _spatializerWidth.asStateFlow()

    private val _spatializerIsHeadphoneMode = MutableStateFlow(true)
    val spatializerIsHeadphoneMode: StateFlow<Boolean> = _spatializerIsHeadphoneMode.asStateFlow()

    // Sub-Bass Exciter (Bypassed by default for clean natural bass)
    private val _subBassEnabled = MutableStateFlow(false)
    val subBassEnabled: StateFlow<Boolean> = _subBassEnabled.asStateFlow()

    private val _subBassIntensity = MutableStateFlow(prefs.getFloat("sub_bass_intensity", 0.40f))
    val subBassIntensity: StateFlow<Float> = _subBassIntensity.asStateFlow()

    // Vocal Clarity (Bypassed by default for pure uncolored vocals)
    private val _vocalClarityEnabled = MutableStateFlow(false)
    val vocalClarityEnabled: StateFlow<Boolean> = _vocalClarityEnabled.asStateFlow()

    private val _vocalClarityIntensity = MutableStateFlow(prefs.getFloat("vocal_clarity_intensity", 0.35f))
    val vocalClarityIntensity: StateFlow<Float> = _vocalClarityIntensity.asStateFlow()

    // Precision L/R Channel Balance (-1.0 Left to +1.0 Right)
    private val _channelBalance = MutableStateFlow(prefs.getFloat("channel_balance", 0.0f))
    val channelBalance: StateFlow<Float> = _channelBalance.asStateFlow()

    // High-Fidelity Playback Speed Scaling (Rubato sinc resampler)
    private val _playbackSpeed = MutableStateFlow(1.0f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    // A-B Looping for critical listening & audiophile tuning
    private val _loopPointA = MutableStateFlow<Double?>(null)
    val loopPointA: StateFlow<Double?> = _loopPointA.asStateFlow()

    private val _loopPointB = MutableStateFlow<Double?>(null)
    val loopPointB: StateFlow<Double?> = _loopPointB.asStateFlow()

    val isLoopingAB: StateFlow<Boolean> = combine(_loopPointA, _loopPointB) { a, b ->
        a != null && b != null && b > a
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), false)

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

    // Prepared off the UI thread so opening Library does not rebuild a large collection on demand.
    private val _artistGroups = MutableStateFlow<List<Triple<String, List<TrackInfo>, Int>>>(emptyList())
    val artistGroups: StateFlow<List<Triple<String, List<TrackInfo>, Int>>> = _artistGroups.asStateFlow()
    private val _albumGroups = MutableStateFlow<List<Triple<String, String, List<TrackInfo>>>>(emptyList())
    val albumGroups: StateFlow<List<Triple<String, String, List<TrackInfo>>>> = _albumGroups.asStateFlow()

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

    private val _lyricsProvider = MutableStateFlow(
        LyricsProvider.fromId(prefs.getString("lyrics_provider", LyricsProvider.AUTO.id))
    )
    val lyricsProvider: StateFlow<LyricsProvider> = _lyricsProvider.asStateFlow()

    private val _romanizeLyrics = MutableStateFlow(prefs.getBoolean("romanize_lyrics", false))
    val romanizeLyrics: StateFlow<Boolean> = _romanizeLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    // Online Streaming Engine
    val onlineStreamManager = OnlineStreamManager(context)
    private val _isOnlineStreaming = MutableStateFlow(false)
    val isOnlineStreaming: StateFlow<Boolean> = _isOnlineStreaming.asStateFlow()

    private val _isOnlineBuffering = MutableStateFlow(false)
    val isOnlineBuffering: StateFlow<Boolean> = _isOnlineBuffering.asStateFlow()

    private val _currentOnlineSong = MutableStateFlow<OnlineSongItem?>(null)
    val currentOnlineSong: StateFlow<OnlineSongItem?> = _currentOnlineSong.asStateFlow()

    private val _downloadingUris = MutableStateFlow<Set<String>>(emptySet())
    val downloadingUris: StateFlow<Set<String>> = _downloadingUris.asStateFlow()

    private val _downloadedUris = MutableStateFlow<Set<String>>(emptySet())
    val downloadedUris: StateFlow<Set<String>> = _downloadedUris.asStateFlow()

    private val _autoFetchLyrics = MutableStateFlow(prefs.getBoolean("auto_fetch_lyrics", true))
    val autoFetchLyrics: StateFlow<Boolean> = _autoFetchLyrics.asStateFlow()

    // Engine Tuning Options (Persisted)
    private val _gaplessEnabled = MutableStateFlow(prefs.getBoolean("gapless_enabled", true))
    val gaplessEnabled: StateFlow<Boolean> = _gaplessEnabled.asStateFlow()

    private val _crossfadeSeconds = MutableStateFlow(prefs.getFloat("crossfade_seconds", 0f))
    val crossfadeSeconds: StateFlow<Float> = _crossfadeSeconds.asStateFlow()

    private val _crossfadeCurve = MutableStateFlow(
        runCatching {
            CrossfadeCurve.valueOf(prefs.getString("crossfade_curve", CrossfadeCurve.EQUAL_POWER.name) ?: CrossfadeCurve.EQUAL_POWER.name)
        }.getOrDefault(CrossfadeCurve.EQUAL_POWER)
    )
    val crossfadeCurve: StateFlow<CrossfadeCurve> = _crossfadeCurve.asStateFlow()

    private val _bufferFrameSize = MutableStateFlow(prefs.getInt("buffer_frame_size", 128))
    val bufferFrameSize: StateFlow<Int> = _bufferFrameSize.asStateFlow()

    private val _replayGainMode = MutableStateFlow(
        runCatching {
            uniffi.audiophile_core.ReplayGainModeEnum.valueOf(
                prefs.getString("replaygain_mode", "TRACK") ?: "TRACK"
            )
        }.getOrDefault(uniffi.audiophile_core.ReplayGainModeEnum.TRACK)
    )
    val replayGainMode: StateFlow<uniffi.audiophile_core.ReplayGainModeEnum> = _replayGainMode.asStateFlow()

    private val _autoEqPreamp = MutableStateFlow(prefs.getBoolean("auto_eq_preamp", true))
    val autoEqPreamp: StateFlow<Boolean> = _autoEqPreamp.asStateFlow()

    private val _xrunCount = MutableStateFlow(0)
    val xrunCount: StateFlow<Int> = _xrunCount.asStateFlow()

    // Dark / Light Theme Mode
    private val _isDarkMode = MutableStateFlow(prefs.getBoolean("is_dark_mode", true))
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // App Experience Mode (Local First vs Cloud First)
    private val _appExperienceMode = MutableStateFlow(
        try {
            AppExperienceMode.valueOf(prefs.getString("app_experience_mode", AppExperienceMode.LOCAL_FIRST.name) ?: AppExperienceMode.LOCAL_FIRST.name)
        } catch (_: Exception) {
            AppExperienceMode.LOCAL_FIRST
        }
    )
    val appExperienceMode: StateFlow<AppExperienceMode> = _appExperienceMode.asStateFlow()

    private val _hasCompletedOnboarding = MutableStateFlow(
        prefs.getBoolean("has_completed_onboarding", prefs.contains("app_experience_mode"))
    )
    val hasCompletedOnboarding: StateFlow<Boolean> = _hasCompletedOnboarding.asStateFlow()

    private val _hasConfiguredCloudPreferences = MutableStateFlow(
        prefs.getBoolean("has_configured_cloud_preferences", prefs.getString("app_experience_mode", null) == AppExperienceMode.CLOUD_FIRST.name && !prefs.getStringSet("selected_languages", emptySet()).isNullOrEmpty())
    )
    val hasConfiguredCloudPreferences: StateFlow<Boolean> = _hasConfiguredCloudPreferences.asStateFlow()

    fun markCloudPreferencesConfigured() {
        _hasConfiguredCloudPreferences.value = true
        prefs.edit().putBoolean("has_configured_cloud_preferences", true).apply()
    }

    private val _selectedLanguages = MutableStateFlow<Set<String>>(
        prefs.getStringSet("selected_languages", setOf("English", "Malayalam", "Tamil", "Hindi")) ?: setOf("English", "Malayalam", "Tamil", "Hindi")
    )
    val selectedLanguages: StateFlow<Set<String>> = _selectedLanguages.asStateFlow()

    private val _selectedArtists = MutableStateFlow<Set<String>>(
        prefs.getStringSet("selected_artists", emptySet()) ?: emptySet()
    )
    val selectedArtists: StateFlow<Set<String>> = _selectedArtists.asStateFlow()

    val onlineSongMetadataCache = java.util.concurrent.ConcurrentHashMap<String, OnlineSongItem>()

    private val _isAutoplayEnabled = MutableStateFlow(prefs.getBoolean("autoplay_enabled", true))
    val isAutoplayEnabled: StateFlow<Boolean> = _isAutoplayEnabled.asStateFlow()

    fun toggleAutoplay() {
        val next = !_isAutoplayEnabled.value
        _isAutoplayEnabled.value = next
        prefs.edit().putBoolean("autoplay_enabled", next).apply()
    }

    fun setAppExperienceMode(mode: AppExperienceMode) {
        _appExperienceMode.value = mode
        prefs.edit().putString("app_experience_mode", mode.name).apply()
    }

    fun completeOnboarding(mode: AppExperienceMode, languages: Set<String>, artists: Set<String>) {
        _appExperienceMode.value = mode
        _hasCompletedOnboarding.value = true
        _selectedLanguages.value = languages
        _selectedArtists.value = artists
        val configuredCloud = mode == AppExperienceMode.CLOUD_FIRST && languages.isNotEmpty()
        _hasConfiguredCloudPreferences.value = configuredCloud
        prefs.edit()
            .putString("app_experience_mode", mode.name)
            .putBoolean("has_completed_onboarding", true)
            .putBoolean("has_configured_cloud_preferences", configuredCloud)
            .putStringSet("selected_languages", languages)
            .putStringSet("selected_artists", artists)
            .apply()
    }

    fun updateMusicPreferences(languages: Set<String>, artists: Set<String>) {
        _selectedLanguages.value = languages
        _selectedArtists.value = artists
        _hasConfiguredCloudPreferences.value = true
        prefs.edit()
            .putStringSet("selected_languages", languages)
            .putStringSet("selected_artists", artists)
            .putBoolean("has_configured_cloud_preferences", true)
            .apply()
    }

    fun resetOnboarding() {
        _hasCompletedOnboarding.value = false
        _hasConfiguredCloudPreferences.value = false
        prefs.edit()
            .putBoolean("has_completed_onboarding", false)
            .putBoolean("has_configured_cloud_preferences", false)
            .apply()
    }

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


    private val _openNowPlayingEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val openNowPlayingEvent: SharedFlow<Unit> = _openNowPlayingEvent.asSharedFlow()

    fun requestOpenNowPlaying() {
        _openNowPlayingEvent.tryEmit(Unit)
    }

    fun resetThemeToDefault() {
        applyThemePreset("monochrome_carbon")
    }

    private val _selectedRootPath = MutableStateFlow<String?>(null)
    val selectedRootPath: StateFlow<String?> = _selectedRootPath.asStateFlow()

    // Home must not briefly show an empty-library prompt while the initial scan is starting.
    private val _isScanning = MutableStateFlow(true)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // DSD & Bit-Perfect Settings
    private val _dsdMode = MutableStateFlow(
        if (prefs.getString("dsd_mode", "pcm") == "dop") DsdModeEnum.DO_P else DsdModeEnum.PCM_DECIMATION
    )
    val dsdMode: StateFlow<DsdModeEnum> = _dsdMode.asStateFlow()

    private val _bitPerfectEnabled = MutableStateFlow(prefs.getBoolean("bit_perfect_enabled", true))
    val bitPerfectEnabled: StateFlow<Boolean> = _bitPerfectEnabled.asStateFlow()
    val isExclusiveMode: StateFlow<Boolean> = _bitPerfectEnabled.asStateFlow()

    private val _playDuringCalls = MutableStateFlow(prefs.getBoolean("play_during_calls", false))
    val playDuringCalls: StateFlow<Boolean> = _playDuringCalls.asStateFlow()

    fun setPlayDuringCalls(enabled: Boolean) {
        _playDuringCalls.value = enabled
        prefs.edit().putBoolean("play_during_calls", enabled).apply()
    }

    fun toggleExclusiveMode(enabled: Boolean) {
        setBitPerfectEnabled(enabled)
    }

    private var currentOutputSampleRate: UInt = 48000u

    val hardwareOutputSummary: StateFlow<String> = combine(
        connectedDac,
        _bitPerfectEnabled,
        _status
    ) { dac, bitPerfect, status ->
        val outRate = currentOutputSampleRate.toInt()
        val rateDisplay = if (outRate >= 1000) "${outRate / 1000} kHz" else "$outRate Hz"
        if (dac != null && bitPerfect) {
            "DIRECT USB • ${dac.name} • $rateDisplay"
        } else if (dac != null) {
            "USB DAC • ${dac.name} • $rateDisplay"
        } else {
            "INTERNAL AUDIO • $rateDisplay • BIT-PERFECT"
        }
    }.stateIn(scope, SharingStarted.WhileSubscribed(5000), "INTERNAL AUDIO • 48 kHz")
    private var pollJob: Job? = null
    private var lastTrackUri: String? = null
    private var isTransitioning = false
    private var eqUpdateJob: Job? = null

    init {
        LyricsManager.init(context)
        loadCachedLibraryTracks()
        initEngine()
        startPolling()
        loadPlaybackHistory()
        loadSavedRootDirectory()
        observeUsbDacChanges()
        registerAudioDeviceListener()
        loadCustomEqPresets()
        nativeScope.launch(Dispatchers.IO) {
            onlineStreamManager.cleanAllStreamCaches()
        }
    }

    private fun toNativeCrossfadeCurve(curve: CrossfadeCurve): uniffi.audiophile_core.CrossfadeCurveEnum = when (curve) {
        CrossfadeCurve.LINEAR -> uniffi.audiophile_core.CrossfadeCurveEnum.LINEAR
        CrossfadeCurve.EQUAL_POWER -> uniffi.audiophile_core.CrossfadeCurveEnum.EQUAL_POWER
        CrossfadeCurve.S_CURVE -> uniffi.audiophile_core.CrossfadeCurveEnum.S_CURVE
    }

    private fun syncAllDspState(engine: AudiophileEngineHandle) {
        engine.setDsdMode(_dsdMode.value)
        engine.setCrossfeedPreset(_crossfeedPreset.value)
        engine.setLoudnessNormalizerEnabled(false)
        engine.setTubeWarmthEnabled(false)
        engine.setSpatializerEnabled(false)
        engine.setSubBassEnabled(false)
        engine.setVocalClarityEnabled(false)
        engine.setChannelBalance(_channelBalance.value)
        engine.setLimiterEnabled(false)
        engine.setPlaybackSpeed(_playbackSpeed.value)
        engine.setGaplessEnabled(_gaplessEnabled.value)
        engine.setCrossfade(_crossfadeSeconds.value, toNativeCrossfadeCurve(_crossfadeCurve.value))
        engine.setReplaygainMode(_replayGainMode.value)
        engine.setEqEnabled(_isEqEnabled.value)
    }

    private fun initEngine() {
        try {
            synchronized(nativeEngineLock) {
                nativeEngine = AudiophileEngineHandle(48000f, 2u)
                nativeEngine?.let { syncAllDspState(it) }
                currentOutputSampleRate = nativeEngine?.getOutputSampleRate() ?: 48000u
            }
            restoreEqState()
        } catch (e: Throwable) {
            Log.e("AudioEngineController", "Failed to initialize native AudiophileEngineHandle", e)
        }
    }

    private fun registerAudioDeviceListener() {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                val hasOutput = addedDevices?.any { it.isSink } == true
                if (hasOutput) {
                    Log.i("AudioEngineController", "Audio output device added (e.g. Bluetooth/Headphones). Refreshing hardware stream...")
                    scope.launch {
                        delay(250) // Let Android audio HAL settle routing
                        refreshHardwareOutput()
                        restoreEqState()
                    }
                }
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                val hasOutput = removedDevices?.any { it.isSink } == true
                if (hasOutput) {
                    Log.i("AudioEngineController", "Audio output device removed. Refreshing hardware stream...")
                    if (_pauseOnDisconnect.value) {
                        pause()
                    }
                    scope.launch {
                        delay(250) // Let Android audio HAL settle routing
                        refreshHardwareOutput()
                        restoreEqState()
                    }
                }
            }
        }
        audioManager.registerAudioDeviceCallback(deviceCallback, null)
    }

    private fun getActiveOutputDeviceKey(): String {
        val dac = connectedDac.value
        if (dac != null) {
            return "usb_${dac.name.replace(Regex("[^a-zA-Z0-9]"), "_").take(32)}"
        }
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return "default"
        val sinks = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val priorityDev = sinks.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        if (priorityDev != null) {
            val name = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                priorityDev.productName?.toString()?.takeIf { it.isNotBlank() } ?: priorityDev.type.toString()
            } else {
                priorityDev.type.toString()
            }
            return "ext_${name.replace(Regex("[^a-zA-Z0-9]"), "_").take(32)}"
        }
        return "speaker"
    }

    private fun getOptimalNonUsbSampleRate(requestedRate: Int): Int {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return 48000
        val sinks = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val activeSink = sinks.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        } ?: return 48000

        val supported = activeSink.sampleRates.filter { it > 0 }
        if (supported.isEmpty()) {
            val mixerRate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)?.toIntOrNull()
            return if (mixerRate != null && mixerRate > 0) mixerRate else 48000
        }
        if (supported.contains(requestedRate)) return requestedRate
        val multiples = supported.filter { it % requestedRate == 0 || requestedRate % it == 0 }
        if (multiples.isNotEmpty()) return multiples.maxOrNull() ?: 48000
        return if (supported.contains(48000)) 48000 else supported.maxOrNull() ?: 48000
    }

    private fun applyAutoPreampForBands(gains: List<Float>) {
        if (!_autoEqPreamp.value) {
            withNativeEngine { it.setEqPreamp(0f) }
            return
        }
        val maxBoost = gains.maxOfOrNull { it.coerceAtLeast(0f) } ?: 0f
        withNativeEngine { it.setEqPreamp(-maxBoost) }
    }

    private fun restoreEqState() {
        val enabled = _isEqEnabled.value
        val devKey = getActiveOutputDeviceKey()
        withNativeEngine { it.setEqEnabled(enabled) }
        val defaultBands = withNativeEngine { it.getEqBands() } ?: emptyList()
        val resolvedGains = mutableListOf<Float>()
        for (i in defaultBands.indices) {
            val savedGain = if (prefs.contains("eq_${devKey}_band_$i")) {
                prefs.getFloat("eq_${devKey}_band_$i", 0f)
            } else {
                prefs.getFloat("eq_band_$i", defaultBands[i].gainDb)
            }
            resolvedGains.add(savedGain)
            val b = defaultBands[i]
            withNativeEngine { it.setEqBand(i.toUInt(), savedGain, b.frequency, b.q) }
        }
        applyAutoPreampForBands(resolvedGains)
        refreshEq()
    }

    private fun observeUsbDacChanges() {
        scope.launch {
            var previousDac: UsbDacInfo? = null
            connectedDac.collect { dac ->
                if (previousDac == null && dac == null) {
                    return@collect
                }
                if (dac != null && dac.id != previousDac?.id) {
                    previousDac = dac
                    Log.i("AudioEngineController", "USB DAC attached: ${dac.name} (Device ID: ${dac.id})")
                    if (_bitPerfectEnabled.value) {
                        val targetRate = currentOutputSampleRate.toInt()
                        val optimalRate = usbDacManager.getOptimalSampleRate(targetRate)
                        reconfigureHardwareOutput(optimalRate.toUInt(), dac.id, exclusive = true)
                    }
                } else if (dac == null && previousDac != null) {
                    previousDac = null
                    Log.i("AudioEngineController", "USB DAC detached, returning to standard output")
                    // Unplugging a physical USB DAC mid-playback must instantaneously pause
                    // to prevent audio blast through the phone speaker at high DAC volume levels
                    pause()
                    reconfigureHardwareOutput(48000u, null, exclusive = false)
                }
            }
        }
    }

    @Volatile var isPlaybackStalled: Boolean = false
        private set

    private var lastTransitionTimestamp = 0L

    @Volatile private var activeCueTrack: TrackInfo? = null
    @Volatile private var activeCueRange: CueSheetParser.CueRange? = null

    private fun startPolling() {
        pollJob?.cancel()
        pollJob = nativeScope.launch {
            var tickCount = 0
            var lastTrackPos = -1.0
            var stalledTicks = 0
            while (isActive) {
                try {
                    withNativeEngine { it.getStatus() }?.let { currentStatus ->
                        val pos = currentStatus.positionSeconds
                        val dur = currentStatus.durationSeconds
                        val isPlaying = currentStatus.state == PlaybackStateEnum.PLAYING

                        // Detect stalled AAudio output stream (e.g. after phone call disconnects stream)
                        if (isPlaying && !_isOnlineBuffering.value && !isTransitioning) {
                            if (pos == lastTrackPos && pos > 0.0) {
                                stalledTicks++
                                if (stalledTicks >= 35) {
                                    isPlaybackStalled = true
                                }
                                if (stalledTicks == 35) {
                                    Log.w("AudioEngineController", "Audio output stream stalled at pos $pos. Auto-recovering hardware stream...")
                                    refreshHardwareOutput()
                                    withNativeEngine { it.play() }
                                }
                            } else {
                                stalledTicks = 0
                                isPlaybackStalled = false
                                lastTrackPos = pos
                            }
                        } else {
                            stalledTicks = 0
                            isPlaybackStalled = false
                            lastTrackPos = pos
                        }

                        // Enforce A-B loop segment
                        val a = _loopPointA.value
                        val b = _loopPointB.value
                        val cue = activeCueRange
                        val cueTrack = activeCueTrack
                        val effectivePos = if (cue != null) {
                            (pos - cue.startSec).coerceIn(0.0, (cue.endSec - cue.startSec).coerceAtLeast(1.0))
                        } else pos

                        if (a != null && b != null && b > a && effectivePos >= b) {
                            seekTo(a)
                        }

                        _currentPositionSec.value = effectivePos
                        _currentPositionMs.value = (effectivePos * 1000.0).toLong()

                        val normalizedStatus = if (cue != null && cueTrack != null) {
                            val cueDur = (cue.endSec - cue.startSec).coerceAtLeast(1.0)
                            currentStatus.copy(
                                currentTrack = cueTrack,
                                positionSeconds = effectivePos,
                                durationSeconds = cueDur
                            )
                        } else currentStatus

                        val currentUri = normalizedStatus.currentTrack?.uri
                        val lastStatus = _status.value
                        val trackChanged = currentUri != null && currentUri != lastTrackUri

                        if (trackChanged) {
                            lastTrackUri = currentUri
                            isTransitioning = false
                            _currentLyrics.value = null
                            _currentPositionSec.value = 0.0
                            _currentPositionMs.value = 0L
                            clearLoop()

                            if (pendingActiveTrack?.uri == currentUri) {
                                pendingActiveTrack = null
                            }

                            // Record play count and history with active track info
                            val curTrack = normalizedStatus.currentTrack
                            recordTrackPlayed(currentUri, curTrack)

                            // Load lyrics asynchronously on track change across configured provider(s)
                            scope.launch {
                                _isLoadingLyrics.value = true
                                val lyrics = try {
                                    LyricsManager.loadLyricsForTrack(
                                        trackUri = currentUri,
                                        title = curTrack?.title,
                                        artist = curTrack?.artist,
                                        album = curTrack?.album,
                                        durationSeconds = curTrack?.durationSeconds ?: 0.0,
                                        allowOnlineFetch = _autoFetchLyrics.value,
                                        preferredProvider = _lyricsProvider.value
                                    )
                                } finally {
                                    _isLoadingLyrics.value = false
                                }
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
                                lastStatus.state != normalizedStatus.state ||
                                lastStatus.sampleRate != normalizedStatus.sampleRate ||
                                (tickCount % 3 == 0)

                        if (shouldUpdateStatus) {
                            if (_isOnlineStreaming.value && _currentOnlineSong.value != null) {
                                val s = _currentOnlineSong.value!!
                                val enrichedTrack = normalizedStatus.currentTrack?.copy(
                                    title = s.title,
                                    artist = s.artist,
                                    album = s.album,
                                    hasArtwork = true
                                )
                                _status.value = normalizedStatus.copy(currentTrack = enrichedTrack)
                            } else {
                                _status.value = normalizedStatus
                            }
                            if (tickCount % 15 == 0) {
                                withNativeEngine { it.getXrunCount() }?.let { _xrunCount.value = it }
                            }
                        }
                        tickCount++

                        // Automatic and seamless track progression
                        val now = System.currentTimeMillis()

                        // Reset transition lock once new track is playing at start
                        if (effectivePos < 1.5 && isPlaying) {
                            isTransitioning = false
                        }

                        val cueEnded = cue != null && isPlaying && cue.endSec > cue.startSec && pos >= (cue.endSec - 0.12)
                        val isEnded = normalizedStatus.state == PlaybackStateEnum.ENDED || cueEnded

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

    private fun trackToJson(t: TrackInfo): org.json.JSONObject {
        return org.json.JSONObject().apply {
            put("uri", t.uri)
            put("title", t.title)
            put("artist", t.artist ?: "")
            put("album", t.album ?: "")
            put("albumArtist", t.albumArtist ?: "")
            put("genre", t.genre ?: "")
            put("durationSeconds", t.durationSeconds)
            put("sampleRate", t.sampleRate.toInt())
            put("bitDepth", t.bitDepth?.toInt() ?: 16)
            put("bitrate", t.bitrate?.toInt() ?: 320000)
            put("channels", t.channels.toInt())
            put("formatName", t.formatName)
            put("hasArtwork", t.hasArtwork)
            val art = ArtworkCache.getOnlineArtwork(t.uri)
                ?: ArtworkCache.getOnlineArtwork(t.title.lowercase())
            if (!art.isNullOrBlank()) {
                put("artworkUrl", art)
            }
        }
    }

    private fun jsonToTrack(obj: org.json.JSONObject): TrackInfo {
        val uri = obj.getString("uri")
        val artUrl = obj.optString("artworkUrl")
        if (artUrl.isNotBlank()) {
            ArtworkCache.registerOnlineArtwork(uri, artUrl)
            val title = obj.optString("title")
            if (title.isNotBlank()) {
                ArtworkCache.registerOnlineArtwork(title.lowercase(), artUrl)
            }
        }
        return TrackInfo(
            uri = uri,
            title = obj.getString("title"),
            artist = obj.optString("artist", "Unknown Artist"),
            album = obj.optString("album", ""),
            albumArtist = obj.optString("albumArtist").takeIf { it.isNotBlank() },
            genre = obj.optString("genre").takeIf { it.isNotBlank() },
            year = null,
            trackNumber = null,
            discNumber = null,
            durationSeconds = obj.optDouble("durationSeconds", 0.0),
            sampleRate = obj.optInt("sampleRate", 44100).toUInt(),
            bitDepth = obj.optInt("bitDepth", 16).toUByte(),
            bitrate = obj.optInt("bitrate", 320000).toUInt(),
            channels = obj.optInt("channels", 2).toUByte(),
            formatName = obj.optString("formatName", "FLAC"),
            hasArtwork = obj.optBoolean("hasArtwork", true)
        )
    }

    private fun loadPlaybackHistory() {
        val tracksJson = playCountPrefs.getString("playback_history_tracks_json", null)
        if (!tracksJson.isNullOrBlank()) {
            try {
                val arr = org.json.JSONArray(tracksJson)
                val list = mutableListOf<TrackInfo>()
                for (i in 0 until arr.length()) {
                    val track = jsonToTrack(arr.getJSONObject(i))
                    // Purge stale or transient cache stream URIs
                    if (!track.uri.contains("veyl_online_stream_") && !track.title.startsWith("veyl_online_stream_")) {
                        list.add(track)
                    }
                }
                if (list.isNotEmpty()) {
                    _playbackHistory.value = list
                    return
                }
            } catch (e: Exception) {
                Log.w("AudioEngineController", "Error parsing playback_history_tracks_json", e)
            }
        }

        val json = playCountPrefs.getString("playback_history_uris", "[]") ?: "[]"
        try {
            val arr = org.json.JSONArray(json)
            val uris = mutableListOf<String>()
            for (i in 0 until arr.length()) {
                val u = arr.getString(i)
                if (u.isNotBlank()) uris.add(u)
            }
            if (uris.isNotEmpty()) {
                val tracks = uris.mapNotNull { resolveTrackInfo(it) }
                _playbackHistory.value = tracks
            }
        } catch (e: Exception) {
            Log.w("AudioEngineController", "Error loading playback history", e)
        }
    }

    private fun persistPlaybackHistory(history: List<TrackInfo>) {
        try {
            val arrUris = org.json.JSONArray()
            val arrTracks = org.json.JSONArray()
            history.forEach {
                arrUris.put(it.uri)
                arrTracks.put(trackToJson(it))
            }
            playCountPrefs.edit()
                .putString("playback_history_uris", arrUris.toString())
                .putString("playback_history_tracks_json", arrTracks.toString())
                .apply()
        } catch (e: Exception) {
            Log.w("AudioEngineController", "Error persisting playback history", e)
        }
    }

    private fun getLibraryCacheFile(): File = File(context.filesDir, "veyl_library_cache.json")

    private fun loadCachedLibraryTracks() {
        try {
            val cacheFile = getLibraryCacheFile()
            if (!cacheFile.exists()) return
            val content = cacheFile.readText()
            if (content.isBlank()) return
            val arr = org.json.JSONArray(content)
            val list = ArrayList<TrackInfo>(arr.length())
            for (i in 0 until arr.length()) {
                list.add(jsonToTrack(arr.getJSONObject(i)))
            }
            if (list.isNotEmpty()) {
                _libraryTracks.value = list
                _isScanning.value = false
                updateDerivedTrackLists(list)
                if (_originalQueue.value.isEmpty()) {
                    _originalQueue.value = list
                    _queue.value = list
                }
                Log.i("AudioEngineController", "Instantly loaded ${list.size} tracks from warm cache in <5ms")
            }
        } catch (e: Exception) {
            Log.w("AudioEngineController", "Error loading library tracks cache", e)
        }
    }

    private fun persistLibraryTracksCache(tracks: List<TrackInfo>) {
        if (tracks.isEmpty()) return
        scope.launch(Dispatchers.IO) {
            try {
                val arr = org.json.JSONArray()
                tracks.forEach { arr.put(trackToJson(it)) }
                getLibraryCacheFile().writeText(arr.toString())
            } catch (e: Exception) {
                Log.w("AudioEngineController", "Error persisting library tracks cache", e)
            }
        }
    }

    fun updateTrackMetadata(
        oldUri: String,
        newTitle: String,
        newArtist: String,
        newAlbum: String,
        newYear: UInt?,
        newArtworkUrl: String?,
        newUri: String = oldUri
    ) {
        val oldF = File(oldUri)
        val oldFName = oldF.name
        val oldCleanPath = oldUri.removePrefix("file://")
        val newCleanPath = newUri.removePrefix("file://")

        fun isMatch(uri: String): Boolean {
            if (uri == oldUri || uri == oldCleanPath || uri == "file://$oldCleanPath") return true
            val f = File(uri.removePrefix("file://"))
            return f.name == oldFName
        }

        // 1. Update library tracks
        val currentLib = _libraryTracks.value
        val updatedLib = currentLib.map { track ->
            if (isMatch(track.uri)) {
                track.copy(
                    uri = newCleanPath,
                    title = newTitle,
                    artist = newArtist,
                    album = newAlbum,
                    year = newYear ?: track.year,
                    hasArtwork = true
                )
            } else {
                track
            }
        }
        _libraryTracks.value = updatedLib
        updateDerivedTrackLists(updatedLib)
        persistLibraryTracksCache(updatedLib)

        // 2. Update current playing track in _status if matched
        val curStatus = _status.value
        val current = curStatus?.currentTrack
        if (current != null && isMatch(current.uri)) {
            val updated = current.copy(
                uri = newCleanPath,
                title = newTitle,
                artist = newArtist,
                album = newAlbum,
                year = newYear ?: current.year,
                hasArtwork = true
            )
            _status.value = curStatus.copy(currentTrack = updated)
            pendingActiveTrack = updated
        }

        // 3. Update active queue and original queue
        val currentQueue = _queue.value
        if (currentQueue.any { isMatch(it.uri) }) {
            _queue.value = currentQueue.map { track ->
                if (isMatch(track.uri)) {
                    track.copy(
                        uri = newCleanPath,
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum,
                        year = newYear ?: track.year,
                        hasArtwork = true
                    )
                } else track
            }
        }
        val origQ = _originalQueue.value
        if (origQ.any { isMatch(it.uri) }) {
            _originalQueue.value = origQ.map { track ->
                if (isMatch(track.uri)) {
                    track.copy(
                        uri = newCleanPath,
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum,
                        year = newYear ?: track.year,
                        hasArtwork = true
                    )
                } else track
            }
        }

        // 4. Update Playback History
        val curHist = _playbackHistory.value
        if (curHist.any { isMatch(it.uri) }) {
            val updatedHistory = curHist.map { track ->
                if (isMatch(track.uri)) {
                    track.copy(
                        uri = newCleanPath,
                        title = newTitle,
                        artist = newArtist,
                        album = newAlbum,
                        year = newYear ?: track.year,
                        hasArtwork = true
                    )
                } else track
            }
            _playbackHistory.value = updatedHistory
            persistPlaybackHistory(updatedHistory)
        }

        // 5. Update PlaylistManager if URI changed
        if (oldUri != newCleanPath) {
            playlistManager.updateTrackUri(oldUri, newCleanPath)
            playlistManager.updateTrackUri(oldCleanPath, newCleanPath)
            playlistManager.updateTrackUri("file://$oldCleanPath", newCleanPath)
        }

        // 6. Invalidate old cache and register fresh in ArtworkCache
        ArtworkCache.invalidateTrackArtwork(oldUri, oldCleanPath, newCleanPath)
        val newF = File(newCleanPath)
        if (!newArtworkUrl.isNullOrBlank()) {
            ArtworkCache.registerOnlineArtwork(newCleanPath, newArtworkUrl)
            ArtworkCache.registerOnlineArtwork("file://$newCleanPath", newArtworkUrl)
            ArtworkCache.registerOnlineArtwork(newF.name, newArtworkUrl)
            ArtworkCache.registerOnlineArtwork(newTitle.lowercase(), newArtworkUrl)
        }
    }

    fun recordTrackPlayed(uri: String, directTrack: TrackInfo? = null) {
        val currentCount = playCountPrefs.getInt(uri, 0)
        playCountPrefs.edit().putInt(uri, currentCount + 1).apply()

        val onlineSong = _currentOnlineSong.value
        val isOnline = uri.contains("veyl_online_stream_") || uri.startsWith("online://") || directTrack?.genre == "Online Stream" || _isOnlineStreaming.value

        val track = if (isOnline && onlineSong != null) {
            val canonicalUri = if (onlineSong.id.startsWith("online://")) onlineSong.id else "online://${onlineSong.id}"
            val artUrl = ArtworkCache.getOnlineArtwork(canonicalUri)
                ?: ArtworkCache.getOnlineArtwork(onlineSong.id)
                ?: ArtworkCache.getOnlineArtwork(onlineSong.title.lowercase())
                ?: onlineSong.thumbnailUrl

            if (artUrl.isNotBlank()) {
                ArtworkCache.registerOnlineArtwork(canonicalUri, artUrl)
                ArtworkCache.registerOnlineArtwork(onlineSong.title.lowercase(), artUrl)
            }

            TrackInfo(
                uri = canonicalUri,
                title = onlineSong.title,
                artist = onlineSong.artist,
                album = onlineSong.album,
                albumArtist = onlineSong.artist,
                genre = "Online Stream",
                year = null,
                trackNumber = null,
                discNumber = null,
                durationSeconds = onlineSong.durationSeconds,
                sampleRate = 44100u,
                bitDepth = 16u,
                bitrate = 320000u,
                channels = 2u,
                formatName = "AAC 320k (Studio Master)",
                hasArtwork = true
            )
        } else {
            val all = _libraryTracks.value
            all.firstOrNull { it.uri == uri }
                ?: directTrack
                ?: resolveTrackInfo(uri)
                ?: _queue.value.firstOrNull { it.uri == uri }
                ?: _status.value?.currentTrack?.takeIf { it.uri == uri }
        }

        if (track != null) {
            val updatedHistory = (listOf(track) + _playbackHistory.value.filter {
                it.uri != track.uri &&
                !it.uri.contains("veyl_online_stream_") &&
                (track.title.isBlank() || !it.title.equals(track.title, ignoreCase = true) || !it.artist.equals(track.artist, ignoreCase = true))
            }).take(50)
            _playbackHistory.value = updatedHistory
            persistPlaybackHistory(updatedHistory)
            updateDerivedTrackLists(_libraryTracks.value)
        }
    }

    fun deleteTrack(track: TrackInfo, onDeleteComplete: (() -> Unit)? = null) {
        val uri = track.uri
        val fileName = File(uri).name
        val targetFile = onlineStreamManager.getTargetStorageFileForTrack(track)

        // 1. If currently playing this track, stop or advance
        val currentUri = _status.value?.currentTrack?.uri
        val isCurrentlyPlaying = currentUri == uri ||
                (currentUri != null && (File(currentUri).name == fileName || File(currentUri).name == targetFile.name))
        if (isCurrentlyPlaying) {
            val q = _queue.value
            val currentIdx = q.indexOfFirst { it.uri == currentUri }
            if (q.size > 1 && currentIdx != -1 && currentIdx < q.size - 1) {
                skipNext()
            } else if (q.size > 1) {
                val next = q.firstOrNull { it.uri != currentUri }
                if (next != null) playTrack(next) else pause()
            } else {
                pause()
                _queue.value = emptyList()
                _originalQueue.value = emptyList()
            }
        }

        // 2. Remove from Queue & Original Queue
        _queue.value = _queue.value.filter {
            it.uri != uri && it.uri != targetFile.absolutePath && File(it.uri).name != fileName && !it.title.equals(track.title, ignoreCase = true)
        }
        _originalQueue.value = _originalQueue.value.filter {
            it.uri != uri && it.uri != targetFile.absolutePath && File(it.uri).name != fileName && !it.title.equals(track.title, ignoreCase = true)
        }

        // 3. Remove from Recently Played / Playback History
        val filteredHistory = _playbackHistory.value.filter {
            it.uri != uri &&
            it.uri != targetFile.absolutePath &&
            File(it.uri).name != fileName &&
            File(it.uri).name != targetFile.name &&
            !it.title.equals(track.title, ignoreCase = true)
        }
        _playbackHistory.value = filteredHistory
        persistPlaybackHistory(filteredHistory)
        playHistory.removeAll { it.uri == uri || it.uri == targetFile.absolutePath || File(it.uri).name == fileName || it.title.equals(track.title, ignoreCase = true) }

        // 4. Remove Play Count
        playCountPrefs.edit().remove(uri).remove(targetFile.absolutePath).remove(fileName).apply()

        // 5. Remove from Favorites, Playlists & onlineTrackMeta across all representations
        playlistManager.removeTrackEverywhere(uri, track.title, track.artist)
        playlistManager.removeTrackEverywhere(targetFile.absolutePath, track.title, track.artist)

        // 6. Remove from downloaded / downloading URIs
        _downloadedUris.value = _downloadedUris.value - uri - targetFile.absolutePath - fileName - targetFile.name
        _downloadingUris.value = _downloadingUris.value - uri - targetFile.absolutePath

        // 7. Remove from Library & Derived Lists
        val filteredLibrary = _libraryTracks.value.filter {
            it.uri != uri &&
            it.uri != targetFile.absolutePath &&
            File(it.uri).name != fileName &&
            File(it.uri).name != targetFile.name &&
            !it.title.equals(track.title, ignoreCase = true)
        }
        _libraryTracks.value = filteredLibrary
        updateDerivedTrackLists(filteredLibrary)

        // 8. Delete physical files from storage & Android MediaStore on IO dispatcher
        scope.launch(Dispatchers.IO) {
            val filesToDelete = mutableListOf<File>()
            val f1 = File(uri)
            if (f1.exists()) filesToDelete.add(f1)
            val f2 = targetFile
            if (f2.exists() && !filesToDelete.any { it.absolutePath == f2.absolutePath }) filesToDelete.add(f2)

            // Companion cover images
            for (f in filesToDelete.toList()) {
                val companionJpg = File(f.parentFile, "${f.nameWithoutExtension}.jpg")
                if (companionJpg.exists()) filesToDelete.add(companionJpg)
            }

            // Remove any associated stream cache files in app cache directory
            try {
                context.cacheDir.listFiles { _, name ->
                    name.startsWith("veyl_online_stream_")
                }?.forEach { cacheF ->
                    val len1 = if (f1.exists()) f1.length() else 0L
                    val len2 = if (f2.exists()) f2.length() else 0L
                    if (cacheF.absolutePath == uri ||
                        cacheF.absolutePath == currentUri ||
                        (len1 > 0L && Math.abs(cacheF.length() - len1) < 300_000L) ||
                        (len2 > 0L && Math.abs(cacheF.length() - len2) < 300_000L)) {
                        filesToDelete.add(cacheF)
                    }
                }
            } catch (_: Exception) {}

            // Remove any .trashed files matching this track or in the Veyl directory
            try {
                targetFile.parentFile?.listFiles { _, name ->
                    name.contains("trashed", ignoreCase = true)
                }?.forEach { trashedF ->
                    filesToDelete.add(trashedF)
                }
            } catch (_: Exception) {}

            // Remove from Android MediaStore ContentResolver
            try {
                for (f in filesToDelete) {
                    context.contentResolver.delete(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        "${MediaStore.Audio.Media.DATA} = ?",
                        arrayOf(f.absolutePath)
                    )
                }
                context.contentResolver.delete(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    "${MediaStore.Audio.Media.DATA} = ? OR ${MediaStore.Audio.Media.TITLE} = ?",
                    arrayOf(targetFile.absolutePath, track.title)
                )
            } catch (e: Exception) {
                Log.w("AudioEngineController", "MediaStore delete error", e)
            }

            // Delete files from storage
            val deletedPaths = mutableListOf<String>()
            for (f in filesToDelete) {
                try {
                    val ok = f.delete()
                    Log.i("AudioEngineController", "Deleted file: ${f.absolutePath} ($ok)")
                    deletedPaths.add(f.absolutePath)
                } catch (e: Exception) {
                    Log.w("AudioEngineController", "Failed to delete file ${f.absolutePath}", e)
                }
            }

            // Scan deleted paths so Android MediaStore updates
            if (deletedPaths.isNotEmpty()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    deletedPaths.toTypedArray(),
                    null,
                    null
                )
            }

            withContext(Dispatchers.Main) {
                onDeleteComplete?.invoke()
            }
        }
    }

    private fun updateDerivedTrackLists(tracks: List<TrackInfo>) {
        scope.launch(Dispatchers.Default) {
            if (tracks.isEmpty()) {
                _topTracks.value = emptyList()
                _lastAddedTracks.value = emptyList()
                _artistGroups.value = emptyList()
                _albumGroups.value = emptyList()
                return@launch
            }
            _topTracks.value = tracks.sortedByDescending { playCountPrefs.getInt(it.uri, 0) }.take(50)
            _lastAddedTracks.value = tracks.sortedByDescending { File(it.uri).lastModified() }.take(50)

            // Group Artists properly: split featured/collaborating artists so every track by an artist stays grouped together
            val artistMap = linkedMapOf<String, Pair<String, MutableList<TrackInfo>>>()
            val artistSplitter = Regex("\\s*(?:,|;|&|\\bfeat\\.?\\b|\\bft\\.?\\b)\\s*", RegexOption.IGNORE_CASE)
            for (track in tracks) {
                val rawArtist = track.artist?.takeIf(String::isNotBlank) ?: "Unknown Artist"
                val parts = if (rawArtist == "Unknown Artist") {
                    listOf(rawArtist)
                } else {
                    rawArtist.split(artistSplitter).map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(rawArtist) }
                }
                for (name in parts.distinctBy { it.lowercase() }) {
                    val key = name.lowercase()
                    val existing = artistMap[key]
                    if (existing == null) {
                        artistMap[key] = name to mutableListOf(track)
                    } else {
                        existing.second.add(track)
                    }
                }
            }
            _artistGroups.value = artistMap.values
                .map { (displayName, artistTracks) ->
                    val distinctTracks = artistTracks.distinctBy { it.uri }
                    val albumCount = distinctTracks.mapNotNull { it.album?.trim()?.takeIf(String::isNotBlank)?.lowercase() }.distinct().size.coerceAtLeast(1)
                    Triple(displayName, distinctTracks.sortedBy { it.title.trim().lowercase() }, albumCount)
                }
                .sortedBy { it.first.lowercase() }

            // Group Albums strictly by normalized Album Name (not Album+TrackArtist, which splits multi-singer albums!)
            val albumMap = linkedMapOf<String, Pair<String, MutableList<TrackInfo>>>()
            for (track in tracks) {
                val rawAlbum = track.album?.trim()?.takeIf(String::isNotBlank) ?: "Unknown Album"
                val key = rawAlbum.lowercase()
                val existing = albumMap[key]
                if (existing == null) {
                    albumMap[key] = rawAlbum to mutableListOf(track)
                } else {
                    existing.second.add(track)
                }
            }
            _albumGroups.value = albumMap.values
                .map { (displayAlbum, albumTracks) ->
                    val albumArtistName = albumTracks.firstNotNullOfOrNull { it.albumArtist?.trim()?.takeIf(String::isNotBlank) }
                        ?: run {
                            val primaryArtists = albumTracks.mapNotNull { it.artist?.trim()?.takeIf(String::isNotBlank) }.distinctBy { it.lowercase() }
                            when {
                                primaryArtists.isEmpty() -> "Unknown Artist"
                                primaryArtists.size == 1 -> primaryArtists.first()
                                else -> "${primaryArtists.first()} & more"
                            }
                        }
                    Triple(displayAlbum, albumArtistName, albumTracks.sortedWith(compareBy<TrackInfo> { it.discNumber ?: 0u }.thenBy { it.trackNumber ?: 0u }.thenBy { it.title.trim().lowercase() }))
                }
                .sortedBy { it.first.lowercase() }
        }
    }

    private fun handleTrackEnded() {
        when (_repeatMode.value) {
            RepeatMode.ONE -> {
                _status.value?.currentTrack?.let { playTrack(it) }
            }
            RepeatMode.ALL -> skipNext()
            RepeatMode.OFF -> {
                val current = pendingActiveTrack ?: _status.value?.currentTrack
                val currentIndex = _queue.value.indexOfFirst {
                    it.uri == current?.uri || (current != null && it.title.equals(current.title, ignoreCase = true))
                }
                val hasNext = if (_isShuffleEnabled.value) {
                    unplayedShufflePool.isNotEmpty() || (currentIndex in 0 until _queue.value.lastIndex)
                } else {
                    currentIndex in 0 until _queue.value.lastIndex
                }
                if (hasNext) {
                    skipNext()
                } else {
                    val isCloud = _appExperienceMode.value == AppExperienceMode.CLOUD_FIRST ||
                            _isOnlineStreaming.value ||
                            _status.value?.currentTrack?.genre == "Online Stream"
                    if (isCloud && _isAutoplayEnabled.value) {
                        triggerAutoplayNext()
                    } else {
                        stop()
                    }
                }
            }
        }
    }

    private fun triggerAutoplayNext() {
        val current = _status.value?.currentTrack
        val artist = current?.artist ?: "Top 50"
        val title = current?.title ?: ""

        scope.launch(Dispatchers.IO) {
            Log.i("AudioEngineController", "Autoplay: fetching continuous radio for $artist ($title)")
            val newItems = com.audiophile.player.online.CloudRecommendationService.getAutoplayRadioTracks(artist, title)
            if (newItems.isNotEmpty()) {
                val newTracks = newItems.map { item ->
                    onlineSongMetadataCache[item.id] = item
                    onlineSongMetadataCache["online://${item.id}"] = item
                    onlineSongMetadataCache[item.title.lowercase()] = item
                    ArtworkCache.registerOnlineArtwork("online://${item.id}", item.thumbnailUrl)

                    TrackInfo(
                        uri = "online://${item.id}",
                        title = item.title,
                        artist = item.artist,
                        album = item.album,
                        albumArtist = null,
                        genre = "Online Stream",
                        year = null,
                        trackNumber = null,
                        discNumber = null,
                        durationSeconds = item.durationSeconds,
                        sampleRate = 44100u,
                        bitDepth = 16u,
                        bitrate = 320000u,
                        channels = 2u,
                        formatName = "AAC 320k",
                        hasArtwork = true
                    )
                }
                withContext(Dispatchers.Main) {
                    _queue.value = _queue.value + newTracks
                    _originalQueue.value = _originalQueue.value + newTracks
                    if (_isShuffleEnabled.value) {
                        unplayedShufflePool.addAll(newTracks.shuffled())
                    }
                    skipNext()
                }
            } else {
                withContext(Dispatchers.Main) { stop() }
            }
        }
    }

    fun toggleTheme() {
        val newDark = !_isDarkMode.value
        setDarkMode(newDark)
    }

    fun setDarkMode(isDark: Boolean) {
        _isDarkMode.value = isDark
        prefs.edit().putBoolean("is_dark_mode", isDark).apply()
        _currentVeylColorScheme.value = loadInitialColorScheme()
    }

    fun resetSettingsToDefault() {
        setDarkMode(true)
        applyThemePreset("monochrome_carbon")
        setBufferFrameSize(128)
        setDsdMode(DsdModeEnum.DO_P)
        setBitPerfectEnabled(true)
        setGaplessEnabled(true)
        setCrossfadeSeconds(0f)
        setCrossfadeCurve(CrossfadeCurve.EQUAL_POWER)
        setReplayGainMode(uniffi.audiophile_core.ReplayGainModeEnum.TRACK)
        setAutoEqPreamp(true)
        setAutoFetchLyrics(true)
        setPlayDuringCalls(false)
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
                val currentItem = orig.firstOrNull { it.uri == currentTrack?.uri || (currentTrack != null && it.title.equals(currentTrack.title, ignoreCase = true)) }
                orig.removeAll { it.uri == currentTrack?.uri || (currentTrack != null && it.title.equals(currentTrack.title, ignoreCase = true)) }
                
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

        val current = pendingActiveTrack ?: _status.value?.currentTrack
        val currentIndex = q.indexOfFirst { it.uri == current?.uri || (current != null && it.title.equals(current.title, ignoreCase = true)) }

        val nextTrack = if (_isShuffleEnabled.value) {
            if (unplayedShufflePool.isNotEmpty()) {
                val next = unplayedShufflePool.removeAt(0)
                if (current != null) playHistory.add(current)
                next
            } else if (currentIndex != -1 && currentIndex < q.size - 1) {
                val next = q[currentIndex + 1]
                if (current != null) playHistory.add(current)
                next
            } else {
                val isCloud = _appExperienceMode.value == AppExperienceMode.CLOUD_FIRST ||
                        _isOnlineStreaming.value ||
                        _status.value?.currentTrack?.genre == "Online Stream"
                if (isCloud && _isAutoplayEnabled.value) {
                    triggerAutoplayNext()
                    return
                }
                q.firstOrNull()
            }
        } else {
            if (currentIndex != -1 && currentIndex >= q.size - 1) {
                val isCloud = _appExperienceMode.value == AppExperienceMode.CLOUD_FIRST ||
                        _isOnlineStreaming.value ||
                        _status.value?.currentTrack?.genre == "Online Stream"
                if (isCloud && _isAutoplayEnabled.value) {
                    triggerAutoplayNext()
                    return
                }
            }
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

        val current = pendingActiveTrack ?: _status.value?.currentTrack
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

    fun isTrackFavorite(track: TrackInfo?, favs: Set<String> = favoriteUris.value): Boolean {
        if (track == null) return false
        if (favs.contains(track.uri)) return true

        // Fast-path for local files: no need to invoke disk-based online stream path resolution
        if (track.uri.startsWith("/") || track.uri.startsWith("content://") || track.uri.startsWith("file://")) {
            return false
        }

        val targetPath = onlineStreamManager.getTargetStorageFileForTrack(track).absolutePath
        if (favs.contains(targetPath)) return true

        val meta = playlistManager.getTrackMeta(track.uri) ?: playlistManager.getTrackMeta(targetPath)
        if (meta != null) {
            if (favs.contains(meta.uri)) return true
            val metaTarget = onlineStreamManager.getTargetStorageFileForTrack(meta).absolutePath
            if (favs.contains(metaTarget)) return true
        }

        return false
    }

    fun toggleFavorite(uri: String, trackInfo: TrackInfo? = null) {
        val resolved = trackInfo ?: resolveTrackInfo(uri) ?: _status.value?.currentTrack?.takeIf { it.uri == uri }
        val targetPath = resolved?.let { onlineStreamManager.getTargetStorageFileForTrack(it).absolutePath }

        val isCurrentlyFav = isTrackFavorite(resolved) || playlistManager.isFavorite(uri) ||
                (targetPath != null && playlistManager.isFavorite(targetPath))

        Log.d("AudioEngineController", "toggleFavorite: uri=$uri, targetPath=$targetPath, isCurrentlyFav=$isCurrentlyFav")

        if (isCurrentlyFav) {
            // Remove from favorites: remove all associated URIs
            val toRemove = mutableSetOf(uri)
            if (targetPath != null) toRemove.add(targetPath)
            if (resolved != null) toRemove.add(resolved.uri)
            val meta = resolved?.let { playlistManager.getTrackMeta(it.uri) }
            if (meta != null) {
                toRemove.add(meta.uri)
                toRemove.add(onlineStreamManager.getTargetStorageFileForTrack(meta).absolutePath)
            }
            playlistManager.removeFavorites(toRemove)
        } else {
            // Add to favorites: immediately add all associated URIs so UI turns red instantly
            val toAdd = mutableSetOf(uri)
            if (targetPath != null) toAdd.add(targetPath)
            if (resolved != null) toAdd.add(resolved.uri)
            playlistManager.addFavorites(toAdd)

            if (resolved != null) {
                val isOnline = resolved.uri.contains("veyl_online_stream_") || resolved.genre == "Online Stream"
                if (isOnline) {
                    playlistManager.registerOnlineTrack(resolved, _currentOnlineSong.value)
                }
            }
        }
    }

    fun addTrackToPlaylist(playlistId: String, uri: String, trackInfo: TrackInfo? = null, artworkUrl: String? = null) {
        playlistManager.addTrackToPlaylist(playlistId, uri)
        val resolved = trackInfo ?: resolveTrackInfo(uri) ?: _status.value?.currentTrack?.takeIf { it.uri == uri }
        val effectiveArt = artworkUrl ?: ArtworkCache.getOnlineArtwork(uri) ?: resolved?.let { ArtworkCache.getOnlineArtwork(it.title.lowercase()) }
        if (!effectiveArt.isNullOrBlank()) {
            ArtworkCache.registerOnlineArtwork(uri, effectiveArt)
            resolved?.let { ArtworkCache.registerOnlineArtwork(it.uri, effectiveArt) }
        }
        if (resolved != null) {
            val isOnline = resolved.uri.contains("veyl_online_stream_") || resolved.genre == "Online Stream" || resolved.uri.startsWith("online://")
            if (isOnline) {
                playlistManager.registerOnlineTrack(resolved, _currentOnlineSong.value?.takeIf { it.id in uri || it.title.equals(resolved.title, ignoreCase = true) }, effectiveArt)
            }
        }
    }

    fun createPlaylist(name: String): UserPlaylist = playlistManager.createPlaylist(name)

    fun renamePlaylist(playlistId: String, newName: String) = playlistManager.renamePlaylist(playlistId, newName)

    fun deletePlaylist(playlistId: String) = playlistManager.deletePlaylist(playlistId)

    fun removeTrackFromPlaylist(playlistId: String, uri: String) = playlistManager.removeTrackFromPlaylist(playlistId, uri)

    fun exportPlaylist(name: String, tracks: List<TrackInfo>, outputStream: java.io.OutputStream): Result<Int> =
        playlistManager.exportPlaylist(name, tracks, outputStream)

    fun importPlaylist(
        inputStream: java.io.InputStream,
        defaultName: String = "Imported Playlist",
        libraryTracks: List<TrackInfo> = tracks.value
    ): Result<UserPlaylist> =
        playlistManager.importPlaylist(inputStream, defaultName, libraryTracks)

    data class SpotifyImportResult(
        val playlist: UserPlaylist,
        val totalTracks: Int,
        val localMatches: Int,
        val onlineStreams: Int
    )

    private fun normalizeTitle(raw: String): String {
        return raw.lowercase()
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace('\u201C', '"').replace('\u201D', '"')
            .replace('\u2013', '-').replace('\u2014', '-')
            .replace(Regex("""^\d{1,3}[\s.\-_]+"""), "") // strip "01. ", "01 - "
            .replace(Regex("""\s*[\(\[](?:feat\.?|ft\.?|with)\s+[^)\]]+[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+(?:feat\.?|ft\.?|with)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[\(\[](?:remastered|remaster|radio edit|original mix|acoustic|live|bonus track|deluxe|version|edit|mono|stereo|anniversary)[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+(?:remastered|remaster|radio edit|original mix|acoustic|live|bonus track|deluxe|anniversary).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*[\(\[]from\s+[^)\]]+[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+from\s+.*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\.(mp3|flac|m4a|wav|aac|ogg|opus|dsf|dff)$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizeArtist(raw: String): String {
        return raw.lowercase()
            .replace('\u2018', '\'').replace('\u2019', '\'')
            .replace(Regex("""\s*(?:feat\.?|ft\.?|with|&|,|/|x)\s*""", RegexOption.IGNORE_CASE), " ")
            .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    suspend fun importSpotifyPlaylist(url: String): Result<SpotifyImportResult> = withContext(Dispatchers.IO) {
        runCatching {
            val spotifyData = com.audiophile.player.online.SpotifyPlaylistImporter.fetchPlaylist(url).getOrThrow()
            val allLibraryTracks = tracks.value.ifEmpty { _libraryTracks.value }
            val importedUris = mutableListOf<String>()
            var localMatches = 0
            var onlineStreams = 0

            val playlistCover = spotifyData.coverUrl ?: ""
            val newlyCreatedTracks = mutableListOf<Pair<TrackInfo, OnlineSongItem>>()

            for (spTrack in spotifyData.tracks) {
                val spNormTitle = normalizeTitle(spTrack.title)
                val spNormArtist = normalizeArtist(spTrack.artist)
                val spArtistWords = spNormArtist.split(" ").filter { it.length > 2 }

                val matched = allLibraryTracks.find { lib ->
                    // Tier 1: Exact title & artist
                    lib.title.equals(spTrack.title, ignoreCase = true) &&
                    (lib.artist.contains(spTrack.artist, ignoreCase = true) || spTrack.artist.contains(lib.artist, ignoreCase = true))
                } ?: allLibraryTracks.find { lib ->
                    // Tier 2: Normalized title match + artist overlap
                    val libNormTitle = normalizeTitle(lib.title)
                    if (libNormTitle == spNormTitle && spNormTitle.isNotBlank()) {
                        val libNormArtist = normalizeArtist(lib.artist ?: "")
                        spArtistWords.isEmpty() || spArtistWords.any { libNormArtist.contains(it) } || libNormArtist.isBlank() || libNormArtist == "unknown artist"
                    } else {
                        false
                    }
                } ?: allLibraryTracks.find { lib ->
                    // Tier 3: Match against local filename (e.g. "Song Name.flac" or "Artist - Song Name.mp3")
                    val fName = File(lib.uri).nameWithoutExtension
                    val normFileName = normalizeTitle(fName)
                    normFileName == spNormTitle || (normFileName.contains(spNormTitle) && spArtistWords.any { normFileName.contains(it) })
                } ?: allLibraryTracks.find { lib ->
                    // Tier 4: Exact normalized title match
                    val libNormTitle = normalizeTitle(lib.title)
                    libNormTitle.isNotBlank() && libNormTitle == spNormTitle
                }

                if (matched != null) {
                    importedUris.add(matched.uri)
                    localMatches++
                } else {
                    // Create streaming-ready track
                    val hash = "${spTrack.title}_${spTrack.artist}".hashCode().toUInt().toString(16)
                    val onlineUri = "online://spotify_$hash"
                    val trackCover = spTrack.coverUrl?.takeIf { it.isNotBlank() } ?: ""
                    val hasArt = trackCover.isNotBlank()

                    val onlineTrack = TrackInfo(
                        uri = onlineUri,
                        title = spTrack.title,
                        artist = spTrack.artist,
                        album = spotifyData.title,
                        albumArtist = null,
                        genre = "Online Stream",
                        year = null,
                        trackNumber = null,
                        discNumber = null,
                        durationSeconds = spTrack.durationSeconds,
                        sampleRate = 44100u,
                        bitDepth = 16u,
                        bitrate = 320000u,
                        channels = 2u,
                        formatName = "AAC 320k",
                        hasArtwork = hasArt
                    )

                    val onlineSong = OnlineSongItem(
                        id = onlineUri.removePrefix("online://"),
                        title = spTrack.title,
                        artist = spTrack.artist,
                        album = spotifyData.title,
                        durationSeconds = spTrack.durationSeconds,
                        thumbnailUrl = trackCover,
                        streamUrl = ""
                    )

                    if (hasArt) {
                        ArtworkCache.registerOnlineArtwork(onlineTrack.uri, trackCover)
                        ArtworkCache.registerOnlineArtwork(onlineSong.id, trackCover)
                        ArtworkCache.registerOnlineArtwork("online://${onlineSong.id}", trackCover)
                        ArtworkCache.registerOnlineArtwork(onlineTrack.title.lowercase(), trackCover)
                    }

                    playlistManager.registerOnlineTrack(onlineTrack, onlineSong)
                    onlineSongMetadataCache[onlineTrack.uri] = onlineSong
                    onlineSongMetadataCache[onlineTrack.title.lowercase()] = onlineSong

                    importedUris.add(onlineUri)
                    newlyCreatedTracks.add(onlineTrack to onlineSong)
                    onlineStreams++
                }
            }

            val playlist = playlistManager.createPlaylistWithTracks(
                name = spotifyData.title,
                trackUris = importedUris,
                coverUrl = spotifyData.coverUrl
            )

            if (playlistCover.isNotBlank()) {
                ArtworkCache.registerOnlineArtwork(playlist.name, playlistCover)
                ArtworkCache.registerOnlineArtwork(playlist.id, playlistCover)
            }

            // Asynchronously resolve individual track covers in background
            scope.launch(Dispatchers.IO) {
                for ((track, song) in newlyCreatedTracks) {
                    try {
                        val query = "${track.title} ${track.artist}".trim()
                        val results = JioSaavnService.searchSongs(query)
                        if (results.isNotEmpty()) {
                            val match = results.first()
                            if (match.thumbnailUrl.isNotBlank()) {
                                ArtworkCache.registerOnlineArtwork(track.uri, match.thumbnailUrl)
                                ArtworkCache.registerOnlineArtwork(song.id, match.thumbnailUrl)
                                ArtworkCache.registerOnlineArtwork("online://${song.id}", match.thumbnailUrl)
                                ArtworkCache.registerOnlineArtwork(track.title.lowercase(), match.thumbnailUrl)
                                val updatedTrack = track.copy(hasArtwork = true)
                                playlistManager.registerOnlineTrack(updatedTrack, match)

                                val enrichedSong = song.copy(
                                    thumbnailUrl = match.thumbnailUrl,
                                    album = if (match.album.isNotBlank()) match.album else song.album,
                                    streamUrl = if (match.streamUrl.isNotBlank()) match.streamUrl else song.streamUrl
                                )
                                onlineSongMetadataCache[track.uri] = enrichedSong
                                onlineSongMetadataCache[song.id] = enrichedSong
                                onlineSongMetadataCache["online://${song.id}"] = enrichedSong
                                onlineSongMetadataCache[track.title.lowercase()] = enrichedSong
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            SpotifyImportResult(
                playlist = playlist,
                totalTracks = spotifyData.tracks.size,
                localMatches = localMatches,
                onlineStreams = onlineStreams
            )
        }
    }

    fun removeFromQueue(index: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (index in currentQueue.indices) {
            val removed = currentQueue.removeAt(index)
            _queue.value = currentQueue
            _originalQueue.value = currentQueue
            unplayedShufflePool.removeAll { it.uri == removed.uri || it.title.equals(removed.title, ignoreCase = true) }
            scheduleNextTrackPrefetch()
        }
    }

    fun moveInQueue(fromIndex: Int, toIndex: Int) {
        val currentQueue = _queue.value.toMutableList()
        if (fromIndex in currentQueue.indices && toIndex in currentQueue.indices && fromIndex != toIndex) {
            val item = currentQueue.removeAt(fromIndex)
            currentQueue.add(toIndex, item)
            _queue.value = currentQueue
            _originalQueue.value = currentQueue

            // Re-sync unplayedShufflePool so that playback strictly follows the user's reordered queue sequence!
            val current = pendingActiveTrack ?: _status.value?.currentTrack
            val currentIdx = currentQueue.indexOfFirst {
                it.uri == current?.uri || (current != null && it.title.equals(current.title, ignoreCase = true))
            }
            unplayedShufflePool.clear()
            if (currentIdx != -1 && currentIdx < currentQueue.size - 1) {
                unplayedShufflePool.addAll(currentQueue.subList(currentIdx + 1, currentQueue.size))
            } else {
                unplayedShufflePool.addAll(currentQueue.filter { it.uri != current?.uri && !it.title.equals(current?.title, ignoreCase = true) })
            }

            // Immediately reschedule next track prefetch so it matches newly positioned next track
            scheduleNextTrackPrefetch()
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
        _originalQueue.value = currentQueue
        if (_isShuffleEnabled.value) {
            unplayedShufflePool.add(0, track)
        }
        scheduleNextTrackPrefetch()
    }

    fun addToQueue(track: TrackInfo) {
        val currentQueue = _queue.value.toMutableList()
        currentQueue.add(track)
        _queue.value = currentQueue
        _originalQueue.value = currentQueue
        if (_isShuffleEnabled.value) {
            unplayedShufflePool.add(track)
        }
    }

    fun clearQueue() {
        val current = _status.value?.currentTrack
        if (current != null) {
            val currentInfo = _queue.value.firstOrNull { it.uri == current.uri }
            val single = if (currentInfo != null) listOf(currentInfo) else emptyList()
            _queue.value = single
            _originalQueue.value = single
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

    fun autoDetectRootDirectory(): String {
        val saved = prefs.getString("root_audio_dir", null)
        if (saved != null && File(saved).exists()) {
            _selectedRootPath.value = saved
            return saved
        }

        // 1. Try to detect where music actually lives from MediaStore
        try {
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null
            )?.use { cursor ->
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dirCounts = mutableMapOf<String, Int>()
                var inspected = 0
                while (cursor.moveToNext() && inspected < 150) {
                    val filePath = cursor.getString(dataCol) ?: continue
                    val file = File(filePath)
                    val parent = file.parentFile ?: continue
                    var dir = parent
                    while (dir.parentFile != null &&
                        dir.parentFile?.absolutePath != "/storage/emulated/0" &&
                        dir.parentFile?.absolutePath != "/storage" &&
                        !dir.parentFile?.absolutePath.orEmpty().matches(Regex("^/storage/[^/]+$"))
                    ) {
                        dir = dir.parentFile ?: break
                    }
                    val pathKey = dir.absolutePath
                    dirCounts[pathKey] = (dirCounts[pathKey] ?: 0) + 1
                    inspected++
                }
                val topDir = dirCounts.maxByOrNull { it.value }?.key
                if (topDir != null && File(topDir).exists()) {
                    _selectedRootPath.value = topDir
                    prefs.edit().putString("root_audio_dir", topDir).apply()
                    return topDir
                }
            }
        } catch (e: Exception) {
            Log.w("AudioEngineController", "Error auto-detecting folder from MediaStore", e)
        }

        // 2. Standard Android system directories
        val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
        val sdcardMusic = File("/storage/emulated/0/Music")
        val downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)

        val detected = when {
            musicDir != null && musicDir.exists() -> musicDir.absolutePath
            sdcardMusic.exists() -> sdcardMusic.absolutePath
            downloadDir != null && downloadDir.exists() -> downloadDir.absolutePath
            else -> "/storage/emulated/0/Music"
        }

        _selectedRootPath.value = detected
        prefs.edit().putString("root_audio_dir", detected).apply()
        return detected
    }

    private fun loadSavedRootDirectory() {
        val hasCached = _libraryTracks.value.isNotEmpty()
        val detected = autoDetectRootDirectory()
        if (!hasCached) {
            val file = File(detected)
            if (file.exists() && file.canRead()) {
                scanDirectory(detected)
            } else {
                scanMediaStore()
            }
        }
    }

    fun setRootDirectory(path: String) {
        prefs.edit().putString("root_audio_dir", path).apply()
        _selectedRootPath.value = path
        scanDirectory(path)
    }

    private fun enrichTracksWithKnownMetadata(tracks: List<TrackInfo>): List<TrackInfo> {
        val knownMap = playlistManager.onlineTrackMeta.value
        return tracks
            .filter { track ->
                val fName = File(track.uri).name
                !fName.startsWith(".") &&
                !fName.contains("trashed", ignoreCase = true) &&
                !track.title.contains("trashed", ignoreCase = true) &&
                !fName.startsWith("veyl_online_stream_") &&
                !track.uri.contains("/cache/")
            }
            .map { track ->
            val file = File(track.uri)
            val fName = file.name
            val match = knownMap[track.uri] ?: knownMap[fName] ?: knownMap.values.find { File(it.uri).name == fName }

            val companionJpg = File(file.parentFile ?: File(""), "${file.nameWithoutExtension}.jpg")
            val hasArt = track.hasArtwork || companionJpg.exists() || ArtworkCache.getOnlineArtwork(track.uri) != null

            if (match != null) {
                val needArtist = track.artist.isNullOrBlank() || track.artist == "Unknown Artist" || track.artist == "<unknown>"
                val needAlbum = track.album.isNullOrBlank() || track.album == "Unknown Album" || track.album == "<unknown>"
                val needTitle = track.title.isBlank() || track.title == file.nameWithoutExtension

                track.copy(
                    title = if (needTitle && match.title.isNotBlank()) match.title else track.title,
                    artist = if (needArtist && !match.artist.isNullOrBlank()) match.artist else track.artist,
                    album = if (needAlbum && !match.album.isNullOrBlank()) match.album else track.album,
                    albumArtist = if (track.albumArtist.isNullOrBlank() && !match.albumArtist.isNullOrBlank()) match.albumArtist else track.albumArtist,
                    hasArtwork = hasArt || match.hasArtwork
                )
            } else if (hasArt && !track.hasArtwork) {
                track.copy(hasArtwork = true)
            } else {
                track
            }
        }
    }

    private fun addTrackToLibrary(track: TrackInfo) {
        val current = _libraryTracks.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.uri == track.uri || File(it.uri).name == File(track.uri).name }
        if (existingIndex != -1) {
            current[existingIndex] = track
        } else {
            current.add(0, track)
        }
        _libraryTracks.value = current
        updateDerivedTrackLists(current)
    }

    fun rescanLibrary() {
        val root = _selectedRootPath.value ?: autoDetectRootDirectory()
        val file = File(root)
        if (file.exists() && file.canRead()) {
            scanDirectory(root)
        } else {
            scanMediaStore()
        }
    }

    private var activeScanJob: Job? = null

    fun scanDirectory(path: String) {
        if (activeScanJob?.isActive == true) return
        activeScanJob = scope.launch(Dispatchers.IO) {
            _isScanning.value = true
            var delegatedToMediaStore = false
            try {
                val dir = File(path)
                if (dir.exists()) {
                    val rawTracks = libraryEngine.scanDirectory(dir.absolutePath).toMutableList()
                    val musicDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                    val veylDir = if (musicDir != null) File(musicDir, "Veyl") else null
                    if (veylDir != null && veylDir.exists() && !veylDir.canonicalPath.startsWith(dir.canonicalPath)) {
                        try {
                            val veylTracks = libraryEngine.scanDirectory(veylDir.absolutePath)
                            rawTracks.addAll(veylTracks)
                        } catch (e: Exception) {
                            Log.w("AudioEngineController", "Error scanning Veyl downloads dir", e)
                        }
                    }
                    val distinctRawTracks = rawTracks.distinctBy { it.uri }
                    val tracks = enrichTracksWithKnownMetadata(distinctRawTracks)
                    Log.i("AudioEngineController", "Scanned ${tracks.size} tracks from $path")
                    withContext(Dispatchers.Main) {
                        _libraryTracks.value = tracks
                        persistLibraryTracksCache(tracks)
                        updateDerivedTrackLists(tracks)
                        if (_originalQueue.value.isEmpty() && tracks.isNotEmpty()) {
                            _originalQueue.value = tracks
                            _queue.value = tracks
                        }
                    }
                    if (tracks.isEmpty()) {
                        delegatedToMediaStore = true
                        scanMediaStore()
                    }
                } else {
                    delegatedToMediaStore = true
                    scanMediaStore()
                }
            } catch (e: Exception) {
                Log.e("AudioEngineController", "Error scanning directory $path", e)
                delegatedToMediaStore = true
                scanMediaStore()
            } finally {
                if (!delegatedToMediaStore) _isScanning.value = false
            }
        }
    }

    fun scanMediaStore() {
        if (activeScanJob?.isActive == true) return
        activeScanJob = scope.launch(Dispatchers.IO) {
            _isScanning.value = true
            try {
                val tracks = mutableListOf<TrackInfo>()
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DATA,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM,
                    MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.YEAR,
                    MediaStore.Audio.Media.TRACK,
                    MediaStore.Audio.Media.SIZE
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

                context.contentResolver.query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    null,
                    sortOrder
                )?.use { cursor ->
                    val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                    val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                    val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                    val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                    val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                    val yearCol = cursor.getColumnIndex(MediaStore.Audio.Media.YEAR)
                    val trackCol = cursor.getColumnIndex(MediaStore.Audio.Media.TRACK)

                    while (cursor.moveToNext()) {
                        val path = cursor.getString(dataCol) ?: continue
                        val file = File(path)
                        if (!file.exists() || file.length() == 0L) continue
                        val fName = file.name
                        if (fName.startsWith(".") || fName.contains("trashed", ignoreCase = true) || fName.startsWith("veyl_online_stream_") || path.contains("/cache/")) continue

                        val title = cursor.getString(titleCol) ?: file.nameWithoutExtension
                        if (title.contains("trashed", ignoreCase = true)) continue
                        val artist = cursor.getString(artistCol) ?: "Unknown Artist"
                        val album = cursor.getString(albumCol) ?: "Unknown Album"
                        val durationMs = cursor.getLong(durationCol)
                        val year = if (yearCol != -1) cursor.getInt(yearCol).takeIf { it > 0 }?.toUInt() else null
                        val trackNum = if (trackCol != -1) cursor.getInt(trackCol).takeIf { it > 0 }?.toUInt() else null

                        val ext = file.extension.lowercase()
                        val formatName = when (ext) {
                            "flac" -> "FLAC"
                            "wav" -> "WAV"
                            "mp3" -> "MP3"
                            "m4a", "aac" -> "AAC"
                            "ogg", "opus" -> "OGG/Opus"
                            "dsf", "dff" -> "DSD"
                            else -> ext.uppercase()
                        }

                        val scannedTrack = TrackInfo(
                            uri = path,
                            title = title,
                            artist = if (artist == "<unknown>") "Unknown Artist" else artist,
                            album = if (album == "<unknown>") "Unknown Album" else album,
                            albumArtist = null,
                            genre = null,
                            year = year,
                            trackNumber = trackNum,
                            discNumber = null,
                            durationSeconds = (durationMs / 1000.0).coerceAtLeast(1.0),
                            sampleRate = 44100u,
                            bitDepth = 16u,
                            bitrate = null,
                            channels = 2u,
                            formatName = formatName,
                            hasArtwork = true
                        )
                        tracks.addAll(CueSheetParser.splitIfCuePresent(scannedTrack))
                    }
                }

                if (tracks.isNotEmpty()) {
                    val enrichedTracks = enrichTracksWithKnownMetadata(tracks)
                    Log.i("AudioEngineController", "Discovered ${enrichedTracks.size} tracks from MediaStore")
                    withContext(Dispatchers.Main) {
                        _libraryTracks.value = enrichedTracks
                        persistLibraryTracksCache(enrichedTracks)
                        updateDerivedTrackLists(enrichedTracks)
                        if (_selectedRootPath.value == null) {
                            _selectedRootPath.value = autoDetectRootDirectory()
                        }
                        if (_originalQueue.value.isEmpty()) {
                            _originalQueue.value = enrichedTracks
                            _queue.value = enrichedTracks
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AudioEngineController", "Error querying MediaStore", e)
            } finally {
                _isScanning.value = false
            }
        }
    }

    fun scanFromTreeUri(treeUri: Uri) {
        if (activeScanJob?.isActive == true) return
        activeScanJob = scope.launch(Dispatchers.IO) {
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

                val rawTracks = libraryEngine.scanDirectory(targetPath)
                val tracks = enrichTracksWithKnownMetadata(rawTracks)
                Log.i("AudioEngineController", "Scanned ${tracks.size} tracks from resolved path: $targetPath")
                withContext(Dispatchers.Main) {
                    _libraryTracks.value = tracks
                    persistLibraryTracksCache(tracks)
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
        unplayedShufflePool.clear()
        if (_isShuffleEnabled.value) {
            val remaining = tracks.filter { it.uri != startTrack.uri }
            unplayedShufflePool.addAll(remaining)
        }
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

    fun ensurePlaybackServiceStarted() {
        try {
            val serviceIntent = Intent(context, com.audiophile.player.service.PlaybackService::class.java).apply {
                action = com.audiophile.player.service.PlaybackService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } catch (e: Exception) {
            Log.w("AudioEngineController", "Failed to start PlaybackService", e)
        }
    }

    fun playOnlineSong(
        song: OnlineSongItem,
        contextQueue: List<OnlineSongItem> = emptyList(),
        startShuffled: Boolean = false,
        preserveQueue: Boolean = false
    ) {
        // Immediately pause existing audio so previous track does not conflict or auto-advance
        nativeScope.launch {
            withNativeEngine { it.pause() }
        }
        isTransitioning = false
        lastTransitionTimestamp = System.currentTimeMillis()

        // Resolve specific track artwork from ArtworkCache before falling back to song.thumbnailUrl
        val sId = song.id.removePrefix("online://")
        val specificTrackArt = ArtworkCache.getOnlineArtwork("online://$sId")
            ?: ArtworkCache.getOnlineArtwork(sId)
            ?: ArtworkCache.getOnlineArtwork(song.title.lowercase())

        val resolvedThumb = specificTrackArt?.takeIf { it.isNotBlank() }
            ?: song.thumbnailUrl.takeIf { it.isNotBlank() }
            ?: ""

        val effectiveSong = if (resolvedThumb.isNotBlank()) song.copy(thumbnailUrl = resolvedThumb) else song

        ensurePlaybackServiceStarted()
        _isOnlineStreaming.value = true
        _isOnlineBuffering.value = true
        _currentOnlineSong.value = effectiveSong

        // Cache metadata for this song
        onlineSongMetadataCache[effectiveSong.id] = effectiveSong
        onlineSongMetadataCache["online://${effectiveSong.id}"] = effectiveSong
        onlineSongMetadataCache[effectiveSong.title.lowercase()] = effectiveSong
        if (resolvedThumb.isNotBlank()) {
            ArtworkCache.registerOnlineArtwork("online://${effectiveSong.id}", resolvedThumb)
            ArtworkCache.registerOnlineArtwork(effectiveSong.id, resolvedThumb)
            ArtworkCache.registerOnlineArtwork(effectiveSong.title.lowercase(), resolvedThumb)
        }

        // Setup Queue if contextQueue is provided
        if (contextQueue.isNotEmpty()) {
            val queueTracks = contextQueue.map { item ->
                onlineSongMetadataCache[item.id] = item
                onlineSongMetadataCache["online://${item.id}"] = item
                onlineSongMetadataCache[item.title.lowercase()] = item
                val itemArt = ArtworkCache.getOnlineArtwork("online://${item.id}")
                    ?: ArtworkCache.getOnlineArtwork(item.id)
                    ?: ArtworkCache.getOnlineArtwork(item.title.lowercase())
                    ?: item.thumbnailUrl
                if (itemArt.isNotBlank()) {
                    ArtworkCache.registerOnlineArtwork("online://${item.id}", itemArt)
                    ArtworkCache.registerOnlineArtwork(item.id, itemArt)
                    ArtworkCache.registerOnlineArtwork(item.title.lowercase(), itemArt)
                }

                TrackInfo(
                    uri = if (item.id.startsWith("online://")) item.id else "online://${item.id}",
                    title = item.title,
                    artist = item.artist,
                    album = item.album,
                    albumArtist = null,
                    genre = "Online Stream",
                    year = null,
                    trackNumber = null,
                    discNumber = null,
                    durationSeconds = item.durationSeconds,
                    sampleRate = 44100u,
                    bitDepth = 16u,
                    bitrate = 320000u,
                    channels = 2u,
                    formatName = "AAC 320k",
                    hasArtwork = true
                )
            }

            _originalQueue.value = queueTracks

            if (startShuffled || _isShuffleEnabled.value) {
                _isShuffleEnabled.value = true
                prefs.edit().putBoolean("shuffle_enabled", true).apply()
                val currentPlaceholder = queueTracks.firstOrNull { it.title.equals(effectiveSong.title, ignoreCase = true) }
                    ?: queueTracks.first()
                val remaining = queueTracks.filter { !it.title.equals(effectiveSong.title, ignoreCase = true) }.shuffled()
                val shuffledQueue = listOf(currentPlaceholder) + remaining
                _queue.value = shuffledQueue
                unplayedShufflePool.clear()
                unplayedShufflePool.addAll(remaining)
            } else {
                _queue.value = queueTracks
            }
        } else if (!preserveQueue && _queue.value.none { it.title.equals(effectiveSong.title, ignoreCase = true) }) {
            val placeholder = TrackInfo(
                uri = "online://${effectiveSong.id}",
                title = effectiveSong.title,
                artist = effectiveSong.artist,
                album = effectiveSong.album,
                albumArtist = null,
                genre = "Online Stream",
                year = null,
                trackNumber = null,
                discNumber = null,
                durationSeconds = effectiveSong.durationSeconds,
                sampleRate = 44100u,
                bitDepth = 16u,
                bitrate = 320000u,
                channels = 2u,
                formatName = "AAC 320k",
                hasArtwork = true
            )
            _queue.value = listOf(placeholder)
            _originalQueue.value = listOf(placeholder)
        }

        // 1. Instant zero-lag playback if the song was already prefetched into the single rolling disk buffer!
        val readyPrefetched = onlineStreamManager.getPrefetchedStreamFile(effectiveSong)
        if (readyPrefetched != null && readyPrefetched.exists() && readyPrefetched.length() > 50_000L) {
            Log.i("AudioEngineController", "Zero-lag playback from prefetched buffer for ${effectiveSong.title}")
            _isOnlineBuffering.value = false
            val currentSpecificArt = ArtworkCache.getOnlineArtwork("online://${effectiveSong.id}")
                ?: ArtworkCache.getOnlineArtwork(effectiveSong.id)
                ?: ArtworkCache.getOnlineArtwork(effectiveSong.title.lowercase())
                ?: effectiveSong.thumbnailUrl
            val bestThumb = currentSpecificArt.takeIf { it.isNotBlank() } ?: effectiveSong.thumbnailUrl
            val trackInfo = onlineStreamManager.createTrackInfo(effectiveSong.copy(thumbnailUrl = bestThumb), readyPrefetched)
            if (bestThumb.isNotBlank()) {
                ArtworkCache.registerOnlineArtwork(readyPrefetched.absolutePath, bestThumb)
                ArtworkCache.registerOnlineArtwork("online://${effectiveSong.id}", bestThumb)
                ArtworkCache.registerOnlineArtwork(effectiveSong.id, bestThumb)
                ArtworkCache.registerOnlineArtwork(effectiveSong.title.lowercase(), bestThumb)
            }

            updateQueueWithStreamTrack(effectiveSong, trackInfo)
            playTrack(trackInfo)
            scheduleNextTrackPrefetch()
            return
        }

        scope.launch(Dispatchers.IO) {
            val streamUrl = if (effectiveSong.streamUrl.isNotBlank()) {
                effectiveSong.streamUrl
            } else {
                onlineStreamManager.resolveAudioStreamUrl(effectiveSong)
            }

            // In case resolveAudioStreamUrl newly resolved track cover from JioSaavn:
            val newlyResolvedArt = ArtworkCache.getOnlineArtwork("online://${effectiveSong.id}")
                ?: ArtworkCache.getOnlineArtwork(effectiveSong.id)
                ?: ArtworkCache.getOnlineArtwork(effectiveSong.title.lowercase())
                ?: effectiveSong.thumbnailUrl

            val playingSong = if (newlyResolvedArt.isNotBlank()) effectiveSong.copy(thumbnailUrl = newlyResolvedArt) else effectiveSong

            if (streamUrl.isNullOrBlank()) {
                Log.e("AudioEngineController", "Failed to resolve stream URL for ${playingSong.title}")
                withContext(Dispatchers.Main) {
                    _isOnlineBuffering.value = false
                    _isOnlineStreaming.value = false
                    _currentOnlineSong.value = null
                }
                return@launch
            }

            onlineStreamManager.startStream(
                streamUrl = streamUrl,
                onStreamReady = { cacheFile ->
                    _isOnlineBuffering.value = false
                    val trackSpecificArt = ArtworkCache.getOnlineArtwork("online://${playingSong.id}")
                        ?: ArtworkCache.getOnlineArtwork(playingSong.id)
                        ?: ArtworkCache.getOnlineArtwork(playingSong.title.lowercase())
                        ?: playingSong.thumbnailUrl
                    val bestThumb = trackSpecificArt.takeIf { it.isNotBlank() } ?: playingSong.thumbnailUrl
                    val trackInfo = onlineStreamManager.createTrackInfo(playingSong.copy(thumbnailUrl = bestThumb), cacheFile)
                    if (bestThumb.isNotBlank()) {
                        ArtworkCache.registerOnlineArtwork(cacheFile.absolutePath, bestThumb)
                        ArtworkCache.registerOnlineArtwork("online://${playingSong.id}", bestThumb)
                        ArtworkCache.registerOnlineArtwork(playingSong.id, bestThumb)
                        ArtworkCache.registerOnlineArtwork(playingSong.title.lowercase(), bestThumb)
                    }

                    updateQueueWithStreamTrack(playingSong, trackInfo)
                    playTrack(trackInfo)
                    scheduleNextTrackPrefetch()
                },
                onError = { error ->
                    Log.e("AudioEngineController", "Error streaming ${playingSong.title}", error)
                    _isOnlineBuffering.value = false
                    _isOnlineStreaming.value = false
                    _currentOnlineSong.value = null
                }
            )
        }
    }

    private fun updateQueueWithStreamTrack(song: OnlineSongItem, trackInfo: TrackInfo) {
        val curQ = _queue.value
        val updatedQ = curQ.map { qItem ->
            if (qItem.title.equals(song.title, ignoreCase = true) || qItem.uri == "online://${song.id}") {
                trackInfo
            } else {
                qItem
            }
        }
        _queue.value = if (updatedQ.none { it.uri == trackInfo.uri }) listOf(trackInfo) + curQ else updatedQ

        val curOrig = _originalQueue.value
        _originalQueue.value = curOrig.map { qItem ->
            if (qItem.title.equals(song.title, ignoreCase = true) || qItem.uri == "online://${song.id}") {
                trackInfo
            } else {
                qItem
            }
        }
    }

    private var prefetchScheduleJob: Job? = null

    /**
     * Schedules prefetching of the single next track in the active queue.
     * ZERO extra RAM: streams audio directly into a single bounded rolling file on disk.
     */
    private fun scheduleNextTrackPrefetch() {
        prefetchScheduleJob?.cancel()
        prefetchScheduleJob = scope.launch(Dispatchers.IO) {
            delay(2500L) // Wait 2.5s after playback start so initial network is calm
            val q = _queue.value
            if (q.isEmpty()) return@launch

            val current = pendingActiveTrack ?: _status.value?.currentTrack
            val currentIndex = q.indexOfFirst { it.uri == current?.uri || (current != null && it.title.equals(current.title, ignoreCase = true)) }

            val nextTrack = if (_isShuffleEnabled.value) {
                unplayedShufflePool.firstOrNull() ?: q.firstOrNull()
            } else {
                if (currentIndex != -1 && currentIndex < q.size - 1) {
                    q[currentIndex + 1]
                } else if (_repeatMode.value == RepeatMode.ALL) {
                    q.firstOrNull()
                } else {
                    null
                }
            } ?: return@launch

            val isOnline = nextTrack.genre == "Online Stream" ||
                           nextTrack.uri.startsWith("online://") ||
                           nextTrack.uri.contains("veyl_online_stream_")

            if (isOnline && !isTrackDownloaded(nextTrack)) {
                val realTrackArt = ArtworkCache.getOnlineArtwork(nextTrack.uri)
                    ?: ArtworkCache.getOnlineArtwork("online://${nextTrack.uri.removePrefix("online://")}")
                    ?: ArtworkCache.getOnlineArtwork(nextTrack.title.lowercase())

                val cachedSong = _currentOnlineSong.value?.takeIf { it.title.equals(nextTrack.title, ignoreCase = true) }
                    ?: onlineSongMetadataCache[nextTrack.uri]
                    ?: onlineSongMetadataCache[nextTrack.title.lowercase()]

                val songItem = (cachedSong ?: OnlineSongItem(
                    id = nextTrack.uri.removePrefix("online://"),
                    title = nextTrack.title,
                    artist = nextTrack.artist ?: "Unknown Artist",
                    album = nextTrack.album ?: "",
                    durationSeconds = nextTrack.durationSeconds,
                    thumbnailUrl = realTrackArt ?: "",
                    streamUrl = ""
                )).let {
                    if (!realTrackArt.isNullOrBlank()) it.copy(thumbnailUrl = realTrackArt) else it
                }
                onlineStreamManager.prefetchNextTrack(songItem)
            } else if (_gaplessEnabled.value || _crossfadeSeconds.value > 0.05f) {
                val localPath = if (isOnline) {
                    onlineStreamManager.getTargetStorageFileForTrack(nextTrack).absolutePath
                } else {
                    nextTrack.uri
                }
                val dac = connectedDac.value
                val rateSwitchingDac = _bitPerfectEnabled.value && dac != null && nextTrack.sampleRate != currentOutputSampleRate
                if (!rateSwitchingDac && File(localPath).exists()) {
                    withNativeEngine {
                        it.preloadNextTrack(
                            uri = localPath,
                            title = nextTrack.title,
                            artist = nextTrack.artist ?: "Unknown Artist",
                            album = nextTrack.album ?: ""
                        )
                    }
                } else {
                    withNativeEngine { it.clearNextTrack() }
                }
            }
        }
    }

    fun isTrackDownloading(track: TrackInfo?): Boolean {
        if (track == null) return false
        val target = onlineStreamManager.getTargetStorageFileForTrack(track).absolutePath
        return _downloadingUris.value.contains(track.uri) || _downloadingUris.value.contains(target)
    }

    fun isTrackDownloaded(track: TrackInfo?): Boolean {
        if (track == null) return false
        val target = onlineStreamManager.getTargetStorageFileForTrack(track).absolutePath
        if (_downloadedUris.value.contains(track.uri) || _downloadedUris.value.contains(target)) return true
        val targetFile = onlineStreamManager.getTargetStorageFileForTrack(track)
        if (targetFile.exists() && targetFile.length() > 50_000L) return true
        val f = File(track.uri)
        return !track.uri.contains("veyl_online_stream_") && f.exists()
    }

    fun downloadCurrentTrack(
        onCompleted: ((File) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val current = _status.value?.currentTrack ?: return
        downloadTrack(current, onCompleted, onError)
    }

    fun downloadTrack(
        track: TrackInfo,
        onCompleted: ((File) -> Unit)? = null,
        onError: ((Throwable) -> Unit)? = null
    ) {
        val targetFile = onlineStreamManager.getTargetStorageFileForTrack(track)
        val songItem = _currentOnlineSong.value?.takeIf { it.title.equals(track.title, ignoreCase = true) }
            ?: OnlineSongItem(
                id = track.uri,
                title = track.title,
                artist = track.artist ?: "Unknown Artist",
                album = track.album ?: "",
                durationSeconds = track.durationSeconds,
                thumbnailUrl = ArtworkCache.getOnlineArtwork(track.uri) ?: ArtworkCache.getOnlineArtwork(targetFile.absolutePath) ?: "",
                streamUrl = ""
            )

        if (targetFile.exists() && targetFile.length() > 50_000L) {
            scope.launch(Dispatchers.IO) {
                onlineStreamManager.finalizeDownloadedFile(songItem, targetFile)
                val downloadedTrack = track.copy(
                    uri = targetFile.absolutePath,
                    album = if (track.album.isNullOrBlank()) track.title else track.album,
                    hasArtwork = true
                )
                playlistManager.registerOnlineTrack(downloadedTrack)
                playlistManager.updateTrackUri(track.uri, targetFile.absolutePath)
                _downloadedUris.value = _downloadedUris.value + track.uri + targetFile.absolutePath
                withContext(Dispatchers.Main) {
                    addTrackToLibrary(downloadedTrack)
                    onCompleted?.invoke(targetFile)
                }
            }
            return
        }

        if (isTrackDownloading(track)) return

        _downloadingUris.value = _downloadingUris.value + track.uri + targetFile.absolutePath

        scope.launch(Dispatchers.IO) {
            val cacheFile = File(track.uri)
            if (cacheFile.exists() && cacheFile.length() > 50_000L) {
                try {
                    cacheFile.copyTo(targetFile, overwrite = true)
                    onlineStreamManager.finalizeDownloadedFile(songItem, targetFile)
                    val downloadedTrack = track.copy(
                        uri = targetFile.absolutePath,
                        album = if (track.album.isNullOrBlank()) track.title else track.album,
                        hasArtwork = true
                    )
                    playlistManager.registerOnlineTrack(downloadedTrack)
                    playlistManager.updateTrackUri(track.uri, targetFile.absolutePath)
                    _downloadedUris.value = _downloadedUris.value + track.uri + targetFile.absolutePath
                    _downloadingUris.value = _downloadingUris.value - track.uri - targetFile.absolutePath
                    withContext(Dispatchers.Main) {
                        addTrackToLibrary(downloadedTrack)
                        rescanLibrary()
                        onCompleted?.invoke(targetFile)
                    }
                    return@launch
                } catch (e: Exception) {
                    Log.w("AudioEngineController", "Fast copy failed, downloading via stream manager", e)
                }
            }

            onlineStreamManager.downloadSongToStorage(
                song = songItem,
                onProgress = {},
                onCompleted = { file ->
                    val downloadedTrack = track.copy(
                        uri = file.absolutePath,
                        album = if (track.album.isNullOrBlank()) track.title else track.album,
                        hasArtwork = true
                    )
                    playlistManager.registerOnlineTrack(downloadedTrack)
                    playlistManager.updateTrackUri(track.uri, file.absolutePath)
                    _downloadedUris.value = _downloadedUris.value + track.uri + file.absolutePath
                    _downloadingUris.value = _downloadingUris.value - track.uri - targetFile.absolutePath
                    addTrackToLibrary(downloadedTrack)
                    rescanLibrary()
                    onCompleted?.invoke(file)
                },
                onError = { err ->
                    _downloadingUris.value = _downloadingUris.value - track.uri - targetFile.absolutePath
                    onError?.invoke(err)
                }
            )
        }
    }

    fun resolveTrackInfo(uri: String): TrackInfo? {
        val fName = File(uri).name
        if (fName.startsWith(".") || fName.contains("trashed", ignoreCase = true) || fName.startsWith("veyl_online_stream_") || uri.contains("/cache/")) {
            // It's a trashed or temp cache file. Purge from favorites and reject immediately.
            playlistManager.removeFavorites(setOf(uri))
            return null
        }

        val fromLib = _libraryTracks.value.find { it.uri == uri || File(it.uri).name == fName }
        if (fromLib != null) {
            val file = File(fromLib.uri)
            if (!file.exists() && (fromLib.uri.startsWith("/") || fromLib.uri.startsWith("file:"))) {
                playlistManager.removeFavorites(setOf(uri, fromLib.uri))
                return null
            }
            return enrichTracksWithKnownMetadata(listOf(fromLib)).firstOrNull()
        }

        val meta = playlistManager.getTrackMeta(uri)
        if (meta != null) {
            val targetFile = onlineStreamManager.getTargetStorageFileForTrack(meta)
            if (targetFile.exists() && targetFile.length() > 50_000L) {
                return meta.copy(uri = targetFile.absolutePath, hasArtwork = true)
            }
            val isDownloading = _downloadingUris.value.contains(targetFile.absolutePath) || _downloadingUris.value.contains(uri)
            if (!isDownloading && meta.genre != "Imported" && (meta.uri.startsWith("/") || meta.uri.startsWith("file:"))) {
                val origFile = File(meta.uri.removePrefix("file://"))
                if (!origFile.exists()) {
                    // Song was deleted from storage!
                    playlistManager.removeTrackEverywhere(uri, meta.title, meta.artist)
                    return null
                }
            }
            if (meta.title.contains("trashed", ignoreCase = true)) {
                playlistManager.removeTrackEverywhere(uri, meta.title, meta.artist)
                return null
            }
            return meta
        }

        val current = _status.value?.currentTrack
        if (current?.uri == uri) {
            val cName = File(current.uri).name
            if (!cName.startsWith(".") && !cName.contains("trashed", ignoreCase = true) && !cName.startsWith("veyl_online_stream_")) {
                return current
            }
        }

        val f = File(uri)
        if (f.exists()) {
            if (f.name.startsWith(".") || f.name.contains("trashed", ignoreCase = true) || f.name.startsWith("veyl_online_stream_") || f.nameWithoutExtension.contains("trashed", ignoreCase = true)) {
                playlistManager.removeFavorites(setOf(uri, f.name))
                return null
            }
            val match = playlistManager.onlineTrackMeta.value.values.find { File(it.uri).name == f.name }
            if (match != null) {
                return match.copy(uri = f.absolutePath, hasArtwork = true)
            }
            // Only create fallback TrackInfo for recognized audio file extensions
            val ext = f.extension.lowercase()
            val validAudioExts = setOf("flac", "wav", "mp3", "m4a", "aac", "ogg", "opus", "dsf", "dff")
            if (!validAudioExts.contains(ext)) {
                return null
            }
            val companionJpg = File(f.parentFile ?: File(""), "${f.nameWithoutExtension}.jpg")
            return TrackInfo(
                uri = f.absolutePath,
                title = f.nameWithoutExtension,
                artist = "Unknown Artist",
                album = "",
                albumArtist = null,
                genre = "Audio",
                year = null,
                trackNumber = null,
                discNumber = null,
                durationSeconds = 0.0,
                sampleRate = 44100u,
                bitDepth = 16u.toUByte(),
                bitrate = 320000u,
                channels = 2u.toUByte(),
                formatName = ext.uppercase(),
                hasArtwork = true
            )
        } else {
            if (uri.startsWith("/") || uri.startsWith("file:")) {
                playlistManager.removeFavorites(setOf(uri))
            }
        }
        return null
    }

    fun downloadOnlineSong(
        song: OnlineSongItem,
        onProgress: (Float) -> Unit = {},
        onCompleted: (File) -> Unit = {},
        onError: (Throwable) -> Unit = {}
    ) {
        val targetFile = onlineStreamManager.getTargetStorageFile(song)
        _downloadingUris.value = _downloadingUris.value + targetFile.absolutePath
        scope.launch(Dispatchers.IO) {
            onlineStreamManager.downloadSongToStorage(
                song = song,
                onProgress = onProgress,
                onCompleted = { file ->
                    val downloadedTrack = TrackInfo(
                        uri = file.absolutePath,
                        title = song.title,
                        artist = song.artist,
                        album = song.album.ifBlank { song.title },
                        albumArtist = song.artist,
                        genre = "Music",
                        year = null,
                        trackNumber = null,
                        discNumber = null,
                        durationSeconds = song.durationSeconds,
                        sampleRate = 44100u,
                        bitDepth = 16u.toUByte(),
                        bitrate = 320000u,
                        channels = 2u.toUByte(),
                        formatName = "AAC",
                        hasArtwork = true
                    )
                    playlistManager.registerOnlineTrack(downloadedTrack, song)
                    if (song.thumbnailUrl.isNotBlank()) {
                        ArtworkCache.registerOnlineArtwork(file.absolutePath, song.thumbnailUrl)
                        ArtworkCache.registerOnlineArtwork("file://${file.absolutePath}", song.thumbnailUrl)
                        ArtworkCache.registerOnlineArtwork(file.name, song.thumbnailUrl)
                    }
                    _downloadedUris.value = _downloadedUris.value + file.absolutePath + song.id
                    _downloadingUris.value = _downloadingUris.value - targetFile.absolutePath
                    addTrackToLibrary(downloadedTrack)
                    rescanLibrary()
                    onCompleted(file)
                },
                onError = { err ->
                    _downloadingUris.value = _downloadingUris.value - targetFile.absolutePath
                    onError(err)
                }
            )
        }
    }

    fun playTrack(track: TrackInfo) {
        var actualTrack = track
        val cueRange = CueSheetParser.parseCueUri(actualTrack.uri)
        val f = File(cueRange?.basePath ?: actualTrack.uri)
        if (!f.exists()) {
            val targetFile = onlineStreamManager.getTargetStorageFileForTrack(actualTrack)
            if (targetFile.exists() && targetFile.length() > 50_000L) {
                actualTrack = actualTrack.copy(uri = targetFile.absolutePath)
                playlistManager.updateTrackUri(track.uri, targetFile.absolutePath)
            } else if (actualTrack.genre == "Online Stream" || actualTrack.genre == "Imported" || actualTrack.uri.contains("veyl_online_stream_") || actualTrack.uri.startsWith("online://")) {
                val realTrackArt = ArtworkCache.getOnlineArtwork(actualTrack.uri)
                    ?: ArtworkCache.getOnlineArtwork("online://${actualTrack.uri.removePrefix("online://")}")
                    ?: ArtworkCache.getOnlineArtwork(actualTrack.title.lowercase())

                val cachedSong = _currentOnlineSong.value?.takeIf { it.title.equals(actualTrack.title, ignoreCase = true) }
                    ?: onlineSongMetadataCache[actualTrack.uri]
                    ?: onlineSongMetadataCache[actualTrack.title.lowercase()]

                val songItem = (cachedSong ?: OnlineSongItem(
                    id = actualTrack.uri.removePrefix("online://"),
                    title = actualTrack.title,
                    artist = actualTrack.artist ?: "Unknown Artist",
                    album = actualTrack.album ?: "",
                    durationSeconds = actualTrack.durationSeconds,
                    thumbnailUrl = realTrackArt ?: "",
                    streamUrl = ""
                )).let {
                    if (!realTrackArt.isNullOrBlank()) it.copy(thumbnailUrl = realTrackArt) else it
                }
                playOnlineSong(song = songItem, preserveQueue = true)
                return
            }
        }

        ensurePlaybackServiceStarted()
        val isOnline = actualTrack.uri.contains("veyl_online_stream_") || actualTrack.genre == "Online Stream"
        if (!isOnline && _isOnlineStreaming.value) {
            onlineStreamManager.stopCurrentStream()
            _isOnlineStreaming.value = false
            _isOnlineBuffering.value = false
            _currentOnlineSong.value = null
        }

        // Immediately record play count and update playback history in real time
        recordTrackPlayed(actualTrack.uri, actualTrack)
        pendingActiveTrack = actualTrack

        playTrackJob?.cancel()
        playTrackJob = nativeScope.launch {
            try {
            // Only switch hardware sample-rate and exclusive mode for external USB DACs!
            // Internal phone speakers, 3.5mm jack, and Bluetooth must remain on standard shared 48kHz output.
            val dac = connectedDac.value
            if (_bitPerfectEnabled.value && dac != null) {
                val isDsd = actualTrack.formatName.contains("DSD", ignoreCase = true) ||
                            actualTrack.uri.endsWith(".dsf", ignoreCase = true) ||
                            actualTrack.uri.endsWith(".dff", ignoreCase = true)

                val targetRate = if (isDsd) {
                    if (_dsdMode.value == DsdModeEnum.DO_P) {
                        if (actualTrack.sampleRate > 2822400u) 352800 else 176400
                    } else {
                        if (actualTrack.sampleRate > 2822400u) 176400 else 88200
                    }
                } else {
                    actualTrack.sampleRate.toInt()
                }

                val optimalRate = usbDacManager.getOptimalSampleRate(targetRate).toUInt()
                if (optimalRate != currentOutputSampleRate) {
                    Log.i("AudioEngineController", "Switching DAC sample rate to $optimalRate Hz for bit-perfect playback")
                    reconfigureHardwareOutput(optimalRate, dac.id, exclusive = true)
                }
            } else if (dac == null) {
                val sinkRate = getOptimalNonUsbSampleRate(actualTrack.sampleRate.toInt()).toUInt()
                if (sinkRate != currentOutputSampleRate) {
                    Log.i("AudioEngineController", "Matching active Bluetooth/wired sink sample rate: $sinkRate Hz")
                    reconfigureHardwareOutput(sinkRate, null, exclusive = false)
                }
            }

            isTransitioning = false
            lastTransitionTimestamp = System.currentTimeMillis()
            _currentLyrics.value = null
            _currentPositionSec.value = 0.0
            _currentPositionMs.value = 0L

            if (cueRange != null) {
                val prevCueBase = activeCueRange?.basePath
                activeCueTrack = actualTrack
                activeCueRange = cueRange
                if (prevCueBase == cueRange.basePath) {
                    withNativeEngine { engine ->
                        engine.seek(cueRange.startSec)
                        engine.play()
                    }
                } else {
                    withNativeEngine { engine ->
                        engine.playTrack(
                            uri = cueRange.basePath,
                            title = actualTrack.title,
                            artist = actualTrack.artist,
                            album = actualTrack.album
                        )
                        engine.seek(cueRange.startSec)
                    }
                }
            } else {
                activeCueTrack = null
                activeCueRange = null
                withNativeEngine { engine -> engine.playTrack(
                    uri = actualTrack.uri,
                    title = actualTrack.title,
                    artist = actualTrack.artist,
                    album = actualTrack.album
                ) }
            }

            // Re-apply user's persistent equalizer state on track transition
            withNativeEngine { it.setEqEnabled(_isEqEnabled.value) }

            // Ensure queue always contains all tracks if queue was empty or single
            val currentQ = _queue.value
            if (isOnline) {
                if (currentQ.isEmpty()) {
                    _queue.value = listOf(actualTrack)
                    _originalQueue.value = listOf(actualTrack)
                }
            } else if (currentQ.isEmpty() || currentQ.size <= 1) {
                val lib = _libraryTracks.value
                if (lib.isNotEmpty()) {
                    _queue.value = lib
                    _originalQueue.value = lib
                } else {
                    _queue.value = listOf(actualTrack)
                }
            } else if (!currentQ.any { it.uri == track.uri }) {
                _queue.value = currentQ + track
            }
            scheduleNextTrackPrefetch()
            } catch (error: Throwable) {
                Log.e("AudioEngineController", "Unable to start playback for ${track.uri}", error)
            }
        }
    }

    fun reconfigureHardwareOutput(sampleRate: UInt, deviceId: Int?, exclusive: Boolean): Boolean {
        return try {
            val bufFrames = _bufferFrameSize.value.coerceIn(64, 4096).toUInt()
            val success = withNativeEngine { it.reconfigureOutput(sampleRate, 2u, deviceId, exclusive, bufFrames) } ?: false
            if (success) {
                val actualRate = withNativeEngine { it.getOutputSampleRate() } ?: sampleRate
                currentOutputSampleRate = actualRate
                Log.i("AudioEngineController", "Hardware output reconfigured: target=$sampleRate Hz, actual=$actualRate Hz")
                withNativeEngine {
                    syncAllDspState(it)
                }
                val bands = _eqBands.value
                for (i in bands.indices) {
                    val b = bands[i]
                    withNativeEngine { it.setEqBand(i.toUInt(), b.gainDb, b.frequency, b.q) }
                }
                refreshEq()
            }
            success
        } catch (e: Exception) {
            Log.e("AudioEngineController", "Failed to reconfigure audio hardware output", e)
            false
        }
    }

    /**
     * Refreshes and reopens the native audio output stream (e.g. after phone call ends or audio route change).
     * Guarantees that the hardware sample rate matches the native mixer and clears any pitch distortion.
     */
    fun refreshHardwareOutput() {
        try {
            val dac = connectedDac.value
            val targetRate = if (_bitPerfectEnabled.value && dac != null) {
                usbDacManager.getOptimalSampleRate(currentOutputSampleRate.toInt()).toUInt()
            } else {
                48000u
            }
            val exclusive = _bitPerfectEnabled.value && dac != null
            Log.i("AudioEngineController", "Refreshing hardware output stream: rate=$targetRate Hz, deviceId=${dac?.id}, exclusive=$exclusive")
            reconfigureHardwareOutput(targetRate, dac?.id, exclusive)
        } catch (e: Exception) {
            Log.e("AudioEngineController", "Error refreshing hardware output stream", e)
        }
    }

    fun setDsdMode(mode: DsdModeEnum) {
        _dsdMode.value = mode
        prefs.edit().putString("dsd_mode", if (mode == DsdModeEnum.DO_P) "dop" else "pcm").apply()
        nativeScope.launch { withNativeEngine { it.setDsdMode(mode) } }
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        _bitPerfectEnabled.value = enabled
        prefs.edit().putBoolean("bit_perfect_enabled", enabled).apply()
    }

    fun togglePlayPause() {
        val current = _status.value?.state ?: PlaybackStateEnum.STOPPED
        if (current == PlaybackStateEnum.PLAYING) {
            pause()
        } else {
            play()
        }
    }

    fun play() {
        ensurePlaybackServiceStarted()
        nativeScope.launch {
            val curStatus = _status.value
            val curTrack = curStatus?.currentTrack
            if (curTrack == null) {
                val trackToPlay = _queue.value.firstOrNull() ?: _libraryTracks.value.firstOrNull()
                if (trackToPlay != null) {
                    playTrack(trackToPlay)
                    return@launch
                }
            } else if (curStatus.state == PlaybackStateEnum.ENDED || curStatus.state == PlaybackStateEnum.STOPPED) {
                playTrack(curTrack)
                return@launch
            }
            // If playback was stalled, refresh the hardware stream before resuming
            if (isPlaybackStalled) {
                refreshHardwareOutput()
            }
            withNativeEngine { it.play() }
        }
    }

    fun pause() {
        nativeScope.launch { withNativeEngine { it.pause() } }
    }

    fun stop() {
        onlineStreamManager.stopCurrentStream()
        onlineStreamManager.cancelPrefetch()
        prefetchScheduleJob?.cancel()
        _isOnlineStreaming.value = false
        _isOnlineBuffering.value = false
        _currentOnlineSong.value = null
        nativeScope.launch { withNativeEngine { it.stop() } }
    }

    fun seekTo(seconds: Double) {
        val safeSeconds = seconds.coerceAtLeast(0.0)
        _currentPositionSec.value = safeSeconds
        _currentPositionMs.value = (safeSeconds * 1000.0).toLong()
        _status.value?.let { current ->
            _status.value = current.copy(positionSeconds = safeSeconds)
        }
        val targetSec = activeCueRange?.let { it.startSec + safeSeconds } ?: safeSeconds
        nativeScope.launch { withNativeEngine { it.seek(targetSec) } }
    }

    /** Current software volume (0.0–1.0). Exposed so PlaybackService can save it before ducking. */
    private val _volume = MutableStateFlow(1.0f)
    val volume: Float get() = _volume.value

    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        _volume.value = clamped
        nativeScope.launch { withNativeEngine { it.setVolume(clamped) } }
    }

    fun toggleEq(enabled: Boolean) {
        setEqEnabled(enabled)
    }

    fun setEqEnabled(enabled: Boolean) {
        _isEqEnabled.value = enabled
        prefs.edit().putBoolean("eq_enabled", enabled).apply()
        nativeScope.launch { withNativeEngine { it.setEqEnabled(enabled) } }
        refreshEq()
    }

    fun setEqBandGain(index: Int, gainDb: Float) {
        val devKey = getActiveOutputDeviceKey()
        prefs.edit()
            .putFloat("eq_band_$index", gainDb)
            .putFloat("eq_${devKey}_band_$index", gainDb)
            .putString("eq_preset", "Custom")
            .apply()
        _currentEqPreset.value = "Custom"
        val bands = _eqBands.value
        if (index in bands.indices) {
            val b = bands[index]
            val updatedGains = bands.mapIndexed { i, band -> if (i == index) gainDb else band.gainDb }
            applyAutoPreampForBands(updatedGains)
            updateEqBand(index.toUInt(), gainDb, b.frequency, b.q)
        }
    }

    fun setEqBand(index: Int, gainDb: Float) = setEqBandGain(index, gainDb)

    fun setEqPreset(name: String, gains: List<Float>) {
        _currentEqPreset.value = name
        val devKey = getActiveOutputDeviceKey()
        val editor = prefs.edit().putString("eq_preset", name)
        for (i in gains.indices) {
            editor.putFloat("eq_band_$i", gains[i])
            editor.putFloat("eq_${devKey}_band_$i", gains[i])
        }
        editor.putBoolean("eq_enabled", true)
        editor.apply()
        _isEqEnabled.value = true

        val defaultFreqs = floatArrayOf(31.25f, 62.5f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
        val defaultQ = 1.414f
        val bands = _eqBands.value

        nativeScope.launch {
            withNativeEngine { engine ->
                for (i in gains.indices) {
                    val freq = if (i in bands.indices) bands[i].frequency else defaultFreqs.getOrElse(i) { 1000f }
                    val q = if (i in bands.indices) bands[i].q else defaultQ
                    engine.setEqBand(i.toUInt(), gains[i], freq, q)
                }
                engine.setEqEnabled(true)
            }
            applyAutoPreampForBands(gains)
            refreshEq()
        }
    }

    fun setCrossfeedPreset(preset: uniffi.audiophile_core.CrossfeedPresetEnum) {
        _crossfeedPreset.value = preset
        prefs.edit().putString("crossfeed_preset", preset.name).apply()
        nativeScope.launch { withNativeEngine { it.setCrossfeedPreset(preset) } }
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val totalSec = minutes * 60
        _sleepTimerActiveOption.value = "${minutes}m"
        _sleepTimerRemainingSeconds.value = totalSec
        sleepTimerPreFadeVolume = _volume.value
        isSleepTimerFading = false

        sleepTimerJob = scope.launch {
            var secLeft = totalSec
            while (isActive && secLeft > 0) {
                delay(1000)
                secLeft--
                _sleepTimerRemainingSeconds.value = secLeft

                // Apply smooth logarithmic fade-out during final 30 seconds
                if (secLeft <= 30) {
                    isSleepTimerFading = true
                    val factor = (secLeft.toFloat() / 30f).coerceIn(0f, 1f)
                    val fadeVol = sleepTimerPreFadeVolume * (factor * factor)
                    setVolume(fadeVol)
                }
            }

            if (isActive) {
                pause()
                setVolume(sleepTimerPreFadeVolume)
                _sleepTimerActiveOption.value = null
                _sleepTimerRemainingSeconds.value = null
                isSleepTimerFading = false
            }
        }
    }

    fun startSleepTimerEndOfTrack() {
        cancelSleepTimer()
        _sleepTimerActiveOption.value = "track"
        sleepTimerPreFadeVolume = _volume.value
        isSleepTimerFading = false

        sleepTimerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val currStatus = _status.value
                val duration = currStatus?.durationSeconds ?: 0.0
                val position = currStatus?.positionSeconds ?: 0.0

                if (duration > 0.0) {
                    val remaining = (duration - position).toInt().coerceAtLeast(0)
                    _sleepTimerRemainingSeconds.value = remaining

                    // Smooth fade out over last 15 seconds
                    if (remaining in 1..15) {
                        isSleepTimerFading = true
                        val factor = (remaining.toFloat() / 15f).coerceIn(0f, 1f)
                        val fadeVol = sleepTimerPreFadeVolume * (factor * factor)
                        setVolume(fadeVol)
                    }

                    if (remaining <= 0 || position >= duration - 0.5) {
                        pause()
                        setVolume(sleepTimerPreFadeVolume)
                        _sleepTimerActiveOption.value = null
                        _sleepTimerRemainingSeconds.value = null
                        isSleepTimerFading = false
                        break
                    }
                }
            }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        if (isSleepTimerFading) {
            setVolume(sleepTimerPreFadeVolume)
            isSleepTimerFading = false
        }
        _sleepTimerActiveOption.value = null
        _sleepTimerRemainingSeconds.value = null
    }

    fun setPauseOnDisconnect(enabled: Boolean) {
        _pauseOnDisconnect.value = enabled
        prefs.edit().putBoolean("pause_on_disconnect", enabled).apply()
    }

    fun setLoudnessNormalizerEnabled(enabled: Boolean) {
        _loudnessNormalizerEnabled.value = enabled
        prefs.edit().putBoolean("loudness_normalizer_enabled", enabled).apply()
        nativeScope.launch { withNativeEngine { it.setLoudnessNormalizerEnabled(enabled) } }
    }

    fun setLoudnessTargetLufs(lufs: Float) {
        _loudnessTargetLufs.value = lufs
        prefs.edit().putFloat("loudness_target_lufs", lufs).apply()
        nativeScope.launch { withNativeEngine { it.setLoudnessTargetLufs(lufs) } }
    }

    fun setLimiterEnabled(enabled: Boolean) {
        _limiterEnabled.value = enabled
        prefs.edit().putBoolean("limiter_enabled", enabled).apply()
        nativeScope.launch { withNativeEngine { it.setLimiterEnabled(enabled) } }
    }

    fun setTubeWarmthEnabled(enabled: Boolean) {
        _tubeWarmthEnabled.value = enabled
        prefs.edit().putBoolean("tube_warmth_enabled", enabled).apply()
        Log.i("AudioEngineController", "DSP setTubeWarmthEnabled: $enabled")
        nativeScope.launch { withNativeEngine { it.setTubeWarmthEnabled(enabled) } }
    }

    fun setTubeWarmthDrive(drive: Float) {
        val clamped = drive.coerceIn(0.0f, 1.0f)
        _tubeWarmthDrive.value = clamped
        prefs.edit().putFloat("tube_warmth_drive", clamped).apply()
        Log.i("AudioEngineController", "DSP setTubeWarmthDrive: $clamped")
        nativeScope.launch { withNativeEngine { it.setTubeWarmthDrive(clamped) } }
    }

    fun setSpatializerEnabled(enabled: Boolean) {
        _spatializerEnabled.value = enabled
        prefs.edit().putBoolean("spatializer_enabled", enabled).apply()
        Log.i("AudioEngineController", "DSP setSpatializerEnabled: $enabled")
        nativeScope.launch { withNativeEngine { it.setSpatializerEnabled(enabled) } }
    }

    fun setSpatializerWidth(width: Float) {
        val clamped = width.coerceIn(0.5f, 2.2f)
        _spatializerWidth.value = clamped
        prefs.edit().putFloat("spatializer_width", clamped).apply()
        Log.i("AudioEngineController", "DSP setSpatializerWidth: $clamped")
        nativeScope.launch { withNativeEngine { it.setSpatializerWidth(clamped) } }
    }

    fun isHeadphonesConnected(): Boolean {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return true
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        return devices.any { dev ->
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
            dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_USB_DEVICE
        }
    }

    fun setSpatializerHeadphoneMode(isHeadphone: Boolean) {
        _spatializerIsHeadphoneMode.value = isHeadphone
        nativeScope.launch { withNativeEngine { it.setSpatializerHeadphoneMode(isHeadphone) } }
    }

    fun updateSpatializerAudioRouting() {
        val isHeadphone = isHeadphonesConnected()
        _spatializerIsHeadphoneMode.value = isHeadphone
        nativeScope.launch { withNativeEngine { it.setSpatializerHeadphoneMode(isHeadphone) } }
    }

    fun setSubBassEnabled(enabled: Boolean) {
        _subBassEnabled.value = enabled
        prefs.edit().putBoolean("sub_bass_enabled", enabled).apply()
        Log.i("AudioEngineController", "DSP setSubBassEnabled: $enabled")
        nativeScope.launch { withNativeEngine { it.setSubBassEnabled(enabled) } }
    }

    fun setSubBassIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.0f, 1.0f)
        _subBassIntensity.value = clamped
        prefs.edit().putFloat("sub_bass_intensity", clamped).apply()
        Log.i("AudioEngineController", "DSP setSubBassIntensity: $clamped")
        nativeScope.launch { withNativeEngine { it.setSubBassIntensity(clamped) } }
    }

    fun setVocalClarityEnabled(enabled: Boolean) {
        _vocalClarityEnabled.value = enabled
        prefs.edit().putBoolean("vocal_clarity_enabled", enabled).apply()
        Log.i("AudioEngineController", "DSP setVocalClarityEnabled: $enabled")
        nativeScope.launch { withNativeEngine { it.setVocalClarityEnabled(enabled) } }
    }

    fun setVocalClarityIntensity(intensity: Float) {
        val clamped = intensity.coerceIn(0.0f, 1.0f)
        _vocalClarityIntensity.value = clamped
        prefs.edit().putFloat("vocal_clarity_intensity", clamped).apply()
        Log.i("AudioEngineController", "DSP setVocalClarityIntensity: $clamped")
        nativeScope.launch { withNativeEngine { it.setVocalClarityIntensity(clamped) } }
    }

    fun setChannelBalance(balance: Float) {
        val clamped = balance.coerceIn(-1.0f, 1.0f)
        _channelBalance.value = clamped
        prefs.edit().putFloat("channel_balance", clamped).apply()
        nativeScope.launch { withNativeEngine { it.setChannelBalance(clamped) } }
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        _playbackSpeed.value = clamped
        nativeScope.launch { withNativeEngine { it.setPlaybackSpeed(clamped) } }
    }

    fun setLoopPointA(sec: Double? = null) {
        val pos = sec ?: _currentPositionSec.value
        _loopPointA.value = pos
        val b = _loopPointB.value
        if (b != null && b <= pos) {
            _loopPointB.value = null
        }
    }

    fun setLoopPointB(sec: Double? = null) {
        val pos = sec ?: _currentPositionSec.value
        val a = _loopPointA.value
        if (a != null && pos > a) {
            _loopPointB.value = pos
        }
    }

    fun clearLoop() {
        _loopPointA.value = null
        _loopPointB.value = null
    }

    fun toggleLoopPoint() {
        if (_loopPointA.value == null) {
            setLoopPointA()
        } else if (_loopPointB.value == null) {
            setLoopPointB()
        } else {
            clearLoop()
        }
    }

    private fun loadCustomEqPresets() {
        try {
            val jsonStr = prefs.getString("custom_eq_presets_json", "[]") ?: "[]"
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<CustomEqPreset>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val id = obj.optString("id", UUID.randomUUID().toString())
                val name = obj.optString("name", "Custom")
                val gainsArr = obj.optJSONArray("gains")
                val gains = mutableListOf<Float>()
                if (gainsArr != null) {
                    for (j in 0 until gainsArr.length()) {
                        gains.add(gainsArr.optDouble(j, 0.0).toFloat())
                    }
                }
                if (gains.size == 10) {
                    list.add(CustomEqPreset(id, name, gains))
                }
            }
            _customEqPresets.value = list
        } catch (e: Exception) {
            Log.e("AudioEngineController", "Error loading custom EQ presets", e)
        }
    }

    private fun saveCustomEqPresetsToPrefs(list: List<CustomEqPreset>) {
        try {
            val arr = JSONArray()
            for (preset in list) {
                val obj = JSONObject()
                obj.put("id", preset.id)
                obj.put("name", preset.name)
                val gainsArr = JSONArray()
                for (g in preset.gains) {
                    gainsArr.put(g.toDouble())
                }
                obj.put("gains", gainsArr)
                arr.put(obj)
            }
            prefs.edit().putString("custom_eq_presets_json", arr.toString()).apply()
        } catch (e: Exception) {
            Log.e("AudioEngineController", "Error saving custom EQ presets", e)
        }
    }

    fun saveCustomEqPreset(name: String, gains: List<Float>): CustomEqPreset {
        val trimmedName = name.trim().ifEmpty { "My Preset" }
        val currentList = _customEqPresets.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.name.equals(trimmedName, ignoreCase = true) }
        val preset = if (existingIndex >= 0) {
            val updated = currentList[existingIndex].copy(gains = gains)
            currentList[existingIndex] = updated
            updated
        } else {
            val newPreset = CustomEqPreset(
                id = UUID.randomUUID().toString(),
                name = trimmedName,
                gains = gains
            )
            currentList.add(newPreset)
            newPreset
        }
        _customEqPresets.value = currentList
        saveCustomEqPresetsToPrefs(currentList)
        setEqPreset(preset.name, preset.gains)
        return preset
    }

    fun deleteCustomEqPreset(id: String) {
        val currentList = _customEqPresets.value.toMutableList()
        val removed = currentList.firstOrNull { it.id == id }
        if (removed != null) {
            currentList.removeAll { it.id == id }
            _customEqPresets.value = currentList
            saveCustomEqPresetsToPrefs(currentList)
            if (_currentEqPreset.value == removed.name) {
                setEqPreset("Flat", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f))
            }
        }
    }

    fun setEqPreamp(gainDb: Float) {
        nativeScope.launch { withNativeEngine { it.setEqPreamp(gainDb) } }
        refreshEq()
    }

    fun updateEqBand(index: UInt, gainDb: Float, freq: Float, q: Float) {
        nativeScope.launch { withNativeEngine { it.setEqBand(index, gainDb, freq, q) } }
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

    fun reloadLyrics(allowOnline: Boolean = true, forceRefresh: Boolean = false) {
        val current = _status.value?.currentTrack ?: return
        scope.launch {
            _isLoadingLyrics.value = true
            val lyrics = try {
                LyricsManager.loadLyricsForTrack(
                    trackUri = current.uri,
                    title = current.title,
                    artist = current.artist,
                    album = current.album,
                    durationSeconds = current.durationSeconds,
                    allowOnlineFetch = allowOnline,
                    preferredProvider = _lyricsProvider.value,
                    forceRefresh = forceRefresh
                )
            } finally {
                _isLoadingLyrics.value = false
            }
            if (_status.value?.currentTrack?.uri == current.uri) {
                _currentLyrics.value = lyrics
            }
        }
    }

    fun setLyricsProvider(provider: LyricsProvider) {
        _lyricsProvider.value = provider
        prefs.edit().putString("lyrics_provider", provider.id).apply()
        reloadLyrics(allowOnline = true, forceRefresh = true)
    }

    fun toggleRomanizeLyrics() {
        val next = !_romanizeLyrics.value
        _romanizeLyrics.value = next
        prefs.edit().putBoolean("romanize_lyrics", next).apply()
    }

    fun setGaplessEnabled(enabled: Boolean) {
        _gaplessEnabled.value = enabled
        prefs.edit().putBoolean("gapless_enabled", enabled).apply()
        nativeScope.launch {
            withNativeEngine { it.setGaplessEnabled(enabled) }
            if (enabled) scheduleNextTrackPrefetch()
        }
    }

    fun setCrossfadeSeconds(sec: Float) {
        _crossfadeSeconds.value = sec
        prefs.edit().putFloat("crossfade_seconds", sec).apply()
        nativeScope.launch {
            withNativeEngine { it.setCrossfade(sec, toNativeCrossfadeCurve(_crossfadeCurve.value)) }
            if (sec > 0.05f) scheduleNextTrackPrefetch()
        }
    }

    fun setCrossfadeCurve(curve: CrossfadeCurve) {
        _crossfadeCurve.value = curve
        prefs.edit().putString("crossfade_curve", curve.name).apply()
        nativeScope.launch {
            withNativeEngine { it.setCrossfade(_crossfadeSeconds.value, toNativeCrossfadeCurve(curve)) }
        }
    }

    fun setReplayGainMode(mode: uniffi.audiophile_core.ReplayGainModeEnum) {
        _replayGainMode.value = mode
        prefs.edit().putString("replaygain_mode", mode.name).apply()
        nativeScope.launch {
            withNativeEngine { it.setReplaygainMode(mode) }
        }
    }

    fun setAutoEqPreamp(enabled: Boolean) {
        _autoEqPreamp.value = enabled
        prefs.edit().putBoolean("auto_eq_preamp", enabled).apply()
        applyAutoPreampForBands(_eqBands.value.map { it.gainDb })
        refreshEq()
    }

    fun startDiagnosticTone(type: DacToneType) {
        pause()
        toneGenerator.startTone(type)
    }

    fun stopDiagnosticTone() {
        toneGenerator.stopTone()
    }

    fun setBufferFrameSize(size: Int) {
        _bufferFrameSize.value = size
        prefs.edit().putInt("buffer_frame_size", size).apply()
        nativeScope.launch {
            refreshHardwareOutput()
        }
    }

    fun refreshEq() {
        eqUpdateJob?.cancel()
        eqUpdateJob = scope.launch {
            kotlinx.coroutines.delay(20) // Debounce native FFI curve calls during slider drag
            withNativeEngine { engine ->
                val bands = engine.getEqBands()
                val curve = engine.getEqCurve(100u)
                bands to curve
            }?.let { (bands, curve) ->
                _eqBands.value = bands
                _eqCurve.value = curve
                _eqState.value = EqState(enabled = _isEqEnabled.value, bands = bands)
            }
        }
    }

    companion object {
        @Volatile
        @Suppress("StaticFieldLeak") // The controller receives and retains applicationContext only.
        private var INSTANCE: AudioEngineController? = null

        fun getInstance(context: Context): AudioEngineController {
            ArtworkCache.init(context.applicationContext)
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AudioEngineController(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
