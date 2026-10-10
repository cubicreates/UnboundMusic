/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelPlayback.kt
 * Purpose: Playback commands, stream resolution, queue management, automix, and favorites.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.os.Looper
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.service.PlaybackMode
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.Locale

private const val TAG = "MainViewModelPlayback"

/**
 * Plays a track by first resolving its stream URL via the Go daemon, then sending to Media3.
 */
fun MainViewModel.playTrack(track: TrackItem, preserveQueue: Boolean = false) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        viewModelScope.launch(Dispatchers.Main) {
            playTrack(track, preserveQueue)
        }
        return
    }
    val offlineMatch = findMatchingOfflineTrack(track)
    val targetTrack = if (offlineMatch != null) {
        track.copy(
            streamUrl = offlineMatch.streamUrl,
            coverUrl = offlineMatch.coverUrl.ifBlank { track.coverUrl }
        )
    } else {
        track
    }

    _currentTrack.value = targetTrack
    saveCurrentPlaybackState(0L)
    try {
        PlaybackStateStore.addRecentlyPlayed(getApplication(), targetTrack)
        _recentlyPlayedTracks.value = PlaybackStateStore.getRecentlyPlayed(getApplication())
        refreshAdaptiveQuickPicks()
    } catch (_: Exception) {}

    if (offlineMatch != null) {
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Playing offline downloaded version (0 MB data)", isLong = false)
    } else {
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Loading '${track.title}'...", isLong = false)
    }

    // If an explicit album/playlist requested queue preservation, retain it
    if (preserveQueue && _currentQueue.value.size > 1) {
        serviceConnection.setQueue(_currentQueue.value)
    } else {
        val initialQ = listOf(targetTrack)
        _currentQueue.value = initialQ
        serviceConnection.setQueue(initialQ)
        if (!_isYouTubeConnected.value) {
            fetchGuestAlgorithmicRadio(targetTrack)
        } else {
            fetchAutomixQueue(targetTrack)
        }
    }

    // Immediately reset lyrics state and fetch for this specific track
    loadLyricsForTrack(targetTrack)
    _rydVotes.value = null
    viewModelScope.launch(Dispatchers.IO) {
        fetchRydVotes(targetTrack)
    }

    viewModelScope.launch(Dispatchers.IO) {
        try {
            val streamUrl = resolveStreamUrl(targetTrack)
            val resolvedTrack = targetTrack.copy(streamUrl = streamUrl)
            withContext(Dispatchers.Main) {
                _currentTrack.value = resolvedTrack
            }

            if (streamUrl.isNotBlank()) {
                // Play via Media3 service
                serviceConnection.playTrack(resolvedTrack, streamUrl)
            } else {
                Log.w(TAG, "Direct stream resolution empty for ${targetTrack.title}, using localhost proxy stream")
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Direct stream empty for '${targetTrack.title}', falling back to proxy")
                val fallbackUrl = if (targetTrack.id.isNotBlank() && targetTrack.id.length == 11 && !targetTrack.id.startsWith("local:")) {
                    "http://127.0.0.1:45731/api/v1/proxy/stream?id=${targetTrack.id}"
                } else targetTrack.streamUrl
                if (fallbackUrl.isNotBlank()) {
                    serviceConnection.playTrack(targetTrack.copy(streamUrl = fallbackUrl), fallbackUrl)
                } else {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Error: No fallback URL for '${targetTrack.title}'")
                }
            }

            // Fetch canvas visuals and SponsorBlock skip segments in parallel
            launch { fetchCanvas(resolvedTrack) }
            launch { fetchSkipSegments(resolvedTrack) }

        } catch (e: Exception) {
            Log.e(TAG, "Error playing track: ${e.message}")
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Play Track Exception:\n${e.message}")
            val fallbackUrl = if (targetTrack.id.isNotBlank() && targetTrack.id.length == 11 && !targetTrack.id.startsWith("local:")) {
                "http://127.0.0.1:45731/api/v1/proxy/stream?id=${targetTrack.id}"
            } else targetTrack.streamUrl
            if (fallbackUrl.isNotBlank()) {
                serviceConnection.playTrack(targetTrack.copy(streamUrl = fallbackUrl), fallbackUrl)
            }
        }
    }
}

