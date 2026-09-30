/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: AppleGlassPlayer.kt
 * Purpose: Fullscreen Apple Music Glass presentation style with frosted glass aesthetic,
 *          large centered album artwork, instant synced-lyrics toggle, bold typography, and central 3-button controls.
 * Subsystem: Multi-Style Player UI
 * Concurrency: Thread-safe Jetpack Compose layout.
 */

package com.cubicreates.unboundmusic.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.cubicreates.unboundmusic.data.RomanizationMode
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

@Composable
internal fun AppleMusicPlayerContent(
    track: TrackItem,
    isPlaying: Boolean,
    isFavorite: Boolean,
    progress: Float,
    currentPositionMs: Long,
    formattedPosition: String,
    formattedRemaining: String,
    lyricsLines: List<LyricLine>,
    lyricsSource: String,
    romanizationMode: RomanizationMode,
    timingOffsetMs: Long,
    isInstrumental: Boolean,
    playbackMode: PlaybackMode,
    downloadStatus: DownloadUiStatus,
    downloadProgress: Double,
    onRomanizationModeChange: (RomanizationMode) -> Unit,
    onTimingOffsetChange: (Long) -> Unit,
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
    onOpenQueue: () -> Unit,
    rydData: RydVoteData? = null,
    onOpenRydStats: () -> Unit = {}
) {
    var showInlineLyricsView by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(top = 16.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Apple Music Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onCollapse,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
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
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "APPLE MUSIC GLASS",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.8f),
                    letterSpacing = 1.2.sp
                )
            }

            IconButton(
                onClick = onOpenStylePicker,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
            ) {
                Icon(
                    imageVector = Icons.Default.DashboardCustomize,
                    contentDescription = "Style Picker",
                    tint = UnboundPrimary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Center View: Crossfade between Large Album Artwork and Synced Lyrics Column
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Crossfade(
                targetState = showInlineLyricsView,
                animationSpec = tween(250),
                label = "apple_music_center_crossfade"
            ) { inLyricsMode ->
                if (inLyricsMode) {
                    // Apple Music Full Synced Lyrics Column
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
                            .padding(8.dp)
                    ) {
                        KineticLyricsView(
                            lyricsLines = lyricsLines,
                            currentPositionMs = currentPositionMs,
                            lyricsSource = lyricsSource,
                            romanizationMode = romanizationMode,
                            timingOffsetMs = timingOffsetMs,
                            isInstrumental = isInstrumental,
                            onRomanizationModeChange = onRomanizationModeChange,
                            onTimingOffsetChange = onTimingOffsetChange,
                            onLineClick = { line -> onSeekPositionMs(line.startMs) }
                        )
                    }
                } else {
                    // Apple Music Large Art (84% width) with Deep Shadow
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .aspectRatio(1f)
                            .shadow(
                                elevation = 32.dp,
                                shape = RoundedCornerShape(20.dp),
                                spotColor = Color.Black.copy(alpha = 0.8f)
                            )
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceGlassHighest)
                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                    ) {
                        UnboundTrackThumbnail(
                            coverUrl = track.coverUrl,
                            contentDescription = track.title,
                            shape = RoundedCornerShape(20.dp),
                            iconSize = 72.dp,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Apple Music Track Info Block (Bold typography)
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
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = track.artist,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                DownloadButton(
                    status = downloadStatus,
                    progress = downloadProgress,
                    onStartDownload = onStartDownload,
                    onCancelDownload = onCancelDownload,
                    onDeleteDownload = onDeleteDownload,
                    trackTitle = track.title,
                    size = 40.dp
                )

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(40.dp)
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

        Spacer(modifier = Modifier.height(14.dp))

        // Apple Music Scrub Slider
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = progress,
                onValueChange = onSeek,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formattedPosition,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = formattedRemaining,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Apple Music Giant 3-Button Central Controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousTrack,
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous Track",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            Box(
                modifier = Modifier
                    .size(70.dp)
                    .shadow(16.dp, CircleShape, spotColor = Color.White.copy(alpha = 0.25f))
                    .clip(CircleShape)
                    .background(Color.White)
                    .clickable(onClick = onPlayPauseToggle),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.width(32.dp))

            IconButton(
                onClick = onNextTrack,
                modifier = Modifier.size(54.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next Track",
                    tint = Color.White,
                    modifier = Modifier.size(38.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // Apple Music Bottom Control Cluster: Lyrics Toggle, Queue, Shuffle, Repeat
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Lyrics Toggle Button (Highlighted when active)
            IconButton(
                onClick = { showInlineLyricsView = !showInlineLyricsView },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(if (showInlineLyricsView) Color.White.copy(alpha = 0.2f) else Color.Transparent)
            ) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = "Toggle Synced Lyrics",
                    tint = if (showInlineLyricsView) Color.White else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Shuffle Button
            IconButton(
                onClick = onToggleShuffle,
                modifier = Modifier.size(42.dp)
            ) {
                val isShuffle = playbackMode == PlaybackMode.SHUFFLE
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffle) UnboundPrimary else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Repeat Button
            IconButton(
                onClick = onCycleRepeatMode,
                modifier = Modifier.size(42.dp)
            ) {
                val isRepeat = playbackMode in listOf(PlaybackMode.LOOP_ALL, PlaybackMode.LOOP_ONE)
                val repeatIcon = if (playbackMode == PlaybackMode.LOOP_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat
                Icon(
                    imageVector = repeatIcon,
                    contentDescription = "Repeat",
                    tint = if (isRepeat) UnboundPrimary else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Up Next Queue Button
            IconButton(
                onClick = onOpenQueue,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = "Queue",
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
