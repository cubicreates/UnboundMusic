/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelAccount.kt
 * Purpose: YouTube account synchronization, OAuth device code flow, synced liked library, and analytics logging.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.data.SessionStore
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.util.UnboundToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "MainViewModelAccount"

/** Checks YouTube connection status from daemon and loads synced tracks if connected. */
fun MainViewModel.checkAccountStatus() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getAccountStatus()
            if (code in 200..299 && resp.isNotBlank()) {
                val status = client.parseAccountStatus(resp)
                if (status != null) {
                    _isYouTubeConnected.value = status.connected
                    _accountName.value = status.accountName
                    _userAvatarUrl.value = status.avatarUrl.takeIf { it.isNotBlank() }
                    if (status.connected) {
                        val store = SessionStore.getInstance(getApplication())
                        store.accountName = status.accountName
                        if (status.avatarUrl.isNotBlank()) {
                            store.avatarUrl = status.avatarUrl
                        }
                        loadSyncedYouTubeTracks()
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Account status check note: ${e.message}")
        }
    }
}

/** Synchronizes YouTube account credentials and pulls liked music into the local library. */
fun MainViewModel.syncYouTubeAccount(cookie: String) {
    viewModelScope.launch(Dispatchers.IO) {
        _isSyncingAccount.value = true
        try {
            val (code, resp) = client.syncAccount(cookie)
            if (code in 200..299) {
                _isYouTubeConnected.value = true
                val store = SessionStore.getInstance(getApplication())
                store.saveSession(cookie)
                checkAccountStatus()
                loadSyncedYouTubeTracks()
                loadSmartFeed()
                refreshHomeFeed()
                withContext(Dispatchers.Main) {
                    UnboundToast.show(getApplication(), "YouTube synced successfully!", isLong = false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    UnboundToast.show(getApplication(), "Sync Error [HTTP $code]: $resp", isLong = true)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account sync failed: ${e.message}")
            withContext(Dispatchers.Main) {
                UnboundToast.show(getApplication(), "Sync Exception: ${e.message}", isLong = true)
            }
        } finally {
            _isSyncingAccount.value = false
        }
    }
}

/** Re-triggers sync of liked music, account metadata, and personalized home feed with daemon. */
fun MainViewModel.resyncYouTubeAccount() {
    viewModelScope.launch(Dispatchers.IO) {
        _isSyncingAccount.value = true
        try {
            val (code, _) = client.syncAccount("")
            if (code in 200..299) {
                checkAccountStatus()
                loadSyncedYouTubeTracks()
                loadSmartFeed()
                refreshHomeFeed()
                withContext(Dispatchers.Main) {
                    UnboundToast.show(getApplication(), "Library refreshed!", isLong = false)
                }
            } else {
                withContext(Dispatchers.Main) {
                    UnboundToast.show(getApplication(), "Refresh error [HTTP $code]", isLong = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Resync failed: ${e.message}")
        } finally {
            _isSyncingAccount.value = false
        }
    }
}

/** Initiates YouTube Device Code flow, opens browser, and starts background polling. */
fun MainViewModel.startYouTubeDeviceAuth(onLaunchBrowser: (String) -> Unit) {
    deviceAuthJob?.cancel()
    deviceAuthJob = viewModelScope.launch(Dispatchers.IO) {
        _isStartingDeviceAuth.value = true
        _deviceAuthError.value = null
        try {
            val (code, resp) = client.startDeviceAuth()
            if (code in 200..299) {
                val data = client.parseDeviceCodeData(resp)
                if (data != null && data.userCode.isNotBlank()) {
                    _deviceAuthData.value = data
                    _isStartingDeviceAuth.value = false

                    val activateUrl = "${data.verificationUrl}?user_code=${data.userCode}"
                    withContext(Dispatchers.Main) {
                        onLaunchBrowser(activateUrl)
                    }

                    pollDeviceAuthToken(data.deviceCode, data.interval)
                    return@launch
                }
            }
            val errMsg = if (resp.isNotBlank()) "Device Auth Error [HTTP $code]: $resp" else "Device Auth Error [HTTP $code]"
            _deviceAuthError.value = errMsg
            withContext(Dispatchers.Main) {
                UnboundToast.show(getApplication(), errMsg, isLong = true)
            }
        } catch (e: Exception) {
            val exMsg = "Device Auth Exception: ${e.message ?: "Unknown error"}"
            _deviceAuthError.value = exMsg
            withContext(Dispatchers.Main) {
                UnboundToast.show(getApplication(), exMsg, isLong = true)
            }
        } finally {
            _isStartingDeviceAuth.value = false
        }
    }
}

internal suspend fun MainViewModel.pollDeviceAuthToken(deviceCode: String, intervalSeconds: Int) {
    _isPollingDeviceAuth.value = true
    val pollDelay = (if (intervalSeconds > 0) intervalSeconds else 5) * 1000L
    val maxAttempts = 60 // 5 minutes max

    for (i in 0 until maxAttempts) {
        if (!currentCoroutineContext().isActive) break
        delay(pollDelay)
        try {
            val (code, resp) = client.pollDeviceAuth(deviceCode)
            if (code in 200..299 && resp.isNotBlank()) {
                val root = JSONObject(resp)
                val status = root.optString("status", "")
                if (status == "success") {
                    _isYouTubeConnected.value = true
                    _deviceAuthData.value = null
                    _isPollingDeviceAuth.value = false
                    checkAccountStatus()
                    loadSyncedYouTubeTracks()
                    withContext(Dispatchers.Main) {
                        UnboundToast.show(
                            getApplication(),
                            "YouTube account connected successfully!",
                            isLong = false
                        )
                    }
                    return
                } else if (status == "pending") {
                    continue
                }
            } else if (code in 400..599 && !resp.contains("authorization_pending") && !resp.contains("slow_down")) {
                val pollErr = "Device Poll Error [HTTP $code]: $resp"
                _deviceAuthError.value = pollErr
                withContext(Dispatchers.Main) {
                    UnboundToast.show(getApplication(), pollErr, isLong = true)
                }
                break
            }
        } catch (e: Exception) {
            Log.d(TAG, "Device poll iteration note: ${e.message}")
        }
    }
    _isPollingDeviceAuth.value = false
}

/** Cancels any active device code authorization polling. */
fun MainViewModel.cancelDeviceAuth() {
    deviceAuthJob?.cancel()
    deviceAuthJob = null
    _deviceAuthData.value = null
    _isStartingDeviceAuth.value = false
    _isPollingDeviceAuth.value = false
    _deviceAuthError.value = null
}

/** Disconnects YouTube account and purges credentials and synced library data. */
fun MainViewModel.disconnectYouTubeAccount() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, _) = client.disconnectAccount()
            if (code in 200..299) {
                _isYouTubeConnected.value = false
                _accountName.value = "Local User"
                _userAvatarUrl.value = null
                _syncedYouTubeTracks.value = emptyList()
                _youtubeCount.value = 0
                SessionStore.getInstance(getApplication()).clear()
                refreshHomeFeed()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account disconnect failed: ${e.message}")
        }
    }
}

/** Loads cached synced Liked Music tracks from the Go engine daemon. */
fun MainViewModel.loadSyncedYouTubeTracks() {
    viewModelScope.launch(Dispatchers.IO) {
        _isSyncingAccount.value = true
        try {
            val (code, resp) = client.getLikedTracks()
            if (code in 200..299 && resp.isNotBlank()) {
                val tracks = client.parseLikedTracks(resp)
                val mixes = client.parseUserMixes(resp)
                _syncedYouTubeTracks.value = tracks
                _userMixes.value = mixes
                _youtubeCount.value = tracks.size
                withContext(Dispatchers.Main) {
                    if (tracks.isNotEmpty()) {
                        UnboundToast.show(getApplication(), "Synced ${tracks.size} tracks from YouTube!", isLong = false)
                    } else {
                        UnboundToast.show(getApplication(), "0 liked tracks found on YouTube account.", isLong = false)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Load synced tracks note: ${e.message}")
        } finally {
            _isSyncingAccount.value = false
        }
    }
}

/** Continuously loads next wave of music tracks for infinite scroll (logged-in & guest). */
fun MainViewModel.loadMorePersonalizedTracks() {
    if (_isLoadingMoreTracks.value) return
    viewModelScope.launch(Dispatchers.IO) {
        _isLoadingMoreTracks.value = true
        try {
            val seedPool = (_syncedYouTubeTracks.value + _adaptiveQuickPicks.value + _regionalCharts.value + _chartTracks.value)
                .filter { it.id.isNotBlank() && !it.id.startsWith("local:") }
            val seed = seedPool.randomOrNull()?.id

            var fetchedTracks: List<TrackItem> = emptyList()
            val (code, resp) = client.getAccountFeedInfinite(seed)
            if (code in 200..299 && resp.isNotBlank()) {
                fetchedTracks = client.parseLikedTracks(resp)
            }
            if (fetchedTracks.isEmpty() && !seed.isNullOrBlank()) {
                val (rCode, rResp) = client.getRadioNext(seed)
                if (rCode in 200..299 && rResp.isNotBlank()) {
                    fetchedTracks = client.parseRadioNext(rResp)
                }
            }

            val musicTracks = fetchedTracks.filter {
                !it.source.equals("podcast", true) && !it.itemType.equals("podcast", true)
            }
            if (musicTracks.isNotEmpty()) {
                if (_isYouTubeConnected.value || _syncedYouTubeTracks.value.isNotEmpty()) {
                    val existingSyncedIds = _syncedYouTubeTracks.value.map { it.id }.toSet()
                    val uniqueSynced = musicTracks.filter { it.id !in existingSyncedIds }
                    if (uniqueSynced.isNotEmpty()) {
                        _syncedYouTubeTracks.value = _syncedYouTubeTracks.value + uniqueSynced
                        _youtubeCount.value = _syncedYouTubeTracks.value.size
                    }
                }
                val existingPickIds = _adaptiveQuickPicks.value.map { it.id }.toSet()
                val uniquePicks = musicTracks.filter { it.id !in existingPickIds }
                if (uniquePicks.isNotEmpty()) {
                    _adaptiveQuickPicks.value = _adaptiveQuickPicks.value + uniquePicks
                }
                val existingHomeIds = _homeJumpBackTracks.value.map { it.id }.toSet()
                val uniqueHome = musicTracks.filter { it.id !in existingHomeIds }
                if (uniqueHome.isNotEmpty()) {
                    _homeJumpBackTracks.value = _homeJumpBackTracks.value + uniqueHome
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Load more infinite tracks: ${e.message}")
        } finally {
            _isLoadingMoreTracks.value = false
        }
    }
}

/** Optimistically toggles like state and dispatches mutation to daemon. */
fun MainViewModel.toggleTrackLike(track: TrackItem) {
    toggleTrackFavorite(track)
}

/** Executes 4-stage intelligent search cascade. */
fun MainViewModel.executeCascadeSearch(query: String) {
    if (query.isBlank()) {
        _cascadeSearchResponse.value = null
        _searchResults.value = emptyList()
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        _isSearching.value = true
        try {
            val (code, resp) = client.searchCascade(query)
            if (code in 200..299 && resp.isNotBlank()) {
                val cascade = client.parseCascadeSearch(resp)
                _cascadeSearchResponse.value = cascade
                if (cascade != null) {
                    _searchResults.value = cascade.tracks
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cascade search error: ${e.message}")
        } finally {
            _isSearching.value = false
        }
    }
}

/**
 * Logs a playback event to the Go daemon analytics engine.
 */
fun MainViewModel.logPlaybackEvent(track: TrackItem, listenedSec: Int) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            client.logPlaybackEvent(
                trackId = track.id.ifBlank { track.title },
                title = track.title,
                artist = track.artist,
                album = track.album,
                listenedSec = listenedSec
            )
            // Refresh smart shelves with updated listening affinity
            loadSmartFeed()
        } catch (e: Exception) {
            Log.d(TAG, "Analytics log note: ${e.message}")
        }
    }
}

/**
 * Imports a public Spotify playlist URL via the Go backend importer and saves it as a local playlist.
 */
fun MainViewModel.importSpotifyPlaylist(url: String) {
    val trimmed = url.trim()
    if (trimmed.isBlank()) return
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.importSpotify(trimmed)
            if (code !in 200..299 || resp.isBlank()) {
                val msg = try {
                    JSONObject(resp).optString("error", "Import failed [HTTP $code]")
                } catch (_: Exception) {
                    "Import failed [HTTP $code]"
                }
                withContext(Dispatchers.Main) {
                    UnboundToast.show(getApplication(), msg, isLong = true)
                }
                return@launch
            }
            val root = JSONObject(resp)
            val title = root.optString("title", "Spotify Playlist").ifBlank { "Spotify Playlist" }
            val description = root.optString("description", "Imported from Spotify")
            val arr = root.optJSONArray("tracks")
            val importedTracks = mutableListOf<TrackItem>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    val trackTitle = obj.optString("title", "").trim()
                    if (trackTitle.isBlank()) continue
                    val artist = obj.optString("artist", "Unknown Artist").ifBlank { "Unknown Artist" }
                    val album = obj.optString("album", "")
                    val durationMs = obj.optLong("duration_sec", 0L) * 1000L
                    importedTracks.add(
                        TrackItem(
                            id = "sp_${(trackTitle + artist).hashCode()}",
                            title = trackTitle,
                            artist = artist,
                            album = album,
                            durationMs = durationMs,
                            coverUrl = "",
                            source = "spotify_import"
                        )
                    )
                }
            }
            LocalPlaylistStore.createPlaylist(
                context = getApplication(),
                title = title,
                description = description,
                coverUrl = "",
                initialTracks = importedTracks
            )
            loadCustomPlaylists()
            withContext(Dispatchers.Main) {
                UnboundToast.show(
                    getApplication(),
                    "Imported \"$title\" (${importedTracks.size} tracks)",
                    isLong = true
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Spotify import failed: ${e.message}", e)
            withContext(Dispatchers.Main) {
                UnboundToast.show(getApplication(), "Spotify import error: ${e.message}", isLong = true)
            }
        }
    }
}
