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
     * Returns the canonical Unbound root folder.
     * Prioritizes the public user-visible root at /storage/emulated/0/Unbound so that
     * the folder is immediately accessible and visible in the device file manager.
     * If external storage is inaccessible or unwritable, gracefully falls back to app-specific external storage.
     */
    fun getCanonicalUnboundRoot(context: Context): File {
        // 1. Prioritize public shared storage root (/storage/emulated/0/Unbound)
        val publicRoot = try {
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null && extStorage.exists()) {
                val candidate = File(extStorage, "Unbound")
                if (!candidate.exists()) {
                    candidate.mkdirs()
                }
                if (candidate.exists() && candidate.canWrite()) {
                    candidate
                } else null
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "Public Unbound root creation attempt: ${e.message}")
            null
        }

        // 2. Fall back to app external or internal files dir if public root is inaccessible
        val baseDir = publicRoot ?: (context.getExternalFilesDir(null) ?: context.filesDir)
        val unboundDir = if (baseDir.name == "Unbound") baseDir else File(baseDir, "Unbound")
        if (!unboundDir.exists()) {
            unboundDir.mkdirs()
        }

        val backendDir = File(unboundDir, ".backend")
        val sqliteDir = File(backendDir, "sqlite")
        val modelsDir = File(backendDir, "models")
        val downloadsDir = File(unboundDir, "Downloads")
        val musicDir = File(unboundDir, "Music")

        if (!backendDir.exists()) backendDir.mkdirs()
        if (!sqliteDir.exists()) sqliteDir.mkdirs()
        if (!modelsDir.exists()) modelsDir.mkdirs()
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        if (!musicDir.exists()) musicDir.mkdirs()

        val nomedia = File(backendDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }

        return unboundDir
    }
}
