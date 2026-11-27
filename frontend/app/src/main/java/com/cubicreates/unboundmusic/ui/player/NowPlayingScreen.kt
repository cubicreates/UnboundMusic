/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: NowPlayingScreen.kt
 * Purpose: Full-screen Spotify-inspired Studio Brutalist Music Player with square hero artwork,
 *          download/playlist/favorite actions, glowing cyan scrubber, 5-button playback deck,
 *          live audio output device indicator, and multi-mode bottom card (Lyrics / Queue / Audio DSP).
 * Subsystem: Player UI
 */

package com.cubicreates.unboundmusic.ui.player

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.BluetoothAudio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicOff
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.cubicreates.unboundmusic.R
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.FallbackStatusDto
import com.cubicreates.unboundmusic.data.RomanizationMode
import com.cubicreates.unboundmusic.data.RydVoteData
import com.cubicreates.unboundmusic.data.SleepTimerState
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.ui.components.DownloadButton
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundEmptyStateCard
import com.cubicreates.unboundmusic.ui.components.UnboundTrackThumbnail
import com.cubicreates.unboundmusic.ui.player.components.PlayerControlsBar
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundOutlineVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimaryContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLow
import com.cubicreates.unboundmusic.viewmodel.LyricLine
import com.cubicreates.unboundmusic.viewmodel.SkitSkipNotice
import java.util.Locale

enum class BottomDeckMode {
    LYRICS,
    QUEUE,
    SETTINGS
}

private data class AudioOutputRoute(
    val label: String,
    val icon: ImageVector,
    val isExternal: Boolean
)

