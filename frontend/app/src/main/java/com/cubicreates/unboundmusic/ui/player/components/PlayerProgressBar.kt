/*
 * Package: com.cubicreates.unboundmusic.ui.player.components
 * File: PlayerProgressBar.kt
 * Purpose: Seekbar with buffered progress indicator, tactile thumb scrubbing, and timestamp labels.
 * Subsystem: Player UI Component
 * Concurrency: Stateless composable.
 */

package com.cubicreates.unboundmusic.ui.player.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

@Composable
fun PlayerProgressBar(
    currentPositionMs: Long,
    totalDurationMs: Long,
    bufferedPositionMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    var isDragging by remember { mutableFloatStateOf(-1f) }
    val progress = when {
        isDragging >= 0f -> isDragging
        totalDurationMs > 0 -> (currentPositionMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
        else -> 0f
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progress,
                onValueChange = { newProgress ->
                    isDragging = newProgress
                },
                onValueChangeFinished = {
                    if (isDragging >= 0f && totalDurationMs > 0) {
                        val seekTarget = (isDragging * totalDurationMs).toLong()
                        onSeek(seekTarget)
                    }
                    isDragging = -1f
                },
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayCurrent = if (isDragging >= 0f && totalDurationMs > 0) {
                (isDragging * totalDurationMs).toLong()
            } else currentPositionMs

            Text(
                text = formatDurationMs(displayCurrent),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
            Text(
                text = formatDurationMs(totalDurationMs),
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

/** Formats milliseconds into standard mm:ss or hh:mm:ss display string. */
fun formatDurationMs(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val totalSec = ms / 1000
    val hours = totalSec / 3600
    val minutes = (totalSec % 3600) / 60
    val seconds = totalSec % 60
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}
