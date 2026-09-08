package com.cubicreates.unboundmusic.ui.components

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest

data class TrackItem(
    val title: String,
    val artist: String,
    val coverUrl: String,
    val streamUrl: String = "",
    val id: String = "",
    val album: String = "",
    val durationMs: Long = 0,
    val source: String = "youtube",
    val isExplicit: Boolean = false,
    val artists: List<String> = emptyList()
)

val defaultTopTracks = listOf(
    TrackItem(
        id = "T6eKbf38x8I",
        title = "Not Like Us",
        artist = "Kendrick Lamar",
        coverUrl = "https://i.ytimg.com/vi/T6eKbf38x8I/hqdefault.jpg",
        streamUrl = "",
        durationMs = 274000,
        source = "youtube"
    ),
    TrackItem(
        id = "eVli-tstM5E",
        title = "Espresso",
        artist = "Sabrina Carpenter",
        coverUrl = "https://i.ytimg.com/vi/eVli-tstM5E/hqdefault.jpg",
        streamUrl = "",
        durationMs = 175000,
        source = "youtube"
    ),
    TrackItem(
        id = "4QIZ6g8I_8Y",
        title = "I Had Some Help",
        artist = "Post Malone ft. Morgan Wallen",
        coverUrl = "https://i.ytimg.com/vi/4QIZ6g8I_8Y/hqdefault.jpg",
        streamUrl = "",
        durationMs = 178000,
        source = "youtube"
    ),
    TrackItem(
        id = "d5gxZXCj68g",
        title = "BIRDS OF A FEATHER",
        artist = "Billie Eilish",
        coverUrl = "https://i.ytimg.com/vi/d5gxZXCj68g/hqdefault.jpg",
        streamUrl = "",
        durationMs = 194000,
        source = "youtube"
    ),
    TrackItem(
        id = "YQHsXMglC9A",
        title = "Hello",
        artist = "Adele",
        coverUrl = "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg",
        streamUrl = "",
        durationMs = 367000,
        source = "youtube"
    ),
    TrackItem(
        id = "Oa_RSwwpPaA",
        title = "Beautiful Things",
        artist = "Benson Boone",
        coverUrl = "https://i.ytimg.com/vi/Oa_RSwwpPaA/hqdefault.jpg",
        streamUrl = "",
        durationMs = 180000,
        source = "youtube"
    ),
    TrackItem(
        id = "044B_2wG13Q",
        title = "MILLION DOLLAR BABY",
        artist = "Tommy Richman",
        coverUrl = "https://i.ytimg.com/vi/044B_2wG13Q/hqdefault.jpg",
        streamUrl = "",
        durationMs = 155000,
        source = "youtube"
    ),
    TrackItem(
        id = "t7bQwwqW-Hc",
        title = "A Bar Song (Tipsy)",
        artist = "Shaboozey",
        coverUrl = "https://i.ytimg.com/vi/t7bQwwqW-Hc/hqdefault.jpg",
        streamUrl = "",
        durationMs = 171000,
        source = "youtube"
    )
)

@Composable
fun TopTracksGrid(
    modifier: Modifier = Modifier,
    tracks: List<TrackItem> = defaultTopTracks,
    onTrackClick: (TrackItem) -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Text(
            text = "Global Top 100",
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            letterSpacing = (-0.01).sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // 2-column Grid Pairs
        val chunked = tracks.chunked(2)
        chunked.forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowItems.forEach { track ->
                    TrackCard(
                        track = track,
                        modifier = Modifier.weight(1f),
                        onClick = { onTrackClick(track) }
                    )
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun TrackCard(
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
                .shadow(elevation = 12.dp, shape = RoundedCornerShape(16.dp), spotColor = Color.Black)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceGlassHighest)
                .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
        ) {
            AsyncImage(
                model = track.coverUrl,
                contentDescription = track.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = track.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Normal,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = track.artist,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
