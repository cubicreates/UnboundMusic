/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelShazam.kt
 * Purpose: Shazam acoustic recognition, voice & hum search, and P2P fallback resolution.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Uri
import android.os.BatteryManager
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.data.FallbackStatusDto
import com.cubicreates.unboundmusic.ui.components.RecognizedTrackVariant
import com.cubicreates.unboundmusic.ui.components.ShazamMode
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "MainViewModelShazam"

fun MainViewModel.clearLastRecognizedTrack() {
    _lastRecognizedTrack.value = null
    _recognizedVariants.value = emptyList()
}

fun MainViewModel.clearLastHummedTrack() {
    _lastHummedTrack.value = null
    _hummedVariants.value = emptyList()
}

fun MainViewModel.clearShazamHistory() {
    _shazamHistory.value = emptyList()
}

fun MainViewModel.clearHummingHistory() {
    _hummingHistory.value = emptyList()
}

fun MainViewModel.setShazamMode(mode: ShazamMode) {
    _shazamMode.value = mode
    _recognizedMessage.value = if (mode == ShazamMode.HUMMING) {
        "Tap the radar to hum a tune with Google Sound Search"
    } else {
        "Tap the radar to identify playing music"
    }
}

fun MainViewModel.launchGoogleSoundSearch(context: Context): Boolean {
    val intents = listOf(
        Intent(Intent.ACTION_VIEW, Uri.parse("googleapp://soundsearch")),
        Intent("com.google.android.googlequicksearchbox.action.RECOGNIZE_MUSIC").apply {
            setPackage("com.google.android.googlequicksearchbox")
        },
        Intent("com.google.android.googlequicksearchbox.MUSIC_SEARCH").apply {
            setPackage("com.google.android.googlequicksearchbox")
        },
        Intent(android.provider.MediaStore.INTENT_ACTION_MEDIA_SEARCH),
        Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Hum, sing, or whistle a tune")
            putExtra("android.speech.extra.RECOGNITION_CONTEXT", "music")
        },
        Intent(Intent.ACTION_WEB_SEARCH).apply {
            putExtra(android.app.SearchManager.QUERY, "what is this song")
        }
    )
    for (intent in intents) {
        try {
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            context.startActivity(intent)
            com.cubicreates.unboundmusic.util.UnboundToast.show(
                context,
                "Opening Google Sound Search for hummed melody...",
                isLong = false
            )
            return true
        } catch (_: Exception) {}
    }
    com.cubicreates.unboundmusic.util.UnboundToast.show(
        context,
        "Google Sound Search not available on this device",
        isLong = false
    )
    return false
}

fun MainViewModel.startVoiceSearch() {
    voiceSearchRequestEvent.tryEmit(Unit)
}

fun MainViewModel.startHumSearch() {
    _recognizedMessage.value = "Listening for hummed melody, singing, or lyrics..."
    humSearchRequestEvent.tryEmit(Unit)
}

fun MainViewModel.requestAllFilesAccess() {
    storagePermissionRequestEvent.tryEmit(Unit)
}

fun MainViewModel.handleVoiceSearchResult(spokenQuery: String) {
    val trimmed = spokenQuery.trim()
    if (trimmed.isBlank()) return
    submitSearch(trimmed)
}

