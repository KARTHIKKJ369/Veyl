package com.audiophile.player.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.audiophile.player.MainActivity
import com.audiophile.player.engine.AudioEngineController
import com.audiophile.player.ui.theme.VeylTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * System-Level Camera Cutout Dynamic Island Overlay Manager.
 *
 * Automatically attaches an interactive floating pill to the WindowManager directly
 * at the device camera cutout when the app is minimized / in background.
 *
 * Utilizes TYPE_APPLICATION_OVERLAY + LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS.
 */
object DynamicIslandOverlayManager {

    private var applicationContext: Context? = null
    private var engineController: AudioEngineController? = null
    private var windowManager: WindowManager? = null

    private var overlayView: ComposeView? = null
    private var lifecycleOwner: OverlayLifecycleOwner? = null

    private var isAppForeground: Boolean = true
    private var isOverlayShowing: Boolean = false
    private var monitorJob: Job? = null

    fun initialize(context: Context, controller: AudioEngineController) {
        applicationContext = context.applicationContext
        engineController = controller
        windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

        startMonitoring()
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        val controller = engineController ?: return
        val context = applicationContext ?: return

        monitorJob = CoroutineScope(Dispatchers.Main).launch {
            combine(
                controller.dynamicIslandEnabled,
                controller.currentTrack,
                controller.playbackState
            ) { enabled, track, _ ->
                Triple(enabled, track != null, Settings.canDrawOverlays(context))
            }.collect { (enabled, hasTrack, canDraw) ->
                evaluateOverlay(enabled, hasTrack, canDraw)
            }
        }
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        val controller = engineController ?: return
        val context = applicationContext ?: return
        val canDraw = Settings.canDrawOverlays(context)
        val enabled = controller.dynamicIslandEnabled.value
        val hasTrack = controller.currentTrack.value != null

        evaluateOverlay(enabled, hasTrack, canDraw)
    }

    private fun evaluateOverlay(enabled: Boolean, hasTrack: Boolean, canDrawOverlays: Boolean) {
        // Show overlay only when app is minimized, music is active, feature is enabled, and permission is granted
        val shouldShow = !isAppForeground && enabled && hasTrack && canDrawOverlays

        if (shouldShow && !isOverlayShowing) {
            show()
        } else if (!shouldShow && isOverlayShowing) {
            hide()
        }
    }

    private fun show() {
        val context = applicationContext ?: return
        val wm = windowManager ?: return
        val controller = engineController ?: return

        try {
            val owner = OverlayLifecycleOwner()
            lifecycleOwner = owner

            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(owner)
                setViewTreeSavedStateRegistryOwner(owner)
                setViewTreeViewModelStoreOwner(owner)

                setContent {
                    val isDarkMode by controller.isDarkMode.collectAsState()
                    val colorScheme by controller.currentVeylColorScheme.collectAsState()

                    VeylTheme(isDarkMode = isDarkMode, colorScheme = colorScheme) {
                        VeylDynamicIsland(
                            controller = controller,
                            onOpenNowPlaying = {
                                // Bring Veyl to foreground and open Now Playing
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("NAVIGATE_TO_NOW_PLAYING", true)
                                }
                                context.startActivity(intent)
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }

            wm.addView(view, params)
            overlayView = view
            isOverlayShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
            isOverlayShowing = false
        }
    }

    private fun hide() {
        val wm = windowManager
        val view = overlayView
        val owner = lifecycleOwner

        try {
            if (view != null && wm != null) {
                wm.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            owner?.destroy()
            overlayView = null
            lifecycleOwner = null
            isOverlayShowing = false
        }
    }
}

/**
 * Dedicated LifecycleOwner, SavedStateRegistryOwner, and ViewModelStoreOwner
 * for running Jetpack Compose smoothly within WindowManager overlays.
 */
class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateRegistryController = SavedStateRegistryController.create(this)
    private val _viewModelStore = ViewModelStore()

    init {
        savedStateRegistryController.performAttach()
        savedStateRegistryController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
    override val viewModelStore: ViewModelStore get() = _viewModelStore

    fun destroy() {
        try {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            _viewModelStore.clear()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
