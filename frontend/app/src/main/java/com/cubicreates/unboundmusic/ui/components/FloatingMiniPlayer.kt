/*
 * Package: com.cubicreates.unboundmusic.ui.components
 * File: FloatingMiniPlayer.kt
 * Purpose: Sleek, high-performance floating mini player for Unbound Music.
 * Subsystem: Player UI
 */

package com.cubicreates.unboundmusic.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlin.math.abs

@Composable
fun FloatingMiniPlayer(
    modifier: Modifier = Modifier,
    title: String = "Neon Ascend",
    artist: String = "Luna Ray",
    coverUrl: String = "",
    isPlaying: Boolean = false,
    isFavorite: Boolean = true,
    onPlayerClick: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onPlayPauseToggle: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    val animatedOffsetX by androidx.compose.animation.core.animateFloatAsState(
        targetValue = offsetX,
        label = "mini_player_offset_x"
    )
    val animatedOffsetY by androidx.compose.animation.core.animateFloatAsState(
        targetValue = offsetY,
        label = "mini_player_offset_y"
    )
    val dragAlpha = (1f - (abs(animatedOffsetX) / 320f + animatedOffsetY / 160f)).coerceIn(0f, 1f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .graphicsLayer {
                translationX = animatedOffsetX
                translationY = animatedOffsetY
                alpha = dragAlpha
            }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offsetX += dragAmount.x
                        if (dragAmount.y > 0 || offsetY > 0) {
                            offsetY = (offsetY + dragAmount.y).coerceAtLeast(0f)
                        }
                    },
                    onDragEnd = {
                        if (abs(offsetX) > 180f || offsetY > 70f) {
                            onDismiss()
                        } else {
                            offsetX = 0f
                            offsetY = 0f
                        }
                    },
                    onDragCancel = {
                        offsetX = 0f
                        offsetY = 0f
                    }
                )
            }
            .clickable(onClick = onPlayerClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF222222),
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383838))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Artwork + Title + Artist
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                UnboundTrackThumbnail(
                    coverUrl = coverUrl,
                    contentDescription = title,
                    modifier = Modifier.size(44.dp),
                    shape = RoundedCornerShape(10.dp),
                    iconSize = 22.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Favorite, Play/Pause & Close (YouTube Music Remover)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) UnboundPrimary else Color(0xFF888888),
                        modifier = Modifier.size(19.dp)
                    )
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove Mini Player",
                        tint = Color(0xFFAAAAAA),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
