/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelRadio.kt
 * Purpose: Markov & InnerTube Algorithmic Radio, Hierarchical Affinity Queue, and Automix engine.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Calendar

private const val TAG = "MainViewModelRadio"

/**
 * Sanitizes and normalizes song titles by stripping off YouTube metadata annotations,
 * video descriptors (Official Video, Lyric Video, Audio), featured artists, and punctuation.
 */
internal fun cleanSongTitle(raw: String): String {
    if (raw.isBlank()) return ""
    var title = raw
    val splitRegex = Regex("""\s*[-–—:]\s*""")
    if (splitRegex.containsMatchIn(title)) {
        val parts = title.split(splitRegex)
        if (parts.size >= 2 && parts[0].length <= 35) {
            title = parts.drop(1).joinToString(" - ")
        }
    }
    return title
        .replace(Regex("""(?i)\(official\s*(music)?\s*video\)"""), "")
        .replace(Regex("""(?i)\[official\s*(music)?\s*video\]"""), "")
        .replace(Regex("""(?i)\(lyric\s*video\)"""), "")
        .replace(Regex("""(?i)\[lyric\s*video\]"""), "")
        .replace(Regex("""(?i)\(official\s*audio\)"""), "")
        .replace(Regex("""(?i)\[official\s*audio\]"""), "")
        .replace(Regex("""(?i)\(full\s*audio\)"""), "")
        .replace(Regex("""(?i)\[full\s*audio\]"""), "")
        .replace(Regex("""(?i)\(audio\)"""), "")
        .replace(Regex("""(?i)\[audio\]"""), "")
        .replace(Regex("""(?i)\(visualizer\)"""), "")
        .replace(Regex("""(?i)\[visualizer\]"""), "")
        .replace(Regex("""(?i)\(video\)"""), "")
        .replace(Regex("""(?i)\(live[^\)]*\)"""), "")
        .replace(Regex("""(?i)\[live[^\]]*\]"""), "")
        .replace(Regex("""(?i)\(acoustic[^\)]*\)"""), "")
        .replace(Regex("""(?i)\[acoustic[^\]]*\]"""), "")
        .replace(Regex("""(?i)\(remix[^\)]*\)"""), "")
        .replace(Regex("""(?i)\[remix[^\]]*\]"""), "")
        .replace(Regex("""(?i)\(slowed[^\)]*\)"""), "")
        .replace(Regex("""(?i)\[slowed[^\]]*\]"""), "")
        .replace(Regex("""(?i)\(reverb[^\)]*\)"""), "")
        .replace(Regex("""(?i)\[reverb[^\]]*\]"""), "")
        .replace(Regex("""(?i)\(feat[^\)]*\)"""), "")
        .replace(Regex("""(?i)\[feat[^\]]*\]"""), "")
        .replace(Regex("""(?i)\(from\s+[^)]+\)"""), "")
        .replace(Regex("""(?i)\[from\s+[^\]]+\]"""), "")
        .replace(Regex("""[^\p{L}\p{Nd}\s]"""), " ")
        .replace(Regex("""\s+"""), " ")
        .lowercase()
        .trim()
}

/**
 * Determines whether two TrackItems represent the exact same musical song or versions of it.
 */
internal fun isSameSong(a: TrackItem, b: TrackItem): Boolean {
    if (a.id.isNotBlank() && b.id.isNotBlank() && a.id == b.id) return true
    val titleA = cleanSongTitle(a.title)
    val titleB = cleanSongTitle(b.title)
    if (titleA.isBlank() || titleB.isBlank()) return false
    if (titleA == titleB) return true
    if (titleA.length >= 4 && titleB.length >= 4) {
        if (titleA.contains(titleB) || titleB.contains(titleA)) return true
    }
    return false
}

/**
 * Cleans up channel noise from artist names (e.g. " - Topic", "VEVO", "Official").
 */
