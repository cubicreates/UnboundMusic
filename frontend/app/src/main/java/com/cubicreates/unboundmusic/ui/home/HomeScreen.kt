/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: HomeScreen.kt
 * Purpose: Unified reusable Home coordinator routing between the MusicScreen and PodcastScreen
 *          using one shared layout for both guest and signed-in listeners.
 * Subsystem: Home UI / Dual-Section Coordinator
 */

package com.cubicreates.unboundmusic.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cubicreates.unboundmusic.data.HomeMixDto
import com.cubicreates.unboundmusic.data.HomeSearchUiState
import com.cubicreates.unboundmusic.data.HomeSection
import com.cubicreates.unboundmusic.data.MixDto
import com.cubicreates.unboundmusic.data.PodcastEpisodeDto
import com.cubicreates.unboundmusic.data.PodcastShowDto
import com.cubicreates.unboundmusic.data.RecognitionUiState
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    selectedSection: HomeSection = HomeSection.MUSIC,
    onSectionSelected: (HomeSection) -> Unit = {},
    jumpBackTracks: List<TrackItem> = defaultTopTracks,
    homeMixes: List<HomeMixDto> = emptyList(),
    globalChartTracks: List<TrackItem> = emptyList(),
    regionalChartTracks: List<TrackItem> = emptyList(),
    regionCode: String = "IN",
    regionName: String = "Your Region",
    accountName: String? = null,
    isYouTubeConnected: Boolean = false,
    isSyncing: Boolean = false,
    isLoadingMore: Boolean = false,
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    onLoadMore: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onConnectClick: () -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onMixClick: (MixDto) -> Unit = {},
    onStartRadio: (TrackItem) -> Unit = {},
    vibeState: VibeSearchUiState = VibeSearchUiState.Idle,
    homeSearchState: HomeSearchUiState = HomeSearchUiState.Idle,
    onUnifiedHomeSearch: (String) -> Unit = {},
    onClearHomeResults: () -> Unit = {},
    isListeningAudio: Boolean = false,
    recordingSeconds: Int = 0,
    recognitionState: RecognitionUiState = RecognitionUiState.Idle,
    onShazamToggle: () -> Unit = {},
    onSingLyricsClick: () -> Unit = {},
    // Dedicated Podcast Props
    podcastFeaturedShows: List<PodcastShowDto> = emptyList(),
    podcastRecentEpisodes: List<PodcastEpisodeDto> = emptyList(),
    podcastTopEpisodes: List<PodcastEpisodeDto> = emptyList(),
    podcastGlobalEpisodes: List<PodcastEpisodeDto> = emptyList(),
    podcastRegionalEpisodes: List<PodcastEpisodeDto> = emptyList(),
    podcastSearchResults: List<PodcastEpisodeDto> = emptyList(),
    selectedPodcastShow: PodcastShowDto? = null,
    activePodcastTopicQuery: String? = null,
    isPodcastLoading: Boolean = false,
    podcastError: String? = null,
    onRefreshPodcasts: () -> Unit = {},
    onPodcastTopicSubmit: (String) -> Unit = {},
    onClearPodcastTopic: () -> Unit = {},
    onPodcastVoiceSearchClick: () -> Unit = {},
    onPodcastShowClick: (PodcastShowDto) -> Unit = {},
    onClosePodcastShow: () -> Unit = {},
    onPodcastEpisodePlay: (PodcastEpisodeDto) -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Crossfade(
            targetState = selectedSection,
            animationSpec = tween(220),
            label = "home_section_crossfade"
        ) { section ->
            when (section) {
                HomeSection.MUSIC -> {
                    MusicScreen(
                        selectedSection = selectedSection,
                        onSectionSelected = onSectionSelected,
                        jumpBackTracks = jumpBackTracks,
                        homeMixes = homeMixes,
                        globalChartTracks = globalChartTracks,
                        regionalChartTracks = regionalChartTracks,
                        regionCode = regionCode,
                        regionName = regionName,
                        accountName = accountName,
                        isYouTubeConnected = isYouTubeConnected,
                        isSyncing = isSyncing,
                        isLoadingMore = isLoadingMore,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onLoadMore = onLoadMore,
                        onSyncClick = onSyncClick,
                        onConnectClick = onConnectClick,
                        onUnifiedHomeSearch = onUnifiedHomeSearch,
                        onClearHomeResults = onClearHomeResults,
                        onTrackSelect = onTrackSelect,
                        onMixClick = onMixClick,
                        onStartRadio = onStartRadio,
                        vibeState = vibeState,
                        homeSearchState = homeSearchState,
                        isListeningAudio = isListeningAudio,
                        recordingSeconds = recordingSeconds,
                        recognitionState = recognitionState,
                        onShazamToggle = onShazamToggle,
                        onSingLyricsClick = onSingLyricsClick
                    )
                }

                HomeSection.PODCASTS -> {
                    PodcastScreen(
                        selectedSection = selectedSection,
                        onSectionSelected = onSectionSelected,
                        accountName = accountName,
                        isYouTubeConnected = isYouTubeConnected,
                        featuredShows = podcastFeaturedShows,
                        recentEpisodes = podcastRecentEpisodes,
                        topEpisodes = podcastTopEpisodes,
                        globalEpisodes = podcastGlobalEpisodes,
                        regionalEpisodes = podcastRegionalEpisodes,
                        searchResults = podcastSearchResults,
                        selectedShow = selectedPodcastShow,
                        activeTopicQuery = activePodcastTopicQuery,
                        regionCode = regionCode,
                        regionName = regionName,
                        isLoading = isPodcastLoading,
                        errorMessage = podcastError,
                        currentTrackId = currentTrackId,
                        isPlaying = isPlaying,
                        onRefresh = onRefreshPodcasts,
                        onTopicQuerySubmit = onPodcastTopicSubmit,
                        onClearTopicQuery = onClearPodcastTopic,
                        onVoiceSearchClick = onPodcastVoiceSearchClick,
                        onShowClick = onPodcastShowClick,
                        onCloseShow = onClosePodcastShow,
                        onEpisodePlay = onPodcastEpisodePlay
                    )
                }
            }
        }
    }
}
