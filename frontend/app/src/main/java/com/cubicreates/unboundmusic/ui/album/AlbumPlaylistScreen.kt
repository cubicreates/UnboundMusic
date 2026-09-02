/*
 * Package: com.cubicreates.unboundmusic.ui.album
 * File: AlbumPlaylistScreen.kt
 * Purpose: Full Album and Playlist detail view with artwork header, total duration metrics,
 *          Play All, Shuffle All, and interactive tracklist.
 * Subsystem: Album / Playlist Detail UI
 */

package com.cubicreates.unboundmusic.ui.album

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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

data class AlbumPlaylistData(
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val tracks: List<TrackItem> = emptyList(),
    val totalDuration: String = "45 mins"
)

@Composable
fun AlbumPlaylistScreen(
    data: AlbumPlaylistData,
    onBack: () -> Unit = {},
    onTrackSelect: (TrackItem) -> Unit = {},
    onPlayAll: () -> Unit = {},
    onShuffleAll: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 120.dp)
        ) {
            // Top Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 40.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = OnSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Artwork & Info Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .shadow(elevation = 20.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black)
                        .clip(RoundedCornerShape(20.dp))
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(20.dp))
                ) {
                    AsyncImage(
                        model = data.coverUrl,
                        contentDescription = data.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = data.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${data.subtitle} • ${data.tracks.size} songs, ${data.totalDuration}",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Play All & Shuffle Buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(UnboundPrimary)
                        .clickable(onClick = onPlayAll)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = OnPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Play All",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnPrimary
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(14.dp))
                        .clickable(onClick = onShuffleAll),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Tracklist
            Text(
                text = "TRACKS",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurfaceVariant,
                letterSpacing = 0.1.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )

            data.tracks.forEachIndexed { index, track ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceGlassHighest)
                        .clickable { onTrackSelect(track) }
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundPrimary,
                        modifier = Modifier.width(24.dp)
                    )

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(UnboundSurfaceContainerHigh),
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
                            color = OnSurfaceVariant
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
