package com.audiophile.player.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uniffi.audiophile_core.PlaybackStateEnum
import uniffi.audiophile_core.TrackInfo

class PlaybackService : MediaBrowserServiceCompat() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private lateinit var engineController: AudioEngineController
    private lateinit var mediaSession: MediaSessionCompat

    private var lastTrackUri: String? = null
    private var cachedArtwork: Bitmap? = null

    override fun onCreate() {
        super.onCreate()
        try {
            engineController = AudioEngineController.getInstance(this)
            createNotificationChannel()
            initMediaSession()
            sessionToken = mediaSession.sessionToken

            val initialNotification = buildNotification(
                track = null,
                isPlaying = false,
                positionSec = 0.0,
                durationSec = 1.0,
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
        mediaSession = MediaSessionCompat(this, "VeylMediaSession").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )

            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() {
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
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Veyl Audio Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active music playback controls and media notifications"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    private fun observePlaybackState() {
        serviceScope.launch {
            engineController.status.collectLatest { status ->
                val track = status?.currentTrack
                val isPlaying = status?.state == PlaybackStateEnum.PLAYING
                val positionSec = status?.positionSeconds ?: 0.0
                val durationSec = (status?.durationSeconds ?: 1.0).coerceAtLeast(1.0)

                // Load artwork asynchronously if track changed
                if (track?.uri != lastTrackUri) {
                    lastTrackUri = track?.uri
                    cachedArtwork = if (track?.uri != null) {
                        withContext(Dispatchers.IO) {
                            ArtworkCache.loadArtwork(track.uri, 384)
                        }
                    } else null
                }

                // Update Android system MediaSession state & metadata
                updateMediaSession(track, isPlaying, positionSec, durationSec, cachedArtwork)

                // Update Foreground Notification with MediaStyle
                val notification = buildNotification(track, isPlaying, positionSec, durationSec, cachedArtwork)
                val notificationManager = getSystemService(NotificationManager::class.java)
                notificationManager?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun updateMediaSession(
        track: TrackInfo?,
        isPlaying: Boolean,
        positionSec: Double,
        durationSec: Double,
        artwork: Bitmap?
    ) {
        val playbackState = if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        val actions = PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_PLAY_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO or
                PlaybackStateCompat.ACTION_STOP

        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(actions)
            .setState(
                playbackState,
                (positionSec * 1000).toLong().coerceAtLeast(0L),
                if (isPlaying) 1.0f else 0.0f
            )

        mediaSession.setPlaybackState(stateBuilder.build())

        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track?.title ?: "Veyl")
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track?.artist ?: "Veyl Audiophile")
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track?.album ?: "")
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, (durationSec * 1000).toLong())

        if (artwork != null) {
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, artwork)
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ART, artwork)
        }

        mediaSession.setMetadata(metadataBuilder.build())
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

        val formatDesc = track?.let {
            "${it.artist} • ${it.formatName} ${it.sampleRate / 1000u}kHz"
        } ?: "Bit-Perfect Audio Engine"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track?.title ?: "Veyl Music Player")
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
        result.sendResult(mutableListOf())
    }

    override fun onDestroy() {
        try {
            mediaSession.isActive = false
            mediaSession.release()
        } catch (e: Exception) {
            Log.w("PlaybackService", "Error releasing mediaSession", e)
        }
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "veyl_playback_channel"
        const val NOTIFICATION_ID = 1001
    }
}
