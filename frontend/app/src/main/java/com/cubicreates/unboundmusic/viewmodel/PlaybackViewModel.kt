/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: PlaybackViewModel.kt
 * Purpose: Dedicated ViewModel for media playback controls, queue orchestration, and stream quality.
 * Subsystem: Domain / Playback Layer (Adheres to Single Responsibility Principle)
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.cubicreates.unboundmusic.data.AudioQuality
import com.cubicreates.unboundmusic.data.PlaybackStateStore
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.service.PlaybackUiState
import com.cubicreates.unboundmusic.service.ServiceConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Domain ViewModel responsible strictly for audio playback lifecycle, queue manipulation, and DSP controls.
 */
class PlaybackViewModel(application: Application) : AndroidViewModel(application) {

    private val serviceConnection = ServiceConnection.getInstance(application)
    val playbackState: StateFlow<PlaybackUiState> = serviceConnection.playbackState

    private val _streamingQuality = MutableStateFlow(PlaybackStateStore.getStreamingQuality(application))
    val streamingQuality: StateFlow<AudioQuality> = _streamingQuality.asStateFlow()

    private val _downloadQuality = MutableStateFlow(PlaybackStateStore.getDownloadQuality(application))
    val downloadQuality: StateFlow<AudioQuality> = _downloadQuality.asStateFlow()

    fun togglePlayPause() {
        serviceConnection.togglePlayPause()
    }

    fun seekToPositionMs(positionMs: Long) {
        serviceConnection.seekTo(positionMs)
    }

    fun seekToFraction(fraction: Float) {
        val duration = playbackState.value.durationMs
        if (duration > 0) {
            val targetMs = (duration * fraction.coerceIn(0f, 1f)).toLong()
            seekToPositionMs(targetMs)
        }
    }

    fun setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
        serviceConnection.setPlaybackSpeed(speed, pitch)
    }

    fun setPlaybackMode(mode: PlaybackMode) {
        serviceConnection.setPlaybackMode(mode)
    }

    fun cyclePlaybackMode() {
        serviceConnection.cyclePlaybackMode()
    }

    fun setStreamingQuality(quality: AudioQuality) {
        _streamingQuality.value = quality
        PlaybackStateStore.setStreamingQuality(getApplication(), quality)
    }

    fun setDownloadQuality(quality: AudioQuality) {
        _downloadQuality.value = quality
        PlaybackStateStore.setDownloadQuality(getApplication(), quality)
    }
}
