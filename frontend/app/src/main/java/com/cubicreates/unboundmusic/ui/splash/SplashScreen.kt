/*
 * Package: com.cubicreates.unboundmusic.ui.splash
 * File: SplashScreen.kt
 * Purpose: Studio-Mode technical brutalist Splash Screen for Unbound Music.
 *          Ultra-optimized with 0 infinite render loops, phase-skipping GPU draw,
 *          and tactile spring entry physics.
 * Subsystem: Startup / Splash UI
 */

package com.cubicreates.unboundmusic.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.R
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(
    modifier: Modifier = Modifier,
    statusText: String = "SYSTEM_INIT",
    progress: Float = 0f,
    onInitializationComplete: () -> Unit = {}
) {
    // Event-driven entry springs (0% CPU at rest)
    val entryAlpha = remember { Animatable(0f) }
    val entryScale = remember { Animatable(0.90f) }

    LaunchedEffect(Unit) {
        launch {
            entryAlpha.animateTo(1f, tween(320))
        }
        launch {
            entryScale.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            )
        }
    }

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
                        Color(0xFF0E1318),
                        Color(0xFF07090C),
                        Color(0xFF020304)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = entryAlpha.value
                    scaleX = entryScale.value
                    scaleY = entryScale.value
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(144.dp)
                    .shadow(
                        elevation = 24.dp,
                        spotColor = Color(0xFF4CD6FB).copy(alpha = 0.30f),
                        shape = RoundedCornerShape(32.dp)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(Color(0xFF0E1318)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.unbound_logo),
                    contentDescription = "Unbound Music",
                    modifier = Modifier.size(108.dp),
                    contentScale = ContentScale.Fit
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Unbound Music",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF1F5F9),
                letterSpacing = (-0.5).sp
            )
        }
    }
}
