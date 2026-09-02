package com.cubicreates.unboundmusic.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cubicreates.unboundmusic.ui.components.FloatingMiniPlayer
import com.cubicreates.unboundmusic.ui.components.MoodItem
import com.cubicreates.unboundmusic.ui.components.MoodsSection
import com.cubicreates.unboundmusic.ui.components.NavigationTab
import com.cubicreates.unboundmusic.ui.components.TopTracksGrid
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundBottomNavBar
import com.cubicreates.unboundmusic.ui.components.UnboundTopAppBar
import com.cubicreates.unboundmusic.ui.components.defaultMoods
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    tracks: List<TrackItem> = defaultTopTracks,
    onTrackSelect: (TrackItem) -> Unit = {},
    onMoodSelect: (MoodItem) -> Unit = {},
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
                .padding(top = 72.dp, bottom = 140.dp)
        ) {
            // Moods & Moments Horizontal Carousel
            MoodsSection(
                moods = defaultMoods,
                onMoodClick = { mood ->
                    onMoodSelect(mood)
                }
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Global Top 100 2-Column Grid
            TopTracksGrid(
                tracks = if (tracks.isNotEmpty()) tracks else defaultTopTracks,
                onTrackClick = { track ->
                    onTrackSelect(track)
                }
            )
        }

        // Fixed Top App Bar
        UnboundTopAppBar(
            modifier = Modifier.align(Alignment.TopCenter),
            onMenuClick = onMenuClick,
            onProfileClick = onProfileClick
        )
    }
}
