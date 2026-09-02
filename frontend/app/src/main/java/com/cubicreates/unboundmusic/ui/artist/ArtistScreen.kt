/*
 * Package: com.cubicreates.unboundmusic.ui.artist
 * File: ArtistScreen.kt
 * Purpose: Full Artist Discography Screen with hero portrait, listener count, biography,
 *          Top Songs list, Discography (Albums & Singles), and Similar Artists graph.
 * Subsystem: Artist Profile UI
 */

package com.cubicreates.unboundmusic.ui.artist

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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
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
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

data class ArtistProfileData(
    val name: String,
    val heroImageUrl: String,
    val monthlyListeners: String = "2.4M monthly listeners",
    val bio: String = "Electronic and ambient producer known for synthetic soundscapes.",
    val topTracks: List<TrackItem> = emptyList(),
    val albums: List<ArtistAlbumItem> = emptyList(),
    val similarArtists: List<SimilarArtistItem> = emptyList()
)

data class ArtistAlbumItem(
    val title: String,
    val year: String,
    val coverUrl: String
)

data class SimilarArtistItem(
    val name: String,
    val imageUrl: String
)

@Composable
fun ArtistScreen(
    profile: ArtistProfileData,
    isLoading: Boolean = false,
    onBack: () -> Unit = {},
    onTrackSelect: (TrackItem) -> Unit = {},
    onPlayAll: () -> Unit = {},
    onArtistClick: (String) -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = UnboundPrimary)
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 120.dp)
            ) {
                // Hero Image Header with Vignette Overlay
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    if (profile.heroImageUrl.isNotBlank()) {
                        AsyncImage(
                            model = profile.heroImageUrl,
                            contentDescription = profile.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }

                    // Gradient overlay
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        UnboundBackground.copy(alpha = 0.8f),
                                        UnboundBackground
                                    ),
                                    startY = 100f
                                )
                            )
                    )

                    // Back button
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .padding(top = 40.dp, start = 16.dp)
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

                    // Artist Name & Listener Count
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        Text(
                            text = profile.name,
                            fontSize = 36.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = profile.monthlyListeners,
                            fontSize = 14.sp,
                            color = OnSurfaceVariant
                        )
                    }
                }

                // Action Buttons: Play All & Shuffle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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
                            .clickable(onClick = onPlayAll),
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

                Spacer(modifier = Modifier.height(16.dp))

                // Top Tracks Section
                if (profile.topTracks.isNotEmpty()) {
                    Text(
                        text = "TOP SONGS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    profile.topTracks.take(5).forEachIndexed { index, track ->
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
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = UnboundPrimary,
                                modifier = Modifier.width(24.dp)
                            )

                            Box(
                                modifier = Modifier
                                    .size(44.dp)
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

                Spacer(modifier = Modifier.height(24.dp))

                // Albums & Discography
                if (profile.albums.isNotEmpty()) {
                    Text(
                        text = "DISCOGRAPHY",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(profile.albums) { album ->
                            Column(
                                modifier = Modifier
                                    .width(130.dp)
                                    .clip(RoundedCornerShape(12.dp))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(SurfaceGlassHighest)
                                        .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(12.dp))
                                ) {
                                    AsyncImage(
                                        model = album.coverUrl,
                                        contentDescription = album.title,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = album.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = album.year,
                                    fontSize = 11.sp,
                                    color = OnSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Similar Artists
                if (profile.similarArtists.isNotEmpty()) {
                    Text(
                        text = "SIMILAR ARTISTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurfaceVariant,
                        letterSpacing = 0.1.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(profile.similarArtists) { sim ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .clickable { onArtistClick(sim.name) }
                                    .width(90.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(SurfaceGlassHighest)
                                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                                ) {
                                    AsyncImage(
                                        model = sim.imageUrl,
                                        contentDescription = sim.name,
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Crop
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = sim.name,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