/**
 * Searches downloaded tracks and local library tracks for a local matching version of the given track.
 * Checks exact ID, partial ID, and fuzzy normalized (title + artist) match.
 */
fun MainViewModel.findMatchingOfflineTrack(track: TrackItem): TrackItem? {
    val candidates = (_downloadedMusicTracks.value + _libraryTracks.value).distinctBy { it.id }
    if (candidates.isEmpty()) return null

    fun clean(s: String): String = s.lowercase(Locale.ROOT)
        .replace(Regex("\\[.*?\\]|\\(.*?\\)"), "")
        .replace(Regex("[^a-z0-9]"), "")

    val targetTitleClean = clean(track.title)
    val targetArtistClean = clean(track.artist)

    // 1. Direct ID match
    if (track.id.isNotBlank()) {
        val byId = candidates.firstOrNull { cand ->
            cand.streamUrl.isNotBlank() &&
            (cand.streamUrl.startsWith("file://") || cand.streamUrl.startsWith("content://")) &&
            (cand.id == track.id || cand.id == "local_dl_${track.id}" || cand.id.contains(track.id) || track.id.contains(cand.id))
        }
        if (byId != null && isLocalAudioPlayable(byId.streamUrl)) return byId
    }

    // 2. Normalized Title & Artist match
    if (targetTitleClean.length >= 3) {
        for (cand in candidates) {
            if (!cand.streamUrl.startsWith("file://") && !cand.streamUrl.startsWith("content://")) continue
            val candTitleClean = clean(cand.title)
            val candArtistClean = clean(cand.artist)

            val titleMatches = candTitleClean == targetTitleClean ||
                    (candTitleClean.length >= 5 && targetTitleClean.length >= 5 &&
                            (candTitleClean.contains(targetTitleClean) || targetTitleClean.contains(candTitleClean)))

            if (titleMatches) {
                val artistMatches = targetArtistClean.isBlank() || candArtistClean.isBlank() ||
                        candArtistClean == "unknownartist" || targetArtistClean == "unknownartist" ||
                        candArtistClean == targetArtistClean ||
                        candArtistClean.contains(targetArtistClean) || targetArtistClean.contains(candArtistClean)

                if (artistMatches && isLocalAudioPlayable(cand.streamUrl)) {
                    return cand
                }
            }
        }
    }
    return null
}

internal fun MainViewModel.isLocalAudioPlayable(streamUrl: String): Boolean {
    return try {
        when {
            streamUrl.startsWith("file://") -> {
                val f = File(streamUrl.removePrefix("file://"))
                f.exists() && f.length() > 0
            }
            streamUrl.startsWith("content://") -> true
            else -> false
        }
    } catch (_: Exception) { false }
}

/**
 * Resolves a stream URL for a track via the Go daemon /api/v1/stream endpoint.
 * Implements zero-data interception: checks local storage first, falls back to remote.
 * Retries up to 5 times if the daemon is cold-starting.
 */
