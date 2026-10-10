/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelLibrary.kt
 * Purpose: Local music library indexing, VLC-style differential storage scanning, acoustic AI track tagging, and custom playlist CRUD.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.data.AlbumCompletionStatus
import com.cubicreates.unboundmusic.data.AudioCategory
import com.cubicreates.unboundmusic.data.CustomPlaylist
import com.cubicreates.unboundmusic.data.LocalPlaylistStore
import com.cubicreates.unboundmusic.data.LocalTrack
import com.cubicreates.unboundmusic.data.MediaStoreAudioBridge
import com.cubicreates.unboundmusic.data.PlaybackStateStore
import com.cubicreates.unboundmusic.data.SmartPlaylistType
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.util.UnboundToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder
import java.util.Locale

private const val TAG = "MainViewModelLibrary"

fun MainViewModel.synthesizeArtistPlaylists(tracks: List<TrackItem>): List<CustomPlaylist> {
    val pureTracks = tracks.filter { it.audioCategory == AudioCategory.MUSIC || it.isIdentifiedMusic }
    val grouped = pureTracks
        .filter { it.artist.isNotBlank() && !it.artist.equals("Unknown Artist", ignoreCase = true) && !it.artist.equals("Unknown", ignoreCase = true) }
        .groupBy { it.artist.trim() }

    val playlists = grouped
        .filter { (_, artistTracks) -> artistTracks.size >= 2 }
        .map { (artistName, artistTracks) ->
            val safeId = "artist_${artistName.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
            CustomPlaylist(
                id = safeId,
                title = "$artistName - Essentials",
                description = "Smart artist mix featuring ${artistTracks.size} downloaded tracks",
                coverUrl = artistTracks.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl ?: "",
                tracks = artistTracks,
                playlistType = SmartPlaylistType.ARTIST_SMART,
                targetArtist = artistName
            )
        }
        .sortedByDescending { it.tracks.size }

    _artistPlaylists.value = playlists
    return playlists
}

fun MainViewModel.synthesizeAlbumicPlaylists(tracks: List<TrackItem>): List<CustomPlaylist> {
    val pureTracks = tracks.filter { it.audioCategory == AudioCategory.MUSIC || it.isIdentifiedMusic }
    val grouped = pureTracks
        .filter { !it.album.isNullOrBlank() && !it.album.equals("Unknown Album", ignoreCase = true) && !it.album.equals("Unknown", ignoreCase = true) }
        .groupBy { it.album.trim() }

    val playlists = grouped
        .filter { (_, albumTracks) -> albumTracks.isNotEmpty() }
        .map { (albumName, albumTracks) ->
            val safeId = "album_${albumName.lowercase().replace(Regex("[^a-z0-9]"), "_")}"
            val primaryArtist = albumTracks.firstOrNull { it.artist.isNotBlank() }?.artist ?: ""
            CustomPlaylist(
                id = safeId,
                title = albumName,
                description = "Album compilation featuring ${albumTracks.size} local tracks",
                coverUrl = albumTracks.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl ?: "",
                tracks = albumTracks,
                playlistType = SmartPlaylistType.ALBUMIC_SMART,
                targetAlbum = albumName,
                targetArtist = primaryArtist
            )
        }
        .sortedByDescending { it.tracks.size }

    _albumicPlaylists.value = playlists
    checkAlbumCompletions(playlists)
    return playlists
}

