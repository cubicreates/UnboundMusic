/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: LibraryComponents.kt
 * Purpose: Reusable UI cards, list views, and data structures for Library screens.
 * Subsystem: Personal Library / UI Components
 */

package com.cubicreates.unboundmusic.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundTrackThumbnail
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import java.io.File

enum class LibrarySubView {
    HUB,
    TRACKS,
    AUDIOS,
    FOLDERS,
    FOLDER_TRACKS,
    FAVORITES,
    RECENT_PLAYED,
    DOWNLOADED
}

data class AudioFolder(
    val name: String,
    val pathDescription: String,
    val tracks: List<TrackItem>
)

fun extractAudioFolders(tracks: List<TrackItem>): List<AudioFolder> {
    val grouped = mutableMapOf<String, MutableList<TrackItem>>()
    tracks.forEach { track ->
        val folderName = when {
            track.source.contains("WhatsApp Voice", ignoreCase = true) -> "WhatsApp Voice Notes"
            track.source.contains("WhatsApp", ignoreCase = true) -> "WhatsApp Audio"
            track.source.contains("Telegram", ignoreCase = true) -> "Telegram Audio"
            track.source.contains("Download", ignoreCase = true) || track.source.contains("Unbound", ignoreCase = true) -> "Downloads"
            track.source.contains("Music", ignoreCase = true) -> "Music"
            track.source.isNotBlank() && !track.source.equals("youtube", ignoreCase = true) -> track.source
            track.streamUrl.startsWith("file://") -> {
                try {
                    val f = File(track.streamUrl.removePrefix("file://"))
                    f.parentFile?.name ?: "Device Audio"
                } catch (_: Exception) {
                    "Device Audio"
                }
            }
            else -> "Device Audio"
        }
        grouped.getOrPut(folderName) { mutableListOf() }.add(track)
    }
    return grouped.map { (name, list) ->
        AudioFolder(
            name = name,
            pathDescription = "Device Storage • ${list.size} audio files",
            tracks = list
        )
    }.sortedByDescending { it.tracks.size }
}

fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSecs = durationMs / 1000
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%d:%02d".format(mins, secs)
}

// ==================== Modern Library Hub Components ====================

