/*
 * Package: com.cubicreates.unboundmusic.ui.search
 * File: GuestSearchScreen.kt
 * Purpose: Discovery and search interface for guest/offline users, featuring trending vibes,
 *          popular charts, genre collections, and ambient Shazam listening.
 * Subsystem: Discovery / Guest Mode
 */

package com.cubicreates.unboundmusic.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Mic
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuestSearchScreen(
    modifier: Modifier = Modifier,
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
    onSearchSubmit: (String) -> Unit = {},
    onPlayNextBatch: (List<TrackItem>) -> Unit = {},
    onAddToQueueBatch: (List<TrackItem>) -> Unit = {},
    onDownloadBatch: (List<TrackItem>) -> Unit = {},
    isListeningAudio: Boolean = false,
    onClearVibe: () -> Unit = {},
    onVoiceSearchClick: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 8.dp, bottom = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Search Hero
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

            // Search Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = Color(0xFF222222),
                border = BorderStroke(1.dp, Color(0xFF383838))
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
                                text = "Search song, artist, album or #vibe...",
                                color = OnSurfaceVariant.copy(alpha = 0.6f),
                                fontSize = 14.sp
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                onSearchQueryChanged(it)
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(UnboundPrimary),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.startsWith("#")) {
                                    onVibeSubmit(searchQuery.removePrefix("#").trim())
                                } else {
                                    onSearchSubmit(searchQuery.trim())
                                }
                            }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (searchQuery.isNotEmpty()) {
                        IconButton(
                            onClick = {
                                searchQuery = ""
                                onSearchQueryChanged("")
                            },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear",
                                tint = OnSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else if (isSearching || vibeState is VibeSearchUiState.Loading) {
                        CircularProgressIndicator(
                            color = UnboundPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onVoiceSearchClick,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = "Voice Search",
                                tint = UnboundPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Category Chips Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchCategory.values().forEach { cat ->
                    val isSelected = selectedCategory == cat
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = if (isSelected) UnboundPrimary else Color(0xFF242424),
                        border = if (isSelected) null else BorderStroke(1.dp, Color(0xFF383838)),
                        modifier = Modifier.clickable { onCategorySelected(cat) }
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

            // Vibe Result Card (if active)
            if (vibeState is VibeSearchUiState.Success) {
                val vr = vibeState.vibeResult
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFF1E1E1E),
                    border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.4f))
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

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
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

                                IconButton(
                                    onClick = onClearVibe,
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear Vibe",
                                        tint = OnSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
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

            // Results or Discovery Feed
            val displayTracks = when {
                vibeState is VibeSearchUiState.Success -> vibeState.radioTracks.filter { track ->
                    val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_")
                    val isArtist = track.itemType.equals("artist", ignoreCase = true) || track.browseId.startsWith("UC")
                    val isPlaylist = track.itemType.equals("playlist", ignoreCase = true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL")
                    !isAlbum && !isArtist && !isPlaylist && !track.id.startsWith("UC") && !track.id.startsWith("MPREb_")
                }
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
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        itemsIndexed(displayTracks, key = { index, track -> "${track.id}_$index" }) { _, track ->
                            SearchResultItem(
                                track = track,
                                isCurrentPlaying = track.id == currentTrackId && isPlaying,
                                onClick = {
                                    val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_") || track.id.startsWith("MPREb_")
                                    val isArtist = track.itemType.equals("artist", ignoreCase = true) || track.browseId.startsWith("UC") || track.id.startsWith("UC")
                                    val isPlaylist = track.itemType.equals("playlist", ignoreCase = true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL") || track.id.startsWith("VL") || track.id.startsWith("PL")

                                    val resolvedArtistName = if (track.itemType.equals("artist", ignoreCase = true) || track.artist.isBlank() || track.artist.contains("subscriber", ignoreCase = true)) {
                                        track.title.ifBlank { track.artist }
                                    } else {
                                        track.artist.ifBlank { track.title }
                                    }
                                    val resolvedAlbumPlaylistId = track.browseId.ifBlank { track.id }

                                    when {
                                        isArtist -> onArtistClick(resolvedArtistName)
                                        isAlbum || isPlaylist -> onAlbumClick(resolvedAlbumPlaylistId, track.title, track.coverUrl)
                                        else -> onTrackSelect(track, listOf(track))
                                    }
                                },
                                onPlayNext = { onPlayNextSingle(track) },
                                onAddToQueue = { onAddToQueueSingle(track) },
                                onStartRadio = { onStartRadioSingle(track) },
                                onDownload = { onDownloadSingle(track) },
                                onAddToPlaylist = { onAddToPlaylistSingle(track) }
                            )
                        }

                        item {
                            Spacer(modifier = Modifier.height(100.dp))
                        }
                    }
                }
            } else {
                if (selectedCategory == SearchCategory.SONGS || selectedCategory == SearchCategory.MUSIC) {
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
                                        isCurrentPlaying = track.id == currentTrackId && isPlaying,
                                        onClick = { onTrackSelect(track, listOf(track)) },
                                        onPlayNext = { onPlayNextSingle(track) },
                                        onAddToQueue = { onAddToQueueSingle(track) },
                                        onStartRadio = { onStartRadioSingle(track) },
                                        onDownload = { onDownloadSingle(track) },
                                        onAddToPlaylist = { onAddToPlaylistSingle(track) }
                                    )
                                }
                                item {
                                    Spacer(modifier = Modifier.height(100.dp))
                                }
                            }
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = UnboundPrimary, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                } else if (selectedCategory == SearchCategory.PODCASTS) {
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
                                border = BorderStroke(1.dp, Color(0xFF2E2E2E))
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

                        Spacer(modifier = Modifier.height(100.dp))
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
                        // Ambient Listening Action
                        Surface(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .clickable { onListenToSurroundings() },
                            shape = RoundedCornerShape(24.dp),
                            color = Color(0xFF222222),
                            border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.5f))
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
                                    text = if (isListeningAudio) "Listening to Audio..." else "Listen to Surroundings",
                                    color = UnboundPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Trending Vibes
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
                                        border = BorderStroke(1.dp, Color(0xFF383838)),
                                        modifier = Modifier.clickable {
                                            searchQuery = vibe
                                            onVibeTagClick(vibe)
                                        }
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

                        // Featured Vibes
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

                        Spacer(modifier = Modifier.height(100.dp))
                    }
                }
            }
        }
    }
}
