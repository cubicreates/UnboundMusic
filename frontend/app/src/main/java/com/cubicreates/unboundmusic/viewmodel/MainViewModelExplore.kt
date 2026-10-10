/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelExplore.kt
 * Purpose: Home feed recommendations, genre exploration, artist profile discography, curated mixes, and Year in Music Recap.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.service.StorageInitializer
import com.cubicreates.unboundmusic.ui.album.AlbumPlaylistData
import com.cubicreates.unboundmusic.ui.artist.ArtistAlbumItem
import com.cubicreates.unboundmusic.ui.artist.ArtistProfileData
import com.cubicreates.unboundmusic.ui.artist.SimilarArtistItem
import com.cubicreates.unboundmusic.ui.components.TrackItem
import com.cubicreates.unboundmusic.ui.components.defaultTopTracks
import com.cubicreates.unboundmusic.ui.recap.RecapData
import com.cubicreates.unboundmusic.util.GeoLocationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

private const val TAG = "MainViewModelExplore"

internal fun MainViewModel.loadHomeFeed() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            // Load Moods & Moments
            val (mCode, mResp) = client.getExploreMoods()
            if (mCode in 200..299 && mResp.isNotBlank()) {
                val json = JSONObject(mResp)
                val cats = json.optJSONArray("categories")
                if (cats != null) {
                    val parsed = mutableListOf<MoodCategory>()
                    for (i in 0 until cats.length()) {
                        val cat = cats.getJSONObject(i)
                        parsed.add(MoodCategory(
                            title = cat.optString("title", ""),
                            description = cat.optString("description", ""),
                            color = cat.optString("color", "#4CD6FB")
                        ))
                    }
                    _moodCategories.value = parsed
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Home feed load note: ${e.message}")
        }

        try {
            // Load Top Charts
            val (cCode, cResp) = client.getExploreCharts()
            if (cCode in 200..299 && cResp.isNotBlank()) {
                val json = JSONObject(cResp)
                val tracks = json.optJSONArray("tracks") ?: json.optJSONArray("chart")
                if (tracks != null) {
                    val parsed = mutableListOf<TrackItem>()
                    for (i in 0 until minOf(tracks.length(), 20)) {
                        val t = tracks.getJSONObject(i)
                        val vId = t.optString("id").ifBlank { t.optString("video_id", "") }
                        parsed.add(TrackItem(
                            id = vId,
                            title = t.optString("title", ""),
                            artist = t.optString("artist", ""),
                            coverUrl = t.optString("thumbnail_url", ""),
                            streamUrl = ""
                        ))
                    }
                    _chartTracks.value = parsed
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Charts load note: ${e.message}")
        }

        // Load native YouTube-esque algorithmic shelves
        loadSmartFeed()
    }
}

/**
 * Loads dynamic algorithmic recommendation shelves from local listening history and song seeds.
 */
fun MainViewModel.loadSmartFeed() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getSmartFeed()
            if (code in 200..299 && resp.isNotBlank()) {
                val feed = client.parseSmartFeed(resp)
                if (feed != null && feed.hasPersonalization) {
                    _smartShelves.value = feed.shelves
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Smart feed load note: ${e.message}")
        }
    }
}

fun MainViewModel.playMoodCapsule(capsule: MoodCapsule) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val tracks = client.getMoodRadio(capsule.browseId)
            if (tracks.isNotEmpty()) {
                playTrack(tracks.first())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play mood capsule: ${e.message}")
        }
    }
}

fun MainViewModel.loadMoodsAndGenres(countryCode: String? = null, langCode: String? = null) {
    val actualCountry = if (!countryCode.isNullOrBlank()) countryCode else GeoLocationProvider.getCountryCode(getApplication())
    val actualLang = if (!langCode.isNullOrBlank()) langCode else GeoLocationProvider.getLanguageCode()
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getMoodsAndGenres(actualCountry, actualLang)
            if (code in 200..299 && resp.isNotBlank()) {
                val sections = client.parseMoodsAndGenres(resp)
                if (sections.isNotEmpty()) {
                    _genreSections.value = sections
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to load moods and genres: ${e.message}")
        }
    }
}

