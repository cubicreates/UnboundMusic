/*
 * Package: com.cubicreates.unboundmusic.ui.search
 * File: SearchScreen.kt
 * Purpose: Production coordinator SearchScreen routing to SignedInSearchScreen (with search history)
 *          or GuestSearchScreen (trending vibes & charts).
 * Subsystem: Discovery / Dual-Mode Coordinator
 */

package com.cubicreates.unboundmusic.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    isYouTubeConnected: Boolean = false,
    searchResults: List<TrackItem> = emptyList(),
    isSearching: Boolean = false,
    vibeState: VibeSearchUiState = VibeSearchUiState.Idle,
    selectedCategory: SearchCategory = SearchCategory.ALL,
    chartTracks: List<TrackItem> = emptyList(),
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    onCategorySelected: (SearchCategory) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onVibeSubmit: (String) -> Unit = {},
    onListenToSurroundings: () -> Unit = {},
    onVibeTagClick: (String) -> Unit = {},
    onGenreCardClick: (String) -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onAlbumClick: (id: String, title: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onArtistClick: (artistName: String) -> Unit = {},
    onPlayNextSingle: (TrackItem) -> Unit = {},
    onAddToQueueSingle: (TrackItem) -> Unit = {},
    onStartRadioSingle: (TrackItem) -> Unit = {},
    onDownloadSingle: (TrackItem) -> Unit = {},
    onAddToPlaylistSingle: (TrackItem) -> Unit = {},
    searchSuggestions: List<String> = emptyList(),
    searchHistory: List<String> = emptyList(),
    onSearchSubmit: (String) -> Unit = {},
    onSearchHistoryItemRemoved: (String) -> Unit = {},
    onSearchHistoryCleared: () -> Unit = {},
    onPlayNextBatch: (List<TrackItem>) -> Unit = {},
    onAddToQueueBatch: (List<TrackItem>) -> Unit = {},
    onDownloadBatch: (List<TrackItem>) -> Unit = {},
    isListeningAudio: Boolean = false,
    onClearVibe: () -> Unit = {}
) {
    // Dual-Mode Routing:
    // Mode 1: Logged-in / history-active user -> SignedInSearchScreen (Recent searches & tailored suggestions)
    // Mode 2: Guest / Offline user -> GuestSearchScreen (Trending vibes, charts, and exploration)
    val isUserActive = isYouTubeConnected || searchHistory.isNotEmpty()

    if (isUserActive) {
        SignedInSearchScreen(
            modifier = modifier,
            searchResults = searchResults,
            isSearching = isSearching,
            vibeState = vibeState,
            selectedCategory = selectedCategory,
            chartTracks = chartTracks,
            searchSuggestions = searchSuggestions,
            searchHistory = searchHistory,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onCategorySelected = onCategorySelected,
            onSearchQueryChanged = onSearchQueryChanged,
            onSearchSubmit = onSearchSubmit,
            onSearchHistoryItemRemoved = onSearchHistoryItemRemoved,
            onSearchHistoryCleared = onSearchHistoryCleared,
            onVibeSubmit = onVibeSubmit,
            onListenToSurroundings = onListenToSurroundings,
            onVibeTagClick = onVibeTagClick,
            onGenreCardClick = onGenreCardClick,
            onTrackSelect = onTrackSelect,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlayNextSingle = onPlayNextSingle,
            onAddToQueueSingle = onAddToQueueSingle,
            onStartRadioSingle = onStartRadioSingle,
            onDownloadSingle = onDownloadSingle,
            onAddToPlaylistSingle = onAddToPlaylistSingle,
            onPlayNextBatch = onPlayNextBatch,
            onAddToQueueBatch = onAddToQueueBatch,
            onDownloadBatch = onDownloadBatch,
            isListeningAudio = isListeningAudio,
            onClearVibe = onClearVibe
        )
    } else {
        GuestSearchScreen(
            modifier = modifier,
            searchResults = searchResults,
            isSearching = isSearching,
            vibeState = vibeState,
            selectedCategory = selectedCategory,
            chartTracks = chartTracks,
            currentTrackId = currentTrackId,
            isPlaying = isPlaying,
            onCategorySelected = onCategorySelected,
            onSearchQueryChanged = onSearchQueryChanged,
            onVibeSubmit = onVibeSubmit,
            onListenToSurroundings = onListenToSurroundings,
            onVibeTagClick = onVibeTagClick,
            onGenreCardClick = onGenreCardClick,
            onTrackSelect = onTrackSelect,
            onAlbumClick = onAlbumClick,
            onArtistClick = onArtistClick,
            onPlayNextSingle = onPlayNextSingle,
            onAddToQueueSingle = onAddToQueueSingle,
            onStartRadioSingle = onStartRadioSingle,
            onDownloadSingle = onDownloadSingle,
            onAddToPlaylistSingle = onAddToPlaylistSingle,
            onSearchSubmit = onSearchSubmit,
            onPlayNextBatch = onPlayNextBatch,
            onAddToQueueBatch = onAddToQueueBatch,
            onDownloadBatch = onDownloadBatch,
            isListeningAudio = isListeningAudio,
            onClearVibe = onClearVibe
        )
    }
}
