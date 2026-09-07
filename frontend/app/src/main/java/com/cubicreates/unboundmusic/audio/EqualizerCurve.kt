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
        val BASS_HEAVY = EqualizerCurve(listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 1f, 2f), 0f)
        val VOCAL_BOOST = EqualizerCurve(listOf(-2f, -1f, 0f, 1f, 3f, 4f, 3f, 1f, 0f, 0f), 0f)
        val EDM = EqualizerCurve(listOf(5f, 4f, 2f, 0f, -1f, 1f, 2f, 3f, 4f, 4f), 0f)
        val ROCK = EqualizerCurve(listOf(4f, 3f, 2f, 0f, -1f, -1f, 1f, 2f, 3f, 3f), 0f)
        val STUDIO_CLEAN = EqualizerCurve(listOf(1f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, 2f, 2f), 0f)

        val PRESET_MAP: Map<String, EqualizerCurve> = mapOf(
            "Flat" to FLAT,
            "Bass Heavy" to BASS_HEAVY,
            "Vocal Boost" to VOCAL_BOOST,
            "EDM" to EDM,
            "Rock" to ROCK,
            "Studio Clean" to STUDIO_CLEAN
        )
    }
}
