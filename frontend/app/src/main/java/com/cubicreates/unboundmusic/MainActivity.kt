/*
 * Package: com.cubicreates.unboundmusic
 * File: MainActivity.kt
 * Purpose: Main entry Activity for Unbound Music. Initializes edge-to-edge display,
 *          connects the Media3 playback service, and hosts the Compose UI.
 * Subsystem: Application Entry
 */

package com.cubicreates.unboundmusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.service.ServiceConnection
import com.cubicreates.unboundmusic.ui.MainApp
import com.cubicreates.unboundmusic.ui.theme.UnboundMusicTheme

/**
 * Primary Activity hosting the Unbound Music Compose UI.
 * Connects to the Media3 foreground playback service on creation
 * and handles runtime permission requests for notifications and storage.
 */
class MainActivity : ComponentActivity() {

    private lateinit var serviceConnection: ServiceConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Connect Media3 playback service
        serviceConnection = ServiceConnection.getInstance(this)
        serviceConnection.connect()

        // Request notification permission for Android 13+ (Tiramisu)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    1001
                )
            }
        }

        setContent {
            UnboundMusicTheme {
                MainApp()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-trigger daemon start if permissions were just granted
        DaemonManager.getInstance(this).startDaemonAuto(force = false)
    }
}