internal suspend fun MainViewModel.resolveStreamUrl(track: TrackItem): String {
    // 1. If the track already has a local file://, content://, localhost proxy audio stream, or valid http stream, use it directly
    if (track.streamUrl.startsWith("file://") ||
        track.streamUrl.startsWith("content://") ||
        (track.streamUrl.contains("127.0.0.1") && track.streamUrl.contains("/proxy/stream")) ||
        (track.streamUrl.startsWith("http") && track.streamUrl.contains("googlevideo.com"))) {
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Playing direct URL for '${track.title}'", isLong = false)
        return track.streamUrl
    }

    // 2. Zero-Data Local Interception: Check if song is already downloaded or in local storage
    val offlineMatch = findMatchingOfflineTrack(track)
    if (offlineMatch != null && offlineMatch.streamUrl.isNotBlank()) {
        Log.i(TAG, "Zero-Data Match: Redirecting search track '${track.title}' to offline '${offlineMatch.title}' -> ${offlineMatch.streamUrl}")
        com.cubicreates.unboundmusic.util.UnboundToast.show(
            getApplication(),
            "Playing offline downloaded version (0 MB data)",
            isLong = false
        )
        return offlineMatch.streamUrl
    }

    // Try resolving via Go daemon (zero-data interception + YouTube stream resolution)
    _streamDebugMessage.value = "Resolving stream for ${track.title}..."
    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Resolving '${track.title}' (ID: ${track.id})...", isLong = false)
    for (attempt in 1..5) {
        try {
            val (code, resp) = client.getStream(
                videoId = track.id,
                title = track.title,
                artist = track.artist
            )
            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                val direct = json.optString("direct_stream_url", "")
                val proxy = json.optString("proxy_stream_url", "")
                val resolved = json.optString("stream_url", "")
                val streamType = json.optString("stream_type", "REMOTE")

                // Prefer local cached file, then localhost proxy (bounded chunking prevents CDN 403), then direct CDN
                val finalUrl = when {
                    resolved.isNotBlank() && (resolved.startsWith("file://") || resolved.startsWith("content://")) -> resolved
                    proxy.isNotBlank() && proxy.startsWith("http") -> proxy
                    direct.isNotBlank() && direct.startsWith("http") -> direct
                    resolved.isNotBlank() && resolved.startsWith("http") -> resolved
                    else -> ""
                }

                if (finalUrl.isNotBlank()) {
                    Log.i(TAG, "Stream resolved (attempt $attempt): type=$streamType for '${track.title}' -> $finalUrl")
                    _streamDebugMessage.value = "Stream ready: $streamType ($finalUrl)"
                    com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Stream Ready: $streamType", isLong = false)
                    return finalUrl
                }
            } else {
                Log.w(TAG, "Daemon stream resolution error attempt $attempt: code=$code, resp=$resp")
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Daemon error (try $attempt/5): code=$code\n$resp")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Stream resolution attempt $attempt failed: ${e.message}")
            _streamDebugMessage.value = "Attempt $attempt failed: ${e.message}"
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Resolution try $attempt failed: ${e.message}")
        }
        if (attempt < 5) {
            delay(400)
        }
    }

    // Secondary fallback: search track title + artist to find matching YouTube stream
    if (track.title.isNotBlank()) {
        try {
            _streamDebugMessage.value = "Direct stream not ready; searching for '${track.title}'"
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Direct stream failed. Searching YouTube for '${track.title}'...")
            val searchQuery = "${track.title} ${track.artist}".trim()
            val (searchCode, searchResp) = client.search(searchQuery)
            if (searchCode in 200..299 && searchResp.isNotBlank()) {
                val searchJson = JSONObject(searchResp)
                val tracksArr = searchJson.optJSONArray("tracks") ?: searchJson.optJSONArray("results")
                if (tracksArr != null && tracksArr.length() > 0) {
                    val firstMatch = tracksArr.getJSONObject(0)
                    val matchId = firstMatch.optString("id", firstMatch.optString("video_id", ""))
                    if (matchId.isNotBlank() && matchId.length == 11) {
                        val (streamCode, streamResp) = client.getStream(videoId = matchId)
                        if (streamCode in 200..299 && streamResp.isNotBlank()) {
                            val json = JSONObject(streamResp)
                            val proxy = json.optString("proxy_stream_url", "")
                            val resolved = json.optString("stream_url", "")
                            val target = if (proxy.isNotBlank() && proxy.startsWith("http")) proxy else resolved
                            if (target.isNotBlank()) {
                                Log.i(TAG, "Stream resolved via search fallback for '${track.title}' -> $target")
                                _streamDebugMessage.value = "Stream resolved via search match for '${track.title}'"
                                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Resolved via YouTube search!", isLong = false)
                                return target
                            }
                        }
                        // Direct localhost proxy fallback for matched video ID
                        _streamDebugMessage.value = "Using proxy fallback for match ID $matchId"
                        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Using proxy for matched ID $matchId")
                        return "http://127.0.0.1:45731/api/v1/proxy/stream?id=$matchId"
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search fallback resolution failed: ${e.message}")
            com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Search fallback failed: ${e.message}")
        }
    }

    // Tertiary fallback: if track.id is a valid 11-char YouTube ID, use the embedded Go daemon proxy stream endpoint directly
    if (track.id.isNotBlank() && track.id.length == 11 && !track.id.startsWith("local:")) {
        Log.i(TAG, "Stream resolved via localhost proxy fallback for '${track.title}' (${track.id})")
        _streamDebugMessage.value = "Using localhost proxy stream for '${track.title}'"
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Using engine proxy stream for '${track.title}'")
        return "http://127.0.0.1:45731/api/v1/proxy/stream?id=${track.id}"
    }

    // Quaternary fallback: use title + artist on localhost proxy (Go engine automatically searches YouTube)
    if (track.title.isNotBlank()) {
        val query = if (track.artist.isNotBlank()) "${track.title} ${track.artist}" else track.title
        val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8")
        Log.i(TAG, "Stream resolved via query proxy fallback for '$query'")
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Falling back to engine query proxy for '${track.title}'")
        return "http://127.0.0.1:45731/api/v1/proxy/stream?id=$encodedQuery"
    }

    // Fallback: return existing stream URL if valid, otherwise empty string
    val fallback = track.streamUrl
    if (fallback.isBlank()) {
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "FATAL: No stream URL could be found for '${track.title}'. Daemon may be offline.")
    }
    return if (fallback.startsWith("http://") || fallback.startsWith("https://") || fallback.startsWith("file://") || fallback.startsWith("content://")) {
        fallback
    } else {
        ""
    }
}

/**
 * Plays a selected track within a playlist context, populating the queue so Next/Previous work.
 */
fun MainViewModel.playTrackWithQueue(track: TrackItem, queue: List<TrackItem>) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
        viewModelScope.launch(Dispatchers.Main) {
            playTrackWithQueue(track, queue)
        }
        return
    }
    // If queue has multiple tracks (e.g. an Album or Playlist), preserve the entire album queue
    if (queue.size > 1) {
        _currentQueue.value = queue
        serviceConnection.setQueue(queue)
        playTrack(track, preserveQueue = true)
    } else {
        playTrack(track, preserveQueue = false)
    }
    saveCurrentPlaybackState(0L)
}

