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
     * Cleans up any orphan .backend directory inside public storage (/storage/emulated/0/Unbound/.backend)
     * left over from older versions, ensuring no hidden machinery clutter exists in user-visible storage.
     * Note: This NEVER deletes user music, downloads, playlists, or recaps.
     */
    fun cleanupOrphanBackendFromPublic(): Boolean {
        return try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists()) {
                val orphanBackend = File(File(extStorage, "Unbound"), ".backend")
                if (orphanBackend.exists() && orphanBackend.isDirectory) {
                    Log.i(TAG, "Legacy orphan .backend found in public storage at ${orphanBackend.absolutePath}. Purging...")
                    orphanBackend.deleteRecursively()
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Orphan backend cleanup note: ${e.message}")
            false
        }
    }

    /**
     * Broadcasts a file/directory scan to Android's MediaScannerConnection so the Phone File Manager
     * immediately indexes the folder in the system file picker and third-party file managers.
     */
    fun scanPathWithMediaScanner(context: Context, path: String) {
        try {
            android.media.MediaScannerConnection.scanFile(
                context.applicationContext,
                arrayOf(path),
                null
            ) { scannedPath, uri ->
                Log.d(TAG, "MediaScanner indexed: $scannedPath -> $uri")
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner scan note: ${e.message}")
        }
    }

    /**
     * Public user-visible Unbound folder directly in Phone File Manager -> Internal Storage:
     * /storage/emulated/0/Unbound/
     * Subdirectories: Downloads/, Music/, Playlists/, Recaps/
     */
    fun getPublicUnboundDir(context: Context? = null): File {
        val extStorage = Environment.getExternalStorageDirectory()
        val unboundDir = if (extStorage != null && extStorage.exists() && extStorage.canWrite()) {
            File(extStorage, "Unbound")
        } else {
            File("/storage/emulated/0/Unbound")
        }

        if (!unboundDir.exists()) {
            unboundDir.mkdirs()
        }

        val pathsToScan = mutableListOf<String>()
        pathsToScan.add(unboundDir.absolutePath)

        val subDirs = listOf("Downloads", "Music", "Playlists", "Recaps")
        for (sub in subDirs) {
            val s = File(unboundDir, sub)
            if (!s.exists()) s.mkdirs()
            pathsToScan.add(s.absolutePath)
        }

        // Also ensure public Music/Unbound exists as an indexed mirror for standard file managers
        try {
            val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (publicMusic != null && (publicMusic.exists() || publicMusic.mkdirs())) {
                val musicUnbound = File(publicMusic, "Unbound")
                if (!musicUnbound.exists()) musicUnbound.mkdirs()
                pathsToScan.add(musicUnbound.absolutePath)
                for (sub in subDirs) {
                    val s = File(musicUnbound, sub)
                    if (!s.exists()) s.mkdirs()
                    pathsToScan.add(s.absolutePath)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Public Music folder mirror note: ${e.message}")
        }

        // Trigger MediaScanner so files app immediately surfaces the folder
        if (context != null) {
            for (p in pathsToScan) {
                scanPathWithMediaScanner(context, p)
            }
        }

        return unboundDir
    }

    /**
     * App-specific internal/external storage root for hidden engine machinery:
     * /storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/.backend
     * Contains: sqlite/, models/, logs/, cache/, daemon.sock, .nomedia
     * 
     * Advantages:
     * 1. Automatic OS Uninstall: Android OS automatically and completely purges this directory upon app uninstallation.
     * 2. Hidden Engine Machinery: /Android/data/ is restricted from regular phone file managers & galleries.
     * 3. Laptop Docking Visibility: When connected to a laptop via USB (MTP), the folder is fully visible and accessible
     *    under Android/data/com.cubicreates.unboundmusic/files/.backend/.
     * 4. Zero Permissions Required: App-specific external storage requires no runtime storage permissions.
     */
    fun getBackendStorageRoot(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val backendDir = File(baseDir, ".backend")
        if (!backendDir.exists()) {
            backendDir.mkdirs()
        }

        val sqliteDir = File(backendDir, "sqlite")
        val modelsDir = File(backendDir, "models")
        val logsDir = File(backendDir, "logs")
        val cacheDir = File(backendDir, "cache")

        if (!sqliteDir.exists()) sqliteDir.mkdirs()
        if (!modelsDir.exists()) modelsDir.mkdirs()
        if (!logsDir.exists()) logsDir.mkdirs()
        if (!cacheDir.exists()) cacheDir.mkdirs()

        val nomedia = File(backendDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }

        return backendDir
    }

    /**
     * Generates a pipe-delimited storage configuration string for the Go engine:
     * "<publicRoot>|<backendRoot>"
     */
    fun getCombinedStorageConfig(context: Context): String {
        val publicDir = getPublicUnboundDir(context)
        val backendDir = getBackendStorageRoot(context)
        return "${publicDir.absolutePath}|${backendDir.absolutePath}"
    }

    /**
     * Returns the user-visible public root folder (/storage/emulated/0/Unbound)
     * for public file storage operations (downloads, music, playlists, recaps).
     */
    fun getCanonicalUnboundRoot(context: Context): File {
        return getPublicUnboundDir(context)
    }
}
