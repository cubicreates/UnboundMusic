/*
 * Package: com.cubicreates.unboundmusic.ui.search
 * File: SearchComponents.kt
 * Purpose: Crisp shared filter enums, chips, and Music/Podcast row cards for SearchScreen.
 * Subsystem: Discovery / UI Components
 */

package com.cubicreates.unboundmusic.ui.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cubicreates.unboundmusic.data.PodcastEpisodeDto
import com.cubicreates.unboundmusic.data.PodcastShowDto
import com.cubicreates.unboundmusic.data.RecognitionUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.UnboundEmptyStateCard
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSecondary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainer
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerLow

enum class SearchCategory(val label: String, val apiParam: String) {
    ALL("All", "all"),
    SONGS("Songs", "songs"),
    ARTISTS("Artists", "artists"),
    ALBUMS("Albums", "albums"),
    PLAYLISTS("Playlists", "playlists");

    companion object {
        val MUSIC = SONGS
    }
}

enum class PodcastSearchFilter(val label: String, val apiParam: String) {
    ALL("All", "all"),
    EPISODES("Episodes", "episodes"),
    SHOWS("Shows", "shows")
}

@Composable
fun <T> SearchFilterChipsRow(
    items: List<T>,
    selectedItem: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items.forEach { item ->
            val isSelected = item == selectedItem
            Surface(
                shape = RoundedCornerShape(50),
                color = if (isSelected) UnboundPrimary else UnboundSurfaceContainer,
                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable { onSelect(item) }
            ) {
                Text(
                    text = labelOf(item),
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) OnPrimary else OnSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/**
 * YouTube-style Previous Searches & Autocomplete Suggestions list.
 * Shows cached SQLite search history when idle, or prefix-filtered history + live suggestions while typing.
 */
@Composable
fun PreviousSearchesSection(
    history: List<String>,
    suggestions: List<String> = emptyList(),
    currentQuery: String = "",
    onQueryClick: (String) -> Unit,
    onFillQuery: (String) -> Unit = onQueryClick,
    onRemoveItem: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    val trimmedQuery = currentQuery.trim()
    val matchingHistory = if (trimmedQuery.isBlank()) {
        history.take(10)
    } else {
        history.filter { it.contains(trimmedQuery, ignoreCase = true) && !it.equals(trimmedQuery, ignoreCase = true) }.take(4)
    }
    val filteredSuggestions = if (trimmedQuery.isBlank()) {
        emptyList()
    } else {
        suggestions.filter { s ->
            !s.equals(trimmedQuery, ignoreCase = true) &&
                matchingHistory.none { it.equals(s, ignoreCase = true) }
        }.take(5)
    }

    if (matchingHistory.isEmpty() && filteredSuggestions.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (trimmedQuery.isBlank() && matchingHistory.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Previous Searches",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                }
                Text(
                    text = "Clear all",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = UnboundPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onClearAll)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = UnboundSurfaceContainerLow,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                matchingHistory.forEach { term ->
                    PreviousSearchRow(
                        text = term,
                        isHistory = true,
                        onClick = { onQueryClick(term) },
                        onFill = { onFillQuery(term) },
                        onRemove = { onRemoveItem(term) }
                    )
                }
                filteredSuggestions.forEach { suggestion ->
                    PreviousSearchRow(
                        text = suggestion,
                        isHistory = false,
                        onClick = { onQueryClick(suggestion) },
                        onFill = { onFillQuery(suggestion) },
                        onRemove = null
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviousSearchRow(
    text: String,
    isHistory: Boolean,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onRemove: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 6.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = if (isHistory) Icons.Default.History else Icons.Default.Search,
            contentDescription = null,
            tint = if (isHistory) UnboundPrimary else OnSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            color = OnSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (onRemove != null) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove $text",
                    tint = OnSurfaceVariant.copy(alpha = 0.75f),
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        IconButton(
            onClick = onFill,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                imageVector = Icons.Default.NorthWest,
                contentDescription = "Insert $text",
                tint = OnSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
fun ShazamRecognitionCard(
    recognitionState: RecognitionUiState,
    recordingSeconds: Int,
    onStopOrMatchNow: () -> Unit,
    onRetry: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    when (recognitionState) {
        is RecognitionUiState.Listening,
        is RecognitionUiState.Loading -> {
            val isListening = recognitionState is RecognitionUiState.Listening
            Surface(
                modifier = modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = UnboundSurfaceContainer,
                border = BorderStroke(1.dp, UnboundPrimary.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    CircularProgressIndicator(
                        color = UnboundPrimary,
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isListening) "Listening (${recordingSeconds}s)..." else "Identifying song...",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = OnSurface
                        )
                        Text(
                            text = if (isListening) {
                                "Hold near audio source — auto-matching via Shazam"
                            } else {
                                (recognitionState as RecognitionUiState.Loading).query.ifBlank { "Resolving playable match..." }
                            },
                            fontSize = 11.5.sp,
                            color = OnSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = UnboundPrimary.copy(alpha = 0.16f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable(onClick = if (isListening) onStopOrMatchNow else onClear)
                    ) {
                        Text(
                            text = if (isListening) "Match Now" else "Cancel",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = UnboundPrimary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }

        is RecognitionUiState.Empty -> {
            UnboundEmptyStateCard(
                title = "No song matched",
                subtitle = recognitionState.message,
                icon = Icons.Default.GraphicEq,
                actionLabel = "Try Again",
                onAction = onRetry,
                compact = true,
                modifier = modifier
            )
        }

        is RecognitionUiState.Error -> {
            UnboundEmptyStateCard(
                title = "Recognition failed",
                subtitle = recognitionState.message,
                icon = Icons.Default.GraphicEq,
                actionLabel = "Retry",
                onAction = onRetry,
                compact = true,
                modifier = modifier
            )
        }

        else -> Unit
    }
}

@Composable
fun RecentSearchChipsSection(
    history: List<String>,
    onQueryClick: (String) -> Unit,
    onRemoveItem: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    PreviousSearchesSection(
        history = history,
        onQueryClick = onQueryClick,
        onRemoveItem = onRemoveItem,
        onClearAll = onClearAll,
        modifier = modifier
    )
}

@Composable
fun RankedChartTrackRow(
    rank: Int,
    track: TrackItem,
    isCurrentPlaying: Boolean,
    onPlayClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlayClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrentPlaying) UnboundSurfaceContainerHigh else UnboundSurfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isCurrentPlaying) UnboundPrimary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.width(24.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isCurrentPlaying) {
                    Icon(
                        imageVector = Icons.Default.GraphicEq,
                        contentDescription = "Playing",
                        tint = UnboundPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = rank.toString().padStart(2, '0'),
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnSurfaceVariant
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(UnboundSurfaceContainerHighest)
                    .border(
                        1.dp,
                        if (isCurrentPlaying) UnboundPrimary.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f),
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (track.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = track.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MusicNote,
                        contentDescription = null,
                        tint = UnboundPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentPlaying) UnboundPrimary else OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = track.artist.ifBlank { "Unknown Artist" },
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentPlaying) UnboundPrimary else UnboundSurfaceContainer)
                        .border(
                            1.dp,
                            if (isCurrentPlaying) UnboundPrimary else Color.White.copy(alpha = 0.08f),
                            CircleShape
                        )
                        .clickable(onClick = onPlayClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                        tint = if (isCurrentPlaying) OnPrimary else UnboundPrimary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = OnSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    TrackOptionsDropdownMenu(
                        expanded = showMenu,
                        isArtist = false,
                        onDismiss = { showMenu = false },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onStartRadio = onStartRadio,
                        onDownload = onDownload,
                        onAddToPlaylist = onAddToPlaylist,
                        onViewArtist = onPlayClick
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(
    track: TrackItem,
    isCurrentPlaying: Boolean,
    onClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_") || track.id.startsWith("MPREb_")
    val isArtist = track.itemType.equals("artist", ignoreCase = true) || track.browseId.startsWith("UC") || track.id.startsWith("UC")
    val isPlaylist = track.itemType.equals("playlist", ignoreCase = true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL") || track.id.startsWith("VL") || track.id.startsWith("PL")

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrentPlaying) UnboundSurfaceContainerHigh else UnboundSurfaceContainerLow,
        border = BorderStroke(
            1.dp,
            when {
                isCurrentPlaying -> UnboundPrimary.copy(alpha = 0.5f)
                isAlbum -> UnboundPrimary.copy(alpha = 0.3f)
                isArtist -> UnboundSecondary.copy(alpha = 0.3f)
                isPlaylist -> Color(0xFFBA68C8).copy(alpha = 0.3f)
                else -> Color.White.copy(alpha = 0.06f)
            }
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(if (isArtist) CircleShape else RoundedCornerShape(10.dp))
                    .background(UnboundSurfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                var coverError by remember(track.coverUrl) { mutableStateOf(false) }
                if (track.coverUrl.isNotBlank() && !coverError) {
                    AsyncImage(
                        model = track.coverUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        onError = { coverError = true }
                    )
                } else {
                    Icon(
                        imageVector = when {
                            isAlbum -> Icons.Default.Album
                            isArtist -> Icons.Default.Person
                            isPlaylist -> Icons.AutoMirrored.Filled.QueueMusic
                            else -> Icons.Default.MusicNote
                        },
                        contentDescription = null,
                        tint = when {
                            isArtist -> UnboundSecondary
                            isPlaylist -> Color(0xFFBA68C8)
                            else -> UnboundPrimary
                        },
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    fontSize = 14.sp,
                    fontWeight = if (isCurrentPlaying) FontWeight.Bold else FontWeight.SemiBold,
                    color = if (isCurrentPlaying) UnboundPrimary else OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isAlbum || isArtist || isPlaylist) {
                        val badgeLabel = when {
                            isAlbum -> "Album"
                            isArtist -> "Artist"
                            else -> "Playlist"
                        }
                        val badgeColor = when {
                            isAlbum -> UnboundPrimary
                            isArtist -> UnboundSecondary
                            else -> Color(0xFFBA68C8)
                        }
                        Surface(
                            shape = RoundedCornerShape(50),
                            color = badgeColor.copy(alpha = 0.18f)
                        ) {
                            Text(
                                text = badgeLabel,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = badgeColor,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = track.artist.ifBlank { "Unknown Artist" },
                        fontSize = 12.sp,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

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

                TrackOptionsDropdownMenu(
                    expanded = showMenu,
                    isArtist = isArtist,
                    onDismiss = { showMenu = false },
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onStartRadio = onStartRadio,
                    onDownload = onDownload,
                    onAddToPlaylist = onAddToPlaylist,
                    onViewArtist = onClick
                )
            }
        }
    }
}

@Composable
fun PodcastShowRowCard(
    show: PodcastShowDto,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = UnboundSurfaceContainerLow,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(UnboundSurfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (show.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = show.thumbnailUrl,
                        contentDescription = show.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Podcasts,
                        contentDescription = null,
                        tint = UnboundSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                if (show.category.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = UnboundSecondary.copy(alpha = 0.16f)
                    ) {
                        Text(
                            text = show.category,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = UnboundSecondary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = show.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = OnSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = show.author.ifBlank { "Podcast Host" },
                    fontSize = 12.sp,
                    color = OnSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(UnboundSurfaceContainerHigh)
                    .border(1.dp, Color.White.copy(alpha = 0.08f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Open show",
                    tint = UnboundPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun PodcastEpisodeRowCard(
    episode: PodcastEpisodeDto,
    isCurrentPlaying: Boolean,
    onPlayClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onPlayClick),
        shape = RoundedCornerShape(16.dp),
        color = if (isCurrentPlaying) UnboundSurfaceContainerHigh else UnboundSurfaceContainerLow,
        border = BorderStroke(
            1.dp,
            if (isCurrentPlaying) UnboundSecondary.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.06f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(UnboundSurfaceContainerHighest),
                    contentAlignment = Alignment.Center
                ) {
                    if (episode.effectiveThumbnail.isNotBlank()) {
                        AsyncImage(
                            model = episode.effectiveThumbnail,
                            contentDescription = episode.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = null,
                            tint = UnboundSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = episode.title,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isCurrentPlaying) UnboundSecondary else OnSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val durationMin = if (episode.durationMs > 0L) "${episode.durationMs / 60000}m" else ""
                    val meta = listOf(episode.showTitle.ifBlank { episode.author }, durationMin)
                        .filter { it.isNotBlank() }
                        .joinToString(" • ")
                    Text(
                        text = meta,
                        fontFamily = FontFamily.SansSerif,
                        fontSize = 11.5.sp,
                        color = OnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isCurrentPlaying) UnboundSecondary else UnboundSurfaceContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isCurrentPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isCurrentPlaying) "Pause" else "Play",
                        tint = if (isCurrentPlaying) OnPrimary else UnboundSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (episode.resumeFraction > 0.01f) {
                LinearProgressIndicator(
                    progress = { episode.resumeFraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp),
                    color = UnboundSecondary,
                    trackColor = Color.White.copy(alpha = 0.08f)
                )
            }
        }
    }
}

@Composable
private fun TrackOptionsDropdownMenu(
    expanded: Boolean,
    isArtist: Boolean,
    onDismiss: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onStartRadio: () -> Unit,
    onDownload: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onViewArtist: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .background(UnboundSurfaceContainerHigh)
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
    ) {
        if (!isArtist) {
            DropdownMenuItem(
                text = { Text("Play Next", color = OnSurface, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = UnboundPrimary, modifier = Modifier.size(18.dp))
                },
                onClick = { onDismiss(); onPlayNext() }
            )
            DropdownMenuItem(
                text = { Text("Add to Queue", color = OnSurface, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.PlayArrow, null, tint = OnSurface, modifier = Modifier.size(18.dp))
                },
                onClick = { onDismiss(); onAddToQueue() }
            )
            DropdownMenuItem(
                text = { Text("Start Radio", color = OnSurface, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Radio, null, tint = OnSurface, modifier = Modifier.size(18.dp))
                },
                onClick = { onDismiss(); onStartRadio() }
            )
            DropdownMenuItem(
                text = { Text("Download Track", color = OnSurface, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Download, null, tint = OnSurface, modifier = Modifier.size(18.dp))
                },
                onClick = { onDismiss(); onDownload() }
            )
            DropdownMenuItem(
                text = { Text("Add to Playlist", color = OnSurface, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null, tint = OnSurface, modifier = Modifier.size(18.dp))
                },
                onClick = { onDismiss(); onAddToPlaylist() }
            )
        } else {
            DropdownMenuItem(
                text = { Text("View Artist Profile", color = OnSurface, fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Person, null, tint = UnboundSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    Icon(Icons.Default.ChevronRight, null, tint = OnSurfaceVariant, modifier = Modifier.size(16.dp))
                },
                onClick = { onDismiss(); onViewArtist() }
            )
        }
    }
}