@Composable
fun ModernHeroMySongsCard(
    count: Int,
    onClick: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = "Music Tracks",
    subtitle: String? = null
) {
    val accentBlue = Color(0xFF2979FF)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF161922),
        border = BorderStroke(
            1.dp,
            Brush.horizontalGradient(
                listOf(
                    accentBlue.copy(alpha = 0.55f),
                    accentBlue.copy(alpha = 0.15f),
                    BorderGlass
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentBlue.copy(alpha = 0.22f),
                            Color(0xFF181B26),
                            Color(0xFF10121A)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        accentBlue.copy(alpha = 0.35f),
                                        Color(0xFF0D47A1).copy(alpha = 0.45f)
                                    )
                                )
                            )
                            .border(1.dp, accentBlue.copy(alpha = 0.6f), RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MusicNote,
                            contentDescription = "Music Tracks",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = title,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface,
                                letterSpacing = (-0.02).sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentBlue.copy(alpha = 0.2f),
                                border = BorderStroke(1.dp, accentBlue.copy(alpha = 0.45f))
                            ) {
                                Text(
                                    text = "MUSIC",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accentBlue,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle ?: "$count verified songs on this device",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = accentBlue,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onShuffle)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Shuffle,
                            contentDescription = "Shuffle All",
                            tint = OnPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Shuffle",
                            color = OnPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ModernBentoAudiosCard(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accentAmber = Color(0xFFFF9100)
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = SurfaceGlassHighest,
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    accentAmber.copy(alpha = 0.5f),
                    BorderGlass
                )
            )
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentAmber.copy(alpha = 0.15f),
                            Color(0xFF221A10),
                            Color(0xFF141210)
                        )
                    )
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Box(
                        modifier = Modifier
                            .size(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        accentAmber.copy(alpha = 0.35f),
                                        Color(0xFFE65100).copy(alpha = 0.45f)
                                    )
                                )
                            )
                            .border(1.dp, accentAmber.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = "Mixed Audios",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Audios & Voice",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface,
                                letterSpacing = (-0.02).sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = accentAmber.copy(alpha = 0.18f),
                                border = BorderStroke(1.dp, accentAmber.copy(alpha = 0.4f))
                            ) {
                                Text(
                                    text = "VOICE & CLIPS",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = accentAmber,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$count recordings & chat audios",
                            fontSize = 12.sp,
                            color = OnSurfaceVariant,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Open Audios",
                    tint = OnSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ModernBentoMediumCard(
    title: String,
    count: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(126.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF171717),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    accentColor.copy(alpha = 0.50f),
                    accentColor.copy(alpha = 0.12f),
                    BorderGlass
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.16f),
                            Color(0xFF1B1B1B),
                            Color(0xFF121212)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 10.dp)
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
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(accentColor.copy(alpha = 0.20f))
                            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(19.dp)
                        )
                    }

                    if (!badgeText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.18f))
                                .border(1.dp, accentColor.copy(alpha = 0.40f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                letterSpacing = 0.4.sp
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(bottom = 2.dp)) {
                    Text(
                        text = count,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 22.sp,
                        letterSpacing = (-0.02).sp
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = title,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        lineHeight = 16.sp,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        fontSize = 11.sp,
                        color = OnSurfaceVariant,
                        lineHeight = 14.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun ModernUtilityCompactCard(
    title: String,
    count: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    badgeText: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(112.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF171717),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    accentColor.copy(alpha = 0.42f),
                    BorderGlass
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(
                            accentColor.copy(alpha = 0.12f),
                            Color(0xFF181818),
                            Color(0xFF111111)
                        )
                    )
                )
                .padding(horizontal = 10.dp, vertical = 8.dp)
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
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(accentColor.copy(alpha = 0.18f))
                            .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = title,
                            tint = accentColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (!badgeText.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(accentColor.copy(alpha = 0.18f))
                                .border(1.dp, accentColor.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor,
                                letterSpacing = 0.3.sp
                            )
                        }
                    }
                }

                Column(modifier = Modifier.padding(bottom = 2.dp)) {
                    Text(
                        text = count,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        lineHeight = 20.sp,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.height(1.dp))
                    Text(
                        text = title,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceVariant,
                        lineHeight = 15.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

// ==================== Playlist Card Component ====================

@Composable
fun PlaylistCard(
    playlist: CustomPlaylist,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceGlassHighest)
            .border(BorderStroke(1.dp, BorderGlass), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4CD6FB).copy(alpha = 0.7f),
                            Color(0xFF7C4DFF).copy(alpha = 0.85f),
                            Color(0xFF1E1E1E)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            val effectiveCover = playlist.effectiveCoverUrl
            var coverError by remember(effectiveCover) { mutableStateOf(false) }
            if (effectiveCover.isNotBlank() && !coverError) {
                AsyncImage(
                    model = effectiveCover,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { coverError = true }
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(40.dp)
                )
            }

            // Floating mini play indicator badge in bottom right corner
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .border(1.dp, UnboundPrimary.copy(alpha = 0.4f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = UnboundPrimary,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Smart Playlist Badge in top left corner
            if (playlist.isSmart) {
                SmartPlaylistBadge(
                    type = playlist.playlistType,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        val completionRatio = playlist.completionRatio
        val officialCount = playlist.officialTrackCount
        if (playlist.isAlbumic && completionRatio != null && officialCount != null && officialCount > 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${playlist.tracks.size}/$officialCount tracks",
                    fontSize = 11.sp,
                    color = OnSurfaceVariant
                )
                Text(
                    text = "${(completionRatio * 100).toInt()}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (completionRatio >= 1.0f) Color(0xFF4CAF50) else UnboundPrimary
                )
            }
            Spacer(modifier = Modifier.height(3.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFF2E2E2E))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(completionRatio.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(
                            if (completionRatio >= 1.0f) Color(0xFF4CAF50) else UnboundPrimary
                        )
                )
            }
        } else {
            Text(
                text = if (playlist.isArtistSmart) "Artist Mix • ${playlist.tracks.size} tracks" else "${playlist.tracks.size} tracks",
                fontSize = 11.sp,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
fun SmartPlaylistBadge(
    type: com.cubicreates.unboundmusic.data.SmartPlaylistType,
    modifier: Modifier = Modifier
) {
    val (label, icon, color) = when (type) {
        com.cubicreates.unboundmusic.data.SmartPlaylistType.ARTIST_SMART,
        com.cubicreates.unboundmusic.data.SmartPlaylistType.ARTIST_COLLECTION -> Triple("ARTIST", Icons.Default.GraphicEq, Color(0xFF8B5CF6))
        com.cubicreates.unboundmusic.data.SmartPlaylistType.ALBUMIC_SMART,
        com.cubicreates.unboundmusic.data.SmartPlaylistType.ALBUMIC -> Triple("ALBUM", Icons.Default.MusicNote, UnboundPrimary)
        else -> Triple("CUSTOM", Icons.AutoMirrored.Filled.QueueMusic, Color.Gray)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(10.dp)
            )
            Text(
                text = label,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp,
                color = color
            )
        }
    }
}

// ==================== Folders List View ====================

@Composable
fun LibraryFoldersListView(
    folders: List<AudioFolder>,
    onBack: () -> Unit,
    onFolderSelect: (AudioFolder) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Folders",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface
                )
                Text(
                    text = "${folders.size} storage folders detected",
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        }

        HorizontalDivider(color = BorderGlass, thickness = 1.dp)

        if (folders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No audio folders found on device",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(folders) { folder ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceGlassHighest)
                            .border(1.dp, BorderGlass, RoundedCornerShape(14.dp))
                            .clickable { onFolderSelect(folder) }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFFFF9100).copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = folder.name,
                                tint = Color(0xFFFF9100),
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = folder.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = folder.pathDescription,
                                fontSize = 12.sp,
                                color = OnSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                            contentDescription = "Open",
                            tint = OnSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==================== Generic Tracks List View ====================

@Composable
fun LibraryTracksListView(
    title: String,
    subtitle: String,
    tracks: List<TrackItem>,
    onBack: () -> Unit,
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit,
    onPlayNext: (TrackItem) -> Unit,
    onAddToQueue: (TrackItem) -> Unit,
    onAddToPlaylist: (TrackItem) -> Unit,
    onOpenRingtoneCutter: (TrackItem) -> Unit,
    onToggleFavorite: (TrackItem) -> Unit,
    onDeleteTrack: (TrackItem) -> Unit,
    onIdentifyTrack: (TrackItem) -> Unit = {},
    onBatchIdentify: () -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }

    val unknownCount = remember(tracks) {
        tracks.count {
            it.title.startsWith("AUD-", ignoreCase = true) ||
            it.title.startsWith("PTT-", ignoreCase = true) ||
            it.title.startsWith("WA", ignoreCase = true) ||
            it.artist.isBlank() ||
            it.artist.equals("Unknown Artist", ignoreCase = true) ||
            it.title.contains("y2mate", ignoreCase = true)
        }
    }

    val filteredTracks = remember(tracks, searchQuery) {
        if (searchQuery.isBlank()) {
            tracks
        } else {
            val q = searchQuery.trim().lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(SurfaceGlassHighest)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = OnSurface
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = OnSurfaceVariant
                )
            }
        }

        // In-list Search Filter Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp)
        ) {
            BasicTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                singleLine = true,
                textStyle = TextStyle(
                    color = OnSurface,
                    fontSize = 14.sp
                ),
                cursorBrush = SolidColor(UnboundPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceGlassHighest)
                    .border(1.dp, BorderGlass, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp),
                decorationBox = { innerTextField ->
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (searchQuery.isEmpty()) {
                                Text(
                                    text = "Filter tracks or artist...",
                                    color = OnSurfaceVariant.copy(alpha = 0.6f),
                                    fontSize = 13.sp
                                )
                            }
                            innerTextField()
                        }
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = { searchQuery = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = OnSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            )
        }

        // Smart Tagging / Identification Banner
        if (unknownCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = UnboundPrimary.copy(alpha = 0.15f),
                border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "$unknownCount Untagged Audio File${if (unknownCount > 1) "s" else ""}",
                                color = OnSurface,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "Identify titles & artists with AcoustID & AI",
                                color = OnSurfaceVariant,
                                fontSize = 11.sp
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onBatchIdentify,
                        colors = ButtonDefaults.buttonColors(containerColor = UnboundPrimary),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Text("Fix All", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (searchQuery.isNotBlank()) "No tracks match '$searchQuery'" else "No tracks found in this section",
                    color = OnSurfaceVariant,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredTracks, key = { it.id.ifBlank { "${it.title}_${it.durationMs}" } }) { track ->
                    LibraryTrackRow(
                        track = track,
                        onClick = { onTrackSelect(track, filteredTracks) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onOpenRingtoneCutter = { onOpenRingtoneCutter(track) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onDeleteTrack = { onDeleteTrack(track) },
                        onIdentifyTrack = { onIdentifyTrack(track) }
                    )
                }
            }
        }
    }
}