fun MainViewModel.loadGenreDetail(params: String, title: String, countryCode: String? = null, langCode: String? = null) {
    val actualCountry = if (!countryCode.isNullOrBlank()) countryCode else GeoLocationProvider.getCountryCode(getApplication())
    val actualLang = if (!langCode.isNullOrBlank()) langCode else GeoLocationProvider.getLanguageCode()
    _selectedGenreTitle.value = title
    _isLoadingGenreDetail.value = true
    _activeGenreShelves.value = emptyList()
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getGenreDetail(params, title, actualCountry, actualLang)
            if (code in 200..299 && resp.isNotBlank()) {
                val shelves = client.parseGenreDetail(resp)
                _activeGenreShelves.value = shelves
            }
        } catch (e: Exception) {
            Log.d(TAG, "Failed to load genre detail: ${e.message}")
        } finally {
            _isLoadingGenreDetail.value = false
        }
    }
}

fun MainViewModel.playPlaylistItem(item: PlaylistItemDto) {
    val track = TrackItem(
        id = item.id.ifBlank { "track_" + System.currentTimeMillis() },
        title = item.title,
        artist = item.subtitle.ifBlank { _selectedGenreTitle.value },
        coverUrl = item.thumbnailUrl,
        streamUrl = "",
        source = "Genre Explore"
    )
    playTrack(track)
}

fun MainViewModel.loadArtistProfile(artistName: String) {
    if (artistName.isBlank()) return
    _artistProfile.value = null
    viewModelScope.launch(Dispatchers.IO) {
        _isLoadingArtist.value = true
        try {
            var loadedProfile: ArtistProfileData? = null
            val (code, resp) = client.getArtistProfile(artistName)
            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                val tracksArr = json.optJSONArray("top_tracks")
                val parsedTracks = mutableListOf<TrackItem>()
                if (tracksArr != null) {
                    for (i in 0 until tracksArr.length()) {
                        val t = tracksArr.getJSONObject(i)
                        val vId = t.optString("id").ifBlank { t.optString("video_id", "") }
                        parsedTracks.add(
                            TrackItem(
                                id = vId,
                                title = t.optString("title"),
                                artist = t.optString("artist", artistName),
                                coverUrl = t.optString("thumbnail_url"),
                                streamUrl = ""
                            )
                        )
                    }
                }
                val albumsArr = json.optJSONArray("albums")
                val parsedAlbums = mutableListOf<ArtistAlbumItem>()
                if (albumsArr != null) {
                    for (i in 0 until albumsArr.length()) {
                        val a = albumsArr.getJSONObject(i)
                        val aId = a.optString("id").ifBlank { a.optString("browse_id", "") }
                        parsedAlbums.add(
                            ArtistAlbumItem(
                                id = aId,
                                title = a.optString("title"),
                                year = a.optString("year"),
                                coverUrl = a.optString("cover_url", a.optString("thumbnail_url", ""))
                            )
                        )
                    }
                }
                val similarArr = json.optJSONArray("similar_artists")
                val parsedSimilar = mutableListOf<SimilarArtistItem>()
                if (similarArr != null) {
                    for (i in 0 until similarArr.length()) {
                        val s = similarArr.getJSONObject(i)
                        parsedSimilar.add(
                            SimilarArtistItem(
                                name = s.optString("name"),
                                imageUrl = s.optString("image_url", s.optString("thumbnail_url", ""))
                            )
                        )
                    }
                }
                if (parsedTracks.isNotEmpty()) {
                    loadedProfile = ArtistProfileData(
                        name = json.optString("name", artistName),
                        heroImageUrl = json.optString("hero_image_url"),
                        monthlyListeners = json.optString("monthly_listeners", "${(10..40).random()}.${(1..9).random()}M monthly listeners"),
                        bio = json.optString("bio", "Verified Artist on YouTube Music & Unbound."),
                        topTracks = parsedTracks,
                        albums = parsedAlbums,
                        similarArtists = parsedSimilar
                    )
                }
            }

            // Zero-fail fallback: If getArtistProfile returns empty or fails, resolve via search
            if (loadedProfile == null) {
                val (sCode, sResp) = client.search("$artistName top songs", type = "song")
                val fallbackTracks = if (sCode in 200..299 && sResp.isNotBlank()) {
                    client.parseSearchResults(sResp).filter {
                        !it.itemType.equals("artist", true) && !it.browseId.startsWith("UC") && it.id.length == 11
                    }
                } else emptyList()

                val heroCover = fallbackTracks.firstOrNull()?.coverUrl ?: ""
                loadedProfile = ArtistProfileData(
                    name = artistName,
                    heroImageUrl = heroCover,
                    monthlyListeners = "${(12..35).random()}.${(1..9).random()}M monthly listeners",
                    bio = "Top tracks and discography for $artistName.",
                    topTracks = fallbackTracks.take(15),
                    albums = emptyList(),
                    similarArtists = emptyList()
                )
            }

            _artistProfile.value = loadedProfile
        } catch (e: Exception) {
            Log.d(TAG, "Artist load error: ${e.message}")
        } finally {
            _isLoadingArtist.value = false
        }
    }
}

