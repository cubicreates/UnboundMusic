/*
 * Package: com.cubicreates.unboundmusic.service
 * File: DaemonWatchdog.kt
 * Purpose: Robust keepalive watchdog for embedded localhost Go daemon.
 *          Performs periodic ping/pong telemetry, tracks consecutive dropouts,
 *          exposes reactive DaemonHealthState, and triggers recovery callbacks.
 * Subsystem: Native Daemon IPC Resilience
 * Concurrency: Thread-safe, coroutine-based heartbeat loop with supervisor isolation.
 */

package com.cubicreates.unboundmusic.service

import android.util.Log
import com.cubicreates.unboundmusic.data.BackendClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class DaemonHealthState {
    ONLINE,
    DEGRADED,
    DISCONNECTED,
    RECONNECTING
}

class DaemonWatchdog(
    private val client: BackendClient,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
    private val onRecoveryAction: (suspend () -> Unit)? = null
) {
    private val _healthState = MutableStateFlow(DaemonHealthState.DISCONNECTED)
    val healthState: StateFlow<DaemonHealthState> = _healthState.asStateFlow()

    private var watchdogJob: Job? = null
    private var consecutiveFailures = 0

    fun start(intervalMs: Long = 10_000L) {
        if (watchdogJob?.isActive == true) return
        watchdogJob = scope.launch {
            while (isActive) {
                val isAlive = try {
                    client.ping()
                } catch (e: Exception) {
                    false
                }

                if (isAlive) {
                    if (consecutiveFailures > 0) {
                        Log.i(TAG, "Daemon recovered after $consecutiveFailures failed attempts.")
                        onRecoveryAction?.invoke()
                    }
                    consecutiveFailures = 0
                    _healthState.value = DaemonHealthState.ONLINE
                    delay(intervalMs)
                } else {
                    consecutiveFailures++
                    Log.w(TAG, "Daemon ping failed (consecutive failures: $consecutiveFailures)")
                    _healthState.value = if (consecutiveFailures < 3) {
                        DaemonHealthState.DEGRADED
                    } else {
                        DaemonHealthState.DISCONNECTED
                    }
                    // Exponential backoff up to 30 seconds
                    val backoff = (intervalMs * (1L shl (consecutiveFailures.coerceAtMost(3) - 1))).coerceAtMost(30_000L)
                    delay(backoff)
                }
            }
        }
    }

    fun stop() {
        watchdogJob?.cancel()
        watchdogJob = null
    }

    companion object {
        private const val TAG = "DaemonWatchdog"
    }
}