fun MainViewModel.dismissMiniPlayer() {
    serviceConnection.clearPlayback()
    _currentTrack.value = TrackItem()
    _currentQueue.value = emptyList()
    viewModelScope.launch(Dispatchers.IO) {
        try {
            com.cubicreates.unboundmusic.service.LockscreenArtworkManager.restoreOriginalLockscreenArtwork(getApplication())
        } catch (_: Exception) {}
    }
}

fun MainViewModel.togglePlayPause() {
    val state = serviceConnection.playbackState.value
    if (!serviceConnection.isPlayerReady() || state.mediaItemCount == 0 || state.durationMs <= 0L) {
        playTrack(_currentTrack.value)
    } else {
        serviceConnection.togglePlayPause()
    }
}

fun MainViewModel.toggleFavorite() {
    val willBeFav = !_isFavorite.value
    _isFavorite.value = willBeFav
    val current = _currentTrack.value
    val effectiveId = if (current.id.isNotBlank()) current.id else current.streamUrl
    val trackToSave = if (current.id.isBlank()) current.copy(id = effectiveId) else current
    if (effectiveId.isNotBlank() || current.title.isNotBlank()) {
        if (willBeFav) {
            PlaybackStateStore.saveFavoriteTrack(getApplication(), trackToSave)
            val currList = _favoriteTracks.value.toMutableList()
            if (currList.none { it.id == trackToSave.id || (it.title == trackToSave.title && it.artist == trackToSave.artist) }) {
                currList.add(0, trackToSave)
                _favoriteTracks.value = currList
            }
        } else {
            PlaybackStateStore.removeFavoriteTrack(getApplication(), effectiveId, current.title)
            _favoriteTracks.value = _favoriteTracks.value.filterNot { 
                it.id == effectiveId || it.streamUrl == effectiveId || 
                (it.title.equals(current.title, ignoreCase = true) && it.artist.equals(current.artist, ignoreCase = true))
            }
        }
    }
    if (willBeFav && _autoDownloadLikedSongs.value) {
        startTrackDownload(trackToSave)
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (trackToSave.id.isNotBlank() || trackToSave.title.isNotBlank()) {
                client.toggleFavorite(trackToSave)
                client.toggleTrackLike(trackToSave.id, willBeFav)
            }
            loadSyncedYouTubeTracks()
            refreshFavoritesList()
        } catch (e: Exception) {
            Log.w(TAG, "Track like toggle error: ${e.message}")
        }
    }
}

