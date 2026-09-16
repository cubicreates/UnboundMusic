/*
 * Package: com.cubicreates.unboundmusic.ui.downloads
 * File: DownloadsScreen.kt
 * Purpose: Centralized In-App Download Queue & Storage Management Hub.
 *          Ported and adapted from SimpMusic with dedicated queue tabs,
 *          live download progress (speed, remaining MB), pause/resume/retry/cancel actions,
 *          downloaded offline playback, and one-tap "Clear Cache" and "Export to Storage" actions.
 * Subsystem: Offline Downloader & Storage Management UI
 * Concurrency: Thread-safe Compose UI driven by StateFlows from MainViewModel.
 */

package com.cubicreates.unboundmusic.ui.downloads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1000.0) {
        String.format("%.2f GB", mb / 1024.0)
    } else {
        String.format("%.1f MB", mb)
    }
}

private fun formatSpeed(bytesPerSec: Long): String {
    if (bytesPerSec <= 0) return "0 KB/s"
    val kb = bytesPerSec / 1024.0
    return if (kb >= 1000.0) {
        String.format("%.1f MB/s", kb / 1024.0)
    } else {
        String.format("%.0f KB/s", kb)
    }
}

/**
 * Full-screen modal hub for active download queues, downloaded tracks, and storage/cache controls.
 */
