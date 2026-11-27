/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: MusicScreen.kt
 * Purpose: Clean, reusable Music Home screen shared by both Guest and Logged-In users.
 *          All heavy feed assembly & chart separation is handled by the Go backend (/api/v1/explore/home):
 *          - Music/Podcast switcher & compact greeting
 *          - Unified Search Bar (AI auto-detect or Normal + Mic + Shazam)
 *          - Jump Back In <-> Results (4-row infinite-column snapping slider)
 *          - Made For Your Mood (1:1 infinite-scrolling YouTube Mixes)
 *          - Trending Globally (worldwide singles carousel)
 *          - Trending Regionally (user country singles carousel)
 * Subsystem: Home UI / Music Experience
 */

package com.cubicreates.unboundmusic.ui.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cubicreates.unboundmusic.data.HomeMixDto
import com.cubicreates.unboundmusic.data.HomeSearchUiState
import com.cubicreates.unboundmusic.data.HomeSection
import com.cubicreates.unboundmusic.data.MixDto
import com.cubicreates.unboundmusic.data.RecognitionUiState
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundEmptyStateCard
import com.cubicreates.unboundmusic.ui.home.components.CompactJumpGridSection
import com.cubicreates.unboundmusic.ui.home.components.CompactMediaItem
import com.cubicreates.unboundmusic.ui.home.components.CuratedDeckCardData
import com.cubicreates.unboundmusic.ui.home.components.HomeUnifiedSearchBar
import com.cubicreates.unboundmusic.ui.home.components.MediaFeedScaffold
import com.cubicreates.unboundmusic.ui.home.components.MediaSectionSwitcher
import com.cubicreates.unboundmusic.ui.home.components.TelemetryGreetingSection
import com.cubicreates.unboundmusic.ui.home.components.TrendingCardData
import com.cubicreates.unboundmusic.ui.home.components.TrendingHorizontalCarouselSection
import com.cubicreates.unboundmusic.ui.home.components.YouTubeMixHorizontalDeckSection
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import java.util.Calendar

