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
     * Returns the canonical Unbound root folder inside context.getExternalFilesDir(null).
     * By placing the Unbound folder in Android/data/<package>/files/, Android OS automatically
     * deletes the entire folder, database, and cache when the app is uninstalled.
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
        val musicDir = File(unboundDir, "Music")

        if (!sqliteDir.exists()) sqliteDir.mkdirs()
        if (!modelsDir.exists()) modelsDir.mkdirs()
        if (!musicDir.exists()) musicDir.mkdirs()

        val nomedia = File(backendDir, ".nomedia")
        if (!nomedia.exists()) {
            try { nomedia.createNewFile() } catch (_: Exception) {}
        }

        return unboundDir
    }
}
