/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: M3ExpressivePlayer.kt
 * Purpose: Fullscreen Material 3 Expressive presentation style with animated sine-wave seek bar,
 *          squircle pill controls, and tactile quick-action pill tray.
 * Subsystem: Multi-Style Player UI
 * Concurrency: Thread-safe Jetpack Compose layout.
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.RydVoteData
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.ui.components.DownloadButton
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundTrackThumbnail
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.viewmodel.LyricLine

/**
 * Material 3 Expressive Fullscreen Player presentation.
 */
@Composable
internal fun M3ExpressivePlayerContent(
    track: TrackItem,
    isPlaying: Boolean,
    isFavorite: Boolean,
    progress: Float,
    formattedPosition: String,
    formattedRemaining: String,
    lyricsLines: List<LyricLine>,
    currentPositionMs: Long,
    timingOffsetMs: Long,
    isInstrumental: Boolean,
    playbackMode: PlaybackMode,
    downloadStatus: DownloadUiStatus,
    downloadProgress: Double,
    queueSize: Int,
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
    playbackSpeed: Float = 1.0f,
    onOpenPlaybackSpeed: () -> Unit = {},
    onOpenQueue: () -> Unit,
    onOpenFullLyrics: () -> Unit,
    rydData: RydVoteData? = null,
    onOpenRydStats: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // M3 Expressive Header Bar
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
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceGlassHighest)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = OnSurface
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(UnboundPrimary.copy(alpha = 0.16f))
                    .border(1.dp, UnboundPrimary.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "M3 EXPRESSIVE",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary,
                    letterSpacing = 1.2.sp
                )
            }

            IconButton(
                onClick = onOpenStylePicker,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = "Style Picker",
                    tint = UnboundPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // M3 Expressive Album Art (78% width with 28dp pill corners)
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .aspectRatio(1f)
                .shadow(
                    elevation = 28.dp,
                    shape = RoundedCornerShape(28.dp),
                    spotColor = UnboundPrimary.copy(alpha = 0.35f)
                )
                .clip(RoundedCornerShape(28.dp))
                .background(SurfaceGlassHighest)
                .border(1.5.dp, BorderGlass, RoundedCornerShape(28.dp))
        ) {
            UnboundTrackThumbnail(
                coverUrl = track.coverUrl,
                contentDescription = track.title,
                shape = RoundedCornerShape(28.dp),
                iconSize = 80.dp,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Track Info Row
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
                    fontWeight = FontWeight.Medium,
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

        // SLEEK PROGRESS BAR (120 FPS Hardware-Accelerated Pill Track)
        Column(modifier = Modifier.fillMaxWidth()) {
            SleekProgressBar(
                progressFraction = progress,
                isPlaying = isPlaying,
                activeColor = UnboundPrimary,
                trackColor = Color.White.copy(alpha = 0.2f),
                thumbColor = UnboundPrimary,
                onSliderChange = { fraction -> onSeek(fraction) },
                onSliderChangeFinished = {}
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formattedPosition,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceVariant
                )
                Text(
                    text = formattedRemaining,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // M3 Expressive 5-Button Deck (Featuring Squircle Pill Play/Pause)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(44.dp)
            ) {
                val isShuffle = playbackMode == PlaybackMode.SHUFFLE
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) UnboundPrimary else OnSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
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

            // Expressive Squircle Pill Button (76x56dp)
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 56.dp)
                    .shadow(16.dp, RoundedCornerShape(28.dp), spotColor = UnboundPrimary.copy(alpha = 0.5f))
                    .clip(RoundedCornerShape(28.dp))
                    .background(UnboundPrimary)
                    .clickable(onClick = onPlayPauseToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(32.dp)
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
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = if (isRepeat) UnboundPrimary else OnSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // M3 Expressive Quick Pills Tray
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Lyrics Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                    .clickable { onOpenFullLyrics() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Lyrics,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Lyrics",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            // Queue Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                    .clickable { onOpenQueue() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Queue ($queueSize)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
            }

            // Sleep Timer Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (sleepTimerActive) UnboundPrimary.copy(alpha = 0.2f) else SurfaceGlassHighest)
                    .border(
                        1.dp,
                        if (sleepTimerActive) UnboundPrimary else BorderGlass,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onOpenSleepTimer() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        tint = if (sleepTimerActive) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Timer",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (sleepTimerActive) UnboundPrimary else OnSurface
                    )
                }
            }

            // Speed Pill
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (playbackSpeed != 1.0f) UnboundPrimary.copy(alpha = 0.2f) else SurfaceGlassHighest)
                    .border(
                        1.dp,
                        if (playbackSpeed != 1.0f) UnboundPrimary else BorderGlass,
                        RoundedCornerShape(14.dp)
                    )
                    .clickable { onOpenPlaybackSpeed() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = null,
                        tint = if (playbackSpeed != 1.0f) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (playbackSpeed != 1.0f) "${playbackSpeed}x" else "Speed",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (playbackSpeed != 1.0f) UnboundPrimary else OnSurface
                    )
                }
            }
        }
    }
}
