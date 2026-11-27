/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelAccount.kt
 * Purpose: YouTube account synchronization, OAuth device code flow, synced liked library, and analytics logging.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.util.Log
import android.webkit.CookieManager
import android.webkit.WebStorage
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.data.SessionStore
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "MainViewModelAccount"

internal fun isPlaceholderAccountName(name: String?): Boolean {
    val trimmed = name?.trim()?.lowercase() ?: return true
    return trimmed.isBlank() || trimmed in setOf(
        "local user",
        "local unbound user",
        "connected user",
        "youtube user",
        "guest listener",
        "youtube account"
    )
}

/** Checks YouTube connection status from daemon and loads synced tracks if connected. */
fun MainViewModel.checkAccountStatus() {
    val epochAtStart = accountSessionEpoch.get()
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getAccountStatus()
            if (accountSessionEpoch.get() != epochAtStart) return@launch
            if (code in 200..299 && resp.isNotBlank()) {
                val status = client.parseAccountStatus(resp)
                if (status != null) {
                    if (accountSessionEpoch.get() != epochAtStart) return@launch
                    _isYouTubeConnected.value = status.connected
                    if (status.connected) {
                        if (!isPlaceholderAccountName(status.accountName)) {
                            _accountName.value = status.accountName
                        }
                        if (status.avatarUrl.isNotBlank()) {
                            _userAvatarUrl.value = status.avatarUrl
                        }
                        val store = SessionStore.getInstance(getApplication())
                        if (!isPlaceholderAccountName(status.accountName)) {
                            store.accountName = status.accountName
                        }
                        if (status.avatarUrl.isNotBlank()) {
                            store.avatarUrl = status.avatarUrl
                        }
                        loadSyncedYouTubeTracks(silent = true)
                    } else {
                        _accountName.value = "Local User"
                        _userAvatarUrl.value = null
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Account status check note: ${e.message}")
        }
    }
}

/** Synchronizes YouTube account credentials and pulls liked music into the local library. */
fun MainViewModel.syncYouTubeAccount(
    cookie: String,
    nameHint: String = "",
    avatarHint: String = ""
) {
    val loginEpoch = accountSessionEpoch.incrementAndGet()
    viewModelScope.launch(Dispatchers.IO) {
        _isSyncingAccount.value = true
        try {
            val (code, resp) = client.syncAccount(cookie, nameHint, avatarHint)
            if (accountSessionEpoch.get() != loginEpoch) return@launch
            if (code in 200..299) {
                _isYouTubeConnected.value = true
                val store = SessionStore.getInstance(getApplication())
                store.saveSession(cookie)

                val parsedStatus = client.parseAccountStatus(resp)
                val resolvedName = parsedStatus?.accountName?.takeIf { !isPlaceholderAccountName(it) }
                    ?: nameHint.takeIf { !isPlaceholderAccountName(it) }
                val resolvedAvatar = parsedStatus?.avatarUrl?.takeIf { it.isNotBlank() }
                    ?: avatarHint.takeIf { it.isNotBlank() }

                if (resolvedName != null) {
                    _accountName.value = resolvedName
                    store.accountName = resolvedName
                }
                if (resolvedAvatar != null) {
                    _userAvatarUrl.value = resolvedAvatar
                    store.avatarUrl = resolvedAvatar
                }

                checkAccountStatus()
                loadSyncedYouTubeTracks(silent = false)
                loadSmartFeed()
                refreshHomeFeed()
                loadSearchHistory()
                withContext(Dispatchers.Main) {
                }
            } else {
                withContext(Dispatchers.Main) {
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Account sync failed: ${e.message}")
            withContext(Dispatchers.Main) {
            }
        } finally {
            _isSyncingAccount.value = false
        }
    }
}

/** Re-triggers sync of liked music, account metadata, and personalized home feed with daemon. */
fun MainViewModel.resyncYouTubeAccount() {
    val epochAtStart = accountSessionEpoch.get()
    viewModelScope.launch(Dispatchers.IO) {
        _isSyncingAccount.value = true
        try {
            val (code, _) = client.syncAccount("")
            if (accountSessionEpoch.get() != epochAtStart) return@launch
            if (code in 200..299) {
                checkAccountStatus()
                loadSyncedYouTubeTracks(silent = false)
                loadSmartFeed()
                refreshHomeFeed()
                loadSearchHistory()
                withContext(Dispatchers.Main) {
                }
            } else {
                withContext(Dispatchers.Main) {
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
            }
        } catch (e: Exception) {
            val exMsg = "Device Auth Exception: ${e.message ?: "Unknown error"}"
            _deviceAuthError.value = exMsg
            withContext(Dispatchers.Main) {
            }
        } finally {
            _isStartingDeviceAuth.value = false
        }
    }
}

internal suspend fun MainViewModel.pollDeviceAuthToken(deviceCode: String, intervalSeconds: Int) {
    val epochAtStart = accountSessionEpoch.get()
    _isPollingDeviceAuth.value = true
    val pollDelay = (if (intervalSeconds > 0) intervalSeconds else 5) * 1000L
    val maxAttempts = 60 // 5 minutes max

    for (i in 0 until maxAttempts) {
        if (!currentCoroutineContext().isActive || accountSessionEpoch.get() != epochAtStart) break
        delay(pollDelay)
        if (!currentCoroutineContext().isActive || accountSessionEpoch.get() != epochAtStart) break
        try {
            val (code, resp) = client.pollDeviceAuth(deviceCode)
            if (accountSessionEpoch.get() != epochAtStart) break
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
                    }
                    return
                } else if (status == "pending") {
                    continue
                }
            } else if (code in 400..599 && !resp.contains("authorization_pending") && !resp.contains("slow_down")) {
                val pollErr = "Device Poll Error [HTTP $code]: $resp"
                _deviceAuthError.value = pollErr
                withContext(Dispatchers.Main) {
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

/** Disconnects YouTube account and purges credentials, WebView cookies, and synced library data. */
fun MainViewModel.disconnectYouTubeAccount() {
    val disconnectEpoch = accountSessionEpoch.incrementAndGet()
    cancelDeviceAuth()
    _isSyncingAccount.value = false

    // Optimistically clear all signed-in UI state immediately so logout is instantaneous and reliable
    _isYouTubeConnected.value = false
    _accountName.value = "Local User"
    _userAvatarUrl.value = null
    _syncedYouTubeTracks.value = emptyList()
    _userMixes.value = emptyList()
    _smartShelves.value = emptyList()
    _homeMixes.value = emptyList()
    _homeJumpBackTracks.value = emptyList()
    _adaptiveQuickPicks.value = emptyList()
    _youtubeCount.value = 0
    _homeJumpBackMode.value = "guest"
    _homeBannerTitle.value = ""
    _homeBannerSubtitle.value = ""
    _homeBannerAction.value = ""

    val cur = _currentTrack.value
    _isFavorite.value = try {
        cur.title.isNotBlank() && PlaybackStateStore.isFavoriteTrack(getApplication(), cur.id, cur.title)
    } catch (_: Throwable) {
        false
    }

    SessionStore.getInstance(getApplication()).clear()

    viewModelScope.launch(Dispatchers.Main) {
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (e: Throwable) {
            Log.w(TAG, "WebView cookie cleanup note: ${e.message}")
        }
        try {
            WebStorage.getInstance().deleteAllData()
        } catch (e: Throwable) {
            Log.w(TAG, "WebStorage cleanup note: ${e.message}")
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
        try {
            var (code, _) = client.disconnectAccount()
            if (code !in 200..299) {
                delay(250)
                code = client.disconnectAccount().first
            }
            // Guard against a startup sync racing right before disconnect finished
            if (accountSessionEpoch.get() == disconnectEpoch) {
                val (sCode, sResp) = client.getAccountStatus()
                if (sCode in 200..299 && client.parseAccountStatus(sResp)?.connected == true) {
                    client.disconnectAccount()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Backend account disconnect note: ${e.message}")
        }
        if (accountSessionEpoch.get() == disconnectEpoch) {
            refreshHomeFeed()
            loadSearchHistory()
        }
    }
}

/** Loads cached synced Liked Music tracks from the Go engine daemon. */
fun MainViewModel.loadSyncedYouTubeTracks(silent: Boolean = false) {
    val epochAtStart = accountSessionEpoch.get()
    viewModelScope.launch(Dispatchers.IO) {
        if (!silent) _isSyncingAccount.value = true
        try {
            val (code, resp) = client.getLikedTracks()
            if (accountSessionEpoch.get() != epochAtStart || !_isYouTubeConnected.value) return@launch
            if (code in 200..299 && resp.isNotBlank()) {
                val tracks = client.parseLikedTracks(resp)
                val mixes = client.parseUserMixes(resp)
                if (accountSessionEpoch.get() != epochAtStart || !_isYouTubeConnected.value) return@launch
                _syncedYouTubeTracks.value = tracks
                _userMixes.value = mixes
                _youtubeCount.value = tracks.size
                val cur = _currentTrack.value
                if (cur.id.isNotBlank() && tracks.any { it.id == cur.id }) {
                    _isFavorite.value = true
                }
                if (!silent) {
                    withContext(Dispatchers.Main) {
                        if (tracks.isNotEmpty()) {
                        } else {
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Load synced tracks note: ${e.message}")
        } finally {
            if (!silent) _isSyncingAccount.value = false
        }
    }
}

/** Continuously loads next wave of music tracks for infinite scroll (logged-in & guest). */
fun MainViewModel.loadMorePersonalizedTracks() {
    if (_isLoadingMoreTracks.value) return
    val epochAtStart = accountSessionEpoch.get()
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

            if (accountSessionEpoch.get() != epochAtStart) return@launch

            val musicTracks = fetchedTracks.filter {
                !it.source.equals("podcast", true) && !it.itemType.equals("podcast", true)
            }
            if (musicTracks.isNotEmpty()) {
                if (_isYouTubeConnected.value) {
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
