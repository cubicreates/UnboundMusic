/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: ShazamViewModel.kt
 * Purpose: Dedicated Domain ViewModel for 16kHz PCM audio recording, acoustic fingerprinting,
 *          and Pure-Go daemon Shazam music recognition.
 * Subsystem: Domain Layer / Acoustic Recognition
 * Concurrency: Thread-safe reactive StateFlow orchestration on viewModelScope.
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.audio.AmbientAudioRecorder
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Domain ViewModel encapsulating acoustic fingerprinting and Shazam audio recognition.
 */
class ShazamViewModel(application: Application) : AndroidViewModel(application) {

    private val daemonManager = DaemonManager.getInstance(application)
    private val client = daemonManager.client

    private val _isListeningShazam = MutableStateFlow(false)
    val isListeningShazam: StateFlow<Boolean> = _isListeningShazam.asStateFlow()

    private val _recognizedMessage = MutableStateFlow("")
    val recognizedMessage: StateFlow<String> = _recognizedMessage.asStateFlow()

    private val _audioPermissionRequestEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val audioPermissionRequestEvent: SharedFlow<Unit> = _audioPermissionRequestEvent.asSharedFlow()

    companion object {
        private const val TAG = "ShazamViewModel"
    }

    fun startAmbientShazamRecognition(onTrackRecognized: ((title: String, artist: String) -> Unit)? = null) {
        if (_isListeningShazam.value) return
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
        _recognizedMessage.value = "Listening to audio acoustics..."

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val pcmData = AmbientAudioRecorder.recordPcm(4500)
                if (pcmData == null || pcmData.isEmpty()) {
                    _recognizedMessage.value = "Could not record ambient audio."
                    return@launch
                }
                _recognizedMessage.value = "Analyzing acoustic fingerprint..."
                val (code, resp) = client.identifyPcmAudio(pcmData)
                if (code in 200..299 && resp.isNotBlank()) {
                    val json = JSONObject(resp)
                    val matched = json.optBoolean("matched", false)
                    val trackTitle = json.optString("title", json.optString("track_title", ""))
                    val artist = json.optString("artist", "")
                    if (matched && trackTitle.isNotBlank()) {
                        val displayMsg = if (artist.isNotBlank()) "Recognized: $trackTitle - $artist" else "Recognized: $trackTitle"
                        _recognizedMessage.value = displayMsg
                        withContext(Dispatchers.Main) {
                            onTrackRecognized?.invoke(trackTitle, artist)
                        }
                    } else {
                        _recognizedMessage.value = "Could not recognize audio. Try again."
                    }
                } else {
                    _recognizedMessage.value = "Recognition service unavailable ($code)."
                }
            } catch (e: Exception) {
                _recognizedMessage.value = "Recognition error: ${e.message}"
            } finally {
                _isListeningShazam.value = false
            }
        }
    }

    fun clearRecognizedMessage() {
        _recognizedMessage.value = ""
    }
}
