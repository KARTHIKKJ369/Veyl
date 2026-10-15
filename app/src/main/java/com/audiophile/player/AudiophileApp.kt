package com.audiophile.player

import android.app.Application
import android.util.Log
import com.audiophile.player.engine.ArtworkCache

class AudiophileApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            System.loadLibrary("c++_shared")
            System.loadLibrary("audiophile_core")
            ArtworkCache.init(this)
            Log.i("AudiophileApp", "Native Rust library 'audiophile_core' & 'c++_shared' loaded successfully.")
        } catch (e: Throwable) {
            Log.e("AudiophileApp", "Native library load error: ${e.message}", e)
        }
    }
}
