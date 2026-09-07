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
        try {
            bassBoost = BassBoost(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(bassBoostStrength.toShort())
                    enabled = bassBoostStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize BassBoost: ${e.message}")
        }

        try {
            virtualizer = Virtualizer(0, sessionId).apply {
                if (strengthSupported) {
                    setStrength(virtualizerStrength.toShort())
                    enabled = virtualizerStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize Virtualizer: ${e.message}")
        }

        try {
            loudnessEnhancer = LoudnessEnhancer(sessionId).apply {
                setTargetGain(loudnessGainMb)
                enabled = loudnessGainMb > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to initialize LoudnessEnhancer: ${e.message}")
        }
        Log.i(TAG, "AudioEffectController attached to audioSessionId: $sessionId")
    }

    @Synchronized
    fun setBassBoost(strength: Int) {
        bassBoostStrength = strength.coerceIn(0, 1000)
        try {
            bassBoost?.let {
                if (it.strengthSupported) {
                    it.setStrength(bassBoostStrength.toShort())
                    it.enabled = bassBoostStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying BassBoost: ${e.message}")
        }
    }

    @Synchronized
    fun setVirtualizer(strength: Int) {
        virtualizerStrength = strength.coerceIn(0, 1000)
        try {
            virtualizer?.let {
                if (it.strengthSupported) {
                    it.setStrength(virtualizerStrength.toShort())
                    it.enabled = virtualizerStrength > 0
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying Virtualizer: ${e.message}")
        }
    }

    @Synchronized
    fun setLoudness(gainMb: Int) {
        loudnessGainMb = gainMb.coerceIn(0, 1500)
        try {
            loudnessEnhancer?.let {
                it.setTargetGain(loudnessGainMb)
                it.enabled = loudnessGainMb > 0
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying LoudnessEnhancer: ${e.message}")
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
