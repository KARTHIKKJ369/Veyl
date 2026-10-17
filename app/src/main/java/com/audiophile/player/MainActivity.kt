package com.audiophile.player

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.service.PlaybackService
import com.audiophile.player.ui.navigation.AudiophileNavHost
import com.audiophile.player.ui.theme.VeylTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue


class MainActivity : ComponentActivity() {

    private lateinit var controller: AudioEngineController
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isServiceBound = true
            Log.i("MainActivity", "Connected to PlaybackService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            Log.i("MainActivity", "Disconnected from PlaybackService")
        }
    }

    private fun bindPlaybackService() {
        try {
            val serviceIntent = Intent(this, PlaybackService::class.java)
            bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.w("MainActivity", "Failed to bind PlaybackService", e)
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkStorageAccessAndScan()
    }

    private val folderPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if permission cannot be persisted
            }
            controller.scanFromTreeUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = androidx.activity.SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        controller = AudioEngineController.getInstance(this)
        bindPlaybackService()
        checkPermissions()
        handleIncomingIntent(intent)

        setContent {
            val isDarkMode by controller.isDarkMode.collectAsState()
            val colorScheme by controller.currentVeylColorScheme.collectAsState()
            VeylTheme(isDarkMode = isDarkMode, colorScheme = colorScheme) {
                AudiophileNavHost(
                    controller = controller,
                    onSelectRootFolder = {
                        folderPickerLauncher.launch(null)
                    }
                )
            }
        }
    }

    private var hasInitialScanRun = false

    override fun onResume() {
        super.onResume()
        if (!hasInitialScanRun) {
            hasInitialScanRun = true
            if (controller.libraryTracks.value.isEmpty()) {
                checkStorageAccessAndScan()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("NAVIGATE_TO_NOW_PLAYING", false) == true) {
            controller.requestOpenNowPlaying()
        }
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) {
            val path = if (uri.scheme == "file") {
                uri.path ?: uri.toString()
            } else if (uri.scheme == "content") {
                var resolvedPath: String? = null
                try {
                    val proj = arrayOf(android.provider.MediaStore.Audio.Media.DATA)
                    contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Audio.Media.DATA)
                            resolvedPath = cursor.getString(idx)
                        }
                    }
                } catch (_: Exception) {}
                resolvedPath ?: uri.path ?: uri.toString()
            } else {
                uri.path ?: uri.toString()
            }

            val track = uniffi.audiophile_core.TrackInfo(
                uri = path,
                title = uri.lastPathSegment ?: "Audio Track",
                artist = "Audiophile",
                album = "Direct Stream",
                albumArtist = null,
                genre = null,
                year = null,
                trackNumber = null,
                discNumber = null,
                durationSeconds = 0.0,
                sampleRate = 48000u,
                bitDepth = 24u,
                bitrate = null,
                channels = 2u,
                formatName = "PCM/FLAC",
                hasArtwork = false
            )
            controller.playTrack(track)
        }
    }

    private fun checkPermissions() {
        val permissions = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val needed = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        }

    }

    private fun checkStorageAccessAndScan() {
        controller.rescanLibrary()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
                isServiceBound = false
            } catch (e: Exception) {
                Log.w("MainActivity", "Error unbinding PlaybackService", e)
            }
        }
    }
}
