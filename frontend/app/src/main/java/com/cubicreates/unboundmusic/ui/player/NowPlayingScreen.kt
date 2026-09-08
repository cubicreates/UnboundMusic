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
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.RomanizationMode
import com.cubicreates.unboundmusic.data.SleepTimerState
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.ui.components.DownloadButton
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHighest
import com.cubicreates.unboundmusic.viewmodel.LyricLine
import com.cubicreates.unboundmusic.viewmodel.SkitSkipNotice

private const val DEFAULT_NOW_PLAYING_ART = "https://lh3.googleusercontent.com/aida-public/AB6AXuAGy1MsCyslDD2_taWPKR5mU3tPXvGKVvWciRUepFaILcmXamjVauny1EQWLqoDCvzhsM7GDQR26ESeRL3SB2YxzIrls64h7PLghaC6My9WUfAeU2sOa-obdX6bUilADztu0rE0L8WVkcOy9OmY81-aHDlqpiPzW3ptX7BPqK5ktUtFR_YzFkpNz1Qqe01rosjRp0L-VZzPxcWo9g_fF6Lu_c11Pgzi3dywdPKmftNQKqqQkbgy89CzsA"
private const val DEFAULT_AMBIENT_BG = "https://lh3.googleusercontent.com/aida-public/AB6AXuAH-Hu1E6PJAepFOCRN2098v6Q-9ts_hkOJha1yQ8bh5V9_zVl7n9OOkKqPNPnOqXOBjeEfhgJldybnJ_XXvWK_3-ZfS4b7ruwgWcGxfDi6Ok830fSOGbjsrN1vhJwhCeu5IkLmJ9STHMGw9SdJGjcz8pw7e-KrDJJkykyu49eEfrHHrBiZNW3cA_mtGU-jwfObxxJAV8tJfK9U8AmaZhC9GOb_mxkLpIKIWhlvGQli0u9SpxqHQ_45lQ"

