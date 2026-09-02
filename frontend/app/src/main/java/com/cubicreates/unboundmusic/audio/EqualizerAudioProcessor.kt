/*
 * Package: com.cubicreates.unboundmusic.audio
 * File: EqualizerAudioProcessor.kt
 * Purpose: Media3 10-band software parametric equalizer audio processor applying Robert Bristow-Johnson biquad filters.
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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val EQUALIZER_Q = 1.41
private const val COEFFS_PER_BAND = 5
private const val STATE_PER_BAND = 4
private const val PCM16_MIN = -32_768.0
private const val PCM16_MAX = 32_767.0

/**
 * Media3 [AudioProcessor] applying a 10-band parametric equalizer to the audio stream.
 */
@OptIn(UnstableApi::class)
class EqualizerAudioProcessor(
    private val curve: () -> EqualizerCurve,
) : BaseAudioProcessor() {
    private var sampleRate = 0
    private var channelCount = 0
    private var appliedCurve: EqualizerCurve? = null
    private var coefficients = DoubleArray(0)
    private var state = DoubleArray(0)
    private var preampGain = 1.0
    private var bypass = true

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        state = DoubleArray(EQUALIZER_BANDS_HZ.size * STATE_PER_BAND * channelCount)
        appliedCurve = null
        return inputAudioFormat
    }

    override fun onFlush() {
        state.fill(0.0)
    }

    override fun onReset() {
        state = DoubleArray(0)
        coefficients = DoubleArray(0)
        appliedCurve = null
        sampleRate = 0
        channelCount = 0
        bypass = true
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val output = replaceOutputBuffer(remaining)
        syncCurve()

        if (bypass) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        var channel = 0
        while (inputBuffer.remaining() >= 2) {
            val processed = process(inputBuffer.short.toDouble(), channel)
            output.putShort(processed.coerceIn(PCM16_MIN, PCM16_MAX).toInt().toShort())
            channel++
            if (channel == channelCount) channel = 0
        }
        while (inputBuffer.hasRemaining()) {
            output.put(inputBuffer.get())
        }

        output.flip()
    }

    private fun syncCurve() {
        val next = curve()
        if (next === appliedCurve) return
        appliedCurve = next
        rebuild(next)
    }

    private fun rebuild(next: EqualizerCurve) {
        val usable = channelCount > 0 && sampleRate > 0
        if (!usable || next.isFlat) {
            if (!bypass) state.fill(0.0)
            bypass = true
            return
        }

        preampGain = 10.0.pow(next.preampDb / 20.0)
        if (coefficients.size != EQUALIZER_BANDS_HZ.size * COEFFS_PER_BAND) {
            coefficients = DoubleArray(EQUALIZER_BANDS_HZ.size * COEFFS_PER_BAND)
        }

        val nyquist = sampleRate / 2.0
        EQUALIZER_BANDS_HZ.forEachIndexed { index, centreHz ->
            val gainDb = if (centreHz < nyquist) next.bandsDb.getOrElse(index) { 0f } else 0f
            writePeakingBiquad(index, centreHz.toDouble(), gainDb.toDouble())
        }
        bypass = false
    }

    private fun writePeakingBiquad(
        index: Int,
        centreHz: Double,
        gainDb: Double,
    ) {
        val a = 10.0.pow(gainDb / 40.0)
        val w0 = 2.0 * PI * centreHz / sampleRate
        val cosW0 = cos(w0)
        val alpha = sin(w0) / (2.0 * EQUALIZER_Q)

        val a0 = 1.0 + alpha / a
        val offset = index * COEFFS_PER_BAND
        coefficients[offset] = (1.0 + alpha * a) / a0
        coefficients[offset + 1] = (-2.0 * cosW0) / a0
        coefficients[offset + 2] = (1.0 - alpha * a) / a0
        coefficients[offset + 3] = (-2.0 * cosW0) / a0
        coefficients[offset + 4] = (1.0 - alpha / a) / a0
    }

    private fun process(
        input: Double,
        channel: Int,
    ): Double {
        var sample = input * preampGain
        var c = 0
        var s = channel * EQUALIZER_BANDS_HZ.size * STATE_PER_BAND
        while (c < coefficients.size) {
            val x1 = state[s]
            val x2 = state[s + 1]
            val y1 = state[s + 2]
            val y2 = state[s + 3]

            val out =
                coefficients[c] * sample +
                    coefficients[c + 1] * x1 +
                    coefficients[c + 2] * x2 -
                    coefficients[c + 3] * y1 -
                    coefficients[c + 4] * y2

            state[s] = sample
            state[s + 1] = x1
            state[s + 2] = out
            state[s + 3] = y1

            sample = out
            c += COEFFS_PER_BAND
            s += STATE_PER_BAND
        }
        return sample
    }
}
