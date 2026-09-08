/*
 * Package: com.cubicreates.unboundmusic
 * File: MainActivity.kt
 * Purpose: Main entry Activity for Unbound Music. Hosts the Studio-Mode technical brutalist
 *          SplashScreen during engine hydration and transitions to MainApp once ready.
 * Subsystem: Application Entry / Lifecycle Gatekeeper
 */

package com.cubicreates.unboundmusic

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    private val mainViewModel: MainViewModel by viewModels()


    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
            permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
        }
        if (audioGranted) {
            com.cubicreates.unboundmusic.service.UnboundStorageManager.getPublicUnboundDir(this)
            mainViewModel.rescanLocalStorage()
        }
    }

    private val manageStorageLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            com.cubicreates.unboundmusic.service.UnboundStorageManager.getPublicUnboundDir(this)
            mainViewModel.rescanLocalStorage()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Connect Media3 playback service
        serviceConnection = ServiceConnection.getInstance(this)
        serviceConnection.connect()

        // Check & request runtime audio and notification permissions
        checkAndRequestPermissions()

        setContent {
            val selectedTheme by mainViewModel.selectedTheme.collectAsStateWithLifecycle()
            val isAppReady by mainViewModel.isAppReady.collectAsStateWithLifecycle()
            val startupPhase by mainViewModel.startupPhase.collectAsStateWithLifecycle()
            val startupProgress by mainViewModel.startupProgress.collectAsStateWithLifecycle()

            UnboundMusicTheme(themePreset = selectedTheme) {
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

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            com.cubicreates.unboundmusic.service.UnboundStorageManager.getPublicUnboundDir(this)
            mainViewModel.rescanLocalStorage()
        }

        // Request MANAGE_APP_ALL_FILES_ACCESS_PERMISSION on Android 11+ (API 30+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    manageStorageLauncher.launch(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        manageStorageLauncher.launch(intent)
                    } catch (_: Exception) {}
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        DaemonManager.getInstance(this).startDaemonAuto(force = false)
        com.cubicreates.unboundmusic.service.UnboundStorageManager.getPublicUnboundDir(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                mainViewModel.rescanLocalStorage()
            }
        } else {
            val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
            } else {
                ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
            }
            if (audioGranted) {
                mainViewModel.rescanLocalStorage()
            }
        }
    }
}
