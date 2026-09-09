/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: KineticLyricsView.kt
 * Purpose: Apple Music-style kinetic typography lyrics view with phonetic Romanization (Romaji/Revised Romanization/Indic),
 *          auto-centering, ambient text glow (#22D3EE), interactive tap-to-seek, timing offset sync, and instrumental badge.
 * Subsystem: Lyrics & Typography UI
 * Concurrency: Jetpack Compose reactive state observation.
 */

package com.cubicreates.unboundmusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.RomanizationMode
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlass
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.viewmodel.LyricLine

@Composable
fun KineticLyricsView(
    modifier: Modifier = Modifier,
    lyricsLines: List<LyricLine>,
    currentPositionMs: Long,
    lyricsSource: String = "LRCLIB Synced Lyrics",
    romanizationMode: RomanizationMode = RomanizationMode.ORIGINAL,
    timingOffsetMs: Long = 0L,
    isInstrumental: Boolean = false,
    onRomanizationModeChange: (RomanizationMode) -> Unit = {},
    onTimingOffsetChange: (Long) -> Unit = {},
    onLineClick: (LyricLine) -> Unit = {}
) {
    val listState = rememberLazyListState()
    var showOffsetSlider by remember { mutableStateOf(false) }

    // Effective position with manual desynchronization compensation
    val effectivePositionMs = currentPositionMs + timingOffsetMs

    // Identify active singing lyric line
    val activeIndex = lyricsLines.indexOfFirst { line ->
        effectivePositionMs in line.startMs..line.endMs
    }.let {
        if (it != -1) it
        else lyricsLines.indexOfLast { line -> line.startMs <= effectivePositionMs }.coerceAtLeast(0)
    }

    // Auto-scroll to center the active singing line smoothly
    LaunchedEffect(activeIndex) {
        if (lyricsLines.isNotEmpty() && activeIndex in lyricsLines.indices) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -220
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isInstrumental) {
            // Instrumental Track Pure Audio Badge
            InstrumentalBadge(modifier = Modifier.align(Alignment.Center))
        } else if (lyricsLines.isEmpty()) {
            // Subtle pulsing shimmer lines while auto-fetching/aligning lyrics
            LyricsShimmerPlaceholder(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 110.dp, bottom = 220.dp, start = 24.dp, end = 24.dp)
            )
        } else {
            // Kinetic Line-by-Line Synchronized Lyrics List
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 110.dp, bottom = 220.dp, start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                itemsIndexed(lyricsLines) { index, line ->
                    val isActive = index == activeIndex

                    val textColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else OnSurfaceVariant.copy(alpha = 0.35f),
                        animationSpec = tween(250),
                        label = "lyric_text_color"
                    )

                    val textScale by animateFloatAsState(
                        targetValue = if (isActive) 1.05f else 0.98f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "lyric_scale"
                    )

                    val textShadow = if (isActive) {
                        Shadow(
                            color = Color(0xFF22D3EE).copy(alpha = 0.65f),
                            offset = Offset(0f, 0f),
                            blurRadius = 18f
                        )
                    } else null

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(textScale)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onLineClick(line) }
                            .padding(vertical = 4.dp)
                    ) {
                        // Display singing text (phonetic Romanized where available for sing-along)
                        val displayText = line.romanized.ifBlank { line.text }
                        Text(
                            text = displayText,
                            fontSize = if (isActive) 28.sp else 23.sp,
                            fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                            color = textColor,
                            lineHeight = if (isActive) 36.sp else 30.sp,
                            style = TextStyle(shadow = textShadow)
                        )
                    }
                }
            }
        }

        // Top Control Glass Bar: Lyrics Source Badge & Timing Offset Toggle
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xEE131313),
                            Color(0xBB131313),
                            Color.Transparent
                        )
                    )
                )
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Source badge / indicator
                Text(
                    text = lyricsSource,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant.copy(alpha = 0.7f)
                )

                // Timing Offset Slider Toggle Button
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (timingOffsetMs != 0L) UnboundPrimary.copy(alpha = 0.2f) else SurfaceGlassHighest)
                        .border(
                            width = 1.dp,
                            color = if (timingOffsetMs != 0L) UnboundPrimary else BorderGlass,
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { showOffsetSlider = !showOffsetSlider }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Sync Offset",
                        tint = if (timingOffsetMs != 0L) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (timingOffsetMs == 0L) "SYNC" else "${if (timingOffsetMs > 0) "+" else ""}${timingOffsetMs / 1000.0}s",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (timingOffsetMs != 0L) UnboundPrimary else OnSurfaceVariant
                    )
                }
            }

            // Expandable Manual Timing Offset Slider (-2.0s to +2.0s)
            AnimatedVisibility(
                visible = showOffsetSlider,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "TIMING OFFSET ADJUSTMENT",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "${if (timingOffsetMs > 0) "+" else ""}${timingOffsetMs} ms",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = UnboundPrimary
                        )
                    }
                    Slider(
                        value = timingOffsetMs.toFloat(),
                        onValueChange = { newVal ->
                            // Snap to 100ms intervals
                            val stepped = (kotlin.math.round(newVal / 100f) * 100f).toLong()
                            onTimingOffsetChange(stepped)
                        },
                        valueRange = -2000f..2000f,
                        steps = 39, // 100ms steps between -2000 and +2000
                        colors = SliderDefaults.colors(
                            thumbColor = UnboundPrimary,
                            activeTrackColor = UnboundPrimary,
                            inactiveTrackColor = Color(0x33FFFFFF)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "-2.0s", fontSize = 10.sp, color = OnSurfaceVariant)
                        Text(
                            text = "RESET (0.0s)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = UnboundPrimary,
                            modifier = Modifier.clickable { onTimingOffsetChange(0L) }
                        )
                        Text(text = "+2.0s", fontSize = 10.sp, color = OnSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun RomanizationChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = if (isSelected) UnboundPrimary.copy(alpha = 0.22f) else SurfaceGlass
    val borderColor = if (isSelected) UnboundPrimary else BorderGlass
    val textColor = if (isSelected) UnboundPrimary else OnSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(width = 1.dp, color = borderColor, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium,
            color = textColor,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun InstrumentalBadge(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(24.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = Color(0xFF22D3EE).copy(alpha = 0.5f), shape = RoundedCornerShape(20.dp))
            .padding(horizontal = 28.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(UnboundPrimary.copy(alpha = 0.15f))
                .border(width = 1.5.dp, color = Color(0xFF22D3EE), shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Instrumental Track",
                tint = Color(0xFF22D3EE),
                modifier = Modifier.size(32.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "INSTRUMENTAL TRACK - PURE AUDIO",
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White,
            letterSpacing = 1.5.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Zero vocal lyrics detected for this composition.",
            fontSize = 12.sp,
            color = OnSurfaceVariant
        )
    }
}

@Composable
private fun LyricsShimmerPlaceholder(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "lyrics_shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer_alpha"
    )

    val widths = listOf(0.70f, 0.88f, 0.55f, 0.82f, 0.65f, 0.78f, 0.48f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(28.dp)
    ) {
        widths.forEach { widthFraction ->
            Box(
                modifier = Modifier
                    .fillMaxWidth(widthFraction)
                    .height(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(alpha = alpha))
            )
        }
    }
}
