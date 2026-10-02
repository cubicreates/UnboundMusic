/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: NowPlayingScreen.kt
 * Purpose: Unified flagship Unbound Canvas Player designed for local-first music experiences.
 *          Features prominent track typography, large center artwork with interactive 3D/crossfade
 *          flipping into Lyrics, Queue, and Audio Settings, compact secondary action deck,
 *          tactile progress scrubbing, and primary 5-button playback controls.
 * Subsystem: Player UI / Flagship Canvas Player
 */

package com.cubicreates.unboundmusic.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.FallbackStatusDto
import com.cubicreates.unboundmusic.data.RomanizationMode
import com.cubicreates.unboundmusic.data.RydVoteData
import com.cubicreates.unboundmusic.data.SleepTimerState
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.player.components.PlayerControlsBar
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.viewmodel.LyricLine
import com.cubicreates.unboundmusic.viewmodel.SkitSkipNotice

private const val DEFAULT_NOW_PLAYING_ART = "https://lh3.googleusercontent.com/aida-public/AB6AXuAGy1MsCyslDD2_taWPKR5mU3tPXvGKVvWciRUepFaILcmXamjVauny1EQWLqoDCvzhsM7GDQR26ESeRL3SB2YxzIrls64h7PLghaC6My9WUfAeU2sOa-obdX6bUilADztu0rE0L8WVkcOy9OmY81-aHDlqpiPzW3ptX7BPqK5ktUtFR_YzFkpNz1Qqe01rosjRp0L-VZzPxcWo9g_fF6Lu_c11Pgzi3dywdPKmftNQKqqQkbgy89CzsA"
private const val DEFAULT_AMBIENT_BG = "https://lh3.googleusercontent.com/aida-public/AB6AXuAH-Hu1E6PJAepFOCRN2098v6Q-9ts_hkOJha1yQ8bh5V9_zVl7n9OOkKqPNPnOqXOBjeEfhgJldybnJ_XXvWK_3-ZfS4b7ruwgWcGxfDi6Ok830fSOGbjsrN1vhJwhCeu5IkLmJ9STHMGw9SdJGjcz8pw7e-KrDJJkykyu49eEfrHHrBiZNW3cA_mtGU-jwfObxxJAV8tJfK9U8AmaZhC9GOb_mxkLpIKIWhlvGQli0u9SpxqHQ_45lQ"

/**
 * Center stage view modes for the unified Canvas Player.
 */
