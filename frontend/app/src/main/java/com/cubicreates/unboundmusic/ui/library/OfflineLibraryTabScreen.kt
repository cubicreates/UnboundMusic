/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: OfflineLibraryTabScreen.kt
 * Purpose: Dedicated 4-tab offline library sub-screen matching Screenshots 2 & 3.
 *          Features TRACKS list, ARTIST grouped view, ALBUM 2-column grid with song count badges,
 *          and GENRES view, with search filtering and contextual track actions (including Ringtone Cutter).
 * Subsystem: Personal Library / Offline Hub
 */

package com.cubicreates.unboundmusic.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer

private val TABS = listOf("TRACKS", "ARTIST", "ALBUM", "GENRES")

@Composable
fun OfflineLibraryTabScreen(
    tracks: List<TrackItem>,
    initialTab: Int = 0,
    modifier: Modifier = Modifier,
    onTrackSelect: (TrackItem) -> Unit = {},
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onOpenRingtoneCutter: (TrackItem) -> Unit = {},
    onToggleFavorite: (TrackItem) -> Unit = {},
    onDeleteTrack: (TrackItem) -> Unit = {},
    onBack: () -> Unit = {}
) {
    var selectedTab by remember { mutableIntStateOf(initialTab.coerceIn(0, 3)) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchActive by remember { mutableStateOf(false) }

    // Filter tracks by search
    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) tracks
        else tracks.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
            it.artist.contains(searchQuery, ignoreCase = true) ||
            it.album.contains(searchQuery, ignoreCase = true)
        }
    }

    // Grouping for Artists
    val artistsGrouped = remember(filteredTracks) {
        filteredTracks.groupBy { it.artist.ifBlank { "Unknown Artist" } }
            .toList()
            .sortedByDescending { it.second.size }
    }

    // Grouping for Albums
    val albumsGrouped = remember(filteredTracks) {
        filteredTracks.groupBy { it.album.ifBlank { "Unknown Album" } }
            .toList()
            .sortedByDescending { it.second.size }
    }

    // Grouping for Genres
    val genresGrouped = remember(filteredTracks) {
        filteredTracks.groupBy {
            val src = it.source.lowercase()
            when {
                src.contains("pop") -> "Pop"
                src.contains("rock") -> "Rock"
                src.contains("jazz") -> "Jazz"
                src.contains("classical") -> "Classical"
                src.contains("hip hop") || src.contains("rap") -> "Hip Hop"
                src.contains("dance") || src.contains("electronic") -> "Dance"
                else -> "Local Audio"
            }
        }.toList().sortedByDescending { it.second.size }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF007A99), // Vibrant Teal / Blue from Screenshot 2 & 3
                        Color(0xFF0B3566),
                        UnboundBackground
                    )
                )
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ==================== Header ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }

                Text(
                    text = "LIBRARY",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { isSearchActive = !isSearchActive },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(SurfaceGlassHighest)
                    ) {
                        Icon(
                            imageVector = if (isSearchActive) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Search",
                            tint = Color.White
                        )
                    }
                }
            }

            // Search Bar (Expandable)
            AnimatedVisibility(visible = isSearchActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search tracks, artists, albums...", color = OnSurfaceVariant) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = OnSurface,
                            unfocusedTextColor = OnSurface,
                            focusedBorderColor = Color(0xFF00E5FF),
                            unfocusedBorderColor = Color(0x55FFFFFF),
                            focusedContainerColor = Color(0x44000000),
                            unfocusedContainerColor = Color(0x33000000)
                        )
                    )
                }
            }

            // ==================== 4 Sub-Tabs ====================
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color(0xFFFFD54F), // Vibrant gold indicator matching screenshot
                divider = {},
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        height = 3.dp,
                        color = Color(0xFFFFD54F)
                    )
                }
            ) {
                TABS.forEachIndexed { index, tabTitle ->
                    val isSelected = selectedTab == index
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = tabTitle,
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color(0xFFFFD54F) else Color(0xAAFFFFFF)
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // ==================== Tab Content ====================
            when (selectedTab) {
                // Tab 0: TRACKS list (Screenshot 2)
                0 -> {
                    if (filteredTracks.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No tracks found", color = Color.White.copy(alpha = 0.7f), fontSize = 16.sp)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filteredTracks, key = { it.id }) { track ->
                                TrackRowItem(
                                    track = track,
                                    onClick = { onTrackSelect(track) },
                                    onPlayNext = { onPlayNext(track) },
                                    onAddToPlaylist = { onAddToPlaylist(track) },
                                    onOpenRingtoneCutter = { onOpenRingtoneCutter(track) },
                                    onToggleFavorite = { onToggleFavorite(track) },
                                    onDeleteTrack = { onDeleteTrack(track) }
                                )
                            }
                        }
                    }
                }

                // Tab 1: ARTIST view
                1 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(artistsGrouped, key = { it.first }) { (artist, artistTracks) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable { onTrackSelect(artistTracks.first()) }
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF00C6FF)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = null,
                                        tint = Color.Black,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = artist,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${artistTracks.size} songs",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }

                                IconButton(onClick = { onTrackSelect(artistTracks.first()) }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Artist",
                                        tint = Color(0xFF00E5FF)
                                    )
                                }
                            }
                        }
                    }
                }

                // Tab 2: ALBUM 2-Column Grid (Screenshot 3)
                2 -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(albumsGrouped, key = { it.first }) { (album, albumTracks) ->
                            AlbumGridCard(
                                albumTitle = album,
                                tracks = albumTracks,
                                onClick = { onTrackSelect(albumTracks.first()) }
                            )
                        }
                    }
                }

                // Tab 3: GENRES view
                3 -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(genresGrouped, key = { it.first }) { (genre, genreTracks) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0x22FFFFFF))
                                    .clickable { onTrackSelect(genreTracks.first()) }
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFE91E63)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MusicNote,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = genre,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "${genreTracks.size} songs",
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }

                                IconButton(onClick = { onTrackSelect(genreTracks.first()) }) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Play Genre",
                                        tint = Color(0xFF00E5FF)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Track row item with album art and 3-dots dropdown menu (Screenshot 2)
