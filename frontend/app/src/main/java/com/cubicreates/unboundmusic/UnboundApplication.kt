/*
 * Package: com.cubicreates.unboundmusic
 * File: UnboundApplication.kt
 * Purpose: Application Entry Point initializing embedded Go engine daemon and high-performance Coil cache.
 * Subsystem: Application Lifecycle / Image Engine
 */

package com.cubicreates.unboundmusic

import android.app.Application
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.cubicreates.unboundmusic.daemon.DaemonManager

class UnboundApplication : Application() {

    companion object {
        private const val TAG = "UnboundApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing Unbound Music Production Application...")

        // Configure high-performance image caching to eliminate UI lag & stutter
        val imageLoader = ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(50L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()

        Coil.setImageLoader(imageLoader)

        // Boot Go Engine Daemon
        DaemonManager.getInstance(this).startDaemonAuto()
    }
}