internal fun extractArtistName(track: TrackItem): String {
    var rawArtist = track.artist.trim()
    if (rawArtist.isBlank() || rawArtist.equals("Unknown Artist", ignoreCase = true) || rawArtist.equals("Unknown", ignoreCase = true)) {
        val splitRegex = Regex("""\s*[-–—:]\s*""")
        if (splitRegex.containsMatchIn(track.title)) {
            val parts = track.title.split(splitRegex)
            if (parts.size >= 2) {
                val candidate = parts[0].trim()
                if (candidate.isNotBlank() && candidate.length <= 40) {
                    rawArtist = candidate
                }
            }
        }
    }
    return rawArtist
        .replace(Regex("""(?i)\s*-\s*topic"""), "")
        .replace(Regex("""(?i)\s*official"""), "")
        .replace(Regex("""(?i)\s*vevo"""), "")
        .replace(Regex("""(?i)\s*records"""), "")
        .replace(Regex("""(?i)\s*music"""), "")
        .replace(Regex("""(?i)\s*channel"""), "")
        .trim()
}

/**
 * Extracts movie, album, or soundtrack context from track metadata and title annotations.
 */
internal fun extractMovieOrAlbumContext(track: TrackItem): String? {
    val album = track.album.trim()
    if (album.isNotBlank() &&
        !album.equals(track.title, ignoreCase = true) &&
        !album.equals("Single", ignoreCase = true) &&
        !album.equals("Unknown Album", ignoreCase = true)
    ) {
        return album
    }
    val title = track.title
    val movieRegexes = listOf(
        Regex("""(?i)\(From\s+["']?([^"'\)]+)["']?\)"""),
        Regex("""(?i)\[From\s+["']?([^"'\]]+)["']?\]"""),
        Regex("""(?i)-\s*From\s+["']?([^"'\-]+)["']?"""),
        Regex("""(?i)\(Soundtrack\s+from\s+["']?([^"'\)]+)["']?\)"""),
        Regex("""(?i)(?:OST|Soundtrack)\s*[:\-]\s*([^\(\)]+)""")
    )
    for (regex in movieRegexes) {
        val match = regex.find(title)
        if (match != null && match.groupValues.size > 1) {
            val extracted = match.groupValues[1].trim()
            if (extracted.isNotBlank()) return extracted
        }
    }
    return null
}

/**
 * Builds a 50-track hierarchical affinity queue matching the user's affinity architecture:
 * Tier 1: Seed track
 * Tier 2: 1-2 tracks by the same artist (excluding the seed track)
 * Tier 3: 3-5 tracks from the same movie / soundtrack / album (if applicable, excluding seed track)
 * Tier 4: 15-20 tracks from the same genre / sonic acoustic profile (excluding seed track)
 * Tier 5: Algorithmic serendipity & discovery tracks to complete a 50-track queue (excluding seed track)
 */
