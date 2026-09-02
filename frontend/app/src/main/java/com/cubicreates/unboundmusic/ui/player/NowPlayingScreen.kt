package com.cubicreates.unboundmusic.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHighest

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
    lyricsLines: List<com.cubicreates.unboundmusic.viewmodel.LyricLine> = emptyList(),
    canvasArtUrl: String? = null,
    queue: List<TrackItem> = emptyList(),
    playbackMode: com.cubicreates.unboundmusic.service.PlaybackMode = com.cubicreates.unboundmusic.service.PlaybackMode.NORMAL,
    onCollapse: () -> Unit = {},
    onPlayPauseToggle: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onPreviousTrack: () -> Unit = {},
    onNextTrack: () -> Unit = {},
    onSeek: (Float) -> Unit = {},
    onSeekPositionMs: (Long) -> Unit = {},
    onCyclePlaybackMode: () -> Unit = {},
    onEqualizerClick: () -> Unit = {},
    onQueueTrackSelect: (Int) -> Unit = {}
) {
    var showQueueSheet by remember { mutableStateOf(false) }
    var showFullLyrics by remember { mutableStateOf(false) }

    // Kinetic syllable pulse animation
    val infiniteTransition = rememberInfiniteTransition(label = "syllable_pulse")
    val pulseAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse1"
    )
    val pulseAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse2"
    )
    val pulseAlpha3 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, delayMillis = 800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse3"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // 1. Immersive Ambient Background Layer with Morphing Blur
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            AsyncImage(
                model = canvasArtUrl ?: track.coverUrl.ifEmpty { DEFAULT_AMBIENT_BG },
                contentDescription = "Ambient Background",
                modifier = Modifier
                    .fillMaxSize()
                    .scale(1.2f)
                    .blur(60.dp),
                contentScale = ContentScale.Crop,
                alpha = 0.35f
            )

            // Dark vignette gradient overlay for high contrast
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                UnboundBackground.copy(alpha = 0.4f),
                                UnboundBackground.copy(alpha = 0.75f),
                                UnboundBackground.copy(alpha = 0.98f)
                            )
                        )
                    )
            )
        }

        // 2. Main Scrollable Canvas Area
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 16.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
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
                        text = "NOW PLAYING",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceVariant,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Vibes Playlist",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        color = UnboundPrimary.copy(alpha = 0.8f)
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

            Spacer(modifier = Modifier.height(16.dp))

            // High-Fidelity Album Artwork
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .aspectRatio(1f)
                    .shadow(
                        elevation = 28.dp,
                        shape = RoundedCornerShape(32.dp),
                        spotColor = UnboundPrimary.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(32.dp))
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(32.dp))
            ) {
                AsyncImage(
                    model = track.coverUrl.ifEmpty { DEFAULT_NOW_PLAYING_ART },
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )

                // Glassmorphic Sheen Overlay
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(Color.White.copy(alpha = 0.12f), Color.Transparent)
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Track Info & Favorite Heart Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = track.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = track.artist,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Normal,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Kinetic Syllable-Glow Lyrics Visualizer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Transparent),
                contentAlignment = Alignment.Center
            ) {
                if (lyricsLines.isNotEmpty()) {
                    // Estimate current elapsed ms from formattedPosition
                    val activeLine = lyricsLines.find { line ->
                        // approximate line selection or display primary lines
                        line.text.isNotBlank()
                    } ?: lyricsLines.first()

                    Text(
                        text = activeLine.text,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundPrimary,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Fading ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UnboundPrimary.copy(alpha = pulseAlpha1)
                        )
                        Text(
                            text = "into ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UnboundPrimary.copy(alpha = pulseAlpha2)
                        )
                        Text(
                            text = "the ",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UnboundPrimary.copy(alpha = pulseAlpha3)
                        )
                        Text(
                            text = "music...",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceVariant.copy(alpha = 0.35f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Tactile Progress Slider & Timers
            Column(modifier = Modifier.fillMaxWidth()) {
                Slider(
                    value = progress,
                    onValueChange = onSeek,
                    colors = SliderDefaults.colors(
                        thumbColor = UnboundPrimary,
                        activeTrackColor = UnboundPrimary,
                        inactiveTrackColor = UnboundSurfaceContainerHighest
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

            Spacer(modifier = Modifier.height(20.dp))

            // Primary Playback Controls (Oversized)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Mode (Left)
                IconButton(
                    onClick = onCyclePlaybackMode,
                    modifier = Modifier.size(44.dp)
                ) {
                    val modeIcon = when (playbackMode) {
                        com.cubicreates.unboundmusic.service.PlaybackMode.NORMAL -> Icons.Default.Refresh
                        com.cubicreates.unboundmusic.service.PlaybackMode.LOOP_ALL -> Icons.Default.Refresh
                        com.cubicreates.unboundmusic.service.PlaybackMode.LOOP_ONE -> Icons.Default.Star
                        com.cubicreates.unboundmusic.service.PlaybackMode.SHUFFLE -> Icons.Default.Refresh
                        com.cubicreates.unboundmusic.service.PlaybackMode.REVERSE_PLAY -> Icons.Default.Close
                    }
                    Icon(
                        imageVector = modeIcon,
                        contentDescription = "Playback Mode",
                        tint = if (playbackMode != com.cubicreates.unboundmusic.service.PlaybackMode.NORMAL) UnboundPrimary else OnSurfaceVariant
                    )
                }

                // Previous
                IconButton(
                    onClick = onPreviousTrack,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Previous Track",
                        tint = OnSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Play / Pause Giant Button (72x72)
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = CircleShape,
                            spotColor = UnboundPrimary.copy(alpha = 0.5f)
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
                        modifier = Modifier.size(36.dp)
                    )
                }

                // Next
                IconButton(
                    onClick = onNextTrack,
                    modifier = Modifier.size(52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowForward,
                        contentDescription = "Next Track",
                        tint = OnSurface,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // Favorite (Right)
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) UnboundPrimary else OnSurfaceVariant
                    )
                }
            }


            Spacer(modifier = Modifier.height(20.dp))

            // Floating UI Action Row: Lyrics / EQ / Queue / Share
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Lyrics Toggle Button
                IconButton(
                    onClick = { showFullLyrics = !showFullLyrics },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (showFullLyrics) UnboundPrimary.copy(alpha = 0.2f) else SurfaceGlassHighest)
                        .border(
                            width = 1.dp,
                            color = if (showFullLyrics) UnboundPrimary else BorderGlass,
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Lyrics",
                        tint = if (showFullLyrics) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // 10-Band Equalizer Button
                IconButton(
                    onClick = onEqualizerClick,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Equalizer",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Up Next Queue Button
                IconButton(
                    onClick = { showQueueSheet = true },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Queue",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Full Screen Kinetic Lyrics Overlay when toggled
        if (showFullLyrics) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UnboundBackground.copy(alpha = 0.95f))
                    .padding(top = 70.dp, bottom = 40.dp)
            ) {
                KineticLyricsView(
                    lyricsLines = lyricsLines,
                    currentPositionMs = currentPositionMs,
                    onLineClick = { line ->
                        onSeekPositionMs(line.startMs)
                        showFullLyrics = false
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

        // Up Next Queue Modal Sheet
        if (showQueueSheet) {
            QueueBottomSheet(
                queue = queue,
                currentTrack = track,
                playbackMode = playbackMode,
                onCycleMode = onCyclePlaybackMode,
                onTrackSelect = onQueueTrackSelect,
                onDismiss = { showQueueSheet = false }
            )
        }
    }
}

