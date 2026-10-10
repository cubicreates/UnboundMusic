/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelEqualizer.kt
 * Purpose: Audio DSP, AutoEq presets, themes, sleep timer, position ticker, SponsorBlock skips, and storage cache purging.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.audio.EqualizerCurve
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.service.UnboundPlaybackService
import com.cubicreates.unboundmusic.service.UnboundStorageManager
import com.cubicreates.unboundmusic.ui.equalizer.AutoEqHeadphoneItem
import com.cubicreates.unboundmusic.ui.theme.AppThemePreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val TAG = "MainViewModelEqualizer"

/**
 * Ticks every 800ms during playback to update progress and check SponsorBlock skips with minimal CPU overhead.
 */
internal fun MainViewModel.startPositionTicker() {
    viewModelScope.launch {
        while (isActive) {
            if (playbackState.value.isPlaying) {
                serviceConnection.updatePosition()
                checkSponsorBlockSkip()
                val now = System.currentTimeMillis()
                if (now - lastSavedStateTick >= 6000L) {
                    lastSavedStateTick = now
                    saveCurrentPlaybackState()
                }
            }
            delay(800)
        }
    }
}

internal fun MainViewModel.checkSponsorBlockSkip() {
    if (!_sponsorBlockEnabled.value) return
    val segments = _activeSkipSegments.value
    if (segments.isEmpty()) return
    val curPos = playbackState.value.currentPositionMs
    val duration = playbackState.value.durationMs

    for (segment in segments) {
        if (curPos >= segment.startMs && curPos < (segment.endMs - 150L) && segment.uuid != lastSkippedSegmentId) {
            lastSkippedSegmentId = segment.uuid
            val fromMs = curPos
            val toMs = segment.endMs.coerceAtMost(duration)
            seekToPositionMs(toMs)

            val startStr = formatTimestamp(segment.startMs)
            val endStr = formatTimestamp(segment.endMs)
            _skippedSkitNotice.value = SkitSkipNotice(
                segment = segment,
                fromMs = fromMs,
                toMs = toMs,
                message = "Skipped Non-Music Skit ($startStr - $endStr)"
            )

            skitNoticeDismissJob?.cancel()
            skitNoticeDismissJob = viewModelScope.launch {
                delay(4500)
                _skippedSkitNotice.value = null
            }
            break
        }
    }
}

private fun formatTimestamp(ms: Long): String {
    val totalSec = ms / 1000
    val m = totalSec / 60
    val s = totalSec % 60
    return String.format("%d:%02d", m, s)
}

/**
 * Starts the bedtime sleep timer with logarithmic 30-second volume fadeout or end-of-track stop.
 */
fun MainViewModel.startSleepTimer(minutes: Int, endOfSong: Boolean = false) {
    cancelSleepTimer()

    if (endOfSong) {
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            remainingMs = 0L,
            initialDurationMs = 0L,
            endOfTrack = true
        )
        sleepTimerJob = viewModelScope.launch {
            var initialTrackId = playbackState.value.currentTrack?.id
            while (isActive) {
                delay(500)
                val state = playbackState.value
                val curTrackId = state.currentTrack?.id
                if (initialTrackId == null && curTrackId != null) {
                    initialTrackId = curTrackId
                }
                val nearEnd = state.durationMs > 5_000L && state.currentPositionMs >= (state.durationMs - 1500L)
                val trackChanged = initialTrackId != null && curTrackId != null && curTrackId != initialTrackId

                if (nearEnd || trackChanged) {
                    for (i in 10 downTo 0) {
                        UnboundPlaybackService.activeSleepFadeGain = (i / 10f) * (i / 10f)
                        delay(100)
                    }
                    if (playbackState.value.isPlaying) {
                        serviceConnection.togglePlayPause()
                    }
                    cancelSleepTimer()
                    break
                }
            }
        }
        return
    }

    val totalMs = minutes * 60 * 1000L
    val fadeDurationMs = minOf(30_000L, totalMs)
    _sleepTimerState.value = SleepTimerState(
        isActive = true,
        remainingMs = totalMs,
        initialDurationMs = totalMs,
        endOfTrack = false
    )

    sleepTimerJob = viewModelScope.launch {
        val startTime = System.currentTimeMillis()
        val endTime = startTime + totalMs

        while (isActive) {
            val now = System.currentTimeMillis()
            val remaining = (endTime - now).coerceAtLeast(0L)
            _sleepTimerState.value = _sleepTimerState.value.copy(remainingMs = remaining)

            // Smooth exponential 30-second volume fadeout
            if (remaining <= fadeDurationMs) {
                val fadeRatio = (remaining.toFloat() / fadeDurationMs.toFloat()).coerceIn(0f, 1f)
                UnboundPlaybackService.activeSleepFadeGain = fadeRatio * fadeRatio
            } else {
                UnboundPlaybackService.activeSleepFadeGain = 1.0f
            }

            if (remaining <= 0L) {
                if (playbackState.value.isPlaying) {
                    serviceConnection.togglePlayPause()
                }
                cancelSleepTimer()
                break
            }
            delay(500)
        }
    }
}

