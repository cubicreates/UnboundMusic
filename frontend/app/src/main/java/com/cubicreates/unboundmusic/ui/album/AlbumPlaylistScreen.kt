/*
 * Package: com.cubicreates.unboundmusic.ui.album
 * File: AlbumPlaylistScreen.kt
 * Purpose: Elevated Album and Playlist detail view mirroring SimpMusic's rich structure:
 *          Hero artwork header, in-playlist search filter, batch multi-selection mode,
 *          animated playing equalizer bars, download status indicators, and full transport row.
 * Subsystem: Album / Playlist Detail UI
 * Concurrency: Thread-safe Compose UI component with local filter and selection state.
 */

package com.cubicreates.unboundmusic.ui.album

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

data class AlbumPlaylistData(
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val tracks: List<TrackItem> = emptyList(),
    val totalDuration: String = "45 mins"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AlbumPlaylistScreen(
    data: AlbumPlaylistData,
    onBack: () -> Unit = {},
    onTrackSelect: (TrackItem) -> Unit = {},
    onPlayAll: () -> Unit = {},
    onShuffleAll: () -> Unit = {},
    onStartDownload: (TrackItem) -> Unit = {},
    onDownloadAll: () -> Unit = {},
    downloadedTrackIds: Set<String> = emptySet(),
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToQueue: (TrackItem) -> Unit = {}
) {
    // In-playlist search state
    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // Multi-selection state
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedTrackIds = remember { mutableStateListOf<String>() }

    // Filtered tracklist
    val displayedTracks by remember(data.tracks, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) data.tracks
            else data.tracks.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UnboundBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 120.dp)
        ) {
            // 1. Navigation / Selection Top Bar
            item {
                if (isSelectionMode) {
                    SelectionTopBar(
                        selectedCount = selectedTrackIds.size,
                        totalCount = displayedTracks.size,
                        onSelectAll = {
                            if (selectedTrackIds.size == displayedTracks.size) {
                                selectedTrackIds.clear()
                            } else {
                                selectedTrackIds.clear()
                                selectedTrackIds.addAll(displayedTracks.map { it.id.ifBlank { it.title } })
                            }
                        },
                        onPlayNextBatch = {
                            val selectedTracks = data.tracks.filter { it.id in selectedTrackIds || it.title in selectedTrackIds }
                            selectedTracks.reversed().forEach { onPlayNext(it) }
                            isSelectionMode = false
                            selectedTrackIds.clear()
                        },
                        onDownloadBatch = {
                            val selectedTracks = data.tracks.filter { it.id in selectedTrackIds || it.title in selectedTrackIds }
                            selectedTracks.forEach { onStartDownload(it) }
                            isSelectionMode = false
                            selectedTrackIds.clear()
                        },
                        onCancel = {
                            isSelectionMode = false
                            selectedTrackIds.clear()
                        }
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(SurfaceGlassHighest)
                                .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = OnSurface
                            )
                        }

                        IconButton(
                            onClick = { isSearching = !isSearching },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(if (isSearching) UnboundPrimary else SurfaceGlassHighest)
                                .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                        ) {
                            Icon(
                                imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                                contentDescription = "Search Playlist",
                                tint = if (isSearching) OnPrimary else OnSurface
                            )
                        }
                    }
                }
            }

            // 2. Artwork & Metadata Header (Hidden during active search to maximize space)
            if (!isSearching) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(210.dp)
                                .shadow(elevation = 28.dp, shape = RoundedCornerShape(22.dp), spotColor = UnboundPrimary.copy(alpha = 0.5f))
                                .clip(RoundedCornerShape(22.dp))
                                .background(SurfaceGlassHighest)
                                .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(22.dp))
                        ) {
                            AsyncImage(
                                model = data.coverUrl,
                                contentDescription = data.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        Spacer(modifier = Modifier.height(18.dp))

                        Text(
                            text = data.title,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (data.subtitle.isNotBlank()) data.subtitle else "Curated Playlist",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = UnboundPrimary
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = "${data.tracks.size} tracks • ${data.totalDuration}",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // 3. Transport Action Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Play All Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(UnboundPrimary)
                                .clickable(onClick = onPlayAll),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = OnPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Play All",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnPrimary
                                )
                            }
                        }

                        // Shuffle Button
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceGlassHighest)
                                .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(14.dp))
                                .clickable(onClick = onShuffleAll),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = UnboundPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Download All Button
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SurfaceGlassHighest)
                                .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(14.dp))
                                .clickable(onClick = onDownloadAll),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download All",
                                tint = UnboundPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // 4. In-Playlist Live Search Filter Bar
            if (isSearching) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("Filter in playlist...", color = OnSurfaceVariant, fontSize = 13.sp) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(18.dp))
                            },
                            trailingIcon = {
                                if (searchQuery.isNotBlank()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = UnboundPrimary,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            // 5. Tracklist Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isSearching) "FILTERED RESULTS (${displayedTracks.size})" else "TRACKS (${data.tracks.size})",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    if (!isSelectionMode && data.tracks.isNotEmpty()) {
                        Text(
                            text = "Long-press to select",
                            fontSize = 11.sp,
                            color = OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            // 6. Interactive Track Rows
            if (displayedTracks.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "No tracks matched '$searchQuery'" else "Curating playlist tracks...",
                            fontSize = 13.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }
            } else {
                itemsIndexed(displayedTracks, key = { index, track -> track.id.ifBlank { "${track.title}_$index" } }) { index, track ->
                    val trackIdentifier = track.id.ifBlank { track.title }
                    val isSelected = trackIdentifier in selectedTrackIds
                    val isCurrentPlaying = (track.id.isNotBlank() && track.id == currentTrackId) ||
                            (track.title.isNotBlank() && track.title.equals(currentTrackId, ignoreCase = true))
                    val isDownloaded = track.id in downloadedTrackIds

                    PlaylistTrackRow(
                        index = index + 1,
                        track = track,
                        isSelected = isSelected,
                        isSelectionMode = isSelectionMode,
                        isCurrentPlaying = isCurrentPlaying,
                        isPlaying = isPlaying,
                        isDownloaded = isDownloaded,
                        onClick = {
                            if (isSelectionMode) {
                                if (isSelected) selectedTrackIds.remove(trackIdentifier)
                                else selectedTrackIds.add(trackIdentifier)
                            } else {
                                onTrackSelect(track)
                            }
                        },
                        onLongClick = {
                            if (!isSelectionMode) {
                                isSelectionMode = true
                                selectedTrackIds.add(trackIdentifier)
                            }
                        },
                        onStartDownload = { onStartDownload(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) }
                    )
                }
            }
        }
    }
}

