/*
 * Package: com.cubicreates.unboundmusic.audio
 * File: CrossfadeFilterAudioProcessor.kt
 * Purpose: Media3 AudioProcessor applying real-time Biquad low-pass and high-pass filters for DJ crossfade transitions.
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
 * Media3 [AudioProcessor] that applies a real-time Biquad low-pass or high-pass filter to the audio stream.
 */
@OptIn(UnstableApi::class)
class CrossfadeFilterAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var enabled: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                if (!value) {
                    filter.reset()
                }
            }
        }

    @Volatile
    var cutoffFrequencyHz: Float = 20000f
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    @Volatile
    var filterType: BiquadFilter.FilterType = BiquadFilter.FilterType.LOW_PASS
        set(value) {
            if (field != value) {
                field = value
                coefficientsDirty = true
            }
        }

    private val filter = BiquadFilter()

    @Volatile
    private var coefficientsDirty = true

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_PCM_16BIT

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT &&
            inputAudioFormat.encoding != C.ENCODING_PCM_FLOAT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        encoding = inputAudioFormat.encoding
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        coefficientsDirty = true
        return inputAudioFormat
    }


    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        if (!enabled || sampleRate == 0) {
            val output = replaceOutputBuffer(remaining)
            copyBuffer(inputBuffer, output, remaining)
            output.flip()
            return
        }

        if (coefficientsDirty) {
            filter.updateCoefficients(
                cutoffHz = cutoffFrequencyHz,
                sampleRate = sampleRate,
                type = filterType,
            )
            coefficientsDirty = false
        }

        val output = replaceOutputBuffer(remaining)
        inputBuffer.order(ByteOrder.nativeOrder())

        if (encoding == C.ENCODING_PCM_FLOAT) {
            when (channelCount) {
                1 -> processMonoBlockFloat(inputBuffer, output)
                2 -> processStereoBlockFloat(inputBuffer, output)
                else -> copyBuffer(inputBuffer, output, remaining)
            }
        } else {
            when (channelCount) {
                1 -> processMonoBlock(inputBuffer, output)
                2 -> processStereoBlock(inputBuffer, output)
                else -> copyBuffer(inputBuffer, output, remaining)
            }
        }

        output.flip()
    }

    private fun copyBuffer(src: ByteBuffer, dst: ByteBuffer, size: Int) {
        if (src === dst) {
            dst.position(0)
            dst.limit(size)
            return
        }
        val pos = src.position()
        for (i in 0 until size) {
            dst.put(src.get(pos + i))
        }
        src.position(pos + size)
    }

    private fun processMonoBlock(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 2) {
            val sample = input.short.toDouble() / Short.MAX_VALUE
            val filtered = filter.processSampleMono(sample)
            output.putShort((filtered.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
        }
    }

    private fun processStereoBlock(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val left = input.short.toDouble() / Short.MAX_VALUE
            val right = input.short.toDouble() / Short.MAX_VALUE
            val (filteredL, filteredR) = filter.processStereo(left, right)
            output.putShort((filteredL.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
            output.putShort((filteredR.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort())
        }
    }

    private fun processMonoBlockFloat(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 4) {
            val sample = input.float.toDouble()
            val filtered = filter.processSampleMono(sample)
            output.putFloat(filtered.coerceIn(-1.0, 1.0).toFloat())
        }
    }

    private fun processStereoBlockFloat(input: ByteBuffer, output: ByteBuffer) {
        while (input.remaining() >= 8) {
            val left = input.float.toDouble()
            val right = input.float.toDouble()
            val (filteredL, filteredR) = filter.processStereo(left, right)
            output.putFloat(filteredL.coerceIn(-1.0, 1.0).toFloat())
            output.putFloat(filteredR.coerceIn(-1.0, 1.0).toFloat())
        }
    }

    override fun onFlush() {
        super.onFlush()
        filter.reset()
    }

    override fun onReset() {
        super.onReset()
        enabled = false
        encoding = C.ENCODING_PCM_16BIT
        cutoffFrequencyHz = 20000f
        filterType = BiquadFilter.FilterType.LOW_PASS
        filter.reset()
    }
}
