/*
 * Package: com.cubicreates.unboundmusic.util
 * File: UnboundToast.kt
 * Purpose: Thread-safe diagnostic Toast dispatcher that can be invoked from any thread,
 *          service, or background coroutine to display real-time errors to the user.
 * Subsystem: Diagnostic UI Feedback
 */

package com.cubicreates.unboundmusic.util

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast

object UnboundToast {
    private const val TAG = "UnboundToast"
    private val mainHandler = Handler(Looper.getMainLooper())

    fun show(context: Context?, message: String, isLong: Boolean = true) {
        if (context == null || message.isBlank()) return
        Log.i(TAG, "DIAGNOSTIC TOAST: $message")
        mainHandler.post {
            try {
                Toast.makeText(
                    context.applicationContext,
                    message,
                    if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to display toast: ${e.message}")
            }
        }
    }
}
