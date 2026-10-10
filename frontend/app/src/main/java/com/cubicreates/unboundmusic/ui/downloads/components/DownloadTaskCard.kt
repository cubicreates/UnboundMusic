/*
 * Package: com.cubicreates.unboundmusic.ui.downloads.components
 * File: DownloadTaskCard.kt
 * Purpose: Individual Download Card displaying thumbnail, title, artist, live progress, speed, and controls.
 */

package com.cubicreates.unboundmusic.ui.downloads.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

@Composable
fun DownloadTaskCard(
    task: DownloadTaskDto,
    downloadSpeedBps: Long,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (task.progress.toFloat() / 100f).coerceIn(0f, 1f),
        label = "download_progress"
    )

    val remainingBytes = (task.totalBytes - task.downloadedBytes).coerceAtLeast(0)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlassHighest)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Row 1: Artwork + Title + Status + Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Artwork thumbnail
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(UnboundSurfaceContainerHigh)
                ) {
                    var taskCoverError by remember(task.artworkUrl) { mutableStateOf(false) }
                    if (task.artworkUrl.isNotBlank() && !taskCoverError) {
                        AsyncImage(
                            model = task.artworkUrl,
                            contentDescription = task.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            onError = { taskCoverError = true }
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier
                                .size(24.dp)
                                .align(Alignment.Center)
                        )
                    }
                }

                // Title + Artist + Format Badge
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title.ifBlank { "Downloading track..." },
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = task.artist.ifBlank { "Unknown Artist" },
                        fontSize = 13.sp,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    // Status Badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val (statusText, badgeColor) = when (task.status) {
                            "DOWNLOADING" -> Pair("Downloading", UnboundPrimary)
                            "TAGGING" -> Pair("Tagging ID3 / Opus", UnboundTertiary)
                            "PAUSED" -> Pair("Paused", Color(0xFFFFD54F))
                            "FAILED" -> Pair("Failed", Color(0xFFFFB4AB))
                            "QUEUED" -> Pair("Queued", Color(0xFF9FEFFE))
                            "COMPLETED" -> Pair("Completed", Color(0xFF81C784))
                            else -> Pair(task.status, OnSurfaceVariant)
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(badgeColor.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = statusText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor
                            )
                        }

                        // Format badge (MP3 320k)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(UnboundSurfaceContainerHigh)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = task.targetFormat.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }

                // Action buttons: Pause/Resume, Retry, Cancel
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (task.status) {
                        "DOWNLOADING", "TAGGING" -> {
                            IconButton(
                                onClick = onPause,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Pause,
                                    contentDescription = "Pause",
                                    tint = OnSurface,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        "PAUSED" -> {
                            IconButton(
                                onClick = onResume,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Resume",
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        "FAILED" -> {
                            IconButton(
                                onClick = onRetry,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Retry",
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Cancel button
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Cancel",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Row 2: Progress bar
            if (task.status == "DOWNLOADING" || task.status == "TAGGING" || task.status == "PAUSED") {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (task.status == "PAUSED") Color(0xFFFFD54F) else UnboundPrimary,
                    trackColor = UnboundSurfaceContainerHigh
                )

                // Row 3: Live Progress Metrics (Speed + Downloaded MB / Total MB + Remaining MB)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Downloaded / Total (Progress %)
                    val downloadedText = if (task.totalBytes > 0) {
                        "${formatBytes(task.downloadedBytes)} / ${formatBytes(task.totalBytes)} (${task.progress.toInt()}%)"
                    } else if (task.downloadedBytes > 0) {
                        formatBytes(task.downloadedBytes)
                    } else {
                        "Starting download..."
                    }

                    Text(
                        text = downloadedText,
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )

                    // Right: Live Speed and Remaining Size
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (downloadSpeedBps > 0 && task.status == "DOWNLOADING") {
                            Text(
                                text = formatSpeed(downloadSpeedBps),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = UnboundPrimary
                            )
                        }

                        if (remainingBytes > 0 && task.totalBytes > 0) {
                            Text(
                                text = "${formatBytes(remainingBytes)} left",
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                }
            } else if (task.status == "FAILED" && task.error.isNotBlank()) {
                Text(
                    text = "Error: ${task.error}",
                    fontSize = 12.sp,
                    color = Color(0xFFFFB4AB),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
