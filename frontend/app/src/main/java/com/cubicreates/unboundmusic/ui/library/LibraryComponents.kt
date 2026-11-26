/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: LibraryComponents.kt
 * Purpose: Studio Brutalist reusable components, enums, and drill-down views for Music & Podcast Library screens.
 * Subsystem: Personal Library / UI Components
 */

package com.cubicreates.unboundmusic.ui.library

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
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
import androidx.compose.ui.draw.alpha
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
import com.cubicreates.unboundmusic.data.AlbumCompletionStatus
import com.cubicreates.unboundmusic.data.AudioCategory
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.data.HomeSection
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundEmptyStateCard
import com.cubicreates.unboundmusic.ui.components.UnboundTrackThumbnail
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLow
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLowest
import java.io.File

enum class LibrarySubView {
    HUB,
    TRACKS,
    AUDIOS,
    FOLDERS,
    FOLDER_TRACKS,
    FAVORITES,
    RECENT_PLAYED,
    DOWNLOADED,
    ALBUMS,
    PLAYLISTS
}

enum class LibrarySortOption(val label: String) {
    RECENTLY_ADDED("Recently Added"),
    TITLE_AZ("Title (A-Z)"),
    ARTIST_AZ("Artist (A-Z)"),
    DURATION("Duration")
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

fun sortLibraryTracks(tracks: List<TrackItem>, sortOption: LibrarySortOption): List<TrackItem> {
    return when (sortOption) {
        LibrarySortOption.RECENTLY_ADDED -> tracks
        LibrarySortOption.TITLE_AZ -> tracks.sortedBy { it.title.lowercase() }
        LibrarySortOption.ARTIST_AZ -> tracks.sortedBy { it.artist.lowercase() }
        LibrarySortOption.DURATION -> tracks.sortedByDescending { it.durationMs }
    }
}

// ==================== Library Shared Components ====================

@Composable
fun LibraryTitleHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = title,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface
        )
        Text(
            text = subtitle,
            fontSize = 13.sp,
            color = OnSurfaceVariant.copy(alpha = 0.85f)
        )
    }
}

