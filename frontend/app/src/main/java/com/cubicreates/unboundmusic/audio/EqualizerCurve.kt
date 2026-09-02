/*
 * Package: com.cubicreates.unboundmusic.audio
 * File: EqualizerCurve.kt
 * Purpose: Immutable data representation of 10-band equalizer settings and preamp gain.
 * Subsystem: Pro Audio DSP
 */

package com.cubicreates.unboundmusic.audio

/**
 * Standard 10 band centre frequencies, in Hz.
 */
val EQUALIZER_BANDS_HZ: IntArray = intArrayOf(31, 62, 125, 250, 500, 1_000, 2_000, 4_000, 8_000, 16_000)

val EQUALIZER_BAND_LABELS: List<String> = listOf("31Hz", "62Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz")

/**
 * Immutable Equalizer Curve definition.
 */
data class EqualizerCurve(
    val bandsDb: List<Float> = List(10) { 0f },
    val preampDb: Float = 0f,
) {
    val isFlat: Boolean = preampDb == 0f && (bandsDb.isEmpty() || bandsDb.all { it == 0f })

    companion object {
        val FLAT = EqualizerCurve(List(10) { 0f }, 0f)
    }
}
