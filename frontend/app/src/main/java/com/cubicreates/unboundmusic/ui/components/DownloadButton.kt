/*
 * Package: com.cubicreates.unboundmusic.ui.components
 * File: DownloadButton.kt
 * Purpose: Three-state interactive download button (NOT_DOWNLOADED, DOWNLOADING, DOWNLOADED) with circular progress and cancellation dialog.
 * Subsystem: Offline UI Components
 * Concurrency: Thread-safe Jetpack Compose state-driven component.
 */

package com.cubicreates.unboundmusic.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.DownloadUiStatus

/**
 * Animated three-state download action button.
 */
@Composable
fun DownloadButton(
    status: DownloadUiStatus,
    progress: Double,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    trackTitle: String = "Track"
) {
    var showCancelDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    val accentCyan = Color(0xFF22D3EE)
    val inactiveTint = Color(0xFFA1A1AA)

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        AnimatedContent(
            targetState = status,
            transitionSpec = {
                fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
            },
            label = "DownloadButtonState"
        ) { currentStatus ->
            when (currentStatus) {
                DownloadUiStatus.NOT_DOWNLOADED -> {
                    IconButton(
                        onClick = onStartDownload,
                        modifier = Modifier.size(size)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "Download Offline",
                            tint = inactiveTint,
                            modifier = Modifier.size(size * 0.6f)
                        )
                    }
                }

                DownloadUiStatus.DOWNLOADING -> {
                    Box(
                        modifier = Modifier
                            .size(size)
                            .clip(CircleShape)
                            .clickable { showCancelDialog = true },
                        contentAlignment = Alignment.Center
                    ) {
                        val progressFloat = (progress / 100.0).toFloat().coerceIn(0f, 1f)
                        if (progressFloat > 0f) {
                            CircularProgressIndicator(
                                progress = { progressFloat },
                                modifier = Modifier.size(size * 0.75f),
                                color = accentCyan,
                                trackColor = Color(0xFF27272A),
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(size * 0.75f),
                                color = accentCyan,
                                trackColor = Color(0xFF27272A),
                                strokeWidth = 2.5.dp
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cancel Download",
                            tint = accentCyan,
                            modifier = Modifier.size(size * 0.35f)
                        )
                    }
                }

                DownloadUiStatus.DOWNLOADED -> {
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(size)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded to Device",
                            tint = accentCyan,
                            modifier = Modifier.size(size * 0.65f)
                        )
                    }
                }
            }
        }
    }

    if (showCancelDialog) {
        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = { Text(text = "Cancel Download?", color = Color.White) },
            text = { Text(text = "Stop downloading '$trackTitle'?", color = Color(0xFFA1A1AA)) },
            containerColor = Color(0xFF18181B),
            confirmButton = {
                TextButton(
                    onClick = {
                        showCancelDialog = false
                        onCancelDownload()
                    }
                ) {
                    Text(text = "Cancel Download", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text(text = "Keep Downloading", color = Color.White)
                }
            }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(text = "Remove Download?", color = Color.White) },
            text = { Text(text = "Delete physical audio file for '$trackTitle' from device storage?", color = Color(0xFFA1A1AA)) },
            containerColor = Color(0xFF18181B),
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteDownload()
                    }
                ) {
                    Text(text = "Delete", color = Color(0xFFEF4444))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(text = "Keep", color = Color.White)
                }
            }
        )
    }
}
