/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: PlaybackSpeedDialog.kt
 * Purpose: Interactive Speed & Pitch Adjustment modal (0.5x to 2.0x) with quick presets and Nightcore mode.
 * Subsystem: Player Controls & Audio DSP
 */

package com.cubicreates.unboundmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import kotlin.math.roundToInt

@Composable
fun PlaybackSpeedDialog(
    currentSpeed: Float,
    currentPitch: Float,
    onSpeedPitchChanged: (speed: Float, pitch: Float) -> Unit,
    onDismiss: () -> Unit
) {
    var speed by remember(currentSpeed) { mutableFloatStateOf(currentSpeed.coerceIn(0.5f, 2.0f)) }
    var pitch by remember(currentPitch) { mutableFloatStateOf(currentPitch.coerceIn(0.5f, 1.5f)) }

    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(UnboundBackground)
                .border(1.dp, BorderGlass, RoundedCornerShape(28.dp))
                .padding(22.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(UnboundPrimary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = null,
                                tint = UnboundPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Playback Speed & Pitch",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                            Text(
                                text = "Fine-tune tempo & voice pitch",
                                fontSize = 11.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(SurfaceGlassHighest)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 1: Playback Speed
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Speed",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Text(
                        text = String.format("%.2fx", speed),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundPrimary
                    )
                }

                Slider(
                    value = speed,
                    onValueChange = {
                        speed = (it * 20).roundToInt() / 20f
                        onSpeedPitchChanged(speed, pitch)
                    },
                    valueRange = 0.5f..2.0f,
                    colors = SliderDefaults.colors(
                        thumbColor = UnboundPrimary,
                        activeTrackColor = UnboundPrimary,
                        inactiveTrackColor = SurfaceGlassHighest
                    )
                )

                // Speed Preset Chips
                val speedPresets = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    speedPresets.forEach { pSpeed ->
                        val isSelected = (speed - pSpeed).let { if (it < 0) -it else it } < 0.04f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) UnboundPrimary else SurfaceGlassHighest)
                                .border(1.dp, if (isSelected) UnboundPrimary else BorderGlass, RoundedCornerShape(10.dp))
                                .clickable {
                                    speed = pSpeed
                                    onSpeedPitchChanged(speed, pitch)
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${pSpeed}x",
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) UnboundBackground else OnSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Section 2: Playback Pitch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Pitch",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Text(
                        text = String.format("%.2fx", pitch),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundPrimary
                    )
                }

                Slider(
                    value = pitch,
                    onValueChange = {
                        pitch = (it * 20).roundToInt() / 20f
                        onSpeedPitchChanged(speed, pitch)
                    },
                    valueRange = 0.5f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = UnboundPrimary,
                        activeTrackColor = UnboundPrimary,
                        inactiveTrackColor = SurfaceGlassHighest
                    )
                )

                // Pitch Preset Chips
                val pitchPresets = listOf(
                    0.8f to "Deep",
                    1.0f to "Normal",
                    1.25f to "Nightcore",
                    1.5f to "Chipmunk"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    pitchPresets.forEach { (pPitch, label) ->
                        val isSelected = (pitch - pPitch).let { if (it < 0) -it else it } < 0.04f
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) UnboundPrimary else SurfaceGlassHighest)
                                .border(1.dp, if (isSelected) UnboundPrimary else BorderGlass, RoundedCornerShape(10.dp))
                                .clickable {
                                    pitch = pPitch
                                    if (label == "Nightcore" && speed == 1.0f) {
                                        speed = 1.25f
                                    }
                                    onSpeedPitchChanged(speed, pitch)
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) UnboundBackground else OnSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Reset Button
                OutlinedButton(
                    onClick = {
                        speed = 1.0f
                        pitch = 1.0f
                        onSpeedPitchChanged(1.0f, 1.0f)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSurfaceVariant),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Reset to Default (1.0x)", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