fun MainViewModel.handleHumSearchResult(spokenQuery: String) {
    val trimmed = spokenQuery.trim()
    if (trimmed.isBlank()) return
    _recognizedMessage.value = "Analyzing hummed melody / lyrics: \"$trimmed\"..."
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var searchResults = emptyList<TrackItem>()
            val (code, resp) = client.search(trimmed, "song")
            if (code in 200..299 && resp.isNotBlank()) {
                searchResults = client.parseSearchResults(resp)
            }
            if (searchResults.isEmpty()) {
                val (code2, resp2) = client.search("$trimmed song", "song")
                if (code2 in 200..299 && resp2.isNotBlank()) {
                    searchResults = client.parseSearchResults(resp2)
                }
            }
            val remixIndicator = Regex("""(?i)\b(remix|mix|club mix|radio edit|acoustic|cover|slowed|reverb|sped up|speed up|bootleg|flip|vip|mashup|live|tribute|orchestral|version|instrumental|karaoke|lofi)\b""")
            val songs = searchResults.filter { it.itemType == "song" }
            val cleanSong = songs.firstOrNull { !remixIndicator.containsMatchIn(it.title) } ?: songs.firstOrNull()
            if (cleanSong != null) {
                val matchedTrack = TrackItem(
                    id = cleanSong.id,
                    title = cleanSong.title,
                    artist = cleanSong.artist,
                    album = cleanSong.album,
                    durationMs = cleanSong.durationMs,
                    coverUrl = cleanSong.coverUrl,
                    source = "hum_search"
                )
                _lastHummedTrack.value = matchedTrack
                _hummingHistory.value = listOf(matchedTrack) + _hummingHistory.value.filter { it.id != matchedTrack.id }

                val variants = songs.take(3).mapIndexed { idx, s ->
                    val isOrig = !remixIndicator.containsMatchIn(s.title)
                    RecognizedTrackVariant(
                        track = s,
                        badge = if (idx == 0 && isOrig) "Top Melodic Match (Original)" else if (idx == 0) "Top Melodic Match" else "Melodic Alternative",
                        isOriginal = isOrig,
                        isRadarMatch = false,
                        explanation = "Matched via Hum-to-Search melody deduction"
                    )
                }
                _hummedVariants.value = variants
                _recognizedMessage.value = "Matched melody: ${matchedTrack.title} - ${matchedTrack.artist}"
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        getApplication(),
                        "Matched: ${matchedTrack.title} - ${matchedTrack.artist}",
                        isLong = true
                    )
                }
                return@launch
            }
            _recognizedMessage.value = "No melody match found for \"$trimmed\". Try humming a distinct section or sing lyrics."
            withContext(Dispatchers.Main) {
                com.cubicreates.unboundmusic.util.UnboundToast.show(
                    getApplication(),
                    "No melody match found for \"$trimmed\"."
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Hum search error: ${e.message}")
            _recognizedMessage.value = "Could not identify hummed tune. Please try again."
        }
    }
}

fun MainViewModel.startAmbientShazamRecognition(contextOverride: Context? = null) {
    if (_shazamMode.value == ShazamMode.HUMMING) {
        val targetContext = contextOverride ?: getApplication<Application>()
        launchGoogleSoundSearch(targetContext)
        return
    }
    if (_isListeningShazam.value) {
        stopAndSendShazamRecording()
        return
    }
    val context = getApplication<Application>()
    if (androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    ) {
        executeAmbientShazamCapture()
    } else {
        audioPermissionRequestEvent.tryEmit(Unit)
    }
}

fun MainViewModel.stopAndSendShazamRecording() {
    if (_isListeningShazam.value) {
        _recognizedMessage.value = "Finalizing & identifying audio..."
        com.cubicreates.unboundmusic.audio.AmbientAudioRecorder.stopRecording()
    }
}

fun MainViewModel.onAudioPermissionGranted() {
    executeAmbientShazamCapture()
}