fun MainViewModel.openAlbumPlaylist(id: String, initialTitle: String = "", initialCover: String = "") {
    if (id.isBlank()) return
    _albumPlaylistData.value = AlbumPlaylistData(
        title = initialTitle.ifBlank { "Loading..." },
        subtitle = "",
        coverUrl = initialCover,
        tracks = emptyList(),
        totalDuration = ""
    )
    viewModelScope.launch(Dispatchers.IO) {
        try {
            var (code, resp) = client.getPlaylist(id)
            if (code !in 200..299 || resp.isBlank()) {
                val (aCode, aResp) = client.getAlbum(id)
                if (aCode in 200..299 && aResp.isNotBlank()) {
                    code = aCode
                    resp = aResp
                }
            }
            if (code in 200..299 && resp.isNotBlank()) {
                val parsed = client.parseAlbumPlaylist(resp)
                if (parsed != null && parsed.tracks.isNotEmpty()) {
                    _albumPlaylistData.value = parsed.toAlbumPlaylistData()
                    return@launch
                }
            }

            // Zero-fail fallback: If playlist returned 0 tracks, query catalog for tracks matching the title
            if (initialTitle.isNotBlank() && initialTitle != "Loading...") {
                val (sCode, sResp) = client.search(initialTitle, type = "song")
                if (sCode in 200..299 && sResp.isNotBlank()) {
                    val tracks = client.parseSearchResults(sResp)
                    if (tracks.isNotEmpty()) {
                        val totalMs = tracks.sumOf { it.durationMs }
                        val totalMins = if (totalMs > 0) totalMs / 60000 else (tracks.size * 3L)
                        withContext(Dispatchers.Main) {
                            _albumPlaylistData.value = AlbumPlaylistData(
                                title = initialTitle,
                                subtitle = "Curated Mix",
                                coverUrl = initialCover,
                                tracks = tracks,
                                totalDuration = "${tracks.size} tracks • $totalMins mins"
                            )
                        }
                        return@launch
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load album/playlist details: ${e.message}")
        }
    }
}

fun MainViewModel.openCuratedCollection(key: String, overrideCover: String? = null) {
    val collection = CuratedCollections.find(key)
    val title = collection?.title ?: key
    val subtitle = collection?.subtitle ?: "Curated Vibe Collection"
    val cover = overrideCover?.takeIf { it.isNotBlank() } ?: collection?.coverUrl ?: ""
    val fallbackQuery = collection?.fallbackQuery ?: "$key music hits"

    _albumPlaylistData.value = AlbumPlaylistData(
        title = title,
        subtitle = subtitle,
        coverUrl = cover,
        tracks = emptyList(),
        totalDuration = "Curating tracks..."
    )

    viewModelScope.launch(Dispatchers.IO) {
        try {
            var tracks: List<TrackItem> = emptyList()

            // Tier 1: Fetch by official curated YouTube Music playlist ID
            if (collection != null && collection.id.isNotBlank()) {
                val (code, resp) = client.getPlaylist(collection.id)
                if (code in 200..299 && resp.isNotBlank()) {
                    val parsed = client.parseAlbumPlaylist(resp)
                    if (parsed != null && parsed.tracks.isNotEmpty()) {
                        tracks = parsed.tracks
                    }
                }
            }

            // Tier 2: Zero-Fail Fallback Engine - search by curated vibe query
            if (tracks.isEmpty()) {
                val (sCode, sResp) = client.search(fallbackQuery, type = "song")
                if (sCode in 200..299 && sResp.isNotBlank()) {
                    tracks = client.parseSearchResults(sResp)
                }
                if (tracks.isEmpty()) {
                    val (allCode, allResp) = client.search(fallbackQuery, type = "all")
                    if (allCode in 200..299 && allResp.isNotBlank()) {
                        tracks = client.parseSearchResults(allResp)
                    }
                }
            }

            val totalMs = tracks.sumOf { it.durationMs }
            val totalMins = if (totalMs > 0) totalMs / 60000 else (tracks.size * 3L)
            val durationStr = if (tracks.isNotEmpty()) "${tracks.size} tracks • $totalMins mins" else "0 tracks"

            withContext(Dispatchers.Main) {
                _albumPlaylistData.value = AlbumPlaylistData(
                    title = title,
                    subtitle = subtitle,
                    coverUrl = cover,
                    tracks = tracks,
                    totalDuration = durationStr
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load curated collection '$key': ${e.message}")
        }
    }
}

fun MainViewModel.closeAlbumPlaylist() {
    _albumPlaylistData.value = null
}

fun MainViewModel.playCuratedMix(mix: MixDto) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            if (mix.id.isNotBlank()) {
                val (code, resp) = client.getPlaylist(mix.id)
                if (code in 200..299 && resp.isNotBlank()) {
                    val parsed = client.parseAlbumPlaylist(resp)
                    if (parsed != null && parsed.tracks.isNotEmpty()) {
                        withContext(Dispatchers.Main) {
                            playTrackWithQueue(parsed.tracks.first(), parsed.tracks)
                        }
                        return@launch
                    }
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Curated mix playlist fetch error, falling back: ${e.message}")
        }

        // Fallback: If mix browseId did not resolve directly, use shuffled personalized library or create an instant seed track
        withContext(Dispatchers.Main) {
            val fallbackQueue = if (_syncedYouTubeTracks.value.isNotEmpty()) {
                _syncedYouTubeTracks.value.shuffled()
            } else if (_musicTracks.value.isNotEmpty()) {
                _musicTracks.value.shuffled()
            } else if (_libraryTracks.value.isNotEmpty()) {
                _libraryTracks.value.filter { it.audioCategory == AudioCategory.MUSIC || it.isIdentifiedMusic }.shuffled().ifEmpty { _libraryTracks.value.shuffled() }
            } else {
                defaultTopTracks.shuffled()
            }
            val seedTrack = TrackItem(
                id = mix.id.ifBlank { "mix_" + System.currentTimeMillis() },
                title = mix.title,
                artist = mix.subtitle.ifBlank { "YouTube Music" },
                coverUrl = mix.coverUrl,
                streamUrl = "",
                source = "Curated Mix"
            )
            val fullQueue = listOf(seedTrack) + fallbackQueue
            playTrackWithQueue(seedTrack, fullQueue)
        }
    }
}

fun MainViewModel.loadRecap() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getRecap()
            if (code in 200..299 && resp.isNotBlank()) {
                val json = JSONObject(resp)
                _recapData.value = RecapData(
                    totalMinutes = json.optInt("total_minutes", 1420),
                    topDecade = json.optString("top_decade", "2020s"),
                    diversityScore = json.optString("diversity_score", "8.7 / 10")
                )
            }
        } catch (e: Exception) {
            Log.d(TAG, "Recap load error: ${e.message}")
        }
    }
}

fun MainViewModel.shufflePlayMusicTracks() {
    val candidates = if (_musicTracks.value.isNotEmpty()) {
        _musicTracks.value
    } else if (_syncedYouTubeTracks.value.isNotEmpty()) {
        _syncedYouTubeTracks.value
    } else {
        _libraryTracks.value.filter { it.audioCategory == AudioCategory.MUSIC || it.isIdentifiedMusic }
    }
    if (candidates.isNotEmpty()) {
        val shuffled = candidates.shuffled()
        playTrackWithQueue(shuffled.first(), shuffled)
    }
}

fun MainViewModel.refreshAdaptiveQuickPicks() {
    val history = _recentlyPlayedTracks.value
    val fallbackCharts = _regionalCharts.value.ifEmpty { _chartTracks.value }
    if (history.isEmpty()) {
        _adaptiveQuickPicks.value = fallbackCharts
        _quickPicksTitle.value = "Trending Quick Picks"
        _quickPicksSubtitle.value = "Start a radio or continuous mix"
        return
    }

    viewModelScope.launch(Dispatchers.IO) {
        try {
            val candidateList = mutableListOf<TrackItem>()
            val seenIds = mutableSetOf<String>()
            val seenTitles = mutableSetOf<String>()

            fun addPick(t: TrackItem): Boolean {
                if (t.title.isBlank()) return false
                val clean = t.title.lowercase().trim()
                if (t.id.isNotBlank() && seenIds.contains(t.id)) return false
                if (seenTitles.contains(clean)) return false
                if (t.id.isNotBlank()) seenIds.add(t.id)
                seenTitles.add(clean)
                candidateList.add(t)
                return true
            }

            // 1. Gather recent favorite artists (up to 3 distinct artists)
            val recentArtists = history.map { it.artist.trim() }
                .filter { it.isNotBlank() && !it.equals("Unknown Artist", ignoreCase = true) }
                .distinct()
                .take(3)

            for (art in recentArtists) {
                val (code, resp) = client.search("$art song", type = "song")
                if (code in 200..299 && resp.isNotBlank()) {
                    val songs = client.parseSearchResults(resp)
                    var count = 0
                    for (s in songs) {
                        if (count >= 2) break
                        if (addPick(s)) count++
                    }
                }
            }

            // 2. Gather algorithmic genre / radio continuum from most recent track
            val seed = history.firstOrNull()
            if (seed != null && seed.id.isNotBlank() && !seed.id.startsWith("local:")) {
                val (code, resp) = client.getRadioNext(seed.id)
                if (code in 200..299 && resp.isNotBlank()) {
                    val radioSongs = client.parseRadioNext(resp)
                    var rCount = 0
                    for (rs in radioSongs) {
                        if (rCount >= 6) break
                        if (addPick(rs)) rCount++
                    }
                }
            }

            // 3. Blend in trending chart tracks for novelty and discovery balance
            for (ch in fallbackCharts) {
                if (candidateList.size >= 16) break
                addPick(ch)
            }

            if (candidateList.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    _adaptiveQuickPicks.value = candidateList.take(24)
                    _quickPicksTitle.value = "Quick Picks for You"
                    _quickPicksSubtitle.value = "Personalized from your recent listening & discoveries"
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Adaptive quick picks refresh note: ${e.message}")
        }
    }
}

fun MainViewModel.initializeColdStart(countryCode: String? = null, languageCode: String? = null) {
    val actualCountry = if (!countryCode.isNullOrBlank()) countryCode else GeoLocationProvider.getCountryCode(getApplication())
    val actualLanguage = if (!languageCode.isNullOrBlank()) languageCode else GeoLocationProvider.getLanguageCode()
    viewModelScope.launch(Dispatchers.IO) {
        // First-boot asset unpacker
        StorageInitializer.initialize(getApplication())

        // 1. Fetch explore charts & mood capsules concurrently
        launch {
            val charts = client.getCharts(actualCountry, actualLanguage)
            if (charts.isNotEmpty()) {
                _regionalCharts.value = charts
                _chartTracks.value = charts
                if (_recentlyPlayedTracks.value.isEmpty()) {
                    _adaptiveQuickPicks.value = charts
                }
            }
        }
        launch {
            val dp = client.getMoodCapsules()
            if (dp != null) {
                _daypartingState.value = dp
            }
        }

        // 2. Trigger VLC-style universal storage crawl (including .nomedia bypass)
        launch {
            performUniversalAudioScan()
        }
    }
}
