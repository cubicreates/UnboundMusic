/*
 * Package: com.cubicreates.unboundmusic.audio
 * File: AmbientAudioRecorder.kt
 * Purpose: Captures raw 16kHz 16-bit Mono PCM ambient microphone audio for Shazam acoustic recognition,
 *          emitting real-time normalized audio amplitude for interactive waveform visualizers.
 * Subsystem: Audio DSP & Ambient Recognition
 * Concurrency: Thread-safe asynchronous capture on Dispatchers.IO.
 */

package com.cubicreates.unboundmusic.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

object AmbientAudioRecorder {

    private const val TAG = "AmbientAudioRecorder"
    const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    /**
     * Records [durationMs] of ambient audio from the device microphone at 16,000 Hz 16-bit Mono.
     * Computes real-time RMS audio levels to drive live animated waveform visualizers.
     * Returns raw PCM byte array ready for Shazam DSP constellation extraction.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordPcm(durationMs: Int = 5500): ByteArray? = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid minBufferSize: $minBufferSize")
            return@withContext null
        }

        val bufferSize = maxOf(minBufferSize, 4096)
        var audioRecord: AudioRecord? = null
        var selectedSource = MediaRecorder.AudioSource.MIC

        try {
            // AudioSource.MIC is standard and universal across 100% of Android devices.
            // VOICE_RECOGNITION and UNPROCESSED serve as alternate fallbacks.
            val sources = intArrayOf(
                MediaRecorder.AudioSource.MIC,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.UNPROCESSED
            )
            for (source in sources) {
                try {
                    val record = AudioRecord(
                        source,
                        SAMPLE_RATE,
                        CHANNEL_CONFIG,
                        AUDIO_FORMAT,
                        bufferSize
                    )
                    if (record.state == AudioRecord.STATE_INITIALIZED) {
                        audioRecord = record
                        selectedSource = source
                        Log.i(TAG, "AudioRecord initialized successfully with source $source")
                        break
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AudioSource $source initialization failed: ${e.message}")
                }
            }

            if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize across all available sources")
                return@withContext null
            }

            audioRecord.startRecording()
            Log.i(TAG, "Started ambient microphone capture ($durationMs ms target, source=$selectedSource, 16kHz PCM)...")

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(bufferSize)
            val targetBytes = (SAMPLE_RATE * 2L * durationMs / 1000).toInt()
            var totalRead = 0
            var overallPeak = 0

            while (coroutineContext.isActive && totalRead < targetBytes) {
                val toRead = minOf(buffer.size, targetBytes - totalRead)
                val read = audioRecord.read(buffer, 0, toRead)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    totalRead += read

                    // Calculate real-time RMS and peak amplitude for live waveform visualizer
                    var chunkMax = 0
                    var sumSquares = 0.0
                    val samplesInChunk = read / 2
                    for (i in 0 until read - 1 step 2) {
                        val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                        val abs = Math.abs(sample.toInt())
                        if (abs > chunkMax) chunkMax = abs
                        sumSquares += (sample.toDouble() * sample.toDouble())
                    }
                    if (chunkMax > overallPeak) {
                        overallPeak = chunkMax
                    }
                    val rms = if (samplesInChunk > 0) Math.sqrt(sumSquares / samplesInChunk) else 0.0
                    // Map RMS (0..6000) to 0.0f..1.0f with logarithmic sensitivity
                    val normalizedAmp = (rms / 6000.0).toFloat().coerceIn(0f, 1f)
                    _audioAmplitude.value = normalizedAmp
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: $read")
                    break
                }
            }

            Log.i(TAG, "Microphone capture completed: $totalRead bytes gathered, overallPeak=$overallPeak (source=$selectedSource)")
            if (overallPeak == 0) {
                Log.e(TAG, "Recorded audio is completely silent (0 peak amplitude)! Microphone may be muted or blocked by OS.")
            }
            return@withContext outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed capturing ambient audio: ${e.message}", e)
            return@withContext null
        } finally {
            _audioAmplitude.value = 0f
            try {
                if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    audioRecord.stop()
                }
                audioRecord?.release()
            } catch (re: Exception) {
                Log.w(TAG, "Error releasing AudioRecord: ${re.message}")
            }
        }
    }
}
