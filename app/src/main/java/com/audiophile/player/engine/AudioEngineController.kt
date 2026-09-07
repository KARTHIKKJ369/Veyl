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

    private val _isEqEnabled = MutableStateFlow(false)
    val isEqEnabled: StateFlow<Boolean> = _isEqEnabled.asStateFlow()

    data class EqState(val enabled: Boolean = false, val bands: List<EqBandInfo> = emptyList())
    private val _eqState = MutableStateFlow(EqState())
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
            refreshEq()
        } catch (e: Throwable) {
            Log.e("AudioEngineController", "Failed to initialize native AudiophileEngineHandle", e)
        }
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
            while (isActive) {
                try {
                    nativeEngine?.let { engine ->
                        val currentStatus = engine.getStatus()
                        _status.value = currentStatus

                        val currentUri = currentStatus.currentTrack?.uri
                        if (currentUri != null && currentUri != lastTrackUri) {
                            lastTrackUri = currentUri
                            isTransitioning = false

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
                                _currentLyrics.value = lyrics
                            }
                            // Preload artwork into memory
                            ArtworkCache.loadArtwork(currentUri, 512)
                        }

                        // Automatic and seamless track progression
                        val pos = currentStatus.positionSeconds
                        val dur = currentStatus.durationSeconds
                        val now = System.currentTimeMillis()

                        // Reset transition lock once new track is playing at start
                        if (pos < 1.5 && currentStatus.state == PlaybackStateEnum.PLAYING) {
                            isTransitioning = false
                        }

                        val isEnded = currentStatus.state == PlaybackStateEnum.ENDED ||
                                      (dur > 2.0 && pos >= dur - 0.45 && currentStatus.state == PlaybackStateEnum.PLAYING)

                        if (isEnded && !isTransitioning && (now - lastTransitionTimestamp > 1500L)) {
                            isTransitioning = true
                            lastTransitionTimestamp = now
                            Log.i("AudioEngineController", "Track completed ($pos / $dur, state=${currentStatus.state}). Auto-advancing next track.")
                            handleTrackEnded()
                        }
                    }
                } catch (e: Exception) {
                    Log.w("AudioEngineController", "Polling status error", e)
                }
                delay(150) // Smooth ~7 FPS UI scrubber updates with zero frame jank
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
        }
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

            nativeEngine?.playTrack(
                uri = track.uri,
                title = track.title,
                artist = track.artist,
                album = track.album
            )

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
        nativeEngine?.seek(seconds)
    }

    fun setVolume(volume: Float) {
        nativeEngine?.setVolume(volume)
    }

    fun toggleEq(enabled: Boolean) {
        setEqEnabled(enabled)
    }

    fun setEqEnabled(enabled: Boolean) {
        _isEqEnabled.value = enabled
        nativeEngine?.setEqEnabled(enabled)
        refreshEq()
    }

    fun setEqBandGain(index: Int, gainDb: Float) {
        val bands = _eqBands.value
        if (index in bands.indices) {
            val b = bands[index]
            updateEqBand(index.toUInt(), gainDb, b.frequency, b.q)
        }
    }

    fun setEqBand(index: Int, gainDb: Float) = setEqBandGain(index, gainDb)

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