suspend fun MainViewModel.buildHierarchicalAffinityQueue(seedTrack: TrackItem): List<TrackItem> {
    val result = mutableListOf<TrackItem>()
    val seenIds = mutableSetOf<String>()
    val seenTitles = mutableSetOf<String>()
    val cleanArtist = extractArtistName(seedTrack)

    fun isNonPlayableEntity(item: TrackItem): Boolean {
        if (item.itemType.equals("artist", ignoreCase = true) ||
            item.itemType.equals("channel", ignoreCase = true) ||
            item.itemType.equals("playlist", ignoreCase = true) ||
            item.itemType.equals("album", ignoreCase = true)) {
            return true
        }
        if (item.browseId.startsWith("UC") || item.id.startsWith("UC")) return true
        if (item.id.startsWith("MPREb_") || item.id.startsWith("VL") || item.id.startsWith("PL")) return true
        if (item.browseId.startsWith("MPREb_") || item.browseId.startsWith("VL") || item.browseId.startsWith("PL")) return true
        if (item.id.length != 11 && !item.id.startsWith("local:")) return true
        if (cleanArtist.isNotBlank() && item.title.trim().equals(cleanArtist.trim(), ignoreCase = true)) return true
        return false
    }

    fun addTrack(t: TrackItem, allowSeedDuplicate: Boolean = false): Boolean {
        if (t.title.isBlank()) return false
        if (!allowSeedDuplicate && isSameSong(t, seedTrack)) return false
        if (!allowSeedDuplicate && isNonPlayableEntity(t)) return false
        val clean = cleanSongTitle(t.title)
        if (clean.isBlank()) return false
        if (t.id.isNotBlank() && seenIds.contains(t.id)) return false
        if (seenTitles.contains(clean)) return false
        if (seenTitles.any { it.length >= 4 && clean.length >= 4 && (it.contains(clean) || clean.contains(it)) }) {
            return false
        }
        if (t.id.isNotBlank()) seenIds.add(t.id)
        seenTitles.add(clean)
        result.add(t)
        return true
    }

    // Tier 0: Seed track itself (always first in queue)
    addTrack(seedTrack, allowSeedDuplicate = true)

    // 1. Tier 1: Same Artist Popular Tracks (1-2 other tracks by same artist)
    if (cleanArtist.isNotBlank() && !cleanArtist.equals("Unknown Artist", ignoreCase = true)) {
        try {
            var artistTracks = emptyList<TrackItem>()
            val (code, resp) = client.getArtistProfile(cleanArtist)
            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                val tracksArr = json.optJSONArray("top_tracks")
                if (tracksArr != null) {
                    val parsed = mutableListOf<TrackItem>()
                    for (i in 0 until minOf(tracksArr.length(), 10)) {
                        val obj = tracksArr.getJSONObject(i)
                        val vId = obj.optString("id").ifBlank { obj.optString("video_id", "") }
                        parsed.add(
                            TrackItem(
                                id = vId,
                                title = obj.optString("title"),
                                artist = obj.optString("artist", cleanArtist),
                                coverUrl = obj.optString("thumbnail_url"),
                                streamUrl = ""
                            )
                        )
                    }
                    artistTracks = parsed
                }
            }
            if (artistTracks.isEmpty()) {
                val (sCode, sResp) = client.search("$cleanArtist top songs", type = "song")
                if (sCode in 200..299 && sResp.isNotBlank()) {
                    artistTracks = client.parseSearchResults(sResp)
                }
            }
            var artistCount = 0
            for (t in artistTracks) {
                if (artistCount >= 2) break
                if (addTrack(t)) artistCount++
            }
        } catch (e: Exception) {
            Log.d(TAG, "Hierarchical Tier 1 (Artist) note: ${e.message}")
        }
    }

    // 2. Tier 2: Same Movie / Soundtrack / Album (3-5 tracks)
    val movieOrAlbum = extractMovieOrAlbumContext(seedTrack)
    if (!movieOrAlbum.isNullOrBlank()) {
        try {
            val searchQuery = "$movieOrAlbum soundtrack songs"
            val (code, resp) = client.search(searchQuery, type = "song")
            if (code in 200..299 && resp.isNotBlank()) {
                val albumTracks = client.parseSearchResults(resp)
                var albumCount = 0
                for (t in albumTracks) {
                    if (albumCount >= 4) break
                    if (addTrack(t)) albumCount++
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Hierarchical Tier 2 (Movie/Album) note: ${e.message}")
        }
    }

    // 3. Tier 3: Same Genre & Acoustic Vibe (15-20 tracks)
    val cleanId = if (!seedTrack.id.startsWith("local:")) seedTrack.id else ""
    var genreTracks = emptyList<TrackItem>()
    if (cleanId.isNotBlank()) {
        try {
            val (code, resp) = client.getRadioNext(cleanId)
            if (code in 200..299 && resp.isNotBlank()) {
                genreTracks = client.parseRadioNext(resp)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Hierarchical Tier 3 (RadioNext) note: ${e.message}")
        }
    }
    if (genreTracks.isEmpty()) {
        try {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val magicResult = client.getMagicRadio(localHour = hour, seedTrackId = cleanId)
            if (magicResult != null && magicResult.queue.isNotEmpty()) {
                genreTracks = magicResult.queue
            }
        } catch (_: Exception) {}
    }
    if (genreTracks.isEmpty() && cleanArtist.isNotBlank()) {
        try {
            val (code, resp) = client.search("$cleanArtist radio mix", type = "song")
            if (code in 200..299 && resp.isNotBlank()) {
                genreTracks = client.parseSearchResults(resp)
            }
        } catch (_: Exception) {}
    }
    for (t in genreTracks) {
        if (result.size >= 28) break
        addTrack(t)
    }

    // 4. Tier 4: Algorithmic Expansion / Fresh Discoveries up to 50 tracks
    if (result.size < 50) {
        val discoveryAnchor = result.drop(1).firstOrNull { 
            it.artist.isNotBlank() && !it.artist.equals(cleanArtist, ignoreCase = true) && it.id.isNotBlank() && !it.id.startsWith("local:")
        } ?: result.lastOrNull()

        if (discoveryAnchor != null && discoveryAnchor.id.isNotBlank() && !discoveryAnchor.id.startsWith("local:")) {
            try {
                val (code, resp) = client.getRadioNext(discoveryAnchor.id)
                if (code in 200..299 && resp.isNotBlank()) {
                    val anchorRadio = client.parseRadioNext(resp)
                    for (t in anchorRadio) {
                        if (result.size >= 50) break
                        addTrack(t)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    if (result.size < 50 && cleanArtist.isNotBlank()) {
        try {
            val (code, resp) = client.search("$cleanArtist mix", type = "song")
            if (code in 200..299 && resp.isNotBlank()) {
                val mixTracks = client.parseSearchResults(resp)
                for (t in mixTracks) {
                    if (result.size >= 50) break
                    addTrack(t)
                }
            }
        } catch (_: Exception) {}
    }

    return result
}

/**
 * Strategy A: Dynamic YouTube Algorithmic Radio Graph (Guest Mode Only).
 */
fun MainViewModel.fetchGuestAlgorithmicRadio(seedTrack: TrackItem, append: Boolean = false) {
    if (_isYouTubeConnected.value) return // Strictly guest mode only
    if (seedTrack.id.isBlank() && seedTrack.title.isBlank()) return

    guestRadioJob?.cancel()
    guestRadioJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            Log.d(TAG, "Guest Algorithmic Radio: Building hierarchical affinity queue for '${seedTrack.title}'...")
            val hierarchicalTracks = buildHierarchicalAffinityQueue(seedTrack)

            if (hierarchicalTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    if (!_isYouTubeConnected.value) {
                        val updatedQueue = if (append) {
                            val existingIds = _currentQueue.value.map { it.id }.toSet()
                            _currentQueue.value + hierarchicalTracks.filter { !existingIds.contains(it.id) }
                        } else {
                            hierarchicalTracks
                        }
                        _currentQueue.value = updatedQueue
                        serviceConnection.setQueue(updatedQueue)
                        if (!append) {
                            val vibeLabel = seedTrack.artist.ifBlank { "Algorithmic" }
                            com.cubicreates.unboundmusic.util.UnboundToast.show(
                                getApplication(),
                                "Radio: Shifted to $vibeLabel vibe (${updatedQueue.size} tracks)",
                                isLong = false
                            )
                        }
                        Log.i(TAG, "Guest Algorithmic Radio: Shifted queue to ${updatedQueue.size} tracks for '${seedTrack.title}'")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Guest Algorithmic Radio error: ${e.message}")
        }
    }
}

/**
 * Queries automix algorithm to populate 50 hierarchical affinity matched songs into active player queue.
 */
fun MainViewModel.fetchAutomixQueue(seedTrack: TrackItem, append: Boolean = false) {
    if (seedTrack.id.isBlank() || seedTrack.id.startsWith("local:")) return
    automixJob?.cancel()
    automixJob = viewModelScope.launch(Dispatchers.IO) {
        try {
            Log.d(TAG, "Automix: Building hierarchical affinity queue for '${seedTrack.title}'...")
            val hierarchicalTracks = buildHierarchicalAffinityQueue(seedTrack)

            if (hierarchicalTracks.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    val updatedQueue = if (append) {
                        val existingIds = _currentQueue.value.map { it.id }.toSet()
                        _currentQueue.value + hierarchicalTracks.filter { !existingIds.contains(it.id) }
                    } else {
                        hierarchicalTracks
                    }
                    _currentQueue.value = updatedQueue
                    serviceConnection.setQueue(updatedQueue)
                    Log.i(TAG, "Automix queue hydrated with ${updatedQueue.size} tracks for '${seedTrack.title}'")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch automix queue for ${seedTrack.id}: ${e.message}")
        }
    }
}

fun MainViewModel.triggerMagicRadio(onReady: () -> Unit) {
    val now = System.currentTimeMillis()
    if (now - lastMagicRadioTriggerTime < 500L) return
    lastMagicRadioTriggerTime = now

    viewModelScope.launch(Dispatchers.IO) {
        magicRadioMutex.withLock {
            try {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                val result = client.getMagicRadio(localHour = hour)
                if (result != null && result.queue.isNotEmpty()) {
                    withContext(Dispatchers.Main) {
                        serviceConnection.setQueue(result.queue)
                        playTrack(result.seedTrack)
                        onReady()
                    }
                    return@withLock
                }
            } catch (e: Exception) {
                Log.e(TAG, "Magic radio error: ${e.message}")
            }

            // Resilient Fallback: If daemon magic radio didn't produce a queue, use libraryTracks or defaultTopTracks
            val candidateList = _libraryTracks.value.ifEmpty { defaultTopTracks }
            if (candidateList.isNotEmpty()) {
                val shuffled = candidateList.shuffled()
                val seed = shuffled.first()
                withContext(Dispatchers.Main) {
                    serviceConnection.setQueue(shuffled)
                    playTrack(seed)
                    onReady()
                }
            }
        }
    }
}

/**
 * Starts an on-demand Magic Serendipity Radio session seeded by the specified track.
 */
fun MainViewModel.startRadio(seedTrack: TrackItem, onStarted: (() -> Unit)? = null) {
    if (seedTrack.id.isBlank() && seedTrack.title.isBlank()) return
    if (!_isYouTubeConnected.value) {
        playTrack(seedTrack)
        fetchGuestAlgorithmicRadio(seedTrack, append = false)
        onStarted?.invoke()
        return
    }
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val cleanId = if (!seedTrack.id.startsWith("local:")) seedTrack.id else ""

            // 1. Query Go daemon on-device Markov Serendipity Radio generator
            val result = client.getMagicRadio(localHour = hour, seedTrackId = cleanId)
            var radioQueue = result?.queue?.filter { it.id != seedTrack.id } ?: emptyList()

            // 2. Fallback to InnerTube /next
            if (radioQueue.isEmpty() && cleanId.isNotBlank()) {
                val (code, resp) = client.getRadioNext(cleanId)
                if (code in 200..299 && resp.isNotBlank()) {
                    radioQueue = client.parseRadioNext(resp).filter { it.id != seedTrack.id }
                }
            }

            // 3. Fallback to YouTube search radio query
            if (radioQueue.isEmpty()) {
                val query = "${seedTrack.artist} ${seedTrack.title} radio"
                val (sCode, sResp) = client.search(query, type = "song")
                if (sCode in 200..299 && sResp.isNotBlank()) {
                    radioQueue = client.parseSearchResults(sResp).filter { it.id != seedTrack.id }
                }
            }

            val fullQueue = listOf(seedTrack) + radioQueue
            withContext(Dispatchers.Main) {
                playTrackWithQueue(seedTrack, fullQueue)
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Radio: ${seedTrack.title}")
                onStarted?.invoke()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting radio: ${e.message}")
            withContext(Dispatchers.Main) {
                playTrack(seedTrack)
                onStarted?.invoke()
            }
        }
    }
}
