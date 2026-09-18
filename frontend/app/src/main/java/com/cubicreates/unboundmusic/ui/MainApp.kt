/*
 * Package: com.cubicreates.unboundmusic.ui
 * File: MainApp.kt
 * Purpose: Root composable shell with tab navigation, floating mini-player, full-screen Now Playing,
 *          and seamless modal routing for 10-Band Equalizer, AutoEq, Settings, Artist, and Unbound Recap.
 * Subsystem: Navigation Shell
 */

package com.cubicreates.unboundmusic.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.graphics.Color
import com.cubicreates.unboundmusic.ui.album.AlbumPlaylistScreen
import com.cubicreates.unboundmusic.ui.artist.ArtistScreen
import com.cubicreates.unboundmusic.ui.downloads.DownloadsScreen
import com.cubicreates.unboundmusic.ui.playlist.CustomPlaylistScreen
import com.cubicreates.unboundmusic.ui.playlist.AddToPlaylistSheet
import com.cubicreates.unboundmusic.ui.components.FloatingMiniPlayer
import com.cubicreates.unboundmusic.ui.components.NavigationTab
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundBottomNavBar
import com.cubicreates.unboundmusic.ui.components.UnboundTopAppBar
import com.cubicreates.unboundmusic.ui.equalizer.AutoEqPickerDialog
import com.cubicreates.unboundmusic.ui.equalizer.EqualizerScreen
import com.cubicreates.unboundmusic.ui.home.HomeScreen
import com.cubicreates.unboundmusic.ui.library.LibraryScreen
import com.cubicreates.unboundmusic.ui.player.NowPlayingScreen
import com.cubicreates.unboundmusic.ui.recap.RecapScreen
import com.cubicreates.unboundmusic.ui.search.SearchScreen
import com.cubicreates.unboundmusic.data.DownloadUiStatus
import com.cubicreates.unboundmusic.data.GenreItemDto
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.account.YouTubeDeviceAuthSheet
import com.cubicreates.unboundmusic.ui.account.YouTubeLoginSheet
import com.cubicreates.unboundmusic.ui.genre.GenreDetailScreen
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.cubicreates.unboundmusic.ui.settings.SettingsScreen
import com.cubicreates.unboundmusic.ui.player.SleepTimerSheet
import com.cubicreates.unboundmusic.ui.tools.RingtoneCutterScreen
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.viewmodel.MainViewModel