@Composable
fun NowPlayingScreen(
    modifier: Modifier = Modifier,
    track: TrackItem,
    isPlaying: Boolean = true,
    isFavorite: Boolean = false,
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
    onAddToPlaylist: () -> Unit = {},
    fallbackStatus: FallbackStatusDto? = null
) {
    val context = LocalContext.current
    var bottomDeckMode by remember { mutableStateOf(BottomDeckMode.LYRICS) }
    var showExpandedLyrics by remember { mutableStateOf(false) }
    var showOverflowMenu by remember { mutableStateOf(false) }

    val artworkUrl = remember(track.coverUrl, canvasArtUrl) {
        track.coverUrl.ifBlank { canvasArtUrl.orEmpty() }.trim()
    }
    val hasTrack = track.title.isNotBlank()
    val isLocalAudio = track.streamUrl.startsWith("file://") ||
        track.streamUrl.startsWith("content://") ||
        track.id.startsWith("local:")
    val audioOutputRoute = rememberActiveAudioOutputDevice(context)

    BackHandler(enabled = true) {
        when {
            showExpandedLyrics -> showExpandedLyrics = false
            bottomDeckMode != BottomDeckMode.LYRICS -> bottomDeckMode = BottomDeckMode.LYRICS
            else -> onCollapse()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // 1. Subtle Ambient Background Backdrop
        if (artworkUrl.isNotBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.3f),
                contentScale = ContentScale.Crop,
                alpha = 0.16f
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                UnboundBackground.copy(alpha = 0.72f),
                                UnboundBackground.copy(alpha = 0.92f),
                                UnboundBackground
                            )
                        )
                    )
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                UnboundPrimaryContainer.copy(alpha = 0.22f),
                                UnboundBackground,
                                UnboundBackground
                            )
                        )
                    )
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            // 2. Pinned Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse Player",
                        tint = OnSurface,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "PLAYING NOW",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.6.sp,
                        color = OnSurfaceVariant
                    )
                    val contextSubtitle = when {
                        !hasTrack -> "No Active Track"
                        track.album.isNotBlank() -> track.album
                        downloadStatus == DownloadUiStatus.DOWNLOADED -> "Offline Downloads"
                        isLocalAudio -> "Local Library"
                        else -> "Unbound Stream"
                    }
                    Text(
                        text = contextSubtitle,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box {
                    IconButton(
                        onClick = { showOverflowMenu = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More Options",
                            tint = OnSurface
                        )
                    }

                    DropdownMenu(
                        expanded = showOverflowMenu,
                        onDismissRequest = { showOverflowMenu = false },
                        containerColor = UnboundSurfaceContainerHigh
                    ) {
                        DropdownMenuItem(
                            text = { Text("Add to Playlist", color = OnSurface) },
                            leadingIcon = {
                                Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = UnboundPrimary)
                            },
                            enabled = hasTrack,
                            onClick = {
                                showOverflowMenu = false
                                onAddToPlaylist()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Start Track Radio", color = OnSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.Radio, contentDescription = null, tint = UnboundPrimary)
                            },
                            enabled = hasTrack,
                            onClick = {
                                showOverflowMenu = false
                                onStartRadio()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share Track", color = OnSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.Share, contentDescription = null, tint = UnboundPrimary)
                            },
                            enabled = hasTrack,
                            onClick = {
                                showOverflowMenu = false
                                shareTrack(context, track)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("10-Band Equalizer", color = OnSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.GraphicEq, contentDescription = null, tint = UnboundPrimary)
                            },
                            onClick = {
                                showOverflowMenu = false
                                onEqualizerClick()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Audio & Sleep Settings", color = OnSurface) },
                            leadingIcon = {
                                Icon(Icons.Default.Tune, contentDescription = null, tint = UnboundPrimary)
                            },
                            onClick = {
                                showOverflowMenu = false
                                bottomDeckMode = BottomDeckMode.SETTINGS
                            }
                        )
                    }
                }
            }

            // 3. Scrollable Main Player Body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 6.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Hero Square Artwork
                HeroArtworkCard(
                    artworkUrl = artworkUrl,
                    hasTrack = hasTrack,
                    sourceLabel = when {
                        !hasTrack -> null
                        downloadStatus == DownloadUiStatus.DOWNLOADED -> "OFFLINE DOWNLOAD"
                        isLocalAudio -> "LOCAL AUDIO"
                        else -> "YOUTUBE STREAM"
                    },
                    isOfflineOrLocal = downloadStatus == DownloadUiStatus.DOWNLOADED || isLocalAudio
                )

                // Optional SponsorBlock / Skit Skip Notice
                if (skippedSkitNotice != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = UnboundSurfaceContainerHigh,
                        border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = skippedSkitNotice.message,
                                fontSize = 12.sp,
                                color = OnSurface,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(
                                    text = "Undo",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UnboundPrimary,
                                    modifier = Modifier.clickable(onClick = onUndoSkip)
                                )
                                Text(
                                    text = "Dismiss",
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.clickable(onClick = onDismissSkipNotice)
                                )
                            }
                        }
                    }
                }

                // Track Info + Download / Playlist / Favorite Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (!hasTrack) {
                            Text(
                                text = "No Track Selected",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Pick a song from Home, Search, or Library",
                                fontSize = 13.sp,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = track.title,
                                    fontSize = 21.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = OnSurface,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                extractRealCodecTag(track.streamUrl)?.let { codec ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = UnboundSurfaceContainerHighest
                                    ) {
                                        Text(
                                            text = codec,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = UnboundPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = track.artist.ifBlank { "Unknown Artist" },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        if (hasTrack && !isLocalAudio) {
                            DownloadButton(
                                status = downloadStatus,
                                progress = downloadProgress,
                                onStartDownload = onStartDownload,
                                onCancelDownload = onCancelDownload,
                                onDeleteDownload = onDeleteDownload,
                                size = 38.dp,
                                trackTitle = track.title
                            )
                        }

                        IconButton(
                            onClick = onAddToPlaylist,
                            enabled = hasTrack,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = "Add to Playlist",
                                tint = if (hasTrack) OnSurfaceVariant else OnSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(23.dp)
                            )
                        }

                        IconButton(
                            onClick = onFavoriteToggle,
                            enabled = hasTrack,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = if (isFavorite) "Unlike" else "Like",
                                tint = when {
                                    !hasTrack -> OnSurfaceVariant.copy(alpha = 0.4f)
                                    isFavorite -> UnboundPrimary
                                    else -> OnSurfaceVariant
                                },
                                modifier = Modifier.size(23.dp)
                            )
                        }
                    }
                }

                // Sleek Cyan Progress Scrubber & Timestamps
                Column(modifier = Modifier.fillMaxWidth()) {
                    SleekProgressBar(
                        progressFraction = progress,
                        isPlaying = isPlaying,
                        onSliderChange = onSeek,
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
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UnboundPrimary
                        )
                        Text(
                            text = formattedRemaining,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant
                        )
                    }
                }

                // Primary 5-Button Playback Deck
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
                    modifier = Modifier.fillMaxWidth()
                )

                // Real Audio Output Route & Utility Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = audioOutputRoute.icon,
                            contentDescription = "Audio Output",
                            tint = if (audioOutputRoute.isExternal) UnboundSecondary else OnSurfaceVariant,
                            modifier = Modifier.size(17.dp)
                        )
                        Text(
                            text = audioOutputRoute.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (audioOutputRoute.isExternal) UnboundSecondary else OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        IconButton(
                            onClick = { shareTrack(context, track) },
                            enabled = hasTrack,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Share Track",
                                tint = if (hasTrack) OnSurfaceVariant else OnSurfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onEqualizerClick,
                            modifier = Modifier.size(34.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = "Equalizer",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }

                // Mode Switcher Pills: Lyrics (default), Queue, Audio DSP
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DeckModePill(
                        icon = Icons.Default.Lyrics,
                        label = "Lyrics",
                        selected = bottomDeckMode == BottomDeckMode.LYRICS,
                        modifier = Modifier.weight(1f),
                        onClick = { bottomDeckMode = BottomDeckMode.LYRICS }
                    )
                    DeckModePill(
                        icon = Icons.AutoMirrored.Filled.QueueMusic,
                        label = if (queue.isNotEmpty()) "Queue (${queue.size})" else "Queue",
                        selected = bottomDeckMode == BottomDeckMode.QUEUE,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            bottomDeckMode = if (bottomDeckMode == BottomDeckMode.QUEUE) {
                                BottomDeckMode.LYRICS
                            } else {
                                BottomDeckMode.QUEUE
                            }
                        }
                    )
                    DeckModePill(
                        icon = Icons.Default.Tune,
                        label = "Audio DSP",
                        selected = bottomDeckMode == BottomDeckMode.SETTINGS,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            bottomDeckMode = if (bottomDeckMode == BottomDeckMode.SETTINGS) {
                                BottomDeckMode.LYRICS
                            } else {
                                BottomDeckMode.SETTINGS
                            }
                        }
                    )
                }

                // Bottom Multi-Mode Card (Lyrics by default; switches to Queue or Audio DSP and back)
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = UnboundSurfaceContainerLow,
                    border = BorderStroke(1.dp, UnboundOutlineVariant)
                ) {
                    AnimatedContent(
                        targetState = bottomDeckMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "bottom_deck_mode"
                    ) { mode ->
                        when (mode) {
                            BottomDeckMode.LYRICS -> LyricsPreviewCard(
                                hasTrack = hasTrack,
                                lyricsLines = lyricsLines,
                                currentPositionMs = currentPositionMs,
                                timingOffsetMs = timingOffsetMs,
                                lyricsSource = lyricsSource,
                                isInstrumental = isInstrumental,
                                onSeekPositionMs = onSeekPositionMs,
                                onExpandLyrics = { showExpandedLyrics = true }
                            )

                            BottomDeckMode.QUEUE -> QueueDeckCard(
                                queue = queue,
                                currentTrack = track,
                                onQueueTrackSelect = onQueueTrackSelect,
                                onMoveQueueItem = onMoveQueueItem,
                                onRemoveQueueItem = onRemoveQueueItem,
                                onBackToLyrics = { bottomDeckMode = BottomDeckMode.LYRICS }
                            )

                            BottomDeckMode.SETTINGS -> AudioSettingsDeckCard(
                                playbackSpeed = playbackSpeed,
                                timingOffsetMs = timingOffsetMs,
                                sleepTimerState = sleepTimerState,
                                onSetPlaybackSpeedAndPitch = onSetPlaybackSpeedAndPitch,
                                onTimingOffsetChange = onTimingOffsetChange,
                                onEqualizerClick = onEqualizerClick,
                                onStartSleepTimer = onStartSleepTimer,
                                onCancelSleepTimer = onCancelSleepTimer,
                                onBackToLyrics = { bottomDeckMode = BottomDeckMode.LYRICS }
                            )
                        }
                    }
                }
            }
        }

        // Full-Screen Kinetic Lyrics Modal Overlay
        if (showExpandedLyrics) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = UnboundBackground.copy(alpha = 0.98f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title.ifBlank { "Lyrics" },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (track.artist.isNotBlank()) {
                                Text(
                                    text = track.artist,
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(onClick = { showExpandedLyrics = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Fullscreen Lyrics",
                                tint = OnSurface
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        KineticLyricsView(
                            lyricsLines = lyricsLines,
                            currentPositionMs = currentPositionMs,
                            lyricsSource = lyricsSource.ifBlank { "Synced Lyrics" },
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
    }
}

@Composable
private fun HeroArtworkCard(
    artworkUrl: String,
    hasTrack: Boolean,
    sourceLabel: String?,
    isOfflineOrLocal: Boolean
) {
    val context = LocalContext.current
    var imageFailed by remember(artworkUrl) { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 356.dp)
            .aspectRatio(1f)
            .shadow(
                elevation = 20.dp,
                shape = RoundedCornerShape(18.dp),
                ambientColor = Color.Black,
                spotColor = UnboundPrimary.copy(alpha = 0.28f)
            )
            .clip(RoundedCornerShape(18.dp))
            .background(UnboundSurfaceContainerLow)
            .border(1.dp, UnboundOutlineVariant, RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        if (artworkUrl.isNotBlank() && !imageFailed) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album Artwork",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true }
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.unbound_logo),
                    contentDescription = "Unbound Music",
                    modifier = Modifier.size(86.dp),
                    contentScale = ContentScale.Fit
                )
                Text(
                    text = if (hasTrack) "No Cover Artwork" else "No Track Playing",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        if (!sourceLabel.isNullOrBlank()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
                shape = CircleShape,
                color = UnboundBackground.copy(alpha = 0.80f),
                border = BorderStroke(1.dp, UnboundOutlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (isOfflineOrLocal) UnboundSecondary else UnboundPrimary)
                    )
                    Text(
                        text = sourceLabel,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp,
                        color = OnSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DeckModePill(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) UnboundPrimary.copy(alpha = 0.16f) else UnboundSurfaceContainerLow,
        border = BorderStroke(1.dp, if (selected) UnboundPrimary.copy(alpha = 0.65f) else UnboundOutlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) UnboundPrimary else OnSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) OnSurface else OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LyricsPreviewCard(
    hasTrack: Boolean,
    lyricsLines: List<LyricLine>,
    currentPositionMs: Long,
    timingOffsetMs: Long,
    lyricsSource: String,
    isInstrumental: Boolean,
    onSeekPositionMs: (Long) -> Unit,
    onExpandLyrics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Lyrics",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                if (lyricsLines.isNotEmpty() && !isInstrumental) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = UnboundPrimary.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = "LIVE SYNC",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = UnboundPrimary,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            if (lyricsLines.isNotEmpty() && !isInstrumental) {
                IconButton(
                    onClick = onExpandLyrics,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.OpenInFull,
                        contentDescription = "Expand Lyrics",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(17.dp)
                    )
                }
            }
        }

        when {
            !hasTrack -> {
                UnboundEmptyStateCard(
                    title = "No Track Playing",
                    subtitle = "Start playing a track to view synchronized lyrics.",
                    icon = Icons.Default.MusicOff,
                    compact = true
                )
            }

            isInstrumental -> {
                UnboundEmptyStateCard(
                    title = "Instrumental Track",
                    subtitle = "No vocal lyrics are detected for this recording.",
                    icon = Icons.Default.GraphicEq,
                    compact = true
                )
            }

            lyricsLines.isEmpty() -> {
                UnboundEmptyStateCard(
                    title = "No Lyrics Available",
                    subtitle = "Synced or plain lyrics could not be found for this track.",
                    icon = Icons.Default.Lyrics,
                    compact = true
                )
            }

            else -> {
                val effectiveMs = currentPositionMs + timingOffsetMs
                val activeIndex = lyricsLines.indexOfFirst {
                    effectiveMs in it.startMs..it.endMs
                }.let {
                    if (it != -1) it else lyricsLines.indexOfLast { line -> line.startMs <= effectiveMs }.coerceAtLeast(0)
                }

                val startIdx = (activeIndex - 1).coerceAtLeast(0)
                val endIdx = (startIdx + 4).coerceAtMost(lyricsLines.lastIndex)

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (idx in startIdx..endIdx) {
                        val line = lyricsLines[idx]
                        val displayText = line.romanized.ifBlank { line.text }
                        val isActive = idx == activeIndex

                        if (isActive) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(UnboundSurfaceContainer)
                                    .clickable { onSeekPositionMs(line.startMs) }
                                    .padding(vertical = 10.dp, horizontal = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(3.5.dp)
                                        .height(22.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(UnboundPrimary)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = displayText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    lineHeight = 23.sp
                                )
                            }
                        } else {
                            Text(
                                text = displayText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurfaceVariant.copy(alpha = if (idx < activeIndex) 0.55f else 0.78f),
                                lineHeight = 21.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onSeekPositionMs(line.startMs) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lyricsSource.ifBlank { "Synced Lyrics" },
                        fontSize = 11.sp,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Tap to expand",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundPrimary,
                        modifier = Modifier.clickable(onClick = onExpandLyrics)
                    )
                }
            }
        }
    }
}

@Composable
private fun QueueDeckCard(
    queue: List<TrackItem>,
    currentTrack: TrackItem,
    onQueueTrackSelect: (Int) -> Unit,
    onMoveQueueItem: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemoveQueueItem: (index: Int) -> Unit,
    onBackToLyrics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Up Next (${queue.size})",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )

            Surface(
                shape = CircleShape,
                color = UnboundPrimary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBackToLyrics)
            ) {
                Text(
                    text = "Show Lyrics",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        if (queue.isEmpty()) {
            UnboundEmptyStateCard(
                title = "Queue Is Empty",
                subtitle = "Play an album, playlist, or radio mix to populate Up Next.",
                icon = Icons.AutoMirrored.Filled.QueueMusic,
                compact = true
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                contentPadding = PaddingValues(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(queue, key = { index, item -> "${item.id}_${index}" }) { index, item ->
                    val isCurrent = (item.id.isNotBlank() && item.id == currentTrack.id) ||
                        (item.id.isBlank() && item.title.equals(currentTrack.title, ignoreCase = true))
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

@Composable
private fun AudioSettingsDeckCard(
    playbackSpeed: Float,
    timingOffsetMs: Long,
    sleepTimerState: SleepTimerState,
    onSetPlaybackSpeedAndPitch: (speed: Float, pitch: Float) -> Unit,
    onTimingOffsetChange: (Long) -> Unit,
    onEqualizerClick: () -> Unit,
    onStartSleepTimer: (minutes: Int, endOfSong: Boolean) -> Unit,
    onCancelSleepTimer: () -> Unit,
    onBackToLyrics: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Playback & Audio DSP",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )

            Surface(
                shape = CircleShape,
                color = UnboundPrimary.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.55f)),
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onBackToLyrics)
            ) {
                Text(
                    text = "Show Lyrics",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }

        // Playback Speed & Pitch
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Playback Speed", fontSize = 12.sp, color = OnSurfaceVariant)
                Text(
                    text = String.format(Locale.US, "%.2fx", playbackSpeed),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary
                )
            }
            Slider(
                value = playbackSpeed,
                onValueChange = { speed -> onSetPlaybackSpeedAndPitch(speed, speed) },
                valueRange = 0.5f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = UnboundPrimary,
                    activeTrackColor = UnboundPrimary,
                    inactiveTrackColor = UnboundSurfaceContainerHighest
                )
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    val active = kotlin.math.abs(playbackSpeed - speed) < 0.02f
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (active) UnboundPrimary.copy(alpha = 0.18f) else UnboundSurfaceContainerHigh,
                        border = BorderStroke(1.dp, if (active) UnboundPrimary else UnboundOutlineVariant),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onSetPlaybackSpeedAndPitch(speed, speed) }
                    ) {
                        Text(
                            text = "${speed}x",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (active) UnboundPrimary else OnSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }

        // Lyrics Sync Offset
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Lyrics Sync: ${if (timingOffsetMs > 0) "+" else ""}${timingOffsetMs}ms",
                fontSize = 12.sp,
                color = OnSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(-250L to "-250ms", 0L to "Reset", 250L to "+250ms").forEach { (delta, label) ->
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UnboundSurfaceContainerHigh,
                        border = BorderStroke(1.dp, UnboundOutlineVariant),
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                if (delta == 0L) onTimingOffsetChange(0L)
                                else onTimingOffsetChange((timingOffsetMs + delta).coerceIn(-5000L, 5000L))
                            }
                    ) {
                        Text(
                            text = label,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnSurface,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        // 10-Band EQ & Sleep Timer
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onEqualizerClick),
                shape = RoundedCornerShape(12.dp),
                color = UnboundSurfaceContainerHigh,
                border = BorderStroke(1.dp, UnboundOutlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(18.dp))
                    Text(text = "10-Band EQ", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OnSurface)
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        if (sleepTimerState.isActive) onCancelSleepTimer()
                        else onStartSleepTimer(30, false)
                    },
                shape = RoundedCornerShape(12.dp),
                color = if (sleepTimerState.isActive) UnboundPrimary.copy(alpha = 0.16f) else UnboundSurfaceContainerHigh,
                border = BorderStroke(1.dp, if (sleepTimerState.isActive) UnboundPrimary else UnboundOutlineVariant)
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
                        text = if (sleepTimerState.isActive) "Timer: ${sleepTimerState.formattedRemaining}" else "Sleep 30m",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

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
        color = if (isCurrent) UnboundPrimary.copy(alpha = 0.14f) else UnboundSurfaceContainer,
        border = BorderStroke(1.dp, if (isCurrent) UnboundPrimary.copy(alpha = 0.55f) else UnboundOutlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            UnboundTrackThumbnail(
                coverUrl = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(8.dp),
                iconSize = 18.dp
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title.ifBlank { "Untitled Track" },
                    fontSize = 13.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = if (isCurrent) UnboundPrimary else OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (index > 0) {
                    IconButton(onClick = onMoveUp, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                if (index < totalCount - 1) {
                    IconButton(onClick = onMoveDown, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun rememberActiveAudioOutputDevice(context: Context): AudioOutputRoute {
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    fun resolveRoute(): AudioOutputRoute {
        val devices = try {
            audioManager?.getDevices(AudioManager.GET_DEVICES_OUTPUTS).orEmpty()
        } catch (_: Throwable) {
            emptyArray()
        }
        val bluetooth = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER))
        }
        if (bluetooth != null) {
            val rawName = bluetooth.productName?.toString()?.trim().orEmpty()
            val label = if (rawName.isNotBlank() && !rawName.equals(Build.MODEL, ignoreCase = true)) {
                rawName
            } else {
                "Bluetooth Audio"
            }
            return AudioOutputRoute(label = label, icon = Icons.Default.BluetoothAudio, isExternal = true)
        }

        val wired = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
        }
        if (wired != null) {
            return AudioOutputRoute(label = "Wired Headphones", icon = Icons.Default.Headphones, isExternal = true)
        }

        val usb = devices.firstOrNull {
            it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                it.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                it.type == AudioDeviceInfo.TYPE_USB_ACCESSORY
        }
        if (usb != null) {
            return AudioOutputRoute(label = "USB Audio", icon = Icons.Default.Headphones, isExternal = true)
        }

        return AudioOutputRoute(label = "Phone Speaker", icon = Icons.Default.Speaker, isExternal = false)
    }

    var route by remember(audioManager) { mutableStateOf(resolveRoute()) }

    DisposableEffect(audioManager) {
        if (audioManager == null) {
            onDispose { }
        } else {
            val callback = object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                    route = resolveRoute()
                }

                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                    route = resolveRoute()
                }
            }
            audioManager.registerAudioDeviceCallback(callback, null)
            onDispose {
                audioManager.unregisterAudioDeviceCallback(callback)
            }
        }
    }

    return route
}

private fun extractRealCodecTag(streamUrl: String): String? {
    val clean = streamUrl.substringBefore('?').lowercase(Locale.ROOT)
    return when {
        clean.endsWith(".flac") -> "FLAC"
        clean.endsWith(".wav") -> "WAV"
        clean.endsWith(".mp3") -> "MP3"
        clean.endsWith(".m4a") || clean.endsWith(".aac") -> "AAC"
        clean.endsWith(".opus") || clean.endsWith(".ogg") -> "OPUS"
        else -> null
    }
}

private fun shareTrack(context: Context, track: TrackItem) {
    if (track.title.isBlank()) return
    val shareBody = buildString {
        append(track.title)
        if (track.artist.isNotBlank()) {
            append(" — ")
            append(track.artist)
        }
        if (track.id.length == 11 && !track.id.startsWith("local")) {
            append("\nhttps://youtu.be/")
            append(track.id)
        }
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, track.title)
        putExtra(Intent.EXTRA_TEXT, shareBody)
    }
    val chooser = Intent.createChooser(intent, "Share Track").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(chooser)
    } catch (_: Throwable) {
    }
}
