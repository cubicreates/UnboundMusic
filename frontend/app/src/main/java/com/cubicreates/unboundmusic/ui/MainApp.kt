/*
 * Package: com.cubicreates.unboundmusic.ui
 * File: MainApp.kt
 * Purpose: Root composable shell with responsive tab navigation, floating mini-player,
 *          and delegation to MainOverlayHost for modal playback, dialogs, and sheets.
 * Subsystem: Navigation Shell
 */

package com.cubicreates.unboundmusic.ui

import android.os.Build
import android.os.Environment
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cubicreates.unboundmusic.data.HomeSection
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.FloatingMiniPlayer
import com.cubicreates.unboundmusic.ui.components.MainOverlayHost
import com.cubicreates.unboundmusic.ui.components.NavigationTab
import com.cubicreates.unboundmusic.ui.components.UnboundBottomNavBar
import com.cubicreates.unboundmusic.ui.components.UnboundTopAppBar
import com.cubicreates.unboundmusic.ui.components.rememberMainOverlayState
import com.cubicreates.unboundmusic.ui.home.HomeScreen
import com.cubicreates.unboundmusic.ui.library.LibraryScreen
import com.cubicreates.unboundmusic.ui.search.SearchScreen
import com.cubicreates.unboundmusic.ui.settings.SettingsScreen
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.viewmodel.*

