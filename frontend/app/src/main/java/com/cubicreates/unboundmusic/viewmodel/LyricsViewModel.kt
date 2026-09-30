/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: LyricsViewModel.kt
 * Purpose: Dedicated Domain ViewModel for synchronized LRCLIB, Genius, and plain lyrics fetching,
 *          millisecond timestamp alignment, and lyrics offset synchronization.
 * Subsystem: Domain Layer / Lyrics
 * Concurrency: Thread-safe reactive StateFlow orchestration on viewModelScope.
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Domain ViewModel responsible strictly for lyrics loading, timestamp synchronization, and offsets.
 */
class LyricsViewModel(application: Application) : AndroidViewModel(application) {

    private val daemonManager = DaemonManager.getInstance(application)
    private val client = daemonManager.client

    private val _lyricsLines = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyricsLines: StateFlow<List<LyricLine>> = _lyricsLines.asStateFlow()

    private val _lyricsSource = MutableStateFlow("")
    val lyricsSource: StateFlow<String> = _lyricsSource.asStateFlow()

    private val _lyricsTimingOffsetMs = MutableStateFlow(0L)
    val lyricsTimingOffsetMs: StateFlow<Long> = _lyricsTimingOffsetMs.asStateFlow()

    private val _isInstrumental = MutableStateFlow(false)
    val isInstrumental: StateFlow<Boolean> = _isInstrumental.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics: StateFlow<Boolean> = _isLoadingLyrics.asStateFlow()

    private var lyricsFetchJob: Job? = null

    companion object {
        private const val TAG = "LyricsViewModel"
    }

    fun setLyricsTimingOffsetMs(offsetMs: Long) {
        _lyricsTimingOffsetMs.value = offsetMs
    }

    fun loadLyricsForTrack(track: TrackItem, durationMs: Long = 0L) {
        lyricsFetchJob?.cancel()
        _lyricsLines.value = emptyList()
        _lyricsSource.value = ""
        _isInstrumental.value = false

        if (track.title.isBlank() || track.title == "Unknown") return

        _isLoadingLyrics.value = true
        lyricsFetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val duration = if (track.durationMs > 0) track.durationMs else durationMs
                val cleanArtist = if (track.artist.equals("Song", ignoreCase = true) ||
                    track.artist.equals("Video", ignoreCase = true) ||
                    track.artist.equals("YouTube Artist", ignoreCase = true)) "" else track.artist

                val (code, resp) = client.getLyrics(
                    trackId = track.id,
                    title = track.title,
                    artist = cleanArtist,
                    durationMs = duration
                )

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
            } finally {
                _isLoadingLyrics.value = false
            }
        }
    }

    fun clearLyrics() {
        lyricsFetchJob?.cancel()
        _lyricsLines.value = emptyList()
        _lyricsSource.value = ""
        _isInstrumental.value = false
        _isLoadingLyrics.value = false
    }
}
