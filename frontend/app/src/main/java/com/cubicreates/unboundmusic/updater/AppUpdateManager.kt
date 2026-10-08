/*
 * Package: com.cubicreates.unboundmusic.updater
 * File: AppUpdateManager.kt
 * Purpose: Independent in-app auto-updater for sideloaded distribution without Google Play Store.
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
    private const val MANIFEST_URL = "https://raw.githubusercontent.com/cubicreates/UnboundMusic/main/manifests/app_version.json"

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
     * Queries remote release manifest to detect newer version code.
     */
    suspend fun checkForUpdates(context: Context, silent: Boolean = true): AppUpdateInfo? = withContext(Dispatchers.IO) {
        _isChecking.value = true
        try {
            val req = Request.Builder().url(MANIFEST_URL).build()
            val resp = httpClient.newCall(req).execute()
            if (!resp.isSuccessful) {
                Log.w(TAG, "Update manifest returned ${resp.code}")
                return@withContext null
            }

            val body = resp.body?.string() ?: return@withContext null
            val json = JSONObject(body)
            val remoteCode = json.optInt("version_code", 0)
            val remoteName = json.optString("version_name", "")
            val notes = json.optString("release_notes", "Bug fixes and performance improvements.")
            val downloadUrl = json.optString("download_url", "")

            val currentCode = getCurrentVersionCode(context)
            val currentName = getCurrentVersionName(context)
            Log.i(TAG, "Update check: currentCode=$currentCode, remoteCode=$remoteCode")

            if (remoteCode > currentCode && downloadUrl.isNotBlank()) {
                val info = AppUpdateInfo(
                    currentVersion = currentName,
                    latestVersion = remoteName,
                    hasUpdate = true,
                    releaseNotes = notes,
                    downloadUrl = downloadUrl,
                    publishedAt = json.optString("published_at", ""),
                    versionCode = remoteCode
                )
                _updateInfo.value = info
                return@withContext info
            } else {
                _updateInfo.value = null
            }
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed: ${e.message}")
        } finally {
            _isChecking.value = false
        }
        return@withContext null
    }

    /**
     * Downloads APK using Android's DownloadManager and triggers immediate package install prompt.
     */
    fun startDownloadAndInstall(context: Context, info: AppUpdateInfo) {
        if (_isDownloading.value) return
        _isDownloading.value = true

        try {
            val destinationFile = File(
                context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
                "unbound-music-${info.latestVersion}.apk"
            )
            if (destinationFile.exists()) {
                destinationFile.delete()
            }

            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(info.downloadUrl))
                .setTitle("Downloading UnboundMusic update")
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

    private fun getCurrentVersionName(context: Context): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: "2.0.0"
        } catch (_: Exception) {
            "2.0.0"
        }
    }

    private fun getCurrentVersionCode(context: Context): Int {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
        } catch (e: Exception) {
            3
        }
    }
}