@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    track: TrackItem,
    isPlaying: Boolean = true,
    isFavorite: Boolean = true,
    progress: Float = 0f,
    currentPositionMs: Long = 0,
    formattedPosition: String = "0:00",
    formattedRemaining: String = "-0:00",
    lyricsLines: List<LyricLine> = emptyList(),
    lyricsSource: String = "",
    romanizationMode: RomanizationMode = RomanizationMode.ORIGINAL,
    timingOffsetMs: Long = 0L,
    isInstrumental: Boolean = false,
    onRomanizationModeChange: (RomanizationMode) -> Unit = {},
    onTimingOffsetChange: (Long) -> Unit = {},
    canvasArtUrl: String? = null,
    queue: List<TrackItem> = emptyList(),
    playbackMode: PlaybackMode = PlaybackMode.NORMAL,
    onCollapse: () -> Unit = {},
    onPlayPauseToggle: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onPreviousTrack: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSeekPositionMs: (Long) -> Unit = {},
    onCyclePlaybackMode: () -> Unit = {},
    onToggleShuffle: () -> Unit = onCyclePlaybackMode,
    onCycleRepeatMode: () -> Unit = onCyclePlaybackMode,
    onEqualizerClick: () -> Unit = {},
    onQueueTrackSelect: (Int) -> Unit = {},
    downloadStatus: DownloadUiStatus = DownloadUiStatus.NOT_DOWNLOADED,
    downloadProgress: Double = 0.0,
    onStartDownload: () -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onDeleteDownload: () -> Unit = {},
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit = { _, _ -> },
    onRemoveQueueItem: (index: Int) -> Unit = {},
    sleepTimerState: SleepTimerState = SleepTimerState(),
    onStartSleepTimer: (minutes: Int, endOfSong: Boolean) -> Unit = { _, _ -> },
    onCancelSleepTimer: () -> Unit = {},
    skippedSkitNotice: SkitSkipNotice? = null,
    onUndoSkip: () -> Unit = {},
    onDismissSkipNotice: () -> Unit = {}
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // 1. High-Performance Ambient Background Layer
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = canvasArtUrl ?: track.coverUrl.ifEmpty { DEFAULT_AMBIENT_BG },
                contentDescription = "Ambient Background",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.2f),
                contentScale = ContentScale.Crop,
                alpha = 0.16f
            )

            // Dark vignette gradient overlay for high contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF101010).copy(alpha = 0.70f),
                                Color(0xFF101010).copy(alpha = 0.90f),
                                Color(0xFF101010)
                            )
                        )
                    )
            )
        }

        // 2. Main Scrollable Canvas Area (Spotify Structure)
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
                        text = "PLAYING FROM PLAYLIST",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Unbound Queue",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UnboundPrimary.copy(alpha = 0.9f)
                    )
                }

                IconButton(
                    onClick = { /* More options */ },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More Options",
                        tint = OnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Balanced Spotify-Proportioned Album Artwork (~76% width, rounded corners)
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
                AsyncImage(
                    model = track.coverUrl.ifEmpty { DEFAULT_NOW_PLAYING_ART },
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Subtle glass sheen overlay
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

            // Track Details & Actions Row (Spotify Standard)
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

            Spacer(modifier = Modifier.height(16.dp))

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

            // Spotify 5-Button Control Deck (Shuffle - Prev - Play/Pause - Next - Repeat)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Shuffle Button
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

                // 2. Previous Track Button
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

                // 3. Play / Pause Filled Circular Button (64x64)
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

                // 4. Next Track Button
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

                // 5. Repeat Mode Button
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
                // 10-Band Equalizer
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

                // Bedtime Sleep Timer
                IconButton(
                    onClick = { showSleepTimerSheet = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = "Sleep Timer",
                        tint = if (sleepTimerState.isActive) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Up Next Queue
                IconButton(
                    onClick = { showQueueSheet = true },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Queue",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Fullscreen Lyrics Toggle
                IconButton(
                    onClick = { showFullLyrics = true },
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
                    .clickable { showFullLyrics = true }
                    .padding(18.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Header Row: "Lyrics" title & Romanization mode pill
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

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (lyricsLines.isNotEmpty() && !isInstrumental) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceGlassHighest)
                                        .clickable {
                                            val next = when (romanizationMode) {
                                                RomanizationMode.ORIGINAL -> RomanizationMode.ROMANIZED
                                                RomanizationMode.ROMANIZED -> RomanizationMode.DUAL
                                                RomanizationMode.DUAL -> RomanizationMode.ORIGINAL
                                            }
                                            onRomanizationModeChange(next)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = romanizationMode.name.take(4),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = UnboundPrimary
                                    )
                                }
                            }

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
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Live Lyrics Preview (3-4 synchronized lines)
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
                                val displayText = when (romanizationMode) {
                                    RomanizationMode.ROMANIZED -> if (line.romanized.isNotBlank()) line.romanized else line.text
                                    RomanizationMode.DUAL -> if (line.romanized.isNotBlank()) "${line.text} (${line.romanized})" else line.text
                                    RomanizationMode.ORIGINAL -> line.text
                                }

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

        // 3. Full Screen Kinetic Lyrics Overlay when toggled
        if (showFullLyrics) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UnboundBackground.copy(alpha = 0.96f))
                    .padding(top = 70.dp, bottom = 40.dp)
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
                    onLineClick = { line ->
                        onSeekPositionMs(line.startMs)
                    }
                )

                IconButton(
                    onClick = { showFullLyrics = false },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close Lyrics",
                        tint = OnSurface,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 4. Up Next Queue Modal Sheet
        if (showQueueSheet) {
            QueueBottomSheet(
                queue = queue,
                currentTrack = track,
                playbackMode = playbackMode,
                onCycleMode = onCyclePlaybackMode,
                onTrackSelect = onQueueTrackSelect,
                onMoveItem = onMoveQueueItem,
                onRemoveItem = onRemoveQueueItem,
                onDismiss = { showQueueSheet = false }
            )
        }

        // 5. Bedtime Sleep Timer Modal Sheet
        if (showSleepTimerSheet) {
            SleepTimerSheet(
                timerState = sleepTimerState,
                onStartTimer = onStartSleepTimer,
                onCancelTimer = onCancelSleepTimer,
                onDismiss = { showSleepTimerSheet = false }
            )
        }
    }
}
