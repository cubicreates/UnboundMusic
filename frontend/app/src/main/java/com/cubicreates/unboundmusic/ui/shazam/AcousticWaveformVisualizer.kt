/*
 * Package: com.cubicreates.unboundmusic.ui.shazam
 * File: AcousticWaveformVisualizer.kt
 * Purpose: Real-time dynamic flickering audio waveform visualizer responding to live microphone amplitude.
 * Subsystem: Domain UI / Acoustic Radar Visuals
 * Concurrency: Rendered on Compose UI thread with hardware-accelerated animations.
 */

package com.cubicreates.unboundmusic.ui.shazam

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import kotlin.math.abs
import kotlin.math.sin

/**
 * Interactive acoustic waveform visualizer displaying a row of lively animated equalizer bars
 * that flicker dynamically in response to real-time ambient microphone amplitude.
 */
@Composable
fun AcousticWaveformVisualizer(
    isListening: Boolean,
    amplitude: Float,
    modifier: Modifier = Modifier,
    barCount: Int = 9,
    maxBarHeight: Dp = 46.dp,
    minBarHeight: Dp = 4.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveform_flicker")

    // Fast ticking phases for fluid per-bar harmonic motion
    val phase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 650, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_fast"
    )
    val phase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 6.28318f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 950, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase_med"
    )

    // Base profile weights centered around the middle bars
    val barWeights = listOf(0.40f, 0.65f, 0.85f, 1.05f, 1.25f, 1.05f, 0.85f, 0.65f, 0.40f)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(maxBarHeight + 6.dp)
        ) {
            for (i in 0 until barCount) {
                val weight = barWeights.getOrElse(i) { 0.7f }
                val perBarPhase = i * 0.72f
                val harmonic = (sin(phase1 + perBarPhase) * 0.6f + sin(phase2 + perBarPhase * 1.5f) * 0.4f)
                val flutter = (harmonic + 1f) / 2f // normalize to 0..1

                // Target height calculation driven by real-time audio amplitude
                val targetHeightDp: Dp = if (!isListening) {
                    minBarHeight
                } else if (amplitude > 0.015f) {
                    // Active sound detected: lively dynamic flickering based on real mic energy
                    val dynamicFactor = (amplitude * 1.8f * weight * (0.45f + 0.55f * flutter)).coerceIn(0f, 1f)
                    minBarHeight + (maxBarHeight - minBarHeight) * dynamicFactor
                } else {
                    // Quiescent / quiet state: gentle subtle baseline breathing
                    minBarHeight + 2.dp * abs(sin(phase2 + perBarPhase))
                }

                val animatedHeight by animateDpAsState(
                    targetValue = targetHeightDp,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = 800f),
                    label = "bar_h_$i"
                )

                val barAlpha = when {
                    !isListening -> 0.25f
                    amplitude > 0.015f -> 0.85f + 0.15f * flutter
                    else -> 0.45f
                }

                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(animatedHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(
                                    UnboundPrimary.copy(alpha = barAlpha),
                                    UnboundSecondary.copy(alpha = barAlpha * 0.85f)
                                )
                            )
                        )
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Status badge indicating active capture
        if (isListening) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (amplitude > 0.02f) UnboundPrimary.copy(alpha = 0.16f)
                        else Color.White.copy(alpha = 0.06f)
                    )
                    .padding(horizontal = 10.dp, vertical = 3.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            if (amplitude > 0.02f) UnboundPrimary
                            else OnSurfaceVariant.copy(alpha = 0.5f)
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (amplitude > 0.02f) "Audio Detected • Capturing" else "Microphone Active • Listening",
                    color = if (amplitude > 0.02f) UnboundPrimary else OnSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
