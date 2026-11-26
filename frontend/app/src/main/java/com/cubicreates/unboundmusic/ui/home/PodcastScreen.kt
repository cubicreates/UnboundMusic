/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: PodcastScreen.kt
 * Purpose: Clean, reusable Podcast Home screen mirroring MusicScreen frame-by-frame:
 *          - Music/Podcast switcher & compact greeting
 *          - Single Unified Search Bar (Text/Topic + Mic voice search)
 *          - Continue Listening <-> Results (4-row infinite-column snapping slider)
 *          - Featured Shows (1:1 infinite-scrolling YouTube-style deck)
 *          - Trending Globally (worldwide top podcast episodes carousel)
 *          - Trending Regionally (user country top podcast episodes carousel)
 * Subsystem: Home UI / Podcasts Experience
 */

package com.cubicreates.unboundmusic.ui.home

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.cubicreates.unboundmusic.data.HomeSection
import com.cubicreates.unboundmusic.data.PodcastEpisodeDto
import com.cubicreates.unboundmusic.data.PodcastShowDto
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
fun PodcastScreen(
    modifier: Modifier = Modifier,
    selectedSection: HomeSection = HomeSection.PODCASTS,
    onSectionSelected: (HomeSection) -> Unit = {},
    accountName: String? = null,
    isYouTubeConnected: Boolean = false,
    featuredShows: List<PodcastShowDto> = emptyList(),
    recentEpisodes: List<PodcastEpisodeDto> = emptyList(),
    topEpisodes: List<PodcastEpisodeDto> = emptyList(),
    globalEpisodes: List<PodcastEpisodeDto> = emptyList(),
    regionalEpisodes: List<PodcastEpisodeDto> = emptyList(),
    searchResults: List<PodcastEpisodeDto> = emptyList(),
    selectedShow: PodcastShowDto? = null,
    activeTopicQuery: String? = null,
    regionCode: String = "IN",
    regionName: String = "Your Region",
    isLoading: Boolean = false,
    errorMessage: String? = null,
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    onRefresh: () -> Unit = {},
    onTopicQuerySubmit: (String) -> Unit = {},
    onClearTopicQuery: () -> Unit = {},
    onVoiceSearchClick: () -> Unit = {},
    onShowClick: (PodcastShowDto) -> Unit = {},
    onCloseShow: () -> Unit = {},
    onEpisodePlay: (PodcastEpisodeDto) -> Unit = {}
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

    val continueEpisodes = remember(recentEpisodes, topEpisodes, regionalEpisodes, globalEpisodes) {
        (recentEpisodes + topEpisodes + regionalEpisodes + globalEpisodes)
            .distinctBy { it.id.ifBlank { it.title } }
    }
    val continueItems = remember(continueEpisodes) {
        continueEpisodes.map { ep ->
            CompactMediaItem(
                id = ep.id,
                title = ep.title,
                subtitle = ep.author.ifBlank { ep.showTitle.ifBlank { "Podcast" } },
                coverUrl = ep.effectiveThumbnail,
                progressFraction = ep.resumeFraction
            )
        }
    }

    // 1:1 Infinite Featured Shows carousel cards
    val showDeckCards = remember(featuredShows) {
        featuredShows.map { show ->
            val countText = if (show.episodeCount > 0) "${show.episodeCount} ep" else "Podcast Series"
            CuratedDeckCardData(
                id = show.id,
                tag = show.category.ifBlank { "SHOW" },
                title = show.title,
                description = "${show.author.ifBlank { "Creator" }} • $countText",
                footer = countText,
                coverUrl = show.thumbnailUrl
            )
        }
    }

    // Trending Globally (worldwide top podcast episodes)
    val globalSource = remember(globalEpisodes, topEpisodes) {
        globalEpisodes.ifEmpty { topEpisodes }
    }
    val globalCards = remember(globalSource) {
        globalSource.mapIndexed { idx, ep ->
            TrendingCardData(
                id = ep.id,
                rankLabel = "#${idx + 1} Global",
                title = ep.title,
                subtitle = ep.author.ifBlank { ep.showTitle.ifBlank { "Podcast" } },
                metaText = formatEpisodeDuration(ep.durationMs)
                    .ifBlank { ep.category.ifBlank { ep.releaseDate } },
                coverUrl = ep.effectiveThumbnail,
                isTopRank = idx < 3
            )
        }
    }

    // Trending Regionally (detected country's top podcast episodes)
    val regionalSource = remember(regionalEpisodes, topEpisodes, regionName) {
        regionalEpisodes.ifEmpty { topEpisodes }
    }
    val regionalCards = remember(regionalSource, regionCode, regionName) {
        regionalSource.mapIndexed { idx, ep ->
            TrendingCardData(
                id = ep.id,
                rankLabel = "#${idx + 1} $regionCode",
                title = ep.title,
                subtitle = ep.author.ifBlank { ep.showTitle.ifBlank { "Regional Podcast" } },
                metaText = formatEpisodeDuration(ep.durationMs)
                    .ifBlank { "Trending in $regionName" },
                coverUrl = ep.effectiveThumbnail,
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

        // 2. Compact Greeting + Refresh
        item(key = "greeting") {
            TelemetryGreetingSection(
                badgeText = "Audio Stories",
                greetingText = "$greetingPrefix, $displayName",
                isSyncing = isLoading,
                onSyncClick = onRefresh
            )
        }

        // 3. Single Unified Search Bar (Podcast Search + Voice Mic)
        item(key = "unified_podcast_search_bar") {
            HomeUnifiedSearchBar(
                isSearching = isLoading && !activeTopicQuery.isNullOrBlank(),
                placeholder = "Search shows, topics, or episodes...",
                showShazamButton = false,
                onSubmitSearch = onTopicQuerySubmit,
                onSingLyricsClick = onVoiceSearchClick,
                onClearResults = {
                    onClearTopicQuery()
                    onCloseShow()
                }
            )
        }

        // 4. Multifunctional "Continue Listening" <-> "Results" (4-Row Infinite Slider)
        val isResultsMode = selectedShow != null || !activeTopicQuery.isNullOrBlank()
        if (isResultsMode || continueItems.isNotEmpty()) {
            item(key = "continue_listening") {
                val activeResultEpisodes = remember(selectedShow, activeTopicQuery, searchResults) {
                    when {
                        selectedShow != null -> selectedShow.episodes
                        !activeTopicQuery.isNullOrBlank() -> searchResults
                        else -> emptyList()
                    }
                }
                val emptyMsg = when {
                    !errorMessage.isNullOrBlank() && activeResultEpisodes.isEmpty() -> errorMessage
                    selectedShow != null && activeResultEpisodes.isEmpty() && !isLoading ->
                        "No episodes loaded for \"${selectedShow.title}\"."
                    !activeTopicQuery.isNullOrBlank() && activeResultEpisodes.isEmpty() && !isLoading ->
                        "No podcast matches found for \"$activeTopicQuery\"."
                    else -> null
                }

                val activePool = if (isResultsMode) activeResultEpisodes else continueEpisodes
                val activeItems = remember(activePool, isResultsMode, continueItems) {
                    if (isResultsMode) {
                        activePool.map { ep ->
                            CompactMediaItem(
                                id = ep.id,
                                title = ep.title,
                                subtitle = ep.author.ifBlank { ep.showTitle.ifBlank { "Podcast" } },
                                coverUrl = ep.effectiveThumbnail,
                                progressFraction = ep.resumeFraction
                            )
                        }
                    } else {
                        continueItems
                    }
                }

                CompactJumpGridSection(
                    title = if (isResultsMode) "Results" else "Continue Listening",
                    items = activeItems,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    isLoadingMore = if (isResultsMode) false else isLoading,
                    isResultsMode = isResultsMode,
                    isResultsLoading = isResultsMode && isLoading && activeItems.isEmpty(),
                    loadingStatusText = if (selectedShow != null) {
                        "Loading episodes from ${selectedShow.title}..."
                    } else {
                        "Finding podcast matches..."
                    },
                    emptyResultsMessage = emptyMsg,
                    actionLabel = if (isResultsMode) "Clear" else "Refresh",
                    onActionClick = {
                        if (isResultsMode) {
                            onClearTopicQuery()
                            onCloseShow()
                        } else {
                            onRefresh()
                        }
                    },
                    onLoadMore = if (isResultsMode) null else onRefresh,
                    onItemClick = { clicked ->
                        val ep = activePool.firstOrNull { it.id == clicked.id && it.title == clicked.title }
                            ?: activePool.firstOrNull { it.title == clicked.title }
                        if (ep != null) onEpisodePlay(ep)
                    }
                )
            }
        }

        // 5. Featured Podcast Shows (1:1 Infinite Carousel)
        if (showDeckCards.isNotEmpty()) {
            item(key = "featured_shows") {
                YouTubeMixHorizontalDeckSection(
                    title = "Featured Shows",
                    subtitle = "Long-form conversations & series",
                    cards = showDeckCards,
                    isLoadingMore = isLoading,
                    badgeLabel = "Show",
                    actionLabel = "Browse",
                    onActionClick = {
                        featuredShows.firstOrNull()?.let(onShowClick)
                    },
                    onLoadMore = onRefresh,
                    onCardClick = { card ->
                        val show = featuredShows.firstOrNull { it.id == card.id }
                        if (show != null) onShowClick(show)
                    }
                )
            }
        }

        // 6. Trending Globally (Worldwide Top Podcast Episodes)
        if (globalCards.isNotEmpty()) {
            item(key = "podcast_trending_globally") {
                TrendingHorizontalCarouselSection(
                    title = "Trending Globally",
                    subtitle = "Worldwide top episodes • Updated daily",
                    items = globalCards,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    accentColor = UnboundPrimary,
                    actionLabel = "Play Top",
                    onActionClick = {
                        globalSource.firstOrNull()?.let(onEpisodePlay)
                    },
                    onItemClick = { card ->
                        val ep = globalSource.firstOrNull { it.id == card.id && it.title == card.title }
                            ?: globalSource.firstOrNull { it.title == card.title }
                        if (ep != null) onEpisodePlay(ep)
                    }
                )
            }
        }

        // 7. Trending Regionally (Detected Country Top Podcasts)
        if (regionalCards.isNotEmpty()) {
            item(key = "podcast_trending_regionally") {
                TrendingHorizontalCarouselSection(
                    title = "Trending Regionally",
                    subtitle = "Top podcasts in $regionName",
                    items = regionalCards,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    accentColor = UnboundSecondary,
                    actionLabel = "Play Top",
                    onActionClick = {
                        regionalSource.firstOrNull()?.let(onEpisodePlay)
                    },
                    onItemClick = { card ->
                        val ep = regionalSource.firstOrNull { it.id == card.id && it.title == card.title }
                            ?: regionalSource.firstOrNull { it.title == card.title }
                        if (ep != null) onEpisodePlay(ep)
                    }
                )
            }
        }
    }
}

private fun formatEpisodeDuration(durationMs: Long): String {
    if (durationMs <= 0L) return ""
    val totalMin = durationMs / 60000L
    val hrs = totalMin / 60L
    val mins = totalMin % 60L
    return if (hrs > 0) "${hrs}h ${mins}m" else "${mins}m"
}