internal fun MainViewModel.executeAmbientShazamCapture() {
    if (_isListeningShazam.value) return
    _isListeningShazam.value = true
    val isHum = _shazamMode.value == ShazamMode.HUMMING
    _recordingDurationSeconds.value = 0
    _recognizedMessage.value = if (isHum) {
        "Hum or sing a tune... Tap radar to identify early"
    } else {
        "Listening to music... Identifying within seconds"
    }

    val tickerJob = viewModelScope.launch {
        while (isActive && _isListeningShazam.value) {
            delay(1000)
            _recordingDurationSeconds.value++
        }
    }

    viewModelScope.launch(Dispatchers.IO) {
        try {
            _recognizedMessage.value = if (isHum) {
                "Humming radar active... Analyzing melody"
            } else {
                "Listening to music... Identifying within seconds"
            }
            val matched = com.cubicreates.unboundmusic.audio.AmbientAudioRecorder.streamPcm(
                maxDurationMs = 12000,
                checkpointIntervalsMs = listOf(3500, 6000, 9000, 12000)
            ) { cumulativePcm, isFinal ->
                _recognizedMessage.value = "Analyzing acoustic fingerprint..."
                val (code, resp) = client.identifyPcmAudio(cumulativePcm)
                if (code in 200..299 && resp.isNotBlank()) {
                    val success = parseAndSetShazamResult(resp)
                    if (success) {
                        return@streamPcm true // EARLY EXIT! Match achieved!
                    }
                }
                if (isFinal) {
                    val reasonMsg = if (_shazamMode.value == ShazamMode.HUMMING) {
                        "Could not match hummed melody. Tap Google Sound Search below to identify melody by pitch, or tap 'Sing Lyrics'."
                    } else {
                        "No acoustic match found. Try again closer to the speaker."
                    }
                    _recognizedMessage.value = reasonMsg
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            reasonMsg
                        )
                    }
                } else {
                    _recognizedMessage.value = if (_shazamMode.value == ShazamMode.HUMMING) {
                        "Listening to melody... (${_recordingDurationSeconds.value}s)"
                    } else {
                        "Listening to music... (${_recordingDurationSeconds.value}s)"
                    }
                }
                false
            }
        } catch (e: Exception) {
            _recognizedMessage.value = "Could not recognize audio. Try again."
            withContext(Dispatchers.Main) {
                com.cubicreates.unboundmusic.util.UnboundToast.show(
                    getApplication(),
                    "Could not recognize audio. Try again."
                )
            }
        } finally {
            _isListeningShazam.value = false
            tickerJob.cancel()
            _recordingDurationSeconds.value = 0
        }
    }
}

