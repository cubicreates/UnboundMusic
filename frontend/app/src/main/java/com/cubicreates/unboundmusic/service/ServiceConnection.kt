/*
 * Package: com.cubicreates.unboundmusic.service
 * File: ServiceConnection.kt
 * Purpose: MediaController connection manager bridging UI to UnboundPlaybackService.
 *          Manages queue reordering, 5 playback modes (including Reverse Play), and DSP curve routing.
 * Subsystem: Audio Playback Bridge
 * Concurrency: Thread-safe singleton. Callbacks dispatch on main thread.
 */

package com.cubicreates.unboundmusic.service

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.cubicreates.unboundmusic.audio.BiquadFilter
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 5 Supported Playback Modes.
 */
enum class PlaybackMode {
    NORMAL,
    LOOP_ALL,
    LOOP_ONE,
    SHUFFLE,
    REVERSE_PLAY
}

/**
 * Represents the current state of audio playback.
 */
data class PlaybackUiState(
    val currentTrack: TrackItem? = null,
    val isPlaying: Boolean = false,
    val currentPositionMs: Long = 0,
    val durationMs: Long = 0,
    val progress: Float = 0f,
    val formattedPosition: String = "0:00",
    val formattedRemaining: String = "-0:00",
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffleModeEnabled: Boolean = false,
    val playbackMode: PlaybackMode = PlaybackMode.NORMAL,
    val hasNext: Boolean = false,
    val hasPrevious: Boolean = false,
    val mediaItemCount: Int = 0,
    val queue: List<TrackItem> = emptyList()
)

/**
 * Singleton managing MediaController connection to UnboundPlaybackService.
 */
