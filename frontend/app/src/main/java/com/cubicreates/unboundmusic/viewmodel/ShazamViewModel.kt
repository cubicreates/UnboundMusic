/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: ShazamViewModel.kt
 * Purpose: Dedicated Domain ViewModel for 16kHz PCM audio recording, acoustic fingerprinting,
 *          and Pure-Go daemon Shazam music recognition with progressive early-exit radar.
 * Subsystem: Domain Layer / Acoustic Recognition
 * Concurrency: Thread-safe reactive StateFlow orchestration on viewModelScope.
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.audio.AmbientAudioRecorder
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.ui.components.RecognizedTrackVariant
import com.cubicreates.unboundmusic.ui.components.ShazamMode
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.util.UnboundToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Domain ViewModel encapsulating acoustic fingerprinting, progressive streaming radar,
 * and Shazam/Humming audio recognition.
 */
class ShazamViewModel(application: Application) : AndroidViewModel(application) {

    private val daemonManager = DaemonManager.getInstance(application)
    private val client = daemonManager.client

    private val _isListeningShazam = MutableStateFlow(false)
    val isListeningShazam: StateFlow<Boolean> = _isListeningShazam.asStateFlow()

    private val _shazamMode = MutableStateFlow<ShazamMode>(ShazamMode.ACOUSTIC)
    val shazamMode: StateFlow<ShazamMode> = _shazamMode.asStateFlow()

    val audioWaveAmplitude: StateFlow<Float> = AmbientAudioRecorder.audioAmplitude