internal suspend fun MainViewModel.parseAndSetShazamResult(resp: String): Boolean {
    return try {
        val json = JSONObject(resp)
        val matched = json.optBoolean("matched", false)
        val trackTitle = json.optString("title", json.optString("track_title", ""))
        val artist = json.optString("artist", "")
        if (matched && trackTitle.isNotBlank()) {
            val trackId = json.optString("id", json.optString("track_id", "shazam_${System.currentTimeMillis()}"))
            val cover = json.optString("cover_url", json.optString("thumbnail", ""))
            val matchedTrack = TrackItem(
                id = trackId,
                title = trackTitle,
                artist = artist,
                album = json.optString("album", ""),
                durationMs = json.optLong("duration_ms", 0L),
                coverUrl = cover,
                source = "shazam"
            )
            _lastRecognizedTrack.value = matchedTrack
            _shazamHistory.value = listOf(matchedTrack) + _shazamHistory.value.filter { it.title != trackTitle || it.artist != artist }

            // Parse backend-provided disambiguation variants (Radar Match vs Vibe AI Original)
            val variantsJson = json.optJSONArray("variants")
            val parsedVariants = mutableListOf<RecognizedTrackVariant>()
            if (variantsJson != null && variantsJson.length() > 0) {
                for (i in 0 until variantsJson.length()) {
                    val vObj = variantsJson.optJSONObject(i) ?: continue
                    val vId = vObj.optString("id", trackId)
                    val vTitle = vObj.optString("title", trackTitle)
                    val vArtist = vObj.optString("artist", artist)
                    val vAlbum = vObj.optString("album", "")
                    val vCover = vObj.optString("cover_url", cover)
                    val vDuration = vObj.optLong("duration_ms", matchedTrack.durationMs)
                    val vBadge = vObj.optString("badge", if (i == 0) "Acoustic Radar Match" else "Vibe AI Original Suggestion")
                    val isOrig = vObj.optBoolean("is_original", i > 0)
                    val isRadar = vObj.optBoolean("is_radar_match", i == 0)
                    val expl = vObj.optString("explanation", "")

                    val vTrack = TrackItem(
                        id = vId,
                        title = vTitle,
                        artist = vArtist,
                        album = vAlbum,
                        coverUrl = vCover,
                        durationMs = vDuration,
                        source = if (isOrig) "youtube" else "shazam"
                    )
                    parsedVariants.add(
                        RecognizedTrackVariant(
                            track = vTrack,
                            badge = vBadge,
                            isOriginal = isOrig,
                            isRadarMatch = isRadar,
                            explanation = expl
                        )
                    )
                }
            }

            // If backend returned single/no variants, perform client-side Edge AI remix disambiguation
            if (parsedVariants.size <= 1) {
                val parenRegex = Regex("""(?i)\s*[\(\[\{][^\)\]\}]*(?:remix|mix|edit|acoustic|cover|slowed|reverb|sped up|speed up|bootleg|flip|vip|mashup|live|tribute|orchestral|version|instrumental|feat\.?|ft\.?|featuring)[^\)\]\}]*[\)\]\}]""")
                val dashRegex = Regex("""(?i)\s*-\s*.*(?:remix|mix|edit|acoustic|cover|slowed|reverb|sped up|speed up|bootleg|flip|vip|mashup|live|tribute|version|instrumental).*""")
                val featRegex = Regex("""(?i)\s+(?:feat\.?|ft\.?|featuring|vs\.?|x|&)\s+.*""")

                val cleanTitle = trackTitle.replace(parenRegex, "").replace(dashRegex, "").trim().ifBlank { trackTitle }
                val cleanArtist = artist.replace(parenRegex, "").replace(dashRegex, "").replace(featRegex, "").trim().ifBlank { artist }

                val remixIndicator = Regex("""(?i)\b(remix|mix|club mix|radio edit|acoustic|cover|slowed|reverb|sped up|speed up|bootleg|flip|vip|mashup|live|tribute|orchestral|version|instrumental|karaoke|lofi)\b""")
                val isRemix = remixIndicator.containsMatchIn(trackTitle) || remixIndicator.containsMatchIn(artist) || !cleanTitle.equals(trackTitle, ignoreCase = true)

                val radarVariant = RecognizedTrackVariant(
                    track = matchedTrack,
                    badge = "Acoustic Radar Match",
                    isOriginal = !isRemix,
                    isRadarMatch = true,
                    explanation = if (!isRemix) "Identified authentic original song from audio stream" else "Identified remix or altered version from audio stream"
                )
                parsedVariants.clear()
                parsedVariants.add(radarVariant)

                if (isRemix && cleanTitle.isNotBlank()) {
                    try {
                        val (sCode, sResp) = client.search("$cleanTitle $cleanArtist", "music")
                        if (sCode in 200..299 && sResp.isNotBlank()) {
                            val results = client.parseSearchResults(sResp)
                            val originalTrack = results.firstOrNull {
                                it.itemType == "song" &&
                                !remixIndicator.containsMatchIn(it.title) &&
                                !it.id.equals(matchedTrack.id, ignoreCase = true)
                            }

                            if (originalTrack != null) {
                                parsedVariants.add(0,
                                    RecognizedTrackVariant(
                                        track = originalTrack,
                                        badge = "Canonical Original Version",
                                        isOriginal = true,
                                        isRadarMatch = false,
                                        explanation = "Edge AI resolved the authentic original recording"
                                    )
                                )
                            }
                        }
                    } catch (se: Exception) {
                        Log.w(TAG, "Client-side variant disambiguation note: ${se.message}")
                    }
                }
            }

            _recognizedVariants.value = parsedVariants

            val isEarly = json.optBoolean("early_exit", false)
            val durationMs = json.optInt("duration_ms", 0)
            val displayMsg = if (isEarly && durationMs > 0) {
                "Early Match (${durationMs}ms): $trackTitle"
            } else if (parsedVariants.size > 1) {
                "Matched: $trackTitle (Vibe AI found ${parsedVariants.size} versions)"
            } else if (artist.isNotBlank()) {
                "Recognized: $trackTitle - $artist"
            } else {
                "Recognized: $trackTitle"
            }
            _recognizedMessage.value = displayMsg
            withContext(Dispatchers.Main) {
                com.cubicreates.unboundmusic.util.UnboundToast.show(
                    getApplication(),
                    displayMsg,
                    isLong = true
                )
            }
            true
        } else {
            false
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error parsing Shazam match: ${e.message}", e)
        false
    }
}

fun MainViewModel.triggerFallbackSearch(title: String, artist: String = "", forceSwarm: Boolean = false) {
    viewModelScope.launch(Dispatchers.IO) {
        val app = getApplication<Application>()

        // 1. Guard against battery depletion on low charge
        if (!forceSwarm) {
            try {
                val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
                val batteryPct = if (level >= 0 && scale > 0) (level * 100) / scale else 100

                if (!isCharging && batteryPct < 15) {
                    _fallbackStatus.value = FallbackStatusDto(
                        stage = "PAUSED_BATTERY",
                        message = "P2P swarming paused to conserve battery (${batteryPct}%)"
                    )
                    return@launch
                }
            } catch (e: Exception) {
                Log.w(TAG, "Battery check warning: ${e.message}")
            }

            // 2. Guard against high cellular data usage on metered networks
            try {
                val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                val isMetered = cm?.isActiveNetworkMetered == true
                if (isMetered) {
                    val prefs = app.getSharedPreferences("unbound_fallback_prefs", Context.MODE_PRIVATE)
                    val allowCellularP2P = prefs.getBoolean("allow_cellular_p2p", false)
                    if (!allowCellularP2P) {
                        _fallbackStatus.value = FallbackStatusDto(
                            stage = "PAUSED_METERED",
                            message = "P2P swarming paused on metered mobile data"
                        )
                        return@launch
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Connectivity check warning: ${e.message}")
            }
        }

        _fallbackStatus.value = FallbackStatusDto(
            stage = "VERIFYING",
            message = "Verifying '$title' on Spotify and music registries..."
        )
        try {
            val (code, json) = client.resolveFallbackTrack(title, artist)
            if (code == 200 && json.isNotBlank()) {
                val status = client.parseFallbackStatus(json)
                _fallbackStatus.value = status
                if (status != null && status.stage == "STREAMING") {
                    withContext(Dispatchers.Main) {
                        com.cubicreates.unboundmusic.util.UnboundToast.show(
                            getApplication(),
                            "Fallback stream found via P2P (${status.sourceFormat ?: "Audio"})",
                            isLong = true
                        )
                    }
                }
            } else {
                _fallbackStatus.value = FallbackStatusDto(
                    stage = "NOT_FOUND",
                    message = "Track could not be resolved on fallback networks."
                )
            }
        } catch (e: Exception) {
            _fallbackStatus.value = FallbackStatusDto(
                stage = "ERROR",
                message = "Fallback resolution error: ${e.message}"
            )
        }
    }
}

fun MainViewModel.setAllowCellularP2P(enabled: Boolean) {
    val prefs = getApplication<Application>().getSharedPreferences("unbound_fallback_prefs", Context.MODE_PRIVATE)
    prefs.edit().putBoolean("allow_cellular_p2p", enabled).apply()
}

fun MainViewModel.isAllowCellularP2P(): Boolean {
    val prefs = getApplication<Application>().getSharedPreferences("unbound_fallback_prefs", Context.MODE_PRIVATE)
    return prefs.getBoolean("allow_cellular_p2p", false)
}

fun MainViewModel.clearFallbackStatus() {
    _fallbackStatus.value = null
}