@Composable
fun DownloadsScreen(
    downloadTasks: Map<String, DownloadTaskDto>,
    downloadedTracks: List<TrackItem>,
    downloadSpeedBps: Long,
    cacheSizeMB: Double,
    downloadsSizeMB: Double,
    freeStorageGB: Double,
    onBack: () -> Unit,
    onPauseDownload: (String) -> Unit,
    onResumeDownload: (String) -> Unit,
    onCancelDownload: (String) -> Unit,
    onRetryDownload: (DownloadTaskDto) -> Unit,
    onDeleteDownload: (String, String) -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onPlayAllDownloaded: (List<TrackItem>) -> Unit,
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onStartRadio: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onClearCache: () -> Unit,
    onExportToStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    // Active tasks include QUEUED, DOWNLOADING, TAGGING, PAUSED, and FAILED
    val activeTasksList = remember(downloadTasks) {
        downloadTasks.values.sortedWith(
            compareByDescending<DownloadTaskDto> { it.status == "DOWNLOADING" || it.status == "TAGGING" }
                .thenByDescending { it.status == "PAUSED" }
                .thenByDescending { it.status == "QUEUED" }
        )
    }

    val filteredDownloadedTracks = remember(downloadedTracks, searchQuery) {
        if (searchQuery.isBlank()) {
            downloadedTracks
        } else {
            downloadedTracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Top Bar Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(1.dp, BorderGlass, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = OnSurface
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Downloads & Storage",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        letterSpacing = (-0.02).sp
                    )
                    Text(
                        text = "Offline queue & storage management",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant
                    )
                }

                if (downloadSpeedBps > 0) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(UnboundPrimary.copy(alpha = 0.15f))
                            .border(1.dp, UnboundPrimary.copy(alpha = 0.4f), RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Speed,
                            contentDescription = "Speed",
                            tint = UnboundPrimary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = formatSpeed(downloadSpeedBps),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = UnboundPrimary
                        )
                    }
                }
            }

            // 2. Storage Overview Card with One-Tap "Clear Cache" & "Export to Storage"
            StorageOverviewCard(
                downloadsSizeMB = downloadsSizeMB,
                cacheSizeMB = cacheSizeMB,
                freeStorageGB = freeStorageGB,
                onClearCache = onClearCache,
                onExportToStorage = onExportToStorage,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            // 3. Tab Selector: Queue (N) vs Downloaded (M)
            TabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = Color.Transparent,
                contentColor = UnboundPrimary,
                divider = { HorizontalDivider(color = BorderGlass, thickness = 1.dp) },
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = UnboundPrimary,
                        height = 3.dp
                    )
                },
                modifier = Modifier.padding(horizontal = 16.dp)
            ) {
                Tab(
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Queue",
                                fontSize = 15.sp,
                                fontWeight = if (selectedTabIndex == 0) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == 0) UnboundPrimary else OnSurfaceVariant
                            )
                            if (activeTasksList.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(if (selectedTabIndex == 0) UnboundPrimary else SurfaceGlassHighest)
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = activeTasksList.size.toString(),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selectedTabIndex == 0) Color.Black else OnSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                )

                Tab(
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Downloaded",
                                fontSize = 15.sp,
                                fontWeight = if (selectedTabIndex == 1) FontWeight.Bold else FontWeight.Medium,
                                color = if (selectedTabIndex == 1) UnboundPrimary else OnSurfaceVariant
                            )
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(if (selectedTabIndex == 1) UnboundPrimary else SurfaceGlassHighest)
                                    .padding(horizontal = 7.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = downloadedTracks.size.toString(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (selectedTabIndex == 1) Color.Black else OnSurfaceVariant
                                )
                            }
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 4. Tab Body Content
            when (selectedTabIndex) {
                0 -> {
                    DownloadQueueTab(
                        tasks = activeTasksList,
                        downloadSpeedBps = downloadSpeedBps,
                        onPause = onPauseDownload,
                        onResume = onResumeDownload,
                        onCancel = onCancelDownload,
                        onRetry = onRetryDownload,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                1 -> {
                    DownloadedTracksTab(
                        tracks = filteredDownloadedTracks,
                        searchQuery = searchQuery,
                        onSearchQueryChanged = { searchQuery = it },
                        onTrackSelect = onTrackSelect,
                        onPlayAll = { onPlayAllDownloaded(filteredDownloadedTracks) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onStartRadio = onStartRadio,
                        onAddToPlaylist = onAddToPlaylist,
                        onDelete = onDeleteDownload,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

/**
 * Storage Breakdown Bento Card with Storage Bar and One-Tap Actions.
 */
@Composable
private fun StorageOverviewCard(
    downloadsSizeMB: Double,
    cacheSizeMB: Double,
    freeStorageGB: Double,
    onClearCache: () -> Unit,
    onExportToStorage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceGlassHighest)
            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(UnboundSurfaceContainerHigh),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Storage",
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "Storage & Cache",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                }

                Text(
                    text = String.format("%.1f GB Free", freeStorageGB),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = OnSurfaceVariant
                )
            }

            // Visual Segmented Storage Bar
            val totalAssessedMB = (downloadsSizeMB + cacheSizeMB + 1.0).coerceAtLeast(10.0)
            val downloadsFraction = ((downloadsSizeMB / totalAssessedMB).toFloat()).coerceIn(0.05f, 0.90f)
            val cacheFraction = ((cacheSizeMB / totalAssessedMB).toFloat()).coerceIn(0.05f, 0.90f)

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape)
                        .background(UnboundSurfaceContainerHigh)
                ) {
                    // Downloads segment
                    Box(
                        modifier = Modifier
                            .weight(downloadsFraction)
                            .fillMaxHeight()
                            .background(UnboundPrimary)
                    )
                    // Cache segment
                    Box(
                        modifier = Modifier
                            .weight(cacheFraction)
                            .fillMaxHeight()
                            .background(UnboundTertiary)
                    )
                }

                // Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(UnboundPrimary)
                        )
                        Text(
                            text = "Downloads: ${String.format("%.1f MB", downloadsSizeMB)}",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(UnboundTertiary)
                        )
                        Text(
                            text = "Cache: ${String.format("%.1f MB", cacheSizeMB)}",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider(color = BorderGlass.copy(alpha = 0.5f), thickness = 1.dp)

            // One-Tap Quick Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // One-Tap "Clear Cache" button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(UnboundSurfaceContainerHigh)
                        .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                        .clickable { onClearCache() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeleteSweep,
                        contentDescription = "Clear Cache",
                        tint = UnboundTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Clear Cache",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                }

                // One-Tap "Export to Storage" button
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(UnboundSurfaceContainerHigh)
                        .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                        .clickable { onExportToStorage() }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderShared,
                        contentDescription = "Export to Storage",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Export to Media",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                }
            }
        }
    }
}

/**
 * Download Queue Tab: lists all active, paused, queued, and failed downloads with speed and remaining MB.
 */
