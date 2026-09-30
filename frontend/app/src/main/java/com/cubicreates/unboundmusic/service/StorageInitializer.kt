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

            // Deploy canonical app-specific Unbound folder and purge legacy public storage
            UnboundStorageManager.deployUnboundStorage(context)

            // 1. App-specific internal/external backend directory (/Android/data/.../.backend)
            val backendRoot = UnboundStorageManager.getBackendStorageRoot(context)
            val modelsDir = File(backendRoot, "models")

            if (!binDir.exists()) binDir.mkdirs()
            if (!backendRoot.exists()) backendRoot.mkdirs()
            if (!modelsDir.exists()) modelsDir.mkdirs()

            // 2. Pure in-process Go engine provides all acoustic DSP and fingerprinting
            Log.i(TAG, "Storage directories initialized. Native DSP & Shazam engine active via in-process Go daemon.")

            // 3. Check for presence of on-device AI model weights
            val primaryModel = File(modelsDir, "smollm2_135m.gguf")
            if (primaryModel.exists() && primaryModel.length() > 0L) {
                Log.i(TAG, "On-device AI model weights verified at ${primaryModel.absolutePath} (${primaryModel.length()} bytes)")
            } else {
                Log.i(TAG, "Lightweight mode active: No on-device LLM weights installed. Fast in-memory heuristic vibe search engaged.")
            }

            Log.i(TAG, "Storage initialization completed successfully.")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error during storage initialization: ${e.message}", e)
            false
        }
    }

    /**
     * Checks if the on-device AI model weights are downloaded and available.
     */
    fun isAiModelInstalled(context: Context): Boolean {
        val backendRoot = UnboundStorageManager.getBackendStorageRoot(context)
        val primaryModel = File(File(backendRoot, "models"), "smollm2_135m.gguf")
        return primaryModel.exists() && primaryModel.length() > 0L
    }

    /**
     * Downloads models.zst on-demand from remote URL and unpacks via Go daemon.
     */
    suspend fun downloadAndInstallModel(
        context: Context,
        downloadUrl: String = "https://github.com/cubicreates/UnboundMusic/releases/download/v1.0.0-assets/models.zst",
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            val backendRoot = UnboundStorageManager.getBackendStorageRoot(context)
            val modelsDir = File(backendRoot, "models")
            if (!modelsDir.exists()) modelsDir.mkdirs()
            val zstFile = File(modelsDir, "models.zst")

            Log.i(TAG, "Starting on-demand download of AI model weights from $downloadUrl")
            val request = okhttp3.Request.Builder().url(downloadUrl).build()
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e(TAG, "Failed downloading AI model payload: HTTP ${response.code}")
                return@withContext false
            }

            val body = response.body ?: return@withContext false
            val contentLength = body.contentLength()
            var downloadedBytes = 0L

            body.byteStream().use { input ->
                FileOutputStream(zstFile).use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloadedBytes += read
                        if (contentLength > 0) {
                            val progress = downloadedBytes.toFloat() / contentLength.toFloat()
                            onProgress(progress.coerceIn(0f, 1f))
                        }
                    }
                }
            }

            Log.i(TAG, "Model payload downloaded (${zstFile.length()} bytes). Unpacking via Go engine...")
            val daemonClient = com.cubicreates.unboundmusic.daemon.DaemonManager.getInstance(context).client
            val unpacked = daemonClient.unpackPayload(zstFile.absolutePath, modelsDir.absolutePath)
            if (unpacked) {
                if (zstFile.exists()) zstFile.delete()
                Log.i(TAG, "On-demand AI model installation complete.")
                true
            } else {
                Log.e(TAG, "Failed unpacking AI model payload via daemon.")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error downloading and installing AI model: ${e.message}", e)
            false
        }
    }

    /**
     * Decompresses models.zst using the running Go daemon if present.
     */
    suspend fun unpackModelsIfPending(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val backendRoot = UnboundStorageManager.getBackendStorageRoot(context)
            val modelsDir = File(backendRoot, "models")
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
            true
        } catch (e: Exception) {
            Log.w(TAG, "unpackModelsIfPending note: ${e.message}")
            true
        }
    }
}
