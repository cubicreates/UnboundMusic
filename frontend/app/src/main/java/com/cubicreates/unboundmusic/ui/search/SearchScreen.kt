/*
 * Package: com.cubicreates.unboundmusic.ui.search
 * File: SearchScreen.kt
 * Purpose: Elevated Discovery, Autocomplete, History & Multi-Select Search Hub for Unbound Music.
 * Subsystem: Discovery / Search UI
 */

package com.cubicreates.unboundmusic.ui.search

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.VibeResult
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary
import kotlinx.coroutines.delay

private val trendingVibes = listOf(
    "Rainy Midnight Coding",
    "Heavy Deadlift Phonk",
    "Sunny Morning Acoustic",
    "Late Night Lo-Fi",
    "Cyberpunk Drive",
    "Deep Focus Flow"
)

private const val IMG_NEON_NOIR = "https://lh3.googleusercontent.com/aida-public/AB6AXuAYApkR1WLZQ1hOJB95_iBd2_6cuBHZ5VbNOvQ_hcNKz3gsZLAuAA6yPer-cv4wpCYpLlw68Hxd1W5C7vYY2UC06lB5ekBMo_nNZokBGdAYqpVtQupurMBSPsqk4e8h0mZN8oEPMAwaAgWr7ERuusrXszfIgYH5lETzYbT9eVnm0PQnIvgH7KIfCGgn6dcFzlWxtoheMs68tYehJtQm41jdKTmPMk5DLyHD6t14YXR9Zny59FV8fN8pRw"
private const val IMG_VELVET_RNB = "https://lh3.googleusercontent.com/aida-public/AB6AXuBSJnsYO276b6VZ7n7LFagIeKmKHHuG6IEVYjF_pjp2JIV8dHBs80dkrCjjG626oVAhRoT0pLENqVIKiLZqeF_xmuxrIfZS54cHPQBRIrOj3x6_R6QjYDWTeMDb8OwPV9OfoUaFvLymzUkf0ghmIl8TB3mcfe8aGHGD2jMsGY7s6Rz7nhFTn69aLj9L8qY1RIP1ose4cRhb7qkN1d2shozxVLqWbD_hqAa-k6OZvsBgtEqdBN832OB5WA"

enum class SearchCategory(val label: String, val apiParam: String) {
    ALL("All", "all"),
    SONGS("Songs", "song"),
    ALBUMS("Albums", "album"),
    ARTISTS("Artists", "artist"),
    PLAYLISTS("Playlists", "playlist"),
    PODCASTS("Podcasts", "podcast");

    companion object {
        val MUSIC = SONGS
    }
}

/**
 * 4-State Search UI Machine mirroring SimpMusic's state architecture elevated with Unbound design.
 */