fun MainViewModel.toggleTrackFavorite(track: TrackItem) {
    val effectiveId = if (track.id.isNotBlank()) track.id else track.streamUrl
    if (effectiveId.isBlank() && track.title.isBlank()) return
    val willBeFav = !PlaybackStateStore.isFavoriteTrack(getApplication(), effectiveId, track.title)
    val trackToSave = if (track.id.isBlank()) track.copy(id = effectiveId) else track
    if (willBeFav) {
        PlaybackStateStore.saveFavoriteTrack(getApplication(), trackToSave)
        val currList = _favoriteTracks.value.toMutableList()
        if (currList.none { it.id == trackToSave.id || (it.title == trackToSave.title && it.artist == trackToSave.artist) }) {
            currList.add(0, trackToSave)
            _favoriteTracks.value = currList
        }
    } else {
        PlaybackStateStore.removeFavoriteTrack(getApplication(), effectiveId, track.title)
        _favoriteTracks.value = _favoriteTracks.value.filterNot {
            it.id == effectiveId || it.streamUrl == effectiveId ||
            (it.title.equals(track.title, ignoreCase = true) && it.artist.equals(track.artist, ignoreCase = true))
        }
    }
    if (track.id == _currentTrack.value.id || track.streamUrl == _currentTrack.value.streamUrl || track.title == _currentTrack.value.title) {
        _isFavorite.value = willBeFav
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            client.toggleFavorite(trackToSave)
            client.toggleTrackLike(trackToSave.id, willBeFav)
            loadSyncedYouTubeTracks()
            refreshFavoritesList()
        } catch (e: Exception) {
            Log.d(TAG, "Track like toggle error: ${e.message}")
        }
    }
}

fun MainViewModel.refreshFavoritesList() {
    viewModelScope.launch(Dispatchers.IO) {
        val localStoredFavs = PlaybackStateStore.getFavoriteTracks(getApplication())
        val favIds = PlaybackStateStore.getFavoriteTrackIds(getApplication())
        var backendFavs = try { client.getFavorites() } catch (_: Exception) { emptyList() }
        if (backendFavs.isEmpty() && (favIds.isNotEmpty() || localStoredFavs.isNotEmpty())) {
            val localFavorites = _libraryTracks.value.filter { favIds.contains(it.id) || favIds.contains(it.title) }
            val syncedFavorites = _syncedYouTubeTracks.value.filter { favIds.contains(it.id) }
            val allTracks = (localStoredFavs + localFavorites + syncedFavorites).distinctBy { if (it.id.isNotBlank()) it.id else it.title }
            for (t in allTracks) {
                try { client.toggleFavorite(t) } catch (_: Exception) {}
            }
            backendFavs = try { client.getFavorites() } catch (_: Exception) { emptyList() }
        }
        val localFavorites = _libraryTracks.value.filter { favIds.contains(it.id) || favIds.contains(it.title) }
        val syncedFavorites = _syncedYouTubeTracks.value.filter { favIds.contains(it.id) }
        val allTracks = (localStoredFavs + backendFavs + localFavorites + syncedFavorites).distinctBy { 
            if (it.id.isNotBlank()) it.id else "${it.title}_${it.artist}"
        }
        withContext(Dispatchers.Main) {
            _favoriteTracks.value = allTracks
        }
    }
}

fun MainViewModel.seekTo(progress: Float) {
    serviceConnection.seekToFraction(progress)
}

internal fun MainViewModel.getEffectiveQueue(): List<TrackItem> {
    val q = _currentQueue.value
    if (q.isNotEmpty()) return q
    val sQueue = serviceConnection.playbackState.value.queue
    if (sQueue.isNotEmpty()) return sQueue
    if (_searchResults.value.size > 1) return _searchResults.value
    if (_libraryTracks.value.size > 1) return _libraryTracks.value
    if (_chartTracks.value.size > 1) return _chartTracks.value
    return defaultTopTracks
}

fun MainViewModel.nextTrack() {
    val q = getEffectiveQueue()
    if (q.isNotEmpty()) {
        val current = _currentTrack.value
        val currentIndex = q.indexOfFirst {
            (it.id.isNotBlank() && it.id == current.id) ||
            (it.title.isNotBlank() && it.title.equals(current.title, ignoreCase = true))
        }

        // Proactive infinite auto-play: When within 3 tracks of the end of the queue, fetch next batch
        if (currentIndex >= q.size - 3 && q.isNotEmpty()) {
            if (!_isYouTubeConnected.value) {
                fetchGuestAlgorithmicRadio(q.last(), append = true)
            } else {
                fetchAutomixQueue(q.last(), append = true)
            }
        }

        val nextIndex = if (currentIndex in 0 until q.size - 1) {
            currentIndex + 1
        } else {
            0 // Loop back to start of queue
        }
        playTrack(q[nextIndex])
        return
    }
    serviceConnection.next()
}

