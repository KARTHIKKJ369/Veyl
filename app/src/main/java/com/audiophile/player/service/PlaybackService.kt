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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.audiophile_core.PlaybackStateEnum
import uniffi.audiophile_core.TrackInfo

class PlaybackService : MediaBrowserServiceCompat() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var engineController: AudioEngineController
    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager

    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var hasAudioFocus = false
    private var resumeOnFocusGain = false
    private var isNoisyReceiverRegistered = false

    private var lastTrackUri: String? = null
    private var cachedArtwork: Bitmap? = null
    private var artworkJob: Job? = null

    private var lastPostedIsPlaying: Boolean? = null
    private var lastMediaSessionPosSec: Double = -1.0

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                hasAudioFocus = true
                engineController.setVolume(1.0f)
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    engineController.play()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                hasAudioFocus = false
                resumeOnFocusGain = false
                engineController.pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                hasAudioFocus = false
                val isCurrentlyPlaying = engineController.playbackState.value == PlaybackStateEnum.PLAYING
                if (isCurrentlyPlaying) {
                    resumeOnFocusGain = true
                    engineController.pause()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                engineController.setVolume(0.2f)
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
        } catch (e: Exception) {
            Log.e("PlaybackService", "Error during service onCreate", e)
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
                    requestAudioFocus()
                    engineController.play()
                }

                override fun onPlayFromMediaId(mediaId: String?, extras: Bundle?) {
                    requestAudioFocus()
                    engineController.play()
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
            } catch (_: Exception) {}

            val channel = NotificationChannel(
                CHANNEL_ID,
                "Veyl Audio Playback",
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
                        requestAudioFocus()
                        registerNoisyReceiver()
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
        val notification = buildNotification(track, isPlaying, positionSec, durationSec, artwork)
        if (isPlaying) {
            try {
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
                Log.e("PlaybackService", "Error calling startForeground", e)
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        } else {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_DETACH)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(false)
                }
            } catch (e: Exception) {
                Log.w("PlaybackService", "Error calling stopForeground", e)
            }
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

        val prevIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
        )
        val playPauseAction = if (isPlaying) PlaybackStateCompat.ACTION_PAUSE else PlaybackStateCompat.ACTION_PLAY
        val playPauseIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, playPauseAction
        )
        val nextIntent = MediaButtonReceiver.buildMediaButtonPendingIntent(
            this, PlaybackStateCompat.ACTION_SKIP_TO_NEXT
        )

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

        val title = track?.title?.takeIf { it.isNotBlank() } ?: "Veyl"
        val formatDesc = track?.let {
            "${it.artist} • ${it.formatName} ${it.sampleRate / 1000u}kHz"
        } ?: "Bit-Perfect Audio Engine"

        val oemExtras = Bundle().apply {
            putString("android.media.session.tag", "VeylMediaSession")
            putBoolean("android.support.action.showsUserInterface", true)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(formatDesc)
            .setSubText(track?.album ?: "Veyl")
            .setSmallIcon(R.drawable.ic_veyl_notification)
            .setLargeIcon(artwork)
            .setContentIntent(openAppIntent)
            .setDeleteIntent(
                MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)
            )
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .setOngoing(isPlaying)
            .setColorized(true)
            .addExtras(oemExtras)
            .addAction(prevAction)
            .addAction(playAction)
            .addAction(nextAction)
            .setStyle(
                MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
                    .setShowCancelButton(true)
                    .setCancelButtonIntent(
                        MediaButtonReceiver.buildMediaButtonPendingIntent(this, PlaybackStateCompat.ACTION_STOP)
                    )
            )
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        MediaButtonReceiver.handleIntent(mediaSession, intent)
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
        unregisterNoisyReceiver()
        abandonAudioFocus()
        try {
            mediaSession.isActive = false
            mediaSession.release()
        } catch (e: Exception) {
            Log.w("PlaybackService", "Error releasing mediaSession", e)
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "veyl_playback_channel_v4"
        const val NOTIFICATION_ID = 1001
    }
}
