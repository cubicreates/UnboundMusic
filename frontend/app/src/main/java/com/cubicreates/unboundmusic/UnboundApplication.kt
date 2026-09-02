/*
 * Package: com.cubicreates.unboundmusic
 * File: UnboundApplication.kt
 * Purpose: Application Entry Point initializing embedded Go engine daemon on app process launch.
 * Subsystem: Application Lifecycle
 */

package com.cubicreates.unboundmusic

import android.app.Application
import android.util.Log
import com.cubicreates.unboundmusic.daemon.DaemonManager

class UnboundApplication : Application() {

    companion object {
        private const val TAG = "UnboundApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing Unbound Music Production Application...")
        DaemonManager.getInstance(this).startDaemonAuto()
    }
}
