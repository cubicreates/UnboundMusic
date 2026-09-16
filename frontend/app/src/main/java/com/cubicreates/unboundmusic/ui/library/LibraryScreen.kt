/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: LibraryScreen.kt
 * Purpose: Unified Single-Screen Music Player & Offline Library for Unbound Music.
 *          Implements the 6-tile categorized hub (Offline Tracks, Folders, Favorite,
 *          Recent Played, Downloaded, Shazam) with consistent Unbound dark OLED aesthetic,
 *          in-app folder browsing, unified favorites, and playlist management.
 * Subsystem: Personal Library / Offline Hub
 */

package com.cubicreates.unboundmusic.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

enum class LibrarySubView {
    HUB,
    TRACKS,
    FOLDERS,
    FOLDER_TRACKS,
    FAVORITES,
    RECENT_PLAYED,
    DOWNLOADED
}

data class AudioFolder(
    val name: String,
    val pathDescription: String,
    val tracks: List<TrackItem>
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
    favoriteTracks: List<TrackItem> = emptyList(),
    recentlyPlayedTracks: List<TrackItem> = emptyList(),
    downloadedTracks: List<TrackItem> = emptyList(),
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSourceClick: (String) -> Unit = {},
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit = { _, _ -> },
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
    onStartRadio: (TrackItem) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenRingtoneCutter: (TrackItem) -> Unit = {},
    onToggleFavorite: (TrackItem) -> Unit = {},
    onStartShazam: () -> Unit = {},
    onShufflePlayAll: () -> Unit = {}
) {
    var subView by remember { mutableStateOf(LibrarySubView.HUB) }
    var selectedFolder by remember { mutableStateOf<AudioFolder?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    // Intercept back navigation to return smoothly to previous subview or Hub
    BackHandler(enabled = subView != LibrarySubView.HUB) {
        if (subView == LibrarySubView.FOLDER_TRACKS) {
            subView = LibrarySubView.FOLDERS
        } else {
            subView = LibrarySubView.HUB
        }
    }

    // Dynamic extraction of audio folders from local indexed tracks
    val audioFolders = remember(tracks) {
        val grouped = mutableMapOf<String, MutableList<TrackItem>>()
        tracks.forEach { track ->
            val folderName = when {
                track.source.contains("WhatsApp", ignoreCase = true) -> "WhatsApp Audio"
                track.source.contains("Telegram", ignoreCase = true) -> "Telegram Audio"
                track.source.contains("Download", ignoreCase = true) || track.source.contains("Unbound", ignoreCase = true) -> "Downloads"
                track.source.contains("Music", ignoreCase = true) -> "Music"
                track.source.isNotBlank() && !track.source.equals("youtube", ignoreCase = true) -> track.source
                else -> "Device Audio"
            }
            grouped.getOrPut(folderName) { mutableListOf() }.add(track)
        }
        grouped.map { (name, list) ->
            AudioFolder(
                name = name,
                pathDescription = "Internal Storage • ${list.size} audio files",
                tracks = list
            )
        }.sortedByDescending { it.tracks.size }
    }

    // Combined favorites: both online synced and offline favorited tracks
    val combinedFavorites = remember(favoriteTracks, syncedYouTubeTracks) {
        (favoriteTracks + syncedYouTubeTracks.filter { it in favoriteTracks }).distinctBy { it.id }
            .ifEmpty { favoriteTracks }
    }

    // Effective downloaded tracks
    val effectiveDownloaded = remember(downloadedTracks, tracks) {
        if (downloadedTracks.isNotEmpty()) {
            downloadedTracks
        } else {
            tracks.filter {
                it.source.contains("Downloads", ignoreCase = true) ||
                it.source.contains("Unbound", ignoreCase = true)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Crossfade(
            targetState = subView,
            label = "library_view_crossfade"
        ) { currentView ->
            when (currentView) {
                LibrarySubView.HUB -> {
                    LibraryHubView(
                        tracksCount = tracks.size,
                        foldersCount = audioFolders.size,
                        favoritesCount = combinedFavorites.size,
                        recentlyPlayedCount = recentlyPlayedTracks.size,
                        downloadedCount = effectiveDownloaded.size,
                        customPlaylists = customPlaylists,
                        onOpenTracks = { subView = LibrarySubView.TRACKS },
                        onOpenFolders = { subView = LibrarySubView.FOLDERS },
                        onOpenFavorites = { subView = LibrarySubView.FAVORITES },
                        onOpenRecentlyPlayed = { subView = LibrarySubView.RECENT_PLAYED },
                        onOpenDownloaded = { subView = LibrarySubView.DOWNLOADED },
                        onStartShazam = onStartShazam,
                        onRefresh = onRefresh,
                        onCreatePlaylist = {
                            newPlaylistTitle = ""
                            showCreatePlaylistDialog = true
                        },
                        onPlaylistClick = onPlaylistClick
                    )
                }

                LibrarySubView.TRACKS -> {
                    LibraryTracksListView(
                        title = "Offline Tracks",
                        subtitle = "${tracks.size} tracks on this device",
                        tracks = tracks,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) }
                    )
                }

                LibrarySubView.FOLDERS -> {
                    LibraryFoldersListView(
                        folders = audioFolders,
                        onBack = { subView = LibrarySubView.HUB },
                        onFolderSelect = { folder ->
                            selectedFolder = folder
                            subView = LibrarySubView.FOLDER_TRACKS
                        }
                    )
                }

                LibrarySubView.FOLDER_TRACKS -> {
                    val currentFolder = selectedFolder
                    LibraryTracksListView(
                        title = currentFolder?.name ?: "Folder Tracks",
                        subtitle = "${currentFolder?.tracks?.size ?: 0} tracks in this folder",
                        tracks = currentFolder?.tracks ?: emptyList(),
                        onBack = { subView = LibrarySubView.FOLDERS },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) }
                    )
                }

                LibrarySubView.FAVORITES -> {
                    LibraryTracksListView(
                        title = "Favorite Songs",
                        subtitle = "${combinedFavorites.size} favorite songs • Online & Offline",
                        tracks = combinedFavorites,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) }
                    )
                }

                LibrarySubView.RECENT_PLAYED -> {
                    LibraryTracksListView(
                        title = "Recently Played",
                        subtitle = "${recentlyPlayedTracks.size} songs in playback history",
                        tracks = recentlyPlayedTracks,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) }
                    )
                }

                LibrarySubView.DOWNLOADED -> {
                    LibraryTracksListView(
                        title = "Downloaded Tracks",
                        subtitle = "${effectiveDownloaded.size} offline tracks ready for playback",
                        tracks = effectiveDownloaded,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) }
                    )
                }
            }
        }

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                containerColor = UnboundSurfaceContainerHigh,
                title = {
                    Text(
                        text = "Create Playlist",
                        color = OnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Give your playlist a title to organize your music.",
                            color = OnSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = newPlaylistTitle,
                            onValueChange = { newPlaylistTitle = it },
                            placeholder = { Text("Playlist name...", color = OnSurfaceVariant.copy(alpha = 0.6f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = UnboundPrimary,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistTitle.isNotBlank()) {
                                onCreatePlaylist(newPlaylistTitle.trim())
                                showCreatePlaylistDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = UnboundPrimary)
                    ) {
                        Text("Create", color = OnPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel", color = OnSurfaceVariant)
                    }
                }
            )
        }
    }
}

