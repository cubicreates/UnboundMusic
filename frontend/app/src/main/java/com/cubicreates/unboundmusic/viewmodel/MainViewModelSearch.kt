/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelSearch.kt
 * Purpose: YouTube Music search, live autocomplete, history persistence, batch actions, and semantic Vibe search.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.data.MoodCapsule
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.search.SearchCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "MainViewModelSearch"

fun MainViewModel.setSearchCategory(category: SearchCategory) {
    if (_searchCategory.value != category) {
        _searchCategory.value = category
        val currentQ = _searchQuery.value
        if (currentQ.isNotBlank()) {
            onSearchQueryChanged(currentQ)
        }
    }
}

/**
 * Updates search query, fetches live autocomplete suggestions, and debounces catalog search.
 */
fun MainViewModel.onSearchQueryChanged(query: String) {
    _searchQuery.value = query
    fetchSearchSuggestions(query)
    searchJob?.cancel()
    if (query.isBlank()) {
        _searchResults.value = emptyList()
        _isSearching.value = false
        return
    }

    searchJob = viewModelScope.launch(Dispatchers.IO) {
        delay(350) // 350ms debounce for full search
        executeFullSearch(query)
    }
}

/**
 * Explicitly submits a search query (e.g. user taps enter, history, or a suggestion),
 * records it into search history, and immediately executes catalog search.
 */
fun MainViewModel.submitSearch(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    _searchQuery.value = trimmed
    addSearchHistory(trimmed)
    searchJob?.cancel()
    searchJob = viewModelScope.launch(Dispatchers.IO) {
        executeFullSearch(trimmed)
    }
}

internal suspend fun MainViewModel.executeFullSearch(query: String) {
    _isSearching.value = true
    try {
        var foundTracks = false
        val (code, resp) = client.search(query, type = _searchCategory.value.apiParam)
        if (code in 200..299 && resp.isNotBlank()) {
            val parsed = client.parseSearchResults(resp)
            if (parsed.isNotEmpty()) {
                _searchResults.value = parsed
                foundTracks = true
            }
        }

        // If daemon returned empty, query YouTube Music public search fallback
        if (!foundTracks) {
            val fallbackResults = executeDirectYouTubeSearch(query)
            if (fallbackResults.isNotEmpty()) {
                _searchResults.value = fallbackResults
            }
        }
    } catch (e: Exception) {
        Log.e(TAG, "YouTube search error: ${e.message}")
        val fallbackResults = executeDirectYouTubeSearch(query)
        if (fallbackResults.isNotEmpty()) {
            _searchResults.value = fallbackResults
        }
    } finally {
        _isSearching.value = false
    }
}

/**
 * Real-time autocomplete suggestions from YouTube Music suggest client.
 */
fun MainViewModel.fetchSearchSuggestions(query: String) {
    suggestionJob?.cancel()
    if (query.isBlank()) {
        _searchSuggestions.value = emptyList()
        return
    }
    suggestionJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            val encoded = java.net.URLEncoder.encode(query, "UTF-8")
            val url = java.net.URL("https://suggestqueries-clients6.youtube.com/complete/search?client=youtube-music&hl=en&gl=US&q=$encoded")
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            if (conn.responseCode in 200..299) {
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                val regex = Regex("""\["([^"]+)",0,""")
                val list = regex.findAll(text).map { it.groupValues[1] }.distinct().take(12).toList()
                _searchSuggestions.value = list
            }
        } catch (_: Exception) {
            // Ignore transient network failures for suggestions
        }
    }
}

