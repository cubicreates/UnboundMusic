/*
 * Package: com.cubicreates.unboundmusic.ui.playlist
 * File: CustomPlaylistScreen.kt
 * Purpose: Elevated, interactive view for custom user-curated local playlists.
 *          Supports playing, shuffling, batch downloading, inline track search,
 *          track reordering (Move Up / Down), deleting tracks, and editing title/description/cover.
 * Subsystem: Local Playlist Management UI
 * Concurrency: Thread-safe Compose UI component with local state for search, dialogs, and menus.
 */

package com.cubicreates.unboundmusic.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

@Composable
fun CustomPlaylistScreen(
    playlist: CustomPlaylist,
    onBack: () -> Unit,
    onTrackSelect: (TrackItem) -> Unit,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    onDownloadAll: () -> Unit,
    onUpdateDetails: (title: String, description: String, coverUrl: String) -> Unit,
    onDeletePlaylist: () -> Unit,
    onRemoveTrack: (trackId: String) -> Unit,
    onMoveTrack: (fromIndex: Int, toIndex: Int) -> Unit,
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToQueue: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onStartDownload: (TrackItem) -> Unit = {},
    downloadedTrackIds: Set<String> = emptySet(),
    currentTrackId: String = "",
    isPlaying: Boolean = false
) {
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Filter tracks if searching
    val displayedTracks by remember(playlist.tracks, searchQuery) {
        derivedStateOf {
            if (searchQuery.isBlank()) {
                playlist.tracks
            } else {
                val query = searchQuery.trim().lowercase()
                playlist.tracks.filter {
                    it.title.lowercase().contains(query) ||
                    it.artist.lowercase().contains(query)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UnboundBackground)
            .statusBarsPadding()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            // Top App Bar
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = OnSurface
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(onClick = { isSearching = !isSearching }) {
                        Icon(
                            imageVector = if (isSearching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isSearching) "Close Search" else "Search Tracks",
                            tint = if (isSearching) UnboundPrimary else OnSurface
                        )
                    }

                    Box {
                        IconButton(onClick = { showOptionsMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Playlist Options",
                                tint = OnSurface
                            )
                        }

                        DropdownMenu(
                            expanded = showOptionsMenu,
                            onDismissRequest = { showOptionsMenu = false },
                            modifier = Modifier
                                .background(SurfaceGlassHighest)
                                .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Edit Playlist", color = OnSurface) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = UnboundPrimary
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showEditDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Delete Playlist", color = Color(0xFFFF5252)) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252)
                                    )
                                },
                                onClick = {
                                    showOptionsMenu = false
                                    showDeleteConfirmDialog = true
                                }
                            )
                        }
                    }
                }
            }

            // Playlist Hero Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Artwork Cover
                    Box(
                        modifier = Modifier
                            .size(190.dp)
                            .shadow(20.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(UnboundSurfaceContainerHigh)
                            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp)),
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
                                tint = UnboundPrimary.copy(alpha = 0.6f),
                                modifier = Modifier.size(72.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Title
                    Text(
                        text = playlist.title,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Description (if present)
                    if (playlist.description.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playlist.description,
                            fontSize = 13.sp,
                            color = OnSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Metadata
                    Text(
                        text = "${playlist.tracks.size} tracks • ${playlist.formattedDuration}",
                        fontSize = 12.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.8f),
                        fontFamily = FontFamily.Monospace
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Buttons: Play All, Shuffle All, Download All
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onPlayAll,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = UnboundPrimary,
                                contentColor = OnPrimary
                            ),
                            enabled = playlist.tracks.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Play", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        OutlinedButton(
                            onClick = onShuffleAll,
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp),
                            shape = RoundedCornerShape(24.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderGlass),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = SurfaceGlassHighest,
                                contentColor = OnSurface
                            ),
                            enabled = playlist.tracks.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = null,
                                tint = UnboundTertiary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = "Shuffle", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }

                        IconButton(
                            onClick = onDownloadAll,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(SurfaceGlassHighest)
                                .border(1.dp, BorderGlass, CircleShape),
                            enabled = playlist.tracks.isNotEmpty()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download All Tracks",
                                tint = UnboundPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // Search Bar Filter
            item {
                AnimatedVisibility(
                    visible = isSearching,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter playlist tracks...", color = OnSurfaceVariant, fontSize = 13.sp) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        shape = RoundedCornerShape(14.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = UnboundPrimary,
                            unfocusedBorderColor = BorderGlass,
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface,
                            focusedContainerColor = SurfaceGlassHighest,
                            unfocusedContainerColor = SurfaceGlassHighest
                        )
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Empty State
            if (playlist.tracks.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp, bottom = 40.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = OnSurfaceVariant.copy(alpha = 0.4f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "This playlist is empty",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add tracks using the 3-dots menu on any song",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            }

            // Tracks List
            itemsIndexed(displayedTracks, key = { _, track -> track.id }) { index, track ->
                val originalIndex = playlist.tracks.indexOfFirst { it.id == track.id }
                val isCurrentPlaying = track.id == currentTrackId
                val isDownloaded = downloadedTrackIds.contains(track.id)

                CustomPlaylistTrackRow(
                    index = index,
                    track = track,
                    isCurrentPlaying = isCurrentPlaying,
                    isDownloaded = isDownloaded,
                    canMoveUp = originalIndex > 0 && searchQuery.isBlank(),
                    canMoveDown = originalIndex < playlist.tracks.lastIndex && originalIndex >= 0 && searchQuery.isBlank(),
                    onClick = { onTrackSelect(track) },
                    onMoveUp = {
                        if (originalIndex > 0) {
                            onMoveTrack(originalIndex, originalIndex - 1)
                        }
                    },
                    onMoveDown = {
                        if (originalIndex >= 0 && originalIndex < playlist.tracks.lastIndex) {
                            onMoveTrack(originalIndex, originalIndex + 1)
                        }
                    },
                    onPlayNext = { onPlayNext(track) },
                    onAddToQueue = { onAddToQueue(track) },
                    onAddToPlaylist = { onAddToPlaylist(track) },
                    onRemove = { onRemoveTrack(track.id) },
                    onStartDownload = { onStartDownload(track) }
                )
            }

            // Bottom spacer for navigation bars and mini player
            item {
                Spacer(modifier = Modifier.height(120.dp))
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        // Edit Playlist Dialog
        if (showEditDialog) {
            EditPlaylistDialog(
                currentTitle = playlist.title,
                currentDescription = playlist.description,
                currentCoverUrl = playlist.coverUrl,
                onDismiss = { showEditDialog = false },
                onSave = { title, desc, cover ->
                    showEditDialog = false
                    onUpdateDetails(title, desc, cover)
                }
            )
        }

        // Delete Confirmation Dialog
        if (showDeleteConfirmDialog) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirmDialog = false },
                title = { Text("Delete Playlist?", color = OnSurface, fontWeight = FontWeight.Bold) },
                text = {
                    Text(
                        "Are you sure you want to delete \"${playlist.title}\"? This cannot be undone.",
                        color = OnSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showDeleteConfirmDialog = false
                            onDeletePlaylist()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF5252))
                    ) {
                        Text("Delete", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteConfirmDialog = false }) {
                        Text("Cancel", color = OnSurfaceVariant)
                    }
                },
                containerColor = SurfaceGlassHighest
            )
        }
    }
}

