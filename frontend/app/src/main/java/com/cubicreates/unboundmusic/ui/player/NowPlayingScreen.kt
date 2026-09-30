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
// 3. Material 3 Expressive Layout (Extracted to M3ExpressivePlayer.kt)
// -------------------------------------------------------------------------------------------------

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

