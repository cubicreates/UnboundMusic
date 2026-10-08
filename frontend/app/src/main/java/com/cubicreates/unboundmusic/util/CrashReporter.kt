/*
 * Package: com.cubicreates.unboundmusic.util
 * File: CrashReporter.kt
 * Purpose: Structured crash reporter capturing stack traces, device specs, and hardware state for offline audit.
 * Subsystem: Reliability & Telemetry
 */

package com.cubicreates.unboundmusic.util

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.cubicreates.unboundmusic.service.LockscreenArtworkManager
import com.cubicreates.unboundmusic.service.UnboundStorageManager
import org.json.JSONObject
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object CrashReporter {

    private const val TAG = "CrashReporter"
    private const val CRASH_DIR_NAME = "crash_dumps"
    private const val LATEST_CRASH_FILE = "latest_crash.txt"
    private const val MAX_SAVED_CRASHES = 5

    private var originalHandler: Thread.UncaughtExceptionHandler? = null

    /**
     * Installs global uncaught exception handler on application thread.
     */
    fun install(application: Application) {
        originalHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            handleUncaughtException(application, thread, throwable)
        }
        Log.i(TAG, "Unbound CrashReporter installed successfully.")
    }

    private fun handleUncaughtException(application: Application, thread: Thread, throwable: Throwable) {
        try {
            // Restore lockscreen wallpaper safely if an artwork was actively applied
            try {
                LockscreenArtworkManager.restoreOriginalLockscreenArtwork(application, synchronous = true)
            } catch (_: Throwable) {}

            // Generate structured crash report
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale.US)
            val timestamp = dateFormat.format(Date())

            val versionName = try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    application.packageManager.getPackageInfo(application.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    application.packageManager.getPackageInfo(application.packageName, 0)
                }
                pInfo.versionName ?: "unknown"
            } catch (_: Exception) {
                "unknown"
            }

            val json = JSONObject().apply {
                put("timestamp", timestamp)
                put("app_version", versionName)
                put("android_sdk", Build.VERSION.SDK_INT)
                put("device_model", "${Build.MANUFACTURER} ${Build.MODEL}")
                put("thread_name", thread.name)
                put("thread_id", thread.id)
                put("exception_type", throwable.javaClass.name)
                put("message", throwable.message ?: "No message")
                put("cause", throwable.cause?.javaClass?.name ?: "None")
                put("stack_trace", stackTrace)
            }

            val humanReadable = buildString {
                appendLine("=========================================")
                appendLine("UNBOUND MUSIC CRASH REPORT")
                appendLine("Timestamp: $timestamp")
                appendLine("App Version: $versionName")
                appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})")
                appendLine("Thread: ${thread.name} (ID=${thread.id})")
                appendLine("Exception: ${throwable.javaClass.name}: ${throwable.message}")
                appendLine("Cause: ${throwable.cause?.javaClass?.name}: ${throwable.cause?.message}")
                appendLine("----------------- STACK -----------------")
                appendLine(stackTrace)
                appendLine("=========================================")
            }

            // Write to internal private directory
            val crashDir = File(application.filesDir, CRASH_DIR_NAME).apply { mkdirs() }
            val timeKey = System.currentTimeMillis()
            val crashFile = File(crashDir, "crash_$timeKey.json")
            crashFile.writeText(json.toString(2))

            val latestFile = File(application.filesDir, LATEST_CRASH_FILE)
            latestFile.writeText(humanReadable)

            // Attempt writing to public Unbound directory for easy user extraction
            try {
                val publicDir = UnboundStorageManager.getPublicUnboundDir(application)
                if (publicDir.exists()) {
                    File(publicDir, "unbound_crash_dump.txt").writeText(humanReadable)
                }
            } catch (_: Throwable) {}

            pruneOldCrashes(crashDir)
            Log.e(TAG, "Fatal crash report generated at ${crashFile.absolutePath}")
        } catch (e: Throwable) {
            Log.e(TAG, "Error generating crash report: ${e.message}", e)
        } finally {
            originalHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun pruneOldCrashes(crashDir: File) {
        val files = crashDir.listFiles { f -> f.name.startsWith("crash_") && f.name.endsWith(".json") }
            ?.sortedByDescending { it.lastModified() } ?: return

        if (files.size > MAX_SAVED_CRASHES) {
            files.drop(MAX_SAVED_CRASHES).forEach { it.delete() }
        }
    }

    /**
     * Checks if a previous crash report is available in internal files.
     */
    fun getLatestCrashReport(context: Context): String? {
        val file = File(context.filesDir, LATEST_CRASH_FILE)
        return if (file.exists() && file.length() > 0) {
            file.readText()
        } else {
            null
        }
    }

    /**
     * Clears latest crash report marker once acknowledged.
     */
    fun clearLatestCrashReport(context: Context) {
        try {
            val file = File(context.filesDir, LATEST_CRASH_FILE)
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }
}
