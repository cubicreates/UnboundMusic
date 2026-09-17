/*
 * Package: com.cubicreates.unboundmusic.audio
 * File: AmbientAudioRecorder.kt
 * Purpose: Captures raw 16kHz 16-bit Mono PCM ambient microphone audio for Shazam acoustic recognition.
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
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.coroutines.coroutineContext

object AmbientAudioRecorder {

    private const val TAG = "AmbientAudioRecorder"
    const val SAMPLE_RATE = 16000
    private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
    private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

    /**
     * Records [durationMs] of ambient audio from the device microphone at 16,000 Hz 16-bit Mono.
     * Returns raw PCM byte array ready for Shazam DSP constellation extraction.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordPcm(durationMs: Int = 4500): ByteArray? = withContext(Dispatchers.IO) {
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid minBufferSize: $minBufferSize")
            return@withContext null
        }

        val bufferSize = maxOf(minBufferSize, 4096)
        var audioRecord: AudioRecord? = null

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize (state=${audioRecord.state})")
                return@withContext null
            }

            audioRecord.startRecording()
            Log.i(TAG, "Started ambient microphone capture ($durationMs ms target, 16kHz PCM)...")

            val outputStream = ByteArrayOutputStream()
            val buffer = ByteArray(bufferSize)
            // Total bytes for durationMs: sampleRate * (16 bits / 8) * (durationMs / 1000)
            val targetBytes = (SAMPLE_RATE * 2L * durationMs / 1000).toInt()
            var totalRead = 0

            while (coroutineContext.isActive && totalRead < targetBytes) {
                val toRead = minOf(buffer.size, targetBytes - totalRead)
                val read = audioRecord.read(buffer, 0, toRead)
                if (read > 0) {
                    outputStream.write(buffer, 0, read)
                    totalRead += read
                } else if (read < 0) {
                    Log.e(TAG, "AudioRecord read error: $read")
                    break
                }
            }

            Log.i(TAG, "Microphone capture completed: $totalRead bytes gathered")
            return@withContext outputStream.toByteArray()
        } catch (e: Exception) {
            Log.e(TAG, "Failed capturing ambient audio: ${e.message}", e)
            return@withContext null
        } finally {
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
