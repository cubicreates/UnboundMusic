/*
 * Package: com.cubicreates.unboundmusic.ui.search
 * File: SearchScreen.kt
 * Purpose: Production Search & Discovery Hub for Unbound Music. Supports YouTube Music catalog search,
 *          natural language vibe exploration, ambient Shazam acoustic recognition, and genre discovery.
 * Subsystem: Discovery / Search UI
 */

package com.cubicreates.unboundmusic.ui.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
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
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

private val trendingVibes = listOf(
    "#LateNightRain",
    "#CyberpunkDrive",
    "#CoffeeHouseAcoustic",
    "#SundayMorningLoFi",
    "#GymHype",
    "#DeepFocusFlow"
)

private const val IMG_NEON_NOIR = "https://lh3.googleusercontent.com/aida-public/AB6AXuAYApkR1WLZQ1hOJB95_iBd2_6cuBHZ5VbNOvQ_hcNKz3gsZLAuAA6yPer-cv4wpCYpLlw68Hxd1W5C7vYY2UC06lB5ekBMo_nNZokBGdAYqpVtQupurMBSPsqk4e8h0mZN8oEPMAwaAgWr7ERuusrXszfIgYH5lETzYbT9eVnm0PQnIvgH7KIfCGgn6dcFzlWxtoheMs68tYehJtQm41jdKTmPMk5DLyHD6t14YXR9Zny59FV8fN8pRw"
private const val IMG_VELVET_RNB = "https://lh3.googleusercontent.com/aida-public/AB6AXuBSJnsYO276b6VZ7n7LFagIeKmKHHuG6IEVYjF_pjp2JIV8dHBs80dkrCjjG626oVAhRoT0pLENqVIKiLZqeF_xmuxrIfZS54cHPQBRIrOj3x6_R6QjYDWTeMDb8OwPV9OfoUaFvLymzUkf0ghmIl8TB3mcfe8aGHGD2jMsGY7s6Rz7nhFTn69aLj9L8qY1RIP1ose4cRhb7qkN1d2shozxVLqWbD_hqAa-k6OZvsBgtEqdBN832OB5WA"
private const val IMG_MINIMAL_TECHNO = "https://lh3.googleusercontent.com/aida-public/AB6AXuAnosEC-gnWsCAmEWPnFxuHS2fKqzJpXbYP2Te8W67oJwj0Pr_tECi8sJ2HCNCOeT4n6WkRuO0OFttj8LL-oU0jw1jr2FJkFUEvQwR7V8c48MrfoIUsR3Ns8H6UEiOVxxpPEZ4jXP4_7EFwVd3RF0HIFlEnVyjEZi0Gm6QbIwe0N7Oua6_D2FlOQpb5468cLAkpaD6eBJm3W0J5RNzKGmf4ute3p-m0Hq8_QA0HG-YdvdnPJ3rQlKAxHQ"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    modifier: Modifier = Modifier,
    searchResults: List<TrackItem> = emptyList(),
    isSearching: Boolean = false,
    onSearchQueryChanged: (String) -> Unit = {},
    onListenToSurroundings: () -> Unit = {},
    onVibeTagClick: (String) -> Unit = {},
    onGenreCardClick: (String) -> Unit = {},
    onTrackSelect: (TrackItem) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }

    // Pulse animation for Shazam / Listen button
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .padding(top = 28.dp, bottom = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. Search Hero Section
            Text(
                text = "Discover",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.03).sp,
                style = TextStyle(
                    brush = Brush.horizontalGradient(
                        colors = listOf(UnboundPrimary, UnboundTertiary)
                    )
                )
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Find music by describing a vibe, title, or sound.",
                fontSize = 14.sp,
                color = OnSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Search Bar with Ambient Glow
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 12.dp,
                        shape = RoundedCornerShape(16.dp),
                        spotColor = UnboundPrimary.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search tracks, artists, or #vibes...",
                                color = OnSurfaceVariant.copy(alpha = 0.5f),
                                fontSize = 15.sp
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
                                fontSize = 15.sp
                            ),
                            cursorBrush = SolidColor(UnboundPrimary),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = UnboundPrimary,
                            strokeWidth = 2.dp
                        )
                    } else if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                onSearchQueryChanged("")
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Content Area: Search Results OR Discovery Feed
            if (searchQuery.isNotBlank() || searchResults.isNotEmpty()) {
                // Live Search Results List
                Text(
                    text = "SEARCH RESULTS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurfaceVariant,
                    letterSpacing = 0.1.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                )

                if (searchResults.isEmpty() && !isSearching) {
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
                        items(searchResults) { track ->
                            SearchResultItem(
                                track = track,
                                onClick = { onTrackSelect(track) }
                            )
                        }
                    }
                }
            } else {
                // Default Discovery Feed (Scrollable)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // 3. Listen to Surroundings Action (Ambient Shazam Recognition)
                    Box(
                        modifier = Modifier
                            .scale(pulseScale)
                            .clip(RoundedCornerShape(32.dp))
                            .background(SurfaceGlassHighest)
                            .border(width = 1.dp, color = UnboundPrimary.copy(alpha = 0.5f), shape = RoundedCornerShape(32.dp))
                            .clickable {
                                isListening = !isListening
                                onListenToSurroundings()
                            }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(UnboundPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GraphicEq,
                                    contentDescription = "Shazam",
                                    tint = OnPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Text(
                                text = if (isListening) "Listening to Audio..." else "Listen to Surroundings",
                                color = UnboundPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 4. Trending Vibe Tags
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "TRENDING VIBES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 0.1.sp,
                            modifier = Modifier.padding(bottom = 10.dp)
                        )

                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            trendingVibes.forEach { tag ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(SurfaceGlassHighest)
                                        .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(10.dp))
                                        .clickable {
                                            searchQuery = tag.removePrefix("#")
                                            onVibeTagClick(tag)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = tag,
                                        color = OnSurface,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 5. Featured Genre Cards
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Large Featured Tile: Neon Noir
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(SurfaceGlassHighest)
                                .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
                                .clickable { onGenreCardClick("Neon Noir") }
                        ) {
                            AsyncImage(
                                model = IMG_NEON_NOIR,
                                contentDescription = "Neon Noir",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                            startY = 60f
                                        )
                                    )
                            )

                            Column(
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(14.dp)
                            ) {
                                Text(
                                    text = "Neon Noir",
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Curated for late night drives.",
                                    fontSize = 13.sp,
                                    color = OnSurfaceVariant
                                )
                            }
                        }

                        // Two Side-by-Side Tiles: Velvet R&B and Minimal Techno
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Tile 1: Velvet R&B
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceGlassHighest)
                                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
                                    .clickable { onGenreCardClick("Velvet R&B") }
                            ) {
                                AsyncImage(
                                    model = IMG_VELVET_RNB,
                                    contentDescription = "Velvet R&B",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                                startY = 60f
                                            )
                                        )
                                    )

                                Text(
                                    text = "Velvet R&B",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                )
                            }

                            // Tile 2: Minimal Techno
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(SurfaceGlassHighest)
                                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
                                    .clickable { onGenreCardClick("Minimal Techno") }
                            ) {
                                AsyncImage(
                                    model = IMG_MINIMAL_TECHNO,
                                    contentDescription = "Minimal Techno",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                                                startY = 60f
                                            )
                                        )
                                    )

                                Text(
                                    text = "Minimal Techno",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(10.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Individual track card in search results.
 */
@Composable
private fun SearchResultItem(
    track: TrackItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(UnboundSurfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            if (track.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist,
                fontSize = 13.sp,
                color = OnSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Play Button
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(UnboundPrimary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = UnboundPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