fun MainViewModel.prevTrack() {
    val pos = serviceConnection.playbackState.value.currentPositionMs
    if (pos > 3000L) {
        // If played > 3s, seek to beginning (Spotify standard)
        serviceConnection.seekTo(0)
        return
    }
    val q = getEffectiveQueue()
    if (q.isNotEmpty()) {
        val current = _currentTrack.value
        val currentIndex = q.indexOfFirst {
            (it.id.isNotBlank() && it.id == current.id) ||
            (it.title.isNotBlank() && it.title.equals(current.title, ignoreCase = true))
        }
        val prevIndex = if (currentIndex > 0) {
            currentIndex - 1
        } else {
            q.lastIndex // Wrap to end of queue
        }
        playTrack(q[prevIndex])
        return
    }
    serviceConnection.previous()
}

/**
 * Ingests physical playback behavior telemetry (completions, skips, loops) into on-device taste engine.
 */
fun MainViewModel.logPlaybackTelemetry(track: TrackItem, listenedMs: Long, durationMs: Long, isCompleted: Boolean) {
    viewModelScope.launch(Dispatchers.IO) {
        val eventType = when {
            isCompleted || (durationMs > 0 && listenedMs.toDouble() / durationMs >= 0.85) -> "COMPLETE"
            listenedMs < 15000 -> "FAST_SKIP"
            listenedMs < 45000 -> "MILD_SKIP"
            else -> "COMPLETE"
        }
        client.recordTasteEvent(
            trackId = track.title,
            title = track.title,
            artistId = track.artist,
            artistName = track.artist,
            durationMs = durationMs,
            listenedMs = listenedMs,
            eventType = eventType
        )
    }
}

fun MainViewModel.cyclePlaybackMode() {
    serviceConnection.cyclePlaybackMode()
}

fun MainViewModel.toggleShuffle() {
    val current = serviceConnection.playbackState.value.playbackMode
    val next = if (current == PlaybackMode.SHUFFLE) {
        PlaybackMode.NORMAL
    } else {
        PlaybackMode.SHUFFLE
    }
    serviceConnection.setPlaybackMode(next)
}

fun MainViewModel.cycleRepeatMode() {
    val current = serviceConnection.playbackState.value.playbackMode
    val next = when (current) {
        PlaybackMode.NORMAL -> PlaybackMode.LOOP_ALL
        PlaybackMode.LOOP_ALL -> PlaybackMode.LOOP_ONE
        PlaybackMode.LOOP_ONE -> PlaybackMode.NORMAL
        else -> PlaybackMode.LOOP_ALL
    }
    serviceConnection.setPlaybackMode(next)
}

fun MainViewModel.seekToPositionMs(positionMs: Long) {
    serviceConnection.seekTo(positionMs)
}

fun MainViewModel.playQueueTrack(index: Int) {
    val q = getEffectiveQueue()
    if (index in q.indices) {
        playTrack(q[index])
    }
}

fun MainViewModel.moveQueueItem(fromIndex: Int, toIndex: Int) {
    val currentQueueList = _currentQueue.value.toMutableList()
    if (fromIndex in currentQueueList.indices && toIndex in currentQueueList.indices) {
        val item = currentQueueList.removeAt(fromIndex)
        currentQueueList.add(toIndex, item)
        _currentQueue.value = currentQueueList
        serviceConnection.setQueue(currentQueueList)
    }
    serviceConnection.moveQueueItem(fromIndex, toIndex)
}

fun MainViewModel.removeQueueItem(index: Int) {
    val currentQueueList = _currentQueue.value.toMutableList()
    if (index in currentQueueList.indices) {
        currentQueueList.removeAt(index)
        _currentQueue.value = currentQueueList
        serviceConnection.setQueue(currentQueueList)
    }
    serviceConnection.removeQueueItem(index)
}

fun MainViewModel.playNext(track: TrackItem) {
    serviceConnection.insertNext(track)
}

fun MainViewModel.addToQueue(track: TrackItem) {
    serviceConnection.addToQueue(track)
}
