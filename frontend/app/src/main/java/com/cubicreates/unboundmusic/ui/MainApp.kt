/*
 * Package: com.cubicreates.unboundmusic.ui
 * File: MainApp.kt
 * Purpose: Root composable shell with tab navigation, floating mini-player, full-screen Now Playing,
 *          and seamless modal routing for 10-Band Equalizer, AutoEq, Settings, Artist, and Unbound Recap.
 * Subsystem: Navigation Shell
 */

package com.cubicreates.unboundmusic.ui

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cubicreates.unboundmusic.ui.artist.ArtistScreen
import com.cubicreates.unboundmusic.ui.components.FloatingMiniPlayer
import com.cubicreates.unboundmusic.ui.components.NavigationTab
import com.cubicreates.unboundmusic.ui.components.UnboundBottomNavBar
import com.cubicreates.unboundmusic.ui.equalizer.AutoEqPickerDialog
import com.cubicreates.unboundmusic.ui.equalizer.EqualizerScreen
import com.cubicreates.unboundmusic.ui.home.HomeScreen
import com.cubicreates.unboundmusic.ui.library.LibraryScreen
import com.cubicreates.unboundmusic.ui.player.NowPlayingScreen
import com.cubicreates.unboundmusic.ui.recap.RecapScreen
import com.cubicreates.unboundmusic.ui.search.SearchScreen
import com.cubicreates.unboundmusic.ui.settings.SettingsScreen
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.viewmodel.MainViewModel

/**
 * Root composable hosting the navigation shell, floating mini-player, and full screen modals.
 */
@Composable
fun MainApp(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel()
) {
    var selectedTab by remember { mutableStateOf(NavigationTab.HOME) }
    var isPlayerExpanded by remember { mutableStateOf(false) }

    // Modal navigation states
    var showSettings by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showAutoEqPicker by remember { mutableStateOf(false) }
    var showRecap by remember { mutableStateOf(false) }
    var viewingArtist by remember { mutableStateOf<String?>(null) }

    val currentTrack by viewModel.currentTrack.collectAsStateWithLifecycle()
    val isFavorite by viewModel.isFavorite.collectAsStateWithLifecycle()
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
    val canvasArtUrl by viewModel.canvasArtUrl.collectAsStateWithLifecycle()
    val chartTracks by viewModel.chartTracks.collectAsStateWithLifecycle()
    val equalizerCurve by viewModel.equalizerCurve.collectAsStateWithLifecycle()
    val autoEqResults by viewModel.autoEqResults.collectAsStateWithLifecycle()
    val isSearchingAutoEq by viewModel.isSearchingAutoEq.collectAsStateWithLifecycle()
    val artistProfile by viewModel.artistProfile.collectAsStateWithLifecycle()
    val isLoadingArtist by viewModel.isLoadingArtist.collectAsStateWithLifecycle()
    val recapData by viewModel.recapData.collectAsStateWithLifecycle()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        if (isPlayerExpanded || selectedTab == NavigationTab.PLAYING) {
            // Full Screen Immersive Now Playing
            NowPlayingScreen(
                track = currentTrack,
                isPlaying = playbackState.isPlaying,
                isFavorite = isFavorite,
                progress = playbackState.progress,
                currentPositionMs = playbackState.currentPositionMs,
                formattedPosition = playbackState.formattedPosition,
                formattedRemaining = playbackState.formattedRemaining,
                lyricsLines = lyricsLines,
                canvasArtUrl = canvasArtUrl,
                queue = playbackState.queue,
                playbackMode = playbackState.playbackMode,
                onCollapse = {
                    isPlayerExpanded = false
                    if (selectedTab == NavigationTab.PLAYING) {
                        selectedTab = NavigationTab.HOME
                    }
                },
                onPlayPauseToggle = { viewModel.togglePlayPause() },
                onFavoriteToggle = { viewModel.toggleFavorite() },
                onPreviousTrack = { viewModel.prevTrack() },
                onNextTrack = { viewModel.nextTrack() },
                onSeek = { viewModel.seekTo(it) },
                onSeekPositionMs = { viewModel.seekToPositionMs(it) },
                onCyclePlaybackMode = { viewModel.cyclePlaybackMode() },
                onEqualizerClick = { showEqualizer = true },
                onQueueTrackSelect = { index -> viewModel.playQueueTrack(index) }
            )
        } else {
            // Standard Tab Navigation Content inside Responsive Scaffold
            androidx.compose.material3.Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = UnboundBackground,
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(UnboundBackground)
                    ) {
                        if (currentTrack.title.isNotBlank() && (playbackState.isPlaying || playbackState.currentPositionMs > 0)) {
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
                            onTabSelected = { tab ->
                                if (tab == NavigationTab.PLAYING) {
                                    isPlayerExpanded = true
                                } else {
                                    selectedTab = tab
                                }
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
                                    tracks = chartTracks,
                                    onTrackSelect = { track ->
                                        viewModel.playTrack(track)
                                        isPlayerExpanded = true
                                    },
                                    onProfileClick = { showSettings = true },
                                    onMenuClick = {
                                        viewModel.loadRecap()
                                        showRecap = true
                                    }
                                )
                            }
                            NavigationTab.SEARCH -> {
                                SearchScreen(
                                    searchResults = searchResults,
                                    isSearching = isSearching,
                                    onSearchQueryChanged = { viewModel.onSearchQueryChanged(it) },
                                    onListenToSurroundings = { viewModel.startAmbientShazamRecognition() },
                                    onVibeTagClick = { tag -> viewModel.executeVibeSearch(tag.removePrefix("#")) },
                                    onGenreCardClick = { genre -> viewModel.executeVibeSearch(genre) },
                                    onTrackSelect = { track ->
                                        viewModel.playTrack(track)
                                        isPlayerExpanded = true
                                    }
                                )
                            }
                            NavigationTab.LIBRARY -> {
                                LibraryScreen(
                                    savedGB = savedGB,
                                    downloadsCount = downloadsCount,
                                    whatsappCount = whatsappCount,
                                    telegramCount = telegramCount,
                                    youtubeCount = youtubeCount,
                                    tracks = libraryTracks,
                                    onSourceClick = { viewModel.refreshLibrary() },
                                    onTrackSelect = { track ->
                                        viewModel.playTrack(track)
                                        isPlayerExpanded = true
                                    },
                                    onRefresh = { viewModel.refreshLibrary() },
                                    onProfileClick = { showSettings = true }
                                )
                            }
                            NavigationTab.PLAYING -> {
                                // Handled by isPlayerExpanded above
                            }
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
                onAutoEqClick = { showAutoEqPicker = true }
            )
        }

        // Modal 2: 10-Band Equalizer Screen
        if (showEqualizer) {
            EqualizerScreen(
                initialCurve = equalizerCurve,
                onCurveChanged = { viewModel.setEqualizerCurve(it) },
                onAutoEqClick = { showAutoEqPicker = true },
                onClose = { showEqualizer = false }
            )
        }

        // Modal 3: AutoEq Headphone Picker Dialog
        if (showAutoEqPicker) {
            AutoEqPickerDialog(
                searchResults = autoEqResults,
                isSearching = isSearchingAutoEq,
                onSearchQueryChanged = { viewModel.searchAutoEqPresets(it) },
                onPresetSelected = { viewModel.applyAutoEqPreset(it) },
                onDismiss = { showAutoEqPicker = false }
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
    }
}
