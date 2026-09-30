/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: SpotifyCanvasPlayer.kt
 * Purpose: Fullscreen Spotify Canvas presentation style with tactile scrub slider,
 *          5-button control deck, animated P2P/SponsorBlock status pills, and live-scrolling lyrics preview card.
 * Subsystem: Multi-Style Player UI
 * Concurrency: Thread-safe Jetpack Compose layout.
 */

package com.cubicreates.unboundmusic.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.FallbackStatusDto
import com.cubicreates.unboundmusic.data.RydVoteData
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.ui.components.DownloadButton
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundTrackThumbnail
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.viewmodel.LyricLine
import com.cubicreates.unboundmusic.viewmodel.SkitSkipNotice

@Composable
internal fun SpotifyPlayerContent(
    track: TrackItem,
    isPlaying: Boolean,
    isFavorite: Boolean,
    progress: Float,
    currentPositionMs: Long,
    formattedPosition: String,
    formattedRemaining: String,
    lyricsLines: List<LyricLine>,
    timingOffsetMs: Long,
    isInstrumental: Boolean,
    playbackMode: PlaybackMode,
    downloadStatus: DownloadUiStatus,
    downloadProgress: Double,
    skippedSkitNotice: SkitSkipNotice?,
    sleepTimerActive: Boolean,
    onCollapse: () -> Unit,
    onOpenStylePicker: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    onSeek: (Float) -> Unit,
    onSeekPositionMs: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onPreviousTrack: () -> Unit,
    onPlayPauseToggle: () -> Unit,
    onNextTrack: () -> Unit,
    onCycleRepeatMode: () -> Unit,
    onEqualizerClick: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onStartRadio: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    onOpenPlaybackSpeed: () -> Unit = {},
    onOpenQueue: () -> Unit,
    onOpenFullLyrics: () -> Unit,
    onUndoSkip: () -> Unit,
    onDismissSkipNotice: () -> Unit,
    rydData: RydVoteData? = null,
    onOpenRydStats: () -> Unit = {},
    fallbackStatus: FallbackStatusDto? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Action Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = OnSurface
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "PLAYING FROM QUEUE",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Unbound Music",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary.copy(alpha = 0.9f)
                )
            }

            IconButton(
                onClick = onOpenStylePicker,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = "Choose Player Style",
                    tint = UnboundPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Balanced Spotify Album Artwork (~76% width)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.76f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 24.dp,
                    shape = RoundedCornerShape(16.dp),
                    spotColor = Color.Black.copy(alpha = 0.65f)
                )
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlassHighest)
                .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
        ) {
            UnboundTrackThumbnail(
                coverUrl = track.coverUrl,
                contentDescription = track.title,
                shape = RoundedCornerShape(16.dp),
                iconSize = 80.dp,
                modifier = Modifier.fillMaxSize()
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(Color.White.copy(alpha = 0.08f), Color.Transparent)
                        )
                    )
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Track Details & Actions Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = track.artist,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DownloadButton(
                    status = downloadStatus,
                    progress = downloadProgress,
                    onStartDownload = onStartDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteDownload = onDeleteDownload,
                    trackTitle = track.title,
                    size = 42.dp
                )

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(42.dp)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Return YouTube Dislike (RYD) Community Sentiment Pill
        if (rydData != null) {
            Spacer(modifier = Modifier.height(10.dp))
            RydCommunitySentimentPill(
                rydData = rydData,
                onClick = onOpenRydStats,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // P2P / Spotify Multi-Stage Fallback Connection Status Pill
        AnimatedVisibility(
            visible = fallbackStatus != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 })
        ) {
            fallbackStatus?.let { status ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                        .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Radio,
                        contentDescription = null,
                        tint = Color(0xFF00E5FF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = status.message,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // SponsorBlock Non-Music Skit Skip Toast Pill
        AnimatedVisibility(
            visible = skippedSkitNotice != null,
            enter = fadeIn() + slideInVertically(initialOffsetY = { -20 }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { -20 })
        ) {
            skippedSkitNotice?.let { notice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF1E1E1E).copy(alpha = 0.95f))
                        .border(1.dp, UnboundPrimary.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FastForward,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notice.message,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "UNDO",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundPrimary,
                        modifier = Modifier
                            .clickable(onClick = onUndoSkip)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                    IconButton(
                        onClick = onDismissSkipNotice,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Tactile Progress Slider & Timestamps
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progress,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = OnSurface,
                    activeTrackColor = OnSurface,
                    inactiveTrackColor = OnSurface.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formattedPosition,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )
                Text(
                    text = formattedRemaining,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Spotify 5-Button Control Deck
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(44.dp)
            ) {
                val isShuffle = playbackMode == PlaybackMode.SHUFFLE
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (isShuffle) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    if (isShuffle) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(UnboundPrimary)
                        )
                    }
                }
            }

            IconButton(
                onClick = onPreviousTrack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = OnSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            Box(
                modifier = Modifier
                    .size(64.dp)
                    .shadow(
                        elevation = 16.dp,
                        shape = CircleShape,
                        spotColor = UnboundPrimary.copy(alpha = 0.45f)
                    )
                    .clip(CircleShape)
                    .background(UnboundPrimary)
                    .clickable(onClick = onPlayPauseToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = OnPrimary,
                    modifier = Modifier.size(34.dp)
                )
            }

            IconButton(
                onClick = onNextTrack,
                modifier = Modifier.size(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = OnSurface,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = onCycleRepeatMode,
                modifier = Modifier.size(44.dp)
            ) {
                val isRepeat = playbackMode in listOf(PlaybackMode.LOOP_ALL, PlaybackMode.LOOP_ONE)
                val repeatIcon = if (playbackMode == PlaybackMode.LOOP_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = repeatIcon,
                        contentDescription = "Repeat",
                        tint = if (isRepeat) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                    if (isRepeat) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(UnboundPrimary)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Quick Tool Tray: EQ / Sleep Timer / Up Next Queue / Fullscreen Lyrics
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onEqualizerClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Equalizer",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenSleepTimer,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = "Sleep Timer",
                    tint = if (sleepTimerActive) UnboundPrimary else OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onStartRadio,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = "Start Radio",
                    tint = UnboundPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenPlaybackSpeed,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Speed,
                    contentDescription = "Playback Speed",
                    tint = if (playbackSpeed != 1.0f) UnboundPrimary else OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Queue",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
            }

            IconButton(
                onClick = onOpenFullLyrics,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.OpenInFull,
                    contentDescription = "Expand Lyrics",
                    tint = if (lyricsLines.isNotEmpty()) UnboundPrimary else OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Embedded Spotify-Style Live-Scrolling Lyrics Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E1E).copy(alpha = 0.82f))
                .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                .clickable { onOpenFullLyrics() }
                .padding(18.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Lyrics",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )

                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(SurfaceGlassHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.OpenInFull,
                            contentDescription = "Expand Lyrics",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                if (isInstrumental) {
                    Text(
                        text = "This is an instrumental track with no lyrics.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else if (lyricsLines.isEmpty()) {
                    Text(
                        text = "No synced lyrics available for this track.",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    val effectivePos = currentPositionMs + timingOffsetMs
                    val activeIndex = lyricsLines.indexOfFirst { effectivePos in it.startMs..it.endMs }.let {
                        if (it != -1) it
                        else lyricsLines.indexOfLast { line -> line.startMs <= effectivePos }.coerceAtLeast(0)
                    }

                    val startIdx = (activeIndex - 1).coerceAtLeast(0)
                    val endIdx = (activeIndex + 2).coerceAtMost(lyricsLines.lastIndex)

                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        for (i in startIdx..endIdx) {
                            val line = lyricsLines[i]
                            val isCurrent = i == activeIndex
                            val displayText = if (line.romanized.isNotBlank()) line.romanized else line.text

                            Text(
                                text = displayText.ifBlank { "..." },
                                fontSize = if (isCurrent) 18.sp else 15.sp,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                color = if (isCurrent) UnboundPrimary else OnSurface.copy(alpha = 0.45f),
                                lineHeight = if (isCurrent) 24.sp else 20.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSeekPositionMs(line.startMs) }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "TAP FOR FULL LYRICS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = UnboundPrimary.copy(alpha = 0.85f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}
