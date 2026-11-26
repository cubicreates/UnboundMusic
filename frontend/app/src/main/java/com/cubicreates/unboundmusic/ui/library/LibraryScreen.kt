/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: LibraryScreen.kt
 * Purpose: Unified, simple Music Library screen matching the app aesthetic.
 *          Features Play/Shuffle All Tracks hero card, Liked Tracks, Downloaded Tracks,
 *          Device & Download Folders, Recent History, Album Completion, Playlists, and Recently Added.
 * Subsystem: Personal Library / Presentation
 */

package com.cubicreates.unboundmusic.ui.library

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.AlbumCompletionStatus
import com.cubicreates.unboundmusic.data.AudioCategory
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundEmptyStateCard
import com.cubicreates.unboundmusic.ui.home.components.MediaFeedScaffold
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    isYouTubeConnected: Boolean = false,
    tracks: List<TrackItem> = emptyList(),
    musicTracks: List<TrackItem> = emptyList(),
    mixedAudioTracks: List<TrackItem> = emptyList(),
    syncedYouTubeTracks: List<TrackItem> = emptyList(),
    favoriteTracks: List<TrackItem> = emptyList(),
    recentlyPlayedTracks: List<TrackItem> = emptyList(),
    downloadedTracks: List<TrackItem> = emptyList(),
    onConnectClick: () -> Unit = {},
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    onDeleteDownload: (String) -> Unit = {},
    customPlaylists: List<CustomPlaylist> = emptyList(),
    artistPlaylists: List<CustomPlaylist> = emptyList(),
    albumicPlaylists: List<CustomPlaylist> = emptyList(),
    albumCompletions: List<AlbumCompletionStatus> = emptyList(),
    onDownloadRemaining: (AlbumCompletionStatus) -> Unit = {},
    onCreatePlaylist: (title: String) -> Unit = {},
    onPlaylistClick: (CustomPlaylist) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToQueue: (TrackItem) -> Unit = {},
    onOpenRingtoneCutter: (TrackItem) -> Unit = {},
    onToggleFavorite: (TrackItem) -> Unit = {},
    onShufflePlayAll: () -> Unit = {},
    onIdentifyTrack: (TrackItem) -> Unit = {},
    onBatchIdentify: () -> Unit = {},
    allFilesGranted: Boolean = true,
    onRequestAllFilesPermission: () -> Unit = {}
) {
    var subView by remember { mutableStateOf(LibrarySubView.HUB) }
    var selectedFolder by remember { mutableStateOf<AudioFolder?>(null) }
    var selectedFilter by remember { mutableStateOf("All") }
    var searchQuery by remember { mutableStateOf("") }
    var sortOption by remember { mutableStateOf(LibrarySortOption.RECENTLY_ADDED) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    BackHandler(enabled = subView != LibrarySubView.HUB) {
        if (subView == LibrarySubView.FOLDER_TRACKS) {
            subView = LibrarySubView.FOLDERS
        } else {
            subView = LibrarySubView.HUB
            selectedFilter = "All"
        }
    }

    val isCloudActive = isYouTubeConnected || syncedYouTubeTracks.isNotEmpty()
    val audioFolders = remember(tracks) { extractAudioFolders(tracks) }
    val effectiveDownloaded = remember(downloadedTracks, tracks) {
        if (downloadedTracks.isNotEmpty()) downloadedTracks
        else tracks.filter { it.source.contains("Downloads", true) || it.source.contains("Unbound", true) }
    }
    val effectiveMusicTracks = remember(musicTracks, tracks) {
        if (musicTracks.isNotEmpty()) musicTracks
        else tracks.filter { it.audioCategory == AudioCategory.MUSIC || it.isIdentifiedMusic }.ifEmpty { tracks }
    }
    val effectiveMixedAudioTracks = remember(mixedAudioTracks, tracks) {
        if (mixedAudioTracks.isNotEmpty()) mixedAudioTracks
        else tracks.filter { it.audioCategory == AudioCategory.MIXED_AUDIO && !it.isIdentifiedMusic }
    }
    val combinedFavorites = remember(favoriteTracks, syncedYouTubeTracks, isCloudActive) {
        if (isCloudActive) {
            (favoriteTracks + syncedYouTubeTracks.filter { it in favoriteTracks }).distinctBy { it.id }.ifEmpty { favoriteTracks }
        } else {
            favoriteTracks
        }
    }
    val allPlaylists = remember(customPlaylists, albumicPlaylists, artistPlaylists) {
        (albumicPlaylists + artistPlaylists + customPlaylists).distinctBy { it.id }
    }
    val filteredRecentTracks = remember(effectiveMusicTracks, searchQuery, sortOption) {
        val base = if (searchQuery.isBlank()) {
            effectiveMusicTracks
        } else {
            val q = searchQuery.trim().lowercase()
            effectiveMusicTracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q) || it.album.lowercase().contains(q)
            }
        }
        sortLibraryTracks(base, sortOption)
    }
    val artistInitials = remember(effectiveMusicTracks) {
        effectiveMusicTracks
            .mapNotNull { it.artist.trim().takeIf { a -> a.isNotBlank() } }
            .distinct()
            .take(3)
            .map { artist ->
                artist.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifBlank { "UB" }
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Crossfade(
            targetState = subView,
            label = "library_subview"
        ) { currentView ->
            when (currentView) {
                LibrarySubView.HUB -> {
                    val hubListState = rememberLazyListState()
                    MediaFeedScaffold(
                        modifier = Modifier.fillMaxSize(),
                        listState = hubListState,
                        bottomContentPadding = 148.dp
                    ) {
                        item(key = "title_hdr") {
                            LibraryTitleHeader(
                                title = "Your Library",
                                subtitle = if (isCloudActive) {
                                    "Personal collection & synced tracks"
                                } else {
                                    "Local tracks, downloads & folders"
                                }
                            )
                        }

                        item(key = "search_and_pills") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LibrarySearchAndSortRow(
                                    query = searchQuery,
                                    placeholder = "Find in your library...",
                                    currentSort = sortOption,
                                    onQueryChange = { searchQuery = it },
                                    onSortChange = { sortOption = it }
                                )

                                LibraryFilterPillsRow(
                                    filters = listOf("All", "Liked", "Downloads", "Folders", "Playlists", "Albums"),
                                    selectedFilter = selectedFilter,
                                    onSelect = { cat ->
                                        selectedFilter = cat
                                        when (cat) {
                                            "Liked" -> subView = LibrarySubView.FAVORITES
                                            "Downloads" -> subView = LibrarySubView.DOWNLOADED
                                            "Folders" -> subView = LibrarySubView.FOLDERS
                                            "Playlists" -> subView = LibrarySubView.PLAYLISTS
                                            "Albums" -> subView = LibrarySubView.ALBUMS
                                            else -> subView = LibrarySubView.HUB
                                        }
                                    }
                                )
                            }
                        }

                        if (!allFilesGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            item(key = "storage_perm_banner") {
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .clickable(onClick = onRequestAllFilesPermission),
                                    shape = RoundedCornerShape(18.dp),
                                    color = UnboundSurfaceContainerHigh,
                                    border = BorderStroke(1.dp, Color(0xFFFFC594).copy(alpha = 0.45f))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Unlock All Device Folders",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OnSurface
                                            )
                                            Text(
                                                text = "Scan Downloads, WhatsApp, Telegram & SD Card",
                                                fontSize = 11.5.sp,
                                                color = OnSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            shape = RoundedCornerShape(12.dp),
                                            color = Color(0xFFFFC594)
                                        ) {
                                            Text(
                                                text = "Unlock",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF2E1500),
                                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "sync_banner") {
                            LibrarySyncStatusBanner(
                                isCloudConnected = isCloudActive,
                                cloudTracksCount = syncedYouTubeTracks.size,
                                offlineTracksCount = tracks.size,
                                onRefresh = onRefresh,
                                onConnectClick = onConnectClick
                            )
                        }

                        item(key = "hero_and_voice") {
                            val hasMusicTracks = effectiveMusicTracks.isNotEmpty()
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                LibraryHeroAllSongsCard(
                                    title = "All Tracks",
                                    subtitle = if (hasMusicTracks) {
                                        "${effectiveMusicTracks.size} tracks • Play or shuffle"
                                    } else {
                                        "No tracks indexed yet"
                                    },
                                    artistInitials = artistInitials,
                                    actionLabel = if (hasMusicTracks) "Play All" else "Scan",
                                    actionIcon = Icons.Default.Shuffle,
                                    heroIcon = Icons.Default.MusicNote,
                                    isEmpty = !hasMusicTracks,
                                    onCardClick = { subView = LibrarySubView.TRACKS },
                                    onActionClick = {
                                        if (hasMusicTracks) {
                                            val shuffled = effectiveMusicTracks.shuffled()
                                            onTrackSelect(shuffled.first(), shuffled)
                                        } else {
                                            onRefresh()
                                        }
                                    }
                                )

                                if (effectiveMixedAudioTracks.isNotEmpty()) {
                                    VoiceRecordingsIsolationBanner(
                                        count = effectiveMixedAudioTracks.size,
                                        onClick = { subView = LibrarySubView.AUDIOS }
                                    )
                                }
                            }
                        }

                        item(key = "bento_grid") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LibraryBentoQuickTile(
                                        title = "Liked Tracks",
                                        subtitle = "${combinedFavorites.size} favorite songs",
                                        icon = Icons.Default.Favorite,
                                        iconTint = Color(0xFFFFB4AB),
                                        iconBg = Color(0xFF93000A).copy(alpha = 0.45f),
                                        trailingIcon = Icons.AutoMirrored.Filled.ArrowForward,
                                        onClick = { subView = LibrarySubView.FAVORITES },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LibraryBentoQuickTile(
                                        title = "Downloaded",
                                        subtitle = "${effectiveDownloaded.size} offline songs",
                                        icon = Icons.Default.DownloadForOffline,
                                        iconTint = Color(0xFF05E777),
                                        iconBg = Color(0xFF05E777).copy(alpha = 0.2f),
                                        trailingBadge = "Saved",
                                        trailingBadgeColor = Color(0xFF05E777),
                                        onClick = { subView = LibrarySubView.DOWNLOADED },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    LibraryBentoQuickTile(
                                        title = "Download Folders",
                                        subtitle = "Downloads, Chats & SD Card",
                                        icon = Icons.Default.Folder,
                                        iconTint = OnSurfaceVariant,
                                        iconBg = UnboundSurfaceContainerHigh,
                                        trailingBadge = "${audioFolders.size} Folders",
                                        trailingBadgeColor = OnSurfaceVariant,
                                        onClick = { subView = LibrarySubView.FOLDERS },
                                        modifier = Modifier.weight(1f)
                                    )
                                    LibraryBentoQuickTile(
                                        title = "Recent History",
                                        subtitle = "${recentlyPlayedTracks.size} recent tracks",
                                        icon = Icons.Default.History,
                                        iconTint = UnboundPrimary,
                                        iconBg = UnboundSurfaceContainerHigh,
                                        trailingBadge = if (recentlyPlayedTracks.isNotEmpty()) "Active" else null,
                                        trailingBadgeColor = UnboundPrimary,
                                        onClick = { subView = LibrarySubView.RECENT_PLAYED },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (albumCompletions.isNotEmpty()) {
                            items(albumCompletions, key = { "album_comp_${it.albumTitle}" }) { status ->
                                SmartCompleteAlbumCard(
                                    status = status,
                                    onDownloadRemaining = { onDownloadRemaining(status) }
                                )
                            }
                        }

                        item(key = "playlists_carousel") {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Your Playlists",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = OnSurface
                                        )
                                        Text(
                                            text = "Handcrafted and smart collections",
                                            fontSize = 12.sp,
                                            color = OnSurfaceVariant.copy(alpha = 0.78f)
                                        )
                                    }

                                    Surface(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .clickable {
                                                newPlaylistTitle = ""
                                                showCreatePlaylistDialog = true
                                            },
                                        shape = RoundedCornerShape(10.dp),
                                        color = UnboundSurfaceContainer
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "New Playlist",
                                                tint = UnboundPrimary,
                                                modifier = Modifier.size(17.dp)
                                            )
                                            Text(
                                                text = "New",
                                                fontSize = 13.5.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = UnboundPrimary
                                            )
                                        }
                                    }
                                }

                                if (allPlaylists.isEmpty()) {
                                    UnboundEmptyStateCard(
                                        title = "No playlists yet",
                                        subtitle = "Create a playlist to organize your local and synced tracks.",
                                        icon = Icons.Default.MusicNote,
                                        actionLabel = "Create Playlist",
                                        onAction = {
                                            newPlaylistTitle = ""
                                            showCreatePlaylistDialog = true
                                        },
                                        compact = true
                                    )
                                } else {
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        items(allPlaylists, key = { it.id }) { pl ->
                                            BrutalistPlaylistTile(
                                                playlist = pl,
                                                onClick = { onPlaylistClick(pl) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "recently_added_hdr") {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (searchQuery.isNotBlank()) "Matching Tracks" else "Recently Added",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurface
                                )
                                Text(
                                    text = "See all",
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = UnboundPrimary,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .clickable { subView = LibrarySubView.TRACKS }
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }

                        if (filteredRecentTracks.isEmpty()) {
                            item(key = "recent_empty") {
                                UnboundEmptyStateCard(
                                    title = if (searchQuery.isNotBlank()) "No matching tracks" else "No tracks indexed yet",
                                    subtitle = if (searchQuery.isNotBlank()) {
                                        "No tracks in your library match \"$searchQuery\""
                                    } else {
                                        "Tap Scan Storage to discover audio files on this device."
                                    },
                                    actionLabel = if (searchQuery.isBlank()) "Scan Storage" else null,
                                    onAction = if (searchQuery.isBlank()) onRefresh else null,
                                    compact = true
                                )
                            }
                        } else {
                            val displayed = if (searchQuery.isNotBlank()) filteredRecentTracks else filteredRecentTracks.take(12)
                            items(displayed, key = { "lib_track_${it.id.ifBlank { it.title }}_${it.durationMs}" }) { track ->
                                val isCurrent = track.id == currentTrackId && track.id.isNotBlank()
                                val isFav = combinedFavorites.any { it.id == track.id && it.id.isNotBlank() }
                                RecentlyAddedTrackRow(
                                    track = track,
                                    isCurrentPlaying = isCurrent && isPlaying,
                                    isFavoriteTrack = isFav,
                                    onClick = { onTrackSelect(track, displayed) },
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    onPlayNext = { onPlayNext(track) },
                                    onAddToQueue = { onAddToQueue(track) },
                                    onAddToPlaylist = { onAddToPlaylist(track) },
                                    onOpenRingtoneCutter = { onOpenRingtoneCutter(track) },
                                    onDeleteTrack = { onDeleteDownload(track.id) },
                                    onIdentifyTrack = { onIdentifyTrack(track) }
                                )
                            }
                        }
                    }
                }

                LibrarySubView.TRACKS -> {
                    LibraryTracksListView(
                        title = "All Tracks",
                        subtitle = "${effectiveMusicTracks.size} tracks on this device",
                        tracks = effectiveMusicTracks,
                        onBack = { subView = LibrarySubView.HUB; selectedFilter = "All" },
                        onTrackSelect = onTrackSelect,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.AUDIOS -> {
                    LibraryTracksListView(
                        title = "Voice & Recordings",
                        subtitle = "${effectiveMixedAudioTracks.size} voice notes, recordings & chat audios",
                        tracks = effectiveMixedAudioTracks,
                        onBack = { subView = LibrarySubView.HUB; selectedFilter = "All" },
                        onTrackSelect = onTrackSelect,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.FOLDERS -> {
                    LibraryFoldersListView(
                        folders = audioFolders,
                        onBack = { subView = LibrarySubView.HUB; selectedFilter = "All" },
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
                        onTrackSelect = onTrackSelect,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.FAVORITES -> {
                    LibraryTracksListView(
                        title = "Liked Tracks",
                        subtitle = "${combinedFavorites.size} loved tracks",
                        tracks = combinedFavorites,
                        onBack = { subView = LibrarySubView.HUB; selectedFilter = "All" },
                        onTrackSelect = onTrackSelect,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.RECENT_PLAYED -> {
                    LibraryTracksListView(
                        title = "Recent History",
                        subtitle = "${recentlyPlayedTracks.size} songs in playback history",
                        tracks = recentlyPlayedTracks,
                        onBack = { subView = LibrarySubView.HUB; selectedFilter = "All" },
                        onTrackSelect = onTrackSelect,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.DOWNLOADED -> {
                    LibraryTracksListView(
                        title = "Downloaded Tracks",
                        subtitle = "${effectiveDownloaded.size} downloaded songs ready offline",
                        tracks = effectiveDownloaded,
                        onBack = { subView = LibrarySubView.HUB; selectedFilter = "All" },
                        onTrackSelect = onTrackSelect,
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.ALBUMS -> {
                    LibraryPlaylistsGridView(
                        title = "Albums",
                        subtitle = "${albumicPlaylists.size} album collections",
                        playlists = albumicPlaylists,
                        onBack = { subView = LibrarySubView.HUB; selectedFilter = "All" },
                        onPlaylistClick = onPlaylistClick
                    )
                }

                LibrarySubView.PLAYLISTS -> {
                    LibraryPlaylistsGridView(
                        title = "Your Playlists",
                        subtitle = "${allPlaylists.size} custom & smart playlists",
                        playlists = allPlaylists,
                        onBack = { subView = LibrarySubView.HUB; selectedFilter = "All" },
                        onPlaylistClick = onPlaylistClick
                    )
                }
            }
        }

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
                                unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
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
