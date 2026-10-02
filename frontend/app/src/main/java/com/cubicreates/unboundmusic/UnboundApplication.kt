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
import kotlinx.coroutines.launch

class UnboundApplication : Application() {

    companion object {
        private const val TAG = "UnboundApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing Unbound Music Production Application...")

        // Configure high-performance, low-RAM image caching to eliminate UI lag & stutter
        val imageLoader = ImageLoader.Builder(this)
            .bitmapConfig(android.graphics.Bitmap.Config.RGB_565)
            .allowRgb565(true)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.10)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(256L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .respectCacheHeaders(false)
            .build()

        Coil.setImageLoader(imageLoader)

        // Configure global appContext for BackendClient telemetry & in-app alerting
        com.cubicreates.unboundmusic.data.BackendClient.appContext = this

        // Automatically deploy canonical Unbound folder in background coroutine to eliminate cold-start I/O stalls
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            com.cubicreates.unboundmusic.service.UnboundStorageManager.deployUnboundStorage(this@UnboundApplication)
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            Coil.imageLoader(this).memoryCache?.clear()
            DaemonManager.getInstance(this).trimMemory()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Coil.imageLoader(this).memoryCache?.clear()
        DaemonManager.getInstance(this).trimMemory()
    }
}
