/*
 * Package: com.cubicreates.unboundmusic.service
 * File: UnboundPlaybackService.kt
 * Purpose: Production Media3 Foreground Playback Service for Unbound Music. Manages background audio
 *          playback, lockscreen controls, notification center, Bluetooth AVRCP, audio focus,
 *          and custom AudioProcessor pipeline (10-Band Parametric Equalizer, Sleep Timer Fade, DJ Crossfade).
 * Subsystem: Audio Playback Service
 * Concurrency: Foreground service on main thread with internal ExoPlayer threading.
 */

package com.cubicreates.unboundmusic.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_MAX_BUFFER_MS
import androidx.media3.exoplayer.DefaultLoadControl.DEFAULT_MIN_BUFFER_MS
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cubicreates.unboundmusic.MainActivity
import com.cubicreates.unboundmusic.R
import com.cubicreates.unboundmusic.audio.CrossfadeFilterAudioProcessor
import com.cubicreates.unboundmusic.audio.EqualizerAudioProcessor
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.audio.SleepFadeAudioProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Foreground playback service using Media3 ExoPlayer and MediaSession.
 * Mirrors SimpMusic's proven streaming pipeline with resilient local proxy fallback.
 */
class UnboundPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request()
            val url = request.url.toString()
            val builder = request.newBuilder()
            if (url.contains("googlevideo.com")) {
                if (url.contains("c=WEB_REMIX")) {
                    builder.header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    builder.header("Referer", "https://music.youtube.com/")
                    builder.header("Origin", "https://music.youtube.com")
                } else if (url.contains("c=TVHTML5")) {
                    builder.header("User-Agent", "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15")
                } else {
                    builder.header("User-Agent", "com.google.ios.youtube/20.08.3 (iPhone16,2; U; CPU iOS 18_3_1 like Mac OS X;)")
                }
            }
            chain.proceed(builder.build())
        }
        .build()

    companion object {
        private const val TAG = "UnboundPlaybackService"
        const val NOTIFICATION_CHANNEL_ID = "unbound_media_playback"
        const val NOTIFICATION_ID = 45731

        @Volatile
        var activeEqualizerCurve: EqualizerCurve = EqualizerCurve.FLAT

        @Volatile
        var activeSleepFadeGain: Float = 1.0f

        val crossfadeProcessor = CrossfadeFilterAudioProcessor()
    }

    private val equalizerProcessor = EqualizerAudioProcessor { activeEqualizerCurve }
    private val sleepFadeProcessor = SleepFadeAudioProcessor { activeSleepFadeGain }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing Unbound Playback Service with SimpMusic audio streaming pipeline...")

        // 0. Set up Foreground Notification Channel and MediaNotificationProvider (matching SimpMusic)
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Unbound Music Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Foreground playback notification for Unbound Music"
                setShowBadge(false)
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
            }
            notificationManager?.createNotificationChannel(channel)
        }

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                NOTIFICATION_CHANNEL_ID,
                R.string.app_name
            ).apply {
                setSmallIcon(R.mipmap.ic_launcher)
            }
        )

        // 1. Universal ExtractorsFactory: supports WebM Opus, MP4 AAC, Ogg, MP3, FLAC, WAV with CBR seeking
        val extractorsFactory = DefaultExtractorsFactory().setConstantBitrateSeekingEnabled(true)

        // 2. SimpMusic DataSourceFactory with mobile User-Agent and redirect support
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        // 3. SimpMusic RenderersFactory with robust AudioSink chain (SimpMusic reference)
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput) // Pass system capability (supports float PCM from Opus decoder)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            equalizerProcessor,
                            sleepFadeProcessor,
                            crossfadeProcessor
                        )
                    )
                    .build()
            }
        }

        // 4. Aggressive LoadControl buffer (SimpMusic download-while-listening pattern):
        // Buffers up to 15 minutes ahead so the whole song rapidly pre-caches within seconds.
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                360_000, // minBufferMs: 6 minutes
                900_000, // maxBufferMs: 15 minutes (full track pre-buffering)
                1_500,   // bufferForPlaybackMs: 1.5s fast start
                3_000    // bufferForPlaybackAfterRebufferMs: 3.0s rebuffer start
            )
            .setBackBuffer(120_000, /* retainBackBufferFromKeyframe = */ true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        // 5. ExoPlayer instance with C.AUDIO_CONTENT_TYPE_MUSIC and handleAudioFocus = true (requests AUDIOFOCUS_GAIN from AudioManager for audio routing)
        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                /* handleAudioFocus = */ true
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        exoPlayer?.volume = 1.0f

        exoPlayer?.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.i(TAG, "onMediaItemTransition: title=${mediaItem?.mediaMetadata?.title}, id=${mediaItem?.mediaId}, uri=${mediaItem?.localConfiguration?.uri}")
                lastFailedMediaId = null
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "STATE_IDLE"
                    Player.STATE_BUFFERING -> "STATE_BUFFERING"
                    Player.STATE_READY -> "STATE_READY"
                    Player.STATE_ENDED -> "STATE_ENDED"
                    else -> "STATE_$playbackState"
                }
                Log.i(TAG, "Playback state: $stateName, playWhenReady=${exoPlayer?.playWhenReady}, isPlaying=${exoPlayer?.isPlaying}, volume=${exoPlayer?.volume}")
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                Log.i(TAG, "onIsPlayingChanged: isPlaying=$isPlaying")
            }

            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId != C.AUDIO_SESSION_ID_UNSET && audioSessionId != 0) {
                    AudioEffectController.attachAudioSession(audioSessionId)
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error: code=${error.errorCode}, name=${error.errorCodeName}, message=${error.message}", error)
                val isNetworkError = error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ||
                                     error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
                                     error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED
                if (isNetworkError) {
                    Log.w(TAG, "Network playback error detected (${error.errorCodeName}), executing resilient proxy stream fallback...")
                }
                fallbackToProxyStream(error)
            }
        })

        val initialSessionId = exoPlayer?.audioSessionId ?: C.AUDIO_SESSION_ID_UNSET
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET && initialSessionId != 0) {
            AudioEffectController.attachAudioSession(initialSessionId)
        }

        // Activity intent for notification tap -> open app
        val activityIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            activityIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // MediaSession for system integration
        mediaSession = MediaSession.Builder(this, exoPlayer!!)
            .setSessionActivity(pendingIntent)
            .setCallback(UnboundMediaSessionCallback())
            .build()

        Log.i(TAG, "Unbound Playback Service successfully initialized.")
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.i(TAG, "Destroying Unbound Playback Service.")
        serviceScope.cancel()
        AudioEffectController.release()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    data class DetailedErrorInfo(
        val errorCodeName: String,
        val summary: String,
        val statusCode: Int?,
        val serverBody: String?,
        val failedUri: String?
    )

    private fun extractDetailedErrorInfo(error: PlaybackException?): DetailedErrorInfo {
        val codeName = error?.errorCodeName ?: "UNKNOWN_ERROR"
        var statusCode: Int? = null
        var serverBody: String? = null
        var failedUri: String? = null
        var detailedSummary = error?.message ?: "Playback stream interrupted"

        var current: Throwable? = error
        while (current != null) {
            if (current is HttpDataSource.InvalidResponseCodeException) {
                statusCode = current.responseCode
                val rawBody = current.responseBody
                if (rawBody.isNotEmpty()) {
                    val bodyStr = String(rawBody, Charsets.UTF_8).trim()
                    serverBody = bodyStr
                }
                failedUri = current.dataSpec.uri.toString()
                val respMsg = current.responseMessage
                detailedSummary = if (!respMsg.isNullOrBlank()) "HTTP $statusCode ($respMsg)" else "HTTP $statusCode"
                break
            } else if (current is HttpDataSource.HttpDataSourceException) {
                failedUri = current.dataSpec.uri.toString()
                detailedSummary = current.message ?: "HTTP Data Source Error"
            }
            current = current.cause
        }

        return DetailedErrorInfo(
            errorCodeName = codeName,
            summary = detailedSummary,
            statusCode = statusCode,
            serverBody = serverBody,
            failedUri = failedUri
        )
    }

    private var lastFailedMediaId: String? = null

    /**
     * Seamlessly recovers from stream errors by switching between the direct YouTube CDN stream
     * and the local embedded Go daemon streaming proxy on 127.0.0.1, showing full diagnostics.
     */
    private fun fallbackToProxyStream(originalError: PlaybackException? = null) {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem ?: return
        val currentPos = player.currentPosition
        val mediaId = currentItem.mediaId
        val title = currentItem.mediaMetadata.title?.toString() ?: mediaId
        val artist = currentItem.mediaMetadata.artist?.toString() ?: ""
        val currentUri = currentItem.localConfiguration?.uri?.toString() ?: ""

        val info = extractDetailedErrorInfo(originalError)
        val errCode = if (info.statusCode != null) "HTTP_${info.statusCode}" else info.errorCodeName
        val errMsg = info.summary

        if (mediaId == lastFailedMediaId) {
            Log.w(TAG, "Stream fallback already attempted for $mediaId, halting to avoid loop.")
            val uriSnippet = currentUri
            val fullReport = buildString {
                append("Playback Failed Completely:\n")
                append("Track: '$title'\n")
                append("Status: ${if (info.statusCode != null) "HTTP ${info.statusCode}" else info.errorCodeName}\n")
                append("Cause: $errMsg\n")
                if (!info.serverBody.isNullOrBlank()) {
                    append("Body: ${info.serverBody}\n")
                }
                append("URI: $uriSnippet")
            }
            Log.e(TAG, fullReport)
            com.cubicreates.unboundmusic.util.UnboundToast.show(
                applicationContext,
                fullReport,
                isLong = true
            )
            return
        }
        lastFailedMediaId = mediaId

        if (mediaId.isBlank() || mediaId.startsWith("local:") || mediaId.startsWith("file://") || mediaId.startsWith("content://")) {
            com.cubicreates.unboundmusic.util.UnboundToast.show(
                applicationContext,
                "Playback Error ($errCode):\n$errMsg\nCannot stream local track: $mediaId"
            )
            return
        }

        serviceScope.launch(Dispatchers.IO) {
            val isCurrentUriProxy = currentUri.contains("127.0.0.1") || currentUri.contains("localhost")
            var fallbackTargetDesc = ""
            var fallbackUrl = ""
            var fallbackErrorReason = ""

            if (isCurrentUriProxy) {
                // Localhost proxy failed -> try querying daemon for direct signed stream URL
                fallbackTargetDesc = "Direct Remote CDN"
                try {
                    val query = if (mediaId.isNotBlank() && mediaId.length == 11 && !mediaId.contains(" ")) {
                        "id=${URLEncoder.encode(mediaId, "UTF-8")}"
                    } else {
                        "title=${URLEncoder.encode(title, "UTF-8")}&artist=${URLEncoder.encode(artist, "UTF-8")}"
                    }
                    val client = okhttp3.OkHttpClient()
                    val req = okhttp3.Request.Builder()
                        .url("http://127.0.0.1:45731/api/v1/stream?$query")
                        .build()
                    val resp = client.newCall(req).execute()
                    val body = resp.body?.string() ?: ""
                    if (resp.isSuccessful && body.isNotBlank()) {
                        val json = JSONObject(body)
                        val direct = json.optString("direct_stream_url", "")
                        fallbackUrl = if (direct.isNotBlank()) direct else json.optString("stream_url", "")
                        if (fallbackUrl.isBlank()) {
                            fallbackErrorReason = "Engine returned no stream URL: $body"
                        }
                    } else {
                        fallbackErrorReason = "Engine returned HTTP ${resp.code}: $body"
                    }
                } catch (e: Exception) {
                    fallbackErrorReason = "Cannot connect to engine at 127.0.0.1:45731: ${e.message}"
                }
            } else {
                // Direct YouTube stream failed -> switch to embedded engine proxy
                fallbackTargetDesc = "Localhost Engine Proxy"
                val proxyId = if (mediaId.isNotBlank() && mediaId.length == 11 && !mediaId.contains(" ")) {
                    mediaId
                } else if (title.isNotBlank()) {
                    if (artist.isNotBlank()) "$title $artist" else title
                } else {
                    ""
                }

                if (proxyId.isNotBlank()) {
                    fallbackUrl = "http://127.0.0.1:45731/api/v1/proxy/stream?id=${URLEncoder.encode(proxyId, "UTF-8")}"
                } else {
                    fallbackErrorReason = "No video ID or title available for proxy resolution."
                }
            }

            if (fallbackUrl.isNotBlank() && fallbackUrl != currentUri) {
                withContext(Dispatchers.Main) {
                    val fromSource = if (isCurrentUriProxy) "Localhost Engine Proxy" else "Direct YouTube CDN"
                    Log.i(TAG, "Stream failed on $fromSource ($errCode). Falling back to $fallbackTargetDesc: $fallbackUrl")
                    val notice = buildString {
                        append("Stream Failed on $fromSource")
                        if (info.statusCode != null) {
                            append(" (HTTP ${info.statusCode})")
                        } else {
                            append(" ($errCode)")
                        }
                        append(":\n")
                        append("Cause: $errMsg\n")
                        if (!info.serverBody.isNullOrBlank()) {
                            append("Body: ${info.serverBody}\n")
                        }
                        append("-> Falling back to $fallbackTargetDesc")
                    }
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        applicationContext,
                        notice,
                        isLong = false
                    )
                    val uri = Uri.parse(fallbackUrl)
                    val newItem = currentItem.buildUpon()
                        .setUri(uri)
                        .setRequestMetadata(
                            currentItem.requestMetadata.buildUpon()
                                .setMediaUri(uri)
                                .build()
                        )
                        .build()
                    player.setMediaItem(newItem, currentPos)
                    player.prepare()
                    player.play()
                }
            } else {
                withContext(Dispatchers.Main) {
                    val uriSnippet = currentUri
                    val fullReport = buildString {
                        append("Playback Error ($errCode):\n")
                        append("Track: '$title'\n")
                        append("Failed URL: $uriSnippet\n")
                        append("Cause: $errMsg\n")
                        if (!info.serverBody.isNullOrBlank()) {
                            append("Body: ${info.serverBody}\n")
                        }
                        if (fallbackErrorReason.isNotBlank()) {
                            append("Fallback Failed: $fallbackErrorReason")
                        } else {
                            append("Fallback stream could not be constructed.")
                        }
                    }
                    Log.e(TAG, fullReport)
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        applicationContext,
                        fullReport,
                        isLong = true
                    )
                }
            }
        }
    }

    private inner class UnboundMediaSessionCallback : MediaSession.Callback {
        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): com.google.common.util.concurrent.ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Log.i(TAG, "onSetMediaItems received: ${mediaItems.size} items, startIndex=$startIndex")
            val resolved = mediaItems.map { resolveMediaItem(it) }
            return com.google.common.util.concurrent.Futures.immediateFuture(
                MediaSession.MediaItemsWithStartPosition(resolved, startIndex, startPositionMs)
            )
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
            Log.i(TAG, "onAddMediaItems received: ${mediaItems.size} items")
            val resolved = mediaItems.map { resolveMediaItem(it) }.toMutableList()
            return com.google.common.util.concurrent.Futures.immediateFuture(resolved)
        }

        private fun resolveMediaItem(item: MediaItem): MediaItem {
            val candidateUri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
            val mediaId = item.mediaId

            val finalUri: Uri? = when {
                // If candidate URI is already provided and non-blank (direct googlevideo.com, localhost proxy, or file/content URI), use it directly!
                candidateUri != null && candidateUri.toString().isNotBlank() -> {
                    Log.i(TAG, "Playing provided stream URI for $mediaId: $candidateUri")
                    candidateUri
                }
                // If mediaId is an 11-char YouTube ID and no URI was provided, route through localhost proxy
                mediaId.isNotBlank() && mediaId.length == 11 && !mediaId.startsWith("local:") -> {
                    Log.i(TAG, "Routing track $mediaId through localhost streaming proxy")
                    Uri.parse("http://127.0.0.1:45731/api/v1/proxy/stream?id=${URLEncoder.encode(mediaId, "UTF-8")}")
                }
                mediaId.startsWith("http://") || mediaId.startsWith("https://") || mediaId.startsWith("file://") || mediaId.startsWith("content://") -> Uri.parse(mediaId)
                else -> null
            }

            return if (finalUri != null) {
                item.buildUpon()
                    .setUri(finalUri)
                    .setRequestMetadata(
                        item.requestMetadata.buildUpon()
                            .setMediaUri(finalUri)
                            .build()
                    )
                    .build()
            } else {
                Log.w(TAG, "resolveMediaItem: Unable to determine URI for item ${item.mediaId}")
                item
            }
        }
    }
}