    private val _recognizedMessage = MutableStateFlow("")
    val recognizedMessage: StateFlow<String> = _recognizedMessage.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0)
    val recordingDurationSeconds: StateFlow<Int> = _recordingDurationSeconds.asStateFlow()

    private val _lastRecognizedTrack = MutableStateFlow<TrackItem?>(null)
    val lastRecognizedTrack: StateFlow<TrackItem?> = _lastRecognizedTrack.asStateFlow()

    private val _recognizedVariants = MutableStateFlow<List<RecognizedTrackVariant>>(emptyList())
    val recognizedVariants: StateFlow<List<RecognizedTrackVariant>> = _recognizedVariants.asStateFlow()

    private val _shazamHistory = MutableStateFlow<List<TrackItem>>(emptyList())
    val shazamHistory: StateFlow<List<TrackItem>> = _shazamHistory.asStateFlow()

    private val _audioPermissionRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val audioPermissionRequestEvent: SharedFlow<Unit> = _audioPermissionRequestEvent.asSharedFlow()

    companion object {
        private const val TAG = "ShazamViewModel"
    }

    fun setShazamMode(mode: ShazamMode) {
        _shazamMode.value = mode
    }

    fun dismissRecognizedTrack() {
        _lastRecognizedTrack.value = null
        _recognizedVariants.value = emptyList()
        _recognizedMessage.value = ""
    }

    fun clearRecognizedMessage() {
        _recognizedMessage.value = ""
    }

    fun startAmbientShazamRecognition(onTrackRecognized: ((title: String, artist: String) -> Unit)? = null) {
        if (_isListeningShazam.value) {
            AmbientAudioRecorder.stopRecording()
            return
        }
        val context = getApplication<Application>()
        if (ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            executeAmbientShazamCapture(onTrackRecognized)
        } else {
            _audioPermissionRequestEvent.tryEmit(Unit)
        }
    }

    fun onAudioPermissionGranted(onTrackRecognized: ((title: String, artist: String) -> Unit)? = null) {
        executeAmbientShazamCapture(onTrackRecognized)
    }

    fun executeAmbientShazamCapture(onTrackRecognized: ((title: String, artist: String) -> Unit)? = null) {
        if (_isListeningShazam.value) return
        _isListeningShazam.value = true

        val isHum = _shazamMode.value == ShazamMode.HUMMING
        _recordingDurationSeconds.value = 0
        _recognizedMessage.value = if (isHum) {
            "Hum or sing a tune... Tap radar to identify early"
        } else {
            "Listening to music... Identifying within seconds"
        }

        val tickerJob = viewModelScope.launch {
            while (isActive && _isListeningShazam.value) {
                delay(1000)
                _recordingDurationSeconds.value++
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val matched = AmbientAudioRecorder.streamPcm(
                    maxDurationMs = 12000,
                    checkpointIntervalsMs = listOf(1500, 3000, 5000, 8000, 12000)
                ) { cumulativePcm, isFinal ->
                    _recognizedMessage.value = "Analyzing acoustic fingerprint..."
                    val (code, resp) = client.identifyPcmAudio(cumulativePcm)
                    if (code in 200..299 && resp.isNotBlank()) {
                        val success = parseAndSetShazamResult(resp, onTrackRecognized)
                        if (success) {
                            return@streamPcm true // EARLY EXIT! Match achieved!
                        }
                    }
                    if (isFinal) {
                        val reasonMsg = if (_shazamMode.value == ShazamMode.HUMMING) {
                            "Could not match hummed melody. Melodies require distinct pitch, or tap below to use Google Sound Search."
                        } else {
                            "No acoustic match found. Try again closer to the speaker."
                        }
                        _recognizedMessage.value = reasonMsg
                        withContext(Dispatchers.Main) {
                            UnboundToast.show(getApplication(), reasonMsg)
                        }
                    } else {
                        _recognizedMessage.value = "Listening to music... (${_recordingDurationSeconds.value}s)"
                    }
                    false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Audio recognition error: ${e.message}", e)
                _recognizedMessage.value = "Could not recognize audio. Try again."
                withContext(Dispatchers.Main) {
                    UnboundToast.show(getApplication(), "Could not recognize audio. Try again.")
                }
            } finally {
                _isListeningShazam.value = false
                tickerJob.cancel()
                _recordingDurationSeconds.value = 0
            }
        }
    }

    private suspend fun parseAndSetShazamResult(
        resp: String,
        onTrackRecognized: ((title: String, artist: String) -> Unit)?
    ): Boolean {
        return try {
            val json = JSONObject(resp)
            val matched = json.optBoolean("matched", false)
            val trackTitle = json.optString("title", json.optString("track_title", ""))
            val artist = json.optString("artist", "")
            if (matched && trackTitle.isNotBlank()) {
                val trackId = json.optString("id", json.optString("track_id", "shazam_${System.currentTimeMillis()}"))
                val cover = json.optString("cover_url", json.optString("thumbnail", ""))
                val matchedTrack = TrackItem(
                    id = trackId,
                    title = trackTitle,
                    artist = artist,
                    album = json.optString("album", ""),
                    durationMs = json.optLong("duration_ms", 0L),
                    coverUrl = cover,
                    source = "shazam"
                )
                _lastRecognizedTrack.value = matchedTrack
                _shazamHistory.value = listOf(matchedTrack) + _shazamHistory.value.filter { it.title != trackTitle || it.artist != artist }

                // Parse disambiguation variants
                val variantsJson = json.optJSONArray("variants")
                val parsedVariants = mutableListOf<RecognizedTrackVariant>()
                if (variantsJson != null && variantsJson.length() > 0) {
                    for (i in 0 until variantsJson.length()) {
                        val vObj = variantsJson.optJSONObject(i) ?: continue
                        val vId = vObj.optString("id", trackId)
                        val vTitle = vObj.optString("title", trackTitle)
                        val vArtist = vObj.optString("artist", artist)
                        val vAlbum = vObj.optString("album", "")
                        val vCover = vObj.optString("cover_url", cover)
                        val vDuration = vObj.optLong("duration_ms", matchedTrack.durationMs)
                        val vBadge = vObj.optString("badge", if (i == 0) "Acoustic Radar Match" else "Vibe AI Original Suggestion")
                        val isOrig = vObj.optBoolean("is_original", i > 0)
                        val isRadar = vObj.optBoolean("is_radar_match", i == 0)
                        val expl = vObj.optString("explanation", "")
                        val vTrack = TrackItem(
                            id = vId,
                            title = vTitle,
                            artist = vArtist,
                            album = vAlbum,
                            coverUrl = vCover,
                            durationMs = vDuration,
                            source = if (isOrig) "youtube" else "shazam"
                        )
                        parsedVariants.add(
                            RecognizedTrackVariant(
                                track = vTrack,
                                badge = vBadge,
                                isOriginal = isOrig,
                                isRadarMatch = isRadar,
                                explanation = expl
                            )
                        )
                    }
                }
                _recognizedVariants.value = parsedVariants

                val displayMsg = if (artist.isNotBlank()) "Recognized: $trackTitle - $artist" else "Recognized: $trackTitle"
                _recognizedMessage.value = displayMsg
                withContext(Dispatchers.Main) {
                    UnboundToast.show(getApplication(), displayMsg)
                    onTrackRecognized?.invoke(trackTitle, artist)
                }
                true
            } else {
                false
            }
        } catch (_: Exception) {
            false
        }
    }
}
