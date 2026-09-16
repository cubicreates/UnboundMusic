/*
 * Package: com.cubicreates.unboundmusic.ui.components
 * File: RotaryKnob.kt
 * Purpose: Reusable pro-audio rotary knob composable with circular touch gestures,
 *          neon glowing arcs, tick marks, and value percentage display.
 * Subsystem: Pro Audio DSP / UI Controls
 */

package com.cubicreates.unboundmusic.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun RotaryKnob(
    value: Float, // 0.0f to 1.0f
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    label: String = "",
    size: Dp = 90.dp,
    activeColor: Color = Color(0xFF00C6FF),
    inactiveColor: Color = Color(0xFF232838),
    indicatorColor: Color = Color(0xFF00E5FF),
    valueText: String? = null
) {
    var currentVal by remember(value) { mutableFloatStateOf(value.coerceIn(0f, 1f)) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        // Vertical drag sensitivity: dragging up increases, down decreases
                        val delta = -dragAmount.y / 200f
                        val newVal = (currentVal + delta).coerceIn(0f, 1f)
                        currentVal = newVal
                        onValueChange(newVal)
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(size)) {
                val center = Offset(size.toPx() / 2f, size.toPx() / 2f)
                val radius = (size.toPx() / 2f) - 10.dp.toPx()
                val knobRadius = radius * 0.72f

                // Outer tick arc angle range: 135 deg to 405 deg (270 deg sweep)
                val startAngle = 135f
                val sweepAngle = 270f
                val activeSweep = sweepAngle * currentVal

                // 1. Draw Inactive background arc
                drawArc(
                    color = inactiveColor,
                    startAngle = startAngle,
                    sweepAngle = sweepAngle,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
                )

                // 2. Draw Active glowing arc
                if (activeSweep > 0) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(activeColor.copy(alpha = 0.6f), activeColor, indicatorColor),
                            center = center
                        ),
                        startAngle = startAngle,
                        sweepAngle = activeSweep,
                        useCenter = false,
                        topLeft = Offset(center.x - radius, center.y - radius),
                        size = Size(radius * 2, radius * 2),
                        style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // 3. Draw tick dots around the arc
                val totalTicks = 11
                for (i in 0 until totalTicks) {
                    val tickAngle = (startAngle + (sweepAngle / (totalTicks - 1)) * i) * (PI / 180f)
                    val tickDist = radius + 6.dp.toPx()
                    val tickPos = Offset(
                        x = (center.x + tickDist * cos(tickAngle)).toFloat(),
                        y = (center.y + tickDist * sin(tickAngle)).toFloat()
                    )
                    val isTickActive = (sweepAngle / (totalTicks - 1)) * i <= activeSweep + 2f
                    drawCircle(
                        color = if (isTickActive) indicatorColor else inactiveColor,
                        radius = if (isTickActive) 2.2.dp.toPx() else 1.5.dp.toPx(),
                        center = tickPos
                    )
                }

                // 4. Draw Center metallic knob body
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF2C3246), Color(0xFF161A24)),
                        center = center,
                        radius = knobRadius
                    ),
                    radius = knobRadius,
                    center = center
                )

                // Knob edge ring
                drawCircle(
                    color = Color(0xFF3E4760),
                    radius = knobRadius,
                    center = center,
                    style = Stroke(width = 1.5.dp.toPx())
                )

                // 5. Draw Pointer Dot / Line on knob
                val currentAngleRad = (startAngle + activeSweep) * (PI / 180f)
                val dotDist = knobRadius * 0.65f
                val dotPos = Offset(
                    x = (center.x + dotDist * cos(currentAngleRad)).toFloat(),
                    y = (center.y + dotDist * sin(currentAngleRad)).toFloat()
                )
                // Glow around dot
                drawCircle(
                    color = indicatorColor.copy(alpha = 0.4f),
                    radius = 4.5.dp.toPx(),
                    center = dotPos
                )
                drawCircle(
                    color = indicatorColor,
                    radius = 3.dp.toPx(),
                    center = dotPos
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (label.isNotBlank()) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurfaceVariant
            )
        }

        if (valueText != null) {
            Text(
                text = valueText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = activeColor
            )
        }
    }
}
