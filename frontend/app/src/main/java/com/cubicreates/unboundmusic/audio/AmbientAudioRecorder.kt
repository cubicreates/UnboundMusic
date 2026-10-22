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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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

    private val isCancelledOrStopped = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * Signals active recording to stop immediately and finalize captured PCM bytes.
     */
    fun stopRecording() {
        isCancelledOrStopped.set(true)
    }

    /**
     * Records up to [durationMs] of ambient audio from the device microphone at 16,000 Hz 16-bit Mono.
     * Can be stopped at any time via [stopRecording] to send captured audio immediately.
     * Computes real-time RMS audio levels to drive live animated waveform visualizers.
     * Returns raw PCM byte array ready for Shazam DSP constellation extraction.
     */
    @SuppressLint("MissingPermission")
    suspend fun recordPcm(durationMs: Int = 5000): ByteArray? = withContext(Dispatchers.IO) {
        isCancelledOrStopped.set(false)
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid minBufferSize: $minBufferSize")
            return@withContext null
        }

        val bufferSize = maxOf(minBufferSize, 4096)
        var audioRecord: AudioRecord? = null
        var selectedSource = MediaRecorder.AudioSource.MIC

        try {
            // Prioritize UNPROCESSED (raw music ADC without AEC/NS) and VOICE_RECOGNITION over MIC.
            // AudioSource.MIC enables hardware echo cancellation and noise suppression which attenuates music.
            val sources = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                intArrayOf(
                    MediaRecorder.AudioSource.UNPROCESSED,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC
                )
            } else {
                intArrayOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC
                )
            }
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

            while (coroutineContext.isActive && totalRead < targetBytes && !isCancelledOrStopped.get()) {
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
            if (totalRead < 16000) {
                Log.w(TAG, "Captured audio sample is too short ($totalRead bytes < 0.5s)")
                return@withContext null
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

    /**
     * Incrementally streams ambient audio from the microphone at 16,000 Hz 16-bit Mono.
     * Evaluates audio at progressive milestones (e.g., 2.0s, 4.0s, 7.0s, 12.0s) via [onCheckpoint]
     * in a non-blocking asynchronous coroutine so the microphone read loop NEVER drops audio frames.
     * If [onCheckpoint] returns true (meaning early recognition match succeeded),
     * recording stops immediately, releasing the microphone hardware and conserving battery.
     * When stopped manually via [stopRecording], the captured audio is immediately finalized and evaluated.
     */
    @SuppressLint("MissingPermission")
    suspend fun streamPcm(
        maxDurationMs: Int = 12000,
        checkpointIntervalsMs: List<Int> = listOf(3500, 6000, 9000, 12000),
        onCheckpoint: suspend (cumulativePcm: ByteArray, isFinal: Boolean) -> Boolean
    ): Boolean = withContext(Dispatchers.IO) {
        isCancelledOrStopped.set(false)
        val minBufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (minBufferSize <= 0) {
            Log.e(TAG, "Invalid minBufferSize: $minBufferSize")
            return@withContext false
        }

        val bufferSize = maxOf(minBufferSize, 4096)
        var audioRecord: AudioRecord? = null
        var selectedSource = MediaRecorder.AudioSource.MIC

        try {
            val sources = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                intArrayOf(
                    MediaRecorder.AudioSource.UNPROCESSED,
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC
                )
            } else {
                intArrayOf(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION,
                    MediaRecorder.AudioSource.MIC
                )
            }
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
                        break
                    } else {
                        record.release()
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "AudioSource $source init failed: ${e.message}")
                }
            }

            if (audioRecord == null || audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return@withContext false
            }

            audioRecord.startRecording()
            Log.i(TAG, "Started progressive streaming capture (source=$selectedSource, maxDuration=$maxDurationMs ms)...")

            val outputStream = ByteArrayOutputStream()
            val streamLock = Any()
            val buffer = ByteArray(bufferSize)
            val maxBytes = (SAMPLE_RATE * 2L * maxDurationMs / 1000).toInt()
            var totalRead = 0
            var checkpointIdx = 0
            val matchFound = java.util.concurrent.atomic.AtomicBoolean(false)
            val isEvaluating = java.util.concurrent.atomic.AtomicBoolean(false)
            var lastEvaluatedBytes = 0
            var finalCheckpointEvaluated = false

            coroutineScope {
                while (coroutineContext.isActive && totalRead < maxBytes && !isCancelledOrStopped.get() && !matchFound.get()) {
                    val toRead = minOf(buffer.size, maxBytes - totalRead)
                    val read = audioRecord.read(buffer, 0, toRead)
                    if (read > 0) {
                        synchronized(streamLock) {
                            outputStream.write(buffer, 0, read)
                        }
                        totalRead += read

                        // Calculate real-time RMS for waveform visualizer
                        var chunkMax = 0
                        var sumSquares = 0.0
                        val samplesInChunk = read / 2
                        for (i in 0 until read - 1 step 2) {
                            val sample = ((buffer[i + 1].toInt() shl 8) or (buffer[i].toInt() and 0xFF)).toShort()
                            val abs = Math.abs(sample.toInt())
                            if (abs > chunkMax) chunkMax = abs
                            sumSquares += (sample.toDouble() * sample.toDouble())
                        }
                        val rms = if (samplesInChunk > 0) Math.sqrt(sumSquares / samplesInChunk) else 0.0
                        val normalizedAmp = (rms / 6000.0).toFloat().coerceIn(0f, 1f)
                        _audioAmplitude.value = normalizedAmp

                        // Check progressive checkpoint milestone (non-blocking evaluation)
                        val elapsedMs = (totalRead * 1000L / (SAMPLE_RATE * 2)).toInt()
                        if (checkpointIdx < checkpointIntervalsMs.size && elapsedMs >= checkpointIntervalsMs[checkpointIdx]) {
                            val isLast = (checkpointIdx == checkpointIntervalsMs.lastIndex) || elapsedMs >= maxDurationMs
                            checkpointIdx++

                            if (!isEvaluating.get()) {
                                val pcmSnapshot = synchronized(streamLock) { outputStream.toByteArray() }
                                if (pcmSnapshot.size >= 32000) { // at least 1.0s
                                    isEvaluating.set(true)
                                    launch {
                                        try {
                                            lastEvaluatedBytes = pcmSnapshot.size
                                            if (isLast) {
                                                finalCheckpointEvaluated = true
                                            }
                                            val matched = onCheckpoint(pcmSnapshot, isLast)
                                            if (matched) {
                                                Log.i(TAG, "Early acoustic match achieved at $elapsedMs ms! Stopping microphone.")
                                                matchFound.set(true)
                                                isCancelledOrStopped.set(true)
                                            }
                                        } catch (e: Exception) {
                                            Log.w(TAG, "Checkpoint evaluation error: ${e.message}")
                                        } finally {
                                            isEvaluating.set(false)
                                        }
                                    }
                                }
                            }
                        }
                    } else if (read < 0) {
                        Log.e(TAG, "AudioRecord read error: $read")
                        break
                    }
                }
            }

            if (matchFound.get()) {
                return@withContext true
            }

            // Always evaluate the full captured buffer when stopped early or when new audio was gathered
            val finalPcm = synchronized(streamLock) { outputStream.toByteArray() }
            if (finalPcm.size >= 16000 && (!finalCheckpointEvaluated || (finalPcm.size - lastEvaluatedBytes) >= 16000)) {
                Log.i(TAG, "Evaluating final audio capture (${finalPcm.size} bytes, ${(finalPcm.size * 1000L / (SAMPLE_RATE * 2))} ms)...")
                return@withContext onCheckpoint(finalPcm, true)
            }
            return@withContext false
        } catch (e: Exception) {
            Log.e(TAG, "Progressive stream audio capture failed: ${e.message}", e)
            return@withContext false
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
