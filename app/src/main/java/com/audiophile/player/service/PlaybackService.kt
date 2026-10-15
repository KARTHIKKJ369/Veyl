package com.audiophile.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media.app.NotificationCompat.MediaStyle
import androidx.media.session.MediaButtonReceiver
import com.audiophile.player.MainActivity
import com.audiophile.player.R
import com.audiophile.player.engine.ArtworkCache
import com.audiophile.player.engine.AudioEngineController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.audiophile_core.PlaybackStateEnum
import uniffi.audiophile_core.TrackInfo

class PlaybackService : MediaBrowserServiceCompat() {

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private lateinit var engineController: AudioEngineController
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager

    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false
    private var isNoisyReceiverRegistered = false
    // Volume in effect before a transient duck so we can restore it exactly.
    private var preDuckVolume = 1.0f

    private var lastTrackUri: String? = null
    private var cachedArtwork: Bitmap? = null
    private var artworkJob: Job? = null

    private var lastPostedIsPlaying: Boolean? = null
    private var lastMediaSessionPosSec: Double = -1.0

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    serviceScope.launch {
                        // Allow Android audio HAL to settle back to MODE_NORMAL after call termination
                        var attempts = 0
                        while (audioManager.mode != AudioManager.MODE_NORMAL && attempts < 10) {
                            delay(50)
                            attempts++
                        }
                        delay(120) // Settling window for audio routing switch
                        engineController.refreshHardwareOutput()
                        engineController.setVolume(preDuckVolume)
                        engineController.play()
                    }
                } else {
                    engineController.setVolume(preDuckVolume)
                    // If music was continuously playing during a phone call, refresh hardware output upon focus gain
                    if (engineController.playbackState.value == PlaybackStateEnum.PLAYING) {
                        serviceScope.launch {
                            delay(120)
                            engineController.refreshHardwareOutput()
                        }
                    }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnFocusGain = false
                engineController.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                hasAudioFocus = false
                val isCurrentlyPlaying = engineController.playbackState.value == PlaybackStateEnum.PLAYING
                val isCallActive = audioManager.mode != AudioManager.MODE_NORMAL

                if (engineController.playDuringCalls.value && isCallActive) {
                    // User explicitly chose to keep playing music during calls!
                    Log.i("PlaybackService", "Call active but playDuringCalls is enabled; continuing playback.")
                } else if (isCallActive || focusChange == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                    // Default behavior: Pause playback during calls or transient audio focus loss
                    if (isCurrentlyPlaying) {
                        resumeOnFocusGain = true
                        engineController.pause()
                    }
                } else {
                    // For short notifications (e.g. SMS chime, GPS alert) outside calls: duck volume
                    preDuckVolume = engineController.volume
                    engineController.setVolume(0.2f)
                }
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        if (hasAudioFocus) return true
        val audioAttributes = AudioAttributesCompat.Builder()
            .setUsage(AudioAttributesCompat.USAGE_MEDIA)
            .setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
            .build()

        val request = AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)
            .setAudioAttributes(audioAttributes)
            .setOnAudioFocusChangeListener(audioFocusListener)
            .build()
        audioFocusRequest = request

        val result = AudioManagerCompat.requestAudioFocus(audioManager, request)
        hasAudioFocus = (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        if (!hasAudioFocus && engineController.playDuringCalls.value) {
            // User requested to listen during calls / focus conflicts
            return true
        }
        return hasAudioFocus
    }

    private fun abandonAudioFocus() {
        if (hasAudioFocus) {
            audioFocusRequest?.let {
                AudioManagerCompat.abandonAudioFocusRequest(audioManager, it)
            }
            hasAudioFocus = false
            resumeOnFocusGain = false
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                Log.i("PlaybackService", "Headphones disconnected (becoming noisy). Pausing playback.")
                engineController.pause()
            }
        }
    }

    private fun registerNoisyReceiver() {
        if (!isNoisyReceiverRegistered) {
            try {
                registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
                isNoisyReceiverRegistered = true
            } catch (e: Exception) {
                Log.w("PlaybackService", "Error registering noisy receiver", e)
            }
        }
    }

    private fun unregisterNoisyReceiver() {
        if (isNoisyReceiverRegistered) {
            try {
                unregisterReceiver(becomingNoisyReceiver)
            } catch (e: Exception) {
                Log.w("PlaybackService", "Error unregistering noisy receiver", e)
            }
            isNoisyReceiverRegistered = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        try {
            engineController = AudioEngineController.getInstance(this)
            audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            createNotificationChannel()
            initMediaSession()
            sessionToken = mediaSession.sessionToken

            // Sync with current controller state
            val curTrack = engineController.currentTrack.value
            val isPlaying = engineController.playbackState.value == PlaybackStateEnum.PLAYING
            val curPos = engineController.currentPositionSec.value

            updateMediaSession(curTrack, isPlaying, curPos, curTrack?.durationSeconds ?: 1.0, null)

            val initialNotification = buildNotification(
                track = curTrack,
                isPlaying = isPlaying,
                positionSec = curPos,
                durationSec = curTrack?.durationSeconds ?: 1.0,
                artwork = null
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    initialNotification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, initialNotification)
            }

            observePlaybackState()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try {
                    audioManager.addOnModeChangedListener(mainExecutor) { mode ->
                        val isCall = mode != AudioManager.MODE_NORMAL
                        val isPlaying = engineController.playbackState.value == PlaybackStateEnum.PLAYING
                        if (isCall && isPlaying) {
                            if (engineController.playDuringCalls.value) {
                                Log.i("PlaybackService", "Audio mode changed to call mode ($mode), but playDuringCalls is enabled; continuing playback.")
                            } else {
                                Log.i("PlaybackService", "Audio mode changed to call mode ($mode). Pausing playback.")
                                resumeOnFocusGain = true
                                engineController.pause()
                            }
                        } else if (!isCall) {
                            if (resumeOnFocusGain) {
                                Log.i("PlaybackService", "Audio mode returned to MODE_NORMAL. Restoring playback.")
                                resumeOnFocusGain = false
                                serviceScope.launch {
                                    delay(120)
                                    engineController.refreshHardwareOutput()
                                    engineController.setVolume(preDuckVolume)
                                    engineController.play()
                                }
                            } else if (isPlaying) {
                                // Music was playing during call! Now that call ended, immediately refresh hardware output
                                // so the sample rate returns to 48kHz with ZERO cartoonish pitch distortion!
                                Log.i("PlaybackService", "Call ended while music was playing. Refreshing hardware output to restore 48kHz route.")
                                serviceScope.launch {
                                    delay(150)
                                    engineController.refreshHardwareOutput()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w("PlaybackService", "Unable to register mode changed listener", e)
                }
            }
        } catch (e: Throwable) {
            Log.e("PlaybackService", "Error during service onCreate", e)
            stopSelf()
        }
    }

    private fun initMediaSession() {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val mediaButtonIntent = Intent(Intent.ACTION_MEDIA_BUTTON).apply {
            setClass(this@PlaybackService, MediaButtonReceiver::class.java)
        }
        val mediaButtonPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            mediaButtonIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSessionCompat(this, "VeylMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setPlaybackToLocal(AudioManager.STREAM_MUSIC)
            setSessionActivity(openAppIntent)
            setMediaButtonReceiver(mediaButtonPendingIntent)

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
                    if (requestAudioFocus()) {
                        engineController.play()
                    } else {
                        Log.i("PlaybackService", "Play request ignored because audio focus was denied")
                    }
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    if (requestAudioFocus()) {
                        engineController.play()
                    } else {
                        Log.i("PlaybackService", "Play request ignored because audio focus was denied")
                    }
                }

                override fun onPause() {
                    engineController.pause()
                }

                override fun onSkipToNext() {
                    engineController.skipNext()
                }

                override fun onSkipToPrevious() {
                    engineController.skipPrevious()
                }

                override fun onSeekTo(pos: Long) {
                    engineController.seekTo((pos / 1000.0).coerceAtLeast(0.0))
                }

                override fun onStop() {
                    engineController.stop()
                    stopSelf()
                }
            })

            isActive = true
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(NotificationManager::class.java)
            try {
                notificationManager?.deleteNotificationChannel("veyl_playback_channel")
                notificationManager?.deleteNotificationChannel("veyl_playback_channel_v2")
                notificationManager?.deleteNotificationChannel("veyl_playback_channel_v3")
                notificationManager?.deleteNotificationChannel("veyl_playback_channel_v4")
                notificationManager?.deleteNotificationChannel("veyl_playback_channel_v5")
                notificationManager?.deleteNotificationChannel("veyl_playback_channel_v6")
                notificationManager?.deleteNotificationChannel("veyl_playback_channel_v7")
                notificationManager?.deleteNotificationChannel("veyl_playback_channel_v8")
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Veyl Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active music playback controls and media notifications"
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            combine(
                engineController.currentTrack,
                engineController.playbackState,
                engineController.currentPositionSec
            ) { track, state, posSec ->
                Triple(track, state, posSec)
            }.collect { (track, state, posSec) ->
                val isPlaying = state == PlaybackStateEnum.PLAYING
                val isTrackChange = track?.uri != lastTrackUri
                val isStateChange = isPlaying != lastPostedIsPlaying
                val isMajorSeek = kotlin.math.abs(posSec - lastMediaSessionPosSec) > 3.0

                // Manage audio focus and noisy receiver
                if (isStateChange) {
                    if (isPlaying) {
                        if (audioManager.mode != AudioManager.MODE_NORMAL && !engineController.playDuringCalls.value) {
                            Log.w("PlaybackService", "Playback attempted while call mode active (${audioManager.mode}). Pausing.")
                            resumeOnFocusGain = true
                            engineController.pause()
                            return@collect
                        }
                        if (requestAudioFocus()) {
                            registerNoisyReceiver()
                        } else {
                            Log.w("PlaybackService", "Pausing because Android denied audio focus")
                            engineController.pause()
                        }
                    } else {
                        unregisterNoisyReceiver()
                    }
                }

                // Asynchronously load artwork on track change
                if (isTrackChange) {
                    lastTrackUri = track?.uri
                    cachedArtwork = null
                    artworkJob?.cancel()
                    if (track?.uri != null) {
                        artworkJob = serviceScope.launch(Dispatchers.IO) {
                            val art = ArtworkCache.loadArtwork(track.uri, 512)
                            withContext(Dispatchers.Main) {
                                cachedArtwork = art
                                if (lastTrackUri == track.uri) {
                                    val nowPlaying = engineController.playbackState.value == PlaybackStateEnum.PLAYING
                                    val nowPos = engineController.currentPositionSec.value
                                    updateMediaSession(track, nowPlaying, nowPos, track.durationSeconds, art)
                                    postNotification(track, nowPlaying, nowPos, track.durationSeconds, art)
                                }
                            }
                        }
                    }
                }

                if (isTrackChange || isStateChange || isMajorSeek) {
                    lastMediaSessionPosSec = posSec
                    lastPostedIsPlaying = isPlaying
                    updateMediaSession(track, isPlaying, posSec, track?.durationSeconds ?: 1.0, cachedArtwork)
                    postNotification(track, isPlaying, posSec, track?.durationSeconds ?: 1.0, cachedArtwork)
                }
            }
        }
    }

    private fun postNotification(
        track: TrackInfo?,
        isPlaying: Boolean,
        positionSec: Double,
        durationSec: Double,
        artwork: Bitmap?
    ) {
        if (track == null && !isPlaying) {
            // If the service just started and hasn't loaded a track yet, do not cancel immediately
            if (lastTrackUri == null) {
                return
            }
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.cancel(NOTIFICATION_ID)
            } catch (e: Exception) {
                Log.w("PlaybackService", "Error stopping foreground for null track", e)
            }
            return
        }

        val notification = buildNotification(track, isPlaying, positionSec, durationSec, artwork)
        try {
            if (isPlaying) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceCompat.startForeground(
                        this,
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
            } else {
                // When paused: detach from ongoing foreground requirement so notification is dismissible/swipeable,
                // but keep media controls visible in the notification shade & lockscreen
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("PlaybackService", "Error posting notification", e)
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.notify(NOTIFICATION_ID, notification)
        }
    }

    private fun updateMediaSession(
        track: TrackInfo?,
        isPlaying: Boolean,
        positionSec: Double,
        durationSec: Double,
        artwork: Bitmap?
    ) {
        if (track == null && !isPlaying) {
            val stateBuilder = PlaybackStateCompat.Builder()
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or
                    PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                )
                .setState(PlaybackStateCompat.STATE_NONE, 0L, 0f)
            mediaSession.setPlaybackState(stateBuilder.build())
            return
        }

        val playbackState = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP or
                PlaybackStateCompat.ACTION_SET_PLAYBACK_SPEED

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                playbackState,
                (positionSec * 1000).toLong().coerceAtLeast(0L),
                if (isPlaying) 1.0f else 0.0f,
                android.os.SystemClock.elapsedRealtime()
            )

        mediaSession.setPlaybackState(stateBuilder.build())

        val title = track?.title?.takeIf { it.isNotBlank() } ?: "Veyl"
        val artist = track?.artist?.takeIf { it.isNotBlank() } ?: "Lossless Audio"
        val album = track?.album?.takeIf { it.isNotBlank() } ?: "Master Recording"
        val formatSpec = track?.let { "${it.formatName} ${it.sampleRate / 1000u}kHz" } ?: "Hi-Res Master"

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, album)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
            .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_DESCRIPTION, formatSpec)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (durationSec * 1000).toLong().coerceAtLeast(1000L))

        if (artwork != null && !artwork.isRecycled) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, artwork)
        }

        mediaSession.setMetadata(metadataBuilder.build())
        mediaSession.isActive = true
    }

    private fun getServicePendingIntent(actionStr: String, requestCode: Int): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply {
            action = actionStr
        }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PendingIntent.getForegroundService(this, requestCode, intent, flags)
        } else {
            PendingIntent.getService(this, requestCode, intent, flags)
        }
    }

    private fun buildNotification(
        track: TrackInfo?,
        isPlaying: Boolean,
        positionSec: Double,
        durationSec: Double,
        artwork: Bitmap?
    ): Notification {
        val openAppIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = getServicePendingIntent(ACTION_PREV, 101)
        val playPauseIntent = getServicePendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY, 102)
        val nextIntent = getServicePendingIntent(ACTION_NEXT, 103)
        val stopIntent = getServicePendingIntent(ACTION_STOP, 104)

        val title = track?.title?.takeIf { it.isNotBlank() } ?: "Veyl"
        val cleanFormat = when {
            track?.formatName?.contains("8192") == true -> "FLAC Lossless"
            track?.formatName?.startsWith("CodecType") == true -> "Hi-Res Master"
            !track?.formatName.isNullOrBlank() -> track.formatName
            else -> "Lossless"
        }
        val artistStr = track?.artist?.takeIf { it.isNotBlank() } ?: "Veyl Lossless Audio"
        val formatDesc = if (!track?.artist.isNullOrBlank()) {
            "$artistStr • $cleanFormat"
        } else {
            cleanFormat
        }

        val mediaStyle = MediaStyle()
            .setMediaSession(mediaSession.sessionToken)
            .setShowActionsInCompactView(0, 1, 2)
            .setShowCancelButton(true)
            .setCancelButtonIntent(stopIntent)

        val prevAction = NotificationCompat.Action(
            android.R.drawable.ic_media_previous,
            "Previous",
            prevIntent
        )
        val playAction = NotificationCompat.Action(
            if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
            if (isPlaying) "Pause" else "Play",
            playPauseIntent
        )
        val nextAction = NotificationCompat.Action(
            android.R.drawable.ic_media_next,
            "Next",
            nextIntent
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(formatDesc)
            .setSubText(track?.album ?: "Veyl")
            .setSmallIcon(R.drawable.ic_veyl_notification)
            .setLargeIcon(artwork)
            .setContentIntent(openAppIntent)
            .setDeleteIntent(stopIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setStyle(mediaStyle)
            .addAction(prevAction)
            .addAction(playAction)
            .addAction(nextAction)
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_SERVICE -> {
                // Keep foreground service active and sync notification
            }
            ACTION_PLAY -> {
                if (requestAudioFocus()) {
                    engineController.play()
                }
            }
            ACTION_PAUSE -> {
                engineController.pause()
            }
            ACTION_TOGGLE_PAUSE -> {
                engineController.togglePlayPause()
            }
            ACTION_NEXT -> {
                engineController.skipNext()
            }
            ACTION_PREV -> {
                engineController.skipPrevious()
            }
            ACTION_STOP -> {
                engineController.stop()
                try {
                    abandonAudioFocus()
                    unregisterNoisyReceiver()
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        @Suppress("DEPRECATION")
                        stopForeground(true)
                    }
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                    notificationManager?.cancel(NOTIFICATION_ID)
                    stopSelf()
                } catch (e: Exception) {
                    Log.e("PlaybackService", "Error stopping service via ACTION_STOP", e)
                }
                return START_NOT_STICKY
            }
            Intent.ACTION_MEDIA_BUTTON -> {
                if (::mediaSession.isInitialized) {
                    MediaButtonReceiver.handleIntent(mediaSession, intent)
                }
            }
        }

        try {
            val curTrack = if (::engineController.isInitialized) engineController.currentTrack.value else null
            val isPlaying = if (::engineController.isInitialized) engineController.playbackState.value == PlaybackStateEnum.PLAYING else false
            val curPos = if (::engineController.isInitialized) engineController.currentPositionSec.value else 0.0

            val notification = buildNotification(
                track = curTrack,
                isPlaying = isPlaying,
                positionSec = curPos,
                durationSec = curTrack?.durationSeconds ?: 1.0,
                artwork = cachedArtwork
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(
                    this,
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("PlaybackService", "Error calling startForeground in onStartCommand", e)
        }

        return START_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        return BrowserRoot("veyl_media_root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<MutableList<MediaBrowserCompat.MediaItem>>
    ) {
        val curTrack = engineController.currentTrack.value
        if (curTrack != null) {
            val description = MediaDescriptionCompat.Builder()
                .setMediaId(curTrack.uri)
                .setTitle(curTrack.title)
                .setSubtitle(curTrack.artist ?: "Veyl Audiophile")
                .setDescription(curTrack.album ?: "Master Recording")
                .build()
            val item = MediaBrowserCompat.MediaItem(
                description,
                MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            )
            result.sendResult(mutableListOf(item))
        } else {
            result.sendResult(mutableListOf())
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val isPlaying = engineController.playbackState.value == PlaybackStateEnum.PLAYING
        if (!isPlaying) {
            Log.i("PlaybackService", "App swiped away and not playing, stopping background service.")
            try {
                abandonAudioFocus()
                unregisterNoisyReceiver()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                notificationManager?.cancel(NOTIFICATION_ID)
                stopSelf()
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error in onTaskRemoved", e)
            }
        } else {
            Log.i("PlaybackService", "App swiped away while playing; keeping audio service active in foreground.")
        }
    }

    override fun onDestroy() {
        artworkJob?.cancel()
        serviceJob.cancel()
        unregisterNoisyReceiver()
        abandonAudioFocus()
        if (::mediaSession.isInitialized) {
            try {
                mediaSession.isActive = false
                mediaSession.release()
            } catch (e: Exception) {
                Log.w("PlaybackService", "Error releasing mediaSession", e)
            }
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "veyl_media_playback_channel_v9"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START_SERVICE = "com.audiophile.player.ACTION_START_SERVICE"
        const val ACTION_PLAY = "com.audiophile.player.ACTION_PLAY"
        const val ACTION_PAUSE = "com.audiophile.player.ACTION_PAUSE"
        const val ACTION_TOGGLE_PAUSE = "com.audiophile.player.ACTION_TOGGLE_PAUSE"
        const val ACTION_NEXT = "com.audiophile.player.ACTION_NEXT"
        const val ACTION_PREV = "com.audiophile.player.ACTION_PREV"
        const val ACTION_STOP = "com.audiophile.player.ACTION_STOP"
    }
}
