/*
 * Package: com.cubicreates.unboundmusic.updater
 * File: AppUpdateManager.kt
 * Purpose: Independent in-app auto-updater that pings the Unbound Music marketing website
 *          backend (https://unboundmusic-two.vercel.app/api/version) to check for newer releases.
 * Subsystem: Distribution & Lifecycle Management
 */

package com.cubicreates.unboundmusic.updater

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import com.cubicreates.unboundmusic.data.AppUpdateInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

object AppUpdateManager {

    private const val TAG = "AppUpdateManager"
    const val FALLBACK_VERSION_NAME = "1.0.27.11.26"
    const val VERSION_API_URL = "https://unboundmusic-two.vercel.app/api/version"
    const val DOWNLOAD_PAGE_URL = "https://unboundmusic-two.vercel.app/download"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _updateInfo = MutableStateFlow<AppUpdateInfo?>(null)
    val updateInfo: StateFlow<AppUpdateInfo?> = _updateInfo.asStateFlow()

    private val _isChecking = MutableStateFlow(false)
    val isChecking: StateFlow<Boolean> = _isChecking.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    fun dismissUpdate() {
        _updateInfo.value = null
    }

    /**
     * Returns true strictly when remoteVersion is newer than currentVersion.
     * Supports both standard semver (M.m.p) and 5-part date versions (M.m.DD.MM.YY).
     */
    fun isNewerVersion(remoteVersion: String, currentVersion: String): Boolean {
        val rClean = remoteVersion.trim().removePrefix("v").removePrefix("V")
        val cClean = currentVersion.trim().removePrefix("v").removePrefix("V")
        if (rClean.isBlank() || cClean.isBlank() || rClean == cClean) return false

        val rParts = rClean.split(".").map { it.trim().toIntOrNull() ?: 0 }
        val cParts = cClean.split(".").map { it.trim().toIntOrNull() ?: 0 }

        if (rParts.size == 5 && cParts.size == 5) {
            val order = intArrayOf(0, 1, 4, 3, 2)
            for (idx in order) {
                if (rParts[idx] != cParts[idx]) {
                    return rParts[idx] > cParts[idx]
                }
            }
            return false
        }

        val maxLen = maxOf(rParts.size, cParts.size)
        for (i in 0 until maxLen) {
            val rv = rParts.getOrElse(i) { 0 }
            val cv = cParts.getOrElse(i) { 0 }
            if (rv != cv) return rv > cv
        }
        return false
    }

    /**
     * Pings https://unboundmusic-two.vercel.app/api/version and compares against installed app version.
     * Returns null when versions match or remote is not newer.
     */
    suspend fun checkForUpdates(context: Context, silent: Boolean = true): AppUpdateInfo? = withContext(Dispatchers.IO) {
        _isChecking.value = true
        try {
            val req = Request.Builder()
                .url(VERSION_API_URL)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "Version API returned HTTP ${resp.code}")
                    return@withContext null
                }

                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val remoteVersion = json.optString("version", "").trim()
                val currentVersion = getCurrentVersionName(context)
                Log.i(TAG, "Version check: installed=$currentVersion, remote=$remoteVersion")

                if (isNewerVersion(remoteVersion, currentVersion)) {
                    val info = AppUpdateInfo(
                        currentVersion = currentVersion,
                        latestVersion = remoteVersion,
                        hasUpdate = true,
                        releaseNotes = "Hey! A new version ($remoteVersion) of Unbound Music is available. Install the latest app now.",
                        downloadUrl = DOWNLOAD_PAGE_URL,
                        publishedAt = ""
                    )
                    _updateInfo.value = info
                    return@withContext info
                } else {
                    _updateInfo.value = null
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
        } finally {
            _isChecking.value = false
        }
        return@withContext null
    }

    /**
     * Opens the official download page or downloads an APK directly and prompts installation.
     */
    fun startDownloadAndInstall(context: Context, info: AppUpdateInfo) {
        val targetUrl = info.downloadUrl.ifBlank { DOWNLOAD_PAGE_URL }
        if (!targetUrl.endsWith(".apk", ignoreCase = true)) {
            val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(browserIntent)
            return
        }

        if (_isDownloading.value) return
        _isDownloading.value = true

        try {
            val safeVer = info.latestVersion.replace(".", "")
            val destinationFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "unboundmusic_V$safeVer.apk"
            )
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(targetUrl))
                .setTitle("Downloading Unbound Music update")
                .setDescription("Version ${info.latestVersion}")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationUri(Uri.fromFile(destinationFile))
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)

            val downloadId = downloadManager.enqueue(request)

            val appContext = context.applicationContext
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(recvContext: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L) ?: -1L
                    if (id == downloadId) {
                        _isDownloading.value = false
                        try {
                            appContext.unregisterReceiver(this)
                        } catch (_: Exception) {}

                        if (destinationFile.exists() && destinationFile.length() > 0) {
                            promptInstallApk(appContext, destinationFile)
                        }
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                appContext.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE),
                    Context.RECEIVER_EXPORTED
                )
            } else {
                appContext.registerReceiver(
                    receiver,
                    IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed initiating APK download: ${e.message}", e)
            _isDownloading.value = false
        }
    }

    /**
     * Fires ACTION_VIEW intent using FileProvider to trigger native system package installer.
     */
    fun promptInstallApk(context: Context, apkFile: File) {
        try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed launching system installer: ${e.message}", e)
        }
    }

    fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: FALLBACK_VERSION_NAME
        } catch (_: Exception) {
            FALLBACK_VERSION_NAME
        }
    }
}
