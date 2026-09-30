/*
 * Package: com.cubicreates.unboundmusic.data
 * File: BackendClient.kt
 * Purpose: Lean core HTTP REST client for Unbound Music connecting directly to the embedded Go Engine daemon.
 *          Provides connection pooling, retry logic, Unix Domain Socket routing, and health checks.
 *          Domain APIs are modularized into domain extension files:
 *          - BackendClientAccount.kt (auth, profile, tastes, sync)
 *          - BackendClientExplore.kt (search, charts, stream resolution, playlists)
 *          - BackendClientDownloader.kt (offline queue, progress, management)
 *          - BackendClientMedia.kt (lyrics, DSP, AutoEq, Last.fm, Shazam, fallback, analytics)
 *          - BackendClientStorage.kt (library scan, acoustic fingerprinting, Scoped Storage)
 * Subsystem: Native Go Engine REST Client - Core Runtime & Transport
 * Concurrency: Non-blocking I/O operations executed on caller coroutine / Dispatchers.IO.
 */

package com.cubicreates.unboundmusic.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.cubicreates.unboundmusic.util.UnboundToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.net.InetAddress
import java.net.Socket
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.TimeUnit
import javax.net.SocketFactory

/**
 * SocketFactory implementation for routing OkHttp connections through a Unix domain socket on Android/Linux.
 */
class UnixDomainSocketFactory(private val socketFile: File) : SocketFactory() {
    override fun createSocket(): Socket {
        return try {
            val addressClass = Class.forName("java.net.UnixDomainSocketAddress")
            val ofMethod = addressClass.getMethod("of", String::class.java)
            val socketAddress = ofMethod.invoke(null, socketFile.absolutePath) as java.net.SocketAddress

            val standardProtocolFamily = Class.forName("java.net.StandardProtocolFamily")
            val unixField = standardProtocolFamily.getField("UNIX").get(null)

            val channelClass = java.nio.channels.SocketChannel::class.java
            val openMethod = channelClass.getMethod("open", java.net.ProtocolFamily::class.java)
            val channel = openMethod.invoke(null, unixField) as java.nio.channels.SocketChannel
            channel.connect(socketAddress)
            channel.socket()
        } catch (_: Throwable) {
            Socket()
        }
    }

    override fun createSocket(host: String?, port: Int): Socket = createSocket()
    override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket = createSocket()
    override fun createSocket(host: InetAddress?, port: Int): Socket = createSocket()
    override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket = createSocket()
}

/**
 * Singleton HTTP client communicating with the embedded Go engine daemon at 127.0.0.1:45731 or via Unix domain socket.
 * All methods return Pair<statusCode, responseBody> for uniform error handling.
 */
class BackendClient(baseUrlInput: String = "http://127.0.0.1:45731") {

    val isUnixSocket: Boolean = baseUrlInput.startsWith("unix:")
    val socketPath: String? = if (isUnixSocket) baseUrlInput.removePrefix("unix:") else null

    private val baseUrl: String = when {
        isUnixSocket -> "http://localhost"
        baseUrlInput.startsWith("http://") || baseUrlInput.startsWith("https://") -> baseUrlInput.trimEnd('/')
        else -> "http://${baseUrlInput.trimEnd('/')}"
    }