enum class CenterDeckView {
    ARTWORK,
    LYRICS,
    QUEUE,
    SETTINGS
}

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
    fallbackStatus: FallbackStatusDto? = null
) {
    var centerDeckView by remember { mutableStateOf(CenterDeckView.ARTWORK) }
    var showRydStatsSheet by remember { mutableStateOf(false) }

    // Intercept back button/gesture:
    // If not in artwork mode, back smoothly returns to artwork.
    // If already in artwork mode, back collapses the player to the underlying tab without closing the app.
    BackHandler(enabled = true) {
        if (showRydStatsSheet) {
            showRydStatsSheet = false
        } else if (centerDeckView != CenterDeckView.ARTWORK) {
            centerDeckView = CenterDeckView.ARTWORK
        } else {
            onCollapse()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // 1. Immersive Ambient Background Layer
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = canvasArtUrl ?: track.coverUrl.ifEmpty { DEFAULT_AMBIENT_BG },
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.35f),
                contentScale = ContentScale.Crop,
                alpha = 0.22f
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF09090C).copy(alpha = 0.70f),
                                Color(0xFF09090C).copy(alpha = 0.90f),
                                Color(0xFF09090C)
                            )
                        )
                    )
            )
        }

        // 2. Main Player Body
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 22.dp)
                .padding(top = 10.dp, bottom = 44.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // ==================== TOP BAR ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(1.dp, BorderGlass, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = Color.White
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.title.ifBlank { "Unbound Music" },
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist.ifBlank { "Local Audio" },
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center
                    )
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(1.dp, BorderGlass, CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFFF4081) else Color.White.copy(alpha = 0.8f)
                    )
                }
            }

            // ==================== CENTER STAGE ====================
            // Large Album Artwork, Live Lyrics, Interactive Queue, or Audio Settings
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                AnimatedContent(
                    targetState = centerDeckView,
                    transitionSpec = {
                        (fadeIn(tween(240)) + scaleIn(initialScale = 0.95f, animationSpec = tween(240)))
                            .togetherWith(fadeOut(tween(180)) + scaleOut(targetScale = 0.95f, animationSpec = tween(180)))
                    },
                    label = "center_deck_mode_transition"
                ) { viewMode ->
                    when (viewMode) {
                        CenterDeckView.ARTWORK -> {
                            // High-impact centered album artwork
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.92f)
                                    .aspectRatio(1f)
                                    .shadow(
                                        elevation = 18.dp,
                                        shape = RoundedCornerShape(24.dp),
                                        ambientColor = Color.Black,
                                        spotColor = UnboundPrimary.copy(alpha = 0.35f)
                                    )
                                    .clip(RoundedCornerShape(24.dp))
                                    .background(Color(0xFF14141A))
                                    .border(1.dp, BorderGlass, RoundedCornerShape(24.dp))
                                    .clickable { centerDeckView = CenterDeckView.LYRICS },
                                contentAlignment = Alignment.Center
                            ) {
                                AsyncImage(
                                    model = canvasArtUrl ?: track.coverUrl.ifEmpty { DEFAULT_NOW_PLAYING_ART },
                                    contentDescription = "Album Artwork",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // Subtle bottom pill indicating flip to lyrics affordance
                                Surface(
                                    shape = CircleShape,
                                    color = Color.Black.copy(alpha = 0.6f),
                                    border = BorderStroke(1.dp, BorderGlass),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .padding(bottom = 12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Lyrics,
                                            contentDescription = null,
                                            tint = UnboundPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Text(
                                            text = "Tap for Lyrics",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = Color.White.copy(alpha = 0.9f)
                                        )
                                    }
                                }
                            }
                        }

                        CenterDeckView.LYRICS -> {
                            // Live kinetic lyrics view
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFF101016).copy(alpha = 0.92f),
                                border = BorderStroke(1.dp, BorderGlass)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Lyrics,
                                                contentDescription = null,
                                                tint = UnboundPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Live Synced Lyrics",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = SurfaceGlassHighest,
                                            border = BorderStroke(1.dp, BorderGlass),
                                            modifier = Modifier.clickable { centerDeckView = CenterDeckView.ARTWORK }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(12.dp))
                                                Text(text = "Artwork", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                            .padding(horizontal = 8.dp)
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
                                }
                            }
                        }

                        CenterDeckView.QUEUE -> {
                            // Interactive playback queue view
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFF101016).copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, BorderGlass)
                            ) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                                                contentDescription = null,
                                                tint = UnboundPrimary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Up Next (${queue.size})",
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = SurfaceGlassHighest,
                                            border = BorderStroke(1.dp, BorderGlass),
                                            modifier = Modifier.clickable { centerDeckView = CenterDeckView.ARTWORK }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(12.dp))
                                                Text(text = "Artwork", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }

                                    if (queue.isEmpty()) {
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "Queue is empty",
                                                fontSize = 13.sp,
                                                color = OnSurfaceVariant
                                            )
                                        }
                                    } else {
                                        LazyColumn(
                                            modifier = Modifier
                                                .weight(1f)
                                                .fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            itemsIndexed(queue, key = { index, item -> "${item.id}_${index}" }) { index, item ->
                                                val isCurrent = item.id.isNotBlank() && item.id == track.id
                                                QueueDeckRowItem(
                                                    track = item,
                                                    isCurrent = isCurrent,
                                                    index = index,
                                                    totalCount = queue.size,
                                                    onClick = { onQueueTrackSelect(index) },
                                                    onMoveUp = { if (index > 0) onMoveQueueItem(index, index - 1) },
                                                    onMoveDown = { if (index < queue.size - 1) onMoveQueueItem(index, index + 1) },
                                                    onRemove = { onRemoveQueueItem(index) }
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        CenterDeckView.SETTINGS -> {
                            // In-place Audio & Playback DSP settings card
                            Surface(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(24.dp)),
                                shape = RoundedCornerShape(24.dp),
                                color = Color(0xFF101016).copy(alpha = 0.94f),
                                border = BorderStroke(1.dp, BorderGlass)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.Tune, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(16.dp))
                                            Text(text = "Playback & Audio Settings", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                        }

                                        Surface(
                                            shape = CircleShape,
                                            color = SurfaceGlassHighest,
                                            border = BorderStroke(1.dp, BorderGlass),
                                            modifier = Modifier.clickable { centerDeckView = CenterDeckView.ARTWORK }
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(12.dp))
                                                Text(text = "Artwork", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
                                            }
                                        }
                                    }

                                    // Playback Speed & Pitch Controls
                                    Column {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(text = "Speed & Pitch", fontSize = 12.sp, color = OnSurfaceVariant)
                                            Text(text = "${"%.2f".format(playbackSpeed)}x", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UnboundPrimary)
                                        }
                                        Slider(
                                            value = playbackSpeed,
                                            onValueChange = { speed -> onSetPlaybackSpeedAndPitch(speed, speed) },
                                            valueRange = 0.5f..2.0f,
                                            colors = SliderDefaults.colors(thumbColor = UnboundPrimary, activeTrackColor = UnboundPrimary)
                                        )
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { s ->
                                                Surface(
                                                    shape = RoundedCornerShape(8.dp),
                                                    color = if (playbackSpeed == s) UnboundPrimary.copy(alpha = 0.2f) else SurfaceGlassHighest,
                                                    border = BorderStroke(1.dp, if (playbackSpeed == s) UnboundPrimary else BorderGlass),
                                                    modifier = Modifier.clickable { onSetPlaybackSpeedAndPitch(s, s) }
                                                ) {
                                                    Text(
                                                        text = "${s}x",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = if (playbackSpeed == s) UnboundPrimary else OnSurfaceVariant,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    // Hardware EQ & Sleep Timer Shortcuts
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable(onClick = onEqualizerClick),
                                            shape = RoundedCornerShape(14.dp),
                                            color = SurfaceGlassHighest,
                                            border = BorderStroke(1.dp, BorderGlass)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(imageVector = Icons.Default.GraphicEq, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(18.dp))
                                                Text(text = "10-Band EQ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                                            }
                                        }

                                        Surface(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .clickable { onStartSleepTimer(30, false) },
                                            shape = RoundedCornerShape(14.dp),
                                            color = if (sleepTimerState.isActive) UnboundPrimary.copy(alpha = 0.2f) else SurfaceGlassHighest,
                                            border = BorderStroke(1.dp, if (sleepTimerState.isActive) UnboundPrimary else BorderGlass)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(12.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Bedtime,
                                                    contentDescription = null,
                                                    tint = if (sleepTimerState.isActive) UnboundPrimary else OnSurfaceVariant,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Text(
                                                    text = if (sleepTimerState.isActive) "Timer: ${sleepTimerState.formattedRemaining}" else "Sleep Timer",
                                                    fontSize = 12.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ==================== COMPACT SECONDARY ACTION ROW (3 ACTIONS) ====================
            // Positioned between center stage and progress bar
            // USER ORDER: 1. Lyrics, 2. Queue, 3. Settings
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Lyrics Pill
                SecondaryActionDeckPill(
                    icon = Icons.Default.Lyrics,
                    label = "Lyrics",
                    isActive = centerDeckView == CenterDeckView.LYRICS,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        centerDeckView = if (centerDeckView == CenterDeckView.LYRICS) CenterDeckView.ARTWORK else CenterDeckView.LYRICS
                    }
                )

                // 2. Queue Pill
                SecondaryActionDeckPill(
                    icon = Icons.AutoMirrored.Filled.QueueMusic,
                    label = if (queue.isNotEmpty()) "Queue (${queue.size})" else "Queue",
                    isActive = centerDeckView == CenterDeckView.QUEUE,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        centerDeckView = if (centerDeckView == CenterDeckView.QUEUE) CenterDeckView.ARTWORK else CenterDeckView.QUEUE
                    }
                )

                // 3. Settings Pill
                SecondaryActionDeckPill(
                    icon = Icons.Default.Tune,
                    label = "Settings",
                    isActive = centerDeckView == CenterDeckView.SETTINGS,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        centerDeckView = if (centerDeckView == CenterDeckView.SETTINGS) CenterDeckView.ARTWORK else CenterDeckView.SETTINGS
                    }
                )
            }

            // ==================== HORIZONTAL SEEK / PROGRESS BAR ====================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                SleekProgressBar(
                    progressFraction = progress,
                    isPlaying = isPlaying,
                    onSliderChange = onSeek,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formattedPosition,
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = formattedRemaining,
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // ==================== PRIMARY 5-BUTTON PLAYBACK DECK ====================
            PlayerControlsBar(
                isPlaying = isPlaying,
                onPlayPause = onPlayPauseToggle,
                onNext = onNextTrack,
                onPrevious = onPreviousTrack,
                isShuffle = playbackMode == PlaybackMode.SHUFFLE,
                onToggleShuffle = onToggleShuffle,
                repeatMode = when (playbackMode) {
                    PlaybackMode.LOOP_ONE -> 2
                    PlaybackMode.LOOP_ALL -> 1
                    else -> 0
                },
                onToggleRepeat = onCycleRepeatMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            )
        }

        // ==================== FLOATING BOTTOM COLLAPSE AFFORDANCE BAR ====================
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceGlassHighest.copy(alpha = 0.85f))
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                .clickable(onClick = onCollapse)
                .padding(horizontal = 20.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse Player",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(15.dp)
                )
                Text(
                    text = "Close Player",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )
            }
        }
    }
}

/**
 * Compact pill button used in the secondary action deck between artwork and progress bar.
 */
@Composable
private fun SecondaryActionDeckPill(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isActive) UnboundPrimary.copy(alpha = 0.20f) else SurfaceGlassHighest,
        border = BorderStroke(1.dp, if (isActive) UnboundPrimary.copy(alpha = 0.65f) else BorderGlass)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isActive) UnboundPrimary else OnSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color.White else OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Queue row item rendered inside the center stage queue view.
 */
@Composable
private fun QueueDeckRowItem(
    track: TrackItem,
    isCurrent: Boolean,
    index: Int,
    totalCount: Int,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isCurrent) UnboundPrimary.copy(alpha = 0.15f) else SurfaceGlassHighest.copy(alpha = 0.6f),
        border = BorderStroke(1.dp, if (isCurrent) UnboundPrimary.copy(alpha = 0.5f) else BorderGlass)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Track Thumbnail
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E1E24))
            ) {
                AsyncImage(
                    model = track.coverUrl.ifEmpty { DEFAULT_NOW_PLAYING_ART },
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Title & Artist
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 13.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) UnboundPrimary else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Move Up / Down Reorder Affordances
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                if (index < totalCount - 1) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Remove", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
