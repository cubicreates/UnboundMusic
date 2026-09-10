/*
 * Package: com.cubicreates.unboundmusic.service
 * File: AudioEffectController.kt
 * Purpose: Native Android AudioEffect controller managing hardware BassBoost, Virtualizer,
 *          and LoudnessEnhancer effects attached to ExoPlayer's audioSessionId.
 * Subsystem: Pro Audio DSP
 * Concurrency: Thread-safe singleton with synchronized state mutations.
 */

package com.cubicreates.unboundmusic.service

import android.media.audiofx.BassBoost
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log

/**
 * Singleton controlling Android hardware/native audio DSP effects.
 * Manages lifecycle tied to the active ExoPlayer audioSessionId.
 */
object AudioEffectController {
    private const val TAG = "AudioEffectController"

    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var currentSessionId: Int = 0

    @Volatile var bassBoostStrength: Int = 0 // 0..1000
    @Volatile var virtualizerStrength: Int = 0 // 0..1000
    @Volatile var loudnessGainMb: Int = 0 // 0..1500 (mB)

    @Synchronized
    fun attachAudioSession(sessionId: Int) {
        if (sessionId == 0 || sessionId == currentSessionId) return
        release()
        currentSessionId = sessionId

        // Only attach effects if non-zero to avoid polluting audio pipeline
        if (bassBoostStrength > 0) {
            applyBassBoostInternal()
        }
        if (virtualizerStrength > 0) {
            applyVirtualizerInternal()
        }
        if (loudnessGainMb > 0) {
            applyLoudnessInternal()
        }
        Log.i(TAG, "AudioEffectController session registered: $sessionId")
    }

    private fun applyBassBoostInternal() {
        if (currentSessionId == 0) return
        try {
            if (bassBoost == null) {
                bassBoost = BassBoost(0, currentSessionId)
            }
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(bassBoostStrength.toShort())
                    it.enabled = bassBoostStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply BassBoost: ${e.message}")
        }
    }

    private fun applyVirtualizerInternal() {
        if (currentSessionId == 0) return
        try {
            if (virtualizer == null) {
                virtualizer = Virtualizer(0, currentSessionId)
            }
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(virtualizerStrength.toShort())
                    it.enabled = virtualizerStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply Virtualizer: ${e.message}")
        }
    }

    private fun applyLoudnessInternal() {
        if (currentSessionId == 0) return
        try {
            if (loudnessEnhancer == null) {
                loudnessEnhancer = LoudnessEnhancer(currentSessionId)
            }
            loudnessEnhancer?.let {
                it.setTargetGain(loudnessGainMb)
                it.enabled = loudnessGainMb > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to apply LoudnessEnhancer: ${e.message}")
        }
    }

    @Synchronized
    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        if (bassBoostStrength > 0) {
            applyBassBoostInternal()
        } else {
            try { bassBoost?.release() } catch (_: Exception) {}
            bassBoost = null
        }
    }

    @Synchronized
    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        if (virtualizerStrength > 0) {
            applyVirtualizerInternal()
        } else {
            try { virtualizer?.release() } catch (_: Exception) {}
            virtualizer = null
        }
    }

    @Synchronized
    fun setLoudness(gainMb: Int) {
        loudnessGainMb = gainMb.coerceIn(0, 1500)
        if (loudnessGainMb > 0) {
            applyLoudnessInternal()
        } else {
            try { loudnessEnhancer?.release() } catch (_: Exception) {}
            loudnessEnhancer = null
        }
    }

    @Synchronized
    fun release() {
        try {
            bassBoost?.release()
        } catch (_: Exception) {}
        bassBoost = null

        try {
            virtualizer?.release()
        } catch (_: Exception) {}
        virtualizer = null

        try {
            loudnessEnhancer?.release()
        } catch (_: Exception) {}
        loudnessEnhancer = null

        currentSessionId = 0
    }
}
