/*
 * Package: com.cubicreates.unboundmusic.ui.equalizer
 * File: EqualizerScreen.kt
 * Purpose: Interactive 10-band software parametric equalizer with live frequency curve visualization,
 *          gain sliders (-12dB to +12dB), preamp gain (-15dB to +12dB), and AutoEq preset launcher.
 * Subsystem: Pro Audio DSP / Equalizer UI
 */

package com.cubicreates.unboundmusic.ui.equalizer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.audio.EQUALIZER_BANDS_HZ
import com.cubicreates.unboundmusic.audio.EQUALIZER_BAND_LABELS
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

@Composable
fun EqualizerScreen(
    modifier: Modifier = Modifier,
    initialCurve: EqualizerCurve = EqualizerCurve.FLAT,
    onCurveChanged: (EqualizerCurve) -> Unit = {},
    onAutoEqClick: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    var bands by remember { mutableStateOf(initialCurve.bandsDb.ifEmpty { List(10) { 0f } }) }
    var preamp by remember { mutableFloatStateOf(initialCurve.preampDb) }

    fun notifyUpdate(newBands: List<Float>, newPreamp: Float) {
        bands = newBands
        preamp = newPreamp
        onCurveChanged(EqualizerCurve(newBands, newPreamp))
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Action Bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OnSurface
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "10-Band Parametric EQ",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }

                IconButton(
                    onClick = {
                        notifyUpdate(List(10) { 0f }, 0f)
                    },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset EQ",
                        tint = OnSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // AutoEq Quick-Calibrate Action Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = UnboundPrimary.copy(alpha = 0.4f), shape = RoundedCornerShape(16.dp))
                    .clickable(onClick = onAutoEqClick)
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(UnboundPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headphones,
                                contentDescription = "AutoEq",
                                tint = OnPrimary,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(
                                text = "AutoEq Calibration",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                            Text(
                                text = "Search 4,000+ headphone models for Harman curve",
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Dynamic Spline Frequency Response Curve Graph
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
                    .padding(16.dp)
                    .drawBehind {
                        val w = size.width
                        val h = size.height
                        val midY = h / 2f

                        // Draw reference zero dB line
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(0f, midY),
                            end = Offset(w, midY),
                            strokeWidth = 1.5f
                        )

                        // Draw Bezier frequency curve
                        val path = Path()
                        val stepX = w / (bands.size - 1)

                        bands.forEachIndexed { i, gain ->
                            val x = i * stepX
                            // Map -12dB..+12dB to bottom..top
                            val y = midY - (gain / 12f) * (midY * 0.8f)
                            if (i == 0) {
                                path.moveTo(x, y)
                            } else {
                                val prevX = (i - 1) * stepX
                                val prevY = midY - (bands[i - 1] / 12f) * (midY * 0.8f)
                                val cX = (prevX + x) / 2f
                                path.cubicTo(cX, prevY, cX, y, x, y)
                            }
                        }

                        drawPath(
                            path = path,
                            color = Color(0xFF4CD6FB),
                            style = Stroke(width = 3.5f)
                        )
                    }
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Preamp Gain Slider (-15dB to +12dB)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(14.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Preamp Gain",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Text(
                        text = String.format("%+.1f dB", preamp),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundPrimary
                    )
                }

                Slider(
                    value = preamp,
                    onValueChange = { notifyUpdate(bands, it) },
                    valueRange = -15f..12f,
                    colors = SliderDefaults.colors(
                        thumbColor = UnboundPrimary,
                        activeTrackColor = UnboundPrimary,
                        inactiveTrackColor = UnboundSurfaceContainerHigh
                    )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 10 Frequency Band Sliders
            Text(
                text = "FREQUENCY BANDS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.1.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            )

            EQUALIZER_BAND_LABELS.forEachIndexed { index, label ->
                val currentGain = bands.getOrElse(index) { 0f }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = label,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface,
                        modifier = Modifier.width(54.dp)
                    )

                    Slider(
                        value = currentGain,
                        onValueChange = { newVal ->
                            val updated = bands.toMutableList()
                            updated[index] = newVal
                            notifyUpdate(updated, preamp)
                        },
                        valueRange = -12f..12f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = UnboundPrimary,
                            activeTrackColor = UnboundPrimary,
                            inactiveTrackColor = UnboundSurfaceContainerHigh
                        )
                    )

                    Text(
                        text = String.format("%+.0fdB", currentGain),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (currentGain != 0f) UnboundPrimary else OnSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.width(48.dp)
                    )
                }
            }
        }
    }
}
