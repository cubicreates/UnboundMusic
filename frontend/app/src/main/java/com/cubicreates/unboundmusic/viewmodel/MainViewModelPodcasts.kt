/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelPodcasts.kt
 * Purpose: Dedicated Podcast discovery, topic search, show browsing, and resume tracking
 *          strictly isolated from Music feeds and Music playback history.
 * Subsystem: ViewModel / Podcasts Domain
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.search.PodcastSearchFilter
import com.cubicreates.unboundmusic.util.GeoLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "MainViewModelPodcasts"

fun MainViewModel.setHomeSection(section: HomeSection) {
    if (_selectedHomeSection.value == section) return
    _selectedHomeSection.value = section
    if (section == HomeSection.PODCASTS && _podcastTopEpisodes.value.isEmpty() && !_isPodcastLoading.value) {
        loadPodcastDiscovery(_selectedPodcastCategory.value)
    }
}

fun MainViewModel.selectPodcastCategory(category: String) {
    if (_selectedPodcastCategory.value == category && _podcastTopEpisodes.value.isNotEmpty()) return
    _selectedPodcastCategory.value = category
    _activePodcastTopicQuery.value = null
    _podcastSearchResults.value = emptyList()
    _selectedPodcastShow.value = null
    loadPodcastDiscovery(category)
}

