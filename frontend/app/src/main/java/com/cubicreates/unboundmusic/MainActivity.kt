/*
 * Package: com.cubicreates.unboundmusic
 * File: MainActivity.kt
 * Purpose: Main entry Activity for Unbound Music. Hosts the Studio-Mode technical brutalist
 *          SplashScreen during engine hydration and transitions to MainApp once ready.
 * Subsystem: Application Entry / Lifecycle Gatekeeper
 */

package com.cubicreates.unboundmusic

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.service.ServiceConnection
import com.cubicreates.unboundmusic.ui.MainApp
import com.cubicreates.unboundmusic.ui.splash.SplashScreen
import com.cubicreates.unboundmusic.ui.theme.UnboundMusicTheme
import com.cubicreates.unboundmusic.viewmodel.MainViewModel

/**
 * Primary Activity hosting Unbound Music with animated startup gatekeeper.
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
                val mainViewModel: MainViewModel = viewModel()
                val isAppReady by mainViewModel.isAppReady.collectAsStateWithLifecycle()
                val startupPhase by mainViewModel.startupPhase.collectAsStateWithLifecycle()
                val startupProgress by mainViewModel.startupProgress.collectAsStateWithLifecycle()

                Crossfade(
                    targetState = isAppReady,
                    animationSpec = tween(durationMillis = 350),
                    label = "splash_crossfade"
                ) { ready ->
                    if (!ready) {
                        SplashScreen(
                            statusText = startupPhase,
                            progress = startupProgress,
                            onInitializationComplete = {
                                mainViewModel.completeStartup()
                            }
                        )
                    } else {
                        MainApp(viewModel = mainViewModel)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-trigger daemon start if permissions were just granted
        DaemonManager.getInstance(this).startDaemonAuto(force = false)
    }
}
