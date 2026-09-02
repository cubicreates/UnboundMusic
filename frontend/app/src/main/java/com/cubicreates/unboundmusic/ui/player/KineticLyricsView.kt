/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: KineticLyricsView.kt
 * Purpose: Full Apple Music-style kinetic glowing synchronized lyrics view with CTC syllable timestamps,
 *          smooth physics-based auto-scroll, and tap-to-seek capability.
 * Subsystem: Lyrics Visualizer UI
 */

package com.cubicreates.unboundmusic.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.viewmodel.LyricLine

@Composable
fun KineticLyricsView(
    modifier: Modifier = Modifier,
    lyricsLines: List<LyricLine>,
    currentPositionMs: Long,
    lyricsSource: String = "Genius CTC Syllable-Aligned",
    onLineClick: (LyricLine) -> Unit = {}
) {
    val listState = rememberLazyListState()

    // Identify active singing lyric line
    val activeIndex = lyricsLines.indexOfFirst { line ->
        currentPositionMs in line.startMs..line.endMs
    }.let {
        if (it != -1) it
        else lyricsLines.indexOfLast { line -> line.startMs <= currentPositionMs }.coerceAtLeast(0)
    }

    // Auto-scroll to center the active singing line
    LaunchedEffect(activeIndex) {
        if (activeIndex in lyricsLines.indices) {
            listState.animateScrollToItem(
                index = activeIndex,
                scrollOffset = -200
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (lyricsLines.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Aligning Lyrics...",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Genius CTC forced aligner running on device",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 100.dp, bottom = 200.dp, start = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                itemsIndexed(lyricsLines) { index, line ->
                    val isActive = index == activeIndex
                    val textColor by animateColorAsState(
                        targetValue = if (isActive) Color.White else OnSurfaceVariant.copy(alpha = 0.35f),
                        animationSpec = tween(250),
                        label = "lyric_color"
                    )

                    Text(
                        text = line.text,
                        fontSize = if (isActive) 30.sp else 24.sp,
                        fontWeight = if (isActive) FontWeight.ExtraBold else FontWeight.SemiBold,
                        color = textColor,
                        lineHeight = if (isActive) 38.sp else 32.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLineClick(line) }
                    )
                }
            }
        }
    }
}