fun MainViewModel.loadPodcastDiscovery(category: String = _selectedPodcastCategory.value) {
    val country = GeoLocationProvider.getCountryCode(getApplication())
    viewModelScope.launch(Dispatchers.IO) {
        _isPodcastLoading.value = true
        _podcastError.value = null
        try {
            val feed = client.discoverPodcasts(category = category, country = country)
            withContext(Dispatchers.Main) {
                if (feed != null) {
                    _podcastFeaturedShows.value = feed.featuredShows
                    _podcastRecentEpisodes.value = feed.recentEpisodes
                    _podcastTopEpisodes.value = feed.topEpisodes
                    if (feed.globalEpisodes.isNotEmpty()) {
                        _podcastGlobalEpisodes.value = feed.globalEpisodes
                    }
                    if (feed.regionalEpisodes.isNotEmpty()) {
                        _podcastRegionalEpisodes.value = feed.regionalEpisodes
                    }
                } else {
                    _podcastError.value = "Unable to load podcast feeds right now."
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "loadPodcastDiscovery failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _podcastError.value = e.message ?: "Podcast feed unavailable"
            }
        } finally {
            _isPodcastLoading.value = false
        }
    }
}

fun MainViewModel.submitPodcastTopicQuery(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    _activePodcastTopicQuery.value = trimmed
    _selectedPodcastShow.value = null

    viewModelScope.launch(Dispatchers.IO) {
        _isPodcastLoading.value = true
        _podcastError.value = null
        try {
            val (episodes, shows) = client.searchPodcastsOnly(trimmed)
            withContext(Dispatchers.Main) {
                _podcastSearchResults.value = episodes
                if (shows.isNotEmpty()) {
                    _podcastFeaturedShows.value = shows
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "submitPodcastTopicQuery failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                _podcastError.value = e.message ?: "Podcast search failed"
            }
        } finally {
            _isPodcastLoading.value = false
        }
    }
}

fun MainViewModel.clearPodcastTopicQuery() {
    _activePodcastTopicQuery.value = null
    _podcastSearchResults.value = emptyList()
}

fun MainViewModel.openPodcastShow(show: PodcastShowDto) {
    _selectedPodcastShow.value = show
    if (show.episodes.size > 1) return

    viewModelScope.launch(Dispatchers.IO) {
        _isPodcastLoading.value = true
        try {
            val fullShow = client.browsePodcastShow(show.id, show.title)
            if (fullShow != null && fullShow.episodes.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _selectedPodcastShow.value = fullShow
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "openPodcastShow note: ${e.message}")
        } finally {
            _isPodcastLoading.value = false
        }
    }
}

fun MainViewModel.closePodcastShow() {
    _selectedPodcastShow.value = null
}

fun MainViewModel.playPodcastEpisode(episode: PodcastEpisodeDto) {
    val track = episode.toPlayableTrackItem()
    playTrack(track, preserveQueue = false)

    if (episode.resumePosMs > 5000L) {
        viewModelScope.launch {
            delay(1200)
            if (_currentTrack.value.id == episode.id) {
                serviceConnection.seekTo(episode.resumePosMs)
            }
        }
    }
}

internal fun MainViewModel.recordPodcastEpisodePlay(track: TrackItem) {
    if (track.id.isBlank()) return
    val currentRecent = _podcastRecentEpisodes.value.toMutableList()
    val existing = currentRecent.firstOrNull { it.id == track.id }
    val updated = existing ?: PodcastEpisodeDto(
        id = track.id,
        showId = track.album.ifBlank { track.artist },
        showTitle = track.album.ifBlank { track.artist },
        author = track.artist,
        title = track.title,
        durationMs = track.durationMs,
        thumbnailUrl = track.coverUrl
    )
    currentRecent.removeAll { it.id == track.id }
    currentRecent.add(0, updated)
    _podcastRecentEpisodes.value = currentRecent.take(10)

    val currentInProgress = _inProgressPodcastEpisodes.value.toMutableList()
    currentInProgress.removeAll { it.id == track.id }
    currentInProgress.add(0, updated)
    _inProgressPodcastEpisodes.value = currentInProgress.take(15)
    saveLocalPodcastLibraryCache()

    viewModelScope.launch(Dispatchers.IO) {
        val pos = playbackState.value.currentPositionMs
        client.savePodcastResume(track.id, pos.coerceAtLeast(1L), updated)
    }
}

// ==================== Split Podcast Search & History ====================

fun MainViewModel.setSearchSection(section: HomeSection) {
    if (_selectedSearchSection.value == section) return
    _selectedSearchSection.value = section
    if (section == HomeSection.PODCASTS && _podcastTopEpisodes.value.isEmpty() && !_isPodcastLoading.value) {
        loadPodcastDiscovery(_selectedPodcastCategory.value)
    }
}

fun MainViewModel.setPodcastSearchFilter(filter: PodcastSearchFilter) {
    if (_podcastSearchFilter.value == filter) return
    _podcastSearchFilter.value = filter
    val q = _podcastSearchQuery.value.trim()
    if (q.isNotBlank()) {
        podcastSearchJob?.cancel()
        podcastSearchJob = viewModelScope.launch(Dispatchers.IO) {
            executePodcastSearch(q)
        }
    }
}

fun MainViewModel.onPodcastSearchQueryChanged(query: String) {
    _podcastSearchQuery.value = query
    podcastSearchJob?.cancel()
    if (query.isBlank()) {
        _podcastSearchResults.value = emptyList()
        _podcastSearchShows.value = emptyList()
        _isPodcastSearching.value = false
        return
    }
    podcastSearchJob = viewModelScope.launch(Dispatchers.IO) {
        delay(350)
        executePodcastSearch(query.trim())
    }
}

fun MainViewModel.submitPodcastSearch(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    _podcastSearchQuery.value = trimmed
    _selectedPodcastShow.value = null
    addPodcastSearchHistory(trimmed)
    podcastSearchJob?.cancel()
    podcastSearchJob = viewModelScope.launch(Dispatchers.IO) {
        executePodcastSearch(trimmed)
    }
}

fun MainViewModel.clearPodcastSearch() {
    podcastSearchJob?.cancel()
    _podcastSearchQuery.value = ""
    _podcastSearchResults.value = emptyList()
    _podcastSearchShows.value = emptyList()
    _isPodcastSearching.value = false
}

internal suspend fun MainViewModel.executePodcastSearch(query: String) {
    _isPodcastSearching.value = true
    _podcastError.value = null
    try {
        val (episodes, shows) = client.searchPodcastsOnly(
            query = query,
            filter = _podcastSearchFilter.value.apiParam
        )
        withContext(Dispatchers.Main) {
            _podcastSearchResults.value = episodes
            _podcastSearchShows.value = shows
        }
    } catch (e: Exception) {
        Log.e(TAG, "executePodcastSearch failed: ${e.message}", e)
        withContext(Dispatchers.Main) {
            _podcastError.value = e.message ?: "Podcast search failed"
        }
    } finally {
        _isPodcastSearching.value = false
    }
}

internal fun MainViewModel.loadPodcastSearchHistory() {
    try {
        val prefs = getApplication<Application>().getSharedPreferences("unbound_podcast_search_history", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("queries", null)
        if (!raw.isNullOrBlank()) {
            _podcastSearchHistory.value = raw.split("||||").filter { it.isNotBlank() }
        }
    } catch (e: Exception) {
        Log.e(TAG, "Failed loading podcast search history: ${e.message}")
    }
}

fun MainViewModel.addPodcastSearchHistory(query: String) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) return
    val current = _podcastSearchHistory.value.toMutableList()
    current.remove(trimmed)
    current.add(0, trimmed)
    val capped = current.take(20)
    _podcastSearchHistory.value = capped
    savePodcastSearchHistory(capped)
}

fun MainViewModel.removePodcastSearchHistoryItem(query: String) {
    val current = _podcastSearchHistory.value.toMutableList()
    current.remove(query)
    _podcastSearchHistory.value = current
    savePodcastSearchHistory(current)
}

fun MainViewModel.clearPodcastSearchHistory() {
    _podcastSearchHistory.value = emptyList()
    savePodcastSearchHistory(emptyList())
}

internal fun MainViewModel.savePodcastSearchHistory(list: List<String>) {
    try {
        val prefs = getApplication<Application>().getSharedPreferences("unbound_podcast_search_history", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("queries", list.joinToString("||||")).apply()
    } catch (e: Exception) {
        Log.e(TAG, "Failed saving podcast search history: ${e.message}")
    }
}

// ==================== Saved Podcast Library Helpers ====================

fun MainViewModel.refreshPodcastLibrary() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val lib = client.getPodcastLibrary() ?: return@launch
            withContext(Dispatchers.Main) {
                if (lib.followedShows.isNotEmpty() || _followedPodcastShows.value.isEmpty()) {
                    _followedPodcastShows.value = lib.followedShows
                }
                if (lib.savedEpisodes.isNotEmpty() || _savedPodcastEpisodes.value.isEmpty()) {
                    _savedPodcastEpisodes.value = lib.savedEpisodes
                }
                if (lib.inProgressEpisodes.isNotEmpty() || _inProgressPodcastEpisodes.value.isEmpty()) {
                    _inProgressPodcastEpisodes.value = lib.inProgressEpisodes
                }
                saveLocalPodcastLibraryCache()
            }
        } catch (e: Exception) {
            Log.w(TAG, "refreshPodcastLibrary note: ${e.message}")
        }
    }
}

