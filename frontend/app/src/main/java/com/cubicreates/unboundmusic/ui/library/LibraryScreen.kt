/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: LibraryScreen.kt
 * Purpose: Production Library & Storage Ingestion Screen for Unbound Music. Displays real-time
 *          storage savings, categorized source folders (Downloads, WhatsApp, Telegram, Synced),
 *          and scanned indexed audio tracks with direct play actions.
 * Subsystem: Personal Library / Storage UI
 */

package com.cubicreates.unboundmusic.ui.library

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.ui.components.DownloadButton
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundTopAppBar
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

data class IngestionSource(
    val title: String,
    val countText: String,
    val icon: ImageVector,
    val iconColor: Color
)

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    savedGB: Double = 0.0,
    downloadsCount: Int = 0,
    whatsappCount: Int = 0,
    telegramCount: Int = 0,
    youtubeCount: Int = 0,
    tracks: List<TrackItem> = emptyList(),
    syncedYouTubeTracks: List<TrackItem> = emptyList(),
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSourceClick: (IngestionSource) -> Unit = {},
    onTrackSelect: (TrackItem) -> Unit = {},
    onRefresh: () -> Unit = {},
    downloadTasks: Map<String, DownloadTaskDto> = emptyMap(),
    onStartDownload: (TrackItem) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onDeleteDownload: (String) -> Unit = {}
) {
    var selectedSourceTitle by remember { mutableStateOf<String?>(null) }

    val sources = listOf(
        IngestionSource(
            title = "Downloads",
            countText = "$downloadsCount tracks",
            icon = Icons.Default.Download,
            iconColor = UnboundPrimary
        ),
        IngestionSource(
            title = "WhatsApp Audio",
            countText = "$whatsappCount items",
            icon = Icons.Default.Forum,
            iconColor = UnboundTertiary
        ),
        IngestionSource(
            title = "Telegram Files",
            countText = "$telegramCount items",
            icon = Icons.Default.Send,
            iconColor = Color(0xFF9FEFFE)
        ),
        IngestionSource(
            title = "Synced YouTube",
            countText = if (youtubeCount > 0) "$youtubeCount tracks" else "Connect Account",
            icon = Icons.Default.Sync,
            iconColor = Color(0xFFFFB4AB)
        )
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 80.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. Header Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Library",
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        letterSpacing = (-0.02).sp
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Zero-data local storage & indexed music",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onRefresh,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Scan & Index",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // 2. Zero-Data Status Card (Bento Style)
            ZeroDataStatusCard(savedGB = savedGB)

            // 3. Ingestion Sources (2x2 Grid)
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "Storage Sources",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                    letterSpacing = (-0.01).sp
                )

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = UnboundSurfaceContainer, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                val chunked = sources.chunked(2)
                chunked.forEach { rowSources ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        rowSources.forEach { source ->
                            SourceCard(
                                source = source,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedSourceTitle = if (selectedSourceTitle == source.title) null else source.title
                                    onSourceClick(source)
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            // 4. Indexed Tracks List
            val displayTracks = when (selectedSourceTitle) {
                "Synced YouTube" -> syncedYouTubeTracks
                "Downloads" -> tracks.filter { it.source.contains("Downloads", ignoreCase = true) || it.source.contains("Unbound", ignoreCase = true) }
                null -> tracks
                else -> tracks.filter { it.source.equals(selectedSourceTitle, ignoreCase = true) }
            }

            if (displayTracks.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (selectedSourceTitle != null) "$selectedSourceTitle (${displayTracks.size})" else "Indexed Music (${displayTracks.size})",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        letterSpacing = (-0.01).sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    displayTracks.take(40).forEach { track ->
                        LibraryTrackRow(
                            track = track,
                            task = downloadTasks[track.id],
                            onClick = { onTrackSelect(track) },
                            onStartDownload = { onStartDownload(track) },
                            onCancelDownload = { onCancelDownload(track.id) },
                            onDeleteDownload = { onDeleteDownload(track.id) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(UnboundSurfaceContainer)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (selectedSourceTitle != null) "No tracks in $selectedSourceTitle" else "No indexed local tracks",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Download tracks or tap refresh to scan local device storage",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }

        // Fixed Top App Bar
        UnboundTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            onMenuClick = onMenuClick,
            onProfileClick = onProfileClick
        )
    }
}

@Composable
private fun ZeroDataStatusCard(savedGB: Double) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
            .padding(20.dp)
    ) {
        // Ambient Radial Glow at top right
        Box(
            modifier = Modifier
                .size(120.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(UnboundPrimary.copy(alpha = 0.25f), Color.Transparent)
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Icon + Title Row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(UnboundSurfaceContainerHigh),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Zero-Data Offline",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = "Zero-Data Interception",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                    Text(
                        text = "Streaming redirected to local high-res audio",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant
                    )
                }
            }

            // Stat Callout: "XX.X GB saved this month"
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = String.format("%.1f", savedGB),
                    fontSize = 44.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = UnboundPrimary,
                    letterSpacing = (-0.03).sp
                )
                Text(
                    text = "GB cellular data saved",
                    fontSize = 15.sp,
                    color = OnSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            // Progress Bar (Dynamic fill)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(UnboundSurfaceContainer)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(UnboundPrimary)
                )
            }
        }
    }
}

@Composable
private fun SourceCard(
    source: IngestionSource,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = source.icon,
                contentDescription = source.title,
                tint = source.iconColor,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = source.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface
            )

            Text(
                text = source.countText,
                fontSize = 12.sp,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun LibraryTrackRow(
    track: TrackItem,
    task: DownloadTaskDto?,
    onClick: () -> Unit,
    onStartDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onDeleteDownload: () -> Unit
) {
    val status = when {
        track.source.contains("Downloads", ignoreCase = true) || task?.status == "COMPLETED" -> DownloadUiStatus.DOWNLOADED
        task?.status == "DOWNLOADING" || task?.status == "TAGGING" || task?.status == "QUEUED" -> DownloadUiStatus.DOWNLOADING
        else -> DownloadUiStatus.NOT_DOWNLOADED
    }
    val progress = task?.progress ?: 0.0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(UnboundSurfaceContainerHigh),
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
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = UnboundPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = OnSurface,
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

        DownloadButton(
            status = status,
            progress = progress,
            onStartDownload = onStartDownload,
            onCancelDownload = onCancelDownload,
            onDeleteDownload = onDeleteDownload,
            trackTitle = track.title,
            size = 36.dp
        )

        Spacer(modifier = Modifier.width(6.dp))

        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = OnSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
