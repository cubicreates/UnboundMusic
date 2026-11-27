/*
 * Package: com.cubicreates.unboundmusic.ui.search
 * File: SearchScreen.kt
 * Purpose: Unified, 100% scrollable Search screen sharing one frame for Music & Podcasts.
 *          Reuses Home's MediaSectionSwitcher and HomeUnifiedSearchBar with zero frozen sections.
 * Subsystem: Discovery / Unified Search
 */

package com.cubicreates.unboundmusic.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.HomeSection
import com.cubicreates.unboundmusic.data.PodcastEpisodeDto
import com.cubicreates.unboundmusic.data.PodcastShowDto
import com.cubicreates.unboundmusic.data.RecognitionUiState
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundEmptyStateCard
import com.cubicreates.unboundmusic.ui.home.components.HomeUnifiedSearchBar
import com.cubicreates.unboundmusic.ui.home.components.MediaFeedScaffold
import com.cubicreates.unboundmusic.ui.home.components.MediaSectionSwitcher
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLow

@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    selectedSection: HomeSection = HomeSection.MUSIC,
    onSectionSelected: (HomeSection) -> Unit = {},
    // Music Search
    searchQuery: String = "",
    searchResults: List<TrackItem> = emptyList(),
    isSearching: Boolean = false,
    vibeState: VibeSearchUiState = VibeSearchUiState.Idle,
    recognitionState: RecognitionUiState = RecognitionUiState.Idle,
    selectedCategory: SearchCategory = SearchCategory.ALL,
    chartTracks: List<TrackItem> = emptyList(),
    searchHistory: List<String> = emptyList(),
    searchSuggestions: List<String> = emptyList(),
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    isListeningAudio: Boolean = false,
    recordingSeconds: Int = 0,
    onCategorySelected: (SearchCategory) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},
    onRecordHistory: (String) -> Unit = {},
    onClearMusicSearch: () -> Unit = {},
    onSearchHistoryItemRemoved: (String) -> Unit = {},
    onSearchHistoryCleared: () -> Unit = {},
    onVibeSubmit: (String) -> Unit = {},
    onClearVibe: () -> Unit = {},
    onSingLyricsClick: () -> Unit = {},
    onIdentifyAudioClick: () -> Unit = {},
    onClearRecognition: () -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onAlbumClick: (id: String, title: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onArtistClick: (artistName: String) -> Unit = {},
    onPlayNextSingle: (TrackItem) -> Unit = {},
    onAddToQueueSingle: (TrackItem) -> Unit = {},
    onStartRadioSingle: (TrackItem) -> Unit = {},
    onDownloadSingle: (TrackItem) -> Unit = {},
    onAddToPlaylistSingle: (TrackItem) -> Unit = {},
    // Podcast Search
    podcastSearchQuery: String = "",
    podcastSearchFilter: PodcastSearchFilter = PodcastSearchFilter.ALL,
    podcastFeaturedShows: List<PodcastShowDto> = emptyList(),
    podcastTopEpisodes: List<PodcastEpisodeDto> = emptyList(),
    podcastSearchResults: List<PodcastEpisodeDto> = emptyList(),
    podcastSearchShows: List<PodcastShowDto> = emptyList(),
    selectedPodcastShow: PodcastShowDto? = null,
    podcastSearchHistory: List<String> = emptyList(),
    podcastSearchSuggestions: List<String> = emptyList(),
    isPodcastLoading: Boolean = false,
    isPodcastSearching: Boolean = false,
    podcastError: String? = null,
    onPodcastFilterSelected: (PodcastSearchFilter) -> Unit = {},
    onPodcastSearchQueryChanged: (String) -> Unit = {},
    onPodcastSearchSubmit: (String) -> Unit = {},
    onRecordPodcastHistory: (String) -> Unit = {},
    onClearPodcastSearch: () -> Unit = {},
    onPodcastVoiceSearchClick: () -> Unit = {},
    onPodcastSearchHistoryItemRemoved: (String) -> Unit = {},
    onPodcastSearchHistoryCleared: () -> Unit = {},
    onPodcastShowClick: (PodcastShowDto) -> Unit = {},
    onClosePodcastShow: () -> Unit = {},
    onPodcastEpisodePlay: (PodcastEpisodeDto) -> Unit = {}
) {
    val listState = rememberLazyListState()
    val isPodcasts = selectedSection == HomeSection.PODCASTS

    MediaFeedScaffold(
        modifier = modifier,
        listState = listState
    ) {
        // 1. Music vs Podcasts Switcher (same component as Home — scrolls with page)
        item(key = "search_section_switcher") {
            MediaSectionSwitcher(
                selectedSection = selectedSection,
                onSectionSelected = onSectionSelected
            )
        }

        // 2. Unified Search Bar (same component as Home — scrolls with page)
        item(key = "unified_search_bar") {
            val activeSearching = if (isPodcasts) {
                isPodcastLoading || isPodcastSearching
            } else {
                isSearching || vibeState is VibeSearchUiState.Loading || recognitionState is RecognitionUiState.Loading
            }
            HomeUnifiedSearchBar(
                isSearching = activeSearching,
                isListeningAudio = if (isPodcasts) false else isListeningAudio,
                recordingSeconds = recordingSeconds,
                placeholder = if (isPodcasts) {
                    "Search shows, topics, or episodes..."
                } else {
                    "Search songs, artists, albums, or #vibe..."
                },
                showShazamButton = !isPodcasts,
                externalQuery = if (isPodcasts) podcastSearchQuery else searchQuery,
                onQueryChange = { q ->
                    if (isPodcasts) {
                        onPodcastSearchQueryChanged(q)
                    } else {
                        if (q.isBlank()) onClearVibe()
                        onSearchQueryChanged(q)
                    }
                },
                onSubmitSearch = { submitted ->
                    if (isPodcasts) {
                        onPodcastSearchSubmit(submitted)
                    } else if (submitted.startsWith("#")) {
                        onVibeSubmit(submitted.removePrefix("#").trim())
                    } else {
                        onSearchSubmit(submitted)
                    }
                },
                onShazamToggle = onIdentifyAudioClick,
                onSingLyricsClick = {
                    if (isPodcasts) onPodcastVoiceSearchClick() else onSingLyricsClick()
                },
                onClearResults = {
                    if (isPodcasts) {
                        onClearPodcastSearch()
                        onClosePodcastShow()
                    } else {
                        onClearMusicSearch()
                        onClearVibe()
                        onClearRecognition()
                    }
                }
            )
        }

        // 3. Domain Filter Chips Row (scrolls cleanly with page — zero frozen bars)
        item(key = "search_filter_chips") {
            if (isPodcasts) {
                SearchFilterChipsRow(
                    items = PodcastSearchFilter.entries,
                    selectedItem = podcastSearchFilter,
                    labelOf = { it.label },
                    onSelect = onPodcastFilterSelected
                )
            } else {
                SearchFilterChipsRow(
                    items = SearchCategory.entries,
                    selectedItem = selectedCategory,
                    labelOf = { it.label },
                    onSelect = onCategorySelected
                )
            }
        }

        if (!isPodcasts) {
            // --- MUSIC SEARCH FRAME ---
            if (recognitionState !is RecognitionUiState.Idle) {
                item(key = "music_recognition_hdr") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = when (recognitionState) {
                                is RecognitionUiState.Listening -> "Listening (${recordingSeconds}s)..."
                                is RecognitionUiState.Loading -> "Finding matches..."
                                is RecognitionUiState.Success -> "Matched Songs"
                                is RecognitionUiState.Empty -> "No Match Found"
                                is RecognitionUiState.Error -> "Recognition Error"
                                else -> ""
                            },
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = UnboundPrimary,
                            modifier = Modifier.weight(1f)
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = UnboundSurfaceContainerLow,
                            border = BorderStroke(1.dp, androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f)),
                            onClick = onClearRecognition
                        ) {
                            Text(
                                text = "Clear",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
                if (recognitionState is RecognitionUiState.Success) {
                    val recTracks = recognitionState.candidates.map { it.toTrackItem() }
                    itemsIndexed(recTracks, key = { idx, t -> "rec_${t.id}_$idx" }) { _, track ->
                        SearchResultItem(
                            track = track,
                            isCurrentPlaying = track.id == currentTrackId && isPlaying,
                            onClick = { onTrackSelect(track, recTracks) },
                            onPlayNext = { onPlayNextSingle(track) },
                            onAddToQueue = { onAddToQueueSingle(track) },
                            onStartRadio = { onStartRadioSingle(track) },
                            onDownload = { onDownloadSingle(track) },
                            onAddToPlaylist = { onAddToPlaylistSingle(track) }
                        )
                    }
                } else {
                    item(key = "music_recognition_card") {
                        ShazamRecognitionCard(
                            recognitionState = recognitionState,
                            recordingSeconds = recordingSeconds,
                            onStopOrMatchNow = onIdentifyAudioClick,
                            onRetry = onIdentifyAudioClick,
                            onClear = onClearRecognition
                        )
                    }
                }
            }

            val isQueryOrVibeActive = searchQuery.isNotBlank() || vibeState !is VibeSearchUiState.Idle
            if (isQueryOrVibeActive) {
                if (searchQuery.isNotBlank()) {
                    item(key = "music_typing_suggestions") {
                        PreviousSearchesSection(
                            history = searchHistory,
                            suggestions = searchSuggestions,
                            currentQuery = searchQuery,
                            onQueryClick = { term ->
                                onSearchQueryChanged(term)
                                onSearchSubmit(term)
                            },
                            onFillQuery = { term -> onSearchQueryChanged(term) },
                            onRemoveItem = onSearchHistoryItemRemoved,
                            onClearAll = onSearchHistoryCleared
                        )
                    }
                }

                val displayTracks = when (vibeState) {
                    is VibeSearchUiState.Success -> vibeState.radioTracks.filter { track ->
                        val isAlbum = track.itemType.equals("album", true) || track.browseId.startsWith("MPREb_")
                        val isArtist = track.itemType.equals("artist", true) || track.browseId.startsWith("UC")
                        val isPlaylist = track.itemType.equals("playlist", true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL")
                        !isAlbum && !isArtist && !isPlaylist && !track.id.startsWith("UC") && !track.id.startsWith("MPREb_")
                    }
                    else -> searchResults
                }

                item(key = "music_results_hdr") {
                    Text(
                        text = if (vibeState is VibeSearchUiState.Success) "Vibe Results" else "Search Results",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }

                if (displayTracks.isEmpty() && !isSearching && vibeState !is VibeSearchUiState.Loading) {
                    item(key = "music_results_empty") {
                        UnboundEmptyStateCard(
                            title = if (vibeState is VibeSearchUiState.Error) "Search couldn't complete" else "No results found",
                            subtitle = if (vibeState is VibeSearchUiState.Error) {
                                vibeState.message
                            } else {
                                "No music matched \"$searchQuery\". Try another keyword or #vibe."
                            },
                            compact = true
                        )
                    }
                } else {
                    itemsIndexed(displayTracks, key = { idx, t -> "mres_${t.id}_$idx" }) { _, track ->
                        SearchResultItem(
                            track = track,
                            isCurrentPlaying = track.id == currentTrackId && isPlaying,
                            onClick = {
                                if (searchQuery.trim().length >= 2) {
                                    onRecordHistory(searchQuery.trim())
                                }
                                val isAlbum = track.itemType.equals("album", true) || track.browseId.startsWith("MPREb_") || track.id.startsWith("MPREb_")
                                val isArtist = track.itemType.equals("artist", true) || track.browseId.startsWith("UC") || track.id.startsWith("UC")
                                val isPlaylist = track.itemType.equals("playlist", true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL") || track.id.startsWith("VL") || track.id.startsWith("PL")
                                val resolvedArtist = if (track.itemType.equals("artist", true) || track.artist.isBlank() || track.artist.contains("subscriber", true)) {
                                    track.title.ifBlank { track.artist }
                                } else {
                                    track.artist.ifBlank { track.title }
                                }
                                val resolvedId = track.browseId.ifBlank { track.id }
                                when {
                                    isArtist -> onArtistClick(resolvedArtist)
                                    isAlbum || isPlaylist -> onAlbumClick(resolvedId, track.title, track.coverUrl)
                                    else -> onTrackSelect(track, displayTracks)
                                }
                            },
                            onPlayNext = { onPlayNextSingle(track) },
                            onAddToQueue = { onAddToQueueSingle(track) },
                            onStartRadio = { onStartRadioSingle(track) },
                            onDownload = { onDownloadSingle(track) },
                            onAddToPlaylist = { onAddToPlaylistSingle(track) }
                        )
                    }
                }
            } else {
                if (searchHistory.isNotEmpty()) {
                    item(key = "recent_music_searches") {
                        PreviousSearchesSection(
                            history = searchHistory,
                            onQueryClick = { term ->
                                onSearchQueryChanged(term)
                                onSearchSubmit(term)
                            },
                            onFillQuery = { term -> onSearchQueryChanged(term) },
                            onRemoveItem = onSearchHistoryItemRemoved,
                            onClearAll = onSearchHistoryCleared
                        )
                    }
                }

                item(key = "music_charts_hdr") {
                    Column {
                        Text(
                            text = "Trending Now",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Text(
                            text = "Global & regional singles",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }

                if (chartTracks.isEmpty()) {
                    item(key = "music_charts_empty") {
                        UnboundEmptyStateCard(
                            title = "No trending tracks loaded",
                            subtitle = "Use the search bar above to find songs, artists, albums, or #vibe mixes.",
                            compact = true
                        )
                    }
                } else {
                    itemsIndexed(chartTracks.take(15), key = { idx, t -> "mchart_${t.id}_$idx" }) { idx, track ->
                        RankedChartTrackRow(
                            rank = idx + 1,
                            track = track,
                            isCurrentPlaying = track.id == currentTrackId && isPlaying,
                            onPlayClick = { onTrackSelect(track, chartTracks) },
                            onPlayNext = { onPlayNextSingle(track) },
                            onAddToQueue = { onAddToQueueSingle(track) },
                            onStartRadio = { onStartRadioSingle(track) },
                            onDownload = { onDownloadSingle(track) },
                            onAddToPlaylist = { onAddToPlaylistSingle(track) }
                        )
                    }
                }
            }
        } else {
            // --- PODCAST SEARCH FRAME (exact structural mirror of Music) ---
            if (selectedPodcastShow != null) {
                item(key = "pod_selected_show") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        color = UnboundSurfaceContainerLow,
                        border = BorderStroke(1.dp, UnboundSecondary.copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            IconButton(
                                onClick = onClosePodcastShow,
                                modifier = Modifier.size(30.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = selectedPodcastShow.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${selectedPodcastShow.author} • ${selectedPodcastShow.episodes.size} episodes",
                                    fontSize = 12.sp,
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (selectedPodcastShow.episodes.isEmpty() && isPodcastLoading) {
                    item(key = "pod_show_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = UnboundPrimary, modifier = Modifier.size(22.dp))
                        }
                    }
                } else if (selectedPodcastShow.episodes.isEmpty()) {
                    item(key = "pod_show_empty") {
                        UnboundEmptyStateCard(
                            title = "No episodes available",
                            subtitle = "Episodes for ${selectedPodcastShow.title} couldn't be loaded right now.",
                            compact = true
                        )
                    }
                } else {
                    itemsIndexed(selectedPodcastShow.episodes, key = { idx, ep -> "sep_${ep.id}_$idx" }) { _, ep ->
                        PodcastEpisodeRowCard(
                            episode = ep,
                            isCurrentPlaying = ep.id == currentTrackId && isPlaying,
                            onPlayClick = { onPodcastEpisodePlay(ep) }
                        )
                    }
                }
            } else if (podcastSearchQuery.isNotBlank()) {
                item(key = "podcast_typing_suggestions") {
                    PreviousSearchesSection(
                        history = podcastSearchHistory,
                        suggestions = podcastSearchSuggestions,
                        currentQuery = podcastSearchQuery,
                        onQueryClick = { term ->
                            onPodcastSearchQueryChanged(term)
                            onPodcastSearchSubmit(term)
                        },
                        onFillQuery = { term -> onPodcastSearchQueryChanged(term) },
                        onRemoveItem = onPodcastSearchHistoryItemRemoved,
                        onClearAll = onPodcastSearchHistoryCleared
                    )
                }

                val showShows = podcastSearchFilter == PodcastSearchFilter.ALL || podcastSearchFilter == PodcastSearchFilter.SHOWS
                val showEpisodes = podcastSearchFilter == PodcastSearchFilter.ALL || podcastSearchFilter == PodcastSearchFilter.EPISODES

                if (showShows && podcastSearchShows.isNotEmpty()) {
                    item(key = "pod_shows_hdr") {
                        Text(
                            text = "Podcast Shows",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
                    itemsIndexed(podcastSearchShows, key = { idx, show -> "pshow_${show.id}_$idx" }) { _, show ->
                        PodcastShowRowCard(show = show, onClick = {
                            if (podcastSearchQuery.trim().length >= 2) {
                                onRecordPodcastHistory(podcastSearchQuery.trim())
                            }
                            onPodcastShowClick(show)
                        })
                    }
                }

                if (showEpisodes && podcastSearchResults.isNotEmpty()) {
                    item(key = "pod_eps_hdr") {
                        Text(
                            text = "Episodes",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                    }
                    itemsIndexed(podcastSearchResults, key = { idx, ep -> "pep_${ep.id}_$idx" }) { _, ep ->
                        PodcastEpisodeRowCard(
                            episode = ep,
                            isCurrentPlaying = ep.id == currentTrackId && isPlaying,
                            onPlayClick = {
                                if (podcastSearchQuery.trim().length >= 2) {
                                    onRecordPodcastHistory(podcastSearchQuery.trim())
                                }
                                onPodcastEpisodePlay(ep)
                            }
                        )
                    }
                }

                if (!isPodcastLoading && !isPodcastSearching &&
                    (showShows && podcastSearchShows.isEmpty()) &&
                    (showEpisodes && podcastSearchResults.isEmpty())
                ) {
                    item(key = "pod_search_empty") {
                        UnboundEmptyStateCard(
                            title = "No podcasts found",
                            subtitle = podcastError ?: "No shows or episodes matched \"$podcastSearchQuery\".",
                            compact = true
                        )
                    }
                }
            } else {
                if (podcastSearchHistory.isNotEmpty()) {
                    item(key = "recent_pod_searches") {
                        PreviousSearchesSection(
                            history = podcastSearchHistory,
                            onQueryClick = { term ->
                                onPodcastSearchQueryChanged(term)
                                onPodcastSearchSubmit(term)
                            },
                            onFillQuery = { term -> onPodcastSearchQueryChanged(term) },
                            onRemoveItem = onPodcastSearchHistoryItemRemoved,
                            onClearAll = onPodcastSearchHistoryCleared
                        )
                    }
                }

                if (podcastFeaturedShows.isNotEmpty()) {
                    item(key = "pod_popular_hdr") {
                        Column {
                            Text(
                                text = "Popular Shows",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface
                            )
                            Text(
                                text = "Curated series & conversations",
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                    itemsIndexed(podcastFeaturedShows.take(5), key = { idx, show -> "pfshow_${show.id}_$idx" }) { _, show ->
                        PodcastShowRowCard(show = show, onClick = { onPodcastShowClick(show) })
                    }
                }

                item(key = "pod_trending_hdr") {
                    Text(
                        text = "Trending Episodes",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }

                if (podcastTopEpisodes.isEmpty() && isPodcastLoading) {
                    item(key = "pod_top_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = UnboundPrimary,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                } else if (podcastTopEpisodes.isEmpty()) {
                    item(key = "pod_top_empty") {
                        UnboundEmptyStateCard(
                            title = "No trending episodes loaded",
                            subtitle = "Search above for a podcast show, host, or topic.",
                            compact = true
                        )
                    }
                } else {
                    itemsIndexed(podcastTopEpisodes.take(12), key = { idx, ep -> "ptop_${ep.id}_$idx" }) { _, ep ->
                        PodcastEpisodeRowCard(
                            episode = ep,
                            isCurrentPlaying = ep.id == currentTrackId && isPlaying,
                            onPlayClick = { onPodcastEpisodePlay(ep) }
                        )
                    }
                }
            }
        }
    }
}
