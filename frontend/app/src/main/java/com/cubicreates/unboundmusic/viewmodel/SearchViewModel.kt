/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: SearchViewModel.kt
 * Purpose: Dedicated Domain ViewModel for search query handling, autocomplete suggestions,
 *          category filtering, and AI semantic vibe search.
 * Subsystem: Domain Layer / Search & Discovery
 * Concurrency: Thread-safe reactive StateFlow orchestration on viewModelScope.
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.VibeSearchUiState
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.search.SearchCategory
import com.cubicreates.unboundmusic.util.GeoLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Domain ViewModel encapsulating search lifecycle, query suggestions, and vibe exploration.
 */
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val daemonManager = DaemonManager.getInstance(application)
    private val client = daemonManager.client

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TrackItem>>(emptyList())
    val searchResults: StateFlow<List<TrackItem>> = _searchResults.asStateFlow()

    private val _searchSuggestions = MutableStateFlow<List<String>>(emptyList())
    val searchSuggestions: StateFlow<List<String>> = _searchSuggestions.asStateFlow()

    private val _searchCategory = MutableStateFlow(SearchCategory.ALL)
    val searchCategory: StateFlow<SearchCategory> = _searchCategory.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    private val _searchVibeState = MutableStateFlow<VibeSearchUiState>(VibeSearchUiState.Idle)
    val searchVibeState: StateFlow<VibeSearchUiState> = _searchVibeState.asStateFlow()

    private val _homeVibeState = MutableStateFlow<VibeSearchUiState>(VibeSearchUiState.Idle)
    val homeVibeState: StateFlow<VibeSearchUiState> = _homeVibeState.asStateFlow()

    private val _searchHistory = MutableStateFlow<List<String>>(emptyList())
    val searchHistory: StateFlow<List<String>> = _searchHistory.asStateFlow()

    private var searchJob: Job? = null
    private var suggestionJob: Job? = null

    companion object {
        private const val TAG = "SearchViewModel"
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
        fetchSearchSuggestions(query)
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            _isSearching.value = false
            return
        }

        searchJob = viewModelScope.launch(Dispatchers.IO) {
            delay(350)
            executeFullSearch(query)
        }
    }

    fun submitSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        _searchQuery.value = trimmed
        addSearchHistory(trimmed)
        searchJob?.cancel()
        searchJob = viewModelScope.launch(Dispatchers.IO) {
            executeFullSearch(trimmed)
        }
    }

    fun setSearchCategory(category: SearchCategory) {
        if (_searchCategory.value != category) {
            _searchCategory.value = category
            val query = _searchQuery.value.trim()
            if (query.isNotBlank()) {
                submitSearch(query)
            }
        }
    }

    private suspend fun executeFullSearch(query: String) {
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

            if (!foundTracks) {
                val fallbackResults = executeDirectYouTubeSearch(query)
                if (fallbackResults.isNotEmpty()) {
                    _searchResults.value = fallbackResults
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search error: ${e.message}")
            val fallbackResults = executeDirectYouTubeSearch(query)
            if (fallbackResults.isNotEmpty()) {
                _searchResults.value = fallbackResults
            }
        } finally {
            _isSearching.value = false
        }
    }

    fun fetchSearchSuggestions(query: String) {
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
                // Ignore transient network errors for suggestions
            }
        }
    }

    private fun executeDirectYouTubeSearch(query: String): List<TrackItem> {
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
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun submitSearchVibeQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val country = GeoLocationProvider.getCountryCode(getApplication())
        val lang = GeoLocationProvider.getLanguageCode()
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

    fun submitHomeVibeQuery(query: String, onPlaySeed: ((TrackItem, List<TrackItem>) -> Unit)? = null) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        val country = GeoLocationProvider.getCountryCode(getApplication())
        val lang = GeoLocationProvider.getLanguageCode()
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
                    if (songsOnly.isNotEmpty() && onPlaySeed != null) {
                        onPlaySeed(songsOnly.first(), songsOnly)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "submitHomeVibeQuery error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    _homeVibeState.value = VibeSearchUiState.Error(e.message ?: "Search failed")
                }
            }
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _searchSuggestions.value = emptyList()
        _isSearching.value = false
    }

    fun clearHomeVibeQuery() {
        _homeVibeState.value = VibeSearchUiState.Idle
    }

    fun clearSearchVibeQuery() {
        _searchVibeState.value = VibeSearchUiState.Idle
    }

    private fun addSearchHistory(query: String) {
        val current = _searchHistory.value.toMutableList()
        current.remove(query)
        current.add(0, query)
        _searchHistory.value = current.take(20)
    }

    fun clearSearchHistory() {
        _searchHistory.value = emptyList()
    }
}
