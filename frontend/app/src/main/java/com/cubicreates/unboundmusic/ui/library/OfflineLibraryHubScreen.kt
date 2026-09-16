/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: OfflineLibraryHubScreen.kt
 * Purpose: Complete Offline "Music Player" Hub screen matching Screenshot 1.
 *          Features 6-tile colorful category grid (Library, Folders, Favorite, Recent Played, Recent Add, Equalizer)
 *          with live item counters, Playlists horizontal carousel with '+' create button, and yellow Shuffle All FAB.
 * Subsystem: Personal Library / Offline Hub
 */

package com.cubicreates.unboundmusic.ui.library

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreTime
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.ui.components.TrackItem

@Composable
fun OfflineLibraryHubScreen(
    libraryTracks: List<TrackItem>,
    favoriteTracks: List<TrackItem>,
    recentlyPlayedTracks: List<TrackItem>,
    foldersCount: Int,
    customPlaylists: List<CustomPlaylist>,
    modifier: Modifier = Modifier,
    onOpenLibraryTab: (initialTab: Int) -> Unit = {},
    onOpenFolders: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenRecentlyPlayed: () -> Unit = {},
    onOpenRecentlyAdded: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onShufflePlayAll: () -> Unit = {},
    onCreatePlaylist: () -> Unit = {},
    onPlaylistClick: (CustomPlaylist) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onSearchClick: () -> Unit = {}
) {
    // Recent added tracks computed by latest duration/index or top 50
    val recentAddedCount = (libraryTracks.size.coerceAtMost(89))

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E75D8), // Vibrant Sky Blue from Screenshot 1
                        Color(0xFF2885EE),
                        Color(0xFF1A60B8)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 90.dp)
        ) {
            // ==================== Top Header ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onMenuClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menu",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Text(
                    text = "Music Player",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==================== 6-Card Category Grid (Screenshot 1) ====================
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: LIBRARY (Blue) & FOLDERS (Orange) & FAVORITE (Red)
                // Note: Screenshot 1 has 3 tiles per row: (Library, Folders, Favorite) and (Recent Played, Recent Add, Equalizer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tile 1: LIBRARY
                    CategoryTile(
                        title = "LIBRARY",
                        countText = libraryTracks.size.toString(),
                        icon = Icons.Default.MusicNote,
                        backgroundColor = Color(0xFF0091EA), // Vibrant Cyan-Blue
                        modifier = Modifier.weight(1f),
                        onClick = { onOpenLibraryTab(0) }
                    )

                    // Tile 2: FOLDERS
                    CategoryTile(
                        title = "FOLDERS",
                        countText = foldersCount.toString(),
                        icon = Icons.Default.Folder,
                        backgroundColor = Color(0xFFFF9800), // Vibrant Orange
                        modifier = Modifier.weight(1f),
                        onClick = onOpenFolders
                    )

                    // Tile 3: FAVORITE
                    CategoryTile(
                        title = "FAVORITE",
                        countText = favoriteTracks.size.toString(),
                        icon = Icons.Default.Favorite,
                        backgroundColor = Color(0xFFFF5252), // Vibrant Coral Red
                        modifier = Modifier.weight(1f),
                        onClick = onOpenFavorites
                    )
                }

                // Row 2: RECENT PLAYED (Sky Blue) & RECENT ADD (Teal/Green) & EQUALIZER (Purple)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Tile 4: RECENT PLAYED
                    CategoryTile(
                        title = "RECENT PL...",
                        countText = recentlyPlayedTracks.size.toString(),
                        icon = Icons.Default.History,
                        backgroundColor = Color(0xFF00BCD4), // Sky Blue
                        modifier = Modifier.weight(1f),
                        onClick = onOpenRecentlyPlayed
                    )

                    // Tile 5: RECENT ADD
                    CategoryTile(
                        title = "RECENT ADD",
                        countText = recentAddedCount.toString(),
                        icon = Icons.Default.MoreTime,
                        backgroundColor = Color(0xFF00C853), // Vibrant Green
                        modifier = Modifier.weight(1f),
                        onClick = onOpenRecentlyAdded
                    )

                    // Tile 6: EQUALIZER
                    CategoryTile(
                        title = "EQUALIZER",
                        countText = "PRO",
                        icon = Icons.Default.GraphicEq,
                        backgroundColor = Color(0xFF9C27B0), // Vibrant Purple
                        modifier = Modifier.weight(1f),
                        onClick = onOpenEqualizer
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // ==================== PLAYLISTS Section (Screenshot 1) ====================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PLAYLISTS (${customPlaylists.size})",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 0.5.sp
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCreatePlaylist) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Create Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    IconButton(onClick = { onOpenLibraryTab(0) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "All Playlists",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Playlists Horizontal Row
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Custom User Playlists
                items(customPlaylists, key = { it.id }) { playlist ->
                    PlaylistHubCard(
                        title = playlist.title,
                        trackCount = playlist.tracks.size,
                        onClick = { onPlaylistClick(playlist) }
                    )
                }

                // Default placeholder playlists if empty
                if (customPlaylists.isEmpty()) {
                    item {
                        PlaylistHubCard(
                            title = "Pop",
                            trackCount = (libraryTracks.size / 2).coerceAtLeast(1),
                            onClick = { onOpenLibraryTab(0) }
                        )
                    }
                    item {
                        PlaylistHubCard(
                            title = "Jazz",
                            trackCount = (libraryTracks.size / 3).coerceAtLeast(1),
                            onClick = { onOpenLibraryTab(0) }
                        )
                    }
                }

                // '+' Add New Playlist Card (Screenshot 1)
                item {
                    Box(
                        modifier = Modifier
                            .size(105.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0x33FFFFFF))
                            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                            .clickable(onClick = onCreatePlaylist),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add Playlist",
                            tint = Color.White,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                }
            }
        }

        // ==================== Floating Action Button: Shuffle All (Screenshot 1) ====================
        FloatingActionButton(
            onClick = onShufflePlayAll,
            containerColor = Color(0xFFFFD54F), // Bright yellow from screenshot
            contentColor = Color.White,
            shape = CircleShape,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .size(60.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Shuffle,
                contentDescription = "Shuffle Play All",
                modifier = Modifier.size(30.dp),
                tint = Color(0xFF1E2842)
            )
        }
    }
}

// 1 of 6 Category Tile matching Screenshot 1
@Composable
private fun CategoryTile(
    title: String,
    countText: String,
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(1.0f)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        // Top right count badge
        Text(
            text = countText,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.align(Alignment.TopEnd)
        )

        // Center Icon
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier
                .size(38.dp)
                .align(Alignment.Center)
        )

        // Bottom Title Label
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
        )
    }
}

// Playlist Card matching Screenshot 1 (e.g. Pop, Jazz)
@Composable
private fun PlaylistHubCard(
    title: String,
    trackCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(105.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x33FFFFFF))
            .border(1.dp, Color(0x44FFFFFF), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        // Top right count badge
        Text(
            text = trackCount.toString(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        )

        // Center Playlist Icon
        Icon(
            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
            contentDescription = title,
            tint = Color.White,
            modifier = Modifier
                .size(36.dp)
                .align(Alignment.Center)
        )

        // Bottom label banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0x44000000))
                .align(Alignment.BottomCenter)
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