internal fun MainViewModel.checkAlbumCompletions(albumPlaylists: List<CustomPlaylist>) {
    viewModelScope.launch(Dispatchers.IO) {
        val completionList = mutableListOf<AlbumCompletionStatus>()
        val updatedAlbumics = albumPlaylists.toMutableList()

        for (playlist in albumPlaylists.take(15)) {
            val album = playlist.targetAlbum ?: playlist.title
            val artist = playlist.targetArtist ?: ""
            if (album.isBlank()) continue

            try {
                val (code, jsonStr) = client.getAlbumDetails(artist = artist, album = album)
                if (code == 200 && jsonStr.isNotBlank()) {
                    val root = JSONObject(jsonStr)
                    val officialCount = root.optInt("track_count", 0)
                    val browseId = root.optString("id", "")
                    val tracksArr = root.optJSONArray("tracks")

                    if (officialCount > 0) {
                        val downloaded = playlist.tracks.size
                        val ratio = downloaded.toFloat() / officialCount.toFloat()
                        val missing = mutableListOf<TrackItem>()

                        if (tracksArr != null) {
                            val downloadedTitles = playlist.tracks.map { it.title.trim().lowercase() }.toSet()
                            for (i in 0 until tracksArr.length()) {
                                val tObj = tracksArr.optJSONObject(i) ?: continue
                                val tTitle = tObj.optString("title", "")
                                if (tTitle.isNotBlank() && !downloadedTitles.contains(tTitle.trim().lowercase())) {
                                    missing.add(
                                        TrackItem(
                                            id = tObj.optString("id", ""),
                                            title = tTitle,
                                            artist = tObj.optString("artist", artist),
                                            album = album,
                                            durationMs = tObj.optLong("duration_ms", 0L),
                                            coverUrl = tObj.optString("thumbnail_url", playlist.effectiveCoverUrl),
                                            streamUrl = tObj.optString("stream_url", ""),
                                            source = "ytmusic_album"
                                        )
                                    )
                                }
                            }
                        }

                        val status = AlbumCompletionStatus(
                            albumTitle = album,
                            artistName = artist,
                            downloadedCount = downloaded,
                            officialTotalCount = officialCount,
                            completionRatio = ratio,
                            albumBrowseId = browseId,
                            missingTracks = missing
                        )
                        if (status.isEligibleForPrompt) {
                            completionList.add(status)
                        }

                        val pIdx = updatedAlbumics.indexOfFirst { it.id == playlist.id }
                        if (pIdx >= 0) {
                            updatedAlbumics[pIdx] = updatedAlbumics[pIdx].copy(
                                officialTrackCount = officialCount,
                                completionRatio = ratio,
                                albumBrowseId = browseId
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to check album completion for $album: ${e.message}")
            }
        }

        withContext(Dispatchers.Main) {
            _albumCompletions.value = completionList
            _albumicPlaylists.value = updatedAlbumics
        }
    }
}

internal fun MainViewModel.setLibraryTracks(tracks: List<TrackItem>) {
    _libraryTracks.value = tracks
    val music = tracks.filter { it.audioCategory == AudioCategory.MUSIC || it.isIdentifiedMusic }
    _musicTracks.value = music
    _mixedAudioTracks.value = tracks.filter { it.audioCategory == AudioCategory.MIXED_AUDIO && !it.isIdentifiedMusic }
    synthesizeArtistPlaylists(music)
    synthesizeAlbumicPlaylists(music)
}

/**
 * Dual-Engine Hybrid Audio Scanner:
 * 1. MediaStore query via Android OS bridge (Kotlin) for instant UI responsiveness.
 * 2. Native Go daemon crawler (Go) for deep POSIX storage traversal, .nomedia bypass,
 *    magic byte probing, ID3 tag extraction, and SQLite database indexing.
 */
fun MainViewModel.performUniversalAudioScan() {
    if (!isUniversalScanning.compareAndSet(false, true)) {
        Log.d(TAG, "performUniversalAudioScan already in progress, skipping duplicate scan request")
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // 1. Fast Kotlin MediaStore pass for immediate UI responsiveness
            val mediaStoreTracks = MediaStoreAudioBridge.queryMediaStoreAudio(getApplication())

            // 1b. Direct recursive physical storage scan (finds files missed by MediaStore, .nomedia folders, USB transfers)
            val knownKeys = mediaStoreTracks.map {
                try {
                    File(it.filePath).canonicalPath.lowercase(Locale.ROOT)
                } catch (_: Exception) {
                    it.filePath.lowercase(Locale.ROOT)
                }
            }.toSet()
            val directDiscovered = MediaStoreAudioBridge.scanPhysicalStorageDirect(getApplication(), knownKeys)
            val allDiscoveredTracks = mediaStoreTracks + directDiscovered

            if (allDiscoveredTracks.isNotEmpty()) {
                val initialTracks = allDiscoveredTracks.map { it.toTrackItem() }
                setLibraryTracks(initialTracks)
                refreshFavoritesList()

                val initialFolders = mutableMapOf<String, MutableList<LocalTrack>>()
                for (track in allDiscoveredTracks) {
                    val folderName = track.sourceFolder.ifBlank { "Device Audio" }
                    initialFolders.getOrPut(folderName) { mutableListOf() }.add(track)
                }
                _libraryFolders.value = initialFolders

                _whatsappCount.value = allDiscoveredTracks.count {
                    it.sourceFolder.contains("WhatsApp", ignoreCase = true) ||
                    it.filePath.contains("WhatsApp", ignoreCase = true)
                }
                _telegramCount.value = allDiscoveredTracks.count {
                    it.sourceFolder.contains("Telegram", ignoreCase = true) ||
                    it.filePath.contains("Telegram", ignoreCase = true)
                }
                _downloadsCount.value = allDiscoveredTracks.count {
                    it.sourceFolder.contains("Download", ignoreCase = true) ||
                    it.sourceFolder.contains("Unbound", ignoreCase = true) ||
                    it.filePath.contains("Download", ignoreCase = true)
                }
            }

            // 2. Discover device storage roots and dispatch to Go backend crawler (.nomedia bypass)
            try {
                val deviceRoots = MediaStoreAudioBridge.discoverDeviceStorageRoots(getApplication())
                client.scanStorage(deviceRoots)
                if (allDiscoveredTracks.isNotEmpty()) {
                    client.ingestMediaStoreTracks(allDiscoveredTracks)
                }

                // 3. Retrieve fully indexed tracks from Go SQLite database
                val daemonTracks = client.getLocalTracks("all")
                val unboundDownloads = client.getLocalTracks("Unbound Downloads")
                if (unboundDownloads.isNotEmpty()) {
                    val dlIds = unboundDownloads.map { it.id }.toSet()
                    _downloadedTrackIds.value = _downloadedTrackIds.value + dlIds
                }

                // 4. Merge discovered tracks with daemon tracks, deduplicating by normalized path
                val combinedMap = LinkedHashMap<String, LocalTrack>()
                for (track in allDiscoveredTracks) {
                    val key = track.filePath.lowercase(Locale.ROOT)
                    combinedMap[key] = track
                }
                for (track in daemonTracks) {
                    val key = track.filePath.lowercase(Locale.ROOT)
                    val existing = combinedMap[key]
                    if (existing != null) {
                        combinedMap[key] = existing.copy(
                            id = if (existing.id.isNotBlank()) existing.id else track.id,
                            durationMs = if (existing.durationMs > 0) existing.durationMs else track.durationMs,
                            coverUrl = if (existing.coverUrl.isNotBlank()) existing.coverUrl else track.coverUrl,
                            album = if (existing.album.isNotBlank()) existing.album else track.album,
                            artist = if (existing.artist.isNotBlank() && existing.artist != "Unknown Artist") existing.artist else track.artist,
                            title = if (existing.title.isNotBlank() && existing.title != "Unknown") existing.title else track.title,
                            audioCategory = if (existing.audioCategory != AudioCategory.MUSIC || existing.isIdentifiedMusic) existing.audioCategory else track.audioCategory
                        )
                    } else {
                        combinedMap[key] = track
                    }
                }

                val allLocal = combinedMap.values.toList()

                // 5. Update folders map
                val updatedFolders = mutableMapOf<String, MutableList<LocalTrack>>()
                for (track in allLocal) {
                    val folderName = track.sourceFolder.ifBlank { "Device Audio" }
                    updatedFolders.getOrPut(folderName) { mutableListOf() }.add(track)
                }
                _libraryFolders.value = updatedFolders

                // 6. Update category counts
                val waTracks = allLocal.filter {
                    it.sourceFolder.contains("WhatsApp", ignoreCase = true) ||
                    it.filePath.contains("WhatsApp", ignoreCase = true)
                }
                val tgTracks = allLocal.filter {
                    it.sourceFolder.contains("Telegram", ignoreCase = true) ||
                    it.filePath.contains("Telegram", ignoreCase = true)
                }
                val dlTracks = allLocal.filter {
                    it.sourceFolder.contains("Download", ignoreCase = true) ||
                    it.sourceFolder.contains("Unbound", ignoreCase = true) ||
                    it.filePath.contains("Download", ignoreCase = true)
                }

                _whatsappCount.value = waTracks.size
                _telegramCount.value = tgTracks.size
                _downloadsCount.value = dlTracks.size

                val allTrackItems = allLocal.map { it.toTrackItem() }
                if (allTrackItems.isNotEmpty()) {
                    setLibraryTracks(allTrackItems)
                    refreshFavoritesList()
                }
            } catch (de: Exception) {
                Log.w(TAG, "Daemon crawler storage pass note: ${de.message}")
            }
        } catch (e: Exception) {
            Log.d(TAG, "Universal audio scan note: ${e.message}")
        } finally {
            isUniversalScanning.set(false)
            try {
                daemonManager.trimMemory()
            } catch (_: Exception) {}
        }
    }
}

fun MainViewModel.rescanLocalStorage() {
    performUniversalAudioScan()
}

/**
 * Silent differential storage scan modeled after VLC Media Player.
 * Runs in the background on app open/resume to detect newly added audio files
 * from downloads, WhatsApp, Telegram, or file transfers without interrupting UI.
 */
fun MainViewModel.triggerVlcDifferentialStorageScan(silent: Boolean = true) {
    if (!PlaybackStateStore.isVlcAutoScanEnabled(getApplication())) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            performUniversalAudioScan()
        } catch (e: Throwable) {
            Log.w(TAG, "VLC differential auto-scan note: ${e.message}")
        }
    }
}

fun MainViewModel.refreshLibrary() {
    rescanLocalStorage()
    loadDownloadedMusicFiles()
    updateStorageMetrics()
}

internal fun MainViewModel.resolveLocalFilePath(track: TrackItem): String {
    val stream = track.streamUrl
    if (stream.startsWith("file://")) {
        val clean = stream.removePrefix("file://")
        return try {
            URLDecoder.decode(clean, "UTF-8")
        } catch (_: Exception) {
            clean
        }
    }
    if (stream.startsWith("content://")) {
        val resolved = MediaStoreAudioBridge.resolveContentUriToPath(getApplication(), stream)
        if (!resolved.isNullOrBlank()) return resolved
    }
    if (stream.startsWith("/")) {
        return stream
    }
    // Fallback: search in library folders for matching track ID
    for (folderTracks in _libraryFolders.value.values) {
        val match = folderTracks.firstOrNull { it.id == track.id }
        if (match != null && match.filePath.isNotBlank()) {
            val fp = match.filePath
            return if (fp.startsWith("file://")) fp.removePrefix("file://") else fp
        }
    }
    return stream.ifBlank { track.id }
}

fun MainViewModel.identifyTrack(track: TrackItem) {
    if (_isIdentifyingTrack.value != null) return
    _isIdentifyingTrack.value = track.id
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val targetPath = resolveLocalFilePath(track)
            withContext(Dispatchers.Main) {
                UnboundToast.show(
                    getApplication(),
                    "Identifying '${track.title}'...",
                    isLong = false
                )
            }

            val result = client.identifyTrackDetailed(targetPath)
            val identified = result.track
            if (identified != null && identified.title.isNotBlank()) {
                val updatedTrack = track.copy(
                    title = identified.title,
                    artist = if (identified.artist.isNotBlank()) identified.artist else track.artist,
                    album = if (identified.album.isNotBlank()) identified.album else track.album,
                    coverUrl = if (identified.coverUrl.isNotBlank()) identified.coverUrl else track.coverUrl,
                    audioCategory = AudioCategory.MUSIC,
                    isIdentifiedMusic = true
                )
                withContext(Dispatchers.Main) {
                    setLibraryTracks(_libraryTracks.value.map { if (it.id == track.id) updatedTrack else it })
                    _favoriteTracks.value = _favoriteTracks.value.map { if (it.id == track.id) updatedTrack else it }
                    if (_currentTrack.value.id == track.id) {
                        _currentTrack.value = updatedTrack
                    }
                    val methodBadge = when {
                        identified.method.contains("llm", ignoreCase = true) || identified.method.contains("ai", ignoreCase = true) -> "On-Device AI"
                        identified.method.contains("acoustid", ignoreCase = true) -> "AcoustID"
                        identified.method.contains("voice", ignoreCase = true) -> "Media Parser"
                        else -> "Smart Heuristic"
                    }
                    UnboundToast.show(
                        getApplication(),
                        "Graduated to Music via $methodBadge (${(identified.confidence * 100).toInt()}%): '${updatedTrack.artist} - ${updatedTrack.title}'",
                        isLong = true
                    )
                }
            } else {
                val errDetail = result.errorMessage ?: "HTTP ${result.statusCode}: No match found"
                withContext(Dispatchers.Main) {
                    UnboundToast.show(
                        getApplication(),
                        "Identification diagnostic: Could not identify track ($errDetail)",
                        isLong = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to identify track ${track.title}: ${e.message}")
            withContext(Dispatchers.Main) {
                UnboundToast.show(
                    getApplication(),
                    "Identification failed: ${e.message}",
                    isLong = true
                )
            }
        } finally {
            _isIdentifyingTrack.value = null
        }
    }
}

fun MainViewModel.batchIdentifyUnknownTracks() {
    viewModelScope.launch(Dispatchers.IO) {
        val candidates = _libraryTracks.value.filter {
            it.audioCategory == AudioCategory.MIXED_AUDIO ||
            it.title.startsWith("AUD-", ignoreCase = true) ||
            it.title.startsWith("PTT-", ignoreCase = true) ||
            it.title.startsWith("voice_", ignoreCase = true) ||
            it.title.startsWith("WA", ignoreCase = true) ||
            it.artist.equals("Unknown Artist", ignoreCase = true) ||
            it.artist.isBlank() ||
            it.title.contains("y2mate", ignoreCase = true)
        }
        if (candidates.isEmpty()) {
            withContext(Dispatchers.Main) {
                UnboundToast.show(getApplication(), "All audio tracks are already identified!", isLong = false)
            }
            return@launch
        }
        withContext(Dispatchers.Main) {
            UnboundToast.show(getApplication(), "Identifying ${candidates.size} untagged tracks...", isLong = false)
        }
        var identifiedCount = 0
        var errorCount = 0
        var lastError = ""
        for (track in candidates) {
            val targetPath = resolveLocalFilePath(track)
            val result = client.identifyTrackDetailed(targetPath)
            val identified = result.track
            if (identified != null && identified.title.isNotBlank()) {
                val updated = track.copy(
                    title = identified.title,
                    artist = if (identified.artist.isNotBlank()) identified.artist else track.artist,
                    album = if (identified.album.isNotBlank()) identified.album else track.album,
                    coverUrl = if (identified.coverUrl.isNotBlank()) identified.coverUrl else track.coverUrl,
                    audioCategory = AudioCategory.MUSIC,
                    isIdentifiedMusic = true
                )
                identifiedCount++
                withContext(Dispatchers.Main) {
                    setLibraryTracks(_libraryTracks.value.map { if (it.id == track.id) updated else it })
                }
            } else {
                errorCount++
                lastError = result.errorMessage ?: "HTTP ${result.statusCode}"
            }
            delay(250)
        }
        withContext(Dispatchers.Main) {
            val msg = if (identifiedCount > 0) {
                "Identified $identifiedCount/${candidates.size} audio files!${if (errorCount > 0) " ($errorCount failed: $lastError)" else ""}"
            } else {
                "Diagnostic: 0/${candidates.size} identified. Reason: $lastError"
            }
            UnboundToast.show(getApplication(), msg, isLong = true)
        }
    }
}

fun MainViewModel.loadCustomPlaylists() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var playlists = try { client.getPlaylists() } catch (_: Throwable) { emptyList() }
            if (playlists.isEmpty()) {
                val legacy = try { LocalPlaylistStore.getPlaylists(getApplication()) } catch (_: Throwable) { emptyList() }
                if (legacy.isNotEmpty()) {
                    Log.i(TAG, "Migrating ${legacy.size} legacy playlists to Go SQLite backend...")
                    for (p in legacy) {
                        try {
                            client.createPlaylist(p.title, p.description, p.coverUrl, p.tracks, p.id)
                        } catch (_: Throwable) {}
                    }
                    playlists = try { client.getPlaylists() } catch (_: Throwable) { emptyList() }
                }
            }
            if (playlists.isEmpty()) {
                playlists = try { LocalPlaylistStore.getPlaylists(getApplication()) } catch (_: Throwable) { emptyList() }
            }
            withContext(Dispatchers.Main) {
                _customPlaylists.value = playlists
                val currentActive = _activeCustomPlaylist.value
                if (currentActive != null) {
                    _activeCustomPlaylist.value = playlists.firstOrNull { it.id == currentActive.id }
                }
            }
        } catch (e: Throwable) {
            Log.w(TAG, "loadCustomPlaylists note: ${e.message}")
        }
    }
}