internal fun MainViewModel.executeDirectYouTubeSearch(query: String): List<TrackItem> {
    return try {
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val url = java.net.URL("https://suggestqueries-clients6.youtube.com/complete/search?client=youtube-music&hl=en&gl=US&q=$encoded")
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.connectTimeout = 4000
        conn.readTimeout = 4000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        if (conn.responseCode in 200..299) {
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val parsed = mutableListOf<TrackItem>()
            val regex = Regex("""\["([^"]+)",0,""")
            regex.findAll(text).take(8).forEach { match ->
                val musicTitle = match.groupValues[1]
                parsed.add(
                    TrackItem(
                        id = "",
                        title = musicTitle,
                        artist = "YouTube Music",
                        coverUrl = "",
                        streamUrl = ""
                    )
                )
            }
            parsed
        } else {
            emptyList()
        }
    } catch (e: Exception) {
        emptyList()
    }
}

internal fun MainViewModel.loadSearchHistory() {
    try {
        val prefs = getApplication<Application>().getSharedPreferences("unbound_search_history", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("queries", null)
        if (!raw.isNullOrBlank()) {
            val list = raw.split("||||").filter { it.isNotBlank() }
            _searchHistory.value = list
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed loading search history: ${e.message}")
    }
}

fun MainViewModel.addSearchHistory(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    val current = _searchHistory.value.toMutableList()
    current.remove(trimmed)
    current.add(0, trimmed)
    val capped = current.take(30)
    _searchHistory.value = capped
    saveSearchHistory(capped)
}

fun MainViewModel.removeSearchHistoryItem(query: String) {
    val current = _searchHistory.value.toMutableList()
    current.remove(query)
    _searchHistory.value = current
    saveSearchHistory(current)
}

fun MainViewModel.clearSearchHistory() {
    _searchHistory.value = emptyList()
    saveSearchHistory(emptyList())
}

internal fun MainViewModel.saveSearchHistory(list: List<String>) {
    try {
        val prefs = getApplication<Application>().getSharedPreferences("unbound_search_history", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("queries", list.joinToString("||||")).apply()
    } catch (e: Exception) {
        Log.e(TAG, "Failed saving search history: ${e.message}")
    }
}

fun MainViewModel.playNextBatch(tracks: List<TrackItem>) {
    if (tracks.isEmpty()) return
    val currentQueueList = _currentQueue.value.toMutableList()
    val current = _currentTrack.value
    val currentIndex = currentQueueList.indexOfFirst { it.id == current.id }
    val insertIndex = if (currentIndex >= 0) currentIndex + 1 else 0
    currentQueueList.addAll(insertIndex, tracks)
    _currentQueue.value = currentQueueList
    serviceConnection.setQueue(currentQueueList)
}

fun MainViewModel.addToQueueBatch(tracks: List<TrackItem>) {
    if (tracks.isEmpty()) return
    val currentQueueList = _currentQueue.value.toMutableList()
    currentQueueList.addAll(tracks)
    _currentQueue.value = currentQueueList
    serviceConnection.setQueue(currentQueueList)
}

fun MainViewModel.downloadBatch(tracks: List<TrackItem>) {
    tracks.forEach { track ->
        if (track.id.isNotBlank()) {
            startTrackDownload(track)
        }
    }
}

/**
 * AI-powered semantic vibe search via the Go daemon.
 */
fun MainViewModel.executeVibeSearch(prompt: String) {
    _isSearching.value = true
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.queryVibe(prompt, topK = 10)
            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                val keywords = json.optJSONArray("search_keywords")
                val genres = json.optJSONArray("target_genres")
                val searchTerm = buildString {
                    keywords?.let { arr ->
                        for (i in 0 until minOf(arr.length(), 3)) {
                            if (isNotBlank()) append(" ")
                            append(arr.optString(i))
                        }
                    }
                    if (isBlank() && genres != null) {
                        for (i in 0 until minOf(genres.length(), 2)) {
                            if (isNotBlank()) append(" ")
                            append(genres.optString(i))
                        }
                    }
                    if (isBlank()) append(prompt)
                }
                val (sCode, sResp) = client.search(searchTerm)
                if (sCode in 200..299) {
                    val parsed = client.parseSearchResults(sResp)
                    if (parsed.isNotEmpty()) {
                        _searchResults.value = parsed
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Vibe search error: ${e.message}")
        } finally {
            _isSearching.value = false
        }
    }
}

/**
 * Natural Language Home Vibe AI query runner.
 */
fun MainViewModel.submitHomeVibeQuery(query: String, autoPlay: Boolean = false) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    val country = com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
    val lang = com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
    viewModelScope.launch(Dispatchers.IO) {
        _homeVibeState.value = VibeSearchUiState.Loading
        try {
            val res = client.searchVibe(trimmed, region = country, language = lang)
            val songsOnly = res.radioTracks.filter { track ->
                val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_")
                val isArtist = track.itemType.equals("artist", ignoreCase = true) || track.browseId.startsWith("UC")
                val isPlaylist = track.itemType.equals("playlist", ignoreCase = true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL")
                !isAlbum && !isArtist && !isPlaylist && !track.id.startsWith("UC") && !track.id.startsWith("MPREb_")
            }
            withContext(Dispatchers.Main) {
                _homeVibeState.value = VibeSearchUiState.Success(res.vibeResult, songsOnly)
                if (songsOnly.isNotEmpty() && autoPlay) {
                    val first = songsOnly.first()
                    playTrackWithQueue(first, songsOnly)
                    val moodTag = res.vibeResult.moodTags.firstOrNull() ?: res.vibeResult.targetGenres.firstOrNull() ?: "Vibe"
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        getApplication(),
                        "Tuning into $moodTag: ${first.title}",
                        isLong = true
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "submitHomeVibeQuery error: ${e.message}", e)
            withContext(Dispatchers.Main) {
                com.cubicreates.unboundmusic.util.UnboundToast.show(
                    getApplication(),
                    "Vibe Search: ${e.message ?: "Failed"}",
                    isLong = true
                )
                _homeVibeState.value = VibeSearchUiState.Error(e.message ?: "Search failed")
            }
        }
    }
}

fun MainViewModel.clearHomeVibeQuery() {
    _homeVibeState.value = VibeSearchUiState.Idle
}

/**
 * Natural Language Search / Discover Screen Vibe AI query runner.
 */
fun MainViewModel.submitSearchVibeQuery(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    val country = com.cubicreates.unboundmusic.util.GeoLocationProvider.getCountryCode(getApplication())
    val lang = com.cubicreates.unboundmusic.util.GeoLocationProvider.getLanguageCode()
    viewModelScope.launch(Dispatchers.IO) {
        _searchVibeState.value = VibeSearchUiState.Loading
        try {
            val res = client.searchVibe(trimmed, region = country, language = lang)
            val songsOnly = res.radioTracks.filter { track ->
                val isAlbum = track.itemType.equals("album", ignoreCase = true) || track.browseId.startsWith("MPREb_")
                val isArtist = track.itemType.equals("artist", ignoreCase = true) || track.browseId.startsWith("UC")
                val isPlaylist = track.itemType.equals("playlist", ignoreCase = true) || track.browseId.startsWith("VL") || track.browseId.startsWith("PL")
                !isAlbum && !isArtist && !isPlaylist && !track.id.startsWith("UC") && !track.id.startsWith("MPREb_")
            }
            withContext(Dispatchers.Main) {
                _searchVibeState.value = VibeSearchUiState.Success(res.vibeResult, songsOnly)
                if (songsOnly.isNotEmpty()) {
                    _searchResults.value = songsOnly
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "submitSearchVibeQuery error: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _searchVibeState.value = VibeSearchUiState.Error(e.message ?: "Search failed")
            }
        }
    }
}

fun MainViewModel.clearSearchVibeQuery() {
    _searchVibeState.value = VibeSearchUiState.Idle
}

fun MainViewModel.submitVibeQuery(query: String, autoPlay: Boolean = false) {
    submitHomeVibeQuery(query, autoPlay = autoPlay)
}

fun MainViewModel.playVibePrompt(prompt: String) {
    submitHomeVibeQuery(prompt, autoPlay = false)
}

fun MainViewModel.clearVibeQuery() {
    clearHomeVibeQuery()
}