/**
 * Top action bar displayed when multi-selection mode is active.
 */
@Composable
private fun SelectionTopBar(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onPlayNextBatch: () -> Unit,
    onDownloadBatch: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlassHighest)
            .border(1.dp, UnboundPrimary.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Cancel Selection", tint = OnSurface)
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "$selectedCount selected",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = onSelectAll, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.SelectAll, contentDescription = "Select All", tint = UnboundPrimary)
            }
            if (selectedCount > 0) {
                IconButton(onClick = onPlayNextBatch, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.QueueMusic, contentDescription = "Play Next", tint = UnboundPrimary)
                }
                IconButton(onClick = onDownloadBatch, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Download, contentDescription = "Download Selected", tint = UnboundPrimary)
                }
            }
        }
    }
}

/**
 * Individual track row with playing equalizer bars, checkbox, duration, and contextual actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistTrackRow(
    index: Int,
    track: TrackItem,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    isCurrentPlaying: Boolean,
    isPlaying: Boolean,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onStartDownload: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit
) {
    var showTrackMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) UnboundPrimary.copy(alpha = 0.15f) else SurfaceGlassHighest)
            .border(
                width = 1.dp,
                color = if (isSelected) UnboundPrimary.copy(alpha = 0.5f) else BorderGlass,
                shape = RoundedCornerShape(12.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Selection Checkbox or Index Number / Animated Equalizer
        if (isSelectionMode) {
            Checkbox(
                checked = isSelected,
                onCheckedChange = { onClick() },
                colors = CheckboxDefaults.colors(
                    checkedColor = UnboundPrimary,
                    uncheckedColor = OnSurfaceVariant,
                    checkmarkColor = OnPrimary
                ),
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        } else if (isCurrentPlaying) {
            // Animated Playing Equalizer Waveform indicator
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(20.dp),
                contentAlignment = Alignment.Center
            ) {
                EqualizerWaveBars(isPlaying = isPlaying)
            }
        } else {
            Text(
                text = "$index",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(26.dp)
            )
        }

        // Cover Thumbnail
        Box(
            modifier = Modifier
                .size(42.dp)
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

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                color = if (isCurrentPlaying) UnboundPrimary else OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                fontSize = 12.sp,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Formatted Duration
        if (track.durationMs > 0) {
            val totalSec = track.durationMs / 1000
            val mins = totalSec / 60
            val secs = totalSec % 60
            Text(
                text = String.format("%d:%02d", mins, secs),
                fontSize = 11.sp,
                color = OnSurfaceVariant,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }

        // Per-track Download Indicator
        IconButton(
            onClick = {
                if (!isDownloaded) {
                    onStartDownload()
                }
            },
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                contentDescription = if (isDownloaded) "Downloaded" else "Download Track",
                tint = if (isDownloaded) UnboundPrimary else OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }

        // 3-dots Context Menu
        Box {
            IconButton(
                onClick = { showTrackMenu = true },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More Actions",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = showTrackMenu,
                onDismissRequest = { showTrackMenu = false },
                modifier = Modifier
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showTrackMenu = false
                        onPlayNext()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Queue", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(18.dp)) },
                    onClick = {
                        showTrackMenu = false
                        onAddToQueue()
                    }
                )
                if (!isDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Download MP3", color = OnSurface, fontSize = 13.sp) },
                        leadingIcon = { Icon(Icons.Default.Download, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(18.dp)) },
                        onClick = {
                            showTrackMenu = false
                            onStartDownload()
                        }
                    )
                }
            }
        }
    }
}

/**
 * Animated Equalizer Bars for the currently playing track.
 */
@Composable
private fun EqualizerWaveBars(isPlaying: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "eq")
        val heights = listOf(
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "bar1"
            ),
            transition.animateFloat(
                initialValue = 0.8f,
                targetValue = 0.2f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(550, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "bar2"
            ),
            transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.9f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(350, easing = androidx.compose.animation.core.LinearEasing),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
                ),
                label = "bar3"
            )
        )

        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (isPlaying) (14.dp * h.value).coerceAtLeast(3.dp) else 4.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(UnboundPrimary)
            )
        }
    }
}