/**
 * Cancels active sleep timer and resets audio fade attenuation gain to 1.0f.
 */
fun MainViewModel.cancelSleepTimer() {
    sleepTimerJob?.cancel()
    sleepTimerJob = null
    UnboundPlaybackService.activeSleepFadeGain = 1.0f
    _sleepTimerState.value = SleepTimerState(isActive = false)
}

/**
 * Reloads custom playlists, favorites, and playback settings after a backup restore.
 */
fun MainViewModel.reloadAfterRestore() {
    loadCustomPlaylists()
    _streamingQuality.value = PlaybackStateStore.getStreamingQuality(getApplication())
    _downloadQuality.value = PlaybackStateStore.getDownloadQuality(getApplication())
    _autoDownloadLikedSongs.value = PlaybackStateStore.isAutoDownloadLiked(getApplication())
    _skipSilenceEnabled.value = PlaybackStateStore.isSkipSilence(getApplication())
    _normalizeVolumeEnabled.value = PlaybackStateStore.isNormalizeVolume(getApplication())
    _sponsorBlockEnabled.value = PlaybackStateStore.isSponsorBlockEnabled(getApplication())
    _isFavorite.value = PlaybackStateStore.isFavoriteTrack(getApplication(), _currentTrack.value.id, _currentTrack.value.title)
}

fun MainViewModel.setEqualizerCurve(curve: EqualizerCurve) {
    _equalizerCurve.value = curve
    serviceConnection.setEqualizerCurve(curve)
}

fun MainViewModel.searchAutoEqPresets(query: String) {
    if (query.isBlank()) {
        _autoEqResults.value = emptyList()
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        _isSearchingAutoEq.value = true
        try {
            val (code, resp) = client.autoEqSearch(query)
            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                val results = json.optJSONArray("results")
                val items = mutableListOf<AutoEqHeadphoneItem>()
                if (results != null) {
                    for (i in 0 until results.length()) {
                        val r = results.getJSONObject(i)
                        items.add(
                            AutoEqHeadphoneItem(
                                id = r.optString("id", r.optString("name")),
                                name = r.optString("name", "Unknown Model"),
                                source = r.optString("source", "Harman Target")
                            )
                        )
                    }
                }
                _autoEqResults.value = items
            }
        } catch (e: Exception) {
            Log.d(TAG, "AutoEq search error: ${e.message}")
        } finally {
            _isSearchingAutoEq.value = false
        }
    }
}

fun MainViewModel.applyAutoEqPreset(item: AutoEqHeadphoneItem) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.autoEqPreset(item.id)
            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                val preamp = json.optDouble("preamp_db", 0.0).toFloat()
                val bandsArr = json.optJSONArray("bands_db")
                val bandsList = mutableListOf<Float>()
                if (bandsArr != null) {
                    for (i in 0 until bandsArr.length()) {
                        bandsList.add(bandsArr.getDouble(i).toFloat())
                    }
                }
                if (bandsList.size == 10) {
                    val curve = EqualizerCurve(bandsList, preamp)
                    setEqualizerCurve(curve)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Apply AutoEq error: ${e.message}")
        }
    }
}

fun MainViewModel.setTheme(preset: AppThemePreset) {
    _selectedTheme.value = preset
    viewModelScope.launch(Dispatchers.IO) {
        client.setAppSetting("theme_preset", preset.id)
    }
}

fun MainViewModel.setBassBoost(strength: Int) {
    _bassBoostStrength.value = strength
    serviceConnection.setBassBoost(strength)
    viewModelScope.launch(Dispatchers.IO) {
        client.setAppSetting("eq_bass_boost", strength.toString())
    }
}

fun MainViewModel.setVirtualizer(strength: Int) {
    _virtualizerStrength.value = strength
    serviceConnection.setVirtualizer(strength)
    viewModelScope.launch(Dispatchers.IO) {
        client.setAppSetting("eq_virtualizer", strength.toString())
    }
}

fun MainViewModel.setLoudness(gainMb: Int) {
    _loudnessGainMb.value = gainMb
    serviceConnection.setLoudness(gainMb)
    viewModelScope.launch(Dispatchers.IO) {
        client.setAppSetting("eq_loudness", gainMb.toString())
    }
}