fun MainViewModel.createCustomPlaylist(
    title: String,
    description: String = "",
    coverUrl: String = "",
    initialTracks: List<TrackItem> = emptyList()
): CustomPlaylist {
    val created = LocalPlaylistStore.createPlaylist(getApplication(), title, description, coverUrl, initialTracks)
    viewModelScope.launch(Dispatchers.IO) {
        client.createPlaylist(title, description, coverUrl, initialTracks, created.id)
        loadCustomPlaylists()
    }
    UnboundToast.show(getApplication(), "Created playlist '${created.title}'", isLong = false)
    return created
}

fun MainViewModel.updateCustomPlaylist(playlistId: String, title: String, description: String = "", coverUrl: String = "") {
    viewModelScope.launch(Dispatchers.IO) {
        LocalPlaylistStore.updatePlaylistDetails(getApplication(), playlistId, title, description, coverUrl)
        val updated = client.updatePlaylistDetails(playlistId, title, description, coverUrl)
        loadCustomPlaylists()
        withContext(Dispatchers.Main) {
            if (updated != null) {
                _activeCustomPlaylist.value = updated
                UnboundToast.show(getApplication(), "Playlist updated", isLong = false)
            }
        }
    }
}

fun MainViewModel.deleteCustomPlaylist(playlistId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        LocalPlaylistStore.deletePlaylist(getApplication(), playlistId)
        val success = client.deletePlaylist(playlistId)
        loadCustomPlaylists()
        withContext(Dispatchers.Main) {
            if (success) {
                if (_activeCustomPlaylist.value?.id == playlistId) {
                    _activeCustomPlaylist.value = null
                }
                UnboundToast.show(getApplication(), "Playlist deleted", isLong = false)
            }
        }
    }
}

