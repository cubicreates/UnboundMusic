package com.cubicreates.unboundmusic.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary

private const val DEFAULT_MINI_COVER = "https://lh3.googleusercontent.com/aida-public/AB6AXuDxptiPrbxiFF1ejcwv1bMxAxWGqdHo_tv1apa2CWBmrg9fbeGklO1YfiCol1v84WTgNqo5Ct9cCnxBKLb_VRP4CW3PSQMGBBhWsaFTR3DHZykgA1kS2k88u2wtzfMsL7I67qnw5s4bfzQkyntZw0iLIJJ3RnhZwHsFgRAVTEsQFqUvpFQYp8AhEkJOchLPC76P0Qxepqec1aT01w9G_oJDGaM0QRHnD0d3cUBxQ_vcOy_bf-g7Xin1eg"

@Composable
fun FloatingMiniPlayer(
    modifier: Modifier = Modifier,
    title: String = "Neon Ascend",
    artist: String = "Luna Ray",
    coverUrl: String = DEFAULT_MINI_COVER,
    isPlaying: Boolean = false,
    isFavorite: Boolean = true,
    onPlayerClick: () -> Unit = {},
    onFavoriteToggle: () -> Unit = {},
    onPlayPauseToggle: () -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shadow(elevation = 20.dp, shape = RoundedCornerShape(20.dp), spotColor = Color.Black)
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceGlassHighest)
            .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(20.dp))
            .clickable(onClick = onPlayerClick)
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Artwork + Title + Artist
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(10.dp))
                ) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = artist,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Right: Favorite & Play Buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = onFavoriteToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) UnboundPrimary else OnSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onPlayPauseToggle,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = OnSurface
                    )
                }
            }
        }
    }
}
