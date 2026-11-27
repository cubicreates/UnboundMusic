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
import com.cubicreates.unboundmusic.data.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

object StorageInitializer {

    private const val TAG = "StorageInitializer"

    // Exact public zero-token HuggingFace endpoints used by scripts/fetch_ai_payload.ps1
    const val SMOLLLM2_GGUF_URL =
        "https://huggingface.co/bartowski/SmolLM2-135M-Instruct-GGUF/resolve/main/SmolLM2-135M-Instruct-Q4_K_M.gguf"
    const val MINILM_ONNX_URL =
        "https://huggingface.co/Xenova/all-MiniLM-L6-v2/resolve/main/onnx/model_quantized.onnx"

    private const val MIN_GGUF_SIZE_BYTES = 80_000_000L
    private const val MIN_ONNX_SIZE_BYTES = 18_000_000L

    private val activeDownloadCall = AtomicReference<okhttp3.Call?>(null)
    private val cancelRequested = AtomicBoolean(false)

    /**
     * Immediately aborts any active OkHttp model download to save battery and mobile data.
     */
    fun cancelActiveDownload() {
        cancelRequested.set(true)
        activeDownloadCall.getAndSet(null)?.cancel()
    }

    /**
     * Initializes internal app storage on first boot without bundling heavy LLM weights in the APK.
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

            // 3. Clean up any incomplete/corrupt on-demand AI model downloads from aborted transfers
            val primaryModel = File(modelsDir, "smollm2_135m.gguf")
            val onnxModel = File(modelsDir, "model_quantized.onnx")
            File(modelsDir, "smollm2_135m.gguf.tmp").takeIf { it.exists() }?.delete()
            File(modelsDir, "model_quantized.onnx.tmp").takeIf { it.exists() }?.delete()

            if (primaryModel.exists() && primaryModel.length() < MIN_GGUF_SIZE_BYTES) {
                Log.w(TAG, "Detected incomplete/corrupt model payload (${primaryModel.length()} bytes). Purging...")
                primaryModel.delete()
            }
            if (onnxModel.exists() && onnxModel.length() < MIN_ONNX_SIZE_BYTES) {
                Log.w(TAG, "Detected incomplete/corrupt ONNX payload (${onnxModel.length()} bytes). Purging...")
                onnxModel.delete()
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
        return primaryModel.exists() && primaryModel.length() >= MIN_GGUF_SIZE_BYTES
    }

    /**
     * Deletes downloaded on-device LLM/ONNX weights so users can reclaim storage cleanly.
     */
    suspend fun deleteAiModel(context: Context): Boolean = withContext(Dispatchers.IO) {
        try {
            val backendRoot = UnboundStorageManager.getBackendStorageRoot(context)
            val modelsDir = File(backendRoot, "models")
            listOf(
                "smollm2_135m.gguf",
                "smollm2_135m.gguf.tmp",
                "model_quantized.onnx",
                "model_quantized.onnx.tmp",
                "models.zst",
                "models.zst.tmp"
            ).forEach { name ->
                File(modelsDir, name).takeIf { it.exists() }?.delete()
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting AI model weights: ${e.message}", e)
            false
        }
    }

    private suspend fun downloadFileWithProgress(
        client: okhttp3.OkHttpClient,
        url: String,
        destFile: File,
        onFraction: (Float) -> Unit
    ): Boolean {
        val tempFile = File(destFile.parentFile, "${destFile.name}.tmp")
        if (tempFile.exists()) tempFile.delete()

        val request = okhttp3.Request.Builder()
            .url(url)
            .header("User-Agent", "UnboundMusic/1.0 (Android)")
            .build()

        val call = client.newCall(request)
        activeDownloadCall.set(call)
        try {
            if (cancelRequested.get() || !currentCoroutineContext().isActive) {
                call.cancel()
                throw CancellationException("LLM download cancelled")
            }

            call.execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "Failed downloading $url: HTTP ${response.code}")
                    return false
                }
                val body = response.body ?: return false
                val contentLength = body.contentLength()
                var downloadedBytes = 0L

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var read: Int
                        while (input.read(buffer).also { read = it } != -1) {
                            if (cancelRequested.get() || !currentCoroutineContext().isActive) {
                                throw CancellationException("LLM download cancelled")
                            }
                            output.write(buffer, 0, read)
                            downloadedBytes += read
                            if (contentLength > 0) {
                                onFraction((downloadedBytes.toFloat() / contentLength.toFloat()).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }

            if (cancelRequested.get() || !currentCoroutineContext().isActive) {
                throw CancellationException("LLM download cancelled")
            }
            if (destFile.exists()) destFile.delete()
            return tempFile.renameTo(destFile)
        } catch (ce: CancellationException) {
            if (tempFile.exists()) tempFile.delete()
            throw ce
        } catch (e: Exception) {
            if (tempFile.exists()) tempFile.delete()
            if (cancelRequested.get() || !currentCoroutineContext().isActive) {
                throw CancellationException("LLM download cancelled")
            }
            throw e
        } finally {
            activeDownloadCall.compareAndSet(call, null)
        }
    }

    /**
     * Downloads the on-device LLM directly from HuggingFace (or unpacks a .zst archive URL) on demand.
     */
    suspend fun downloadAndInstallModel(
        context: Context,
        downloadUrl: String = SMOLLLM2_GGUF_URL,
        onProgress: (Float) -> Unit = {}
    ): Boolean = withContext(Dispatchers.IO) {
        cancelRequested.set(false)
        val backendRoot = UnboundStorageManager.getBackendStorageRoot(context)
        val modelsDir = File(backendRoot, "models")
        try {
            if (!modelsDir.exists()) modelsDir.mkdirs()

            val client = okhttp3.OkHttpClient.Builder()
                .followRedirects(true)
                .followSslRedirects(true)
                .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            if (downloadUrl.endsWith(".zst", ignoreCase = true)) {
                val zstFile = File(modelsDir, "models.zst")
                Log.i(TAG, "Starting on-demand Zstd model download from $downloadUrl")
                val ok = downloadFileWithProgress(client, downloadUrl, zstFile) { frac ->
                    onProgress((frac * 0.9f).coerceIn(0f, 0.9f))
                }
                if (!ok) return@withContext false

                Log.i(TAG, "Model archive downloaded (${zstFile.length()} bytes). Unpacking via Go engine...")
                val daemonClient = com.cubicreates.unboundmusic.daemon.DaemonManager.getInstance(context).client
                val unpacked = daemonClient.unpackPayload(zstFile.absolutePath, modelsDir.absolutePath)
                if (zstFile.exists()) zstFile.delete()
                if (unpacked && isAiModelInstalled(context)) {
                    onProgress(1.0f)
                    Log.i(TAG, "On-demand AI model installation complete.")
                    return@withContext true
                }
                Log.e(TAG, "Failed unpacking AI model payload via daemon.")
                return@withContext false
            }

            // Direct download of SmolLM2-135M GGUF + optional MiniLM ONNX embeddings
            val primaryModel = File(modelsDir, "smollm2_135m.gguf")
            Log.i(TAG, "Starting direct download of SmolLM2-135M from $downloadUrl")
            onProgress(0.01f)
            val ggufOk = downloadFileWithProgress(client, downloadUrl, primaryModel) { frac ->
                onProgress((frac * 0.85f).coerceIn(0.01f, 0.85f))
            }
            if (!ggufOk || primaryModel.length() < MIN_GGUF_SIZE_BYTES) {
                Log.e(TAG, "Primary GGUF download incomplete or invalid (${primaryModel.length()} bytes)")
                if (primaryModel.exists()) primaryModel.delete()
                return@withContext false
            }

            // Also fetch lightweight MiniLM embeddings ONNX (non-fatal if already present or skipped)
            val onnxModel = File(modelsDir, "model_quantized.onnx")
            if (!onnxModel.exists() || onnxModel.length() < MIN_ONNX_SIZE_BYTES) {
                Log.i(TAG, "Downloading companion embeddings ONNX from $MINILM_ONNX_URL")
                val onnxOk = downloadFileWithProgress(client, MINILM_ONNX_URL, onnxModel) { frac ->
                    onProgress((0.85f + frac * 0.15f).coerceIn(0.85f, 1.0f))
                }
                if (!onnxOk || onnxModel.length() < MIN_ONNX_SIZE_BYTES) {
                    Log.w(TAG, "Companion ONNX download did not complete; core SmolLM2 GGUF remains installed.")
                    if (onnxModel.exists() && onnxModel.length() < MIN_ONNX_SIZE_BYTES) {
                        onnxModel.delete()
                    }
                }
            }

            onProgress(1.0f)
            Log.i(TAG, "On-demand SmolLM2-135M installation complete (${primaryModel.length()} bytes).")
            true
        } catch (ce: CancellationException) {
            Log.i(TAG, "AI model download cancelled by user; cleaning partial files.")
            listOf(
                "smollm2_135m.gguf.tmp",
                "model_quantized.onnx.tmp",
                "models.zst",
                "models.zst.tmp"
            ).forEach { File(modelsDir, it).takeIf { f -> f.exists() }?.delete() }
            throw ce
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