/**
 * Individual track row within a Custom Playlist.
 * Includes direct Move Up / Down controls for drag-and-drop-free precision reordering,
 * play on tap, and rich 3-dots actions.
 */
@Composable
private fun CustomPlaylistTrackRow(
    index: Int,
    track: TrackItem,
    isCurrentPlaying: Boolean,
    isDownloaded: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRemove: () -> Unit,
    onStartDownload: () -> Unit
) {
    var showTrackMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isCurrentPlaying) UnboundPrimary.copy(alpha = 0.12f) else SurfaceGlassHighest)
            .border(
                width = 1.dp,
                color = if (isCurrentPlaying) UnboundPrimary.copy(alpha = 0.4f) else BorderGlass,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Reordering controls (Move Up / Move Down)
        Column(
            modifier = Modifier.width(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowUp,
                    contentDescription = "Move Up",
                    tint = if (canMoveUp) OnSurfaceVariant else OnSurfaceVariant.copy(alpha = 0.2f),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Move Down",
                    tint = if (canMoveDown) OnSurfaceVariant else OnSurfaceVariant.copy(alpha = 0.2f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(6.dp))

        // Artwork Thumbnail
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
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Download Indicator
        IconButton(
            onClick = {
                if (!isDownloaded) onStartDownload()
            },
            modifier = Modifier.size(30.dp)
        ) {
            Icon(
                imageVector = if (isDownloaded) Icons.Default.CheckCircle else Icons.Default.Download,
                contentDescription = if (isDownloaded) "Downloaded" else "Download Track",
                tint = if (isDownloaded) UnboundPrimary else OnSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.size(16.dp)
            )
        }

        // 3-dots Menu
        Box {
            IconButton(
                onClick = { showTrackMenu = true },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track Actions",
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
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showTrackMenu = false
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
                        showTrackMenu = false
                        onAddToQueue()
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
                        showTrackMenu = false
                        onAddToPlaylist()
                    }
                )
                if (!isDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Download Track", color = OnSurface, fontSize = 13.sp) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null,
                                tint = UnboundPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        onClick = {
                            showTrackMenu = false
                            onStartDownload()
                        }
                    )
                }
                DropdownMenuItem(
                    text = { Text("Remove from Playlist", color = Color(0xFFFF5252), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showTrackMenu = false
                        onRemove()
                    }
                )
            }
        }
    }
}

/**
 * Dialog to edit custom playlist title, description, and cover image.
 */
@Composable
private fun EditPlaylistDialog(
    currentTitle: String,
    currentDescription: String,
    currentCoverUrl: String,
    onDismiss: () -> Unit,
    onSave: (title: String, description: String, coverUrl: String) -> Unit
) {
    var title by remember { mutableStateOf(currentTitle) }
    var description by remember { mutableStateOf(currentDescription) }
    var coverUrl by remember { mutableStateOf(currentCoverUrl) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Playlist", color = OnSurface, fontWeight = FontWeight.Bold) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Playlist Title", color = OnSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UnboundPrimary,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)", color = OnSurfaceVariant) },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UnboundPrimary,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = coverUrl,
                    onValueChange = { coverUrl = it },
                    label = { Text("Cover Image URL (optional)", color = OnSurfaceVariant) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UnboundPrimary,
                        unfocusedBorderColor = BorderGlass,
                        focusedTextColor = OnSurface,
                        unfocusedTextColor = OnSurface
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onSave(title.trim(), description.trim(), coverUrl.trim())
                    }
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = UnboundPrimary)
            ) {
                Text("Save", color = OnPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = OnSurfaceVariant)
            }
        },
        containerColor = SurfaceGlassHighest
    )
}