// ==================== 1. The Music Player Hub View ====================

@Composable
private fun LibraryHubView(
    tracksCount: Int,
    foldersCount: Int,
    favoritesCount: Int,
    recentlyPlayedCount: Int,
    downloadedCount: Int,
    customPlaylists: List<CustomPlaylist>,
    onOpenTracks: () -> Unit,
    onOpenFolders: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenRecentlyPlayed: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onStartShazam: () -> Unit,
    onRefresh: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistClick: (CustomPlaylist) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // Top Header: Clean "Music Player" title with refresh button (NO 3-dots, NO search icon)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Music Player",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    letterSpacing = (-0.02).sp
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Personal offline library & tools",
                    fontSize = 13.sp,
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
                    contentDescription = "Scan & Refresh",
                    tint = UnboundPrimary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // ==================== The 6 Hub Category Tiles ====================
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Row 1: OFFLINE TRACKS, FOLDERS, FAVORITE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UnboundHubTile(
                    title = "OFFLINE TRACKS",
                    count = tracksCount.toString(),
                    icon = Icons.Default.MusicNote,
                    accentColor = Color(0xFF2979FF), // Electric Blue
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTracks
                )

                UnboundHubTile(
                    title = "FOLDERS",
                    count = foldersCount.toString(),
                    icon = Icons.Default.Folder,
                    accentColor = Color(0xFFFF9100), // Warm Amber
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFolders
                )

                UnboundHubTile(
                    title = "FAVORITE",
                    count = favoritesCount.toString(),
                    icon = Icons.Default.Favorite,
                    accentColor = Color(0xFFFF5252), // Coral Red
                    modifier = Modifier.weight(1f),
                    onClick = onOpenFavorites
                )
            }

            // Row 2: RECENT PLAYED, DOWNLOADED, SHAZAM
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                UnboundHubTile(
                    title = "RECENT PLAYED",
                    count = recentlyPlayedCount.toString(),
                    icon = Icons.Default.History,
                    accentColor = Color(0xFF00E5FF), // Aqua Cyan
                    modifier = Modifier.weight(1f),
                    onClick = onOpenRecentlyPlayed
                )

                UnboundHubTile(
                    title = "DOWNLOADED",
                    count = downloadedCount.toString(),
                    icon = Icons.Default.Download,
                    accentColor = Color(0xFF00E676), // Neon Green
                    modifier = Modifier.weight(1f),
                    onClick = onOpenDownloaded
                )

                UnboundHubTile(
                    title = "SHAZAM",
                    count = "LISTEN",
                    icon = Icons.Default.GraphicEq,
                    accentColor = Color(0xFFB388FF), // Neon Purple
                    modifier = Modifier.weight(1f),
                    onClick = onStartShazam
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // ==================== Playlists Section ====================
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLAYLISTS (${customPlaylists.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    letterSpacing = 0.5.sp
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(UnboundPrimary.copy(alpha = 0.15f))
                        .clickable(onClick = onCreatePlaylist)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Playlist",
                            tint = UnboundPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "New",
                            color = UnboundPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (customPlaylists.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(SurfaceGlassHighest)
                        .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = null,
                            tint = OnSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No custom playlists yet",
                            color = OnSurface,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Tap '+ New' to create your first music playlist",
                            color = OnSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(customPlaylists) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist) }
                        )
                    }
                }
            }
        }
    }
}