fun MainViewModel.toggleFollowPodcastShow(show: PodcastShowDto) {
    val key = show.id.ifBlank { show.title }
    if (key.isBlank()) return
    val current = _followedPodcastShows.value.toMutableList()
    val exists = current.any { it.id == key || it.title.equals(show.title, ignoreCase = true) }
    if (exists) {
        current.removeAll { it.id == key || it.title.equals(show.title, ignoreCase = true) }
    } else {
        current.add(0, show.copy(id = key))
    }
    _followedPodcastShows.value = current
    saveLocalPodcastLibraryCache()

    viewModelScope.launch(Dispatchers.IO) {
        client.toggleFollowPodcastShow(show.copy(id = key))
    }
}

fun MainViewModel.toggleSavePodcastEpisode(episode: PodcastEpisodeDto) {
    if (episode.id.isBlank()) return
    val current = _savedPodcastEpisodes.value.toMutableList()
    val exists = current.any { it.id == episode.id }
    if (exists) {
        current.removeAll { it.id == episode.id }
    } else {
        current.add(0, episode)
    }
    _savedPodcastEpisodes.value = current
    saveLocalPodcastLibraryCache()

    viewModelScope.launch(Dispatchers.IO) {
        client.toggleSavePodcastEpisode(episode)
    }
}

internal fun MainViewModel.loadLocalPodcastLibraryCache() {
    try {
        val prefs = getApplication<Application>().getSharedPreferences("unbound_podcast_library", android.content.Context.MODE_PRIVATE)
        val raw = prefs.getString("state_json", null) ?: return
        val parsed = client.parsePodcastLibrary(raw) ?: return
        _followedPodcastShows.value = parsed.followedShows
        _savedPodcastEpisodes.value = parsed.savedEpisodes
        _inProgressPodcastEpisodes.value = parsed.inProgressEpisodes
    } catch (e: Exception) {
        Log.w(TAG, "loadLocalPodcastLibraryCache note: ${e.message}")
    }
}

internal fun MainViewModel.saveLocalPodcastLibraryCache() {
    try {
        val dto = PodcastLibraryDto(
            followedShows = _followedPodcastShows.value,
            savedEpisodes = _savedPodcastEpisodes.value,
            inProgressEpisodes = _inProgressPodcastEpisodes.value
        )
        val json = BackendClient.defaultJson.encodeToString(PodcastLibraryDto.serializer(), dto)
        val prefs = getApplication<Application>().getSharedPreferences("unbound_podcast_library", android.content.Context.MODE_PRIVATE)
        prefs.edit().putString("state_json", json).apply()
    } catch (e: Exception) {
        Log.w(TAG, "saveLocalPodcastLibraryCache note: ${e.message}")
    }
}