    private val httpClient: OkHttpClient = if (isUnixSocket && socketPath != null) {
        sharedOkHttpClient.newBuilder()
            .socketFactory(UnixDomainSocketFactory(File(socketPath)))
            .dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    return listOf(InetAddress.getByAddress("localhost", byteArrayOf(127, 0, 0, 1)))
                }
            })
            .build()
    } else {
        sharedOkHttpClient
    }

    companion object {
        val defaultJson = Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
        }
        private const val TAG = "BackendClient"
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        val sharedOkHttpClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
                .retryOnConnectionFailure(true)
                .build()
        }

        @Volatile
        var appContext: Context? = null

        private val stream502Timestamps = ConcurrentLinkedQueue<Long>()
        @Volatile
        private var last502ToastTimestamp: Long = 0L

        val recent502Count: Int
            get() = stream502Timestamps.size

        fun resetTelemetryForTesting() {
            stream502Timestamps.clear()
            last502ToastTimestamp = 0L
        }

        /**
         * Logs and tracks consecutive HTTP 502 errors from /api/v1/proxy/stream.
         * If 3 consecutive requests fail with 502 within a 60-second window,
         * triggers an in-app toast notifying the user:
         * "Streaming source temporarily adapting. Local offline tracks remain accessible."
         */
        fun recordProxyStreamStatus(statusCode: Int, context: Context? = null) {
            val targetContext = context ?: appContext
            if (statusCode == 502) {
                val now = System.currentTimeMillis()
                stream502Timestamps.add(now)

                // Purge entries older than 60 seconds (rolling 60s window)
                while (true) {
                    val oldest = stream502Timestamps.peek() ?: break
                    if (now - oldest > 60_000L) {
                        stream502Timestamps.poll()
                    } else {
                        break
                    }
                }

                Log.w(TAG, "Recorded HTTP 502 from /api/v1/proxy/stream. Recent 502 count in 60s window: ${stream502Timestamps.size}")

                if (stream502Timestamps.size >= 3) {
                    // Debounce toast notifications (minimum 30s interval)
                    if (now - last502ToastTimestamp > 30_000L) {
                        last502ToastTimestamp = now
                        stream502Timestamps.clear()
                        targetContext?.let { ctx ->
                            Handler(Looper.getMainLooper()).post {
                                UnboundToast.show(
                                    ctx.applicationContext,
                                    "Streaming source temporarily adapting. Local offline tracks remain accessible.",
                                    isLong = true
                                )
                            }
                        }
                    }
                }
            } else if (statusCode in 200..299) {
                // A successful stream resets the consecutive error streak
                stream502Timestamps.clear()
            }
        }
    }

    data class IdentifyTrackResult(
        val track: IdentifiedTrackDto? = null,
        val statusCode: Int = 0,
        val errorMessage: String? = null
    )

    // ==================== System Health & Status ====================

    /** Rapid low-overhead keepalive ping. Returns true if daemon responds with 200 OK. */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (code, _) = get("/api/v1/ping")
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    /** Returns true if daemon health check responds 200 OK. */
    suspend fun isHealthy(): Boolean = withContext(Dispatchers.IO) {
        try {
            val (code, _) = get("/api/v1/health")
            code == 200
        } catch (_: Exception) {
            false
        }
    }

    /** Verifies daemon is online, returns engine version, storage mode, RAM, goroutines. */
    suspend fun getStatus(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/status")
    }

    /** Alias for getStatus() - backward compatibility with DaemonManager health polling. */
    suspend fun healthCheck(): Pair<Int, String> = getStatus()

    // ==================== HTTP Transport (High-Performance Pooled OkHttp) ====================

    internal fun executeWithRetry(
        path: String,
        method: String,
        jsonBody: String? = null,
        headers: Map<String, String> = emptyMap()
    ): Pair<Int, String> {
        val maxAttempts = 3
        var attempt = 0
        var lastError = "Network error"

        while (attempt < maxAttempts) {
            attempt++
            try {
                val reqBuilder = Request.Builder().url("$baseUrl$path")
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                val body = jsonBody?.toRequestBody(JSON_MEDIA_TYPE)
                when (method.uppercase()) {
                    "POST" -> reqBuilder.post(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
                    "PUT" -> reqBuilder.put(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
                    "DELETE" -> {
                        if (body != null) reqBuilder.delete(body) else reqBuilder.delete()
                    }
                    else -> reqBuilder.get()
                }
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val respBody = response.body?.string() ?: ""
                    if (path.contains("proxy/stream")) {
                        recordProxyStreamStatus(response.code)
                    }
                    return Pair(response.code, respBody)
                }
            } catch (e: Exception) {
                lastError = e.message ?: "Network error"
                if (isUnixSocket) {
                    try {
                        val fallbackUrl = "http://127.0.0.1:45731$path"
                        val reqBuilder = Request.Builder().url(fallbackUrl)
                        headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                        val body = jsonBody?.toRequestBody(JSON_MEDIA_TYPE)
                        when (method.uppercase()) {
                            "POST" -> reqBuilder.post(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
                            "PUT" -> reqBuilder.put(body ?: "".toRequestBody(JSON_MEDIA_TYPE))
                            "DELETE" -> {
                                if (body != null) reqBuilder.delete(body) else reqBuilder.delete()
                            }
                            else -> reqBuilder.get()
                        }
                        sharedOkHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                            val respBody = response.body?.string() ?: ""
                            if (path.contains("proxy/stream")) {
                                recordProxyStreamStatus(response.code)
                            }
                            return Pair(response.code, respBody)
                        }
                    } catch (fbErr: Exception) {
                        lastError = fbErr.message ?: lastError
                    }
                }

                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(50L * attempt)
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        break
                    }
                }
            }
        }
        return Pair(-1, lastError)
    }

    internal fun get(path: String): Pair<Int, String> = executeWithRetry(path, "GET")

    internal fun post(
        path: String,
        jsonBody: String,
        headers: Map<String, String> = emptyMap()
    ): Pair<Int, String> = executeWithRetry(path, "POST", jsonBody = jsonBody, headers = headers)

    internal fun put(path: String, jsonBody: String): Pair<Int, String> = executeWithRetry(path, "PUT", jsonBody = jsonBody)

    internal fun delete(path: String): Pair<Int, String> = executeWithRetry(path, "DELETE")
}