enum class SearchUIType {
    EMPTY,
    SEARCH_HISTORY,
    SEARCH_SUGGESTIONS,
    SEARCH_RESULTS
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    searchResults: List<TrackItem> = emptyList(),
    isSearching: Boolean = false,
    vibeState: VibeSearchUiState = VibeSearchUiState.Idle,
    selectedCategory: SearchCategory = SearchCategory.ALL,
    chartTracks: List<TrackItem> = emptyList(),
    searchSuggestions: List<String> = emptyList(),
    searchHistory: List<String> = emptyList(),
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    onCategorySelected: (SearchCategory) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onSearchSubmit: (String) -> Unit = {},
    onSearchHistoryItemRemoved: (String) -> Unit = {},
    onSearchHistoryCleared: () -> Unit = {},
    onVibeSubmit: (String) -> Unit = {},
    onListenToSurroundings: () -> Unit = {},
    onVibeTagClick: (String) -> Unit = {},
    onGenreCardClick: (String) -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onAlbumClick: (id: String, title: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onArtistClick: (artistName: String) -> Unit = {},
    onPlayNextBatch: (List<TrackItem>) -> Unit = {},
    onAddToQueueBatch: (List<TrackItem>) -> Unit = {},
    onDownloadBatch: (List<TrackItem>) -> Unit = {},
    onPlayNextSingle: (TrackItem) -> Unit = {},
    onAddToQueueSingle: (TrackItem) -> Unit = {},
    onDownloadSingle: (TrackItem) -> Unit = {}
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var isFocused by remember { mutableStateOf(false) }
    var isSearchSubmitted by rememberSaveable { mutableStateOf(false) }
    var isListening by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    // Multi-Selection State for Search Results
    var isSelectionMode by rememberSaveable { mutableStateOf(false) }
    val selectedTracks = remember { mutableStateListOf<TrackItem>() }

    // Placeholder animation
    val placeholderOptions = remember {
        listOf(
            "Search songs, artists, albums...",
            "Search late night vibes...",
            "Search cyberpunk phonk...",
            "Search lo-fi chill beats...",
            "Search acoustic morning...",
            "Search popular podcasts..."
        )
    }
    var currentPlaceholderIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(isFocused) {
        while (!isFocused) {
            delay(3200)
            currentPlaceholderIndex = (currentPlaceholderIndex + 1) % placeholderOptions.size
        }
    }

    // Determine 4-State UI
    val searchUIType = when {
        isFocused && searchQuery.isNotBlank() && !isSearchSubmitted -> SearchUIType.SEARCH_SUGGESTIONS
        isFocused && searchQuery.isBlank() -> SearchUIType.SEARCH_HISTORY
        isSearchSubmitted || (!isFocused && searchQuery.isNotBlank()) -> SearchUIType.SEARCH_RESULTS
        else -> SearchUIType.EMPTY
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Search Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFF1B1B1F),
                border = BorderStroke(
                    1.dp,
                    if (isFocused) UnboundPrimary.copy(alpha = 0.8f) else BorderGlass
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = if (isFocused) UnboundPrimary else OnSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            AnimatedContent(
                                targetState = placeholderOptions[currentPlaceholderIndex],
                                transitionSpec = {
                                    (slideInVertically { height -> height } + fadeIn()).togetherWith(
                                        slideOutVertically { height -> -height } + fadeOut()
                                    )
                                },
                                label = "placeholder_animation"
                            ) { text ->
                                Text(
                                    text = text,
                                    color = OnSurfaceVariant.copy(alpha = 0.55f),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                isSearchSubmitted = false
                                onSearchQueryChanged(it)
                            },
                            textStyle = TextStyle(
                                color = OnSurface,
                                fontSize = 14.sp
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(
                                onSearch = {
                                    if (searchQuery.isNotBlank()) {
                                        isSearchSubmitted = true
                                        focusManager.clearFocus()
                                        onSearchSubmit(searchQuery)
                                    }
                                }
                            ),
                            cursorBrush = SolidColor(UnboundPrimary),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { isFocused = it.isFocused }
                        )
                    }

                    if (isSearching || vibeState is VibeSearchUiState.Loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = UnboundPrimary,
                            strokeWidth = 2.dp
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                isSearchSubmitted = true
                                focusManager.clearFocus()
                                onSearchSubmit(searchQuery)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Submit Search",
                                tint = UnboundPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                searchQuery = ""
                                isSearchSubmitted = false
                                onSearchQueryChanged("")
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 2. State-driven Body Area
            Crossfade(
                targetState = searchUIType,
                animationSpec = tween(220),
                label = "search_state_crossfade",
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { uiState ->
                when (uiState) {
                    SearchUIType.EMPTY -> {
                        EmptyDiscoveryContent(
                            isListening = isListening,
                            onListenToggle = {
                                isListening = !isListening
                                onListenToSurroundings()
                            },
                            selectedCategory = selectedCategory,
                            chartTracks = chartTracks,
                            onCategorySelected = onCategorySelected,
                            onVibeTagClick = { tag ->
                                searchQuery = tag
                                onVibeTagClick(tag)
                            },
                            onGenreCardClick = onGenreCardClick,
                            onPodcastClick = { name ->
                                searchQuery = name
                                isSearchSubmitted = true
                                focusManager.clearFocus()
                                onSearchSubmit(name)
                            },
                            onTrackSelect = onTrackSelect
                        )
                    }

                    SearchUIType.SEARCH_HISTORY -> {
                        SearchHistoryContent(
                            historyItems = searchHistory,
                            onHistoryItemClick = { query ->
                                searchQuery = query
                                isSearchSubmitted = true
                                focusManager.clearFocus()
                                onSearchSubmit(query)
                            },
                            onHistoryItemRemove = onSearchHistoryItemRemoved,
                            onClearAll = onSearchHistoryCleared,
                            onVibeSuggestionClick = { vibe ->
                                searchQuery = vibe
                                isSearchSubmitted = true
                                focusManager.clearFocus()
                                onSearchSubmit(vibe)
                            }
                        )
                    }

                    SearchUIType.SEARCH_SUGGESTIONS -> {
                        SearchSuggestionsContent(
                            query = searchQuery,
                            suggestions = searchSuggestions,
                            onSuggestionClick = { suggestion ->
                                searchQuery = suggestion
                                isSearchSubmitted = true
                                focusManager.clearFocus()
                                onSearchSubmit(suggestion)
                            },
                            onRefineArrowClick = { suggestion ->
                                searchQuery = suggestion
                                onSearchQueryChanged(suggestion)
                                focusRequester.requestFocus()
                            }
                        )
                    }

                    SearchUIType.SEARCH_RESULTS -> {
                        SearchResultsContent(
                            searchQuery = searchQuery,
                            searchResults = if (vibeState is VibeSearchUiState.Success) vibeState.radioTracks else searchResults,
                            vibeState = vibeState,
                            selectedCategory = selectedCategory,
                            currentTrackId = currentTrackId,
                            isPlaying = isPlaying,
                            isSearching = isSearching,
                            isSelectionMode = isSelectionMode,
                            selectedTracks = selectedTracks,
                            onCategorySelected = onCategorySelected,
                            onToggleSelectionMode = {
                                isSelectionMode = !isSelectionMode
                                if (!isSelectionMode) selectedTracks.clear()
                            },
                            onSelectAll = { allTracks ->
                                selectedTracks.clear()
                                selectedTracks.addAll(allTracks)
                            },
                            onClearSelection = { selectedTracks.clear() },
                            onToggleTrackSelection = { track ->
                                if (selectedTracks.any { it.id == track.id }) {
                                    selectedTracks.removeAll { it.id == track.id }
                                } else {
                                    selectedTracks.add(track)
                                }
                            },
                            onTrackClick = { track, allTracks ->
                                val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_")
                                val isArtist = track.itemType.equals("artist", ignoreCase = true) || track.browseId.startsWith("UC")
                                val isPlaylist = track.itemType.equals("playlist", ignoreCase = true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL")
                                when {
                                    isAlbum -> onAlbumClick(track.browseId.ifBlank { track.id }, track.title, track.coverUrl)
                                    isArtist -> onArtistClick(track.artist.ifBlank { track.title })
                                    isPlaylist -> onAlbumClick(track.browseId.ifBlank { track.id }, track.title, track.coverUrl)
                                    else -> onTrackSelect(track, allTracks)
                                }
                            },
                            onPlayNextBatch = {
                                onPlayNextBatch(selectedTracks.toList())
                                isSelectionMode = false
                                selectedTracks.clear()
                            },
                            onAddToQueueBatch = {
                                onAddToQueueBatch(selectedTracks.toList())
                                isSelectionMode = false
                                selectedTracks.clear()
                            },
                            onDownloadBatch = {
                                onDownloadBatch(selectedTracks.toList())
                                isSelectionMode = false
                                selectedTracks.clear()
                            },
                            onPlayNextSingle = onPlayNextSingle,
                            onAddToQueueSingle = onAddToQueueSingle,
                            onDownloadSingle = onDownloadSingle
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 1. EMPTY DISCOVERY CONTENT
// ==========================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyDiscoveryContent(
    isListening: Boolean,
    onListenToggle: () -> Unit,
    selectedCategory: SearchCategory,
    chartTracks: List<TrackItem>,
    onCategorySelected: (SearchCategory) -> Unit,
    onVibeTagClick: (String) -> Unit,
    onGenreCardClick: (String) -> Unit,
    onPodcastClick: (String) -> Unit,
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Category Pills
        CategoryChipsRow(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Hero Title
        Text(
            text = "Discover",
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            style = TextStyle(
                brush = Brush.horizontalGradient(
                    colors = listOf(UnboundPrimary, UnboundTertiary)
                )
            )
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "AI Vibe Matching & Live YouTube Music Catalog",
            fontSize = 12.sp,
            color = OnSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(18.dp))

        // Listen to Surroundings Action (Ambient Shazam)
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable { onListenToggle() },
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF202025),
            border = BorderStroke(1.dp, if (isListening) UnboundPrimary else BorderGlass)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(UnboundPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Shazam",
                        tint = OnPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Text(
                    text = if (isListening) "Listening to surroundings..." else "Listen to Surroundings",
                    color = if (isListening) UnboundPrimary else OnSurface,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Trending Vibes Chips
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "TRENDING VIBES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                trendingVibes.forEach { vibe ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF1E1E22),
                        border = BorderStroke(1.dp, BorderGlass),
                        modifier = Modifier.clickable { onVibeTagClick(vibe) }
                    ) {
                        Text(
                            text = vibe,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = OnSurface,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Featured Vibe Cards
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "FEATURED MOODS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                FeaturedVibeCard(
                    title = "Neon Noir",
                    description = "Moody synths & midnight driving",
                    imageUrl = IMG_NEON_NOIR,
                    modifier = Modifier.weight(1f),
                    onClick = { onGenreCardClick("Neon Noir") }
                )

                FeaturedVibeCard(
                    title = "Velvet R&B",
                    description = "Silky vocals & deep 808s",
                    imageUrl = IMG_VELVET_RNB,
                    modifier = Modifier.weight(1f),
                    onClick = { onGenreCardClick("Velvet R&B") }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Popular Podcasts or Trending Charts
        if (selectedCategory == SearchCategory.PODCASTS) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "POPULAR PODCAST SHOWS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                val popularPodcasts = listOf(
                    "The Joe Rogan Experience" to "Interviews with thought leaders, comedians, and experts.",
                    "Huberman Lab" to "Science-based tools for everyday health and performance.",
                    "Lex Fridman Podcast" to "Deep conversations on AI, science, and philosophy.",
                    "Stuff You Should Know" to "How everything around us actually works."
                )

                popularPodcasts.forEach { (name, desc) ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clickable { onPodcastClick(name) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFF1E1E22),
                        border = BorderStroke(1.dp, BorderGlass)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF282830)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Radio,
                                    contentDescription = "Podcast",
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = name,
                                    color = OnSurface,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = desc,
                                    color = OnSurfaceVariant,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Curated Top Tracks Feed
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "TOP CHARTS & TRENDING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                chartTracks.take(8).forEach { track ->
                    SearchResultItem(
                        track = track,
                        isPlaying = false,
                        isCurrentTrack = false,
                        isSelectionMode = false,
                        isSelected = false,
                        onClick = { onTrackSelect(track, chartTracks) }
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                }
            }
        }
    }
}

// ==========================================
// 2. SEARCH HISTORY CONTENT
// ==========================================

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchHistoryContent(
    historyItems: List<String>,
    onHistoryItemClick: (String) -> Unit,
    onHistoryItemRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    onVibeSuggestionClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT SEARCHES",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.5.sp
                )

                if (historyItems.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text(
                            text = "Clear All",
                            color = UnboundPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        if (historyItems.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = OnSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No recent searches yet",
                        fontSize = 14.sp,
                        color = OnSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = "TRY SEARCHING FOR",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.5.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        trendingVibes.take(4).forEach { vibe ->
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF1E1E22),
                                border = BorderStroke(1.dp, BorderGlass),
                                modifier = Modifier.clickable { onVibeSuggestionClick(vibe) }
                            ) {
                                Text(
                                    text = vibe,
                                    fontSize = 12.sp,
                                    color = OnSurface,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        }
                    }
                }
            }
        } else {
            items(historyItems, key = { "hist_$it" }) { query ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onHistoryItemClick(query) },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF1B1B1F),
                    border = BorderStroke(1.dp, BorderGlass)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )

                        Spacer(modifier = Modifier.width(12.dp))

                        Text(
                            text = query,
                            fontSize = 14.sp,
                            color = OnSurface,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        IconButton(
                            onClick = { onHistoryItemRemove(query) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove",
                                tint = OnSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 3. SEARCH SUGGESTIONS CONTENT
// ==========================================

@Composable
private fun SearchSuggestionsContent(
    query: String,
    suggestions: List<String>,
    onSuggestionClick: (String) -> Unit,
    onRefineArrowClick: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        item {
            Text(
                text = "SUGGESTIONS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )
        }

        items(suggestions, key = { "sug_$it" }) { suggestion ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onSuggestionClick(suggestion) },
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1B1B1F),
                border = BorderStroke(1.dp, BorderGlass)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(18.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    // Text with query highlight
                    val annotatedString = buildAnnotatedString {
                        val lowerSuggestion = suggestion.lowercase()
                        val lowerQuery = query.lowercase()
                        val matchIndex = lowerSuggestion.indexOf(lowerQuery)
                        if (matchIndex >= 0) {
                            append(suggestion.substring(0, matchIndex))
                            withStyle(style = SpanStyle(color = UnboundPrimary, fontWeight = FontWeight.Bold)) {
                                append(suggestion.substring(matchIndex, matchIndex + query.length))
                            }
                            append(suggestion.substring(matchIndex + query.length))
                        } else {
                            append(suggestion)
                        }
                    }

                    Text(
                        text = annotatedString,
                        fontSize = 14.sp,
                        color = OnSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Refine query diagonal arrow (puts suggestion in box for rapid editing)
                    IconButton(
                        onClick = { onRefineArrowClick(suggestion) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.NorthEast,
                            contentDescription = "Refine Query",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 4. SEARCH RESULTS CONTENT (With Multi-Select)
// ==========================================

@Composable
private fun SearchResultsContent(
    searchQuery: String,
    searchResults: List<TrackItem>,
    vibeState: VibeSearchUiState,
    selectedCategory: SearchCategory,
    currentTrackId: String,
    isPlaying: Boolean,
    isSearching: Boolean,
    isSelectionMode: Boolean,
    selectedTracks: List<TrackItem>,
    onCategorySelected: (SearchCategory) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onSelectAll: (List<TrackItem>) -> Unit,
    onClearSelection: () -> Unit,
    onToggleTrackSelection: (TrackItem) -> Unit,
    onTrackClick: (TrackItem, List<TrackItem>) -> Unit,
    onPlayNextBatch: () -> Unit,
    onAddToQueueBatch: () -> Unit,
    onDownloadBatch: () -> Unit,
    onPlayNextSingle: (TrackItem) -> Unit,
    onAddToQueueSingle: (TrackItem) -> Unit,
    onDownloadSingle: (TrackItem) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Category Pills
        CategoryChipsRow(
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Vibe AI Card (if active)
        if (vibeState is VibeSearchUiState.Success) {
            val vr = vibeState.vibeResult
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1E1E22),
                border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.45f))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "VIBE: \"${vr.originalPrompt}\"",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = UnboundPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = UnboundPrimary.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = "${vr.suggestedBpm} BPM",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = UnboundPrimary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        vr.targetGenres.take(3).forEach { g ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF282830)
                            ) {
                                Text(
                                    text = g,
                                    fontSize = 10.sp,
                                    color = OnSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Section Title & Multi-Select Toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (vibeState is VibeSearchUiState.Success) "VIBE RADIO" else "RESULTS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            if (searchResults.isNotEmpty()) {
                TextButton(onClick = onToggleSelectionMode) {
                    Text(
                        text = if (isSelectionMode) "Done" else "Select",
                        color = UnboundPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Selection Actions Bar (if in selection mode)
        AnimatedVisibility(visible = isSelectionMode) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF222228),
                border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${selectedTracks.size} Selected",
                        color = OnSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = { onSelectAll(searchResults) }) {
                            Text("All", fontSize = 12.sp, color = UnboundPrimary)
                        }
                        TextButton(onClick = onClearSelection) {
                            Text("Clear", fontSize = 12.sp, color = OnSurfaceVariant)
                        }
                    }
                }
            }
        }

        // Search Results List
        if (searchResults.isEmpty() && !isSearching) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No results found for \"$searchQuery\"",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                itemsIndexed(searchResults, key = { index, track -> "${track.id}_${track.title}_$index" }) { _, track ->
                    val isSelected = selectedTracks.any { it.id == track.id }
                    val isCurrent = track.id.isNotBlank() && track.id == currentTrackId

                    SearchResultItem(
                        track = track,
                        isPlaying = isPlaying,
                        isCurrentTrack = isCurrent,
                        isSelectionMode = isSelectionMode,
                        isSelected = isSelected,
                        onClick = {
                            if (isSelectionMode) {
                                onToggleTrackSelection(track)
                            } else {
                                onTrackClick(track, searchResults)
                            }
                        },
                        onPlayNext = { onPlayNextSingle(track) },
                        onAddToQueue = { onAddToQueueSingle(track) },
                        onDownload = { onDownloadSingle(track) }
                    )
                }
            }
        }

        // Floating Batch Actions Strip (if in selection mode and tracks selected)
        AnimatedVisibility(visible = isSelectionMode && selectedTracks.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF1E1E24),
                border = BorderStroke(1.dp, UnboundPrimary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onPlayNextBatch) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play Next", color = UnboundPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = onAddToQueueBatch) {
                        Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Queue", color = UnboundPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    TextButton(onClick = onDownloadBatch) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = UnboundPrimary, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download", color = UnboundPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ==========================================
// SHARED COMPONENTS
// ==========================================

@Composable
private fun CategoryChipsRow(
    selectedCategory: SearchCategory,
    onCategorySelected: (SearchCategory) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchCategory.values().forEach { cat ->
            val isSelected = cat == selectedCategory
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = if (isSelected) UnboundPrimary else Color(0xFF1E1E22),
                border = if (isSelected) null else BorderStroke(1.dp, BorderGlass),
                modifier = Modifier.clickable { onCategorySelected(cat) }
            ) {
                Text(
                    text = cat.label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color.Black else OnSurface,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    track: TrackItem,
    isPlaying: Boolean,
    isCurrentTrack: Boolean,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit = {},
    onAddToQueue: () -> Unit = {},
    onDownload: () -> Unit = {}
) {
    val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_")
    val isArtist = track.itemType.equals("artist", ignoreCase = true) || track.browseId.startsWith("UC")
    val isPlaylist = track.itemType.equals("playlist", ignoreCase = true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL")
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) UnboundPrimary.copy(alpha = 0.12f) else Color(0xFF1A1A1E),
        border = BorderStroke(
            1.dp,
            when {
                isSelected -> UnboundPrimary
                isCurrentTrack -> UnboundPrimary.copy(alpha = 0.6f)
                isAlbum -> UnboundPrimary.copy(alpha = 0.25f)
                isArtist -> Color(0xFF81C784).copy(alpha = 0.25f)
                isPlaylist -> Color(0xFFBA68C8).copy(alpha = 0.25f)
                else -> BorderGlass
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) UnboundPrimary else OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(8.dp))
                    .background(Color(0xFF282830)),
                contentAlignment = Alignment.Center
            ) {
                if (track.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isAlbum -> Icons.Default.Album
                            isArtist -> Icons.Default.Person
                            isPlaylist -> Icons.AutoMirrored.Filled.QueueMusic
                            else -> Icons.Default.MusicNote
                        },
                        contentDescription = null,
                        tint = when {
                            isArtist -> Color(0xFF81C784)
                            isPlaylist -> Color(0xFFBA68C8)
                            else -> UnboundPrimary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }

                if (isCurrentTrack && isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        EqualizerWaveBars(isPlaying = true)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentTrack) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentTrack) UnboundPrimary else OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isAlbum) {
                        BadgePill("ALBUM", UnboundPrimary)
                    } else if (isArtist) {
                        BadgePill("ARTIST", Color(0xFF81C784))
                    } else if (isPlaylist) {
                        BadgePill("PLAYLIST", Color(0xFFBA68C8))
                    }

                    val subtitle = buildString {
                        if (track.artist.isNotBlank()) append(track.artist)
                        if (track.durationMs > 0) {
                            if (isNotEmpty()) append(" • ")
                            val totalSec = track.durationMs / 1000
                            val m = totalSec / 60
                            val s = totalSec % 60
                            append(String.format("%d:%02d", m, s))
                        }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(
                            text = subtitle,
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (!isSelectionMode) {
                if (isAlbum || isArtist || isPlaylist) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "View",
                        tint = when {
                            isArtist -> Color(0xFF81C784)
                            isPlaylist -> Color(0xFFBA68C8)
                            else -> UnboundPrimary
                        },
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Options",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Play Next") },
                                onClick = {
                                    showMenu = false
                                    onPlayNext()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Add to Queue") },
                                onClick = {
                                    showMenu = false
                                    onAddToQueue()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Download MP3") },
                                onClick = {
                                    showMenu = false
                                    onDownload()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.18f)
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = color,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}

@Composable
private fun FeaturedVibeCard(
    title: String,
    description: String,
    imageUrl: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .aspectRatio(1.2f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFF1E1E22),
        border = BorderStroke(1.dp, BorderGlass)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            startY = 50f
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
            ) {
                Text(
                    text = title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = description,
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.75f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EqualizerWaveBars(isPlaying: Boolean) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom,
        modifier = Modifier.height(14.dp)
    ) {
        val transition = rememberInfiniteTransition(label = "eq")
        val heights = listOf(
            transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(400, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar1"
            ),
            transition.animateFloat(
                initialValue = 0.8f,
                targetValue = 0.2f,
                animationSpec = infiniteRepeatable(
                    animation = tween(550, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar2"
            ),
            transition.animateFloat(
                initialValue = 0.4f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(350, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "bar3"
            )
        )

        heights.forEach { h ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (isPlaying) (14.dp * h.value).coerceAtLeast(3.dp) else 4.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(UnboundPrimary)
            )
        }
    }
}