/**
 * Root composable hosting the navigation shell, floating mini-player, and full screen modals.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(NavigationTab.HOME) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    // Modal navigation states
    var showSettings by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showAutoEqPicker by remember { mutableStateOf(false) }
    var showRecap by remember { mutableStateOf(false) }
    var showYouTubeLoginSheet by remember { mutableStateOf(false) }
    var showYouTubeDeviceAuthSheet by remember { mutableStateOf(false) }
    var showDownloadsScreen by remember { mutableStateOf(false) }
    var viewingArtist by remember { mutableStateOf<String?>(null) }
    var viewingGenre by remember { mutableStateOf<GenreItemDto?>(null) }
    var ringtoneCutterTrack by remember { mutableStateOf<TrackItem?>(null) }

    val deviceAuthData by viewModel.deviceAuthData.collectAsStateWithLifecycle()
    val isStartingDeviceAuth by viewModel.isStartingDeviceAuth.collectAsStateWithLifecycle()
    val isPollingDeviceAuth by viewModel.isPollingDeviceAuth.collectAsStateWithLifecycle()
    val deviceAuthError by viewModel.deviceAuthError.collectAsStateWithLifecycle()

    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsStateWithLifecycle()
    val isSyncingAccount by viewModel.isSyncingAccount.collectAsStateWithLifecycle()
    val userMixes by viewModel.userMixes.collectAsStateWithLifecycle()

    LaunchedEffect(isYouTubeConnected) {
        if (isYouTubeConnected) {
            showYouTubeDeviceAuthSheet = false
            showYouTubeLoginSheet = false
        }
    }

    val launchYouTubeAuth: () -> Unit = {
        showYouTubeLoginSheet = true
    }
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsStateWithLifecycle()
    val syncedYouTubeTracks by viewModel.syncedYouTubeTracks.collectAsStateWithLifecycle()
    val smartShelves by viewModel.smartShelves.collectAsStateWithLifecycle()
    val isLoadingMoreTracks by viewModel.isLoadingMoreTracks.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val autoDownloadLikedSongs by viewModel.autoDownloadLikedSongs.collectAsStateWithLifecycle()
    val skipSilenceEnabled by viewModel.skipSilenceEnabled.collectAsStateWithLifecycle()
    val normalizeVolumeEnabled by viewModel.normalizeVolumeEnabled.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val libraryTracks by viewModel.libraryTracks.collectAsStateWithLifecycle()
    val savedGB by viewModel.savedGB.collectAsStateWithLifecycle()
    val downloadsCount by viewModel.downloadsCount.collectAsStateWithLifecycle()
    val whatsappCount by viewModel.whatsappCount.collectAsStateWithLifecycle()
    val telegramCount by viewModel.telegramCount.collectAsStateWithLifecycle()
    val youtubeCount by viewModel.youtubeCount.collectAsStateWithLifecycle()
    val lyricsLines by viewModel.lyricsLines.collectAsStateWithLifecycle()
    val lyricsSource by viewModel.lyricsSource.collectAsStateWithLifecycle()
    val romanizationMode by viewModel.romanizationMode.collectAsStateWithLifecycle()
    val lyricsTimingOffsetMs by viewModel.lyricsTimingOffsetMs.collectAsStateWithLifecycle()
    val isInstrumental by viewModel.isInstrumental.collectAsStateWithLifecycle()
    val canvasArtUrl by viewModel.canvasArtUrl.collectAsStateWithLifecycle()
    val chartTracks by viewModel.chartTracks.collectAsStateWithLifecycle()
    val regionalCharts by viewModel.regionalCharts.collectAsStateWithLifecycle()
    val searchCategory by viewModel.searchCategory.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val daypartingState by viewModel.daypartingState.collectAsStateWithLifecycle()
    val vibeSearchResult by viewModel.vibeSearchResult.collectAsStateWithLifecycle()
    val equalizerCurve by viewModel.equalizerCurve.collectAsStateWithLifecycle()
    val autoEqResults by viewModel.autoEqResults.collectAsStateWithLifecycle()
    val isSearchingAutoEq by viewModel.isSearchingAutoEq.collectAsStateWithLifecycle()
    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val bassBoostStrength by viewModel.bassBoostStrength.collectAsStateWithLifecycle()
    val virtualizerStrength by viewModel.virtualizerStrength.collectAsStateWithLifecycle()
    val loudnessGainMb by viewModel.loudnessGainMb.collectAsStateWithLifecycle()
    val customEqPresets by viewModel.customEqPresets.collectAsStateWithLifecycle()
    val cachePurgeStatus by viewModel.cachePurgeStatus.collectAsStateWithLifecycle()
    val genreSections by viewModel.genreSections.collectAsStateWithLifecycle()
    val activeGenreShelves by viewModel.activeGenreShelves.collectAsStateWithLifecycle()
    val isLoadingGenreDetail by viewModel.isLoadingGenreDetail.collectAsStateWithLifecycle()
    val selectedGenreTitle by viewModel.selectedGenreTitle.collectAsStateWithLifecycle()
    val artistProfile by viewModel.artistProfile.collectAsStateWithLifecycle()
    val isLoadingArtist by viewModel.isLoadingArtist.collectAsStateWithLifecycle()
    val recapData by viewModel.recapData.collectAsStateWithLifecycle()

    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val downloadedTrackIds by viewModel.downloadedTrackIds.collectAsStateWithLifecycle()
    val downloadSpeedBps by viewModel.downloadSpeedBps.collectAsStateWithLifecycle()
    val downloadedMusicTracks by viewModel.downloadedMusicTracks.collectAsStateWithLifecycle()
    val cacheSizeMB by viewModel.cacheSizeMB.collectAsStateWithLifecycle()
    val downloadsSizeMB by viewModel.downloadsSizeMB.collectAsStateWithLifecycle()
    val freeStorageGB by viewModel.freeStorageGB.collectAsStateWithLifecycle()
    val sleepTimerState by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val skippedSkitNotice by viewModel.skippedSkitNotice.collectAsStateWithLifecycle()
    val albumPlaylistData by viewModel.albumPlaylistData.collectAsStateWithLifecycle()
    val rydVotes by viewModel.rydVotes.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val activeCustomPlaylist by viewModel.activeCustomPlaylist.collectAsStateWithLifecycle()
    val trackToAddToPlaylist by viewModel.trackToAddToPlaylist.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentlyPlayedTracks by viewModel.recentlyPlayedTracks.collectAsStateWithLifecycle()
    val reverbPreset by viewModel.reverbPreset.collectAsStateWithLifecycle()
    val isListeningShazam by viewModel.isListeningShazam.collectAsStateWithLifecycle()

    val sponsorBlockEnabled by viewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val selectedHomeMood by viewModel.selectedHomeMood.collectAsStateWithLifecycle()
    val moodTracks by viewModel.moodTracks.collectAsStateWithLifecycle()
    val isMoodLoading by viewModel.isMoodLoading.collectAsStateWithLifecycle()

    val streamingQuality by viewModel.streamingQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    var showSleepTimerFromSettings by remember { mutableStateOf(false) }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                val json = com.cubicreates.unboundmusic.data.BackupRestoreManager.exportBackupJson(context)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                com.cubicreates.unboundmusic.util.UnboundToast.show(context, "Backup exported successfully!")
            } catch (e: Exception) {
                com.cubicreates.unboundmusic.util.UnboundToast.show(context, "Export failed: ${e.message}")
            }
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            try {
                val json = context.contentResolver.openInputStream(uri)?.use { inp ->
                    inp.bufferedReader().readText()
                } ?: ""
                val res = com.cubicreates.unboundmusic.data.BackupRestoreManager.restoreBackupJson(context, json)
                if (res.success) {
                    viewModel.reloadAfterRestore()
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        context,
                        "Restored ${res.playlistsRestored} playlists, ${res.favoritesRestored} favorites!"
                    )
                } else {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(context, "Restore failed: ${res.errorMessage}")
                }
            } catch (e: Exception) {
                com.cubicreates.unboundmusic.util.UnboundToast.show(context, "Restore error: ${e.message}")
            }
        }
    }

    val activeDownloadsCount = remember(downloadTasks) {
        downloadTasks.values.count { it.status == "DOWNLOADING" || it.status == "TAGGING" || it.status == "QUEUED" || it.status == "PAUSED" }
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // Standard Tab Navigation Content inside Responsive Scaffold
        androidx.compose.material3.Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = UnboundBackground,
            topBar = {
                UnboundTopAppBar(
                    currentTab = selectedTab,
                    userAvatarUrl = userAvatarUrl,
                    accountName = accountName,
                    isLoggedIn = isYouTubeConnected,
                    activeDownloadsCount = activeDownloadsCount,
                    onMenuClick = { showSettings = true },
                    onProfileClick = { showSettings = true },
                    onDownloadsClick = { showDownloadsScreen = true }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UnboundBackground)
                ) {
                    if (currentTrack.title.isNotBlank() && (playbackState.isPlaying || playbackState.currentPositionMs > 0) && !isPlayerExpanded) {
                            FloatingMiniPlayer(
                                title = currentTrack.title,
                                artist = currentTrack.artist,
                                coverUrl = currentTrack.coverUrl,
                                isPlaying = playbackState.isPlaying,
                                isFavorite = isFavorite,
                                onPlayPauseToggle = { viewModel.togglePlayPause() },
                                onFavoriteToggle = { viewModel.toggleFavorite() },
                                onPlayerClick = { isPlayerExpanded = true }
                            )

                            Spacer(modifier = Modifier.height(4.dp))
                        }

                        UnboundBottomNavBar(
                            currentTab = selectedTab,
                            userAvatarUrl = userAvatarUrl,
                            accountName = accountName,
                            isLoggedIn = isYouTubeConnected,
                            isProfileActive = showSettings,
                            onTabSelected = { tab ->
                                selectedTab = tab
                            },
                            onProfileClick = {
                                showSettings = true
                            }
                        )
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(
                        targetState = selectedTab,
                        animationSpec = tween(200),
                        label = "screen_crossfade"
                    ) { tab ->
                        when (tab) {
                            NavigationTab.HOME -> {
                                HomeScreen(
                                    tracks = if (regionalCharts.isNotEmpty()) regionalCharts else chartTracks,
                                    syncedYouTubeTracks = syncedYouTubeTracks,
                                    userMixes = userMixes,
                                    smartShelves = smartShelves,
                                    daypartingState = daypartingState,
                                    genreSections = genreSections,
                                    userAvatarUrl = userAvatarUrl,
                                    accountName = accountName,
                                    isYouTubeConnected = isYouTubeConnected,
                                    isSyncing = isSyncingAccount,
                                    isLoadingMore = isLoadingMoreTracks,
                                    onLoadMore = { viewModel.loadMorePersonalizedTracks() },
                                    onSyncClick = { viewModel.resyncYouTubeAccount() },
                                    onConnectClick = { showYouTubeLoginSheet = true },
                                    onCapsuleSelect = { capsule ->
                                        viewModel.playMoodCapsule(capsule)
                                        isPlayerExpanded = true
                                    },
                                    onMoodSelect = { mood ->
                                        viewModel.openCuratedCollection(mood.title, mood.imageUrl)
                                    },
                                    onGenreSelect = { genre ->
                                        viewingGenre = genre
                                        viewModel.loadGenreDetail(genre.params, genre.title)
                                    },
                                    onMixClick = { mix ->
                                        viewModel.playCuratedMix(mix)
                                        isPlayerExpanded = true
                                    },
                                    onAlbumPlaylistClick = { id, title, coverUrl ->
                                        viewModel.openAlbumPlaylist(id, title, coverUrl)
                                    },
                                    onTrackSelect = { track, queue ->
                                        viewModel.playTrackWithQueue(track, queue)
                                        isPlayerExpanded = true
                                    },
                                    onProfileClick = { showSettings = true },
                                    onMenuClick = {
                                        viewModel.loadRecap()
                                        showRecap = true
                                    },
                                    currentTrackId = currentTrack.id,
                                    isPlaying = playbackState.isPlaying,
                                    onPlayNext = { track -> viewModel.playNextBatch(listOf(track)) },
                                    onAddToQueue = { track -> viewModel.addToQueueBatch(listOf(track)) },
                                    onDownload = { track -> viewModel.downloadBatch(listOf(track)) },
                                    onStartRadio = { track ->
                                        viewModel.startRadio(track)
                                        isPlayerExpanded = true
                                    },
                                    selectedMood = selectedHomeMood,
                                    moodTracks = moodTracks,
                                    isMoodLoading = isMoodLoading,
                                    onMoodFilterSelect = { viewModel.selectHomeMood(it) },
                                    isVibeLoading = vibeSearchResult is VibeSearchUiState.Loading,
                                    onVibeSubmit = { prompt ->
                                        viewModel.playVibePrompt(prompt)
                                    }
                                )
                            }
                            NavigationTab.SEARCH -> {
                                SearchScreen(
                                    searchResults = searchResults,
                                    isSearching = isSearching,
                                    vibeState = vibeSearchResult,
                                    selectedCategory = searchCategory,
                                    chartTracks = if (regionalCharts.isNotEmpty()) regionalCharts else chartTracks,
                                    searchSuggestions = searchSuggestions,
                                    searchHistory = searchHistory,
                                    currentTrackId = currentTrack.id,
                                    isPlaying = playbackState.isPlaying,
                                    onCategorySelected = { viewModel.setSearchCategory(it) },
                                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                                    onSearchSubmit = { viewModel.submitSearch(it) },
                                    onSearchHistoryItemRemoved = { viewModel.removeSearchHistoryItem(it) },
                                    onSearchHistoryCleared = { viewModel.clearSearchHistory() },
                                    onVibeSubmit = { viewModel.submitVibeQuery(it) },
                                    onListenToSurroundings = { viewModel.startAmbientShazamRecognition() },
                                    onVibeTagClick = { tag -> viewModel.submitVibeQuery(tag.removePrefix("#")) },
                                    onGenreCardClick = { genre -> viewModel.openCuratedCollection(genre) },
                                    onTrackSelect = { track, queue ->
                                        viewModel.playTrackWithQueue(track, queue)
                                        isPlayerExpanded = true
                                    },
                                    onAlbumClick = { id, title, coverUrl ->
                                        viewModel.openAlbumPlaylist(id, title, coverUrl)
                                    },
                                    onArtistClick = { artistName ->
                                        viewingArtist = artistName
                                        viewModel.loadArtistProfile(artistName)
                                    },
                                    onPlayNextBatch = { tracks -> viewModel.playNextBatch(tracks) },
                                    onAddToQueueBatch = { tracks -> viewModel.addToQueueBatch(tracks) },
                                    onDownloadBatch = { tracks -> viewModel.downloadBatch(tracks) },
                                    onPlayNextSingle = { track -> viewModel.playNextBatch(listOf(track)) },
                                    onAddToQueueSingle = { track -> viewModel.addToQueueBatch(listOf(track)) },
                                    onStartRadioSingle = { track ->
                                        viewModel.startRadio(track)
                                        isPlayerExpanded = true
                                    },
                                    onDownloadSingle = { track -> viewModel.downloadBatch(listOf(track)) },
                                    onAddToPlaylistSingle = { track -> viewModel.showAddToPlaylist(track) },
                                    isListeningAudio = isListeningShazam
                                )
                            }
                            NavigationTab.LIBRARY -> {
                                LibraryScreen(
                                    savedGB = savedGB,
                                    downloadsCount = downloadsCount,
                                    whatsappCount = whatsappCount,
                                    telegramCount = telegramCount,
                                    youtubeCount = if (isYouTubeConnected) syncedYouTubeTracks.size else youtubeCount,
                                    tracks = libraryTracks,
                                    syncedYouTubeTracks = syncedYouTubeTracks,
                                    downloadTasks = downloadTasks,
                                    onStartDownload = { viewModel.startTrackDownload(it) },
                                    onCancelDownload = { viewModel.cancelTrackDownload(it) },
                                    onDeleteDownload = { viewModel.deleteTrackDownload(it) },
                                    onOpenDownloadsHub = { showDownloadsScreen = true },
                                    customPlaylists = customPlaylists,
                                    onCreatePlaylist = { title -> viewModel.createCustomPlaylist(title) },
                                    onPlaylistClick = { playlist -> viewModel.openCustomPlaylist(playlist) },
                                    onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) },
                                    onPlayNext = { track -> viewModel.playNext(track) },
                                    onAddToQueue = { track -> viewModel.addToQueue(track) },
                                    onStartRadio = { track ->
                                        viewModel.startRadio(track)
                                        isPlayerExpanded = true
                                    },
                                    downloadedTracks = downloadedMusicTracks,
                                    onTrackSelect = { track, queue ->
                                        viewModel.playTrackWithQueue(track, queue)
                                        isPlayerExpanded = true
                                    },
                                    onRefresh = { viewModel.refreshLibrary() },
                                    onProfileClick = { showSettings = true },
                                    favoriteTracks = favoriteTracks,
                                    recentlyPlayedTracks = recentlyPlayedTracks,
                                    onOpenEqualizer = { showEqualizer = true },
                                    onOpenRingtoneCutter = { track -> ringtoneCutterTrack = track },
                                    onToggleFavorite = { track -> viewModel.toggleTrackFavorite(track) },
                                    onStartShazam = { viewModel.startAmbientShazamRecognition() },
                                    onIdentifyTrack = { track -> viewModel.identifyTrack(track) },
                                    onBatchIdentify = { viewModel.batchIdentifyUnknownTracks() }
                                )
                            }
                        }
                    }
                }
            }

        // Modal 1: Settings Screen
        if (showSettings) {
            SettingsScreen(
                onClose = { showSettings = false },
                onEqualizerClick = { showEqualizer = true },
                onAutoEqClick = { showAutoEqPicker = true },
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
                streamingQuality = streamingQuality,
                downloadQuality = downloadQuality,
                onAutoDownloadLikedSongsChange = { viewModel.setAutoDownloadLikedSongs(it) },
                onSkipSilenceChange = { viewModel.setSkipSilenceEnabled(it) },
                onNormalizeVolumeChange = { viewModel.setNormalizeVolumeEnabled(it) },
                onSponsorBlockChange = { viewModel.setSponsorBlockEnabled(it) },
                onStreamingQualityChange = { viewModel.setStreamingQuality(it) },
                onDownloadQualityChange = { viewModel.setDownloadQuality(it) },
                onOpenDownloadsHub = { showDownloadsScreen = true },
                onSleepTimerClick = { showSleepTimerFromSettings = true },
                onExportBackupClick = { exportBackupLauncher.launch("unbound_backup_${System.currentTimeMillis()}.json") },
                onRestoreBackupClick = { restoreBackupLauncher.launch("application/json") }
            )
        }

        // Modal: Sleep Timer from Settings
        if (showSleepTimerFromSettings) {
            SleepTimerSheet(
                timerState = sleepTimerState,
                onStartTimer = { minutes, endOfSong -> viewModel.startSleepTimer(minutes, endOfSong) },
                onCancelTimer = { viewModel.cancelSleepTimer() },
                onDismiss = { showSleepTimerFromSettings = false }
            )
        }

        // Modal 4: Unbound Recap (Wrapped) Screen
        if (showRecap) {
            RecapScreen(
                data = recapData,
                onClose = { showRecap = false }
            )
        }

        // Modal 5: Artist Profile Screen
        if (viewingArtist != null) {
            artistProfile?.let { prof ->
                ArtistScreen(
                    profile = prof,
                    isLoading = isLoadingArtist,
                    onBack = { viewingArtist = null },
                    onTrackSelect = { track ->
                        viewModel.playTrack(track)
                        isPlayerExpanded = true
                    },
                    onPlayAll = {
                        if (prof.topTracks.isNotEmpty()) {
                            viewModel.playTrack(prof.topTracks[0])
                            isPlayerExpanded = true
                        }
                    },
                    onArtistClick = { nextArtist ->
                        viewingArtist = nextArtist
                        viewModel.loadArtistProfile(nextArtist)
                    }
                )
            }
        }

        // Modal 6: Genre Detail Screen
        if (viewingGenre != null) {
            GenreDetailScreen(
                genreTitle = selectedGenreTitle,
                shelves = activeGenreShelves,
                isLoading = isLoadingGenreDetail,
                onBack = { viewingGenre = null },
                onPlaylistClick = { playlistItem ->
                    viewModel.playPlaylistItem(playlistItem)
                    isPlayerExpanded = true
                },
                onRetry = {
                    viewingGenre?.let {
                        viewModel.loadGenreDetail(it.params, it.title)
                    }
                }
            )
        }

        // Modal 7: Album & Playlist Detail Screen
        albumPlaylistData?.let { albumData ->
            AlbumPlaylistScreen(
                data = albumData,
                onBack = { viewModel.closeAlbumPlaylist() },
                onTrackSelect = { track ->
                    viewModel.playTrackWithQueue(track, albumData.tracks)
                    isPlayerExpanded = true
                },
                onPlayAll = {
                    if (albumData.tracks.isNotEmpty()) {
                        viewModel.playTrackWithQueue(albumData.tracks.first(), albumData.tracks)
                        isPlayerExpanded = true
                    }
                },
                onShuffleAll = {
                    if (albumData.tracks.isNotEmpty()) {
                        val shuffled = albumData.tracks.shuffled()
                        viewModel.playTrackWithQueue(shuffled.first(), shuffled)
                        isPlayerExpanded = true
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
                isPlaying = playbackState.isPlaying,
                onPlayNext = { track -> viewModel.playNext(track) },
                onAddToQueue = { track -> viewModel.addToQueue(track) },
                onStartRadio = { track ->
                    viewModel.startRadio(track)
                    isPlayerExpanded = true
                },
                onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) }
            )
        }

        // Modal 8: Centralized Downloads & Storage Management Screen
        if (showDownloadsScreen) {
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
                onBack = { showDownloadsScreen = false },
                onPauseDownload = { viewModel.pauseDownload(it) },
                onResumeDownload = { viewModel.resumeDownload(it) },
                onCancelDownload = { viewModel.cancelTrackDownload(it) },
                onRetryDownload = { viewModel.retryDownload(it) },
                onDeleteDownload = { id, title -> viewModel.deleteTrackDownload(id, title) },
                onTrackSelect = { track ->
                    viewModel.playTrack(track)
                    isPlayerExpanded = true
                },
                onPlayAllDownloaded = { tracks ->
                    if (tracks.isNotEmpty()) {
                        viewModel.playTrackWithQueue(tracks.first(), tracks)
                        isPlayerExpanded = true
                    }
                },
                onPlayNext = { track -> viewModel.playNextBatch(listOf(track)) },
                onAddToQueue = { track -> viewModel.addToQueueBatch(listOf(track)) },
                onStartRadio = { track ->
                    viewModel.startRadio(track)
                    isPlayerExpanded = true
                },
                onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) },
                onClearCache = { viewModel.clearCacheAndStorage() },
                onExportToStorage = { viewModel.exportDownloadsToPublicStorage() }
            )
        }

        // Full Screen Immersive Now Playing Overlay (Pops in front of tabs, albums, artists, genres)
        if (isPlayerExpanded) {
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
                queue = playbackState.queue,
                playbackMode = playbackState.playbackMode,
                onCollapse = {
                    isPlayerExpanded = false
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
                onEqualizerClick = { showEqualizer = true },
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
                onSetPlaybackSpeedAndPitch = { speed, pitch -> viewModel.setPlaybackSpeed(speed, pitch) }
            )
        }

        // Modal 1.0: Zero-Typing YouTube Device Activation Sheet (Method 1)
        if (showYouTubeDeviceAuthSheet) {
            YouTubeDeviceAuthSheet(
                deviceData = deviceAuthData,
                isStarting = isStartingDeviceAuth,
                isPolling = isPollingDeviceAuth,
                errorMessage = deviceAuthError,
                onDismiss = {
                    showYouTubeDeviceAuthSheet = false
                    viewModel.cancelDeviceAuth()
                },
                onRetry = {
                    launchYouTubeAuth()
                },
                onSwitchToWebView = {
                    showYouTubeDeviceAuthSheet = false
                    viewModel.cancelDeviceAuth()
                    showYouTubeLoginSheet = true
                }
            )
        }

        // Modal 1.1: YouTube In-App WebView Login Sheet (Manual Fallback)
        if (showYouTubeLoginSheet) {
            YouTubeLoginSheet(
                onDismiss = { showYouTubeLoginSheet = false },
                onCookieExtracted = { cookie ->
                    viewModel.syncYouTubeAccount(cookie)
                }
            )
        }

        // Modal 2: Pro Equalizer & Sound Effects Screen
        if (showEqualizer) {
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
                onAutoEqClick = { showAutoEqPicker = true },
                onClose = { showEqualizer = false }
            )
        }

        // Modal 2.5: Ringtone Cutter & Waveform Trimmer
        ringtoneCutterTrack?.let { track ->
            RingtoneCutterScreen(
                track = track,
                onClose = { ringtoneCutterTrack = null }
            )
        }

        // Modal 3: AutoEq Headphone Picker Dialog (Opens from Equalizer)
        if (showAutoEqPicker) {
            AutoEqPickerDialog(
                searchResults = autoEqResults,
                isSearching = isSearchingAutoEq,
                onSearchQueryChanged = { viewModel.searchAutoEqPresets(it) },
                onPresetSelected = { viewModel.applyAutoEqPreset(it) },
                onDismiss = { showAutoEqPicker = false }
            )
        }

        // Modal 9: Custom User Playlist Detail Screen
        activeCustomPlaylist?.let { customPlaylist ->
            CustomPlaylistScreen(
                playlist = customPlaylist,
                onBack = { viewModel.closeCustomPlaylist() },
                onTrackSelect = { track ->
                    viewModel.playTrackWithQueue(track, customPlaylist.tracks)
                    isPlayerExpanded = true
                },
                onPlayAll = {
                    if (customPlaylist.tracks.isNotEmpty()) {
                        viewModel.playTrackWithQueue(customPlaylist.tracks.first(), customPlaylist.tracks)
                        isPlayerExpanded = true
                    }
                },
                onShuffleAll = {
                    if (customPlaylist.tracks.isNotEmpty()) {
                        val shuffled = customPlaylist.tracks.shuffled()
                        viewModel.playTrackWithQueue(shuffled.first(), shuffled)
                        isPlayerExpanded = true
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
                    isPlayerExpanded = true
                },
                onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) },
                onStartDownload = { track -> viewModel.startTrackDownload(track) },
                downloadedTrackIds = downloadedTrackIds,
                currentTrackId = currentTrack.id,
                isPlaying = playbackState.isPlaying
            )
        }

        // Modal 10: Add to Playlist Bottom Sheet
        trackToAddToPlaylist?.let { track ->
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
                        Color(0xFFB45309) // Amber/warning
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
}
