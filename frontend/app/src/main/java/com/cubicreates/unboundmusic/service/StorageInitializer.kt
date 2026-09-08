/*
 * Package: com.cubicreates.unboundmusic.service
 * File: StorageInitializer.kt
 * Purpose: First-boot cold-start asset unpacker: extracts native binaries (fpcalc, llama-cli),
 *          sets POSIX executable permissions, and explodes models.zst into context.filesDir/models/.
 * Subsystem: Cold-Start Guest Engine & Native Assets
 * Concurrency: Thread-safe singleton; operations run in background coroutines.
 */

package com.cubicreates.unboundmusic.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object StorageInitializer {

    private const val TAG = "StorageInitializer"

    /**
     * Initializes internal app storage on first boot:
     * 1. Extracts fpcalc and llama-cli from assets/bin/arm64-v8a/ to files/bin/ and marks them executable.
     * 2. Verifies presence of AI models in files/models/; if missing, unpacks models.zst.
     */
    suspend fun initialize(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val filesDir = context.filesDir
            val binDir = File(filesDir, "bin")

            // Clean up any legacy public storage leftovers from previous versions (/storage/emulated/0/Unbound)
            UnboundStorageManager.cleanupLegacyPublicStorage()

            // Canonical Unbound directory (app-specific external storage, automatically purged by OS on uninstall)
            val unboundRoot = UnboundStorageManager.getCanonicalUnboundRoot(context)
            val backendDir = File(unboundRoot, ".backend")
            val modelsDir = File(backendDir, "models")

            if (!binDir.exists()) binDir.mkdirs()
            if (!backendDir.exists()) backendDir.mkdirs()
            if (!modelsDir.exists()) modelsDir.mkdirs()

            // Create .nomedia so Android MediaStore & gallery apps ignore .backend completely
            val noMediaFile = File(backendDir, ".nomedia")
            if (!noMediaFile.exists()) {
                try { noMediaFile.createNewFile() } catch (_: Exception) {}
            }

            // 1. Extract and set executable permissions for fpcalc & llama-cli (must be in app internal binDir for execve permissions)
            extractBinaryAsset(context, "bin/arm64-v8a/fpcalc", File(binDir, "fpcalc"))
            extractBinaryAsset(context, "bin/arm64-v8a/llama-cli", File(binDir, "llama-cli"))

            // 2. Extract AI models archive into hidden Unbound/.backend/models/ if primary model missing
            val primaryModel = File(modelsDir, "smollm2_135m.gguf")
            if (!primaryModel.exists() || primaryModel.length() == 0L) {
                Log.i(TAG, "AI model weights missing from ${modelsDir.absolutePath}. Extracting archive payload...")
                extractPayloadAsset(context, "payload/models.zst", File(modelsDir, "models.zst"))
            } else {
                Log.i(TAG, "AI model weights already initialized at ${primaryModel.absolutePath}")
            }

            Log.i(TAG, "Storage and binary initialization completed successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during storage initialization: ${e.message}", e)
            false
        }
    }

    /**
     * Decompresses models.zst using the running Go daemon if smollm2_135m.gguf is missing.
     */
    suspend fun unpackModelsIfPending(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val unboundRoot = UnboundStorageManager.getCanonicalUnboundRoot(context)
            val modelsDir = File(unboundRoot, ".backend/models")
            val primaryModel = File(modelsDir, "smollm2_135m.gguf")
            val zstFile = File(modelsDir, "models.zst")

            if (primaryModel.exists() && primaryModel.length() > 0L) {
                if (zstFile.exists()) {
                    zstFile.delete()
                }
                return@withContext true
            }

            if (zstFile.exists() && zstFile.length() > 0L) {
                Log.i(TAG, "Calling daemon to unpack ${zstFile.absolutePath} into ${modelsDir.absolutePath}")
                val client = com.cubicreates.unboundmusic.daemon.DaemonManager.getInstance(context).client
                val success = client.unpackPayload(zstFile.absolutePath, modelsDir.absolutePath)
                if (success && primaryModel.exists()) {
                    Log.i(TAG, "Successfully extracted AI model: ${primaryModel.absolutePath} (${primaryModel.length()} bytes)")
                    zstFile.delete()
                    return@withContext true
                }
            }
            false
        } catch (e: Exception) {
            Log.w(TAG, "unpackModelsIfPending note: ${e.message}")
            false
        }
    }

    private fun extractBinaryAsset(context: Context, assetPath: String, destFile: File) {
        try {
            var needsCopy = !destFile.exists()
            if (!needsCopy) {
                // Verify file size matches asset if accessible
                try {
                    context.assets.openFd(assetPath).use { fd ->
                        if (destFile.length() != fd.length) {
                            needsCopy = true
                        }
                    }
                } catch (_: Exception) {
                    // Compressed assets do not support openFd, keep existing if non-empty
                    if (destFile.length() == 0L) needsCopy = true
                }
            }

            if (needsCopy) {
                Log.i(TAG, "Extracting binary asset $assetPath to ${destFile.absolutePath}")
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }

            // Set POSIX executable permissions: readable and executable by all
            val execSuccess = destFile.setExecutable(true, false)
            val readSuccess = destFile.setReadable(true, false)
            Log.d(TAG, "Binary ${destFile.name} permissions: executable=$execSuccess, readable=$readSuccess")
        } catch (e: Exception) {
            Log.w(TAG, "Failed extracting binary asset $assetPath: ${e.message}")
        }
    }

    private fun extractPayloadAsset(context: Context, assetPath: String, destFile: File) {
        try {
            if (!destFile.exists() || destFile.length() == 0L) {
                Log.i(TAG, "Copying $assetPath to ${destFile.absolutePath}")
                context.assets.open(assetPath).use { input ->
                    FileOutputStream(destFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            Log.i(TAG, "Payload asset copied (${destFile.length()} bytes).")
        } catch (e: Exception) {
            Log.w(TAG, "Failed copying payload asset $assetPath: ${e.message}")
        }
    }
}
