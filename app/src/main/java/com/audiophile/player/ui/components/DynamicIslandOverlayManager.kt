package com.audiophile.player.ui.components

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
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
 * Optional floating pill for devices that lack native HyperOS Super Island.
 * Controlled by `screenOverlayIslandEnabled` in AudioEngineController (default false).
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
                controller.screenOverlayIslandEnabled,
                controller.currentTrack,
                controller.playbackState
            ) { overlayEnabled, track, _ ->
                Triple(overlayEnabled, track != null, Settings.canDrawOverlays(context))
            }.collect { (overlayEnabled, hasTrack, canDraw) ->
                evaluateOverlay(overlayEnabled, hasTrack, canDraw)
            }
        }
    }

    fun setAppForeground(foreground: Boolean) {
        isAppForeground = foreground
        val controller = engineController ?: return
        val context = applicationContext ?: return
        val canDraw = Settings.canDrawOverlays(context)
        val overlayEnabled = controller.screenOverlayIslandEnabled.value
        val hasTrack = controller.currentTrack.value != null

        evaluateOverlay(overlayEnabled, hasTrack, canDraw)
    }

    private fun evaluateOverlay(enabled: Boolean, hasTrack: Boolean, canDrawOverlays: Boolean) {
        // Show overlay only when app is minimized, music is active, manual overlay is enabled, and permission granted
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
        val density = context.resources.displayMetrics.density

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
                                val intent = Intent(context, MainActivity::class.java).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    putExtra("NAVIGATE_TO_NOW_PLAYING", true)
                                }
                                context.startActivity(intent)
                            },
                            onExpandChanged = { expanded ->
                                updateWindowSize(expanded)
                            }
                        )
                    }
                }
            }

            val initialWidth = (136 * density).toInt()
            val initialHeight = (44 * density).toInt()

            val params = WindowManager.LayoutParams(
                initialWidth,
                initialHeight,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
                x = 0
                y = (4 * density).toInt()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                }
            }

            wm.addView(view, params)
            overlayView = view
            isOverlayShowing = true
        } catch (e: Exception) {
            Log.e("DynamicIslandOverlay", "Error displaying overlay window", e)
            isOverlayShowing = false
        }
    }

    private fun updateWindowSize(expanded: Boolean) {
        val wm = windowManager ?: return
        val view = overlayView ?: return
        val context = applicationContext ?: return
        val density = context.resources.displayMetrics.density

        val params = (view.layoutParams as? WindowManager.LayoutParams) ?: return
        if (expanded) {
            params.width = (356 * density).toInt()
            params.height = (156 * density).toInt()
        } else {
            params.width = (136 * density).toInt()
            params.height = (44 * density).toInt()
        }
        try {
            wm.updateViewLayout(view, params)
        } catch (e: Exception) {
            Log.w("DynamicIslandOverlay", "Error resizing overlay window", e)
        }
    }

    fun hide() {
        val wm = windowManager
        val view = overlayView
        val owner = lifecycleOwner

        if (wm != null && view != null && isOverlayShowing) {
            try {
                owner?.onDestroy()
                wm.removeView(view)
            } catch (e: Exception) {
                Log.w("DynamicIslandOverlay", "Error removing overlay view", e)
            }
        }
        overlayView = null
        lifecycleOwner = null
        isOverlayShowing = false
    }

    fun destroy() {
        monitorJob?.cancel()
        hide()
        applicationContext = null
        engineController = null
        windowManager = null
    }

    private class OverlayLifecycleOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
        private val lifecycleRegistry = LifecycleRegistry(this)
        private val savedStateRegistryController = SavedStateRegistryController.create(this)
        private val store = ViewModelStore()

        init {
            savedStateRegistryController.performRestore(null)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        }

        fun onDestroy() {
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
            store.clear()
        }

        override val lifecycle: Lifecycle get() = lifecycleRegistry
        override val savedStateRegistry: SavedStateRegistry get() = savedStateRegistryController.savedStateRegistry
        override val viewModelStore: ViewModelStore get() = store
    }
}
