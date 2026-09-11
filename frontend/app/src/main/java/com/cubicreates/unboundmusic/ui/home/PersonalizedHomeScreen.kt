/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: PersonalizedHomeScreen.kt
 * Purpose: Authenticated User HomeScreen ("My Music") rendering personal YouTube library, custom mixes, and taste recommendations.
 * Subsystem: Home UI / Personalized Presentation
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cubicreates.unboundmusic.data.DaypartingState
import com.cubicreates.unboundmusic.data.MixDto
import com.cubicreates.unboundmusic.data.MoodCapsule
import com.cubicreates.unboundmusic.ui.components.TopTracksGrid
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember

@Composable
fun PersonalizedHomeScreen(
    modifier: Modifier = Modifier,
    accountName: String? = null,
    userAvatarUrl: String? = null,
    syncedTracks: List<TrackItem> = emptyList(),
    userMixes: List<MixDto> = emptyList(),
    daypartingState: DaypartingState? = null,
    isSyncing: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onCapsuleSelect: (MoodCapsule) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSyncClick: () -> Unit = {}
) {
    val listState = rememberLazyListState()

    // Detect when user has scrolled near bottom (within 3 items of the end)
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore && syncedTracks.isNotEmpty()) {
            onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(top = 76.dp, bottom = 24.dp)
    ) {
        // 1. Personalized Google Welcome Card
        item(key = "welcome_card") {
            PersonalizedWelcomeCard(
                accountName = accountName,
                userAvatarUrl = userAvatarUrl,
                syncedCount = syncedTracks.size,
                isSyncing = isSyncing,
                onProfileClick = onProfileClick,
                onSyncClick = onSyncClick
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 2. "Jump Back In" Liked Songs Carousel (if tracks synced)
        if (syncedTracks.isNotEmpty()) {
            item(key = "liked_carousel") {
                PersonalizedLikedCarousel(
                    tracks = syncedTracks,
                    accountName = accountName,
                    onTrackClick = { track ->
                        onTrackSelect(track, syncedTracks)
                    },
                    onPlayAll = {
                        if (syncedTracks.isNotEmpty()) {
                            onTrackSelect(syncedTracks.first(), syncedTracks)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(28.dp))
            }

            // 3. YouTube Infinite Mix Hero Card
            item(key = "hero_station") {
                PersonalizedMixHeroCard(
                    tracks = syncedTracks,
                    onPlayStation = {
                        if (syncedTracks.isNotEmpty()) {
                            onTrackSelect(syncedTracks.first(), syncedTracks.shuffled())
                        }
                    }
                )
                Spacer(modifier = Modifier.height(30.dp))
            }
        }

        // 4. YouTube Mixes & Artist Radios Shelf (My Supermix, Artist Mixes, Replay & Discover)
        if (userMixes.isNotEmpty()) {
            item(key = "user_mixes") {
                PersonalizedMixesSection(
                    mixes = userMixes,
                    onMixClick = { _ ->
                        if (syncedTracks.isNotEmpty()) {
                            onTrackSelect(syncedTracks.first(), syncedTracks.shuffled())
                        }
                    }
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // 5. Time-Aware Situational Mood Mixes
        if (daypartingState != null && daypartingState.capsules.isNotEmpty()) {
            item(key = "mood_capsules") {
                PersonalizedCapsulesSection(
                    state = daypartingState,
                    onCapsuleClick = onCapsuleSelect
                )
                Spacer(modifier = Modifier.height(28.dp))
            }
        }

        // 6. "Tuned To Your Taste" Header (Strictly personal music, zero Billboard Top 100)
        item(key = "taste_header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.ThumbUp,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Tuned To Your Taste",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
                Text(
                    text = if (syncedTracks.isNotEmpty()) {
                        "${syncedTracks.size} Tracks"
                    } else if (isSyncing) {
                        "Syncing..."
                    } else {
                        "0 Tracks"
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSyncing) UnboundPrimary else OnSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(14.dp))
        }

        if (syncedTracks.isNotEmpty()) {
            // 7. Infinite Music Grid in 2-column chunks
            val chunked = syncedTracks.chunked(2)
            items(
                count = chunked.size,
                key = { index -> chunked[index].firstOrNull()?.id ?: "$index" }
            ) { index ->
                val rowItems = chunked[index]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    rowItems.forEach { track ->
                        PersonalizedGridTrackCard(
                            track = track,
                            modifier = Modifier.weight(1f),
                            onClick = { onTrackSelect(track, syncedTracks) }
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 8. Infinite Scroll Loading Indicator / Continuous Stream Footer
            item(key = "infinite_footer") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingMore) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = UnboundPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Loading more music for you...",
                                fontSize = 13.sp,
                                color = OnSurfaceVariant,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    } else {
                        Text(
                            text = "• Infinite Music Feed •",
                            fontSize = 11.sp,
                            color = OnSurfaceVariant.copy(alpha = 0.5f),
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
        } else {
            // Loading / Ingestion state — strictly personal, NO Global Top 100
            item(key = "sync_placeholder") {
                PersonalizedSyncingPlaceholder(
                    isSyncing = isSyncing,
                    onSyncClick = onSyncClick
                )
            }
        }
    }
}

@Composable
private fun PersonalizedGridTrackCard(
    track: TrackItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlassHighest)
                .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
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
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = track.artist,
            fontSize = 12.sp,
            color = OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PersonalizedWelcomeCard(
    accountName: String?,
    userAvatarUrl: String?,
    syncedCount: Int,
    isSyncing: Boolean,
    onProfileClick: () -> Unit,
    onSyncClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        UnboundPrimary.copy(alpha = 0.22f),
                        SurfaceGlassHighest,
                        Color(0xFF161616)
                    )
                )
            )
            .border(1.dp, BorderGlass, RoundedCornerShape(22.dp))
            .clickable(onClick = onProfileClick)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Google Avatar with vibrant glow border
            Box(
                modifier = Modifier
                    .size(56.dp)
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
                        fontSize = 24.sp,
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
                    text = if (!accountName.isNullOrBlank()) accountName else "Your Account",
                    fontSize = 19.sp,
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
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (syncedCount > 0) "Tuned to your YouTube taste • $syncedCount tracks" else "YouTube Music Connected",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant
                    )
                }
            }

            // Sync / Refresh action icon
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, CircleShape)
                    .clickable(onClick = onSyncClick),
                contentAlignment = Alignment.Center
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = UnboundPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh Sync",
                        tint = OnSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PersonalizedLikedCarousel(
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
                    text = "RECENTLY WATCHED MUSIC",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = if (!accountName.isNullOrBlank()) "$accountName's Music" else "Your YouTube Taste",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(10.dp))
                    .clickable(onClick = onPlayAll)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
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
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(tracks) { track ->
                PersonalizedTrackCard(track = track, onClick = { onTrackClick(track) })
            }
        }
    }
}

@Composable
private fun PersonalizedTrackCard(
    track: TrackItem,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceGlassHighest)
                .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
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
                        modifier = Modifier.size(38.dp)
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
private fun PersonalizedMixHeroCard(
    tracks: List<TrackItem>,
    onPlayStation: () -> Unit
) {
    val nonMusicKeywords = listOf("shorts", "sam and monica", "vlog", "prank", "tiktok", "couple", "reaction", "gaming")
    val sampleArtists = tracks
        .map { it.artist }
        .filter { artist ->
            val lower = artist.lowercase()
            nonMusicKeywords.none { lower.contains(it) }
        }
        .distinct()
        .take(3)
        .joinToString(", ")

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2A1535),
                        Color(0xFF171322),
                        Color(0xFF0F0E17)
                    )
                )
            )
            .border(1.dp, UnboundPrimary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .clickable(onClick = onPlayStation)
            .padding(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = UnboundTertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "SMART RADIO STATION",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = UnboundTertiary,
                        letterSpacing = 1.2.sp
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Your YouTube Infinite Mix",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (sampleArtists.isNotBlank()) "Seeded with $sampleArtists & more" else "Continuous music tuned to your preferences",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(UnboundPrimary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Start Station",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
private fun PersonalizedMixesSection(
    mixes: List<MixDto>,
    onMixClick: (MixDto) -> Unit
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
                    text = "MIXES FOR YOU",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "YouTube Mixes & Radios",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(mixes) { mix ->
                PersonalizedMixCard(mix = mix, onClick = { onMixClick(mix) })
            }
        }
    }
}