/**
 * Root composable hosting the primary tab navigation shell, floating mini-player,
 * and delegating overlays to [MainOverlayHost].
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun MainApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(NavigationTab.HOME) }
    val overlayState = rememberMainOverlayState()

    val isYouTubeConnected by viewModel.isYouTubeConnected.collectAsStateWithLifecycle()
    val isSyncingAccount by viewModel.isSyncingAccount.collectAsStateWithLifecycle()
    val userMixes by viewModel.userMixes.collectAsStateWithLifecycle()
    val accountName by viewModel.accountName.collectAsStateWithLifecycle()
    val userAvatarUrl by viewModel.userAvatarUrl.collectAsStateWithLifecycle()
    val syncedYouTubeTracks by viewModel.syncedYouTubeTracks.collectAsStateWithLifecycle()
    val smartShelves by viewModel.smartShelves.collectAsStateWithLifecycle()
    val isLoadingMoreTracks by viewModel.isLoadingMoreTracks.collectAsStateWithLifecycle()

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
    val isPlaying by viewModel.isPlaying.collectAsStateWithLifecycle()
    val hasActivePlayback by viewModel.hasActivePlayback.collectAsStateWithLifecycle()
    val playbackProgress by viewModel.playbackProgress.collectAsStateWithLifecycle()
    val selectedHomeSection by viewModel.selectedHomeSection.collectAsStateWithLifecycle()
    val selectedSearchSection by viewModel.selectedSearchSection.collectAsStateWithLifecycle()
    val homeRecognitionState by viewModel.homeRecognitionState.collectAsStateWithLifecycle()
    val searchRecognitionState by viewModel.searchRecognitionState.collectAsStateWithLifecycle()
    val activeShazamSurface by viewModel.activeShazamSurface.collectAsStateWithLifecycle()
    val selectedPodcastCategory by viewModel.selectedPodcastCategory.collectAsStateWithLifecycle()
    val podcastFeaturedShows by viewModel.podcastFeaturedShows.collectAsStateWithLifecycle()
    val podcastRecentEpisodes by viewModel.podcastRecentEpisodes.collectAsStateWithLifecycle()
    val podcastTopEpisodes by viewModel.podcastTopEpisodes.collectAsStateWithLifecycle()
    val podcastGlobalEpisodes by viewModel.podcastGlobalEpisodes.collectAsStateWithLifecycle()
    val podcastRegionalEpisodes by viewModel.podcastRegionalEpisodes.collectAsStateWithLifecycle()
    val podcastSearchResults by viewModel.podcastSearchResults.collectAsStateWithLifecycle()
    val podcastSearchShows by viewModel.podcastSearchShows.collectAsStateWithLifecycle()
    val podcastSearchQuery by viewModel.podcastSearchQuery.collectAsStateWithLifecycle()
    val podcastSearchFilter by viewModel.podcastSearchFilter.collectAsStateWithLifecycle()
    val podcastSearchHistory by viewModel.podcastSearchHistory.collectAsStateWithLifecycle()
    val podcastSearchSuggestions by viewModel.podcastSearchSuggestions.collectAsStateWithLifecycle()
    val isPodcastSearching by viewModel.isPodcastSearching.collectAsStateWithLifecycle()
    val selectedPodcastShow by viewModel.selectedPodcastShow.collectAsStateWithLifecycle()
    val isPodcastLoading by viewModel.isPodcastLoading.collectAsStateWithLifecycle()
    val podcastError by viewModel.podcastError.collectAsStateWithLifecycle()
    val activePodcastTopicQuery by viewModel.activePodcastTopicQuery.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val libraryTracks by viewModel.libraryTracks.collectAsStateWithLifecycle()
    val musicTracks by viewModel.musicTracks.collectAsStateWithLifecycle()
    val mixedAudioTracks by viewModel.mixedAudioTracks.collectAsStateWithLifecycle()
    val savedGB by viewModel.savedGB.collectAsStateWithLifecycle()
    val downloadsCount by viewModel.downloadsCount.collectAsStateWithLifecycle()
    val whatsappCount by viewModel.whatsappCount.collectAsStateWithLifecycle()
    val telegramCount by viewModel.telegramCount.collectAsStateWithLifecycle()
    val youtubeCount by viewModel.youtubeCount.collectAsStateWithLifecycle()
    val chartTracks by viewModel.chartTracks.collectAsStateWithLifecycle()
    val regionalCharts by viewModel.regionalCharts.collectAsStateWithLifecycle()
    val adaptiveQuickPicks by viewModel.adaptiveQuickPicks.collectAsStateWithLifecycle()
    val homeJumpBackTracks by viewModel.homeJumpBackTracks.collectAsStateWithLifecycle()
    val homeMixes by viewModel.homeMixes.collectAsStateWithLifecycle()
    val quickPicksTitle by viewModel.quickPicksTitle.collectAsStateWithLifecycle()
    val quickPicksSubtitle by viewModel.quickPicksSubtitle.collectAsStateWithLifecycle()
    val searchCategory by viewModel.searchCategory.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val daypartingState by viewModel.daypartingState.collectAsStateWithLifecycle()
    val homeVibeState by viewModel.homeVibeState.collectAsStateWithLifecycle()
    val homeSearchState by viewModel.homeSearchState.collectAsStateWithLifecycle()
    val searchVibeState by viewModel.searchVibeState.collectAsStateWithLifecycle()
    val genreSections by viewModel.genreSections.collectAsStateWithLifecycle()

    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val downloadedMusicTracks by viewModel.downloadedMusicTracks.collectAsStateWithLifecycle()
    val customPlaylists by viewModel.customPlaylists.collectAsStateWithLifecycle()
    val artistPlaylists by viewModel.artistPlaylists.collectAsStateWithLifecycle()
    val albumicPlaylists by viewModel.albumicPlaylists.collectAsStateWithLifecycle()
    val albumCompletions by viewModel.albumCompletions.collectAsStateWithLifecycle()
    val favoriteTracks by viewModel.favoriteTracks.collectAsStateWithLifecycle()
    val recentlyPlayedTracks by viewModel.recentlyPlayedTracks.collectAsStateWithLifecycle()
    val isListeningShazam by viewModel.isListeningShazam.collectAsStateWithLifecycle()
    val recordingDurationSeconds by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()

    val selectedTheme by viewModel.selectedTheme.collectAsStateWithLifecycle()
    val cachePurgeStatus by viewModel.cachePurgeStatus.collectAsStateWithLifecycle()
    val cacheSizeMB by viewModel.cacheSizeMB.collectAsStateWithLifecycle()
    val downloadsSizeMB by viewModel.downloadsSizeMB.collectAsStateWithLifecycle()
    val autoDownloadLikedSongs by viewModel.autoDownloadLikedSongs.collectAsStateWithLifecycle()
    val skipSilenceEnabled by viewModel.skipSilenceEnabled.collectAsStateWithLifecycle()
    val normalizeVolumeEnabled by viewModel.normalizeVolumeEnabled.collectAsStateWithLifecycle()
    val sponsorBlockEnabled by viewModel.sponsorBlockEnabled.collectAsStateWithLifecycle()
    val streamingQuality by viewModel.streamingQuality.collectAsStateWithLifecycle()
    val downloadQuality by viewModel.downloadQuality.collectAsStateWithLifecycle()
    val isLockscreenWallpaperEnabled by viewModel.isLockscreenWallpaperEnabled.collectAsStateWithLifecycle()
    val isVlcAutoScanEnabled by viewModel.isVlcAutoScanEnabled.collectAsStateWithLifecycle()

    val selectedHomeMood by viewModel.selectedHomeMood.collectAsStateWithLifecycle()
    val moodTracks by viewModel.moodTracks.collectAsStateWithLifecycle()
    val isMoodLoading by viewModel.isMoodLoading.collectAsStateWithLifecycle()

    val launchYouTubeAuth: () -> Unit = {
        overlayState.showYouTubeLoginSheet = true
    }

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

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.onAudioPermissionGranted()
        } else {
            com.cubicreates.unboundmusic.util.UnboundToast.show(
                context,
                "Microphone permission is required to identify ambient music."
            )
        }
    }

    LaunchedEffect(Unit) {
        viewModel.audioPermissionRequestEvent.collect {
            audioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }

    BackHandler(enabled = overlayState.isPlayerExpanded) {
        overlayState.isPlayerExpanded = false
    }

    BackHandler(
        enabled = selectedTab == NavigationTab.HOME &&
            selectedHomeSection == HomeSection.PODCASTS &&
            !overlayState.isPlayerExpanded
    ) {
        viewModel.setHomeSection(HomeSection.MUSIC)
    }

    BackHandler(
        enabled = selectedTab == NavigationTab.SEARCH &&
            !overlayState.isPlayerExpanded &&
            (selectedPodcastShow != null || selectedSearchSection == HomeSection.PODCASTS)
    ) {
        if (selectedPodcastShow != null) {
            viewModel.closePodcastShow()
        } else {
            viewModel.setSearchSection(HomeSection.MUSIC)
        }
    }

    BackHandler(enabled = selectedTab != NavigationTab.HOME && !overlayState.isPlayerExpanded) {
        selectedTab = NavigationTab.HOME
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = UnboundBackground,
            topBar = {
                UnboundTopAppBar(
                    currentTab = selectedTab,
                    telemetrySubtitle = when {
                        selectedTab == NavigationTab.HOME && selectedHomeSection == HomeSection.PODCASTS -> "Stories & conversations"
                        selectedTab == NavigationTab.SEARCH && selectedSearchSection == HomeSection.PODCASTS -> "Explore shows & episodes"
                        selectedTab == NavigationTab.SEARCH -> "Explore sound & moods"
                        selectedTab == NavigationTab.LIBRARY -> "Your personal collection"
                        selectedTab == NavigationTab.PROFILE -> "Audio studio & account"
                        else -> "Made for your ears"
                    }
                )
            },
            bottomBar = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(UnboundBackground)
                ) {
                    if (currentTrack.title.isNotBlank() && hasActivePlayback && !overlayState.isPlayerExpanded) {
                        FloatingMiniPlayer(
                            title = currentTrack.title,
                            artist = currentTrack.artist,
                            coverUrl = currentTrack.coverUrl,
                            isPlaying = isPlaying,
                            isFavorite = isFavorite,
                            progress = playbackProgress,
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onFavoriteToggle = { viewModel.toggleFavorite() },
                            onPlayerClick = { overlayState.isPlayerExpanded = true },
                            onDismiss = { viewModel.dismissMiniPlayer() }
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    UnboundBottomNavBar(
                        currentTab = selectedTab,
                        userAvatarUrl = userAvatarUrl,
                        accountName = accountName,
                        isLoggedIn = isYouTubeConnected,
                        activeDownloadsCount = downloadTasks.values.count { it.status == "DOWNLOADING" || it.status == "TAGGING" || it.status == "QUEUED" },
                        onTabSelected = { tab -> selectedTab = tab }
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
                            val userRegionCode = remember(context) {
                                com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(context)
                            }
                            val userRegionName = remember(context) {
                                com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryDisplayName(context)
                            }
                            HomeScreen(
                                selectedSection = selectedHomeSection,
                                onSectionSelected = { viewModel.setHomeSection(it) },
                                jumpBackTracks = if (homeJumpBackTracks.isNotEmpty()) homeJumpBackTracks
                                    else if (syncedYouTubeTracks.isNotEmpty()) syncedYouTubeTracks
                                    else if (adaptiveQuickPicks.isNotEmpty()) adaptiveQuickPicks
                                    else if (regionalCharts.isNotEmpty()) regionalCharts
                                    else chartTracks,
                                homeMixes = homeMixes,
                                globalChartTracks = chartTracks,
                                regionalChartTracks = regionalCharts,
                                regionCode = userRegionCode,
                                regionName = userRegionName,
                                accountName = accountName,
                                isYouTubeConnected = isYouTubeConnected,
                                isSyncing = isSyncingAccount,
                                isLoadingMore = isLoadingMoreTracks,
                                currentTrackId = currentTrack.id,
                                isPlaying = isPlaying,
                                onLoadMore = { viewModel.loadMorePersonalizedTracks() },
                                onSyncClick = { viewModel.resyncYouTubeAccount() },
                                onConnectClick = { overlayState.showYouTubeLoginSheet = true },
                                onMixClick = { mix ->
                                    viewModel.playCuratedMix(mix)
                                    overlayState.isPlayerExpanded = true
                                },
                                onTrackSelect = { track, queue ->
                                    viewModel.playTrackWithQueue(track, queue)
                                    overlayState.isPlayerExpanded = true
                                },
                                onStartRadio = { track ->
                                    viewModel.startRadio(track)
                                    overlayState.isPlayerExpanded = true
                                },
                                vibeState = homeVibeState,
                                homeSearchState = homeSearchState,
                                onUnifiedHomeSearch = { q -> viewModel.submitUnifiedHomeSearch(q) },
                                onClearHomeResults = { viewModel.clearUnifiedHomeResults() },
                                isListeningAudio = isListeningShazam && activeShazamSurface == MainViewModel.RecognitionSurface.HOME,
                                recordingSeconds = recordingDurationSeconds,
                                recognitionState = homeRecognitionState,
                                onShazamToggle = { viewModel.startAmbientShazamRecognition(context, MainViewModel.RecognitionSurface.HOME) },
                                onSingLyricsClick = { viewModel.startHumSearch(MainViewModel.RecognitionSurface.HOME) },
                                podcastFeaturedShows = podcastFeaturedShows,
                                podcastRecentEpisodes = podcastRecentEpisodes,
                                podcastTopEpisodes = podcastTopEpisodes,
                                podcastGlobalEpisodes = podcastGlobalEpisodes,
                                podcastRegionalEpisodes = podcastRegionalEpisodes,
                                podcastSearchResults = podcastSearchResults,
                                selectedPodcastShow = selectedPodcastShow,
                                activePodcastTopicQuery = activePodcastTopicQuery,
                                isPodcastLoading = isPodcastLoading,
                                podcastError = podcastError,
                                onRefreshPodcasts = { viewModel.loadPodcastDiscovery() },
                                onPodcastTopicSubmit = { viewModel.submitPodcastTopicQuery(it) },
                                onClearPodcastTopic = { viewModel.clearPodcastTopicQuery() },
                                onPodcastVoiceSearchClick = { viewModel.startVoiceSearch(HomeSection.PODCASTS) },
                                onPodcastShowClick = { viewModel.openPodcastShow(it) },
                                onClosePodcastShow = { viewModel.closePodcastShow() },
                                onPodcastEpisodePlay = { ep ->
                                    viewModel.playPodcastEpisode(ep)
                                    overlayState.isPlayerExpanded = true
                                }
                            )
                        }
                        NavigationTab.SEARCH -> {
                            val combinedSearchCharts = remember(chartTracks, regionalCharts) {
                                (chartTracks + regionalCharts).distinctBy { it.id.ifBlank { it.title } }
                            }
                            val combinedPodcastDiscover = remember(podcastGlobalEpisodes, podcastRegionalEpisodes, podcastTopEpisodes) {
                                (podcastGlobalEpisodes + podcastRegionalEpisodes + podcastTopEpisodes)
                                    .distinctBy { it.id.ifBlank { it.title } }
                            }
                            SearchScreen(
                                selectedSection = selectedSearchSection,
                                onSectionSelected = { viewModel.setSearchSection(it) },
                                searchQuery = searchQuery,
                                searchResults = searchResults,
                                isSearching = isSearching,
                                vibeState = searchVibeState,
                                recognitionState = searchRecognitionState,
                                selectedCategory = searchCategory,
                                chartTracks = combinedSearchCharts,
                                searchHistory = searchHistory,
                                searchSuggestions = searchSuggestions,
                                currentTrackId = currentTrack.id,
                                isPlaying = isPlaying,
                                isListeningAudio = isListeningShazam && activeShazamSurface == MainViewModel.RecognitionSurface.SEARCH,
                                recordingSeconds = recordingDurationSeconds,
                                onCategorySelected = { viewModel.setSearchCategory(it) },
                                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                                onSearchSubmit = { viewModel.submitSearch(it) },
                                onRecordHistory = { viewModel.addSearchHistory(it) },
                                onClearMusicSearch = { viewModel.clearMusicSearch() },
                                onSearchHistoryItemRemoved = { viewModel.removeSearchHistoryItem(it) },
                                onSearchHistoryCleared = { viewModel.clearSearchHistory() },
                                onVibeSubmit = { viewModel.submitSearchVibeQuery(it) },
                                onClearVibe = { viewModel.clearSearchVibeQuery() },
                                onSingLyricsClick = { viewModel.startHumSearch(MainViewModel.RecognitionSurface.SEARCH) },
                                onIdentifyAudioClick = { viewModel.startAmbientShazamRecognition(context, MainViewModel.RecognitionSurface.SEARCH) },
                                onClearRecognition = { viewModel.clearLyricsRecognition(MainViewModel.RecognitionSurface.SEARCH) },
                                onTrackSelect = { track, queue ->
                                    if (queue.size > 1) {
                                        viewModel.playTrackWithQueue(track, queue)
                                    } else {
                                        viewModel.playTrack(track)
                                    }
                                    overlayState.isPlayerExpanded = true
                                },
                                onAlbumClick = { id, title, coverUrl ->
                                    viewModel.openAlbumPlaylist(id, title, coverUrl)
                                },
                                onArtistClick = { artistName ->
                                    overlayState.viewingArtist = artistName
                                    viewModel.loadArtistProfile(artistName)
                                },
                                onPlayNextSingle = { track -> viewModel.playNextBatch(listOf(track)) },
                                onAddToQueueSingle = { track -> viewModel.addToQueueBatch(listOf(track)) },
                                onStartRadioSingle = { track ->
                                    viewModel.startRadio(track)
                                    overlayState.isPlayerExpanded = true
                                },
                                onDownloadSingle = { track -> viewModel.downloadBatch(listOf(track)) },
                                onAddToPlaylistSingle = { track -> viewModel.showAddToPlaylist(track) },
                                podcastSearchQuery = podcastSearchQuery,
                                podcastSearchFilter = podcastSearchFilter,
                                podcastFeaturedShows = podcastFeaturedShows,
                                podcastTopEpisodes = combinedPodcastDiscover,
                                podcastSearchResults = podcastSearchResults,
                                podcastSearchShows = podcastSearchShows,
                                selectedPodcastShow = selectedPodcastShow,
                                podcastSearchHistory = podcastSearchHistory,
                                podcastSearchSuggestions = podcastSearchSuggestions,
                                isPodcastLoading = isPodcastLoading,
                                isPodcastSearching = isPodcastSearching,
                                podcastError = podcastError,
                                onPodcastFilterSelected = { viewModel.setPodcastSearchFilter(it) },
                                onPodcastSearchQueryChanged = { viewModel.onPodcastSearchQueryChanged(it) },
                                onPodcastSearchSubmit = { viewModel.submitPodcastSearch(it) },
                                onRecordPodcastHistory = { viewModel.addPodcastSearchHistory(it) },
                                onClearPodcastSearch = { viewModel.clearPodcastSearch() },
                                onPodcastVoiceSearchClick = { viewModel.startVoiceSearch(HomeSection.PODCASTS) },
                                onPodcastSearchHistoryItemRemoved = { viewModel.removePodcastSearchHistoryItem(it) },
                                onPodcastSearchHistoryCleared = { viewModel.clearPodcastSearchHistory() },
                                onPodcastShowClick = { viewModel.openPodcastShow(it) },
                                onClosePodcastShow = { viewModel.closePodcastShow() },
                                onPodcastEpisodePlay = { ep ->
                                    viewModel.playPodcastEpisode(ep)
                                    overlayState.isPlayerExpanded = true
                                }
                            )
                        }
                        NavigationTab.PROFILE -> {
                            SettingsScreen(
                                enableBackHandler = false,
                                onClose = { selectedTab = NavigationTab.HOME },
                                onEqualizerClick = { overlayState.showEqualizer = true },
                                onAutoEqClick = { overlayState.showAutoEqPicker = true },
                                isYouTubeConnected = isYouTubeConnected,
                                accountName = accountName,
                                userAvatarUrl = userAvatarUrl,
                                cachePurgeStatus = cachePurgeStatus,
                                downloadedTracksCount = downloadedMusicTracks.size,
                                downloadsSizeMB = downloadsSizeMB,
                                cacheSizeMB = cacheSizeMB,
                                localTracksCount = libraryTracks.size,
                                onYouTubeSyncClick = { launchYouTubeAuth() },
                                onYouTubeResyncClick = { viewModel.resyncYouTubeAccount() },
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
                                onExportBackupClick = { exportBackupLauncher.launch("unbound_backup_${System.currentTimeMillis()}.json") },
                                onRestoreBackupClick = { restoreBackupLauncher.launch("application/json") }
                            )
                        }
                        NavigationTab.LIBRARY -> {
                            LibraryScreen(
                                currentTrackId = currentTrack.id,
                                isPlaying = isPlaying,
                                isYouTubeConnected = isYouTubeConnected,
                                tracks = libraryTracks,
                                musicTracks = musicTracks,
                                mixedAudioTracks = mixedAudioTracks,
                                syncedYouTubeTracks = syncedYouTubeTracks,
                                onDeleteDownload = { viewModel.deleteTrackDownload(it) },
                                customPlaylists = customPlaylists,
                                artistPlaylists = artistPlaylists,
                                albumicPlaylists = albumicPlaylists,
                                albumCompletions = albumCompletions,
                                onDownloadRemaining = { viewModel.downloadMissingAlbumTracks(it) },
                                onCreatePlaylist = { title -> viewModel.createCustomPlaylist(title) },
                                onPlaylistClick = { playlist -> viewModel.openCustomPlaylist(playlist) },
                                onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) },
                                onPlayNext = { track -> viewModel.playNext(track) },
                                onAddToQueue = { track -> viewModel.addToQueue(track) },
                                downloadedTracks = downloadedMusicTracks,
                                onTrackSelect = { track, queue ->
                                    viewModel.playTrackWithQueue(track, queue)
                                    overlayState.isPlayerExpanded = true
                                },
                                onRefresh = { viewModel.refreshLibrary() },
                                onConnectClick = { launchYouTubeAuth() },
                                favoriteTracks = favoriteTracks,
                                recentlyPlayedTracks = recentlyPlayedTracks,
                                onOpenRingtoneCutter = { track -> overlayState.ringtoneCutterTrack = track },
                                onToggleFavorite = { track -> viewModel.toggleTrackFavorite(track) },
                                onShufflePlayAll = { viewModel.shufflePlayMusicTracks() },
                                onIdentifyTrack = { track -> viewModel.identifyTrack(track) },
                                onBatchIdentify = { viewModel.batchIdentifyUnknownTracks() },
                                allFilesGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    Environment.isExternalStorageManager()
                                } else true,
                                onRequestAllFilesPermission = { viewModel.requestAllFilesAccess() }
                            )
                        }
                    }
                }
            }
        }

        // Host for all modals, sheets, playback screens, and diagnostic HUD banners
        MainOverlayHost(
            overlayState = overlayState,
            viewModel = viewModel,
            launchYouTubeAuth = launchYouTubeAuth,
            onExportBackupClick = { exportBackupLauncher.launch("unbound_backup_${System.currentTimeMillis()}.json") },
            onRestoreBackupClick = { restoreBackupLauncher.launch("application/json") }
        )
    }
}
