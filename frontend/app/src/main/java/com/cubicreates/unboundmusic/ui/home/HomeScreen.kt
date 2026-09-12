/*
 * Package: com.cubicreates.unboundmusic.ui.home
 * File: HomeScreen.kt
 * Purpose: Production coordinator HomeScreen routing to PersonalizedHomeScreen (for logged-in YouTube users)
 *          or GuestHomeScreen (for offline/logged-out users with Billboard Top 100).
 * Subsystem: Home UI / Dual-Mode Coordinator
 */

package com.cubicreates.unboundmusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.cubicreates.unboundmusic.data.DaypartingState
import com.cubicreates.unboundmusic.data.GenreItemDto
import com.cubicreates.unboundmusic.data.GenreSectionDto
import com.cubicreates.unboundmusic.data.MoodCapsule
import com.cubicreates.unboundmusic.ui.components.MoodItem
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundTopAppBar
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    tracks: List<TrackItem> = defaultTopTracks,
    syncedYouTubeTracks: List<TrackItem> = emptyList(),
    userMixes: List<com.cubicreates.unboundmusic.data.MixDto> = emptyList(),
    smartShelves: List<com.cubicreates.unboundmusic.data.SmartShelfDto> = emptyList(),
    daypartingState: DaypartingState? = null,
    genreSections: List<GenreSectionDto> = emptyList(),
    userAvatarUrl: String? = null,
    accountName: String? = null,
    isYouTubeConnected: Boolean = false,
    isSyncing: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onMoodSelect: (MoodItem) -> Unit = {},
    onCapsuleSelect: (MoodCapsule) -> Unit = {},
    onGenreSelect: (GenreItemDto) -> Unit = {},
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
    onConnectClick: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // Dual-Mode Routing:
        // Mode 1: Logged-in YouTube user -> Personalized "My Music" (Zero Global Billboard Top 100)
        // Mode 2: Guest / Logged-out user -> Billboard Top 100 Charts & Connect CTA
        if (isYouTubeConnected) {
            PersonalizedHomeScreen(
                accountName = accountName,
                userAvatarUrl = userAvatarUrl,
                syncedTracks = syncedYouTubeTracks,
                userMixes = userMixes,
                daypartingState = daypartingState,
                isSyncing = isSyncing,
                isLoadingMore = isLoadingMore,
                onLoadMore = onLoadMore,
                onTrackSelect = onTrackSelect,
                onCapsuleSelect = onCapsuleSelect,
                onProfileClick = onProfileClick,
                onSyncClick = onSyncClick
            )
        } else {
            GuestHomeScreen(
                tracks = tracks,
                smartShelves = smartShelves,
                daypartingState = daypartingState,
                genreSections = genreSections,
                onTrackSelect = onTrackSelect,
                onMoodSelect = onMoodSelect,
                onCapsuleSelect = onCapsuleSelect,
                onGenreSelect = onGenreSelect,
                onConnectClick = onConnectClick
            )
        }

        // Fixed Top App Bar across both experiences
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
