/*
 * Package: com.cubicreates.unboundmusic.util
 * File: UnboundToast.kt
 * Purpose: Production silent diagnostic logger (toasts disabled for clean release UX).
 * Subsystem: Diagnostic Feedback
 */

package com.cubicreates.unboundmusic.util

import android.content.Context
import android.util.Log

object UnboundToast {
    private const val TAG = "UnboundLog"

    fun show(context: Context?, message: String, isLong: Boolean = true) {
        if (context == null || message.isBlank()) return
        Log.d(TAG, message)
    }

    fun clear() {}
}
