/*
 * Package: com.cubicreates.unboundmusic.daemon
 * File: DaemonManager.kt
 * Purpose: Production Native Daemon Lifecycle Manager for Unbound Music: loads libunbound_engine.so and orchestrates the embedded Go daemon.
 * Subsystem: Native JNI Engine Lifecycle
 * Concurrency: Thread-safe singleton with coroutine polling and reactive StateFlow state.
 */

package com.cubicreates.unboundmusic.daemon

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.res.Configuration
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
    val client: BackendClient by lazy {
        BackendClient("http://127.0.0.1:$DAEMON_PORT")
    }

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
    private external fun trimEngineMemoryNative(): Int

    init {
        context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) {
                if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
                    try {
                        trimEngineMemoryNative()
                        Log.d(TAG, "Native Go engine memory trimmed on level $level")
                    } catch (e: UnsatisfiedLinkError) {
                        Log.w(TAG, "trimEngineMemoryNative linkage unavailable: ${e.message}")
                    }
                }
            }

            override fun onConfigurationChanged(newConfig: Configuration) {}

            override fun onLowMemory() {
                try {
                    trimEngineMemoryNative()
                    Log.d(TAG, "Native Go engine memory trimmed on onLowMemory")
                } catch (e: UnsatisfiedLinkError) {
                    Log.w(TAG, "trimEngineMemoryNative linkage unavailable: ${e.message}")
                }
            }
        })
    }

    fun startDaemonAuto(force: Boolean = false) {
        if (!force && _state.value is DaemonLifecycleState.Running) {
            Log.d(TAG, "Native daemon is already running.")
            return
        }

        _state.value = DaemonLifecycleState.Starting

        scope.launch {
            try {
                val storageConfig = com.cubicreates.unboundmusic.service.UnboundStorageManager.getCombinedStorageConfig(context)
                val publicRoot = com.cubicreates.unboundmusic.service.UnboundStorageManager.getPublicUnboundDir()

                Log.d(TAG, "Starting Go Engine on port $DAEMON_PORT with storage config $storageConfig")

                val ret = try {
                    startEngineNative(storageConfig, DAEMON_PORT)
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
                    _state.value = DaemonLifecycleState.Running(DAEMON_PORT, publicRoot.absolutePath)
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

    /**
     * Suspends until the embedded Go Engine daemon is healthy and accepting requests, or until timeoutMs expires.
     * Guarantees that the splash screen only transitions after the backend is ready, eliminating cold-start 502/refused errors.
     */
    suspend fun awaitReady(timeoutMs: Long = 5000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (_state.value is DaemonLifecycleState.Running) {
                val (code, _) = client.healthCheck()
                if (code in 200..299) {
                    return true
                }
            }
            delay(50)
        }
        return false
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
