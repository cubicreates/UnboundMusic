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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.ui.components.DownloadButton
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
    onDeleteDownload: (String) -> Unit = {},
    onOpenDownloadsHub: () -> Unit = {},
    customPlaylists: List<CustomPlaylist> = emptyList(),
    onCreatePlaylist: (title: String) -> Unit = {},
    onPlaylistClick: (CustomPlaylist) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToQueue: (TrackItem) -> Unit = {},
    onStartRadio: (TrackItem) -> Unit = {}
) {
    var selectedSourceTitle by remember { mutableStateOf<String?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

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
                .padding(top = 8.dp, bottom = 24.dp),
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

            // 2.5 Your Playlists (Carousel)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Playlists",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        letterSpacing = (-0.01).sp
                    )

                    TextButton(onClick = {
                        newPlaylistTitle = ""
                        showCreatePlaylistDialog = true
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", color = UnboundPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = UnboundSurfaceContainer, thickness = 1.dp)
                Spacer(modifier = Modifier.height(14.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Create Playlist Action Card
                    item {
                        CreatePlaylistCard(
                            onClick = {
                                newPlaylistTitle = ""
                                showCreatePlaylistDialog = true
                            }
                        )
                    }

                    // Existing Custom Playlists
                    items(customPlaylists, key = { it.id }) { playlist ->
                        CustomPlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }

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
                                    if (source.title == "Downloads") {
                                        onOpenDownloadsHub()
                                    } else {
                                        selectedSourceTitle = if (selectedSourceTitle == source.title) null else source.title
                                        onSourceClick(source)
                                    }
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
                        val task = downloadTasks[track.id]
                            ?: downloadTasks.values.find { it.title.isNotBlank() && it.title.equals(track.title, ignoreCase = true) }
                        LibraryTrackRow(
                            track = track,
                            task = task,
                            onClick = { onTrackSelect(track) },
                            onStartDownload = { onStartDownload(track) },
                            onCancelDownload = { onCancelDownload(task?.videoId ?: track.id) },
                            onDeleteDownload = { onDeleteDownload(task?.videoId ?: track.id) },
                            onAddToPlaylist = { onAddToPlaylist(track) },
                            onPlayNext = { onPlayNext(track) },
                            onAddToQueue = { onAddToQueue(track) },
                            onStartRadio = { onStartRadio(track) }
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

        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                title = { Text("New Playlist", color = OnSurface, fontWeight = FontWeight.Bold) },
                text = {
                    OutlinedTextField(
                        value = newPlaylistTitle,
                        onValueChange = { newPlaylistTitle = it },
                        label = { Text("Playlist Name", color = OnSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = UnboundPrimary,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface
                        )
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistTitle.isNotBlank()) {
                                onCreatePlaylist(newPlaylistTitle.trim())
                                showCreatePlaylistDialog = false
                                newPlaylistTitle = ""
                            }
                        },
                        enabled = newPlaylistTitle.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = UnboundPrimary)
                    ) {
                        Text("Create", color = Color.Black)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel", color = OnSurfaceVariant)
                    }
                },
                containerColor = SurfaceGlassHighest
            )
        }
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
    onDeleteDownload: () -> Unit,
    onAddToPlaylist: () -> Unit = {},
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onStartRadio: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

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

        Spacer(modifier = Modifier.width(4.dp))

        // 3-dots Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track Actions",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.QueueMusic,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Start Radio", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Radio,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onStartRadio()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PlaylistAdd,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )
            }
        }
    }
}

@Composable
private fun CreatePlaylistCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .width(130.dp)
            .height(160.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlassHighest)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(UnboundPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Playlist",
                    tint = UnboundPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "New Playlist",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Create custom",
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun CustomPlaylistCard(
    playlist: CustomPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(130.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlassHighest)
            .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(UnboundSurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.effectiveCoverUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.effectiveCoverUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = UnboundPrimary,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${playlist.tracks.size} tracks",
            fontSize = 11.sp,
            color = OnSurfaceVariant,
            maxLines = 1
        )
    }
}