@Composable
fun LibrarySearchAndSortRow(
    query: String,
    placeholder: String,
    currentSort: LibrarySortOption,
    onQueryChange: (String) -> Unit,
    onSortChange: (LibrarySortOption) -> Unit,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(18.dp),
            color = UnboundSurfaceContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search library",
                    tint = OnSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontSize = 13.sp,
                            color = OnSurfaceVariant.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = OnSurface,
                            fontSize = 13.sp
                        ),
                        cursorBrush = SolidColor(UnboundPrimary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear",
                        tint = OnSurfaceVariant,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable { onQueryChange("") }
                    )
                }
            }
        }

        Box {
            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { showSortMenu = true },
                shape = RoundedCornerShape(18.dp),
                color = UnboundSurfaceContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = "Filter & Sort",
                        tint = if (currentSort != LibrarySortOption.RECENTLY_ADDED) UnboundPrimary else OnSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = { showSortMenu = false },
                modifier = Modifier
                    .background(UnboundSurfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
            ) {
                LibrarySortOption.values().forEach { opt ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = opt.label,
                                fontSize = 13.sp,
                                color = if (opt == currentSort) UnboundPrimary else OnSurface,
                                fontWeight = if (opt == currentSort) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        trailingIcon = if (opt == currentSort) {
                            { Icon(Icons.Default.Check, null, tint = UnboundPrimary, modifier = Modifier.size(16.dp)) }
                        } else null,
                        onClick = {
                            showSortMenu = false
                            onSortChange(opt)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryFilterPillsRow(
    filters: List<String>,
    selectedFilter: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        filters.forEach { label ->
            val isSelected = label == selectedFilter
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) UnboundPrimary else UnboundSurfaceContainer,
                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(label) }
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) OnPrimary else OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
fun LibrarySyncStatusBanner(
    isCloudConnected: Boolean,
    cloudTracksCount: Int,
    offlineTracksCount: Int,
    onRefresh: () -> Unit,
    onConnectClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sync_dot")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "sync_pulse"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(if (!isCloudConnected) Modifier.clickable(onClick = onConnectClick) else Modifier),
        shape = RoundedCornerShape(18.dp),
        color = UnboundSurfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(UnboundSurfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCloudConnected) Icons.Default.CloudDone else Icons.Default.CloudSync,
                        contentDescription = null,
                        tint = if (isCloudConnected) Color(0xFF05E777) else UnboundPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (isCloudConnected) "YouTube Music Synced" else "Sync YouTube Music",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = if (isCloudConnected) Color(0xFF05E777).copy(alpha = 0.2f) else UnboundPrimary.copy(alpha = 0.16f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .alpha(if (isCloudConnected) pulseAlpha else 1f)
                                        .clip(CircleShape)
                                        .background(if (isCloudConnected) Color(0xFF05E777) else UnboundPrimary)
                                )
                                Text(
                                    text = if (isCloudConnected) "Online" else "Local",
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isCloudConnected) Color(0xFF05E777) else UnboundPrimary
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isCloudConnected) {
                            "$cloudTracksCount cloud tracks • $offlineTracksCount offline device tracks"
                        } else {
                            "$offlineTracksCount offline device tracks • Tap to connect"
                        },
                        fontSize = 11.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.78f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(UnboundSurfaceContainer)
                    .clickable(onClick = if (isCloudConnected) onRefresh else onConnectClick),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Sync,
                    contentDescription = if (isCloudConnected) "Sync library" else "Connect account",
                    tint = OnSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun LibraryHeroAllSongsCard(
    title: String,
    subtitle: String,
    artistInitials: List<String>,
    actionLabel: String,
    actionIcon: ImageVector,
    heroIcon: ImageVector,
    isEmpty: Boolean = false,
    onCardClick: () -> Unit,
    onActionClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onCardClick),
        shape = RoundedCornerShape(18.dp),
        color = UnboundSurfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(UnboundPrimary.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = heroIcon,
                            contentDescription = title,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Text(
                            text = subtitle,
                            fontSize = 11.5.sp,
                            color = OnSurfaceVariant.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Icon(
                    imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                    contentDescription = null,
                    tint = OnSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (artistInitials.isNotEmpty()) {
                        artistInitials.take(3).forEachIndexed { index, initials ->
                            Box(
                                modifier = Modifier
                                    .offset(x = (-6 * index).dp)
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (index) {
                                            0 -> UnboundSurfaceContainerHighest
                                            1 -> Color(0xFF38393C)
                                            else -> UnboundSurfaceContainerHigh
                                        }
                                    )
                                    .border(1.5.dp, UnboundSurfaceContainer, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initials,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface
                                )
                            }
                        }
                    } else {
                        Text(
                            text = if (isEmpty) "Library empty" else "Ready to play",
                            fontSize = 11.5.sp,
                            color = OnSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onActionClick),
                    shape = RoundedCornerShape(10.dp),
                    color = if (isEmpty) UnboundSurfaceContainerHigh else UnboundPrimary
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = actionIcon,
                            contentDescription = actionLabel,
                            tint = if (isEmpty) OnSurfaceVariant else OnPrimary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = actionLabel,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isEmpty) OnSurfaceVariant else OnPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceRecordingsIsolationBanner(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = UnboundSurfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFFFC594).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = "Voice & Recordings",
                        tint = Color(0xFFFFC594),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "Voice & Recordings",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = UnboundSurfaceContainerHigh
                        ) {
                            Text(
                                text = "$count clips",
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = OnSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "WhatsApp & voice memos kept out of your music",
                        fontSize = 11.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = "Open voice recordings",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun LibraryBentoQuickTile(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    trailingBadge: String? = null,
    trailingBadgeColor: Color = UnboundSecondary,
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .height(124.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = UnboundSurfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(19.dp)
                    )
                }

                if (!trailingBadge.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = trailingBadgeColor.copy(alpha = 0.2f)
                    ) {
                        Text(
                            text = trailingBadge,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = trailingBadgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                } else if (trailingIcon != null) {
                    Icon(
                        imageVector = trailingIcon,
                        contentDescription = null,
                        tint = OnSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Column {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = subtitle,
                    fontSize = 11.sp,
                    color = OnSurfaceVariant.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun SmartCompleteAlbumCard(
    status: AlbumCompletionStatus,
    onDownloadRemaining: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pct = (status.completionRatio * 100).toInt().coerceIn(0, 100)
    val missingCount = (status.officialTotalCount - status.downloadedCount).coerceAtLeast(1)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = UnboundSurfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(UnboundSurfaceContainerHighest),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryAddCheck,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Complete the album",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UnboundPrimary,
                            letterSpacing = 0.2.sp
                        )
                        Text(
                            text = status.albumTitle,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = OnSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Text(
                    text = "$pct%",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = OnSurfaceVariant
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(UnboundSurfaceContainerLowest)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(status.completionRatio.coerceIn(0.05f, 1f))
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(50))
                        .background(UnboundPrimary)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$missingCount songs missing from storage",
                    fontSize = 11.5.sp,
                    color = OnSurfaceVariant.copy(alpha = 0.8f)
                )

                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onDownloadRemaining),
                    shape = RoundedCornerShape(8.dp),
                    color = UnboundPrimary.copy(alpha = 0.2f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            tint = UnboundPrimary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = "Download $missingCount tracks",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UnboundPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BrutalistPlaylistTile(
    playlist: CustomPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .width(144.dp)
            .clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(144.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(UnboundSurfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            val cover = playlist.effectiveCoverUrl
            var coverError by remember(cover) { mutableStateOf(false) }
            if (cover.isNotBlank() && !coverError) {
                AsyncImage(
                    model = cover,
                    contentDescription = playlist.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    onError = { coverError = true }
                )
            } else {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = UnboundPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, UnboundSurfaceContainerLowest.copy(alpha = 0.85f))
                        )
                    )
                    .padding(8.dp),
                contentAlignment = Alignment.BottomStart
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = UnboundSurfaceContainerLowest.copy(alpha = 0.9f)
                ) {
                    Text(
                        text = "${playlist.tracks.size} songs",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = UnboundPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Text(
            text = playlist.title,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = when {
                playlist.isAlbumic -> "Album Collection"
                playlist.isArtistSmart -> "Artist Mix"
                else -> "Custom Playlist"
            },
            fontSize = 11.sp,
            color = OnSurfaceVariant.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun RecentlyAddedTrackRow(
    track: TrackItem,
    isCurrentPlaying: Boolean,
    isFavoriteTrack: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenRingtoneCutter: () -> Unit,
    onDeleteTrack: () -> Unit,
    onIdentifyTrack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = if (isCurrentPlaying) UnboundSurfaceContainer else UnboundSurfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(UnboundSurfaceContainerHigh),
                contentAlignment = Alignment.Center
            ) {
                UnboundTrackThumbnail(
                    coverUrl = track.coverUrl,
                    contentDescription = track.title,
                    modifier = Modifier.size(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    iconSize = 20.dp
                )
                if (isCurrentPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(UnboundPrimary.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Playing",
                            tint = UnboundPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentPlaying) UnboundPrimary else OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    fontSize = 11.sp,
                    color = OnSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (track.durationMs > 0L) {
                    Text(
                        text = formatDuration(track.durationMs),
                        fontSize = 11.sp,
                        color = OnSurfaceVariant.copy(alpha = 0.8f),
                        modifier = Modifier.padding(end = 4.dp)
                    )
                }

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = if (isFavoriteTrack) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavoriteTrack) UnboundPrimary else OnSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More options",
                            tint = OnSurfaceVariant.copy(alpha = 0.75f),
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    TrackContextDropdown(
                        expanded = showMenu,
                        track = track,
                        onDismiss = { showMenu = false },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onIdentifyTrack = onIdentifyTrack,
                        onDeleteTrack = onDeleteTrack
                    )
                }
            }
        }
    }
}

// ==================== Drill-Down Sub-Views ====================

@Composable
fun LibraryPlaylistsGridView(
    title: String,
    subtitle: String,
    playlists: List<CustomPlaylist>,
    onBack: () -> Unit,
    onPlaylistClick: (CustomPlaylist) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubViewHeader(title = title, subtitle = subtitle, onBack = onBack)
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

        if (playlists.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                UnboundEmptyStateCard(
                    title = "No collections yet",
                    subtitle = "Create a custom playlist or save music to see collections here.",
                    icon = Icons.AutoMirrored.Filled.QueueMusic
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(playlists, key = { it.id }) { pl ->
                    BrutalistPlaylistTile(
                        playlist = pl,
                        onClick = { onPlaylistClick(pl) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun LibraryFoldersListView(
    folders: List<AudioFolder>,
    onBack: () -> Unit,
    onFolderSelect: (AudioFolder) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        SubViewHeader(
            title = "Device Folders",
            subtitle = "${folders.size} storage folders detected",
            onBack = onBack
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.06f), thickness = 1.dp)

        if (folders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                UnboundEmptyStateCard(
                    title = "No audio folders found",
                    subtitle = "No audio folders were detected on your device storage yet.",
                    icon = Icons.Default.Folder
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders) { folder ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .clickable { onFolderSelect(folder) },
                        shape = RoundedCornerShape(18.dp),
                        color = UnboundSurfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(UnboundSurfaceContainerHigh),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = folder.name,
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = folder.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = OnSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = folder.pathDescription,
                                    fontSize = 11.5.sp,
                                    color = OnSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                                contentDescription = "Open",
                                tint = OnSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

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
    var sortOption by remember { mutableStateOf(LibrarySortOption.RECENTLY_ADDED) }

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

    val filteredTracks = remember(tracks, searchQuery, sortOption) {
        val base = if (searchQuery.isBlank()) {
            tracks
        } else {
            val q = searchQuery.trim().lowercase()
            tracks.filter {
                it.title.lowercase().contains(q) || it.artist.lowercase().contains(q)
            }
        }
        sortLibraryTracks(base, sortOption)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        SubViewHeader(title = title, subtitle = subtitle, onBack = onBack)

        Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            LibrarySearchAndSortRow(
                query = searchQuery,
                placeholder = "Filter tracks or artist...",
                currentSort = sortOption,
                onQueryChange = { searchQuery = it },
                onSortChange = { sortOption = it }
            )
        }

        if (unknownCount > 0) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(18.dp),
                color = UnboundPrimary.copy(alpha = 0.14f),
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
                            modifier = Modifier.size(22.dp)
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
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Fix All", color = OnPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        if (filteredTracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                UnboundEmptyStateCard(
                    title = if (searchQuery.isNotBlank()) "No matching tracks" else "Nothing here yet",
                    subtitle = if (searchQuery.isNotBlank()) {
                        "No tracks match \"$searchQuery\""
                    } else {
                        "Tracks added or downloaded in this section will appear here."
                    },
                    icon = if (searchQuery.isNotBlank()) Icons.Default.Search else Icons.Default.MusicNote
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 140.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredTracks, key = { it.id.ifBlank { "${it.title}_${it.durationMs}" } }) { track ->
                    RecentlyAddedTrackRow(
                        track = track,
                        isCurrentPlaying = false,
                        isFavoriteTrack = false,
                        onClick = { onTrackSelect(track, filteredTracks) },
                        onToggleFavorite = { onToggleFavorite(track) },
                        onPlayNext = { onPlayNext(track) },
                        onAddToQueue = { onAddToQueue(track) },
                        onAddToPlaylist = { onAddToPlaylist(track) },
                        onOpenRingtoneCutter = { onOpenRingtoneCutter(track) },
                        onDeleteTrack = { onDeleteTrack(track) },
                        onIdentifyTrack = { onIdentifyTrack(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SubViewHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit
) {
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
                .clip(RoundedCornerShape(8.dp))
                .background(UnboundSurfaceContainer)
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
                fontSize = 11.5.sp,
                color = OnSurfaceVariant
            )
        }
    }
}

@Composable
private fun TrackContextDropdown(
    expanded: Boolean,
    track: TrackItem,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenRingtoneCutter: () -> Unit,
    onToggleFavorite: () -> Unit,
    onIdentifyTrack: () -> Unit,
    onDeleteTrack: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(UnboundSurfaceContainerHigh)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
    ) {
        DropdownMenuItem(
            text = { Text("Play Next", color = OnSurface, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = UnboundPrimary, modifier = Modifier.size(18.dp)) },
            onClick = { onDismiss(); onPlayNext() }
        )
        DropdownMenuItem(
            text = { Text("Add to Queue", color = OnSurface, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = OnSurface, modifier = Modifier.size(18.dp)) },
            onClick = { onDismiss(); onAddToQueue() }
        )
        DropdownMenuItem(
            text = { Text("Add to Playlist", color = OnSurface, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = OnSurface, modifier = Modifier.size(18.dp)) },
            onClick = { onDismiss(); onAddToPlaylist() }
        )
        DropdownMenuItem(
            text = { Text("Ringtone Cutter", color = OnSurface, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.ContentCut, null, tint = Color(0xFFFFC594), modifier = Modifier.size(18.dp)) },
            onClick = { onDismiss(); onOpenRingtoneCutter() }
        )
        DropdownMenuItem(
            text = { Text("Favorite", color = OnSurface, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Favorite, null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(18.dp)) },
            onClick = { onDismiss(); onToggleFavorite() }
        )
        DropdownMenuItem(
            text = {
                Text(
                    if (track.audioCategory == AudioCategory.MIXED_AUDIO && !track.isIdentifiedMusic) {
                        "Identify & Move to Music"
                    } else {
                        "Identify Track (Acoustic + AI)"
                    },
                    color = UnboundPrimary,
                    fontSize = 13.sp
                )
            },
            leadingIcon = { Icon(Icons.Default.GraphicEq, null, tint = UnboundPrimary, modifier = Modifier.size(18.dp)) },
            onClick = { onDismiss(); onIdentifyTrack() }
        )
        HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)
        DropdownMenuItem(
            text = { Text("Delete Track", color = Color(0xFFFFB4AB), fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Delete, null, tint = Color(0xFFFFB4AB), modifier = Modifier.size(18.dp)) },
            onClick = { onDismiss(); onDeleteTrack() }
        )
    }
}