// ==================== Unbound Hub Tile Component ====================

@Composable
private fun UnboundHubTile(
    title: String,
    count: String,
    icon: ImageVector,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(0.95f)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlassHighest)
            .border(
                width = 1.dp,
                color = BorderGlass,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Icon Pill
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.20f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text Info
            Column {
                Text(
                    text = count,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = title,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    letterSpacing = 0.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ==================== Playlist Card Component ====================

@Composable
private fun PlaylistCard(
    playlist: CustomPlaylist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(130.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(SurfaceGlassHighest)
            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            UnboundPrimary.copy(alpha = 0.3f),
                            UnboundTertiary.copy(alpha = 0.15f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            if (playlist.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
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
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "${playlist.tracks.size} tracks",
            fontSize = 11.sp,
            color = OnSurfaceVariant
        )
    }
}

// ==================== 2. Folders List View ====================

@Composable
private fun LibraryFoldersListView(
    folders: List<AudioFolder>,
    onBack: () -> Unit,
    onFolderSelect: (AudioFolder) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 90.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Folders",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Text(
                    text = "${folders.size} storage folders detected",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = BorderGlass, thickness = 1.dp)

        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No audio folders found on device",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceGlassHighest)
                            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                            .clickable { onFolderSelect(folder) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF9100).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = folder.name,
                                tint = Color(0xFFFF9100),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = folder.pathDescription,
                                fontSize = 12.sp,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Open",
                            tint = OnSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== 3. Generic Tracks List View (Offline, Folders, Favorites, etc.) ====================

@Composable
private fun LibraryTracksListView(
    title: String,
    subtitle: String,
    tracks: List<TrackItem>,
    onBack: () -> Unit,
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit,
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onOpenRingtoneCutter: (TrackItem) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit,
    onDeleteTrack: (TrackItem) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) {
            tracks
        } else {
            val q = searchQuery.trim().lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 90.dp)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        }

        // In-list Search Filter Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Filter tracks or artist...", color = OnSurfaceVariant.copy(alpha = 0.6f), fontSize = 13.sp) },
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
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UnboundPrimary,
                    unfocusedBorderColor = BorderGlass,
                    focusedContainerColor = SurfaceGlassHighest,
                    unfocusedContainerColor = SurfaceGlassHighest,
                    focusedTextColor = OnSurface,
                    unfocusedTextColor = OnSurface
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No tracks match '$searchQuery'" else "No tracks found in this section",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredTracks, key = { it.id.ifBlank { "${it.title}_${it.durationMs}" } }) { track ->
                    LibraryTrackRow(
                        track = track,
                        onClick = { onTrackSelect(track, filteredTracks) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onOpenRingtoneCutter = { onOpenRingtoneCutter(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onDeleteTrack = { onDeleteTrack(track) }
                    )
                }
            }
        }
    }
}

// ==================== 4. Individual Track Row Item ====================

@Composable
private fun LibraryTrackRow(
    track: TrackItem,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenRingtoneCutter: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteTrack: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlassHighest.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.durationMs > 0) {
                    Text(
                        text = " • ${formatDuration(track.durationMs)}",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 3-Dots Context Menu
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
                modifier = Modifier
                    .background(UnboundSurfaceContainerHigh)
                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
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
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = OnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = OnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Ringtone Cutter", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onOpenRingtoneCutter()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Favorite", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleFavorite()
                    }
                )

                HorizontalDivider(color = BorderGlass, thickness = 1.dp)

                DropdownMenuItem(
                    text = { Text("Delete Track", color = Color(0xFFFF5252), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDeleteTrack()
                    }
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%d:%02d".format(mins, secs)
}
