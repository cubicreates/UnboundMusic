/*
 * Package: com.cubicreates.unboundmusic.ui.library
 * File: GuestLibraryScreen.kt
 * Purpose: Offline-First Guest Library presentation focusing on local device audio, downloads,
 *          custom SQLite playlists, and an eye-catching YouTube Music connect card.
 * Subsystem: Personal Library / Guest Mode
 */

package com.cubicreates.unboundmusic.ui.library

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.theme.BorderGlass
import com.cubicreates.unboundmusic.ui.theme.OnPrimary
import com.cubicreates.unboundmusic.ui.theme.OnSurface
import com.cubicreates.unboundmusic.ui.theme.OnSurfaceVariant
import com.cubicreates.unboundmusic.ui.theme.SurfaceGlassHighest
import com.cubicreates.unboundmusic.ui.theme.UnboundBackground
import com.cubicreates.unboundmusic.ui.theme.UnboundPrimary
import com.cubicreates.unboundmusic.ui.theme.UnboundSurfaceContainerHigh

@Composable
fun GuestLibraryScreen(
    modifier: Modifier = Modifier,
    savedGB: Double = 0.0,
    downloadsCount: Int = 0,
    whatsappCount: Int = 0,
    telegramCount: Int = 0,
    tracks: List<TrackItem> = emptyList(),
    musicTracks: List<TrackItem> = emptyList(),
    mixedAudioTracks: List<TrackItem> = emptyList(),
    favoriteTracks: List<TrackItem> = emptyList(),
    recentlyPlayedTracks: List<TrackItem> = emptyList(),
    downloadedTracks: List<TrackItem> = emptyList(),
    onMenuClick: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onConnectClick: () -> Unit = onProfileClick,
    onSourceClick: (String) -> Unit = {},
    onTrackSelect: (TrackItem, List<TrackItem>) -> Unit = { _, _ -> },
    onRefresh: () -> Unit = {},
    downloadTasks: Map<String, DownloadTaskDto> = emptyMap(),
    onStartDownload: (TrackItem) -> Unit = {},
    onCancelDownload: (String) -> Unit = {},
    onDeleteDownload: (String) -> Unit = {},
    onOpenDownloadsHub: () -> Unit = {},
    customPlaylists: List<CustomPlaylist> = emptyList(),
    artistPlaylists: List<CustomPlaylist> = emptyList(),
    albumicPlaylists: List<CustomPlaylist> = emptyList(),
    albumCompletions: List<com.cubicreates.unboundmusic.data.AlbumCompletionStatus> = emptyList(),
    onDownloadRemaining: (com.cubicreates.unboundmusic.data.AlbumCompletionStatus) -> Unit = {},
    onCreatePlaylist: (title: String) -> Unit = {},
    onPlaylistClick: (CustomPlaylist) -> Unit = {},
    onAddToPlaylist: (TrackItem) -> Unit = {},
    onPlayNext: (TrackItem) -> Unit = {},
    onAddToQueue: (TrackItem) -> Unit = {},
    onStartRadio: (TrackItem) -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenRingtoneCutter: (TrackItem) -> Unit = {},
    onToggleFavorite: (TrackItem) -> Unit = {},
    onStartShazam: () -> Unit = {},
    onShufflePlayAll: () -> Unit = {},
    onIdentifyTrack: (TrackItem) -> Unit = {},
    onBatchIdentify: () -> Unit = {},
    allFilesGranted: Boolean = true,
    onRequestAllFilesPermission: () -> Unit = {}
) {
    var subView by remember { mutableStateOf(LibrarySubView.HUB) }
    var selectedFolder by remember { mutableStateOf<AudioFolder?>(null) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistTitle by remember { mutableStateOf("") }

    BackHandler(enabled = subView != LibrarySubView.HUB) {
        if (subView == LibrarySubView.FOLDER_TRACKS) {
            subView = LibrarySubView.FOLDERS
        } else {
            subView = LibrarySubView.HUB
        }
    }

    val audioFolders = remember(tracks) {
        extractAudioFolders(tracks)
    }

    val effectiveDownloaded = remember(downloadedTracks, tracks) {
        if (downloadedTracks.isNotEmpty()) {
            downloadedTracks
        } else {
            tracks.filter {
                it.source.contains("Downloads", ignoreCase = true) ||
                it.source.contains("Unbound", ignoreCase = true)
            }
        }
    }

    val effectiveMusicTracks = remember(musicTracks, tracks) {
        if (musicTracks.isNotEmpty()) {
            musicTracks
        } else {
            val filtered = tracks.filter { it.audioCategory == com.cubicreates.unboundmusic.data.AudioCategory.MUSIC || it.isIdentifiedMusic }
            if (filtered.isNotEmpty()) filtered else tracks
        }
    }

    val effectiveMixedAudioTracks = remember(mixedAudioTracks, tracks) {
        if (mixedAudioTracks.isNotEmpty()) mixedAudioTracks else tracks.filter { it.audioCategory == com.cubicreates.unboundmusic.data.AudioCategory.MIXED_AUDIO && !it.isIdentifiedMusic }
    }

    val allPlaylists = remember(customPlaylists, albumicPlaylists, artistPlaylists) {
        (albumicPlaylists + artistPlaylists + customPlaylists).distinctBy { it.id }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(UnboundBackground)
    ) {
        Crossfade(
            targetState = subView,
            label = "guest_library_view_crossfade"
        ) { currentView ->
            when (currentView) {
                LibrarySubView.HUB -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Library",
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = OnSurface,
                                    letterSpacing = (-0.02).sp
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Local device audio & offline storage",
                                    fontSize = 13.sp,
                                    color = OnSurfaceVariant
                                )
                            }

                            IconButton(
                                onClick = onRefresh,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceGlassHighest)
                                    .border(width = 1.dp, color = BorderGlass, shape = CircleShape)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Library",
                                    tint = UnboundPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Filter chips
                        var selectedFilter by remember { mutableStateOf("All") }
                        val categoryFilters = listOf("All", "Music", "Audios", "Downloads", "Favorites", "Playlists", "Folders")
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(categoryFilters) { cat ->
                                val isSelected = selectedFilter == cat
                                Surface(
                                    shape = RoundedCornerShape(20.dp),
                                    color = if (isSelected) UnboundPrimary else SurfaceGlassHighest,
                                    border = BorderStroke(1.dp, if (isSelected) UnboundPrimary else BorderGlass),
                                    modifier = Modifier.clickable {
                                        selectedFilter = cat
                                        when (cat) {
                                            "Music" -> subView = LibrarySubView.TRACKS
                                            "Audios" -> subView = LibrarySubView.AUDIOS
                                            "Downloads" -> subView = LibrarySubView.DOWNLOADED
                                            "Favorites" -> subView = LibrarySubView.FAVORITES
                                            "Folders" -> subView = LibrarySubView.FOLDERS
                                            else -> {}
                                        }
                                    }
                                ) {
                                    Text(
                                        text = cat,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) OnPrimary else OnSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }

                        // Storage Permission Banner (Android 11+)
                        if (!allFilesGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp))
                                    .clickable(onClick = onRequestAllFilesPermission),
                                shape = RoundedCornerShape(18.dp),
                                color = Color(0xFF261815),
                                border = BorderStroke(
                                    1.dp,
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color(0xFFFF9800).copy(alpha = 0.50f),
                                            Color(0xFFFF5722).copy(alpha = 0.35f),
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
                                                    Color(0xFFFF9800).copy(alpha = 0.16f),
                                                    Color(0xFF221411),
                                                    Color(0xFF18100E)
                                                )
                                            )
                                        )
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(46.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(Color(0xFFFF9800), Color(0xFFFF5722))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Folder,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(26.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Unlock All Device Folders",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OnSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Grant All Files access to scan WhatsApp audio, Downloads, Telegram, SD card and custom folders.",
                                                fontSize = 11.sp,
                                                color = OnSurfaceVariant,
                                                lineHeight = 15.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(Color(0xFFFF9800))
                                                .padding(horizontal = 14.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "UNLOCK",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.Black
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Connect YouTube Music Prompt Banner
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable(onClick = onConnectClick),
                            shape = RoundedCornerShape(18.dp),
                            color = Color(0xFF181B26),
                            border = BorderStroke(
                                1.dp,
                                Brush.horizontalGradient(
                                    listOf(
                                        UnboundPrimary.copy(alpha = 0.50f),
                                        Color(0xFF7C4DFF).copy(alpha = 0.35f),
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
                                                UnboundPrimary.copy(alpha = 0.16f),
                                                Color(0xFF7C4DFF).copy(alpha = 0.12f),
                                                Color(0xFF12141C)
                                            )
                                        )
                                    )
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                Brush.linearGradient(
                                                    listOf(UnboundPrimary, Color(0xFF7C4DFF))
                                                )
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudSync,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(14.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Sync with YouTube Music",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = OnSurface
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Sign in to synchronize your liked songs and cloud playlists",
                                            fontSize = 11.sp,
                                            color = OnSurfaceVariant,
                                            lineHeight = 15.sp
                                        )
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(20.dp))
                                            .background(UnboundPrimary)
                                            .padding(horizontal = 14.dp, vertical = 7.dp)
                                    ) {
                                        Text(
                                            text = "Connect",
                                            color = OnPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Hero Card: Pure Music Tracks
                        ModernHeroMySongsCard(
                            count = effectiveMusicTracks.size,
                            title = "Music Tracks",
                            subtitle = "${effectiveMusicTracks.size} verified songs on this device",
                            onClick = { subView = LibrarySubView.TRACKS },
                            onShuffle = {
                                if (effectiveMusicTracks.isNotEmpty()) {
                                    val s = effectiveMusicTracks.shuffled()
                                    onTrackSelect(s.first(), s)
                                } else {
                                    onShufflePlayAll()
                                }
                            }
                        )

                        // Audios & Voice Card (for WhatsApp audio, recordings & voice clips)
                        if (effectiveMixedAudioTracks.isNotEmpty()) {
                            ModernBentoAudiosCard(
                                count = effectiveMixedAudioTracks.size,
                                onClick = { subView = LibrarySubView.AUDIOS }
                            )
                        }

                        // 50% - 75% Album Completion Recommendation Banners
                        if (albumCompletions.isNotEmpty()) {
                            for (comp in albumCompletions) {
                                CompleteAlbumBanner(
                                    status = comp,
                                    onDownloadRemaining = { onDownloadRemaining(comp) }
                                )
                            }
                        }

                        // Bento Split Row: Downloads & Favorites
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ModernBentoMediumCard(
                                title = "Downloads",
                                count = effectiveDownloaded.size.toString(),
                                subtitle = "Saved offline",
                                icon = Icons.Default.Download,
                                accentColor = Color(0xFF00E676),
                                badgeText = "OFFLINE",
                                modifier = Modifier.weight(1f),
                                onClick = { subView = LibrarySubView.DOWNLOADED }
                            )

                            ModernBentoMediumCard(
                                title = "Favorites",
                                count = favoriteTracks.size.toString(),
                                subtitle = "Liked tracks",
                                icon = Icons.Default.Favorite,
                                accentColor = Color(0xFFFF5252),
                                badgeText = "LOCAL",
                                modifier = Modifier.weight(1f),
                                onClick = { subView = LibrarySubView.FAVORITES }
                            )
                        }

                        // Utility Row: Folders & Recent
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ModernUtilityCompactCard(
                                title = "Folders",
                                count = audioFolders.size.toString(),
                                subtitle = "Storage",
                                icon = Icons.Default.Folder,
                                accentColor = Color(0xFFFF9100),
                                modifier = Modifier.weight(1f),
                                onClick = { subView = LibrarySubView.FOLDERS }
                            )

                            ModernUtilityCompactCard(
                                title = "Recent",
                                count = recentlyPlayedTracks.size.toString(),
                                subtitle = "Played",
                                icon = Icons.Default.History,
                                accentColor = Color(0xFF00E5FF),
                                modifier = Modifier.weight(1f),
                                onClick = { subView = LibrarySubView.RECENT_PLAYED }
                            )
                        }

                        // Playlists Section
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Playlists",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = OnSurface,
                                        letterSpacing = (-0.01).sp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(SurfaceGlassHighest)
                                            .border(1.dp, BorderGlass, RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = allPlaylists.size.toString(),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = UnboundPrimary
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(UnboundPrimary.copy(alpha = 0.15f))
                                        .clickable {
                                            newPlaylistTitle = ""
                                            showCreatePlaylistDialog = true
                                        }
                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "New Playlist",
                                            tint = UnboundPrimary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "New",
                                            color = UnboundPrimary,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (allPlaylists.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    UnboundPrimary.copy(alpha = 0.22f),
                                                    Color(0xFF7C4DFF).copy(alpha = 0.18f),
                                                    Color(0xFF181818)
                                                )
                                            )
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        UnboundPrimary.copy(alpha = 0.45f),
                                                        Color(0xFF7C4DFF).copy(alpha = 0.30f),
                                                        BorderGlass
                                                    )
                                                )
                                            ),
                                            RoundedCornerShape(18.dp)
                                        )
                                        .clickable {
                                            newPlaylistTitle = ""
                                            showCreatePlaylistDialog = true
                                        }
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(52.dp)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(
                                                    Brush.linearGradient(
                                                        listOf(UnboundPrimary, Color(0xFF7C4DFF))
                                                    )
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.MusicNote,
                                                contentDescription = null,
                                                tint = Color.Black,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(14.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "Create Your Custom Playlist",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = OnSurface
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = "Organize local device audio into personalized playlists",
                                                fontSize = 12.sp,
                                                color = OnSurfaceVariant,
                                                lineHeight = 16.sp
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(20.dp))
                                                .background(UnboundPrimary)
                                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                        ) {
                                            Text(
                                                text = "Create",
                                                color = OnPrimary,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            } else {
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    contentPadding = PaddingValues(horizontal = 2.dp)
                                ) {
                                    items(allPlaylists) { playlist ->
                                        PlaylistCard(
                                            playlist = playlist,
                                            onClick = { onPlaylistClick(playlist) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                LibrarySubView.TRACKS -> {
                    LibraryTracksListView(
                        title = "Music Tracks",
                        subtitle = "${effectiveMusicTracks.size} verified songs on this device",
                        tracks = effectiveMusicTracks,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.AUDIOS -> {
                    LibraryTracksListView(
                        title = "Audios & Voice",
                        subtitle = "${effectiveMixedAudioTracks.size} voice notes, recordings & chat audios",
                        tracks = effectiveMixedAudioTracks,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.FOLDERS -> {
                    LibraryFoldersListView(
                        folders = audioFolders,
                        onBack = { subView = LibrarySubView.HUB },
                        onFolderSelect = { folder ->
                            selectedFolder = folder
                            subView = LibrarySubView.FOLDER_TRACKS
                        }
                    )
                }

                LibrarySubView.FOLDER_TRACKS -> {
                    val currentFolder = selectedFolder
                    LibraryTracksListView(
                        title = currentFolder?.name ?: "Folder Tracks",
                        subtitle = "${currentFolder?.tracks?.size ?: 0} tracks in this folder",
                        tracks = currentFolder?.tracks ?: emptyList(),
                        onBack = { subView = LibrarySubView.FOLDERS },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.FAVORITES -> {
                    LibraryTracksListView(
                        title = "Favorite Songs",
                        subtitle = "${favoriteTracks.size} favorite songs on this device",
                        tracks = favoriteTracks,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.RECENT_PLAYED -> {
                    LibraryTracksListView(
                        title = "Recently Played",
                        subtitle = "${recentlyPlayedTracks.size} songs in playback history",
                        tracks = recentlyPlayedTracks,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }

                LibrarySubView.DOWNLOADED -> {
                    LibraryTracksListView(
                        title = "Downloads",
                        subtitle = "${effectiveDownloaded.size} downloaded songs ready offline",
                        tracks = effectiveDownloaded,
                        onBack = { subView = LibrarySubView.HUB },
                        onTrackSelect = { track, list -> onTrackSelect(track, list) },
                        onPlayNext = onPlayNext,
                        onAddToQueue = onAddToQueue,
                        onAddToPlaylist = onAddToPlaylist,
                        onOpenRingtoneCutter = onOpenRingtoneCutter,
                        onToggleFavorite = onToggleFavorite,
                        onDeleteTrack = { onDeleteDownload(it.id) },
                        onIdentifyTrack = onIdentifyTrack,
                        onBatchIdentify = onBatchIdentify
                    )
                }
            }
        }

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            AlertDialog(
                onDismissRequest = { showCreatePlaylistDialog = false },
                containerColor = UnboundSurfaceContainerHigh,
                title = {
                    Text(
                        text = "Create Playlist",
                        color = OnSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Give your playlist a title to organize your music.",
                            color = OnSurfaceVariant,
                            fontSize = 13.sp
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedTextField(
                            value = newPlaylistTitle,
                            onValueChange = { newPlaylistTitle = it },
                            placeholder = { Text("Playlist name...", color = OnSurfaceVariant.copy(alpha = 0.6f)) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = UnboundPrimary,
                                unfocusedBorderColor = BorderGlass,
                                focusedTextColor = OnSurface,
                                unfocusedTextColor = OnSurface
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (newPlaylistTitle.isNotBlank()) {
                                onCreatePlaylist(newPlaylistTitle.trim())
                                showCreatePlaylistDialog = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = UnboundPrimary)
                    ) {
                        Text("Create", color = OnPrimary, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCreatePlaylistDialog = false }) {
                        Text("Cancel", color = OnSurfaceVariant)
                    }
                }
            )
        }
    }
}
