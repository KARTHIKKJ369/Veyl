package com.audiophile.player

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
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

import android.os.Environment
import android.provider.Settings

class MainActivity : ComponentActivity() {

    private lateinit var controller: AudioEngineController

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        checkStorageAccessAndScan()
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
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
        startPlaybackService()
        checkPermissions()
        handleIncomingIntent(intent)

        setContent {
            val isDarkMode by controller.isDarkMode.collectAsState()
            VeylTheme(isDarkMode = isDarkMode) {
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
            checkStorageAccessAndScan()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (intent.action == Intent.ACTION_VIEW) {
            val path = uri.path ?: uri.toString()
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

    private fun startPlaybackService() {
        try {
            val serviceIntent = Intent(this, PlaybackService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent)
            } else {
                startService(serviceIntent)
            }
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "Failed to start PlaybackService", e)
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                manageStorageLauncher.launch(intent)
            } catch (e: Exception) {
                val fallbackIntent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                manageStorageLauncher.launch(fallbackIntent)
            }
        }
    }

    private fun checkStorageAccessAndScan() {
        controller.selectedRootPath.value?.let { path ->
            controller.scanDirectory(path)
        }
    }
}