fun MainViewModel.setReverbPreset(preset: Short) {
    _reverbPreset.value = preset
    serviceConnection.setReverbPreset(preset)
}

fun MainViewModel.setAutoDownloadLikedSongs(enabled: Boolean) {
    _autoDownloadLikedSongs.value = enabled
    PlaybackStateStore.setAutoDownloadLiked(getApplication(), enabled)
}

fun MainViewModel.setSkipSilenceEnabled(enabled: Boolean) {
    _skipSilenceEnabled.value = enabled
    PlaybackStateStore.setSkipSilence(getApplication(), enabled)
    serviceConnection.setSkipSilence(enabled)
}

fun MainViewModel.setNormalizeVolumeEnabled(enabled: Boolean) {
    _normalizeVolumeEnabled.value = enabled
    PlaybackStateStore.setNormalizeVolume(getApplication(), enabled)
    val targetGain = if (enabled) 1000 else 0
    serviceConnection.setLoudness(targetGain)
}

fun MainViewModel.setSponsorBlockEnabled(enabled: Boolean) {
    _sponsorBlockEnabled.value = enabled
    PlaybackStateStore.setSponsorBlockEnabled(getApplication(), enabled)
    if (!enabled) {
        _activeSkipSegments.value = emptyList()
        _skippedSkitNotice.value = null
    } else {
        val current = _currentTrack.value
        if (current.id.isNotBlank()) {
            viewModelScope.launch(Dispatchers.IO) {
                fetchSkipSegments(current)
            }
        }
    }
}

fun MainViewModel.setLockscreenWallpaperEnabled(enabled: Boolean) {
    _isLockscreenWallpaperEnabled.value = enabled
    PlaybackStateStore.setLockscreenWallpaperEnabled(getApplication(), enabled)
    if (!enabled) {
        com.cubicreates.unboundmusic.service.LockscreenArtworkManager.clearLockscreenArtwork(getApplication())
    } else {
        val track = _currentTrack.value
        if (track.id.isNotBlank() && track.title.isNotBlank()) {
            com.cubicreates.unboundmusic.service.LockscreenArtworkManager.updateLockscreenArtwork(getApplication(), track)
        }
    }
}

fun MainViewModel.setVlcAutoScanEnabled(enabled: Boolean) {
    _isVlcAutoScanEnabled.value = enabled
    PlaybackStateStore.setVlcAutoScanEnabled(getApplication(), enabled)
}

fun MainViewModel.setStreamingQuality(quality: AudioQuality) {
    _streamingQuality.value = quality
    PlaybackStateStore.setStreamingQuality(getApplication(), quality)
}

fun MainViewModel.setDownloadQuality(quality: AudioQuality) {
    _downloadQuality.value = quality
    PlaybackStateStore.setDownloadQuality(getApplication(), quality)
}

fun MainViewModel.setPlaybackSpeed(speed: Float, pitch: Float = 1.0f) {
    serviceConnection.setPlaybackSpeed(speed, pitch)
}

fun MainViewModel.selectHomeMood(mood: String) {
    if (_selectedHomeMood.value == mood) return
    _selectedHomeMood.value = mood
    if (mood.equals("All", ignoreCase = true)) {
        _moodTracks.value = emptyList()
        _isMoodLoading.value = false
        return
    }

    val cached = moodCache[mood.lowercase()]
    if (cached != null && cached.isNotEmpty()) {
        _moodTracks.value = cached
        _isMoodLoading.value = false
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        _isMoodLoading.value = true
        try {
            val browseId = when (mood.lowercase()) {
                "relax" -> "FEmusic_moods_and_genres_category_chill"
                "sleep" -> "FEmusic_moods_and_genres_category_sleep"
                "energize" -> "FEmusic_moods_and_genres_category_energy"
                "sad" -> "FEmusic_moods_and_genres_category_sad"
                "romance" -> "FEmusic_moods_and_genres_category_rnb"
                "feel good" -> "FEmusic_moods_and_genres_category_pop"
                "workout" -> "FEmusic_moods_and_genres_category_workout"
                "party" -> "FEmusic_moods_and_genres_category_dance"
                "commute" -> "FEmusic_moods_and_genres_category_commute"
                "focus" -> "FEmusic_moods_and_genres_category_focus"
                else -> "FEmusic_moods_and_genres_category_${mood.lowercase()}"
            }

            var tracks = client.getMoodRadio(browseId)
            if (tracks.isEmpty()) {
                val (code, json) = client.search("$mood mix", "music")
                if (code in 200..299 && json.isNotBlank()) {
                    val searchTracks = client.parseSearchResults(json)
                    if (searchTracks.isNotEmpty()) {
                        tracks = searchTracks
                    }
                }
            }

            if (tracks.isNotEmpty()) {
                moodCache[mood.lowercase()] = tracks
                if (_selectedHomeMood.value == mood) {
                    _moodTracks.value = tracks
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed loading mood tracks for $mood: ${e.message}")
        } finally {
            if (_selectedHomeMood.value == mood) {
                _isMoodLoading.value = false
            }
        }
    }
}

fun MainViewModel.saveCustomEqPreset(name: String, curve: EqualizerCurve, bassBoost: Int, virtualizer: Int, loudness: Int) {
    val preset = UserEqPresetDto(
        id = "preset_" + System.currentTimeMillis(),
        name = name,
        bandGains = curve.bandsDb,
        bassBoost = bassBoost,
        virtualizer = virtualizer,
        loudness = loudness
    )
    viewModelScope.launch(Dispatchers.IO) {
        client.saveCustomEqPreset(preset)
        loadCustomEqPresets()
    }
}

fun MainViewModel.loadCustomEqPresets() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getCustomEqPresets()
            if (code in 200..299 && resp.isNotBlank()) {
                val list = client.parseEqPresets(resp)
                _customEqPresets.value = list
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed loading custom presets: ${e.message}")
        }
    }
}