fun MainViewModel.addTrackToCustomPlaylist(playlistId: String, track: TrackItem) {
    viewModelScope.launch(Dispatchers.IO) {
        val (added, _) = LocalPlaylistStore.addTrackToPlaylist(getApplication(), playlistId, track)
        val updated = client.addTrackToPlaylist(playlistId, track)
        loadCustomPlaylists()
        withContext(Dispatchers.Main) {
            if (updated != null) {
                if (_activeCustomPlaylist.value?.id == playlistId) {
                    _activeCustomPlaylist.value = updated
                }
                val msg = if (added) "Added '${track.title}' to '${updated.title}'" else "'${track.title}' is already in '${updated.title}'"
                UnboundToast.show(getApplication(), msg, isLong = false)
            }
            _trackToAddToPlaylist.value = null
        }
    }
}

fun MainViewModel.removeTrackFromCustomPlaylist(playlistId: String, trackIndex: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        LocalPlaylistStore.removeTrackFromPlaylist(getApplication(), playlistId, trackIndex)
        val updated = client.removeTrackFromPlaylist(playlistId, trackIndex)
        loadCustomPlaylists()
        withContext(Dispatchers.Main) {
            if (updated != null) {
                if (_activeCustomPlaylist.value?.id == playlistId) {
                    _activeCustomPlaylist.value = updated
                }
                UnboundToast.show(getApplication(), "Removed track from playlist", isLong = false)
            }
        }
    }
}

