/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: SignedInHomeScreen.kt
 * Purpose: Authenticated User HomeScreen ("My Music") orchestrating personal YouTube library, custom mixes, and taste recommendations.
 * Subsystem: Home UI / Signed-In Presentation
 */

package com.cubicreates.unboundmusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.DaypartingState
import com.cubicreates.unboundmusic.data.MixDto
import com.cubicreates.unboundmusic.data.MoodCapsule
import com.cubicreates.unboundmusic.data.SmartShelfDto
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.MoodItem
import com.cubicreates.unboundmusic.ui.components.MoodsSection
import com.cubicreates.unboundmusic.ui.components.defaultMoods
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.VibeAIStatusCard
import com.cubicreates.unboundmusic.ui.components.VibePromptBar
import com.cubicreates.unboundmusic.ui.home.components.PersonalizedCapsulesSection
import com.cubicreates.unboundmusic.ui.home.components.PersonalizedGridTrackCard
import com.cubicreates.unboundmusic.ui.home.components.PersonalizedLikedCarousel
import com.cubicreates.unboundmusic.ui.home.components.PersonalizedMixHeroCard
import com.cubicreates.unboundmusic.ui.home.components.PersonalizedMixesSection
import com.cubicreates.unboundmusic.ui.home.components.PersonalizedShelfItem
import com.cubicreates.unboundmusic.ui.home.components.PersonalizedSyncingPlaceholder
import com.cubicreates.unboundmusic.ui.home.components.PersonalizedWelcomeCard
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

@Composable
fun SignedInHomeScreen(
    modifier: Modifier = Modifier,
    accountName: String? = null,
    userAvatarUrl: String? = null,
    syncedTracks: List<TrackItem> = emptyList(),
    userMixes: List<MixDto> = emptyList(),
    smartShelves: List<SmartShelfDto> = emptyList(),
    daypartingState: DaypartingState? = null,
    isSyncing: Boolean = false,
    isLoadingMore: Boolean = false,
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    onLoadMore: () -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onMoodSelect: (MoodItem) -> Unit = {},
    onCapsuleSelect: (MoodCapsule) -> Unit = {},
    onMixClick: (MixDto) -> Unit = {},
    onAlbumPlaylistClick: (id: String, title: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onArtistClick: (artistName: String) -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
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
    val listState = rememberLazyListState()

    // Quick picks tracks: personal taste (Vibe Search, or user smart shelf "Quick picks", or personal synced tracks)
    val quickPicksShelf = smartShelves.find { it.title.contains("quick", ignoreCase = true) }
    val isVibeActive = vibeState is VibeSearchUiState.Success && vibeState.radioTracks.isNotEmpty()
    val quickPicksTracks = remember(quickPicksShelf, syncedTracks, selectedMood, moodTracks, vibeState) {
        if (isVibeActive) {
            (vibeState as VibeSearchUiState.Success).radioTracks
        } else if (selectedMood.equals("All", ignoreCase = true)) {
            quickPicksShelf?.tracks?.ifEmpty { null } ?: syncedTracks.take(16)
        } else {
            if (moodTracks.isNotEmpty()) {
                moodTracks.take(16)
            } else {
                quickPicksShelf?.tracks?.ifEmpty { null } ?: syncedTracks.take(16)
            }
        }
    }

    // Detect when user has scrolled near bottom (within 3 items of the end)
    val shouldLoadMore by remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItems > 0 && lastVisibleIndex >= totalItems - 3
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore && !isLoadingMore) {
            onLoadMore()
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

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, bottom = 24.dp)
        ) {
            // 0. Situational Mood Filter Chips Row
            item(key = "mood_chips") {
                MoodFilterChipsRow(
                    selectedMood = selectedMood,
                    onMoodSelected = onMoodFilterSelect
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

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
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Conversational Natural Language Vibe Bar
            item(key = "vibe_prompt_bar") {
                VibePromptBar(
                    isLoading = isVibeLoading,
                    onVibeSubmit = onVibeSubmit
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            val isVibeLoadingNow = isVibeLoading || vibeState is VibeSearchUiState.Loading

            // Vibe AI Status / Offline Intelligence Card
            item(key = "vibe_status_card") {
                VibeAIStatusCard(
                    isOfflineReady = true,
                    statusText = when {
                        isVibeLoadingNow -> "Vibe AI: Analyzing prompt & curating tracks..."
                        isVibeActive -> {
                            val vr = (vibeState as VibeSearchUiState.Success).vibeResult
                            "Vibe AI: \"${vr.originalPrompt}\" active"
                        }
                        else -> "Vibe AI Engine: Offline Ready (SmolLM2 135M Active)"
                    }
                )
                Spacer(modifier = Modifier.height(14.dp))
            }

            // 2. 4-Row Snapping Quick Picks Grid (dynamically replaced by Vibe Search or user shelf)
            if (quickPicksTracks.isNotEmpty()) {
                item(key = "quick_picks_grid") {
                    val vibePrompt = (vibeState as? VibeSearchUiState.Success)?.vibeResult?.originalPrompt
                    QuickPicksSection(
                        title = when {
                            isVibeActive -> "Vibe: \"$vibePrompt\""
                            isVibeLoadingNow -> "Tuning Vibe Radio..."
                            selectedMood.equals("All", ignoreCase = true) -> "Quick Picks"
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
                    Spacer(modifier = Modifier.height(24.dp))
                }
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
                        onMixClick = onMixClick
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // 5. Time-Aware Situational Mood Mixes or Curated Moods
            if (daypartingState != null && daypartingState.capsules.isNotEmpty()) {
                item(key = "mood_capsules") {
                    PersonalizedCapsulesSection(
                        state = daypartingState,
                        onCapsuleClick = onCapsuleSelect
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }
            } else {
                item(key = "curated_moods") {
                    MoodsSection(
                        moods = defaultMoods,
                        onMoodClick = onMoodSelect
                    )
                    Spacer(modifier = Modifier.height(28.dp))
                }
            }

            // 6. Authentic YouTube Music Personalized Shelves ("Mixed for you", "Listen again", "Quick picks", etc.)
            if (smartShelves.isNotEmpty()) {
                smartShelves.forEach { shelf ->
                    if (shelf.tracks.isNotEmpty()) {
                        item(key = "smart_shelf_${shelf.id}") {
                            PersonalizedShelfItem(
                                shelf = shelf,
                                onTrackSelect = onTrackSelect,
                                onAlbumPlaylistClick = onAlbumPlaylistClick,
                                onArtistClick = onArtistClick
                            )
                            Spacer(modifier = Modifier.height(28.dp))
                        }
                    }
                }
            }

            // 7. "Tuned To Your Taste" Liked Tracks Section (if syncedTracks.isNotEmpty)
            if (syncedTracks.isNotEmpty()) {
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
                            text = "${syncedTracks.size} Tracks",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                }

                // Infinite Music Grid in 2-column chunks
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

                // Infinite Scroll Loading Indicator / Continuous Stream Footer
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
            } else if (smartShelves.isEmpty()) {
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
}
