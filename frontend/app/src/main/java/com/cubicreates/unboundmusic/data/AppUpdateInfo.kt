/*
 * Package: com.cubicreates.unboundmusic.data
 * File: AppUpdateInfo.kt
 * Purpose: Data class representing in-app release update metadata from GitHub releases.
 * Subsystem: Application Lifecycle & Updates
 */

package com.cubicreates.unboundmusic.data

import org.json.JSONObject

data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val hasUpdate: Boolean,
    val releaseNotes: String,
    val downloadUrl: String,
    val publishedAt: String
) {
    companion object {
        fun fromJson(jsonStr: String): AppUpdateInfo? {
            return try {
                val json = JSONObject(jsonStr)
                AppUpdateInfo(
                    currentVersion = json.optString("current_version", ""),
                    latestVersion = json.optString("latest_version", ""),
                    hasUpdate = json.optBoolean("has_update", false),
                    releaseNotes = json.optString("release_notes", ""),
                    downloadUrl = json.optString("download_url", ""),
                    publishedAt = json.optString("published_at", "")
                )
            } catch (_: Exception) {
                null
            }
        }
    }
}
