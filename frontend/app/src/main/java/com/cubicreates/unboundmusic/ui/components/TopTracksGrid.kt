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
    val streamUrl: String = ""
)

val defaultTopTracks = listOf(
    TrackItem(
        title = "Midnight Echoes",
        artist = "The Synthetics",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBw5t8Y1FbT4VMMSu_CQulQmGp9NUOJtIVO_QccREqF1-VNYU6GSYAWuoMmByxWZYoNCn3L5IxeFmiWiWLUmyFCraDaVtb-HwEt5URRvChTfV_uZsWzpihUxqK4Gy3WMtop5guc_DZL4kp3vhdB0h_k3m2I2LXz0F2u1n5LrmMN5pZD-zKepe463HeSdPU3rdcdbPdcy5MgEhVyqFfezTqKihs709zw-y3IippDKYvra8nmAm8Rj0MShw",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3"
    ),
    TrackItem(
        title = "Neon Ascend",
        artist = "Luna Ray",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDUk5k7o-AFNK6nRPQLO5ZGVX_ObIOJ_PHSsN-sNtK4NTAYxOyw-WiVgLr9dQmynk574XuTXZXahLLiTXcoehxJ7Q7mF5lYjKniWrh0ec_JLyxPbr7FPqL6ySfS5dPrCh_dq4oq00dLjRdFk_zzMEorsM0qU7IqVw9Nx-nJb6Vf7a2MiiEHTwhZE86Fcq7tOYBB6TAmlQGuEo1jQM1lBrvV1Dt6dTyZrXOuAMonapF1zwF7BFmk3fRs-A",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3"
    ),
    TrackItem(
        title = "Silent Orbit",
        artist = "Vanguard",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuDNhWWPTV2nAizIBx8UKBUTnFPWLoJX037Pv8X0RXMWsdwloS-8re433mK-O7us-lxGr4fwPefTcgEjmlwJNI04LWNIgzamgbTt1iFXVKtmO5YMqYl4_NUO8kWRtH8Tt5RWsxif3bssGv_PLj36VvAKzum8E_OSemNc-9FBIKYcFJxSrBuKcmLThk-GZ328_H5W_zR6zAUnW1NpikP16sQ-5_iHaibEYbM7KOVm1jFBll6pY0Vl3yNQBw",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3"
    ),
    TrackItem(
        title = "Raw Canvas",
        artist = "Art School Dropout",
        coverUrl = "https://lh3.googleusercontent.com/aida-public/AB6AXuBHG_W6rq5159KDGoP7W7B01Z73bLi41TVG9urI3QqTH_xJYP6lP8IdVBg6SHRNGHMf_cms-px7n5JXwpI7m4Yu8H5V4NM2DpAB9-YSsyP82-lJyW2O4F-OMQJg-b7buVkh7GnSEqxs-VxeMzPWC6sBQx7rBKJUAyzWM-Mb1XSHUqA7j7bYV-n4p6M_HxF7-6g_J68Uh3FfJPj__AvEFLDNVbJ8NfBLXeqiJIJ4y09XS6jAA5OmqaS91A",
        streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3"
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