@Composable
fun MusicScreen(
    modifier: Modifier = Modifier,
    selectedSection: HomeSection = HomeSection.MUSIC,
    onSectionSelected: (HomeSection) -> Unit = {},
    jumpBackTracks: List<TrackItem> = emptyList(),
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
    onUnifiedHomeSearch: (String) -> Unit = {},
    onClearHomeResults: () -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onMixClick: (MixDto) -> Unit = {},
    onStartRadio: (TrackItem) -> Unit = {},
    vibeState: VibeSearchUiState = VibeSearchUiState.Idle,
    homeSearchState: HomeSearchUiState = HomeSearchUiState.Idle,
    isListeningAudio: Boolean = false,
    recordingSeconds: Int = 0,
    recognitionState: RecognitionUiState = RecognitionUiState.Idle,
    onShazamToggle: () -> Unit = {},
    onSingLyricsClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    val hour = remember { Calendar.getInstance().get(Calendar.HOUR_OF_DAY) }
    val greetingPrefix = remember(hour) {
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Good night"
        }
    }
    val displayName = remember(accountName, isYouTubeConnected) {
        if (isYouTubeConnected && !accountName.isNullOrBlank() && accountName != "Local User") {
            accountName.substringBefore(" ")
        } else {
            "Explorer"
        }
    }

    val safeJumpBackTracks = remember(jumpBackTracks, regionalChartTracks, globalChartTracks) {
        jumpBackTracks.ifEmpty {
            (regionalChartTracks + globalChartTracks).distinctBy { it.id.ifBlank { it.title } }
        }
    }

    val jumpBackItems = remember(safeJumpBackTracks) {
        safeJumpBackTracks.map { t ->
            CompactMediaItem(
                id = t.id,
                title = t.title,
                subtitle = t.artist.ifBlank { "Music" },
                coverUrl = t.coverUrl
            )
        }
    }

    // Backend-built mixes (falling back cleanly to seed-derived mixes if cold-boot cache is empty)
    val mixCards = remember(homeMixes, safeJumpBackTracks) {
        if (homeMixes.isNotEmpty()) {
            homeMixes.map { mix ->
                CuratedDeckCardData(
                    id = mix.id,
                    tag = "MIX",
                    title = mix.title,
                    description = mix.subtitle,
                    footer = "Continuous Mix",
                    coverUrl = mix.coverUrl
                )
            }
        } else {
            val artists = safeJumpBackTracks.map { it.artist.trim() }.filter { it.isNotBlank() }.distinct()
            safeJumpBackTracks.take(16).mapIndexed { idx, track ->
                val primaryArtist = track.artist.trim().ifBlank { "YouTube Music" }
                val others = artists.filter { !it.equals(primaryArtist, true) }
                    .let { pool ->
                        if (pool.isEmpty()) emptyList()
                        else listOfNotNull(pool.getOrNull(idx % pool.size), pool.getOrNull((idx + 1) % pool.size)).distinct()
                    }
                val roster = when {
                    others.size >= 2 -> "$primaryArtist, ${others[0]}, ${others[1]}, and more"
                    others.size == 1 -> "$primaryArtist, ${others[0]}, and more"
                    else -> "$primaryArtist, and more"
                }
                CuratedDeckCardData(
                    id = "trackmix:${track.id.ifBlank { track.title }}",
                    tag = "MIX",
                    title = "Mix – ${track.title}",
                    description = roster,
                    footer = "Continuous Mix",
                    coverUrl = track.coverUrl
                )
            }
        }
    }

    // Trending Globally (worldwide singles from backend)
    val globalSource = remember(globalChartTracks) { globalChartTracks }
    val globalCards = remember(globalSource) {
        globalSource.mapIndexed { idx, t ->
            TrendingCardData(
                id = t.id,
                rankLabel = "#${idx + 1} Global",
                title = t.title,
                subtitle = t.artist.ifBlank { "Global Artist" },
                metaText = t.album.ifBlank { "Global Singles" },
                coverUrl = t.coverUrl,
                isTopRank = idx < 3
            )
        }
    }

    // Trending Regionally (user country singles from backend)
    val regionalSource = remember(regionalChartTracks, regionName) { regionalChartTracks }
    val regionalCards = remember(regionalSource, regionCode, regionName) {
        regionalSource.mapIndexed { idx, t ->
            TrendingCardData(
                id = t.id,
                rankLabel = "#${idx + 1} $regionCode",
                title = t.title,
                subtitle = t.artist.ifBlank { "Regional Artist" },
                metaText = t.album.ifBlank { "Trending in $regionName" },
                coverUrl = t.coverUrl,
                isTopRank = idx < 3
            )
        }
    }

    MediaFeedScaffold(
        modifier = modifier,
        listState = listState
    ) {
        // 1. Music vs Podcasts Switcher
        item(key = "section_switcher") {
            MediaSectionSwitcher(
                selectedSection = selectedSection,
                onSectionSelected = onSectionSelected
            )
        }

        // 2. Compact Greeting + Sync
        item(key = "greeting") {
            TelemetryGreetingSection(
                badgeText = if (isYouTubeConnected) "Synced Library" else "Fresh Picks",
                greetingText = "$greetingPrefix, $displayName",
                isSyncing = isSyncing,
                onSyncClick = if (isYouTubeConnected) onSyncClick else onConnectClick
            )
        }

        // 3. Single Unified Search Bar (AI auto-detect or Normal + Mic + Shazam)
        item(key = "unified_search_bar") {
            val isAnySearching = homeSearchState is HomeSearchUiState.Loading ||
                vibeState is VibeSearchUiState.Loading ||
                recognitionState is RecognitionUiState.Loading
            HomeUnifiedSearchBar(
                isSearching = isAnySearching,
                isListeningAudio = isListeningAudio,
                recordingSeconds = recordingSeconds,
                onSubmitSearch = onUnifiedHomeSearch,
                onShazamToggle = onShazamToggle,
                onSingLyricsClick = onSingLyricsClick,
                onClearResults = onClearHomeResults
            )
        }

        // 4. Multifunctional "Jump Back In" <-> "Results" Section
        val isResultsMode = homeSearchState !is HomeSearchUiState.Idle ||
            vibeState !is VibeSearchUiState.Idle ||
            recognitionState !is RecognitionUiState.Idle ||
            isListeningAudio
        if (isResultsMode || jumpBackItems.isNotEmpty()) {
            item(key = "jump_back_in") {
                val isResultsLoading = homeSearchState is HomeSearchUiState.Loading ||
                    vibeState is VibeSearchUiState.Loading ||
                    recognitionState is RecognitionUiState.Loading ||
                    recognitionState is RecognitionUiState.Listening ||
                    isListeningAudio

                val activeResultTracks = remember(homeSearchState, vibeState, recognitionState) {
                    when {
                        homeSearchState is HomeSearchUiState.Success -> homeSearchState.tracks
                        vibeState is VibeSearchUiState.Success -> vibeState.radioTracks
                        recognitionState is RecognitionUiState.Success ->
                            recognitionState.candidates.map { it.toTrackItem() }
                        else -> emptyList()
                    }
                }
                val emptyMessage = when {
                    homeSearchState is HomeSearchUiState.Empty -> homeSearchState.message
                    vibeState is VibeSearchUiState.Error -> vibeState.message
                    recognitionState is RecognitionUiState.Empty -> recognitionState.message
                    recognitionState is RecognitionUiState.Error -> recognitionState.message
                    else -> null
                }

                val activeTracks = if (isResultsMode) activeResultTracks else safeJumpBackTracks
                val activeItems = remember(activeTracks, isResultsMode, jumpBackItems) {
                    if (isResultsMode) {
                        activeTracks.map { t ->
                            CompactMediaItem(
                                id = t.id,
                                title = t.title,
                                subtitle = t.artist.ifBlank { "Music" },
                                coverUrl = t.coverUrl
                            )
                        }
                    } else {
                        jumpBackItems
                    }
                }

                CompactJumpGridSection(
                    title = if (isResultsMode) "Results" else "Jump Back In",
                    items = activeItems,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    isLoadingMore = if (isResultsMode) false else isLoadingMore,
                    isResultsMode = isResultsMode,
                    isResultsLoading = isResultsLoading,
                    loadingStatusText = if (isListeningAudio || recognitionState is RecognitionUiState.Listening) {
                        "Listening for music (${recordingSeconds}s)..."
                    } else {
                        "Finding matches..."
                    },
                    emptyResultsMessage = emptyMessage,
                    actionLabel = if (isResultsMode) "Clear" else "See all",
                    onActionClick = {
                        if (isResultsMode) {
                            onClearHomeResults()
                        } else if (safeJumpBackTracks.isNotEmpty()) {
                            onTrackSelect(safeJumpBackTracks.first(), safeJumpBackTracks)
                        }
                    },
                    onLoadMore = if (isResultsMode) null else onLoadMore,
                    onItemClick = { clicked ->
                        val found = activeTracks.firstOrNull { it.id == clicked.id && it.title == clicked.title }
                            ?: activeTracks.firstOrNull { it.title == clicked.title }
                        if (found != null) {
                            val startIdx = activeTracks.indexOf(found).coerceAtLeast(0)
                            val rotatedQueue = (activeTracks.drop(startIdx) + activeTracks.take(startIdx)).take(60)
                            onTrackSelect(found, rotatedQueue)
                        }
                    }
                )
            }
        } else {
            item(key = "home_feed_empty") {
                UnboundEmptyStateCard(
                    title = "No feed tracks loaded yet",
                    subtitle = "Tap Refresh above or search for a song, artist, or #vibe.",
                    compact = true
                )
            }
        }

        // 5. Made For Your Mood (Backend-driven 1:1 Infinite Mixes)
        if (mixCards.isNotEmpty()) {
            item(key = "made_for_mood") {
                YouTubeMixHorizontalDeckSection(
                    title = "Made For Your Mood",
                    subtitle = if (isYouTubeConnected) "Endless mixes from your history" else "Non-stop YouTube mixes",
                    cards = mixCards,
                    isLoadingMore = isLoadingMore,
                    actionLabel = "Shuffle Mix",
                    onActionClick = {
                        safeJumpBackTracks.randomOrNull()?.let(onStartRadio)
                    },
                    onLoadMore = onLoadMore,
                    onCardClick = { card ->
                        val backendMix = homeMixes.firstOrNull { it.id == card.id }
                        if (backendMix != null && backendMix.mixType == "account_mix") {
                            onMixClick(
                                MixDto(
                                    id = backendMix.id.removePrefix("mix:"),
                                    title = backendMix.title,
                                    subtitle = backendMix.subtitle,
                                    coverUrl = backendMix.coverUrl
                                )
                            )
                        } else {
                            val rawId = card.id.removePrefix("trackmix:")
                            val seed = safeJumpBackTracks.firstOrNull { it.id == rawId }
                                ?: backendMix?.takeIf { it.seedTitle.isNotBlank() }?.let {
                                    TrackItem(
                                        id = it.seedTrackId,
                                        title = it.seedTitle,
                                        artist = it.seedArtist,
                                        coverUrl = it.coverUrl
                                    )
                                }
                                ?: safeJumpBackTracks.firstOrNull { "Mix – ${it.title}" == card.title }
                            if (seed != null) {
                                onStartRadio(seed)
                            }
                        }
                    }
                )
            }
        }

        // 6. Trending Globally (Worldwide Singles)
        if (globalCards.isNotEmpty()) {
            item(key = "trending_globally") {
                TrendingHorizontalCarouselSection(
                    title = "Trending Globally",
                    subtitle = "Worldwide top singles • Updated daily",
                    items = globalCards,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    accentColor = UnboundPrimary,
                    actionLabel = "Play All",
                    onActionClick = {
                        globalSource.firstOrNull()?.let { first ->
                            onTrackSelect(first, globalSource)
                        }
                    },
                    onItemClick = { card ->
                        val found = globalSource.firstOrNull { it.id == card.id && it.title == card.title }
                            ?: globalSource.firstOrNull { it.title == card.title }
                        if (found != null) {
                            val startIdx = globalSource.indexOf(found).coerceAtLeast(0)
                            val rotatedQueue = (globalSource.drop(startIdx) + globalSource.take(startIdx)).take(60)
                            onTrackSelect(found, rotatedQueue)
                        }
                    }
                )
            }
        }

        // 7. Trending Regionally (Detected Country Singles)
        if (regionalCards.isNotEmpty()) {
            item(key = "trending_regionally") {
                TrendingHorizontalCarouselSection(
                    title = "Trending Regionally",
                    subtitle = "Top singles in $regionName",
                    items = regionalCards,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    accentColor = UnboundSecondary,
                    actionLabel = "Play All",
                    onActionClick = {
                        regionalSource.firstOrNull()?.let { first ->
                            onTrackSelect(first, regionalSource)
                        }
                    },
                    onItemClick = { card ->
                        val found = regionalSource.firstOrNull { it.id == card.id && it.title == card.title }
                            ?: regionalSource.firstOrNull { it.title == card.title }
                        if (found != null) {
                            val startIdx = regionalSource.indexOf(found).coerceAtLeast(0)
                            val rotatedQueue = (regionalSource.drop(startIdx) + regionalSource.take(startIdx)).take(60)
                            onTrackSelect(found, rotatedQueue)
                        }
                    }
                )
            }
        }
    }
}
