/*
 * Package: com.cubicreates.unboundmusic.audio
 * File: SleepFadeAudioProcessor.kt
 * Purpose: Media3 AudioProcessor for smooth logarithmic volume attenuation during sleep timer countdown.
 * Subsystem: Pro Audio DSP
 */

package com.cubicreates.unboundmusic.audio

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Media3 [AudioProcessor] that attenuates playback by the sleep timer's fade-out gain.
 */
@OptIn(UnstableApi::class)
class SleepFadeAudioProcessor(
    private val gain: () -> Float,
) : BaseAudioProcessor() {
    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)
        val currentGain = gain()

        if (currentGain >= 1f) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        while (inputBuffer.remaining() >= 2) {
            output.putShort((inputBuffer.short * currentGain).toInt().toShort())
        }
        while (inputBuffer.hasRemaining()) {
            output.put(inputBuffer.get())
        }

        output.flip()
    }
}
