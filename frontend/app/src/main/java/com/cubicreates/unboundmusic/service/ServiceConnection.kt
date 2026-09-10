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
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
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
    private val mainHandler = Handler(Looper.getMainLooper())

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
        }, ContextCompat.getMainExecutor(context))
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

    fun isPlayerReady(): Boolean = controller != null && (controller?.mediaItemCount ?: 0) > 0

    // --- Playback Commands ---

    fun playTrack(track: TrackItem, streamUrl: String? = null) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { playTrack(track, streamUrl) }
            return
        }

        val targetUrl = streamUrl ?: track.streamUrl
        if (targetUrl.isBlank()) {
            Log.w(TAG, "No stream URL available for track: ${track.title}")
            com.cubicreates.unboundmusic.util.UnboundToast.show(context, "Playback Error: No stream URL for '${track.title}'")
            return
        }

        val uri = try {
            Uri.parse(targetUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Invalid stream URI for track: ${track.title} ($targetUrl): ${e.message}")
            com.cubicreates.unboundmusic.util.UnboundToast.show(context, "Playback Error: Invalid URI for '${track.title}': ${e.message}")
            return
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https" && scheme != "file" && scheme != "content") {
            Log.e(TAG, "Unsupported or non-audio URI scheme '$scheme' for track: ${track.title} ($targetUrl)")
            com.cubicreates.unboundmusic.util.UnboundToast.show(context, "Playback Error: Unsupported URI scheme '$scheme' for '${track.title}'")
            return
        }

        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.ifBlank { track.title })
            .setUri(uri)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(uri)
                    .build()
            )
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

        val ctrl = controller
        if (ctrl == null) {
            Log.w(TAG, "MediaController not ready yet, connecting and deferring playTrack...")
            connect()
            controllerFuture?.addListener({
                try {
                    val activeCtrl = controller ?: controllerFuture?.get()
                    activeCtrl?.apply {
                        setMediaItem(mediaItem)
                        prepare()
                        play()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Deferred playTrack failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))
            return
        }

        ctrl.setMediaItem(mediaItem)
        ctrl.prepare()
        ctrl.play()
    }

    fun playQueue(tracks: List<TrackItem>, startIndex: Int = 0) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { playQueue(tracks, startIndex) }
            return
        }

        originalQueue = tracks.toMutableList()
        val mediaItems = tracks.mapNotNull { track ->
            val url = track.streamUrl
            if (url.isBlank()) return@mapNotNull null
            val uri = try {
                Uri.parse(url)
            } catch (_: Exception) {
                return@mapNotNull null
            }
            val scheme = uri.scheme?.lowercase()
            if (scheme != "http" && scheme != "https" && scheme != "file" && scheme != "content") {
                return@mapNotNull null
            }
            MediaItem.Builder()
                .setMediaId(track.id.ifBlank { track.title })
                .setUri(uri)
                .setRequestMetadata(
                    MediaItem.RequestMetadata.Builder()
                        .setMediaUri(uri)
                        .build()
                )
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

        val ctrl = controller
        if (ctrl == null) {
            Log.w(TAG, "MediaController not ready yet, connecting and deferring playQueue...")
            connect()
            controllerFuture?.addListener({
                try {
                    val activeCtrl = controller ?: controllerFuture?.get()
                    activeCtrl?.apply {
                        setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
                        prepare()
                        play()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Deferred playQueue failed: ${e.message}")
                }
            }, ContextCompat.getMainExecutor(context))
            return
        }

        ctrl.setMediaItems(mediaItems, startIndex, C.TIME_UNSET)
        ctrl.prepare()
        ctrl.play()
    }

    fun setQueue(tracks: List<TrackItem>) {
        originalQueue = tracks.toMutableList()
        syncState()
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
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { togglePlayPause() }
            return
        }
        controller?.let { ctrl ->
            if (ctrl.mediaItemCount == 0) {
                Log.w(TAG, "Cannot togglePlayPause: ExoPlayer timeline has 0 media items.")
                return
            }
            if (ctrl.isPlaying) ctrl.pause() else ctrl.play()
        }
    }

    fun seekTo(positionMs: Long) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { seekTo(positionMs) }
            return
        }
        controller?.seekTo(positionMs)
    }

    fun seekToFraction(fraction: Float) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { seekToFraction(fraction) }
            return
        }
        controller?.let { ctrl ->
            val targetMs = (ctrl.duration * fraction.coerceIn(0f, 1f)).toLong()
            ctrl.seekTo(targetMs)
        }
    }

    fun next() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { next() }
            return
        }
        val ctrl = controller
        if (ctrl != null && ctrl.hasNextMediaItem()) {
            ctrl.seekToNextMediaItem()
        } else {
            onSkipToNextListener?.invoke()
        }
    }

    fun previous() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { previous() }
            return
        }
        val ctrl = controller
        if (ctrl != null && ctrl.hasPreviousMediaItem()) {
            ctrl.seekToPreviousMediaItem()
        } else {
            onSkipToPreviousListener?.invoke()
        }
    }

    fun stop() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mainHandler.post { stop() }
            return
        }
        controller?.stop()
    }

    // --- Queue Management ---

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val ctrl = controller ?: return
        if (fromIndex in 0 until ctrl.mediaItemCount && toIndex in 0 until ctrl.mediaItemCount && fromIndex != toIndex) {
            ctrl.moveMediaItem(fromIndex, toIndex)
            if (fromIndex in originalQueue.indices && toIndex in originalQueue.indices) {
                val item = originalQueue.removeAt(fromIndex)
                originalQueue.add(toIndex, item)
            }
            syncState()
        }
    }

    fun removeQueueItem(index: Int) {
        val ctrl = controller ?: return
        if (index in 0 until ctrl.mediaItemCount) {
            ctrl.removeMediaItem(index)
            if (index in originalQueue.indices) {
                originalQueue.removeAt(index)
            }
            syncState()
        }
    }

    fun insertNext(track: TrackItem) {
        val ctrl = controller ?: return
        val url = if (track.streamUrl.isNotBlank() && !track.streamUrl.contains("/api/v1/stream?")) {
            track.streamUrl
        } else if (track.id.isNotBlank() && !track.id.startsWith("local:")) {
            "http://127.0.0.1:45731/api/v1/proxy/stream?id=${track.id}"
        } else {
            track.streamUrl
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.ifBlank { track.title })
            .setUri(Uri.parse(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(if (track.coverUrl.isNotBlank()) Uri.parse(track.coverUrl) else null)
                    .build()
            )
            .build()

        val nextIndex = (ctrl.currentMediaItemIndex + 1).coerceAtMost(ctrl.mediaItemCount)
        ctrl.addMediaItem(nextIndex, mediaItem)
        if (nextIndex <= originalQueue.size) {
            originalQueue.add(nextIndex, track)
        } else {
            originalQueue.add(track)
        }
        syncState()
    }

    fun addToQueue(track: TrackItem) {
        val ctrl = controller ?: return
        val url = if (track.streamUrl.isNotBlank() && !track.streamUrl.contains("/api/v1/stream?")) {
            track.streamUrl
        } else if (track.id.isNotBlank() && !track.id.startsWith("local:")) {
            "http://127.0.0.1:45731/api/v1/proxy/stream?id=${track.id}"
        } else {
            track.streamUrl
        }
        val mediaItem = MediaItem.Builder()
            .setMediaId(track.id.ifBlank { track.title })
            .setUri(Uri.parse(url))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(track.title)
                    .setArtist(track.artist)
                    .setArtworkUri(if (track.coverUrl.isNotBlank()) Uri.parse(track.coverUrl) else null)
                    .build()
            )
            .build()

        ctrl.addMediaItem(mediaItem)
        originalQueue.add(track)
        syncState()
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
            hasNext = ctrl.hasNextMediaItem() || originalQueue.size > 1 || onSkipToNextListener != null,
            hasPrevious = ctrl.hasPreviousMediaItem() || originalQueue.size > 1 || onSkipToPreviousListener != null,
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

    fun getEqualizerCurve(): EqualizerCurve {
        return UnboundPlaybackService.activeEqualizerCurve
    }

    fun setBassBoost(strength: Int) {
        AudioEffectController.setBassBoost(strength)
    }

    fun setVirtualizer(strength: Int) {
        AudioEffectController.setVirtualizer(strength)
    }

    fun setLoudness(gainMb: Int) {
        AudioEffectController.setLoudness(gainMb)
    }

    var onTrackEndedListener: (() -> Unit)? = null
    var onSkipToNextListener: (() -> Unit)? = null
    var onSkipToPreviousListener: (() -> Unit)? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = syncState()
        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) = syncState()
        override fun onPlaybackStateChanged(playbackState: Int) {
            syncState()
            if (playbackState == Player.STATE_ENDED) {
                onTrackEndedListener?.invoke()
            }
        }
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = syncState()
        override fun onRepeatModeChanged(repeatMode: Int) = syncState()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = syncState()
        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "ExoPlayer playback error: ${error.errorCodeName} (${error.errorCode}): ${error.message}", error)
            syncState()
        }
    }
}
