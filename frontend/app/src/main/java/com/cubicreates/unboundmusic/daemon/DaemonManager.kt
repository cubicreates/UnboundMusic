/*
 * Package: com.cubicreates.unboundmusic.daemon
 * File: DaemonManager.kt
 * Purpose: Production Native Daemon Lifecycle Manager for Unbound Music: loads libunbound_engine.so and orchestrates the embedded Go daemon.
 * Subsystem: Native JNI Engine Lifecycle
 * Concurrency: Thread-safe singleton with coroutine polling and reactive StateFlow state.
 */

package com.cubicreates.unboundmusic.daemon

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.Log
import com.cubicreates.unboundmusic.data.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

sealed interface DaemonLifecycleState {
    object Idle : DaemonLifecycleState
    object Starting : DaemonLifecycleState
    data class Running(val port: Int, val appStoragePath: String) : DaemonLifecycleState
    data class Error(val message: String) : DaemonLifecycleState
}

class DaemonManager private constructor(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    val client = BackendClient("http://127.0.0.1:$DAEMON_PORT")

    private val _state = MutableStateFlow<DaemonLifecycleState>(DaemonLifecycleState.Idle)
    val state: StateFlow<DaemonLifecycleState> = _state.asStateFlow()

    companion object {
        private const val TAG = "DaemonManager"
        const val DAEMON_PORT = 45731

        init {
            try {
                System.loadLibrary("unbound_engine")
                Log.d(TAG, "Native library libunbound_engine.so loaded successfully.")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "Failed loading native library libunbound_engine.so: ${e.message}", e)
            }
        }

        @Volatile
        private var instance: DaemonManager? = null

        fun getInstance(context: Context): DaemonManager {
            return instance ?: synchronized(this) {
                instance ?: DaemonManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private external fun startEngineNative(appStoragePath: String, port: Int): Int
    private external fun stopEngineNative(): Int

    fun startDaemonAuto(force: Boolean = false) {
        if (!force && _state.value is DaemonLifecycleState.Running) {
            Log.d(TAG, "Native daemon is already running.")
            return
        }

        _state.value = DaemonLifecycleState.Starting

        scope.launch {
            try {
                val hasPerms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Environment.isExternalStorageManager()
                } else {
                    context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED
                }

                val targetBaseDir = if (hasPerms && Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED) {
                    Environment.getExternalStorageDirectory()
                } else {
                    context.getExternalFilesDir(null) ?: context.filesDir
                }

                val unboundRoot = File(targetBaseDir, "Unbound")
                val backendHiddenDir = File(unboundRoot, ".backend")
                val sqliteDir = File(backendHiddenDir, "sqlite")
                val musicDir = File(unboundRoot, "Music")

                if (!sqliteDir.exists()) sqliteDir.mkdirs()
                if (!musicDir.exists()) musicDir.mkdirs()

                Log.d(TAG, "Starting Go Engine on port $DAEMON_PORT with storage path ${unboundRoot.absolutePath}")

                val ret = try {
                    startEngineNative(unboundRoot.absolutePath, DAEMON_PORT)
                } catch (e: UnsatisfiedLinkError) {
                    Log.e(TAG, "startEngineNative linkage failed: ${e.message}")
                    -2
                }

                if (ret < 0 && ret != -2) {
                    _state.value = DaemonLifecycleState.Error("Native startEngine failed (code: $ret)")
                    return@launch
                }

                var isAlive = false
                for (i in 1..25) {
                    delay(150)
                    val (code, _) = client.healthCheck()
                    if (code in 200..299) {
                        isAlive = true
                        break
                    }
                }

                if (isAlive) {
                    _state.value = DaemonLifecycleState.Running(DAEMON_PORT, unboundRoot.absolutePath)
                    Log.i(TAG, "Production Go Engine is running and healthy on 127.0.0.1:$DAEMON_PORT")
                } else {
                    _state.value = DaemonLifecycleState.Error("Daemon failed to answer health check within timeout.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error booting daemon: ${e.message}", e)
                _state.value = DaemonLifecycleState.Error(e.message ?: "Unknown startup exception")
            }
        }
    }

    fun stopDaemon() {
        scope.launch {
            try {
                stopEngineNative()
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping daemon: ${e.message}")
            }
            _state.value = DaemonLifecycleState.Idle
        }
    }
}
