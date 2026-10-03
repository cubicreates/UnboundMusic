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
import android.os.PowerManager
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

import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Primary Activity hosting Unbound Music with animated startup gatekeeper.
 */
class MainActivity : ComponentActivity() {

    private lateinit var serviceConnection: ServiceConnection
    private val mainViewModel: MainViewModel by viewModels()


    private val showOnboardingState = androidx.compose.runtime.mutableStateOf(false)

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.READ_MEDIA_AUDIO] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
        } else {
            permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
        }
        if (audioGranted) {
            lifecycleScope.launch(Dispatchers.IO) {
                com.cubicreates.unboundmusic.service.UnboundStorageManager.deployUnboundStorage(this@MainActivity)
            }
            mainViewModel.triggerVlcDifferentialStorageScan(silent = false)
        }
        promptBatteryOptimizationIfNeeded()
        com.cubicreates.unboundmusic.data.PlaybackStateStore.setCompletedOnboarding(this, true)
        showOnboardingState.value = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            volumeControlStream = android.media.AudioManager.STREAM_MUSIC
            enableEdgeToEdge()
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Audio stream / edge-to-edge configuration note: ${e.message}")
        }

        // Connect Media3 playback service safely
        try {
            serviceConnection = ServiceConnection.getInstance(this)
            serviceConnection.connect()
        } catch (e: Throwable) {
            android.util.Log.e("MainActivity", "ServiceConnection connect error: ${e.message}")
        }

        // Deploy visible Unbound storage asynchronously in background to ensure zero main-thread blockage
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.cubicreates.unboundmusic.service.UnboundStorageManager.deployUnboundStorage(this@MainActivity)
            } catch (_: Throwable) {}
        }

        // Check if first-run permissions onboarding should display
        if (!com.cubicreates.unboundmusic.data.PlaybackStateStore.hasCompletedOnboarding(this)) {
            showOnboardingState.value = true
        } else {
            checkAndRequestPermissions()
        }

        setContent {
            val selectedTheme by mainViewModel.selectedTheme.collectAsStateWithLifecycle()
            val isAppReady by mainViewModel.isAppReady.collectAsStateWithLifecycle()
            val startupPhase by mainViewModel.startupPhase.collectAsStateWithLifecycle()
            val startupProgress by mainViewModel.startupProgress.collectAsStateWithLifecycle()
            val showOnboarding by showOnboardingState

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

                // Studio Brutalist One-Tap Permissions Onboarding Modal
                if (showOnboarding) {
                    val audioGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO) == PackageManager.PERMISSION_GRANTED
                    } else {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    }
                    val notifGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else true
                    val micGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
                    val batteryOptimized = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        powerManager?.isIgnoringBatteryOptimizations(packageName) == true
                    } else true
                    val allFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Environment.isExternalStorageManager()
                    } else true

                    com.cubicreates.unboundmusic.ui.components.PermissionsOnboardingSheet(
                        audioGranted = audioGranted,
                        notificationsGranted = notifGranted,
                        microphoneGranted = micGranted,
                        batteryOptimized = batteryOptimized,
                        allFilesGranted = allFilesGranted,
                        onGrantAllClicked = {
                            val toRequest = mutableListOf<String>()
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (!audioGranted) toRequest.add(Manifest.permission.READ_MEDIA_AUDIO)
                                if (!notifGranted) toRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                if (!audioGranted) toRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
                            }
                            if (!micGranted) toRequest.add(Manifest.permission.RECORD_AUDIO)

                            if (toRequest.isNotEmpty()) {
                                permissionLauncher.launch(toRequest.toTypedArray())
                            } else {
                                promptBatteryOptimizationIfNeeded()
                                com.cubicreates.unboundmusic.data.PlaybackStateStore.setCompletedOnboarding(this@MainActivity, true)
                                showOnboardingState.value = false
                            }
                        },
                        onRequestAllFilesClicked = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = Uri.parse("package:$packageName")
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                    }
                                    startActivity(intent)
                                } catch (_: Exception) {}
                            }
                        },
                        onDismissOrSkip = {
                            com.cubicreates.unboundmusic.data.PlaybackStateStore.setCompletedOnboarding(this@MainActivity, true)
                            showOnboardingState.value = false
                        }
                    )
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
            // Permissions already granted (app update scenario)
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    com.cubicreates.unboundmusic.service.UnboundStorageManager.deployUnboundStorage(this@MainActivity)
                } catch (_: Throwable) {}
            }
            // Defer battery optimization until UI is fully mounted and rendered
            lifecycleScope.launch(Dispatchers.Main) {
                kotlinx.coroutines.delay(1500)
                promptBatteryOptimizationIfNeeded()
            }
        }
    }

    private fun promptBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val powerManager = getSystemService(POWER_SERVICE) as? PowerManager
                if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                    val prefs = getSharedPreferences("unbound_prefs", MODE_PRIVATE)
                    val alreadyPrompted = prefs.getBoolean("battery_optimization_prompted", false)
                    if (!alreadyPrompted) {
                        prefs.edit().putBoolean("battery_optimization_prompted", true).apply()
                        try {
                            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                data = Uri.parse("package:$packageName")
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            startActivity(intent)
                        } catch (e: Exception) {
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                                }
                                startActivity(intent)
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Throwable) {
                android.util.Log.w("MainActivity", "Battery optimization check note: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            DaemonManager.getInstance(this).startDaemonAuto(force = false)
        } catch (e: Throwable) {
            android.util.Log.w("MainActivity", "Daemon auto-start onResume note: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
