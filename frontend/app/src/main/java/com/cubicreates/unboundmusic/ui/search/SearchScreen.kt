/*
 * Package: com.cubicreates.unboundmusic.ui.search
 * File: SearchScreen.kt
 * Purpose: High-performance Discovery & Search Hub for Unbound Music with instant search results and genre browsing.
 * Subsystem: Discovery / Search UI
 */

package com.cubicreates.unboundmusic.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.VibeResult
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

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
private const val IMG_MINIMAL_TECHNO = "https://lh3.googleusercontent.com/aida-public/AB6AXuAnosEC-gnWsCAmEWPnFxuHS2fKqzJpXbYP2Te8W67oJwj0Pr_tECi8sJ2HCNCOeT4n6WkRuO0OFttj8LL-oU0jw1jr2FJkFUEvQwR7V8c48MrfoIUsR3Ns8H6UEiOVxxpPEZ4jXP4_7EFwVd3RF0HIFlEnVyjEZi0Gm6QbIwe0N7Oua6_D2FlOQpb5468cLAkpaD6eBJm3W0J5RNzKGmf4ute3p-m0Hq8_QA0HG-YdvdnPJ3rQlKAxHQ"

enum class SearchCategory(val label: String, val apiParam: String) {
    ALL("All", "all"),
    MUSIC("Music", "music"),
    PODCASTS("Podcasts", "podcast")
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
    onCategorySelected: (SearchCategory) -> Unit = {},
    onSearchQueryChanged: (String) -> Unit = {},
    onVibeSubmit: (String) -> Unit = {},
    onListenToSurroundings: () -> Unit = {},
    onVibeTagClick: (String) -> Unit = {},
    onGenreCardClick: (String) -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> }
) {
    var searchQuery by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 24.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Search Hero Section
            Text(
                text = "Discover",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(UnboundPrimary, UnboundTertiary)
                    )
                )
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Offline Edge AI Vibe Search & YouTube Music catalog",
                fontSize = 13.sp,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Vibe AI Search Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF222222),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383838))
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
                        tint = UnboundPrimary,
                        modifier = Modifier.size(22.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search songs, artists, albums, or vibes...",
                                color = OnSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
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
                                        onSearchQueryChanged(searchQuery)
                                    }
                                }
                            ),
                            cursorBrush = SolidColor(UnboundPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
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
                                onVibeSubmit(searchQuery)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Run Vibe AI",
                                tint = UnboundPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                searchQuery = ""
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

            Spacer(modifier = Modifier.height(12.dp))

            // 3. Category Filter Pills: All | Music | Podcasts
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchCategory.values().forEach { cat ->
                    val isSelected = cat == selectedCategory
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) UnboundPrimary else Color(0xFF242424),
                        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383838)),
                        modifier = Modifier.clickable {
                            onCategorySelected(cat)
                        }
                    ) {
                        Text(
                            text = cat.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color.Black else OnSurface,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Vibe AI Result Card (if active)
            if (vibeState is VibeSearchUiState.Success) {
                val vr = vibeState.vibeResult
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1E1E),
                    border = androidx.compose.foundation.BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.4f))
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
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
                                overflow = TextOverflow.Ellipsis
                            )

                            // BPM Badge
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = UnboundPrimary.copy(alpha = 0.15f)
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

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Energy level pill
                            val energyColor = when (vr.energyLevel.uppercase()) {
                                "INTENSE" -> Color(0xFFE57373)
                                "HIGH" -> Color(0xFFFFB74D)
                                "CHILL" -> Color(0xFF64B5F6)
                                else -> Color(0xFF81C784)
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = energyColor.copy(alpha = 0.2f)
                            ) {
                                Text(
                                    text = "ENERGY: ${vr.energyLevel}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = energyColor,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }

                            vr.targetGenres.take(2).forEach { g ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = Color(0xFF2A2A2A)
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

            // Content Area: Search Results OR Discovery Feed
            val displayTracks = when {
                vibeState is VibeSearchUiState.Success -> vibeState.radioTracks
                else -> searchResults
            }

            if (searchQuery.isNotBlank() || displayTracks.isNotEmpty()) {
                Text(
                    text = if (vibeState is VibeSearchUiState.Success) "VIBE RADIO TRACKS" else "SEARCH RESULTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.1.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                )

                if (displayTracks.isEmpty() && !isSearching && vibeState !is VibeSearchUiState.Loading) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No tracks found for \"$searchQuery\"",
                            color = OnSurfaceVariant,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(displayTracks, key = { index, track -> "${track.id}_${track.title}_$index" }) { _, track ->
                            SearchResultItem(
                                track = track,
                                onClick = { onTrackSelect(track, displayTracks) }
                            )
                        }
                    }
                }
            } else {
                if (selectedCategory == SearchCategory.MUSIC) {
                    // Category: Music -> Curated Top Charts & Trending Hits
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        Text(
                            text = "TOP CHARTS & TRENDING MUSIC",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 0.1.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        if (chartTracks.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                itemsIndexed(chartTracks, key = { index, track -> "chart_${track.id}_$index" }) { _, track ->
                                    SearchResultItem(
                                        track = track,
                                        onClick = { onTrackSelect(track, chartTracks) }
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = UnboundPrimary, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                } else if (selectedCategory == SearchCategory.PODCASTS) {
                    // Category: Podcasts -> Popular Podcast Shows & Episodes
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = "POPULAR PODCAST SHOWS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 0.1.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )

                        val popularPodcasts = listOf(
                            "The Joe Rogan Experience" to "Interviews with thought leaders, comedians, and experts.",
                            "Huberman Lab" to "Science-based tools for everyday health and performance.",
                            "Lex Fridman Podcast" to "Deep conversations on AI, science, and philosophy.",
                            "Stuff You Should Know" to "How everything around us actually works.",
                            "Crime Junkie" to "True crime investigations and gripping cases.",
                            "SmartLess" to "Improvised comedy interviews and genuine stories.",
                            "Science Vs" to "Separating viral trends and fads from scientific fact.",
                            "The Daily" to "Daily in-depth journalism on key global developments."
                        )

                        popularPodcasts.forEach { (name, desc) ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp)
                                    .clickable {
                                        searchQuery = name
                                        onSearchQueryChanged(name)
                                    },
                                shape = RoundedCornerShape(12.dp),
                                color = Color(0xFF1E1E1E),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
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
                                            .background(Color(0xFF2A2A2A)),
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
                    // Default Discovery Feed (Scrollable) for ALL
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 3. Listen to Surroundings Action
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .clickable {
                                    isListening = !isListening
                                    onListenToSurroundings()
                                },
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF222222),
                            border = androidx.compose.foundation.BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.5f))
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
                                    text = if (isListening) "Listening to Audio..." else "Listen to Surroundings",
                                    color = UnboundPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 4. Trending Vibe Tags
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "TRENDING VIBES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant,
                                letterSpacing = 0.1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                trendingVibes.forEach { vibe ->
                                    Surface(
                                        shape = RoundedCornerShape(20.dp),
                                        color = Color(0xFF222222),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF383838)),
                                        modifier = Modifier.clickable { onVibeTagClick(vibe) }
                                    ) {
                                        Text(
                                            text = vibe,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = OnSurface,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 5. Featured Vibes
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "FEATURED VIBES",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant,
                                letterSpacing = 0.1.sp,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                FeaturedVibeCard(
                                    title = "Neon Noir",
                                    description = "Moody synths & late night driving.",
                                    imageUrl = IMG_NEON_NOIR,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onGenreCardClick("Neon Noir") }
                                )

                                FeaturedVibeCard(
                                    title = "Velvet R&B",
                                    description = "Silky vocals and heavy 808s.",
                                    imageUrl = IMG_VELVET_RNB,
                                    modifier = Modifier.weight(1f),
                                    onClick = { onGenreCardClick("Velvet R&B") }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultItem(
    track: TrackItem,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E1E),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF2E2E2E))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF2A2A2A)),
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
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = UnboundPrimary,
                modifier = Modifier.size(22.dp)
            )
        }
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
        color = Color(0xFF1E1E1E),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333333))
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
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.85f)
                            ),
                            startY = 60f
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
                    fontSize = 14.sp,
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
