/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: WavySeekBar.kt
 * Purpose: Seekable animated wavy progress bar inspired by Material 3 Expressive & Android 13/14/15 media controls.
 *          Features live sine-wave phase oscillation during playback, smooth flatten when paused or scrubbing,
 *          and morphing thumb pill under touch.
 * Subsystem: Player UI / Expressive Controls
 */

package com.cubicreates.unboundmusic.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Seekable wavy progress bar for the M3 Expressive Now Playing style.
 *
 * Behavior:
 * - When playing, active segment oscillates as a sine wave traveling horizontally.
 * - When paused or during scrub/drag, wave smoothly flattens to 0 amplitude.
 * - Inactive part of track remains a clean straight guide line.
 * - Thumb morphs from a circle (14dp) into a taller rounded bar (6x22dp) during drag.
 */
@Composable
fun WavySeekBar(
    progressFraction: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    trackColor: Color = Color.White.copy(alpha = 0.25f),
    thumbColor: Color = Color.White,
    onSliderChange: (Float) -> Unit = {},
    onSliderChangeFinished: () -> Unit = {}
) {
    val density = LocalDensity.current
    var isInteracting by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableIntStateOf(0) }

    val displayedFraction = (if (isInteracting) dragFraction else progressFraction).coerceIn(0f, 1f)

    // Animated amplitude: 1f when playing and not scrubbing; 0f when paused or interacting
    val targetAmplitude = if (isPlaying && !isInteracting) 1f else 0f
    val amplitudeAnim = remember { Animatable(0f) }
    LaunchedEffect(targetAmplitude) {
        amplitudeAnim.animateTo(
            targetValue = targetAmplitude,
            animationSpec = tween(durationMillis = 350)
        )
    }

    // Infinite phase shift for live wave flow
    val infiniteTransition = rememberInfiniteTransition(label = "wavySeekBarPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    // Thumb morph animation: 0f (circle) -> 1f (tall pill)
    val thumbMorph by animateFloatAsState(
        targetValue = if (isInteracting) 1f else 0f,
        animationSpec = tween(200),
        label = "thumbMorph"
    )

    val maxAmplitudePx = with(density) { 3.5.dp.toPx() }
    val wavelengthPx = with(density) { 26.dp.toPx() }
    val strokeWidthPx = with(density) { 4.dp.toPx() }

    fun fractionAt(x: Float): Float = if (widthPx <= 0) 0f else (x / widthPx).coerceIn(0f, 1f)

    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .onSizeChanged { widthPx = it.width }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = fractionAt(offset.x)
                    dragFraction = fraction
                    onSliderChange(fraction)
                    onSliderChangeFinished()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isInteracting = true
                        val fraction = fractionAt(offset.x)
                        dragFraction = fraction
                        onSliderChange(fraction)
                    },
                    onDragEnd = {
                        isInteracting = false
                        onSliderChangeFinished()
                    },
                    onDragCancel = {
                        isInteracting = false
                        onSliderChangeFinished()
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        val fraction = fractionAt(change.position.x)
                        dragFraction = fraction
                        onSliderChange(fraction)
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().height(44.dp)) {
            val totalW = size.width
            val centerY = size.height / 2f
            val activeW = totalW * displayedFraction
            val currentAmplitude = maxAmplitudePx * amplitudeAnim.value

            // 1. Inactive Track (straight line from activeW to totalW)
            if (activeW < totalW) {
                drawLine(
                    color = trackColor,
                    start = Offset(activeW, centerY),
                    end = Offset(totalW, centerY),
                    strokeWidth = strokeWidthPx,
                    cap = StrokeCap.Round
                )
            }

            // 2. Active Track (Sine wave from 0 to activeW)
            if (activeW > 0f) {
                val wavePath = Path()
                wavePath.moveTo(0f, centerY)

                val step = 2f
                var x = 0f
                while (x <= activeW) {
                    val y = if (currentAmplitude > 0.05f) {
                        centerY + currentAmplitude * sin((2 * PI * (x / wavelengthPx) - phase).toFloat())
                    } else {
                        centerY
                    }
                    wavePath.lineTo(x, y)
                    x += step
                }

                drawPath(
                    path = wavePath,
                    color = activeColor,
                    style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round)
                )
            }

            // 3. Morphing Thumb at activeW
            val thumbRadiusCircle = with(density) { 7.dp.toPx() }
            val thumbWidthBar = with(density) { 5.dp.toPx() }
            val thumbHeightBar = with(density) { 18.dp.toPx() }

            val currentThumbW = thumbRadiusCircle * 2f * (1f - thumbMorph) + thumbWidthBar * thumbMorph
            val currentThumbH = thumbRadiusCircle * 2f * (1f - thumbMorph) + thumbHeightBar * thumbMorph

            val thumbY = if (currentAmplitude > 0.05f && activeW > 0f) {
                centerY + currentAmplitude * sin((2 * PI * (activeW / wavelengthPx) - phase).toFloat())
            } else {
                centerY
            }

            val thumbLeft = (activeW - currentThumbW / 2f).coerceIn(0f, totalW - currentThumbW)
            val thumbTop = thumbY - currentThumbH / 2f

            val thumbPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = thumbLeft,
                        top = thumbTop,
                        right = thumbLeft + currentThumbW,
                        bottom = thumbTop + currentThumbH,
                        cornerRadius = CornerRadius(currentThumbW / 2f, currentThumbW / 2f)
                    )
                )
            }
            drawPath(path = thumbPath, color = thumbColor)
        }
    }
}
