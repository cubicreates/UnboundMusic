/*
 * Package: com.cubicreates.unboundmusic.ui.splash
 * File: SplashScreen.kt
 * Purpose: Studio-mode technical brutalist Splash Screen for Unbound Music.
 *          Features 32dp canvas grid, corner tick brackets, cyan pulse glow, technical progress bar,
 *          and telemetry phase readout.
 * Subsystem: Startup / Splash UI
 */

package com.cubicreates.unboundmusic.ui.splash

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

private const val SPLASH_LOGO_URL = "https://lh3.googleusercontent.com/aida-public/AB6AXuCiCHsQD6ywAgAk-W6VoIUdO1HFQY3AI2rLY_bbdoWFW0xgkO8UJYAd3UknVFBw2t0B_WLsOrbRERpoUHFswsBKpfSwWOAHb-sJwqi7jhP8BNO_UW0xdMqfqRGuYPpfoFdX1lAlk0UvkRm7Qvw8H1j-cz5zwhr0rV8ITYg_ELFlOv1XCPI73JT4DDjOc6uCxRQSv0Vl1TucvQdHpseSMrq-Bv1SipZ8mNKhXz5VBYZdY_A059PL9UMC7g"

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    statusText: String = "SYSTEM_INIT",
    progress: Float = 0f,
    onInitializationComplete: () -> Unit = {}
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "progress_anim"
    )

    // Cyan border pulse effect
    val infiniteTransition = rememberInfiniteTransition(label = "pulse_border")
    val pulseGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    LaunchedEffect(progress) {
        if (progress >= 1f) {
            onInitializationComplete()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0A0A0A),
                        Color(0xFF000000)
                    )
                )
            )
    ) {
        // 1. Studio-Mode 32dp Grid Overlay
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gridSpacing = 32.dp.toPx()
            val gridColor = Color(0xFF3D494D).copy(alpha = 0.18f)

            // Vertical grid lines
            var x = 0f
            while (x <= size.width) {
                drawLine(
                    color = gridColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += gridSpacing
            }

            // Horizontal grid lines
            var y = 0f
            while (y <= size.height) {
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += gridSpacing
            }
        }

        // 2. Central Technical Brutalist Presentation
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Brutalist Logo Container with Corner Brackets
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .shadow(
                        elevation = 24.dp,
                        spotColor = Color(0xFF4CD6FB).copy(alpha = pulseGlowAlpha * 0.4f),
                        shape = RoundedCornerShape(2.dp)
                    )
                    .background(Color(0xFF0E0E0E))
                    .border(
                        width = 1.dp,
                        color = Color(0xFF4CD6FB).copy(alpha = pulseGlowAlpha)
                    )
                    .drawBehind {
                        // Technical L-shaped corner accent brackets (length: 10dp, thickness: 2dp)
                        val tickLen = 10.dp.toPx()
                        val tickWidth = 2.dp.toPx()
                        val tickColor = Color(0xFF4CD6FB)

                        // Top-Left corner
                        drawLine(tickColor, Offset(0f, 0f), Offset(tickLen, 0f), tickWidth)
                        drawLine(tickColor, Offset(0f, 0f), Offset(0f, tickLen), tickWidth)

                        // Top-Right corner
                        drawLine(tickColor, Offset(size.width, 0f), Offset(size.width - tickLen, 0f), tickWidth)
                        drawLine(tickColor, Offset(size.width, 0f), Offset(size.width, tickLen), tickWidth)

                        // Bottom-Left corner
                        drawLine(tickColor, Offset(0f, size.height), Offset(tickLen, size.height), tickWidth)
                        drawLine(tickColor, Offset(0f, size.height), Offset(0f, size.height - tickLen), tickWidth)

                        // Bottom-Right corner
                        drawLine(tickColor, Offset(size.width, size.height), Offset(size.width - tickLen, size.height), tickWidth)
                        drawLine(tickColor, Offset(size.width, size.height), Offset(size.width, size.height - tickLen), tickWidth)
                    },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = SPLASH_LOGO_URL,
                    contentDescription = "Unbound Music Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .shadow(elevation = 16.dp, spotColor = Color(0xFF4CD6FB).copy(alpha = 0.5f)),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Brand Typography
            Text(
                text = "UNBOUND",
                fontSize = 44.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFFB2EBFF),
                letterSpacing = (-0.04).sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Technical Horizontal Loading Bar (192dp x 4dp)
            Box(
                modifier = Modifier
                    .width(192.dp)
                    .height(4.dp)
                    .background(Color(0xFF1F1F1F))
                    .border(width = 1.dp, color = Color(0xFF3D494D))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(4.dp)
                        .background(Color(0xFF4CD6FB))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Monospace Telemetry Readout
            Row(
                modifier = Modifier.width(192.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.1.sp,
                    color = Color(0xFFBCC9CE)
                )

                val displayPercent = (animatedProgress * 100).toInt()
                Text(
                    text = if (displayPercent >= 100) "READY" else "$displayPercent%",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF4CD6FB)
                )
            }
        }
    }
}
