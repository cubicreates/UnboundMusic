/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: GuestHomeScreen.kt
 * Purpose: Public Guest/Offline HomeScreen displaying Global Billboard Top 100, temporal capsules, and Connect YouTube CTA.
 * Subsystem: Home UI / Guest Presentation
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
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.DaypartingState
import com.cubicreates.unboundmusic.data.GenreItemDto
import com.cubicreates.unboundmusic.data.GenreSectionDto
import com.cubicreates.unboundmusic.data.MoodCapsule
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.MoodItem
import com.cubicreates.unboundmusic.ui.components.MoodsSection
import com.cubicreates.unboundmusic.ui.components.TopTracksGrid
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.VibeAIStatusCard
import com.cubicreates.unboundmusic.ui.components.VibePromptBar
import com.cubicreates.unboundmusic.ui.components.defaultMoods
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary
import coil.compose.AsyncImage

@Composable
fun GuestHomeScreen(
    modifier: Modifier = Modifier,
    tracks: List<TrackItem> = defaultTopTracks,
    smartShelves: List<com.cubicreates.unboundmusic.data.SmartShelfDto> = emptyList(),
    daypartingState: DaypartingState? = null,
    genreSections: List<GenreSectionDto> = emptyList(),
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onMoodSelect: (MoodItem) -> Unit = {},
    onCapsuleSelect: (MoodCapsule) -> Unit = {},
    onGenreSelect: (GenreItemDto) -> Unit = {},
    onAlbumPlaylistClick: (id: String, title: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onConnectClick: () -> Unit = {},
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToQueue: (TrackItem) -> Unit = {},
    onDownload: (TrackItem) -> Unit = {},
    selectedMood: String = "All",
    moodTracks: List<TrackItem> = emptyList(),
    isMoodLoading: Boolean = false,
    onMoodFilterSelect: (String) -> Unit = {},
    onStartRadio: (TrackItem) -> Unit = {},
    isVibeLoading: Boolean = false,
    vibeState: VibeSearchUiState = VibeSearchUiState.Idle,
    onVibeSubmit: (String) -> Unit = {},
    onClearVibe: () -> Unit = {}
) {
    val hour = remember {
        java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
    }
    val greeting = remember(hour) {
        when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..22 -> "Good evening"
            else -> "Good night"
        }
    }

    val isVibeActive = vibeState is VibeSearchUiState.Success && vibeState.radioTracks.isNotEmpty()
    val quickPicksTracks = remember(tracks, selectedMood, moodTracks, vibeState) {
        if (isVibeActive) {
            (vibeState as VibeSearchUiState.Success).radioTracks
        } else if (selectedMood.equals("All", ignoreCase = true)) {
            tracks.take(16)
        } else {
            if (moodTracks.isNotEmpty()) {
                moodTracks.take(16)
            } else {
                tracks.take(16)
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // Ambient dominant aura mesh at top of Home
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(340.dp)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            UnboundPrimary.copy(alpha = 0.18f),
                            UnboundTertiary.copy(alpha = 0.05f),
                            Color.Transparent
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            // 0. Mood/Moment Filter Pills
            MoodFilterChipsRow(
                selectedMood = selectedMood,
                onMoodSelected = onMoodFilterSelect
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Time-Aware Salutation Pill
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$greeting, Explorer".uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = UnboundPrimary,
                    letterSpacing = 1.2.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Conversational Natural Language Vibe Bar
            VibePromptBar(
                isLoading = isVibeLoading,
                onVibeSubmit = onVibeSubmit
            )

            val isVibeLoadingNow = isVibeLoading || vibeState is VibeSearchUiState.Loading

            // Vibe AI Status / Offline Intelligence Card
            VibeAIStatusCard(
                isOfflineReady = true,
                statusText = when {
                    isVibeLoadingNow -> "Vibe AI: Analyzing prompt & curating tracks..."
                    isVibeActive -> "Vibe AI: \"${(vibeState as VibeSearchUiState.Success).vibeResult.originalPrompt}\" active"
                    else -> "Vibe AI Engine: Offline Ready (SmolLM2 135M Active)"
                }
            )

            // Quick Picks Grid (dynamically replaced by Vibe Search or Billboard Top Tracks)
            if (quickPicksTracks.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                val vibePrompt = (vibeState as? VibeSearchUiState.Success)?.vibeResult?.originalPrompt
                QuickPicksSection(
                    title = when {
                        isVibeActive -> "Vibe: \"$vibePrompt\""
                        isVibeLoadingNow -> "Tuning Vibe Radio..."
                        selectedMood.equals("All", ignoreCase = true) -> "Trending Quick Picks"
                        else -> "$selectedMood Picks"
                    },
                    subtitle = when {
                        isVibeActive -> "Curated Vibe Tracks (${quickPicksTracks.size})"
                        isVibeLoadingNow -> "Synthesizing and fetching tracks for your mood..."
                        selectedMood.equals("All", ignoreCase = true) -> "Start a radio or continuous mix"
                        isMoodLoading -> "Fetching $selectedMood soundtrack..."
                        else -> "Curated $selectedMood soundtrack"
                    },
                    tracks = quickPicksTracks,
                    currentTrackId = currentTrackId,
                    isPlaying = isPlaying,
                    isLoading = isVibeLoadingNow,
                    onTrackSelect = onTrackSelect,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onDownload = onDownload,
                    onStartRadio = onStartRadio,
                    onReset = if (isVibeActive) onClearVibe else null
                )
            }

        // Native YouTube-esque Algorithmic Smart Shelves (Variations, Quick Picks, Artist Spotlights)
        if (smartShelves.isNotEmpty()) {
            Spacer(modifier = Modifier.height(24.dp))
            SmartShelvesSection(
                shelves = smartShelves,
                onTrackSelect = onTrackSelect,
                onAlbumPlaylistClick = onAlbumPlaylistClick
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 2. Situational Mood Capsules
        if (daypartingState != null && daypartingState.capsules.isNotEmpty()) {
            GuestTemporalCapsulesSection(
                state = daypartingState,
                onCapsuleClick = onCapsuleSelect
            )
        } else {
            MoodsSection(
                moods = defaultMoods,
                onMoodClick = onMoodSelect
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

        // 4. Global Billboard Top 100 Header & Grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Public,
                    contentDescription = null,
                    tint = UnboundPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Trending Billboard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
            }
            Text(
                text = "Global Top 100",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = UnboundPrimary
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        TopTracksGrid(
            tracks = if (tracks.isNotEmpty()) tracks else defaultTopTracks,
            onTrackClick = { track, queue ->
                onTrackSelect(track, queue)
            }
        )
    }
}
}

@Composable
private fun GuestTemporalCapsulesSection(
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
                GuestCapsuleCard(capsule = capsule, onClick = { onCapsuleClick(capsule) })
            }
        }
    }
}

@Composable
private fun GuestCapsuleCard(
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
private fun SmartShelvesSection(
    shelves: List<com.cubicreates.unboundmusic.data.SmartShelfDto>,
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit,
    onAlbumPlaylistClick: (id: String, title: String, coverUrl: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        shelves.forEach { shelf ->
            if (shelf.tracks.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Text(
                            text = shelf.title,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        if (shelf.subtitle.isNotBlank()) {
                            Text(
                                text = shelf.subtitle,
                                fontSize = 12.sp,
                                color = OnSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(shelf.tracks, key = { it.id }) { track ->
                            val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_")
                            val isPlaylist = track.itemType.equals("playlist", ignoreCase = true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL")
                            SmartShelfTrackCard(
                                track = track,
                                onClick = {
                                    if (isAlbum || isPlaylist) {
                                        val targetId = track.browseId.ifBlank { track.id }
                                        onAlbumPlaylistClick(targetId, track.title, track.coverUrl)
                                    } else {
                                        onTrackSelect(track, shelf.tracks)
                                    }
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
private fun SmartShelfTrackCard(
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
        ) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = track.title,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x66000000))
                        )
                    )
            )
            Box(
                modifier = Modifier
                    .padding(8.dp)
                    .size(28.dp)
                    .align(Alignment.BottomEnd)
                    .clip(CircleShape)
                    .background(UnboundPrimary),
                contentAlignment = Alignment.Center
            ) {
                val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_")
                Icon(
                    imageVector = if (isAlbum) Icons.Default.Album else Icons.Default.PlayArrow,
                    contentDescription = if (isAlbum) "Album" else "Play",
                    tint = Color.Black,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = track.title,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = track.artist,
            color = OnSurfaceVariant,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