// ==================== Individual Track Row Item ====================

@Composable
fun LibraryTrackRow(
    track: TrackItem,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenRingtoneCutter: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDeleteTrack: () -> Unit,
    onIdentifyTrack: () -> Unit = {}
) {
    var showMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceGlassHighest.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Thumbnail
        UnboundTrackThumbnail(
            coverUrl = track.coverUrl,
            contentDescription = track.title,
            modifier = Modifier.size(46.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Title and Artist
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = OnSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (track.audioCategory == com.cubicreates.unboundmusic.data.AudioCategory.MIXED_AUDIO && !track.isIdentifiedMusic) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color(0xFFFF9100).copy(alpha = 0.2f),
                        border = BorderStroke(1.dp, Color(0xFFFF9100).copy(alpha = 0.45f)),
                        modifier = Modifier.padding(end = 6.dp)
                    ) {
                        Text(
                            text = "AUDIO",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFFFF9100),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (track.durationMs > 0) {
                    Text(
                        text = " • ${formatDuration(track.durationMs)}",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // 3-Dots Context Menu
        Box {
            IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
                modifier = Modifier
                    .background(UnboundSurfaceContainerHigh)
                    .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
            ) {
                DropdownMenuItem(
                    text = { Text("Play Next", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onPlayNext()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Add to Queue", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = OnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onAddToQueue()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Add to Playlist", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = null,
                            tint = OnSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onAddToPlaylist()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Ringtone Cutter", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.ContentCut,
                            contentDescription = null,
                            tint = Color(0xFFFF9100),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onOpenRingtoneCutter()
                    }
                )

                DropdownMenuItem(
                    text = { Text("Favorite", color = OnSurface, fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onToggleFavorite()
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text(
                            if (track.audioCategory == com.cubicreates.unboundmusic.data.AudioCategory.MIXED_AUDIO && !track.isIdentifiedMusic) {
                                "Identify & Move to Music"
                            } else {
                                "Identify Track (Acoustic + AI)"
                            },
                            color = UnboundPrimary,
                            fontSize = 13.sp
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.GraphicEq,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onIdentifyTrack()
                    }
                )

                HorizontalDivider(color = BorderGlass, thickness = 1.dp)

                DropdownMenuItem(
                    text = { Text("Delete Track", color = Color(0xFFFF5252), fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            tint = Color(0xFFFF5252),
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    onClick = {
                        showMenu = false
                        onDeleteTrack()
                    }
                )
            }
        }
    }
}

/**
 * Modern album completion recommendation card shown when 50% to 75% of an album is downloaded.
 * "You have X of Y songs downloaded, why not download the rest of the album if you're liking it?"
 */
@Composable
fun CompleteAlbumBanner(
    status: com.cubicreates.unboundmusic.data.AlbumCompletionStatus,
    modifier: Modifier = Modifier,
    onDownloadRemaining: () -> Unit,
    onDismiss: () -> Unit = {}
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = Color(0xFF161616),
        border = BorderStroke(
            1.dp,
            Brush.linearGradient(
                listOf(
                    UnboundPrimary.copy(alpha = 0.55f),
                    Color(0xFF8B5CF6).copy(alpha = 0.35f),
                    BorderGlass
                )
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            UnboundPrimary.copy(alpha = 0.15f),
                            Color(0xFF8B5CF6).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        radius = 800f
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(UnboundPrimary.copy(alpha = 0.2f))
                                .border(1.dp, UnboundPrimary.copy(alpha = 0.4f), RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = null,
                                tint = UnboundPrimary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "COMPLETE THE ALBUM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                color = UnboundPrimary
                            )
                            Text(
                                text = status.albumTitle,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // Percentage pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF222222))
                            .border(1.dp, UnboundPrimary.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "${(status.completionRatio * 100).toInt()}% Done",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = UnboundPrimary
                        )
                    }
                }

                Text(
                    text = "You have ${status.downloadedCount} of ${status.officialTotalCount} songs downloaded. Why not download the rest of the album if you're liking it?",
                    fontSize = 13.sp,
                    color = OnSurfaceVariant,
                    lineHeight = 18.sp
                )

                // Progress Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF262626))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(status.completionRatio.coerceIn(0f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(UnboundPrimary, Color(0xFF8B5CF6))
                                )
                            )
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onDownloadRemaining,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UnboundPrimary,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = Color.Black
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        val missingCount = status.officialTotalCount - status.downloadedCount
                        Text(
                            text = "Download Rest (${missingCount})",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
