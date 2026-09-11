/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: HomeScreen.kt
 * Purpose: Production Guest HomeScreen rendering 24-hour temporal mood capsules and Top 100 regional charts.
 * Subsystem: Home UI / Cold-Start Presentation
 */

package com.cubicreates.unboundmusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.DaypartingState
import com.cubicreates.unboundmusic.data.MoodCapsule
import com.cubicreates.unboundmusic.ui.components.MoodItem
import com.cubicreates.unboundmusic.ui.components.MoodsSection
import com.cubicreates.unboundmusic.ui.components.TopTracksGrid
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundTopAppBar
import com.cubicreates.unboundmusic.ui.components.defaultMoods
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.data.GenreItemDto
import com.cubicreates.unboundmusic.data.GenreSectionDto
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.LibraryMusic

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    tracks: List<TrackItem> = defaultTopTracks,
    syncedYouTubeTracks: List<TrackItem> = emptyList(),
    daypartingState: DaypartingState? = null,
    genreSections: List<GenreSectionDto> = emptyList(),
    userAvatarUrl: String? = null,
    accountName: String? = null,
    isYouTubeConnected: Boolean = false,
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onMoodSelect: (MoodItem) -> Unit = {},
    onCapsuleSelect: (MoodCapsule) -> Unit = {},
    onGenreSelect: (GenreItemDto) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 76.dp, bottom = 24.dp)
        ) {
            // 0. Personalized YouTube Welcome Greeting
            if (isYouTubeConnected) {
                PersonalizedWelcomeHeader(
                    accountName = accountName,
                    userAvatarUrl = userAvatarUrl,
                    syncedCount = syncedYouTubeTracks.size,
                    onProfileClick = onProfileClick
                )
                Spacer(modifier = Modifier.height(20.dp))
            }

            // 1. Synced YouTube Music Preferences & Liked Songs
            if (isYouTubeConnected && syncedYouTubeTracks.isNotEmpty()) {
                YouTubePreferencesSection(
                    tracks = syncedYouTubeTracks,
                    accountName = accountName,
                    onTrackClick = { track ->
                        onTrackSelect(track, syncedYouTubeTracks)
                    },
                    onPlayAll = {
                        if (syncedYouTubeTracks.isNotEmpty()) {
                            onTrackSelect(syncedYouTubeTracks.first(), syncedYouTubeTracks)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 2. Time-Aware Situational Mood Capsules Section
            if (daypartingState != null && daypartingState.capsules.isNotEmpty()) {
                TemporalCapsulesSection(
                    state = daypartingState,
                    onCapsuleClick = onCapsuleSelect
                )
            } else {
                MoodsSection(
                    moods = defaultMoods,
                    onMoodClick = { mood ->
                        onMoodSelect(mood)
                    }
                )
            }

            // 3. Genre & Mood Discovery Boards
            if (genreSections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                MoodAndGenreBoard(
                    sections = genreSections,
                    onGenreClick = onGenreSelect
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 4. Personalized Recommendations / Billboard Top 100 Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isYouTubeConnected && syncedYouTubeTracks.isNotEmpty()) "Recommended For You" else "Trending Billboard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Text(
                    text = if (isYouTubeConnected && syncedYouTubeTracks.isNotEmpty()) "Tuned To Your Taste" else "Top 100",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            TopTracksGrid(
                tracks = if (isYouTubeConnected && syncedYouTubeTracks.isNotEmpty()) syncedYouTubeTracks else (if (tracks.isNotEmpty()) tracks else defaultTopTracks),
                onTrackClick = { track, queue ->
                    onTrackSelect(track, queue)
                }
            )
        }

        // Fixed Top App Bar
        UnboundTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            userAvatarUrl = userAvatarUrl,
            accountName = accountName,
            isLoggedIn = isYouTubeConnected,
            onMenuClick = onMenuClick,
            onProfileClick = onProfileClick
        )
    }
}

@Composable
private fun TemporalCapsulesSection(
    state: DaypartingState,
    onCapsuleClick: (MoodCapsule) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = state.activeWindow.replace("_", " "),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundTertiary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Situational Mixes",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }

            // Hour Badge
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = String.format("%02d:00", state.localHour),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.capsules) { capsule ->
                CapsuleCard(capsule = capsule, onClick = { onCapsuleClick(capsule) })
            }
        }
    }
}

@Composable
private fun CapsuleCard(
    capsule: MoodCapsule,
    onClick: () -> Unit
) {
    val pillColor = try {
        Color(android.graphics.Color.parseColor(capsule.colorHex))
    } catch (_: Exception) {
        UnboundPrimary
    }

    Box(
        modifier = Modifier
            .width(190.dp)
            .height(115.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        pillColor.copy(alpha = 0.35f),
                        Color(0xFF141414)
                    )
                )
            )
            .border(1.dp, pillColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = capsule.tag,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = pillColor
                )

                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(pillColor.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play Station",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Column {
                Text(
                    text = capsule.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = capsule.description,
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun YouTubePreferencesSection(
    tracks: List<TrackItem>,
    accountName: String?,
    onTrackClick: (TrackItem) -> Unit,
    onPlayAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "FROM YOUR YOUTUBE ACCOUNT",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary,
                    letterSpacing = 1.sp
                )
                Text(
                    text = if (!accountName.isNullOrBlank()) "$accountName's Preferences" else "Your Music Preferences",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play All",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Play All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tracks) { track ->
                YouTubeTrackCard(track = track, onClick = { onTrackClick(track) })
            }
        }
    }
}

@Composable
private fun YouTubeTrackCard(
    track: TrackItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(136.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(136.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceGlassHighest)
                .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
        ) {
            if (track.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = track.coverUrl,
                    contentDescription = track.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LibraryMusic,
                        contentDescription = null,
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist,
            fontSize = 11.sp,
            color = OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PersonalizedWelcomeHeader(
    accountName: String?,
    userAvatarUrl: String?,
    syncedCount: Int,
    onProfileClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        UnboundPrimary.copy(alpha = 0.15f),
                        SurfaceGlassHighest
                    )
                )
            )
            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            .clickable(onClick = onProfileClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Google Avatar with vibrant glow border
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
                    .border(2.dp, UnboundPrimary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (!userAvatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = userAvatarUrl,
                        contentDescription = "Google Avatar",
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = (accountName?.take(1) ?: "U").uppercase(),
                        color = UnboundPrimary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "WELCOME BACK",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary,
                    letterSpacing = 1.5.sp
                )
                Text(
                    text = if (!accountName.isNullOrBlank()) accountName else "YouTube User",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ElectricBolt,
                        contentDescription = null,
                        tint = UnboundTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (syncedCount > 0) "Tuned to your YouTube taste • $syncedCount tracks" else "Tuned to your YouTube taste",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}


