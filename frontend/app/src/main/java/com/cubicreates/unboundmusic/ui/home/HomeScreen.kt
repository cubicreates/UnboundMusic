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

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    tracks: List<TrackItem> = defaultTopTracks,
    daypartingState: DaypartingState? = null,
    genreSections: List<GenreSectionDto> = emptyList(),
    userAvatarUrl: String? = null,
    accountName: String? = null,
    isYouTubeConnected: Boolean = false,
    onTrackSelect: (TrackItem) -> Unit = {},
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
            // 1. Time-Aware Situational Mood Capsules Section
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

            // 2. Genre & Mood Discovery Boards
            if (genreSections.isNotEmpty()) {
                Spacer(modifier = Modifier.height(28.dp))
                MoodAndGenreBoard(
                    sections = genreSections,
                    onGenreClick = onGenreSelect
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 3. Regional Billboard Top 100 Grid
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Trending Billboard",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Text(
                    text = "Top 100",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

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
