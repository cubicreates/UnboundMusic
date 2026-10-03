/*
 * Package: com.cubicreates.unboundmusic.ui.components
 * File: MainOverlayHost.kt
 * Purpose: Centralized host for full-screen modals, sheets, dialogs, and playback overlays.
 * Subsystem: Navigation & Overlay Host
 */

package com.cubicreates.unboundmusic.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.GenreItemDto
import com.cubicreates.unboundmusic.ui.account.YouTubeDeviceAuthSheet
import com.cubicreates.unboundmusic.ui.account.YouTubeLoginSheet
import com.cubicreates.unboundmusic.ui.album.AlbumPlaylistScreen
import com.cubicreates.unboundmusic.ui.artist.ArtistScreen
import com.cubicreates.unboundmusic.ui.dialogs.UpdateAvailableDialog
import com.cubicreates.unboundmusic.ui.downloads.DownloadsScreen
import com.cubicreates.unboundmusic.ui.equalizer.AutoEqPickerDialog
import com.cubicreates.unboundmusic.ui.equalizer.EqualizerScreen
import com.cubicreates.unboundmusic.ui.genre.GenreDetailScreen
import com.cubicreates.unboundmusic.ui.player.NowPlayingScreen
import com.cubicreates.unboundmusic.ui.player.SleepTimerSheet
import com.cubicreates.unboundmusic.ui.playlist.AddToPlaylistSheet
import com.cubicreates.unboundmusic.ui.playlist.CustomPlaylistScreen
import com.cubicreates.unboundmusic.ui.recap.RecapScreen
import com.cubicreates.unboundmusic.ui.settings.SettingsScreen
import com.cubicreates.unboundmusic.ui.tools.RingtoneCutterScreen
import com.cubicreates.unboundmusic.viewmodel.MainViewModel

/**
 * State holder tracking modal presentation across the app shell.
 */
class MainOverlayState(
    isPlayerExpanded: Boolean = false,
    showEqualizer: Boolean = false,
    showAutoEqPicker: Boolean = false,
    showRecap: Boolean = false,
    showYouTubeLoginSheet: Boolean = false,
    showYouTubeDeviceAuthSheet: Boolean = false,
    showDownloadsScreen: Boolean = false,
    showSettings: Boolean = false,
    viewingArtist: String? = null,
    viewingGenre: GenreItemDto? = null,
    ringtoneCutterTrack: TrackItem? = null,
    showSleepTimerFromSettings: Boolean = false
) {
    var isPlayerExpanded by mutableStateOf(isPlayerExpanded)
    var showEqualizer by mutableStateOf(showEqualizer)
    var showAutoEqPicker by mutableStateOf(showAutoEqPicker)
    var showRecap by mutableStateOf(showRecap)
    var showYouTubeLoginSheet by mutableStateOf(showYouTubeLoginSheet)
    var showYouTubeDeviceAuthSheet by mutableStateOf(showYouTubeDeviceAuthSheet)
    var showDownloadsScreen by mutableStateOf(showDownloadsScreen)
    var showSettings by mutableStateOf(showSettings)
    var viewingArtist by mutableStateOf(viewingArtist)
    var viewingGenre by mutableStateOf(viewingGenre)
    var ringtoneCutterTrack by mutableStateOf(ringtoneCutterTrack)
    var showSleepTimerFromSettings by mutableStateOf(showSleepTimerFromSettings)
}

@Composable
fun rememberMainOverlayState(): MainOverlayState = remember { MainOverlayState() }

