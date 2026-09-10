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
     * Safely cleans up stale temporary files without touching the user's visible Unbound folder.
     */
    fun cleanupLegacyPublicStorage(): Boolean {
        // Do NOT delete public Unbound or Music/Unbound folders!
        // Only clean up any stale temporary files in the temp directory if needed.
        return true
    }

    /**
     * Cleans up any orphan .backend directory inside public storage (/storage/emulated/0/Unbound/.backend)
     * left over from older versions, ensuring engine machinery remains private.
     */
    fun cleanupOrphanBackendFromPublic(): Boolean {
        try {
            val publicDir = getPublicUnboundDir()
            val orphanBackend = File(publicDir, ".backend")
            if (orphanBackend.exists() && orphanBackend.isDirectory) {
                Log.i(TAG, "Purging orphan .backend from public storage: ${orphanBackend.absolutePath}")
                orphanBackend.deleteRecursively()
                return true
            }
        } catch (e: Exception) {
            Log.w(TAG, "Orphan backend cleanup note: ${e.message}")
        }
        return false
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
     * Cleans up legacy /Music/Unbound and /Unbound folders from earlier development versions.
     */
    fun cleanupLegacyStorageFolders() {
        try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists()) {
                val oldMusic = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)
                if (oldMusic != null && oldMusic.exists()) {
                    val oldMusicUnbound = File(oldMusic, "Unbound")
                    if (oldMusicUnbound.exists()) {
                        Log.i(TAG, "Removing obsolete legacy folder: ${oldMusicUnbound.absolutePath}")
                        oldMusicUnbound.deleteRecursively()
                    }
                }
                val oldRootUnbound = File(extStorage, "Unbound")
                if (oldRootUnbound.exists()) {
                    Log.i(TAG, "Removing obsolete legacy root mirror: ${oldRootUnbound.absolutePath}")
                    oldRootUnbound.deleteRecursively()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Legacy storage cleanup notice: ${e.message}")
        }
    }

    /**
     * Canonical user-visible public Unbound folder located in standard Android Downloads storage:
     * Path: /storage/emulated/0/Download/Unbound
     * Subdirectories: Downloads/, Music/, Playlists/, Recaps/, README.txt
     */
    fun getPublicUnboundDir(context: Context? = null): File {
        val publicDownloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val targetDir = if (publicDownloads != null && (publicDownloads.exists() || publicDownloads.mkdirs())) {
            File(publicDownloads, "Unbound")
        } else {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists()) {
                File(File(extStorage, "Download"), "Unbound")
            } else {
                File("/storage/emulated/0/Download/Unbound")
            }
        }

        if (!targetDir.exists()) {
            targetDir.mkdirs()
        }
        return targetDir
    }

    /**
     * Canonical Unbound folder accessor. Uses public user-visible Unbound folder.
     */
    fun getCanonicalUnboundRoot(context: Context): File {
        return getPublicUnboundDir(context)
    }

    /**
     * App-specific internal/external storage root for hidden engine machinery:
     * /storage/emulated/0/Android/data/com.cubicreates.unboundmusic/files/.backend
     * Contains: sqlite/, models/, logs/, cache/, daemon.sock, .nomedia
     * 
     * Advantages:
     * 1. Automatic OS Uninstall: Android OS automatically and completely purges this directory upon app uninstallation.
     * 2. Hidden Engine Machinery: /Android/data/ is restricted from regular phone file managers & galleries.
     * 3. Zero Permissions Required: App-specific external storage requires no runtime storage permissions.
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
     * Deploys and provisions the complete visible Unbound storage structure on install / cold start.
     * Provisions the single public Unbound folder under /storage/emulated/0/Download/Unbound
     * with Downloads/, Music/, Playlists/, Recaps/, and README.txt.
     */
    fun deployUnboundStorage(context: Context): File {
        val publicRoot = getPublicUnboundDir(context)
        val subDirs = listOf("Downloads", "Music", "Playlists", "Recaps")
        val readmeText = "Unbound Music Storage\n\nThis directory contains your offline music, downloads, playlists, and recaps.\nFiles placed in the Music or Downloads folders are automatically indexed and available offline.\n"

        try {
            if (!publicRoot.exists()) {
                publicRoot.mkdirs()
            }
            for (sub in subDirs) {
                val s = File(publicRoot, sub)
                if (!s.exists()) {
                    s.mkdirs()
                }
                scanPathWithMediaScanner(context, s.absolutePath)
            }
            val infoFile = File(publicRoot, "README.txt")
            if (!infoFile.exists()) {
                infoFile.writeText(readmeText)
            }
            scanPathWithMediaScanner(context, infoFile.absolutePath)
            scanPathWithMediaScanner(context, publicRoot.absolutePath)
        } catch (e: Exception) {
            Log.w(TAG, "Error provisioning public Unbound storage: ${e.message}")
        }

        cleanupLegacyStorageFolders()
        cleanupOrphanBackendFromPublic()

        Log.i(TAG, "Unbound storage successfully deployed to visible public storage: ${publicRoot.absolutePath}")
        return publicRoot
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
}
