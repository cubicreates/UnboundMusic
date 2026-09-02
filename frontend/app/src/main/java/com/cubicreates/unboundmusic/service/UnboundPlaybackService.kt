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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.cubicreates.unboundmusic.MainActivity
import com.cubicreates.unboundmusic.audio.CrossfadeFilterAudioProcessor
import com.cubicreates.unboundmusic.audio.EqualizerAudioProcessor
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.audio.SleepFadeAudioProcessor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Foreground playback service using Media3 ExoPlayer and MediaSession.
 * Provides persistent notification with playback controls, lockscreen integration,
 * Bluetooth AVRCP, and custom audio DSP pipeline.
 */
class UnboundPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

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
        Log.i(TAG, "Initializing Unbound Playback Service with Media3 ExoPlayer & DSP Pipeline...")

        // OkHttp client for streaming remote audio with connection pooling
        val okHttpClient = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

        val okHttpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(this, okHttpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)

        // Custom RenderersFactory with 10-band Equalizer, Sleep Fade, and Crossfade processors
        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink? {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(
                        arrayOf(equalizerProcessor, sleepFadeProcessor, crossfadeProcessor)
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        // ExoPlayer with audio attributes, DSP renderers, and network wake lock
        exoPlayer = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
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

        Log.i(TAG, "Unbound Playback Service initialized with Pro Audio DSP Pipeline.")
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
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        exoPlayer = null
        super.onDestroy()
    }

    private inner class UnboundMediaSessionCallback : MediaSession.Callback {
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): com.google.common.util.concurrent.ListenableFuture<MutableList<MediaItem>> {
            val resolvedItems = mediaItems.map { item ->
                val uri = item.requestMetadata.mediaUri ?: item.localConfiguration?.uri
                if (uri != null) {
                    item.buildUpon()
                        .setUri(uri)
                        .build()
                } else {
                    item
                }
            }.toMutableList()

            return com.google.common.util.concurrent.Futures.immediateFuture(resolvedItems)
        }
    }
}