@Composable
private fun PersonalizedMixCard(
    mix: MixDto,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(148.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .size(148.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlassHighest)
                .border(1.dp, BorderGlass, RoundedCornerShape(16.dp))
        ) {
            if (mix.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = mix.coverUrl,
                    contentDescription = mix.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = UnboundTertiary,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            // Overlay play badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.72f))
                    .border(1.dp, UnboundPrimary.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Mix",
                    tint = UnboundPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = mix.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = mix.subtitle,
            fontSize = 11.sp,
            color = OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PersonalizedCapsulesSection(
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
                    text = "Time-Aware Mixes",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }

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
                PersonalizedCapsuleCard(capsule = capsule, onClick = { onCapsuleClick(capsule) })
            }
        }
    }
}

@Composable
private fun PersonalizedCapsuleCard(
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
private fun PersonalizedSyncingPlaceholder(
    isSyncing: Boolean,
    onSyncClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceGlassHighest)
            .border(1.dp, BorderGlass, RoundedCornerShape(20.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(UnboundPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = UnboundPrimary,
                        strokeWidth = 3.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = UnboundTertiary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = if (isSyncing) "Finding Your Music Taste..." else "Curating Your YouTube Feed",
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = OnSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = if (isSyncing)
                    "Analyzing your YouTube watch activity to find the songs and artists you love..."
                else
                    "Watch or listen to music on YouTube, and Unbound will automatically extract your tracks and curate your personalized feed.",
                fontSize = 12.sp,
                color = OnSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSyncClick,
                enabled = !isSyncing,
                colors = ButtonDefaults.buttonColors(
                    containerColor = UnboundPrimary,
                    contentColor = Color.White,
                    disabledContainerColor = UnboundPrimary.copy(alpha = 0.5f),
                    disabledContentColor = Color.White.copy(alpha = 0.7f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Analyzing YouTube Activity...",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Check YouTube Activity",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
