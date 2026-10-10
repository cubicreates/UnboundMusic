/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelLyrics.kt
 * Purpose: Lyrics synchronization, phonetic Romanization, Spotify Canvas visuals, and RYD voting.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.data.RomanizationMode
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

private const val TAG = "MainViewModelLyrics"

fun MainViewModel.setRomanizationMode(mode: RomanizationMode) {
    _romanizationMode.value = mode
}

fun MainViewModel.setLyricsTimingOffsetMs(offsetMs: Long) {
    _lyricsTimingOffsetMs.value = offsetMs
}

/**
 * Loads live lyrics for a specific track, ensuring in-flight requests from older tracks
 * are cleanly cancelled, the UI immediately shows the shimmer placeholder, and stale
 * lyrics never linger.
 */
fun MainViewModel.loadLyricsForTrack(track: TrackItem) {
    lyricsFetchJob?.cancel()
    _lyricsLines.value = emptyList()
    _lyricsSource.value = ""
    _isInstrumental.value = false

    if (track.title.isBlank() || track.title == "Unknown") return

    lyricsFetchJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            val duration = if (track.durationMs > 0) track.durationMs else playbackState.value.durationMs
            val cleanArtist = if (track.artist.equals("Song", ignoreCase = true) ||
                                  track.artist.equals("Video", ignoreCase = true) ||
                                  track.artist.equals("YouTube Artist", ignoreCase = true)) "" else track.artist
            val (code, resp) = client.getLyrics(
                trackId = track.id,
                title = track.title,
                artist = cleanArtist,
                durationMs = duration
            )

            // Verify user hasn't switched to another track during network flight
            val current = _currentTrack.value
            val isStillCurrent = (current.id.isNotBlank() && current.id == track.id) ||
                    (current.title.isNotBlank() && current.title.equals(track.title, ignoreCase = true))
            if (!isStillCurrent) {
                Log.d(TAG, "Discarding lyrics: track moved from '${track.title}' to '${current.title}'")
                return@launch
            }

            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                val source = json.optString("source", "LRCLIB Synced Lyrics")
                val isInst = json.optBoolean("instrumental", false)
                _isInstrumental.value = isInst

                val linesArray = json.optJSONArray("lines")
                if (linesArray != null && linesArray.length() > 0) {
                    val lines = mutableListOf<LyricLine>()
                    for (i in 0 until linesArray.length()) {
                        val lineObj = linesArray.getJSONObject(i)
                        lines.add(
                            LyricLine(
                                text = lineObj.optString("text", ""),
                                startMs = lineObj.optLong("start_ms", 0),
                                endMs = lineObj.optLong("end_ms", 0),
                                romanized = lineObj.optString("romanized", "")
                            )
                        )
                    }
                    _lyricsLines.value = lines
                    _lyricsSource.value = source
                } else {
                    val plain = json.optString("plain_lyrics", "")
                    if (plain.isNotBlank() && !isInst) {
                        val plainLines = plain.lines().filter { it.isNotBlank() }.map { text ->
                            LyricLine(text = text, startMs = 0, endMs = 0, romanized = "")
                        }
                        _lyricsLines.value = plainLines
                        _lyricsSource.value = source
                    } else {
                        _lyricsLines.value = emptyList()
                        _lyricsSource.value = if (isInst) source else ""
                    }
                }
            } else {
                _lyricsLines.value = emptyList()
                _lyricsSource.value = ""
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lyrics fetch error for '${track.title}': ${e.message}")
            _lyricsLines.value = emptyList()
            _lyricsSource.value = ""
        }
    }
}

/**
 * Fetches Spotify Canvas visual assets (video, hi-res art) from the Go daemon.
 */
internal suspend fun MainViewModel.fetchCanvas(track: TrackItem) {
    try {
        val (code, resp) = client.getCanvas(track.title, track.artist)
        if (code in 200..299 && resp.isNotBlank()) {
            val json = JSONObject(resp)
            val found = json.optBoolean("found", false)
            if (found) {
                val canvasUrl = json.optString("canvas_url").takeIf { it.isNotBlank() && it.startsWith("http") }
                val artUrl = (json.optString("thumbnail_url").takeIf { it.isNotBlank() && it.startsWith("http") && !it.contains("/vi//maxresdefault") }
                    ?: json.optString("song_art_url").takeIf { it.isNotBlank() && it.startsWith("http") })
                _canvasVideoUrl.value = canvasUrl
                if (track.coverUrl.isBlank() && artUrl != null) {
                    _canvasArtUrl.value = artUrl
                }
            }
        }
    } catch (e: Exception) {
        Log.w(TAG, "Canvas fetch error: ${e.message}")
    }
}

/**
 * Fetches SponsorBlock music_offtopic skip segments for the current track.
 */
internal suspend fun MainViewModel.fetchSkipSegments(track: TrackItem) {
    if (!_sponsorBlockEnabled.value) {
        _activeSkipSegments.value = emptyList()
        lastSkippedSegmentId = null
        return
    }
    try {
        _activeSkipSegments.value = emptyList()
        lastSkippedSegmentId = null
        val videoId = track.id.ifBlank {
            val stream = track.streamUrl
            if (!stream.startsWith("http") && stream.isNotBlank() && !stream.contains(" ")) {
                stream
            } else ""
        }
        if (videoId.isBlank()) return
        val (code, resp) = client.getSkipSegments(videoId)
        if (code in 200..299 && resp.isNotBlank()) {
            val segments = client.parseSkipSegments(resp)
            _activeSkipSegments.value = segments.sortedBy { it.startMs }
            Log.d(TAG, "Loaded ${segments.size} skip segments for track: ${track.title}")
        }
    } catch (e: Exception) {
        Log.w(TAG, "Skip segments fetch note: ${e.message}")
    }
}

/**
 * Fetches Return YouTube Dislike (RYD) community statistics and approval ratio for the current track.
 */
suspend fun MainViewModel.fetchRydVotes(track: TrackItem) {
    try {
        val videoId = track.id.ifBlank {
            val stream = track.streamUrl
            if (!stream.startsWith("http") && stream.isNotBlank() && !stream.contains(" ")) {
                stream
            } else ""
        }
        if (videoId.isBlank() || videoId.startsWith("local") || videoId.startsWith("file")) {
            _rydVotes.value = null
            return
        }

        val votes = client.getRydVotes(videoId)
        val current = _currentTrack.value
        val isStillCurrent = (current.id.isNotBlank() && current.id == track.id) ||
                (current.title.isNotBlank() && current.title.equals(track.title, ignoreCase = true))
        if (isStillCurrent) {
            _rydVotes.value = votes
            Log.d(TAG, "Loaded RYD votes for '${track.title}': ${votes?.likes} likes, ${votes?.dislikes} dislikes (${votes?.likePercentage}%)")
        }
    } catch (e: Exception) {
        Log.w(TAG, "RYD votes fetch note: ${e.message}")
    }
}

fun MainViewModel.refreshRydVotes() {
    val track = _currentTrack.value
    viewModelScope.launch(Dispatchers.IO) {
        fetchRydVotes(track)
    }
}

fun MainViewModel.undoSkitSkip() {
    val notice = _skippedSkitNotice.value ?: return
    seekToPositionMs(notice.fromMs)
    _skippedSkitNotice.value = null
    skitNoticeDismissJob?.cancel()
}

fun MainViewModel.dismissSkitNotice() {
    _skippedSkitNotice.value = null
    skitNoticeDismissJob?.cancel()
}
