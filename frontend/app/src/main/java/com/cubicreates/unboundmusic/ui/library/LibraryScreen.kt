/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: LibraryScreen.kt
 * Purpose: Production coordinator LibraryScreen routing to SignedInLibraryScreen (cloud-synced library)
 *          or GuestLibraryScreen (local offline storage & YouTube connect prompt).
 * Subsystem: Personal Library / Dual-Mode Coordinator
 */

package com.cubicreates.unboundmusic.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.ui.components.TrackItem

@Composable
fun LibraryScreen(
    modifier: Modifier = Modifier,
    isYouTubeConnected: Boolean = false,
    savedGB: Double = 0.0,
    downloadsCount: Int = 0,
    whatsappCount: Int = 0,
    telegramCount: Int = 0,
    youtubeCount: Int = 0,
    tracks: List<TrackItem> = emptyList(),
    musicTracks: List<TrackItem> = emptyList(),
    mixedAudioTracks: List<TrackItem> = emptyList(),
    syncedYouTubeTracks: List<TrackItem> = emptyList(),
    favoriteTracks: List<TrackItem> = emptyList(),
    recentlyPlayedTracks: List<TrackItem> = emptyList(),
    downloadedTracks: List<TrackItem> = emptyList(),
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onConnectClick: () -> Unit = onProfileClick,
    onSourceClick: (String) -> Unit = {},
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    downloadTasks: Map<String, DownloadTaskDto> = emptyMap(),
    onStartDownload: (TrackItem) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onDeleteDownload: (String) -> Unit = {},
    onOpenDownloadsHub: () -> Unit = {},
    customPlaylists: List<CustomPlaylist> = emptyList(),
    artistPlaylists: List<CustomPlaylist> = emptyList(),
    albumicPlaylists: List<CustomPlaylist> = emptyList(),
    albumCompletions: List<com.cubicreates.unboundmusic.data.AlbumCompletionStatus> = emptyList(),
    onDownloadRemaining: (com.cubicreates.unboundmusic.data.AlbumCompletionStatus) -> Unit = {},
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
    onShufflePlayAll: () -> Unit = {},
    onIdentifyTrack: (TrackItem) -> Unit = {},
    onBatchIdentify: () -> Unit = {},
    allFilesGranted: Boolean = true,
    onRequestAllFilesPermission: () -> Unit = {}
) {
    // Dual-Mode Routing:
    // Mode 1: Logged-in YouTube user -> SignedInLibraryScreen (Cloud sync + merged favorites)
    // Mode 2: Guest / Offline user -> GuestLibraryScreen (Local storage hub + Connect CTA)
    val isCloudActive = isYouTubeConnected || syncedYouTubeTracks.isNotEmpty()

    if (isCloudActive) {
        SignedInLibraryScreen(
            modifier = modifier,
            savedGB = savedGB,
            downloadsCount = downloadsCount,
            whatsappCount = whatsappCount,
            telegramCount = telegramCount,
            youtubeCount = youtubeCount,
            tracks = tracks,
            musicTracks = musicTracks,
            mixedAudioTracks = mixedAudioTracks,
            syncedYouTubeTracks = syncedYouTubeTracks,
            favoriteTracks = favoriteTracks,
            recentlyPlayedTracks = recentlyPlayedTracks,
            downloadedTracks = downloadedTracks,
            onMenuClick = onMenuClick,
            onProfileClick = onProfileClick,
            onSourceClick = onSourceClick,
            onTrackSelect = onTrackSelect,
            onRefresh = onRefresh,
            downloadTasks = downloadTasks,
            onStartDownload = onStartDownload,
            onCancelDownload = onCancelDownload,
            onDeleteDownload = onDeleteDownload,
            onOpenDownloadsHub = onOpenDownloadsHub,
            customPlaylists = customPlaylists,
            artistPlaylists = artistPlaylists,
            albumicPlaylists = albumicPlaylists,
            albumCompletions = albumCompletions,
            onDownloadRemaining = onDownloadRemaining,
            onCreatePlaylist = onCreatePlaylist,
            onPlaylistClick = onPlaylistClick,
            onAddToPlaylist = onAddToPlaylist,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onStartRadio = onStartRadio,
            onOpenEqualizer = onOpenEqualizer,
            onOpenRingtoneCutter = onOpenRingtoneCutter,
            onToggleFavorite = onToggleFavorite,
            onStartShazam = onStartShazam,
            onShufflePlayAll = onShufflePlayAll,
            onIdentifyTrack = onIdentifyTrack,
            onBatchIdentify = onBatchIdentify,
            allFilesGranted = allFilesGranted,
            onRequestAllFilesPermission = onRequestAllFilesPermission
        )
    } else {
        GuestLibraryScreen(
            modifier = modifier,
            savedGB = savedGB,
            downloadsCount = downloadsCount,
            whatsappCount = whatsappCount,
            telegramCount = telegramCount,
            tracks = tracks,
            musicTracks = musicTracks,
            mixedAudioTracks = mixedAudioTracks,
            favoriteTracks = favoriteTracks,
            recentlyPlayedTracks = recentlyPlayedTracks,
            downloadedTracks = downloadedTracks,
            onMenuClick = onMenuClick,
            onProfileClick = onProfileClick,
            onConnectClick = onConnectClick,
            onSourceClick = onSourceClick,
            onTrackSelect = onTrackSelect,
            onRefresh = onRefresh,
            downloadTasks = downloadTasks,
            onStartDownload = onStartDownload,
            onCancelDownload = onCancelDownload,
            onDeleteDownload = onDeleteDownload,
            onOpenDownloadsHub = onOpenDownloadsHub,
            customPlaylists = customPlaylists,
            artistPlaylists = artistPlaylists,
            albumicPlaylists = albumicPlaylists,
            albumCompletions = albumCompletions,
            onDownloadRemaining = onDownloadRemaining,
            onCreatePlaylist = onCreatePlaylist,
            onPlaylistClick = onPlaylistClick,
            onAddToPlaylist = onAddToPlaylist,
            onPlayNext = onPlayNext,
            onAddToQueue = onAddToQueue,
            onStartRadio = onStartRadio,
            onOpenEqualizer = onOpenEqualizer,
            onOpenRingtoneCutter = onOpenRingtoneCutter,
            onToggleFavorite = onToggleFavorite,
            onStartShazam = onStartShazam,
            onShufflePlayAll = onShufflePlayAll,
            onIdentifyTrack = onIdentifyTrack,
            onBatchIdentify = onBatchIdentify,
            allFilesGranted = allFilesGranted,
            onRequestAllFilesPermission = onRequestAllFilesPermission
        )
    }
}
