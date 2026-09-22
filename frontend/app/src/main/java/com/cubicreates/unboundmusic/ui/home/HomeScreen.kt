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
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.MoodItem
import com.cubicreates.unboundmusic.ui.components.TrackItem
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
    currentTrackId: String = "",
    isPlaying: Boolean = false,
    onLoadMore: () -> Unit = {},
    onTrackSelect: (track: TrackItem, queue: List<TrackItem>) -> Unit = { _, _ -> },
    onMoodSelect: (MoodItem) -> Unit = {},
    onCapsuleSelect: (MoodCapsule) -> Unit = {},
    onGenreSelect: (GenreItemDto) -> Unit = {},
    onMixClick: (com.cubicreates.unboundmusic.data.MixDto) -> Unit = {},
    onAlbumPlaylistClick: (id: String, title: String, coverUrl: String) -> Unit = { _, _, _ -> },
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onSyncClick: () -> Unit = {},
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
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        // Dual-Mode Routing:
        // Mode 1: Logged-in YouTube user -> Strictly Personal Feed + Vibe AI
        // Mode 2: Guest / Logged-out user -> Billboard Top 100 Charts & Connect CTA
        if (isYouTubeConnected) {
            PersonalizedHomeScreen(
                accountName = accountName,
                userAvatarUrl = userAvatarUrl,
                syncedTracks = syncedYouTubeTracks,
                userMixes = userMixes,
                smartShelves = smartShelves,
                daypartingState = daypartingState,
                isSyncing = isSyncing,
                isLoadingMore = isLoadingMore,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                onLoadMore = onLoadMore,
                onTrackSelect = onTrackSelect,
                onMoodSelect = onMoodSelect,
                onCapsuleSelect = onCapsuleSelect,
                onMixClick = onMixClick,
                onAlbumPlaylistClick = onAlbumPlaylistClick,
                onProfileClick = onProfileClick,
                onSyncClick = onSyncClick,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onDownload = onDownload,
                selectedMood = selectedMood,
                moodTracks = moodTracks,
                isMoodLoading = isMoodLoading,
                onMoodFilterSelect = onMoodFilterSelect,
                onStartRadio = onStartRadio,
                isVibeLoading = isVibeLoading,
                vibeState = vibeState,
                onVibeSubmit = onVibeSubmit,
                onClearVibe = onClearVibe
            )
        } else {
            GuestHomeScreen(
                tracks = tracks,
                smartShelves = smartShelves,
                daypartingState = daypartingState,
                genreSections = genreSections,
                currentTrackId = currentTrackId,
                isPlaying = isPlaying,
                onTrackSelect = onTrackSelect,
                onMoodSelect = onMoodSelect,
                onCapsuleSelect = onCapsuleSelect,
                onGenreSelect = onGenreSelect,
                onAlbumPlaylistClick = onAlbumPlaylistClick,
                onConnectClick = onConnectClick,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onDownload = onDownload,
                selectedMood = selectedMood,
                moodTracks = moodTracks,
                isMoodLoading = isMoodLoading,
                onMoodFilterSelect = onMoodFilterSelect,
                onStartRadio = onStartRadio,
                isVibeLoading = isVibeLoading,
                vibeState = vibeState,
                onVibeSubmit = onVibeSubmit,
                onClearVibe = onClearVibe
            )
        }
    }
}