fun MainViewModel.removeTrackFromCustomPlaylist(playlistId: String, trackId: String) {
    val playlist = _customPlaylists.value.find { it.id == playlistId } ?: return
    val index = playlist.tracks.indexOfFirst { it.id == trackId }
    if (index >= 0) {
        removeTrackFromCustomPlaylist(playlistId, index)
    }
}

fun MainViewModel.moveTrackInCustomPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        LocalPlaylistStore.reorderTracks(getApplication(), playlistId, fromIndex, toIndex)
        val updated = client.reorderPlaylist(playlistId, fromIndex, toIndex)
        loadCustomPlaylists()
        withContext(Dispatchers.Main) {
            if (updated != null && _activeCustomPlaylist.value?.id == playlistId) {
                _activeCustomPlaylist.value = updated
            }
        }
    }
}

fun MainViewModel.openCustomPlaylist(playlist: CustomPlaylist) {
    _activeCustomPlaylist.value = playlist
}

fun MainViewModel.closeCustomPlaylist() {
    _activeCustomPlaylist.value = null
}

fun MainViewModel.showAddToPlaylist(track: TrackItem) {
    _trackToAddToPlaylist.value = track
}

fun MainViewModel.hideAddToPlaylist() {
    _trackToAddToPlaylist.value = null
}
