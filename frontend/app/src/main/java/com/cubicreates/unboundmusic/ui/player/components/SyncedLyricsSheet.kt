/*
 * Package: com.cubicreates.unboundmusic.ui.player.components
 * File: SyncedLyricsSheet.kt
 * Purpose: Fullscreen and embedded synchronized lyrics renderer with click-to-seek,
 *          phonetic romanization, and smooth auto-scrolling to active lines.
 * Subsystem: Player UI Component
 * Concurrency: Thread-safe Compose UI list with automatic scroll tracking.
 */

package com.cubicreates.unboundmusic.ui.player.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.LyricsPayloadDto

@Composable
fun SyncedLyricsSheet(
    lyricsPayload: LyricsPayloadDto?,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    if (lyricsPayload == null || (lyricsPayload.lines.isEmpty() && lyricsPayload.plainLyrics.isBlank())) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No lyrics available for this track",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    // Plain lyrics fallback if no synchronized timing timestamps are available
    if (lyricsPayload.lines.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = lyricsPayload.plainLyrics,
                style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 28.sp),
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val lines = lyricsPayload.lines
    val activeIndex = lines.indexOfLast { currentPositionMs >= it.startMs }.coerceAtLeast(0)
    val listState = rememberLazyListState()

    // Smoothly scroll active line towards center of the viewport
    LaunchedEffect(activeIndex) {
        if (activeIndex in lines.indices) {
            val targetScroll = (activeIndex - 2).coerceAtLeast(0)
            listState.animateScrollToItem(targetScroll)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 120.dp, horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        itemsIndexed(lines) { index, line ->
            val isActive = index == activeIndex
            val textColor by animateColorAsState(
                targetValue = if (isActive) Color.White else Color.White.copy(alpha = 0.35f),
                label = "lyricTextColor"
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSeekTo(line.startMs) }
                    .padding(vertical = 4.dp)
            ) {
                Text(
                    text = line.text,
                    fontSize = if (isActive) 26.sp else 20.sp,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                    color = textColor,
                    lineHeight = if (isActive) 34.sp else 28.sp
                )

                if (line.romanized.isNotBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = line.romanized,
                        fontSize = if (isActive) 18.sp else 14.sp,
                        fontWeight = FontWeight.Normal,
                        color = textColor.copy(alpha = if (isActive) 0.85f else 0.25f)
                    )
                }
            }
        }
    }
}
