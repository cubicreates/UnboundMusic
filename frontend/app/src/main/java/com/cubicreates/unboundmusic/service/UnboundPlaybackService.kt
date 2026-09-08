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

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.media3.extractor.ExtractorsFactory
import androidx.media3.extractor.flac.FlacExtractor
import androidx.media3.extractor.mkv.MatroskaExtractor
import androidx.media3.extractor.mp4.FragmentedMp4Extractor
import androidx.media3.extractor.mp4.Mp4Extractor
import androidx.media3.extractor.text.DefaultSubtitleParserFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cubicreates.unboundmusic.MainActivity
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
        .build()

    companion object {
        private const val TAG = "UnboundPlaybackService"

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

        // 1. SimpMusic ExtractorsFactory supporting WebM/Matroska (Opus), MP4/M4A (AAC), and FLAC
        val extractorsFactory = ExtractorsFactory {
            arrayOf(
                FlacExtractor(FlacExtractor.FLAG_DISABLE_ID3_METADATA),
                MatroskaExtractor(DefaultSubtitleParserFactory()),
                FragmentedMp4Extractor(DefaultSubtitleParserFactory()),
                Mp4Extractor(DefaultSubtitleParserFactory()),
            )
        }

        // 2. SimpMusic DataSourceFactory with modern mobile User-Agent and redirect support
        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)

        // 3. SimpMusic RenderersFactory with float output support and Sonic/Silence audio processors
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            emptyArray(),
                            SilenceSkippingAudioProcessor(
                                2_000_000,
                                (20_000 / 2_000_000).toFloat(),
                                2_000_000,
                                0,
                                256
                            ),
                            SonicAudioProcessor()
                        )
                    ).build()
        }

        // 4. SimpMusic LoadControl: bufferForPlaybackMs = 0 -> audio starts IMMEDIATELY without lag
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                DEFAULT_MIN_BUFFER_MS * 4,
                DEFAULT_MAX_BUFFER_MS * 4,
                0,
                0
            ).build()

        // 5. ExoPlayer instance with C.AUDIO_CONTENT_TYPE_MUSIC and handleAudioFocus = true
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
                lastRehydratedMediaId = null
                rehydrateAttempts = 0
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
                Log.w(TAG, "Playback error encountered: ${error.errorCodeName} (code=${error.errorCode}) - ${error.message}", error)
                rehydrateExpiredStream()
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

    private var lastRehydratedMediaId: String? = null
    private var rehydrateAttempts: Int = 0

    private fun rehydrateExpiredStream() {
        val player = exoPlayer ?: return
        val currentItem = player.currentMediaItem ?: return
        val currentPos = player.currentPosition
        val mediaId = currentItem.mediaId
        val trackTitle = currentItem.mediaMetadata.title?.toString() ?: ""
        val trackArtist = currentItem.mediaMetadata.artist?.toString() ?: ""

        if (lastRehydratedMediaId == mediaId && rehydrateAttempts >= 2) {
            Log.w(TAG, "Already attempted rehydration twice for $mediaId. Halting to prevent loop.")
            return
        }
        lastRehydratedMediaId = mediaId
        rehydrateAttempts++

        Log.i(TAG, "Re-hydrating stream for '$trackTitle' ($mediaId) at $currentPos ms (attempt $rehydrateAttempts)...")

        serviceScope.launch(Dispatchers.IO) {
            try {
                // If attempt 2, or if direct resolution previously failed, use localhost proxy stream directly
                if (rehydrateAttempts >= 2 && mediaId.isNotBlank() && mediaId.length == 11 && !mediaId.startsWith("local:")) {
                    val proxyUrl = "http://127.0.0.1:45731/api/v1/proxy/stream?id=${URLEncoder.encode(mediaId, "UTF-8")}"
                    withContext(Dispatchers.Main) {
                        val uri = Uri.parse(proxyUrl)
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
                        Log.i(TAG, "Switched to resilient localhost proxy stream for $mediaId at $currentPos ms.")
                    }
                    return@launch
                }

                val targetUrl = if (mediaId.isNotBlank() && !mediaId.startsWith("http") && !mediaId.contains(" ")) {
                    "http://127.0.0.1:45731/api/v1/stream?id=${URLEncoder.encode(mediaId, "UTF-8")}"
                } else {
                    "http://127.0.0.1:45731/api/v1/stream?title=${URLEncoder.encode(trackTitle, "UTF-8")}&artist=${URLEncoder.encode(trackArtist, "UTF-8")}"
                }

                val req = Request.Builder().url(targetUrl).build()
                val resp = okHttpClient.newCall(req).execute()
                var resolvedUrl = ""
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val json = JSONObject(body)
                    resolvedUrl = json.optString("stream_url", "")
                }

                if (resolvedUrl.isBlank() && mediaId.isNotBlank() && mediaId.length == 11 && !mediaId.startsWith("local:")) {
                    resolvedUrl = "http://127.0.0.1:45731/api/v1/proxy/stream?id=${URLEncoder.encode(mediaId, "UTF-8")}"
                }

                if (resolvedUrl.isNotBlank()) {
                    withContext(Dispatchers.Main) {
                        val uri = Uri.parse(resolvedUrl)
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
                        Log.i(TAG, "Stream re-hydration successful. Resumed playback at $currentPos ms.")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to re-hydrate stream: ${e.message}")
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
                candidateUri != null -> candidateUri
                mediaId.startsWith("http://") || mediaId.startsWith("https://") || mediaId.startsWith("file://") || mediaId.startsWith("content://") -> Uri.parse(mediaId)
                mediaId.isNotBlank() && mediaId.length == 11 && !mediaId.startsWith("local:") -> {
                    Log.i(TAG, "Resolving bare YouTube mediaId ($mediaId) to localhost streaming proxy")
                    Uri.parse("http://127.0.0.1:45731/api/v1/proxy/stream?id=${URLEncoder.encode(mediaId, "UTF-8")}")
                }
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
