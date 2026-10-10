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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.downloads.components.DownloadQueueTab
import com.cubicreates.unboundmusic.ui.downloads.components.DownloadedTracksTab
import com.cubicreates.unboundmusic.ui.downloads.components.StorageOverviewCard
import com.cubicreates.unboundmusic.ui.downloads.components.formatSpeed
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

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