fun MainViewModel.loadAppSettings() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getAppSettings()
            if (code in 200..299 && resp.isNotBlank()) {
                val settings = client.parseSettings(resp)
                settings["theme_preset"]?.let { themeId ->
                    _selectedTheme.value = AppThemePreset.fromId(themeId)
                }
                settings["eq_bass_boost"]?.toIntOrNull()?.let { bb ->
                    _bassBoostStrength.value = bb
                    serviceConnection.setBassBoost(bb)
                }
                settings["eq_virtualizer"]?.toIntOrNull()?.let { v ->
                    _virtualizerStrength.value = v
                    serviceConnection.setVirtualizer(v)
                }
                settings["eq_loudness"]?.toIntOrNull()?.let { l ->
                    _loudnessGainMb.value = l
                    serviceConnection.setLoudness(l)
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed loading app settings: ${e.message}")
        }
    }
}

fun MainViewModel.purgeCache() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // Purge .backend models, logs and cache
            val unboundRoot = UnboundStorageManager.getCanonicalUnboundRoot(getApplication())
            val backendDir = File(unboundRoot, ".backend")
            if (backendDir.exists()) {
                val modelsDir = File(backendDir, "models")
                if (modelsDir.exists()) modelsDir.deleteRecursively()
            }

            val (code, resp) = client.purgeStorageCache()
            if (code in 200..299 && resp.isNotBlank()) {
                val result = client.parseCachePurgeResult(resp)
                if (result != null) {
                    val mb = result.freedBytes / (1024 * 1024f)
                    _cachePurgeStatus.value = String.format("Purged %.1f MB across %d categories", mb, result.purgedCategories.size)
                } else {
                    _cachePurgeStatus.value = "Storage cache purged successfully"
                }
            } else {
                _cachePurgeStatus.value = "Storage & AI models cache purged"
            }
        } catch (e: Exception) {
            _cachePurgeStatus.value = "Purge error: ${e.message}"
        }
    }
}

/**
 * Gracefully stops the Go engine daemon, wipes all unpacked native binaries,
 * LLM model weights, and internal caches, leaving downloaded songs safe in device storage,
 * and triggers the system uninstaller prompt.
 */
fun MainViewModel.purgeUnboundStorageForUninstall(triggerAppUninstall: Boolean = true) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            _cachePurgeStatus.value = "Stopping Go background engine..."
            daemonManager.stopDaemon()

            _cachePurgeStatus.value = "Purging LLM models, native engine & internal caches..."
            val bytesFreed = UnboundStorageManager.purgeBackendAndEngine(getApplication())
            val mbFreed = bytesFreed / (1024 * 1024)

            _cachePurgeStatus.value = "Cleaned engine & models (${mbFreed}MB freed). Music preserved."

            if (triggerAppUninstall) {
                withContext(Dispatchers.Main) {
                    try {
                        val context = getApplication<Application>()
                        val uninstallIntent = Intent(Intent.ACTION_DELETE).apply {
                            data = Uri.parse("package:${context.packageName}")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        context.startActivity(uninstallIntent)
                    } catch (ue: Exception) {
                        Log.w(TAG, "Trigger uninstall note: ${ue.message}")
                    }
                }
            }
        } catch (e: Exception) {
            _cachePurgeStatus.value = "Uninstall cleanup error: ${e.message}"
        }
    }
}
