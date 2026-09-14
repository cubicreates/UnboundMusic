/*
 * Package: com.cubicreates.unboundmusic.notification
 * File: DownloadNotificationHelper.kt
 * Purpose: Android System Notification Manager for Unbound Offline MP3 Downloads.
 *          Guarantees exactly ONE notification is displayed at any time by using
 *          a dedicated progress ID and auto-dismissing prior progress alerts.
 * Subsystem: Offline Physical Downloader & Notifications
 * Concurrency: Thread-safe Android NotificationManagerCompat helper.
 */

package com.cubicreates.unboundmusic.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.cubicreates.unboundmusic.MainActivity

class DownloadNotificationHelper(private val context: Context) {

    companion object {
        private const val TAG = "DownloadNotificationHelper"
        const val CHANNEL_ID = "unbound_downloads_channel"
        const val CHANNEL_NAME = "Unbound Downloads"
        
        // Single unified notification IDs to ensure exactly ONE notification is shown
        const val PROGRESS_NOTIF_ID = 54321
        const val RESULT_NOTIF_ID = 54322
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows real-time progress for music downloads saved to Unbound/Downloads"
                setShowBadge(false)
                enableVibration(false)
                setSound(null, null)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    private fun getPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, 0, intent, flags)
    }

    /** Posts an ongoing notification indicating that a download has queued or started. */
    fun notifyDownloadStarted(videoId: String, title: String, artist: String) {
        try {
            // Dismiss any old result notification first
            notificationManager.cancel(RESULT_NOTIF_ID)

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(if (title.isNotBlank()) "Downloading: $title" else "Downloading music...")
                .setContentText(if (artist.isNotBlank()) "$artist • MP3" else "Saving to Unbound/Downloads (MP3)")
                .setProgress(100, 0, true)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(getPendingIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW)

            notificationManager.notify(PROGRESS_NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post download started notification: ${e.message}")
        }
    }

    /** Updates the single ongoing notification with numeric progress percentage (0 - 100). */
    fun notifyDownloadProgress(videoId: String, title: String, artist: String, progressPercent: Int) {
        try {
            val clamped = progressPercent.coerceIn(0, 100)
            val text = if (artist.isNotBlank()) "$clamped% • $artist • MP3" else "$clamped% • Unbound/Downloads"

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(if (title.isNotBlank()) "Downloading: $title" else "Downloading music...")
                .setContentText(text)
                .setProgress(100, clamped, false)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(getPendingIntent())
                .setPriority(NotificationCompat.PRIORITY_LOW)

            notificationManager.notify(PROGRESS_NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update download progress notification: ${e.message}")
        }
    }

    /** Replaces the progress notification with a dismissible completion notification. */
    fun notifyDownloadCompleted(videoId: String, title: String, artist: String) {
        try {
            // Dismiss ongoing progress bar notification
            notificationManager.cancel(PROGRESS_NOTIF_ID)

            val text = if (artist.isNotBlank()) "$artist • Saved as MP3 to Unbound/Downloads" else "Saved as MP3 to Unbound/Downloads"

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(if (title.isNotBlank()) "Downloaded: $title" else "Download complete")
                .setContentText(text)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(getPendingIntent())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            notificationManager.notify(RESULT_NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post download complete notification: ${e.message}")
        }
    }

    /** Replaces the progress notification with a dismissible failure notification. */
    fun notifyDownloadFailed(videoId: String, title: String, artist: String, errorReason: String) {
        try {
            // Dismiss ongoing progress bar notification
            notificationManager.cancel(PROGRESS_NOTIF_ID)

            val text = if (errorReason.isNotBlank()) errorReason else "Download failed"

            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(if (title.isNotBlank()) "Download Failed: $title" else "Download failed")
                .setContentText(text)
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .setContentIntent(getPendingIntent())
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)

            notificationManager.notify(RESULT_NOTIF_ID, builder.build())
        } catch (e: SecurityException) {
            Log.w(TAG, "POST_NOTIFICATIONS permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to post download failed notification: ${e.message}")
        }
    }

    /** Cancels all download notifications immediately. */
    fun cancelNotification(videoId: String? = null) {
        try {
            notificationManager.cancel(PROGRESS_NOTIF_ID)
            notificationManager.cancel(RESULT_NOTIF_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel download notification: ${e.message}")
        }
    }
}
