/*
 * Package: com.cubicreates.unboundmusic.ui
 * File: MainApp.kt
 * Purpose: Root composable shell with responsive tab navigation, floating mini-player,
 *          and delegation to MainOverlayHost for modal playback, dialogs, and sheets.
 * Subsystem: Navigation Shell
 */

package com.cubicreates.unboundmusic.ui

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
import com.cubicreates.unboundmusic.ui.shazam.ShazamScreen
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.viewmodel.MainViewModel

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
    val searchCategory by viewModel.searchCategory.collectAsStateWithLifecycle()
    val searchSuggestions by viewModel.searchSuggestions.collectAsStateWithLifecycle()
    val searchHistory by viewModel.searchHistory.collectAsStateWithLifecycle()
    val daypartingState by viewModel.daypartingState.collectAsStateWithLifecycle()
    val homeVibeState by viewModel.homeVibeState.collectAsStateWithLifecycle()
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
    val audioWaveAmplitude by viewModel.audioWaveAmplitude.collectAsStateWithLifecycle()
    val lastRecognizedTrack by viewModel.lastRecognizedTrack.collectAsStateWithLifecycle()
    val recognizedVariants by viewModel.recognizedVariants.collectAsStateWithLifecycle()
    val shazamHistory by viewModel.shazamHistory.collectAsStateWithLifecycle()
    val recognizedMessage by viewModel.recognizedMessage.collectAsStateWithLifecycle()
    val shazamMode by viewModel.shazamMode.collectAsStateWithLifecycle()
    val recordingDurationSeconds by viewModel.recordingDurationSeconds.collectAsStateWithLifecycle()

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
                    userAvatarUrl = userAvatarUrl,
                    accountName = accountName,
                    isLoggedIn = isYouTubeConnected,
                    activeDownloadsCount = downloadTasks.values.count { it.status == "DOWNLOADING" || it.status == "TAGGING" || it.status == "QUEUED" },
                    onProfileClick = { overlayState.showSettings = true },
                    onDownloadsClick = { overlayState.showDownloadsScreen = true }
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
                            onPlayPauseToggle = { viewModel.togglePlayPause() },
                            onFavoriteToggle = { viewModel.toggleFavorite() },
                            onPlayerClick = { overlayState.isPlayerExpanded = true }
                        )

                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    UnboundBottomNavBar(
                        currentTab = selectedTab,
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
                                onConnectClick = { overlayState.showYouTubeLoginSheet = true },
                                onCapsuleSelect = { capsule ->
                                    viewModel.playMoodCapsule(capsule)
                                    overlayState.isPlayerExpanded = true
                                },
                                onMoodSelect = { mood ->
                                    viewModel.openCuratedCollection(mood.title, mood.imageUrl)
                                },
                                onGenreSelect = { genre ->
                                    overlayState.viewingGenre = genre
                                    viewModel.loadGenreDetail(genre.params, genre.title)
                                },
                                onMixClick = { mix ->
                                    viewModel.playCuratedMix(mix)
                                    overlayState.isPlayerExpanded = true
                                },
                                onAlbumPlaylistClick = { id, title, coverUrl ->
                                    viewModel.openAlbumPlaylist(id, title, coverUrl)
                                },
                                onTrackSelect = { track, queue ->
                                    viewModel.playTrackWithQueue(track, queue)
                                    overlayState.isPlayerExpanded = true
                                },
                                onProfileClick = { overlayState.showSettings = true },
                                onMenuClick = {
                                    viewModel.loadRecap()
                                    overlayState.showRecap = true
                                },
                                currentTrackId = currentTrack.id,
                                isPlaying = isPlaying,
                                onPlayNext = { track -> viewModel.playNextBatch(listOf(track)) },
                                onAddToQueue = { track -> viewModel.addToQueueBatch(listOf(track)) },
                                onDownload = { track -> viewModel.downloadBatch(listOf(track)) },
                                onStartRadio = { track ->
                                    viewModel.startRadio(track)
                                    overlayState.isPlayerExpanded = true
                                },
                                selectedMood = selectedHomeMood,
                                moodTracks = moodTracks,
                                isMoodLoading = isMoodLoading,
                                onMoodFilterSelect = { viewModel.selectHomeMood(it) },
                                isVibeLoading = homeVibeState is VibeSearchUiState.Loading,
                                vibeState = homeVibeState,
                                onVibeSubmit = { prompt -> viewModel.submitHomeVibeQuery(prompt) },
                                onClearVibe = { viewModel.clearHomeVibeQuery() }
                            )
                        }
                        NavigationTab.SEARCH -> {
                            SearchScreen(
                                isYouTubeConnected = isYouTubeConnected,
                                searchResults = searchResults,
                                isSearching = isSearching,
                                vibeState = searchVibeState,
                                selectedCategory = searchCategory,
                                chartTracks = if (regionalCharts.isNotEmpty()) regionalCharts else chartTracks,
                                searchSuggestions = searchSuggestions,
                                searchHistory = searchHistory,
                                currentTrackId = currentTrack.id,
                                isPlaying = isPlaying,
                                onCategorySelected = { viewModel.setSearchCategory(it) },
                                onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                                onSearchSubmit = { viewModel.submitSearch(it) },
                                onSearchHistoryItemRemoved = { viewModel.removeSearchHistoryItem(it) },
                                onSearchHistoryCleared = { viewModel.clearSearchHistory() },
                                onVibeSubmit = { viewModel.submitSearchVibeQuery(it) },
                                onClearVibe = { viewModel.clearSearchVibeQuery() },
                                onListenToSurroundings = { viewModel.startAmbientShazamRecognition() },
                                onVibeTagClick = { tag -> viewModel.submitSearchVibeQuery(tag.removePrefix("#")) },
                                onGenreCardClick = { genre -> viewModel.openCuratedCollection(genre) },
                                onTrackSelect = { track, queue ->
                                    viewModel.playTrackWithQueue(track, queue)
                                    overlayState.isPlayerExpanded = true
                                },
                                onAlbumClick = { id, title, coverUrl ->
                                    viewModel.openAlbumPlaylist(id, title, coverUrl)
                                },
                                onArtistClick = { artistName ->
                                    overlayState.viewingArtist = artistName
                                    viewModel.loadArtistProfile(artistName)
                                },
                                onPlayNextBatch = { tracks -> viewModel.playNextBatch(tracks) },
                                onAddToQueueBatch = { tracks -> viewModel.addToQueueBatch(tracks) },
                                onDownloadBatch = { tracks -> viewModel.downloadBatch(tracks) },
                                onPlayNextSingle = { track -> viewModel.playNextBatch(listOf(track)) },
                                onAddToQueueSingle = { track -> viewModel.addToQueueBatch(listOf(track)) },
                                onStartRadioSingle = { track ->
                                    viewModel.startRadio(track)
                                    overlayState.isPlayerExpanded = true
                                },
                                onDownloadSingle = { track -> viewModel.downloadBatch(listOf(track)) },
                                onAddToPlaylistSingle = { track -> viewModel.showAddToPlaylist(track) },
                                isListeningAudio = isListeningShazam
                            )
                        }
                        NavigationTab.SHAZAM -> {
                            ShazamScreen(
                                isYouTubeConnected = isYouTubeConnected,
                                isListening = isListeningShazam,
                                audioAmplitude = audioWaveAmplitude,
                                statusMessage = recognizedMessage,
                                accountName = accountName,
                                lastRecognizedTrack = lastRecognizedTrack,
                                recognizedVariants = recognizedVariants,
                                shazamHistory = shazamHistory,
                                isFavorite = lastRecognizedTrack?.let { it.id == currentTrack.id && isFavorite } ?: false,
                                shazamMode = shazamMode,
                                recordingDurationSeconds = recordingDurationSeconds,
                                onModeChange = { viewModel.setShazamMode(it) },
                                onLaunchGoogleSoundSearch = { viewModel.launchGoogleSoundSearch(context) },
                                onStartListening = { viewModel.startAmbientShazamRecognition() },
                                onStopListening = { viewModel.stopAndSendShazamRecording() },
                                onDismissRecognized = { viewModel.clearLastRecognizedTrack() },
                                onPlayTrack = { track ->
                                    viewModel.playTrack(track)
                                    overlayState.isPlayerExpanded = true
                                },
                                onStartRadio = { track ->
                                    viewModel.startRadio(track)
                                    overlayState.isPlayerExpanded = true
                                },
                                onToggleFavorite = { track -> viewModel.toggleTrackFavorite(track) },
                                onAddToPlaylist = { track -> viewModel.showAddToPlaylist(track) },
                                onDownload = { track -> viewModel.startTrackDownload(track) },
                                onConnectYouTubeClick = { launchYouTubeAuth() }
                            )
                        }
                        NavigationTab.LIBRARY -> {
                            LibraryScreen(
                                isYouTubeConnected = isYouTubeConnected,
                                savedGB = savedGB,
                                downloadsCount = downloadsCount,
                                whatsappCount = whatsappCount,
                                telegramCount = telegramCount,
                                youtubeCount = if (isYouTubeConnected) syncedYouTubeTracks.size else youtubeCount,
                                tracks = libraryTracks,
                                musicTracks = musicTracks,
                                mixedAudioTracks = mixedAudioTracks,
                                syncedYouTubeTracks = syncedYouTubeTracks,
                                downloadTasks = downloadTasks,
                                onStartDownload = { viewModel.startTrackDownload(it) },
                                onCancelDownload = { viewModel.cancelTrackDownload(it) },
                                onDeleteDownload = { viewModel.deleteTrackDownload(it) },
                                onOpenDownloadsHub = { overlayState.showDownloadsScreen = true },
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
                                onStartRadio = { track ->
                                    viewModel.startRadio(track)
                                    overlayState.isPlayerExpanded = true
                                },
                                downloadedTracks = downloadedMusicTracks,
                                onTrackSelect = { track, queue ->
                                    viewModel.playTrackWithQueue(track, queue)
                                    overlayState.isPlayerExpanded = true
                                },
                                onRefresh = { viewModel.refreshLibrary() },
                                onProfileClick = { overlayState.showSettings = true },
                                favoriteTracks = favoriteTracks,
                                recentlyPlayedTracks = recentlyPlayedTracks,
                                onOpenEqualizer = { overlayState.showEqualizer = true },
                                onOpenRingtoneCutter = { track -> overlayState.ringtoneCutterTrack = track },
                                onToggleFavorite = { track -> viewModel.toggleTrackFavorite(track) },
                                onStartShazam = { viewModel.startAmbientShazamRecognition() },
                                onShufflePlayAll = { viewModel.shufflePlayMusicTracks() },
                                onIdentifyTrack = { track -> viewModel.identifyTrack(track) },
                                onBatchIdentify = { viewModel.batchIdentifyUnknownTracks() }
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
