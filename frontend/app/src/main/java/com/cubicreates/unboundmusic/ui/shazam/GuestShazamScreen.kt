/*
 * Package: com.cubicreates.unboundmusic.ui.shazam
 * File: GuestShazamScreen.kt
 * Purpose: Offline-first Shazam music recognition screen for Guest users with animated acoustic radar,
 *          16kHz FFT recognition status, recognized track card, and identification history.
 * Subsystem: Domain UI / Acoustic Recognition
 */

package com.cubicreates.unboundmusic.ui.shazam

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlass
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

@Composable
fun GuestShazamScreen(
    modifier: Modifier = Modifier,
    isListening: Boolean = false,
    audioAmplitude: Float = 0f,
    statusMessage: String? = null,
    lastRecognizedTrack: TrackItem? = null,
    shazamHistory: List<TrackItem> = emptyList(),
    onStartListening: () -> Unit = {},
    onDismissRecognized: () -> Unit = {},
    onPlayTrack: (TrackItem) -> Unit = {},
    onStartRadio: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onDownload: (TrackItem) -> Unit = {},
    onConnectYouTubeClick: () -> Unit = {}
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_waves")
    val pulseScale1 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.55f else 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 1000 else 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale_1"
    )
    val pulseScale2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListening) 1.95f else 1.16f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 1400 else 3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale_2"
    )
    val waveAlpha by infiniteTransition.animateFloat(
        initialValue = if (isListening) 0.5f else 0.15f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isListening) 1200 else 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "wave_alpha"
    )

    // Dynamic audio-reactive scale boost driven by real microphone energy
    val reactiveScale1 = pulseScale1 + (if (isListening) audioAmplitude * 0.25f else 0f)
    val reactiveScale2 = pulseScale2 + (if (isListening) audioAmplitude * 0.35f else 0f)
    val reactiveAlpha = (waveAlpha + (if (isListening) audioAmplitude * 0.35f else 0f)).coerceIn(0f, 1f)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Spacer(modifier = Modifier.height(16.dp))

            // Screen Header
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Acoustic Radar",
                    color = OnSurface,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.02).sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap to identify songs playing nearby with embedded Go SigX",
                    color = OnSurfaceVariant,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Central Animated Radar Sensor
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                // Outer Pulsing Ripple Ring 2
                Box(
                    modifier = Modifier
                        .size(190.dp)
                        .scale(reactiveScale2)
                        .border(
                            width = 1.5.dp,
                            color = UnboundPrimary.copy(alpha = reactiveAlpha),
                            shape = CircleShape
                        )
                )

                // Outer Pulsing Ripple Ring 1
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .scale(reactiveScale1)
                        .border(
                            width = 2.dp,
                            color = UnboundPrimary.copy(alpha = if (isListening) (reactiveAlpha + 0.15f).coerceAtMost(1f) else 0.1f),
                            shape = CircleShape
                        )
                )

                // Central Button
                Box(
                    modifier = Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = if (isListening) {
                                    listOf(UnboundPrimary, Color(0xFF007799))
                                } else {
                                    listOf(Color(0xFF242B30), Color(0xFF161A1D))
                                }
                            )
                        )
                        .border(
                            width = 2.dp,
                            color = if (isListening) Color.White else UnboundPrimary.copy(alpha = 0.6f),
                            shape = CircleShape
                        )
                        .clickable(enabled = !isListening, onClick = onStartListening),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Identify Song",
                        tint = if (isListening) Color.White else UnboundPrimary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // Live Audio Waveform Visualizer
            AcousticWaveformVisualizer(
                isListening = isListening,
                amplitude = audioAmplitude,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Live Status Text
            Text(
                text = when {
                    isListening -> statusMessage ?: "Listening to audio acoustics..."
                    !statusMessage.isNullOrBlank() -> statusMessage
                    lastRecognizedTrack != null -> "Match Found!"
                    else -> "Tap the radar to identify"
                },
                color = if (isListening) UnboundPrimary else OnSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Result Card when track is recognized
            AnimatedVisibility(
                visible = lastRecognizedTrack != null,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                lastRecognizedTrack?.let { track ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color(0xFF1E2226),
                        border = androidx.compose.foundation.BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.5f)),
                        shadowElevation = 8.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Album artwork
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0xFF2C3238)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (track.coverUrl.isNotBlank()) {
                                        AsyncImage(
                                            model = track.coverUrl,
                                            contentDescription = track.title,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.GraphicEq,
                                            contentDescription = null,
                                            tint = UnboundPrimary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = track.title,
                                        color = Color.White,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(3.dp))
                                    Text(
                                        text = track.artist.ifBlank { "Unknown Artist" },
                                        color = OnSurfaceVariant,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (track.album.isNotBlank()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = track.album,
                                            color = OnSurfaceVariant.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                IconButton(
                                    onClick = onDismissRecognized,
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Dismiss",
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Action buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Button(
                                    onClick = { onPlayTrack(track) },
                                    colors = ButtonDefaults.buttonColors(containerColor = UnboundPrimary),
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Play",
                                        color = Color.Black,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { onStartRadio(track) },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceGlass)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Radio,
                                        contentDescription = "Start Radio",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { onAddToPlaylist(track) },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceGlass)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                        contentDescription = "Add to Playlist",
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = { onDownload(track) },
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceGlass)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Download,
                                        contentDescription = "Download Offline",
                                        tint = Color.White
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Cloud Sync Prompt Card for Guest Users
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable(onClick = onConnectYouTubeClick),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1B1B1B),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Sync Identifications to YouTube",
                            color = OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Connect YouTube to automatically add recognized songs to your Liked Music playlist.",
                            color = OnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // History Section Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = UnboundPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Recent Identifications",
                    color = OnSurface,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // History items
        if (shazamHistory.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No songs identified yet.\nWhen audio is playing around you, tap the radar above.",
                        color = OnSurfaceVariant,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            items(shazamHistory, key = { it.id }) { track ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onPlayTrack(track) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1A1A1A),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF262626)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (track.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = track.coverUrl,
                                    contentDescription = track.title,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = null,
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = track.title,
                                color = OnSurface,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = track.artist.ifBlank { "Unknown Artist" },
                                color = OnSurfaceVariant,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        IconButton(
                            onClick = { onPlayTrack(track) },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = UnboundPrimary
                            )
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(96.dp))
        }
    }
}
