/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: NowPlayingScreen.kt
 * Purpose: Full-screen Now Playing music player supporting 3 selectable presentation styles:
 *          1. Spotify Canvas (tactile slider, 5-button deck, live-scrolling lyrics card)
 *          2. Apple Music Glass (frosted glass, prominent synced lyrics toggle, bold typography)
 *          3. M3 Expressive (animated sine-wave WavySeekBar, pill controls, squircle cards)
 * Subsystem: Player UI / Multi-Style Fullscreen Player
 */

package com.cubicreates.unboundmusic.ui.player

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.DashboardCustomize
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.material.icons.filled.ThumbUp
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.RomanizationMode
import com.cubicreates.unboundmusic.data.RydVoteData
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
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary
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
    onDismissSkipNotice: () -> Unit = {},
    rydData: RydVoteData? = null,
    onRefreshRydVotes: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("unbound_player_prefs", Context.MODE_PRIVATE) }

    var currentStyle by remember {
        mutableStateOf(
            try {
                val saved = prefs.getString("now_playing_style", NowPlayingStyle.SPOTIFY.name)
                NowPlayingStyle.valueOf(saved ?: NowPlayingStyle.SPOTIFY.name)
            } catch (_: Exception) {
                NowPlayingStyle.SPOTIFY
            }
        )
    }

    var showStylePickerSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showRydStatsSheet by remember { mutableStateOf(false) }

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
                    .scale(1.25f),
                contentScale = ContentScale.Crop,
                alpha = if (currentStyle == NowPlayingStyle.APPLE_MUSIC) 0.32f else 0.16f
            )

            // Dynamic vignette gradient overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = if (currentStyle == NowPlayingStyle.APPLE_MUSIC) {
                                listOf(
                                    Color(0xFF0C0C10).copy(alpha = 0.65f),
                                    Color(0xFF0C0C10).copy(alpha = 0.88f),
                                    Color(0xFF0C0C10)
                                )
                            } else {
                                listOf(
                                    Color(0xFF101010).copy(alpha = 0.70f),
                                    Color(0xFF101010).copy(alpha = 0.90f),
                                    Color(0xFF101010)
                                )
                            }
                        )
                    )
            )
        }

        // 2. Selectable Layout Content
        Crossfade(
            targetState = currentStyle,
            animationSpec = tween(300),
            label = "player_style_crossfade"
        ) { style ->
            when (style) {
                NowPlayingStyle.SPOTIFY -> {
                    SpotifyPlayerContent(
                        track = track,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        progress = progress,
                        currentPositionMs = currentPositionMs,
                        formattedPosition = formattedPosition,
                        formattedRemaining = formattedRemaining,
                        lyricsLines = lyricsLines,
                        timingOffsetMs = timingOffsetMs,
                        isInstrumental = isInstrumental,
                        playbackMode = playbackMode,
                        downloadStatus = downloadStatus,
                        downloadProgress = downloadProgress,
                        skippedSkitNotice = skippedSkitNotice,
                        sleepTimerActive = sleepTimerState.isActive,
                        onCollapse = onCollapse,
                        onOpenStylePicker = { showStylePickerSheet = true },
                        onFavoriteToggle = onFavoriteToggle,
                        onStartDownload = onStartDownload,
                        onCancelDownload = onCancelDownload,
                        onDeleteDownload = onDeleteDownload,
                        onSeek = onSeek,
                        onSeekPositionMs = onSeekPositionMs,
                        onToggleShuffle = onToggleShuffle,
                        onPreviousTrack = onPreviousTrack,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onNextTrack = onNextTrack,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onEqualizerClick = onEqualizerClick,
                        onOpenSleepTimer = { showSleepTimerSheet = true },
                        onOpenQueue = { showQueueSheet = true },
                        onOpenFullLyrics = { showFullLyrics = true },
                        onUndoSkip = onUndoSkip,
                        onDismissSkipNotice = onDismissSkipNotice,
                        rydData = rydData,
                        onOpenRydStats = { showRydStatsSheet = true }
                    )
                }

                NowPlayingStyle.APPLE_MUSIC -> {
                    AppleMusicPlayerContent(
                        track = track,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        progress = progress,
                        currentPositionMs = currentPositionMs,
                        formattedPosition = formattedPosition,
                        formattedRemaining = formattedRemaining,
                        lyricsLines = lyricsLines,
                        lyricsSource = lyricsSource,
                        romanizationMode = romanizationMode,
                        timingOffsetMs = timingOffsetMs,
                        isInstrumental = isInstrumental,
                        playbackMode = playbackMode,
                        downloadStatus = downloadStatus,
                        downloadProgress = downloadProgress,
                        onRomanizationModeChange = onRomanizationModeChange,
                        onTimingOffsetChange = onTimingOffsetChange,
                        onCollapse = onCollapse,
                        onOpenStylePicker = { showStylePickerSheet = true },
                        onFavoriteToggle = onFavoriteToggle,
                        onStartDownload = onStartDownload,
                        onCancelDownload = onCancelDownload,
                        onDeleteDownload = onDeleteDownload,
                        onSeek = onSeek,
                        onSeekPositionMs = onSeekPositionMs,
                        onToggleShuffle = onToggleShuffle,
                        onPreviousTrack = onPreviousTrack,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onNextTrack = onNextTrack,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onOpenQueue = { showQueueSheet = true },
                        rydData = rydData,
                        onOpenRydStats = { showRydStatsSheet = true }
                    )
                }

                NowPlayingStyle.M3_EXPRESSIVE -> {
                    M3ExpressivePlayerContent(
                        track = track,
                        isPlaying = isPlaying,
                        isFavorite = isFavorite,
                        progress = progress,
                        formattedPosition = formattedPosition,
                        formattedRemaining = formattedRemaining,
                        lyricsLines = lyricsLines,
                        currentPositionMs = currentPositionMs,
                        timingOffsetMs = timingOffsetMs,
                        isInstrumental = isInstrumental,
                        playbackMode = playbackMode,
                        downloadStatus = downloadStatus,
                        downloadProgress = downloadProgress,
                        queueSize = queue.size,
                        sleepTimerActive = sleepTimerState.isActive,
                        onCollapse = onCollapse,
                        onOpenStylePicker = { showStylePickerSheet = true },
                        onFavoriteToggle = onFavoriteToggle,
                        onStartDownload = onStartDownload,
                        onCancelDownload = onCancelDownload,
                        onDeleteDownload = onDeleteDownload,
                        onSeek = onSeek,
                        onSeekPositionMs = onSeekPositionMs,
                        onToggleShuffle = onToggleShuffle,
                        onPreviousTrack = onPreviousTrack,
                        onPlayPauseToggle = onPlayPauseToggle,
                        onNextTrack = onNextTrack,
                        onCycleRepeatMode = onCycleRepeatMode,
                        onEqualizerClick = onEqualizerClick,
                        onOpenSleepTimer = { showSleepTimerSheet = true },
                        onOpenQueue = { showQueueSheet = true },
                        onOpenFullLyrics = { showFullLyrics = true },
                        rydData = rydData,
                        onOpenRydStats = { showRydStatsSheet = true }
                    )
                }
            }
        }

        // 3. Style Picker Modal Bottom Sheet
        if (showStylePickerSheet) {
            NowPlayingStylePickerSheet(
                currentStyle = currentStyle,
                onStyleSelected = { selected ->
                    currentStyle = selected
                    prefs.edit().putString("now_playing_style", selected.name).apply()
                },
                onDismiss = { showStylePickerSheet = false }
            )
        }

        // 4. Full Screen Kinetic Lyrics Overlay (when invoked in Spotify / M3 mode)
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

        // 5. Up Next Queue Modal Sheet
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

        // 6. Bedtime Sleep Timer Modal Sheet
        if (showSleepTimerSheet) {
            SleepTimerSheet(
                timerState = sleepTimerState,
                onStartTimer = onStartSleepTimer,
                onCancelTimer = onCancelSleepTimer,
                onDismiss = { showSleepTimerSheet = false }
            )
        }

        // 7. Return YouTube Dislike (RYD) Community Sentiment Modal Sheet
        if (showRydStatsSheet) {
            RydCommunityStatsSheet(
                track = track,
                rydData = rydData,
                onRefresh = onRefreshRydVotes,
                onDismiss = { showRydStatsSheet = false }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 1. Spotify Player Layout
// -------------------------------------------------------------------------------------------------
@Composable
private fun SpotifyPlayerContent(
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
    onOpenQueue: () -> Unit,
    onOpenFullLyrics: () -> Unit,
    onUndoSkip: () -> Unit,
    onDismissSkipNotice: () -> Unit,
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
            AsyncImage(
                model = track.coverUrl.ifEmpty { DEFAULT_NOW_PLAYING_ART },
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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

// -------------------------------------------------------------------------------------------------
// 2. Apple Music Glass Layout (Seamless Artwork vs Synced Lyrics View Toggle)
// -------------------------------------------------------------------------------------------------
@Composable
private fun AppleMusicPlayerContent(
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
                        AsyncImage(
                            model = track.coverUrl.ifEmpty { DEFAULT_NOW_PLAYING_ART },
                            contentDescription = track.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
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

// -------------------------------------------------------------------------------------------------
// 3. Material 3 Expressive Layout (Animated Sine-Wave WavySeekBar + Expressive Pills)
// -------------------------------------------------------------------------------------------------
@Composable
private fun M3ExpressivePlayerContent(
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
            AsyncImage(
                model = track.coverUrl.ifEmpty { DEFAULT_NOW_PLAYING_ART },
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
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

        // ANIMATED WAVY SEEK BAR (Live Sine-Wave Oscillating Progress Track)
        Column(modifier = Modifier.fillMaxWidth()) {
            WavySeekBar(
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
        }
    }
}

/**
 * Modern glassmorphic pill displaying community Likes, Dislikes, and approval ratio from Return YouTube Dislike (RYD).
 */
@Composable
fun RydCommunitySentimentPill(
    rydData: RydVoteData?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (rydData == null) return

    val approvalPct = rydData.likePercentage
    val barColor = when {
        approvalPct >= 90 -> Color(0xFF10B981) // Emerald Green
        approvalPct >= 75 -> UnboundPrimary     // Cyan / Accent
        approvalPct >= 50 -> Color(0xFFF59E0B) // Amber
        else -> Color(0xFFEF4444)              // Coral Red
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFF141416).copy(alpha = 0.85f))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Likes & Dislikes
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbUp,
                            contentDescription = "Likes",
                            tint = barColor,
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = rydData.formattedLikes,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface
                        )
                    }

                    Text(
                        text = "•",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.5f)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ThumbDown,
                            contentDescription = "Dislikes",
                            tint = OnSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = rydData.formattedDislikes,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            color = OnSurfaceVariant
                        )
                    }
                }

                // Approval Rating Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "$approvalPct% Approval",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = barColor
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // Two-tone Ratio Micro-Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.10f))
            ) {
                val likeRatio = (approvalPct / 100f).coerceIn(0.01f, 1f)
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(likeRatio)
                        .background(barColor)
                )
                if (likeRatio < 1f) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(1f - likeRatio)
                            .background(Color(0xFFEF4444).copy(alpha = 0.6f))
                    )
                }
            }
        }
    }
}

