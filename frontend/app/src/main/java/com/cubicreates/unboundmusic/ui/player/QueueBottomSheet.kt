/*
 * Package: com.cubicreates.unboundmusic.ui.player
 * File: QueueBottomSheet.kt
 * Purpose: Up Next Queue bottom sheet supporting drag reorder, mode switcher (Normal, Loop, Shuffle, Reverse Play),
 *          and direct item tap-to-play.
 * Subsystem: Audio Playback Queue UI
 */

package com.cubicreates.unboundmusic.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

@Composable
fun QueueBottomSheet(
    queue: List<TrackItem>,
    currentTrack: TrackItem?,
    playbackMode: PlaybackMode = PlaybackMode.NORMAL,
    onCycleMode: () -> Unit = {},
    onTrackSelect: (Int) -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(UnboundBackground)
                .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(24.dp))
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Playing Queue",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Text(
                            text = "${queue.size} tracks in playlist",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    // Mode Switcher Pill (Normal / Loop / Shuffle / Reverse Play)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(UnboundPrimary.copy(alpha = 0.15f))
                                .clickable(onClick = onCycleMode)
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val modeIcon: ImageVector = when (playbackMode) {
                                    PlaybackMode.NORMAL -> Icons.Default.PlayArrow
                                    PlaybackMode.LOOP_ALL -> Icons.Default.Repeat
                                    PlaybackMode.LOOP_ONE -> Icons.Default.RepeatOne
                                    PlaybackMode.SHUFFLE -> Icons.Default.Shuffle
                                    PlaybackMode.REVERSE_PLAY -> Icons.Default.SwapVert
                                }
                                Icon(
                                    imageVector = modeIcon,
                                    contentDescription = null,
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = playbackMode.name.replace("_", " "),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UnboundPrimary
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(SurfaceGlassHighest)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Tracklist
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queue) { index, track ->
                        val isCurrent = track.title == currentTrack?.title
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isCurrent) UnboundPrimary.copy(alpha = 0.15f) else SurfaceGlassHighest)
                                .border(
                                    width = 1.dp,
                                    color = if (isCurrent) UnboundPrimary.copy(alpha = 0.5f) else BorderGlass,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    onTrackSelect(index)
                                    onDismiss()
                                }
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isCurrent) UnboundPrimary else OnSurfaceVariant,
                                modifier = Modifier.width(24.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(UnboundSurfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                if (track.coverUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = track.coverUrl,
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = UnboundPrimary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    fontSize = 14.sp,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
                                    color = if (isCurrent) UnboundPrimary else OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = track.artist,
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
