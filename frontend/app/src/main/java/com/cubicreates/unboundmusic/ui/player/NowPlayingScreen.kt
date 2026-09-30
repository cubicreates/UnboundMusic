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
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
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
import com.cubicreates.unboundmusic.ui.components.UnboundTrackThumbnail
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
    onRefreshRydVotes: () -> Unit = {},
    onStartRadio: () -> Unit = {},
    playbackSpeed: Float = 1.0f,
    playbackPitch: Float = 1.0f,
    onSetPlaybackSpeedAndPitch: (speed: Float, pitch: Float) -> Unit = { _, _ -> },
    fallbackStatus: com.cubicreates.unboundmusic.data.FallbackStatusDto? = null
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
    var showSpeedPitchDialog by remember { mutableStateOf(false) }

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
                        fallbackStatus = fallbackStatus,
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
                        onStartRadio = onStartRadio,
                        playbackSpeed = playbackSpeed,
                        onOpenPlaybackSpeed = { showSpeedPitchDialog = true },
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
                        playbackSpeed = playbackSpeed,
                        onOpenPlaybackSpeed = { showSpeedPitchDialog = true },
                        onOpenQueue = { showQueueSheet = true },
                        onOpenFullLyrics = { showFullLyrics = true },
                        rydData = rydData,
                        onOpenRydStats = { showRydStatsSheet = true }
                    )
                }
            }
        }

        // -----------------------------------------------------------------------------------------
        // Modal Bottom Sheets & Overlays
        // -----------------------------------------------------------------------------------------

        // 1. Interactive UI Style Picker
        if (showStylePickerSheet) {
            NowPlayingStylePickerSheet(
                currentStyle = currentStyle,
                onStyleSelected = { newStyle ->
                    currentStyle = newStyle
                    prefs.edit().putString("now_playing_style", newStyle.name).apply()
                    showStylePickerSheet = false
                },
                onDismiss = { showStylePickerSheet = false }
            )
        }

        // 2. Full-Screen Interactive Karaoke Lyrics Overlay
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
                onStartRadio = onStartRadio,
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

        // 8. Playback Speed & Pitch Modal Dialog
        if (showSpeedPitchDialog) {
            PlaybackSpeedDialog(
                currentSpeed = playbackSpeed,
                currentPitch = playbackPitch,
                onSpeedPitchChanged = onSetPlaybackSpeedAndPitch,
                onDismiss = { showSpeedPitchDialog = false }
            )
        }
    }
}



// -------------------------------------------------------------------------------------------------
// 2. Apple Music Glass Layout (Extracted to AppleGlassPlayer.kt)
// -------------------------------------------------------------------------------------------------

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

