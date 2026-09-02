/*
 * Package: com.cubicreates.unboundmusic.ui.recap
 * File: RecapScreen.kt
 * Purpose: On-device "Unbound Recap" (Spotify Wrapped equivalent) presentation cards showing
 *          listening minutes, top tracks, top artists, decade distribution, and Shannon Entropy score.
 * Subsystem: Analytics / Recap UI
 */

package com.cubicreates.unboundmusic.ui.recap

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundTertiary

data class RecapData(
    val totalMinutes: Int = 1420,
    val topTracks: List<TrackItem> = emptyList(),
    val topArtists: List<String> = listOf("The Weeknd", "Daft Punk", "Hans Zimmer"),
    val topDecade: String = "2020s (68%)",
    val diversityScore: String = "8.7 / 10 (Eclectic Explorer)"
)

@Composable
fun RecapScreen(
    data: RecapData,
    onClose: () -> Unit = {},
    onShare: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = OnSurface
                    )
                }

                Text(
                    text = "Unbound Recap",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )

                IconButton(
                    onClick = onShare,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(SurfaceGlassHighest)
                        .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = UnboundPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Card 1: Total Listening Minutes Hero Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1E3C72),
                                Color(0xFF2A5298),
                                Color(0xFF4CD6FB).copy(alpha = 0.6f)
                            )
                        )
                    )
                    .padding(24.dp)
            ) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TOTAL PLAYTIME",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.8f),
                            letterSpacing = 1.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "${data.totalMinutes}",
                        fontSize = 54.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "minutes listened this month across offline & online audio",
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 2: Top Artists
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TOP ARTISTS",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }

                    data.topArtists.forEachIndexed { index, artist ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "#${index + 1}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = UnboundPrimary,
                                modifier = Modifier.width(32.dp)
                            )
                            Text(
                                text = artist,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Card 3: Taste Diversity Score (Shannon Entropy)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceGlassHighest)
                    .border(width = 1.dp, color = BorderGlass, shape = RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Equalizer,
                            contentDescription = null,
                            tint = UnboundTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "TASTE DIVERSITY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurfaceVariant,
                            letterSpacing = 1.sp
                        )
                    }

                    Text(
                        text = data.diversityScore,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )

                    Text(
                        text = "Primary era: ${data.topDecade}",
                        fontSize = 13.sp,
                        color = OnSurfaceVariant
                    )
                }
            }
        }
    }
}