@Composable
private fun TrackRowItem(
    track: TrackItem,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenRingtoneCutter: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteTrack: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF1E2842)),
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
                    tint = Color(0xFF00E5FF)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = track.artist.ifBlank { "Unknown Artist" },
                fontSize = 12.sp,
                color = Color.White.copy(alpha = 0.7f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // 3-Dots Menu
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Track Menu",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(Color(0xFF1B2338))
                    .border(1.dp, Color(0xFF2A3654), RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Play Now", color = OnSurface) },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF00E5FF)) },
                    onClick = {
                        onClick()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Play Next", color = OnSurface) },
                    leadingIcon = { Icon(Icons.Default.QueueMusic, null, tint = Color(0xFF00E5FF)) },
                    onClick = {
                        onPlayNext()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = OnSurface) },
                    leadingIcon = { Icon(Icons.Default.PlaylistAdd, null, tint = Color(0xFF00E5FF)) },
                    onClick = {
                        onAddToPlaylist()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Ringtone Cutter", color = Color(0xFFFFD54F), fontWeight = FontWeight.Bold) },
                    leadingIcon = { Icon(Icons.Default.ContentCut, null, tint = Color(0xFFFFD54F)) },
                    onClick = {
                        onOpenRingtoneCutter()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Favorite", color = OnSurface) },
                    leadingIcon = { Icon(Icons.Default.Favorite, null, tint = Color(0xFFE91E63)) },
                    onClick = {
                        onToggleFavorite()
                        showMenu = false
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = Color(0xFFFF5252)) },
                    leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFF5252)) },
                    onClick = {
                        onDeleteTrack()
                        showMenu = false
                    }
                )
            }
        }
    }
}

// 2-Column Album Card (Screenshot 3)
@Composable
private fun AlbumGridCard(
    albumTitle: String,
    tracks: List<TrackItem>,
    onClick: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x22000000))
            .clickable(onClick = onClick)
    ) {
        // Square Album Artwork
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp))
                .background(Color(0xFF1B2338)),
            contentAlignment = Alignment.Center
        ) {
            val firstCover = tracks.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl
            if (firstCover != null) {
                AsyncImage(
                    model = firstCover,
                    contentDescription = albumTitle,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Album,
                    contentDescription = null,
                    tint = Color(0xFF00E5FF),
                    modifier = Modifier.size(54.dp)
                )
            }
        }

        // Details Row below image (Screenshot 3)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = albumTitle,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${tracks.size} songs",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Album Menu",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    modifier = Modifier.background(Color(0xFF1B2338))
                ) {
                    DropdownMenuItem(
                        text = { Text("Play Album", color = OnSurface) },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = Color(0xFF00E5FF)) },
                        onClick = {
                            onClick()
                            showMenu = false
                        }
                    )
                }
            }
        }
    }
}