@Composable
private fun DownloadQueueTab(
    tasks: List<DownloadTaskDto>,
    downloadSpeedBps: Long,
    onPause: (String) -> Unit,
    onResume: (String) -> Unit,
    onCancel: (String) -> Unit,
    onRetry: (DownloadTaskDto) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tasks.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(1.dp, BorderGlass, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DownloadDone,
                        contentDescription = "Empty Queue",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Download Queue is Idle",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Text(
                    text = "Songs, albums, or playlists you download will appear here with live speed and remaining file size.",
                    fontSize = 14.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            items(tasks, key = { it.videoId }) { task ->
                DownloadTaskCard(
                    task = task,
                    downloadSpeedBps = if (task.status == "DOWNLOADING" || task.status == "TAGGING") downloadSpeedBps else 0L,
                    onPause = { onPause(task.videoId) },
                    onResume = { onResume(task.videoId) },
                    onCancel = { onCancel(task.videoId) },
                    onRetry = { onRetry(task) }
                )
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

/**
 * Individual Download Card displaying thumbnail, title, artist, live progress, speed, and controls.
 */
@Composable
private fun DownloadTaskCard(
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

/**
 * Downloaded Tracks Tab: lists all physically downloaded tracks with search, play all, and item options.
 */
@Composable
private fun DownloadedTracksTab(
    tracks: List<TrackItem>,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onPlayAll: () -> Unit,
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onStartRadio: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit,
    onDelete: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Search & Play All Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Search Input Field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                placeholder = { Text("Filter downloaded songs...", fontSize = 14.sp, color = OnSurfaceVariant) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceGlassHighest,
                    unfocusedContainerColor = SurfaceGlassHighest,
                    focusedBorderColor = UnboundPrimary,
                    unfocusedBorderColor = BorderGlass,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )

            // "Play All" Button
            if (tracks.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(UnboundPrimary)
                        .clickable { onPlayAll() }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play All",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Play All",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(SurfaceGlassHighest)
                            .border(1.dp, BorderGlass, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "No Downloads",
                            tint = UnboundPrimary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Text(
                        text = if (searchQuery.isNotBlank()) "No Matching Downloads" else "No Downloaded Tracks Yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Text(
                        text = if (searchQuery.isNotBlank()) "Try checking for spelling or searching by artist." else "Download songs from any search result, album, or player screen for zero-data offline playback.",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(tracks, key = { it.id.ifBlank { it.streamUrl } }) { track ->
                    DownloadedTrackRow(
                        track = track,
                        onClick = { onTrackSelect(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onStartRadio = { onStartRadio(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onDelete = { onDelete(track.id, track.title) }
                    )
                }

                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

/**
 * Individual Downloaded Track Row with offline badge, direct playback, and 3-dots actions menu.
 */
@Composable
private fun DownloadedTrackRow(
    track: TrackItem,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit = {},
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlassHighest)
            .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UnboundSurfaceContainerHigh)
        ) {
            var trackCoverError by remember(track.coverUrl) { mutableStateOf(false) }
            if (track.coverUrl.isNotBlank() && !trackCoverError) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { trackCoverError = true }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = UnboundPrimary,
                    modifier = Modifier
                        .size(22.dp)
                        .align(Alignment.Center)
                )
            }

            // Green offline check badge
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2E7D32))
                    .align(Alignment.BottomEnd)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Offline",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp).align(Alignment.Center)
                )
            }
        }

        // Title + Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = track.artist,
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (track.album.isNotBlank()) {
                    Text(
                        text = "• ${track.album}",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Quick play button
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(UnboundPrimary.copy(alpha = 0.15f))
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play Offline",
                tint = UnboundPrimary,
                modifier = Modifier.size(20.dp)
            )
        }

        // 3-dots Options Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier.background(SurfaceGlassHighest)
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = OnSurface) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = OnSurface) },
                    onClick = {
                        showMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = OnSurface) },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, contentDescription = null, tint = OnSurface) },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Start Radio", color = OnSurface) },
                    leadingIcon = { Icon(Icons.Default.Radio, contentDescription = null, tint = OnSurface) },
                    onClick = {
                        showMenu = false
                        onStartRadio()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = OnSurface) },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = OnSurface) },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete from Storage", color = Color(0xFFFFB4AB)) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFFB4AB)) },
                    onClick = {
                        showMenu = false
                        onDelete()
                    }
                )
            }
        }
    }
}
