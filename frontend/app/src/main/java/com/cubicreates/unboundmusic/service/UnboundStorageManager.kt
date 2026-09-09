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
     * Purges any legacy /storage/emulated/0/Unbound or /storage/emulated/0/Music/Unbound folders
     * left over from older versions, ensuring no orphan files linger outside the app lifecycle.
     */
    fun cleanupLegacyPublicStorage(): Boolean {
        var purged = false
        try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists()) {
                val legacyUnbound = File(extStorage, "Unbound")
                if (legacyUnbound.exists()) {
                    Log.i(TAG, "Legacy public Unbound folder found at ${legacyUnbound.absolutePath}. Purging for clean uninstall lifecycle...")
                    purged = legacyUnbound.deleteRecursively() || purged
                }
            }
            val publicFallback = File("/storage/emulated/0/Unbound")
            if (publicFallback.exists()) {
                Log.i(TAG, "Legacy public /storage/emulated/0/Unbound found. Purging...")
                purged = publicFallback.deleteRecursively() || purged
            }
            val publicMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
            if (publicMusic != null && publicMusic.exists()) {
                val musicUnbound = File(publicMusic, "Unbound")
                if (musicUnbound.exists()) {
                    Log.i(TAG, "Legacy public Music/Unbound mirror found. Purging...")
                    purged = musicUnbound.deleteRecursively() || purged
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Legacy public storage cleanup note: ${e.message}")
        }
        return purged
    }

    /**
     * Cleans up any orphan .backend directory inside public storage (/storage/emulated/0/Unbound/.backend)
     * left over from older versions.
     */
    fun cleanupOrphanBackendFromPublic(): Boolean {
        return cleanupLegacyPublicStorage()
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
     * Canonical Unbound folder located in app-specific external storage:
     * /storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/Unbound/
     * Subdirectories: Downloads/, Music/, Playlists/, Recaps/
     * 
     * Lifecycle Guarantees:
     * 1. Auto-deploy on install / first launch: All directories deploy instantly without permissions.
     * 2. Auto-delete on uninstall: Android OS automatically wipes the entire folder and package directory.
     * 3. MTP / Laptop Visibility: When connected to a laptop via USB (MTP), the folder is fully visible
     *    under Android/data/com.cubicreates.unboundmusic/files/Unbound/.
     */
    fun getCanonicalUnboundRoot(context: Context): File {
        val baseDir = context.getExternalFilesDir(null) ?: context.filesDir
        val unboundDir = File(baseDir, "Unbound")
        if (!unboundDir.exists()) {
            unboundDir.mkdirs()
        }

        val subDirs = listOf("Downloads", "Music", "Playlists", "Recaps")
        for (sub in subDirs) {
            val s = File(unboundDir, sub)
            if (!s.exists()) s.mkdirs()
        }

        scanPathWithMediaScanner(context, unboundDir.absolutePath)
        return unboundDir
    }

    /**
     * Public user-visible Unbound folder accessor. Delegates to canonical app-specific root.
     */
    fun getPublicUnboundDir(context: Context? = null): File {
        if (context != null) {
            return getCanonicalUnboundRoot(context)
        }
        val extStorage = Environment.getExternalStorageDirectory()
        val fallback = if (extStorage != null && extStorage.exists()) {
            File(extStorage, "Android/data/com.cubicreates.unboundmusic/files/Unbound")
        } else {
            File("/storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/Unbound")
        }
        if (!fallback.exists()) fallback.mkdirs()
        return fallback
    }

    /**
     * App-specific internal/external storage root for hidden engine machinery:
     * /storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/.backend
     * Contains: sqlite/, models/, logs/, cache/, daemon.sock, .nomedia
     * 
     * Advantages:
     * 1. Automatic OS Uninstall: Android OS automatically and completely purges this directory upon app uninstallation.
     * 2. Hidden Engine Machinery: /Android/data/ is restricted from regular phone file managers & galleries.
     * 3. Laptop Docking Visibility: When connected to a laptop via USB (MTP), the folder is fully visible and accessible.
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
     * Deploys and provisions the complete Unbound storage structure on install / cold start.
     * Also cleans up any legacy public folders so only the lifecycle-managed Unbound folder exists.
     */
    fun deployUnboundStorage(context: Context): File {
        cleanupLegacyPublicStorage()
        val canonicalRoot = getCanonicalUnboundRoot(context)
        val backendRoot = getBackendStorageRoot(context)
        Log.i(TAG, "Unbound storage deployed: root=${canonicalRoot.absolutePath}, backend=${backendRoot.absolutePath}")
        return canonicalRoot
    }

    /**
     * Generates a pipe-delimited storage configuration string for the Go engine:
     * "<publicRoot>|<backendRoot>"
     */
    fun getCombinedStorageConfig(context: Context): String {
        val canonicalDir = getCanonicalUnboundRoot(context)
        val backendDir = getBackendStorageRoot(context)
        return "${canonicalDir.absolutePath}|${backendDir.absolutePath}"
    }
}