/**
 * Host component managing the presentation of all full-screen modals, sheets,
 * playback overlays, and diagnostic HUD banners in Unbound Music.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainOverlayHost(
    overlayState: MainOverlayState,
    viewModel: MainViewModel,
    launchYouTubeAuth: () -> Unit,
    onExportBackupClick: () -> Unit = {},
    onRestoreBackupClick: () -> Unit = {}
) {
    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val cachePurgeStatus by viewModel.cachePurgeStatus.collectAsStateWithLifecycle()
    val autoDownloadLikedSongs by viewModel.autoDownloadLikedSongs.collectAsStateWithLifecycle()
    val skipSilenceEnabled by viewModel.skipSilenceEnabled.collectAsStateWithLifecycle()
    val normalizeVolumeEnabled by viewModel.normalizeVolumeEnabled.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by viewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val streamingQuality by viewModel.streamingQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val isLockscreenWallpaperEnabled by viewModel.isLockscreenWallpaperEnabled.collectAsStateWithLifecycle()
    val isVlcAutoScanEnabled by viewModel.isVlcAutoScanEnabled.collectAsStateWithLifecycle()
    val deviceAuthData by viewModel.deviceAuthData.collectAsStateWithLifecycle()
    val isStartingDeviceAuth by viewModel.isStartingDeviceAuth.collectAsStateWithLifecycle()
    val isPollingDeviceAuth by viewModel.isPollingDeviceAuth.collectAsStateWithLifecycle()
    val deviceAuthError by viewModel.deviceAuthError.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val lyricsLines by viewModel.lyricsLines.collectAsStateWithLifecycle()
    val lyricsSource by viewModel.lyricsSource.collectAsStateWithLifecycle()
    val romanizationMode by viewModel.romanizationMode.collectAsStateWithLifecycle()
    val lyricsTimingOffsetMs by viewModel.lyricsTimingOffsetMs.collectAsStateWithLifecycle()
    val isInstrumental by viewModel.isInstrumental.collectAsStateWithLifecycle()
    val canvasArtUrl by viewModel.canvasArtUrl.collectAsStateWithLifecycle()
    val skippedSkitNotice by viewModel.skippedSkitNotice.collectAsStateWithLifecycle()
    val fallbackStatus by viewModel.fallbackStatus.collectAsStateWithLifecycle()
    val rydVotes by viewModel.rydVotes.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()

    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloadSpeedBps by viewModel.downloadSpeedBps.collectAsStateWithLifecycle()
    val downloadedMusicTracks by viewModel.downloadedMusicTracks.collectAsStateWithLifecycle()
    val libraryTracks by viewModel.libraryTracks.collectAsStateWithLifecycle()
    val cacheSizeMB by viewModel.cacheSizeMB.collectAsStateWithLifecycle()
    val downloadsSizeMB by viewModel.downloadsSizeMB.collectAsStateWithLifecycle()
    val freeStorageGB by viewModel.freeStorageGB.collectAsStateWithLifecycle()

    val equalizerCurve by viewModel.equalizerCurve.collectAsStateWithLifecycle()
    val bassBoostStrength by viewModel.bassBoostStrength.collectAsStateWithLifecycle()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsStateWithLifecycle()
    val loudnessGainMb by viewModel.loudnessGainMb.collectAsStateWithLifecycle()
    val reverbPreset by viewModel.reverbPreset.collectAsStateWithLifecycle()
    val customEqPresets by viewModel.customEqPresets.collectAsStateWithLifecycle()
    val autoEqResults by viewModel.autoEqResults.collectAsStateWithLifecycle()
    val isSearchingAutoEq by viewModel.isSearchingAutoEq.collectAsStateWithLifecycle()

    val recapData by viewModel.recapData.collectAsStateWithLifecycle()
    val artistProfile by viewModel.artistProfile.collectAsStateWithLifecycle()
    val isLoadingArtist by viewModel.isLoadingArtist.collectAsStateWithLifecycle()

    val selectedGenreTitle by viewModel.selectedGenreTitle.collectAsStateWithLifecycle()
    val activeGenreShelves by viewModel.activeGenreShelves.collectAsStateWithLifecycle()
    val isLoadingGenreDetail by viewModel.isLoadingGenreDetail.collectAsStateWithLifecycle()

    val albumPlaylistData by viewModel.albumPlaylistData.collectAsStateWithLifecycle()
    val activeCustomPlaylist by viewModel.activeCustomPlaylist.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val trackToAddToPlaylist by viewModel.trackToAddToPlaylist.collectAsStateWithLifecycle()
    val availableUpdate by viewModel.availableUpdate.collectAsStateWithLifecycle()
    val currentQueue by viewModel.currentQueue.collectAsStateWithLifecycle()

    LaunchedEffect(isYouTubeConnected) {
        if (isYouTubeConnected) {
            overlayState.showYouTubeDeviceAuthSheet = false
            overlayState.showYouTubeLoginSheet = false
        }
    }

    val currentTask = downloadTasks[currentTrack.id]
        ?: downloadTasks.values.find { it.title.isNotBlank() && it.title.equals(currentTrack.title, ignoreCase = true) }
    val isTrackDownloaded = currentTrack.id in downloadedTrackIds
        || currentTrack.source.contains("Downloads", ignoreCase = true)
        || currentTask?.status == "COMPLETED"
        || downloadedTrackIds.any { id -> downloadTasks[id]?.title?.equals(currentTrack.title, ignoreCase = true) == true }
    val currentDownloadStatus = when {
        isTrackDownloaded -> DownloadUiStatus.DOWNLOADED
        currentTask?.status == "DOWNLOADING" || currentTask?.status == "TAGGING" || currentTask?.status == "QUEUED" -> DownloadUiStatus.DOWNLOADING
        else -> DownloadUiStatus.NOT_DOWNLOADED
    }
    val currentDownloadProgress = currentTask?.progress ?: 0.0

    // Modal: Settings Screen (Launched via top bar profile avatar)
    if (overlayState.showSettings) {
        BackHandler(enabled = true) { overlayState.showSettings = false }
        SettingsScreen(
            onClose = { overlayState.showSettings = false },
            onEqualizerClick = { overlayState.showEqualizer = true },
            onAutoEqClick = { overlayState.showAutoEqPicker = true },
            isYouTubeConnected = isYouTubeConnected,
            accountName = accountName,
            userAvatarUrl = userAvatarUrl,
            currentTheme = selectedTheme,
            cachePurgeStatus = cachePurgeStatus,
            onThemeSelected = { viewModel.setTheme(it) },
            onYouTubeSyncClick = { launchYouTubeAuth() },
            onDisconnectYouTubeClick = { viewModel.disconnectYouTubeAccount() },
            onPurgeCacheClick = { viewModel.purgeCache() },
            onCleanStorageForUninstallClick = { viewModel.purgeUnboundStorageForUninstall() },
            autoDownloadLikedSongs = autoDownloadLikedSongs,
            skipSilenceEnabled = skipSilenceEnabled,
            normalizeVolumeEnabled = normalizeVolumeEnabled,
            sponsorBlockEnabled = sponsorBlockEnabled,
            lockscreenWallpaperEnabled = isLockscreenWallpaperEnabled,
            vlcAutoScanEnabled = isVlcAutoScanEnabled,
            streamingQuality = streamingQuality,
            downloadQuality = downloadQuality,
            onAutoDownloadLikedSongsChange = { viewModel.setAutoDownloadLikedSongs(it) },
            onSkipSilenceChange = { viewModel.setSkipSilenceEnabled(it) },
            onNormalizeVolumeChange = { viewModel.setNormalizeVolumeEnabled(it) },
            onSponsorBlockChange = { viewModel.setSponsorBlockEnabled(it) },
            onLockscreenWallpaperChange = { viewModel.setLockscreenWallpaperEnabled(it) },
            onVlcAutoScanChange = { viewModel.setVlcAutoScanEnabled(it) },
            onStreamingQualityChange = { viewModel.setStreamingQuality(it) },
            onDownloadQualityChange = { viewModel.setDownloadQuality(it) },
            onOpenDownloadsHub = { overlayState.showDownloadsScreen = true },
            onSleepTimerClick = { overlayState.showSleepTimerFromSettings = true },
            onCheckUpdateClick = { viewModel.checkForAppUpdates(manual = true) },
            onExportBackupClick = onExportBackupClick,
            onRestoreBackupClick = onRestoreBackupClick
        )
    }

    // Modal: Sleep Timer from Settings
    if (overlayState.showSleepTimerFromSettings) {
        BackHandler(enabled = true) { overlayState.showSleepTimerFromSettings = false }
        SleepTimerSheet(
            timerState = sleepTimerState,
            onStartTimer = { minutes, endOfSong -> viewModel.startSleepTimer(minutes, endOfSong) },
            onCancelTimer = { viewModel.cancelSleepTimer() },
            onDismiss = { overlayState.showSleepTimerFromSettings = false }
        )
    }

    // Modal: Unbound Recap (Wrapped) Screen
    if (overlayState.showRecap) {
        BackHandler(enabled = true) { overlayState.showRecap = false }
        RecapScreen(
            data = recapData,
            onClose = { overlayState.showRecap = false }
        )
    }

    // Modal: Artist Profile Screen
    if (overlayState.viewingArtist != null) {
        BackHandler(enabled = true) { overlayState.viewingArtist = null }
        artistProfile?.let { prof ->
            ArtistScreen(
                profile = prof,
                isLoading = isLoadingArtist,
                onBack = { overlayState.viewingArtist = null },
                onTrackSelect = { track ->
                    viewModel.playTrack(track)
                    overlayState.isPlayerExpanded = true
                },
                onPlayAll = {
                    if (prof.topTracks.isNotEmpty()) {
                        viewModel.playTrack(prof.topTracks[0])
                        overlayState.isPlayerExpanded = true
                    }
                },
                onArtistClick = { nextArtist ->
                    overlayState.viewingArtist = nextArtist
                    viewModel.loadArtistProfile(nextArtist)
                }
            )
        }
    }

    // Modal: Genre Detail Screen
    if (overlayState.viewingGenre != null) {
        BackHandler(enabled = true) { overlayState.viewingGenre = null }
        GenreDetailScreen(
            genreTitle = selectedGenreTitle,
            shelves = activeGenreShelves,
            isLoading = isLoadingGenreDetail,
            onBack = { overlayState.viewingGenre = null },
            onPlaylistClick = { playlistItem ->
                viewModel.playPlaylistItem(playlistItem)
                overlayState.isPlayerExpanded = true
            },
            onRetry = {
                overlayState.viewingGenre?.let {
                    viewModel.loadGenreDetail(it.params, it.title)
                }
            }
        )
    }

    // Modal: Album & Playlist Detail Screen
    albumPlaylistData?.let { albumData ->
        BackHandler(enabled = true) { viewModel.closeAlbumPlaylist() }
        AlbumPlaylistScreen(
            data = albumData,
            onBack = { viewModel.closeAlbumPlaylist() },
            onTrackSelect = { track ->
                viewModel.playTrackWithQueue(track, albumData.tracks)
                overlayState.isPlayerExpanded = true
            },
            onPlayAll = {
                if (albumData.tracks.isNotEmpty()) {
                    viewModel.playTrackWithQueue(albumData.tracks.first(), albumData.tracks)
                    overlayState.isPlayerExpanded = true
                }
            },
            onShuffleAll = {
                if (albumData.tracks.isNotEmpty()) {
                    val shuffled = albumData.tracks.shuffled()
                    viewModel.playTrackWithQueue(shuffled.first(), shuffled)
                    overlayState.isPlayerExpanded = true
                }
            },
            onStartDownload = { track ->
                viewModel.startTrackDownload(track)
            },
            onDownloadAll = {
                albumData.tracks.forEach { track ->
                    viewModel.startTrackDownload(track)
                }
            },
            downloadedTrackIds = downloadedTrackIds,
            currentTrackId = currentTrack.id,
            isPlaying = isPlaying,
            onPlayNext = { track -> viewModel.playNext(track) },
            onAddToQueue = { track -> viewModel.addToQueue(track) },
            onStartRadio = { track ->
                viewModel.startRadio(track)
                overlayState.isPlayerExpanded = true
            },
            onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) }
        )
    }

    // Modal: Centralized Downloads & Storage Management Screen
    if (overlayState.showDownloadsScreen) {
        BackHandler(enabled = true) { overlayState.showDownloadsScreen = false }
        val effectiveDownloadedTracks = if (downloadedMusicTracks.isNotEmpty()) {
            downloadedMusicTracks
        } else {
            libraryTracks.filter { it.source.contains("Downloads", ignoreCase = true) || it.source.contains("Unbound", ignoreCase = true) }
        }

        DownloadsScreen(
            downloadTasks = downloadTasks,
            downloadedTracks = effectiveDownloadedTracks,
            downloadSpeedBps = downloadSpeedBps,
            cacheSizeMB = cacheSizeMB,
            downloadsSizeMB = downloadsSizeMB,
            freeStorageGB = freeStorageGB,
            onBack = { overlayState.showDownloadsScreen = false },
            onPauseDownload = { viewModel.pauseDownload(it) },
            onResumeDownload = { viewModel.resumeDownload(it) },
            onCancelDownload = { viewModel.cancelTrackDownload(it) },
            onRetryDownload = { viewModel.retryDownload(it) },
            onDeleteDownload = { id, title -> viewModel.deleteTrackDownload(id, title) },
            onTrackSelect = { track ->
                viewModel.playTrack(track)
                overlayState.isPlayerExpanded = true
            },
            onPlayAllDownloaded = { tracks ->
                if (tracks.isNotEmpty()) {
                    viewModel.playTrackWithQueue(tracks.first(), tracks)
                    overlayState.isPlayerExpanded = true
                }
            },
            onPlayNext = { track -> viewModel.playNextBatch(listOf(track)) },
            onAddToQueue = { track -> viewModel.addToQueueBatch(listOf(track)) },
            onStartRadio = { track ->
                viewModel.startRadio(track)
                overlayState.isPlayerExpanded = true
            },
            onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) },
            onClearCache = { viewModel.clearCacheAndStorage() },
            onExportToStorage = { viewModel.exportDownloadsToPublicStorage() }
        )
    }

    // Full Screen Immersive Now Playing Overlay
    if (overlayState.isPlayerExpanded) {
        val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
        NowPlayingScreen(
            track = currentTrack,
            isPlaying = playbackState.isPlaying,
            isFavorite = isFavorite,
            progress = playbackState.progress,
            currentPositionMs = playbackState.currentPositionMs,
            formattedPosition = playbackState.formattedPosition,
            formattedRemaining = playbackState.formattedRemaining,
            lyricsLines = lyricsLines,
            lyricsSource = lyricsSource,
            romanizationMode = romanizationMode,
            timingOffsetMs = lyricsTimingOffsetMs,
            isInstrumental = isInstrumental,
            onRomanizationModeChange = { viewModel.setRomanizationMode(it) },
            onTimingOffsetChange = { viewModel.setLyricsTimingOffsetMs(it) },
            canvasArtUrl = canvasArtUrl,
            queue = if (currentQueue.isNotEmpty()) currentQueue else playbackState.queue,
            playbackMode = playbackState.playbackMode,
            onCollapse = {
                overlayState.isPlayerExpanded = false
            },
            onPlayPauseToggle = { viewModel.togglePlayPause() },
            onFavoriteToggle = { viewModel.toggleFavorite() },
            onPreviousTrack = { viewModel.prevTrack() },
            onNextTrack = { viewModel.nextTrack() },
            onSeek = { viewModel.seekTo(it) },
            onSeekPositionMs = { viewModel.seekToPositionMs(it) },
            onCyclePlaybackMode = { viewModel.cyclePlaybackMode() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onCycleRepeatMode = { viewModel.cycleRepeatMode() },
            onEqualizerClick = { overlayState.showEqualizer = true },
            onQueueTrackSelect = { index -> viewModel.playQueueTrack(index) },
            downloadStatus = currentDownloadStatus,
            downloadProgress = currentDownloadProgress,
            onStartDownload = { viewModel.startTrackDownload(currentTrack) },
            onCancelDownload = { viewModel.cancelTrackDownload(currentTask?.videoId ?: currentTrack.id, currentTrack.title) },
            onDeleteDownload = { viewModel.deleteTrackDownload(currentTask?.videoId ?: currentTrack.id, currentTrack.title) },
            onMoveQueueItem = { from, to -> viewModel.moveQueueItem(from, to) },
            onRemoveQueueItem = { index -> viewModel.removeQueueItem(index) },
            sleepTimerState = sleepTimerState,
            onStartSleepTimer = { minutes, endOfSong -> viewModel.startSleepTimer(minutes, endOfSong) },
            onCancelSleepTimer = { viewModel.cancelSleepTimer() },
            skippedSkitNotice = skippedSkitNotice,
            onUndoSkip = { viewModel.undoSkitSkip() },
            onDismissSkipNotice = { viewModel.dismissSkitNotice() },
            rydData = rydVotes,
            onRefreshRydVotes = { viewModel.refreshRydVotes() },
            onStartRadio = {
                if (currentTrack.title.isNotBlank()) {
                    viewModel.startRadio(currentTrack)
                }
            },
            playbackSpeed = playbackState.playbackSpeed,
            playbackPitch = playbackState.playbackPitch,
            onSetPlaybackSpeedAndPitch = { speed, pitch -> viewModel.setPlaybackSpeed(speed, pitch) },
            fallbackStatus = fallbackStatus
        )
    }

    // Modal: Zero-Typing YouTube Device Activation Sheet (Method 1)
    if (overlayState.showYouTubeDeviceAuthSheet) {
        YouTubeDeviceAuthSheet(
            deviceData = deviceAuthData,
            isStarting = isStartingDeviceAuth,
            isPolling = isPollingDeviceAuth,
            errorMessage = deviceAuthError,
            onDismiss = {
                overlayState.showYouTubeDeviceAuthSheet = false
                viewModel.cancelDeviceAuth()
            },
            onRetry = {
                launchYouTubeAuth()
            },
            onSwitchToWebView = {
                overlayState.showYouTubeDeviceAuthSheet = false
                viewModel.cancelDeviceAuth()
                overlayState.showYouTubeLoginSheet = true
            }
        )
    }

    // Modal: YouTube In-App WebView Login Sheet (Manual Fallback)
    if (overlayState.showYouTubeLoginSheet) {
        YouTubeLoginSheet(
            onDismiss = { overlayState.showYouTubeLoginSheet = false },
            onCookieExtracted = { cookie ->
                viewModel.syncYouTubeAccount(cookie)
            }
        )
    }

    // Modal: Pro Equalizer & Sound Effects Screen
    if (overlayState.showEqualizer) {
        BackHandler(enabled = true) { overlayState.showEqualizer = false }
        EqualizerScreen(
            initialCurve = equalizerCurve,
            initialBassBoost = bassBoostStrength,
            initialVirtualizer = virtualizerStrength,
            initialLoudness = loudnessGainMb,
            initialReverbPreset = reverbPreset,
            customPresets = customEqPresets,
            onCurveChanged = { viewModel.setEqualizerCurve(it) },
            onBassBoostChanged = { viewModel.setBassBoost(it) },
            onVirtualizerChanged = { viewModel.setVirtualizer(it) },
            onLoudnessChanged = { viewModel.setLoudness(it) },
            onReverbPresetChanged = { viewModel.setReverbPreset(it) },
            onSaveCustomPreset = { name, curve, bb, v, l ->
                viewModel.saveCustomEqPreset(name, curve, bb, v, l)
            },
            onAutoEqClick = { overlayState.showAutoEqPicker = true },
            onClose = { overlayState.showEqualizer = false }
        )
    }

    // Modal: Ringtone Cutter & Waveform Trimmer
    overlayState.ringtoneCutterTrack?.let { track ->
        BackHandler(enabled = true) { overlayState.ringtoneCutterTrack = null }
        RingtoneCutterScreen(
            track = track,
            onClose = { overlayState.ringtoneCutterTrack = null }
        )
    }

    // Modal: AutoEq Headphone Picker Dialog (Opens from Equalizer)
    if (overlayState.showAutoEqPicker) {
        BackHandler(enabled = true) { overlayState.showAutoEqPicker = false }
        AutoEqPickerDialog(
            searchResults = autoEqResults,
            isSearching = isSearchingAutoEq,
            onSearchQueryChanged = { viewModel.searchAutoEqPresets(it) },
            onPresetSelected = { viewModel.applyAutoEqPreset(it) },
            onDismiss = { overlayState.showAutoEqPicker = false }
        )
    }

    // Modal: Custom User Playlist Detail Screen
    activeCustomPlaylist?.let { customPlaylist ->
        BackHandler(enabled = true) { viewModel.closeCustomPlaylist() }
        CustomPlaylistScreen(
            playlist = customPlaylist,
            onBack = { viewModel.closeCustomPlaylist() },
            onTrackSelect = { track ->
                viewModel.playTrackWithQueue(track, customPlaylist.tracks)
                overlayState.isPlayerExpanded = true
            },
            onPlayAll = {
                if (customPlaylist.tracks.isNotEmpty()) {
                    viewModel.playTrackWithQueue(customPlaylist.tracks.first(), customPlaylist.tracks)
                    overlayState.isPlayerExpanded = true
                }
            },
            onShuffleAll = {
                if (customPlaylist.tracks.isNotEmpty()) {
                    val shuffled = customPlaylist.tracks.shuffled()
                    viewModel.playTrackWithQueue(shuffled.first(), shuffled)
                    overlayState.isPlayerExpanded = true
                }
            },
            onDownloadAll = {
                customPlaylist.tracks.forEach { track ->
                    viewModel.startTrackDownload(track)
                }
            },
            onUpdateDetails = { title, desc, cover ->
                viewModel.updateCustomPlaylist(customPlaylist.id, title, desc, cover)
            },
            onDeletePlaylist = {
                viewModel.deleteCustomPlaylist(customPlaylist.id)
            },
            onRemoveTrack = { trackId ->
                viewModel.removeTrackFromCustomPlaylist(customPlaylist.id, trackId)
            },
            onMoveTrack = { from, to ->
                viewModel.moveTrackInCustomPlaylist(customPlaylist.id, from, to)
            },
            onPlayNext = { track -> viewModel.playNext(track) },
            onAddToQueue = { track -> viewModel.addToQueue(track) },
            onStartRadio = { track ->
                viewModel.startRadio(track)
                overlayState.isPlayerExpanded = true
            },
            onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) },
            onStartDownload = { track -> viewModel.startTrackDownload(track) },
            downloadedTrackIds = downloadedTrackIds,
            currentTrackId = currentTrack.id,
            isPlaying = isPlaying
        )
    }

    // Modal: Add to Playlist Bottom Sheet
    trackToAddToPlaylist?.let { track ->
        BackHandler(enabled = true) { viewModel.hideAddToPlaylist() }
        AddToPlaylistSheet(
            track = track,
            playlists = customPlaylists,
            onDismiss = { viewModel.hideAddToPlaylist() },
            onSelectPlaylist = { playlistId ->
                viewModel.addTrackToCustomPlaylist(playlistId, track)
                viewModel.hideAddToPlaylist()
            },
            onCreateNewPlaylistWithTrack = { title ->
                val newPlaylist = viewModel.createCustomPlaylist(title)
                viewModel.addTrackToCustomPlaylist(newPlaylist.id, track)
                viewModel.hideAddToPlaylist()
            }
        )
    }

    // Modal: App Update Available Dialog
    availableUpdate?.let { info ->
        UpdateAvailableDialog(
            updateInfo = info,
            onDismiss = { viewModel.dismissUpdateDialog() }
        )
    }

    // Persistent Diagnostic Toast HUD Banner Overlay
    val diagnosticMessage by com.cubicreates.unboundmusic.util.UnboundToast.lastDiagnostic.collectAsStateWithLifecycle()
    diagnosticMessage?.let { msg ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp, start = 12.dp, end = 12.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                color = if (msg.contains("Error", ignoreCase = true) ||
                    msg.contains("Fail", ignoreCase = true) ||
                    msg.contains("FATAL", ignoreCase = true) ||
                    msg.contains("Exception", ignoreCase = true)) {
                    Color(0xFF8B0000)
                } else if (msg.contains("Fallback", ignoreCase = true) || msg.contains("Retrying", ignoreCase = true)) {
                    Color(0xFFB45309)
                } else {
                    Color(0xFF1E293B)
                },
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 10.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = if (msg.contains("Error", ignoreCase = true) || msg.contains("Fail", ignoreCase = true)) {
                                "DIAGNOSTIC ERROR REPORT (FULL DETAILS)"
                            } else {
                                "DIAGNOSTIC STATUS"
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        SelectionContainer {
                            Text(
                                text = msg,
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = { com.cubicreates.unboundmusic.util.UnboundToast.clear() },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}