class ServiceConnection private constructor(private val context: Context) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val _playbackState = MutableStateFlow(PlaybackUiState())
    val playbackState: StateFlow<PlaybackUiState> = _playbackState.asStateFlow()

    private var currentMode = PlaybackMode.NORMAL
    private var originalQueue = mutableListOf<TrackItem>()

    companion object {
        private const val TAG = "ServiceConnection"

        @Volatile
        private var instance: ServiceConnection? = null

        fun getInstance(context: Context): ServiceConnection {
            return instance ?: synchronized(this) {
                instance ?: ServiceConnection(context.applicationContext).also { instance = it }
            }
        }

        fun formatTime(ms: Long): String {
            val totalSeconds = (ms / 1000).coerceAtLeast(0)
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return String.format("%d:%02d", minutes, seconds)
        }
    }

    fun connect() {
        if (controller != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, UnboundPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                controller = controllerFuture?.get()
                controller?.addListener(playerListener)
                syncState()
                Log.i(TAG, "MediaController connected to UnboundPlaybackService.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed connecting MediaController: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    fun disconnect() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        controllerFuture = null
    }

    // --- Audio DSP Controls ---

    fun setEqualizerCurve(curve: EqualizerCurve) {
        UnboundPlaybackService.activeEqualizerCurve = curve
    }

    fun setSleepFadeGain(gain: Float) {
        UnboundPlaybackService.activeSleepFadeGain = gain.coerceIn(0f, 1f)
    }

    fun setCrossfade(enabled: Boolean, cutoffHz: Float = 20000f, type: BiquadFilter.FilterType = BiquadFilter.FilterType.LOW_PASS) {
        UnboundPlaybackService.crossfadeProcessor.enabled = enabled
        UnboundPlaybackService.crossfadeProcessor.cutoffFrequencyHz = cutoffHz
        UnboundPlaybackService.crossfadeProcessor.filterType = type
    }

    // --- Playback Commands ---

    fun playTrack(track: TrackItem, streamUrl: String? = null) {
        val targetUrl = streamUrl ?: track.streamUrl
        if (targetUrl.isBlank()) {
            Log.w(TAG, "No stream URL available for track: ${track.title}")
            return
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.title)
            .setUri(Uri.parse(targetUrl))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(
                        if (track.coverUrl.isNotBlank()) Uri.parse(track.coverUrl) else null
                    )
                    .build()
            )
            .build()

        originalQueue.clear()
        originalQueue.add(track)

        controller?.apply {
            setMediaItem(mediaItem)
            prepare()
            play()
        }
    }

    fun playQueue(tracks: List<TrackItem>, startIndex: Int = 0) {
        originalQueue = tracks.toMutableList()
        val mediaItems = tracks.mapNotNull { track ->
            val url = track.streamUrl
            if (url.isBlank()) return@mapNotNull null
            MediaItem.Builder()
                .setMediaId(track.title)
                .setUri(Uri.parse(url))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(track.title)
                        .setArtist(track.artist)
                        .setArtworkUri(
                            if (track.coverUrl.isNotBlank()) Uri.parse(track.coverUrl) else null
                        )
                        .build()
                )
                .build()
        }

        controller?.apply {
            setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
            prepare()
            play()
        }
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        currentMode = mode
        val ctrl = controller ?: return
        when (mode) {
            PlaybackMode.NORMAL -> {
                ctrl.repeatMode = Player.REPEAT_MODE_OFF
                ctrl.shuffleModeEnabled = false
            }
            PlaybackMode.LOOP_ALL -> {
                ctrl.repeatMode = Player.REPEAT_MODE_ALL
                ctrl.shuffleModeEnabled = false
            }
            PlaybackMode.LOOP_ONE -> {
                ctrl.repeatMode = Player.REPEAT_MODE_ONE
                ctrl.shuffleModeEnabled = false
            }
            PlaybackMode.SHUFFLE -> {
                ctrl.repeatMode = Player.REPEAT_MODE_OFF
                ctrl.shuffleModeEnabled = true
            }
            PlaybackMode.REVERSE_PLAY -> {
                ctrl.repeatMode = Player.REPEAT_MODE_OFF
                ctrl.shuffleModeEnabled = false
                // Reverse the playlist order
                if (originalQueue.size > 1) {
                    val reversed = originalQueue.reversed()
                    playQueue(reversed, 0)
                }
            }
        }
        syncState()
    }

    fun cyclePlaybackMode() {
        val nextMode = when (currentMode) {
            PlaybackMode.NORMAL -> PlaybackMode.LOOP_ALL
            PlaybackMode.LOOP_ALL -> PlaybackMode.LOOP_ONE
            PlaybackMode.LOOP_ONE -> PlaybackMode.SHUFFLE
            PlaybackMode.SHUFFLE -> PlaybackMode.REVERSE_PLAY
            PlaybackMode.REVERSE_PLAY -> PlaybackMode.NORMAL
        }
        setPlaybackMode(nextMode)
    }

    fun togglePlayPause() {
        controller?.let { ctrl ->
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun seekToFraction(fraction: Float) {
        controller?.let { ctrl ->
            val targetMs = (ctrl.duration * fraction.coerceIn(0f, 1f)).toLong()
            ctrl.seekTo(targetMs)
        }
    }

    fun next() {
        controller?.seekToNextMediaItem()
    }

    fun previous() {
        controller?.seekToPreviousMediaItem()
    }

    fun stop() {
        controller?.stop()
    }

    // --- State Sync ---

    private fun syncState() {
        val ctrl = controller ?: return
        val metadata = ctrl.mediaMetadata
        val duration = ctrl.duration.coerceAtLeast(0)
        val position = ctrl.currentPosition.coerceAtLeast(0)
        val frac = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        val remaining = (duration - position).coerceAtLeast(0)

        val queueList = mutableListOf<TrackItem>()
        for (i in 0 until ctrl.mediaItemCount) {
            val item = ctrl.getMediaItemAt(i)
            val meta = item.mediaMetadata
            queueList.add(
                TrackItem(
                    title = meta.title?.toString() ?: "Track ${i + 1}",
                    artist = meta.artist?.toString() ?: "Artist",
                    coverUrl = meta.artworkUri?.toString() ?: "",
                    streamUrl = item.localConfiguration?.uri?.toString() ?: ""
                )
            )
        }

        _playbackState.value = PlaybackUiState(
            currentTrack = TrackItem(
                title = metadata.title?.toString() ?: "Unknown",
                artist = metadata.artist?.toString() ?: "Unknown Artist",
                coverUrl = metadata.artworkUri?.toString() ?: "",
                streamUrl = ctrl.currentMediaItem?.localConfiguration?.uri?.toString() ?: ""
            ),
            isPlaying = ctrl.isPlaying,
            currentPositionMs = position,
            durationMs = duration,
            progress = frac,
            formattedPosition = formatTime(position),
            formattedRemaining = "-${formatTime(remaining)}",
            repeatMode = ctrl.repeatMode,
            shuffleModeEnabled = ctrl.shuffleModeEnabled,
            playbackMode = currentMode,
            hasNext = ctrl.hasNextMediaItem(),
            hasPrevious = ctrl.hasPreviousMediaItem(),
            mediaItemCount = ctrl.mediaItemCount,
            queue = if (queueList.isNotEmpty()) queueList else originalQueue
        )
    }

    fun updatePosition() {
        val ctrl = controller ?: return
        if (!ctrl.isPlaying && !ctrl.isLoading) return

        val duration = ctrl.duration.coerceAtLeast(0)
        val position = ctrl.currentPosition.coerceAtLeast(0)
        val frac = if (duration > 0) (position.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f
        val remaining = (duration - position).coerceAtLeast(0)

        _playbackState.value = _playbackState.value.copy(
            currentPositionMs = position,
            durationMs = duration,
            progress = frac,
            formattedPosition = formatTime(position),
            formattedRemaining = "-${formatTime(remaining)}"
        )
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = syncState()
        override fun onPlaybackStateChanged(playbackState: Int) = syncState()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncState()
        override fun onRepeatModeChanged(repeatMode: Int) = syncState()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = syncState()
    }
}
