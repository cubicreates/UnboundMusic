/*
 * Package: com.cubicreates.unboundmusic.service
 * File: UnboundStorageManager.kt
 * Purpose: Manages Unbound directory lifecycle: detects existing legacy folders across storage,
 *          prompts user for clean re-creation, and routes all storage to app-specific external
 *          storage so that Android OS automatically deletes all data upon app uninstallation.
 * Subsystem: Storage & Lifecycle Management
 */

package com.cubicreates.unboundmusic.service

import android.content.Context
import android.os.Environment
import android.util.Log
import java.io.File

object UnboundStorageManager {

    private const val TAG = "UnboundStorageManager"

    /**
     * Finds any existing directory containing "unbound" (case-insensitive) on:
     * 1. Public storage root (/storage/emulated/0/Unbound...)
     * 2. App external files dir (/storage/emulated/0/Android/data/.../files/Unbound...)
     * 3. App internal files dir (/data/data/.../files/Unbound...)
     */
    fun findExistingUnboundFolders(context: Context): List<File> {
        val found = mutableListOf<File>()

        try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists() && extStorage.canRead()) {
                val matches = extStorage.listFiles { file ->
                    file.isDirectory && file.name.contains("unbound", ignoreCase = true)
                }
                matches?.let { found.addAll(it) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "External storage scan note: ${e.message}")
        }

        try {
            val appExtDir = context.getExternalFilesDir(null)
            if (appExtDir != null && appExtDir.exists()) {
                val matches = appExtDir.listFiles { file ->
                    file.isDirectory && file.name.contains("unbound", ignoreCase = true)
                }
                matches?.let { found.addAll(it) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "App external dir scan note: ${e.message}")
        }

        try {
            val internalFiles = context.filesDir
            if (internalFiles != null && internalFiles.exists()) {
                val matches = internalFiles.listFiles { file ->
                    file.isDirectory && file.name.contains("unbound", ignoreCase = true)
                }
                matches?.let { found.addAll(it) }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Internal files dir scan note: ${e.message}")
        }

        return found.distinctBy { it.absolutePath }
            .filter { it.exists() && it.isDirectory && (it.listFiles()?.isNotEmpty() == true) }
    }

    /**
     * Deletes the specified folders recursively and provisions a fresh Unbound directory.
     */
    fun deleteFolders(folders: List<File>): Boolean {
        var allDeleted = true
        for (f in folders) {
            try {
                Log.i(TAG, "Deleting folder: ${f.absolutePath}")
                val success = f.deleteRecursively()
                if (!success) allDeleted = false
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete ${f.absolutePath}: ${e.message}")
                allDeleted = false
            }
        }
        return allDeleted
    }

    /**
     * Cleans up legacy public shared storage at /storage/emulated/0/Unbound if it exists,
     * ensuring no orphan files or exposed .backend folders remain from previous versions.
     */
    fun cleanupLegacyPublicStorage(): Boolean {
        return try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists()) {
                val legacyPublic = File(extStorage, "Unbound")
                if (legacyPublic.exists() && legacyPublic.isDirectory) {
                    Log.i(TAG, "Legacy public Unbound folder detected at ${legacyPublic.absolutePath}. Purging...")
                    legacyPublic.deleteRecursively()
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Legacy public storage cleanup note: ${e.message}")
            false
        }
    }

    /**
     * Returns the canonical Unbound root folder inside app-specific external storage:
     * /storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/Unbound
     * 
     * Advantages:
     * 1. Automatic OS Uninstall: Android OS automatically and completely purges this directory upon app uninstallation.
     * 2. Hidden Engine Machinery: Since Android 11, /Android/data/ is restricted from regular phone file managers,
     *    preventing .backend from cluttering the phone's gallery/file explorer.
     * 3. Laptop Docking Visibility: When connected to a laptop via USB (MTP), the folder is fully visible and accessible
     *    under Android/data/com.cubicreates.unboundmusic/files/Unbound/.
     * 4. Zero Permissions Required: App-specific external storage requires no runtime storage permissions.
     */
    fun getCanonicalUnboundRoot(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val unboundDir = File(baseDir, "Unbound")
        if (!unboundDir.exists()) {
            unboundDir.mkdirs()
        }

        val backendDir = File(unboundDir, ".backend")
        val sqliteDir = File(backendDir, "sqlite")
        val modelsDir = File(backendDir, "models")
        val logsDir = File(backendDir, "logs")
        val cacheDir = File(backendDir, "cache")
        val downloadsDir = File(unboundDir, "Downloads")
        val musicDir = File(unboundDir, "Music")
        val playlistsDir = File(unboundDir, "Playlists")
        val recapsDir = File(unboundDir, "Recaps")

        if (!backendDir.exists()) backendDir.mkdirs()
        if (!sqliteDir.exists()) sqliteDir.mkdirs()
        if (!modelsDir.exists()) modelsDir.mkdirs()
        if (!logsDir.exists()) logsDir.mkdirs()
        if (!cacheDir.exists()) cacheDir.mkdirs()
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        if (!musicDir.exists()) musicDir.mkdirs()
        if (!playlistsDir.exists()) playlistsDir.mkdirs()
        if (!recapsDir.exists()) recapsDir.mkdirs()

        val nomedia = File(backendDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }

        return unboundDir
    }
}
