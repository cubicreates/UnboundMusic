/*
 * Package: com.cubicreates.unboundmusic.data
 * File: BackendClient.kt
 * Purpose: Production HTTP REST client for Unbound Music connecting directly to the embedded Go Engine daemon.
 *          Covers ALL 44+ endpoints exposed by the Go daemon across search, streaming, lyrics, canvas,
 *          analytics, autoeq, storage, downloads, explore, artist, playlists, and social features.
 * Subsystem: Native Go Engine REST Client
 * Concurrency: Non-blocking I/O operations executed on caller coroutine / Dispatchers.IO.
 */

package com.cubicreates.unboundmusic.data

import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Singleton HTTP client communicating with the embedded Go engine daemon at 127.0.0.1:45731.
 * All methods return Pair<statusCode, responseBody> for uniform error handling.
 */
class BackendClient(private val baseUrl: String = "http://127.0.0.1:45731") {

    // ==================== SECTION 1: System Health & Storage ====================

    /** Verifies daemon is online, returns engine version, storage mode, RAM, goroutines. */
    suspend fun getStatus(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/status")
    }

    /** Alias for getStatus() - backward compatibility with DaemonManager health polling. */
    suspend fun healthCheck(): Pair<Int, String> = getStatus()


    /** Returns the provisioned Unbound/ directory tree structure. */
    suspend fun getStorageTree(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/storage/tree")
    }

    // ==================== SECTION 2: Search & Streaming ====================

    /** Searches YouTube Music catalog. Returns track list with videoId, title, artist, album, duration, thumbnail. */
    suspend fun search(query: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        get("/api/v1/search?q=$encoded")
    }

    /** Resolves a direct audio stream URL for a given video ID (or title+artist for zero-data interception). */
    suspend fun getStream(
        videoId: String = "",
        title: String = "",
        artist: String = ""
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val params = mutableListOf<String>()
        if (videoId.isNotBlank()) params.add("id=${URLEncoder.encode(videoId, "UTF-8")}")
        if (title.isNotBlank()) params.add("title=${URLEncoder.encode(title, "UTF-8")}")
        if (artist.isNotBlank()) params.add("artist=${URLEncoder.encode(artist, "UTF-8")}")
        get("/api/v1/stream?${params.joinToString("&")}")
    }

    // ==================== SECTION 3: Lyrics & Alignment ====================

    /** Fetches uncensored lyrics with syllable-level timestamps from Genius + CTC aligner. */
    suspend fun getLyrics(
        trackId: String = "",
        title: String = "",
        artist: String = "",
        durationMs: Long = 0
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        val params = mutableListOf<String>()
        if (trackId.isNotBlank()) params.add("id=${URLEncoder.encode(trackId, "UTF-8")}")
        if (title.isNotBlank()) params.add("title=${URLEncoder.encode(title, "UTF-8")}")
        if (artist.isNotBlank()) params.add("artist=${URLEncoder.encode(artist, "UTF-8")}")
        if (durationMs > 0) params.add("duration=$durationMs")
        get("/api/v1/lyrics?${params.joinToString("&")}")
    }

    // ==================== SECTION 4: Spotify Canvas ====================

    /** Fetches high-resolution visual assets (canvas video, album art, artist portrait). */
    suspend fun getCanvas(title: String, artist: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val t = URLEncoder.encode(title, "UTF-8")
        val a = URLEncoder.encode(artist, "UTF-8")
        get("/api/v1/canvas?title=$t&artist=$a")
    }

    // ==================== SECTION 5: Shazam Recognition ====================

    /** Runs Shazam DSP spectral analysis on raw audio samples. */
    suspend fun recognizeAudioDsp(samples: FloatArray): Pair<Int, String> = withContext(Dispatchers.IO) {
        val jsonSamples = org.json.JSONArray()
        for (s in samples) jsonSamples.put(s.toDouble())
        val payload = JSONObject().apply { put("samples", jsonSamples) }
        post("/api/v1/shazam/recognize", payload.toString())
    }

    /** Runs Shazam recognition on a local audio file path. */
    suspend fun recognizeFile(filePath: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/shazam/file", JSONObject().apply { put("file_path", filePath) }.toString())
    }

    /** Gets DSP statistics (sample rate, peak count, etc.). */
    suspend fun getShazamDspStats(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/shazam/dsp")
    }

    // ==================== SECTION 6: Edge AI & Vector RAG ====================

    /** Natural language semantic vibe search. */
    suspend fun queryVibe(prompt: String, topK: Int = 10): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/ai/query", JSONObject().apply {
            put("prompt", prompt)
            put("top_k", topK)
        }.toString())
    }

    /** Track mood and emotional valence evaluation. */
    suspend fun queryMood(title: String = "", artist: String = "", lyrics: String = ""): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/ai/mood", JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("lyrics", lyrics)
        }.toString())
    }

    /** 128-D vector cosine similarity benchmark. */
    suspend fun vectorSimilarity(vectorA: FloatArray, vectorB: FloatArray): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/vector/similarity", JSONObject().apply {
            put("vector_a", org.json.JSONArray().also { arr -> vectorA.forEach { arr.put(it.toDouble()) } })
            put("vector_b", org.json.JSONArray().also { arr -> vectorB.forEach { arr.put(it.toDouble()) } })
        }.toString())
    }

    /** Offline smart radio mix generator based on seed track. */
    suspend fun getRecommendations(seedId: String = "", limit: Int = 10): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/recommend?id=${URLEncoder.encode(seedId, "UTF-8")}&limit=$limit")
    }

    // ==================== SECTION 7: Storage Ingestion ====================

    /** Non-destructive in-place virtual audio indexer. */
    suspend fun storageIndex(directoryPath: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/storage/index", JSONObject().apply { put("directory_path", directoryPath) }.toString())
    }

    /** Opt-in library consolidator (copy/move based on source rules). */
    suspend fun storageConsolidate(sourceDir: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/storage/consolidate", JSONObject().apply { put("source_dir", sourceDir) }.toString())
    }

    /** Classify audio file as music vs voice/noise. */
    suspend fun storageClassify(filePath: String, durationMs: Long): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/storage/classify", JSONObject().apply {
            put("file_path", filePath)
            put("duration_ms", durationMs)
        }.toString())
    }

    /** Multi-threaded directory scanner. */
    suspend fun scanDirectory(directoryPath: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/scan", JSONObject().apply { put("directory_path", directoryPath) }.toString())
    }

    // ==================== SECTION 8: Pro Audio DSP & AutoEq ====================

    /** Search AutoEq database for headphone calibration presets. */
    suspend fun autoEqSearch(query: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/autoeq/search?q=${URLEncoder.encode(query, "UTF-8")}")
    }

    /** Get 10-band parametric EQ preset for a specific headphone model. */
    suspend fun autoEqPreset(headphoneId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/autoeq/preset?id=${URLEncoder.encode(headphoneId, "UTF-8")}")
    }

    /** ReplayGain / EBU R128 loudness normalization. */
    suspend fun audioNormalize(filePath: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/audio/normalize", JSONObject().apply { put("file_path", filePath) }.toString())
    }

    /** DJ crossfade curve calculation. */
    suspend fun audioCrossfade(progress: Float): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/audio/crossfade?progress=$progress", "")
    }

    // ==================== SECTION 9: Analytics & Recap ====================

    /** Logs a playback event for analytics tracking. */
    suspend fun logPlaybackEvent(
        trackId: String, title: String, artist: String,
        album: String, listenedSec: Int, year: Int = 0
    ): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/analytics/log", JSONObject().apply {
            put("track_id", trackId)
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("listened_sec", listenedSec)
            if (year > 0) put("year", year)
        }.toString())
    }

    /** Retrieves "Unbound Recap" (on-device Spotify Wrapped) summary. */
    suspend fun getRecap(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/analytics/recap")
    }

    // ==================== SECTION 10: Playlist Import & Account ====================

    /** Import playlist from Spotify URL. */
    suspend fun importSpotify(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/import/spotify", JSONObject().apply { put("url", url) }.toString())
    }

    /** Sync YouTube account with cookies. */
    suspend fun accountSync(cookie: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/account/sync", JSONObject().apply { put("cookie", cookie) }.toString())
    }

    /** Get synced liked music from YouTube account. */
    suspend fun getAccountLiked(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/account/liked")
    }

    // ==================== SECTION 11: Explore, Artists & Podcasts ====================

    /** Curated moods & moments categories. */
    suspend fun getExploreMoods(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/explore/moods")
    }

    /** Regional & global Top 100 charts. */
    suspend fun getExploreCharts(country: String = "US"): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/explore/charts?country=${URLEncoder.encode(country, "UTF-8")}")
    }

    /** Artist deep-dive profile with discography and similar artists. */
    suspend fun getArtistProfile(name: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/artist/profile?name=${URLEncoder.encode(name, "UTF-8")}")
    }

    /** YouTube podcasts browser with resume position. */
    suspend fun getPodcasts(podcastId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/podcasts/browse?id=${URLEncoder.encode(podcastId, "UTF-8")}")
    }

    // ==================== SECTION 12: Social & Utilities ====================

    /** Create a shared listening room. */
    suspend fun createRoom(): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/rooms/create", "")
    }

    /** Join an existing listening room. */
    suspend fun joinRoom(code: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/rooms/join", JSONObject().apply { put("code", code) }.toString())
    }

    /** Sync room playback position. */
    suspend fun syncRoom(code: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/rooms/sync?code=${URLEncoder.encode(code, "UTF-8")}")
    }

    /** Discover local LAN peers. */
    suspend fun getPeers(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/peers")
    }

    /** Update Discord Rich Presence. */
    suspend fun setDiscordPresence(title: String, artist: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/discord/presence", JSONObject().apply {
            put("details", title)
            put("state", artist)
            put("large_image_key", "unbound_logo")
        }.toString())
    }

    /** Get SponsorBlock segments for a video. */
    suspend fun getSponsorBlock(videoId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/sponsorblock?id=${URLEncoder.encode(videoId, "UTF-8")}")
    }

    /** Last.fm now playing update. */
    suspend fun lastfmNowPlaying(title: String, artist: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/lastfm/nowplaying", JSONObject().apply {
            put("title", title)
            put("artist", artist)
        }.toString())
    }

    /** Last.fm scrobble a completed track. */
    suspend fun lastfmScrobble(title: String, artist: String, album: String, durationSec: Int): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/lastfm/scrobble", JSONObject().apply {
            put("title", title)
            put("artist", artist)
            put("album", album)
            put("duration", durationSec)
        }.toString())
    }

    /** Start sleep timer with countdown. */
    suspend fun startSleepTimer(durationMin: Int): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/sleeptimer/start", JSONObject().apply { put("duration_min", durationMin) }.toString())
    }

    /** Get sleep timer status. */
    suspend fun getSleepTimerStatus(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/sleeptimer/status")
    }

    /** Check for app updates from GitHub releases. */
    suspend fun checkForUpdates(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/updater/check")
    }

    // ==================== SECTION 13: Downloads ====================

    /** Start downloading a track to physical storage. */
    suspend fun downloadStart(trackId: String, title: String, artist: String, album: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/download/start", JSONObject().apply {
            put("track_id", trackId)
            put("title", title)
            put("artist", artist)
            put("album", album)
        }.toString())
    }

    /** List all physically downloaded audio files. */
    suspend fun downloadList(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/download/list")
    }

    // ==================== SECTION 14: Phase 0 Typed Contracts ====================

    /** Fetches Top 100 regional charts directly into TrackItem list. */
    suspend fun getCharts(gl: String = "US", hl: String = "en"): List<TrackItem> = withContext(Dispatchers.IO) {
        val (code, json) = get("/api/v1/explore/charts?gl=${URLEncoder.encode(gl, "UTF-8")}&hl=${URLEncoder.encode(hl, "UTF-8")}")
        if (code != 200 || json.isBlank()) return@withContext emptyList()
        val list = mutableListOf<TrackItem>()
        try {
            val root = JSONObject(json)
            val tracksArr = root.optJSONArray("tracks") ?: return@withContext emptyList()
            for (i in 0 until tracksArr.length()) {
                val obj = tracksArr.getJSONObject(i)
                list.add(
                    TrackItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album"),
                        coverUrl = obj.optString("thumbnail"),
                        streamUrl = "",
                        durationMs = obj.optLong("duration_ms"),
                        source = obj.optString("source", "youtube"),
                        isExplicit = obj.optBoolean("is_explicit", false)
                    )
                )
            }
        } catch (_: Exception) {}
        list
    }

    /** Fetches 24-hour situational mood capsules. */
    suspend fun getMoodCapsules(hour: Int? = null): DaypartingState? = withContext(Dispatchers.IO) {
        val path = if (hour != null) "/api/v1/explore/moods?hour=$hour" else "/api/v1/explore/moods"
        val (code, json) = get(path)
        if (code != 200 || json.isBlank()) return@withContext null
        try {
            val root = JSONObject(json)
            val window = root.optString("active_window", "DEEP_FOCUS")
            val localHour = root.optInt("local_hour", 12)
            val capsulesArr = root.optJSONArray("capsules")
            val capsules = mutableListOf<MoodCapsule>()
            if (capsulesArr != null) {
                for (i in 0 until capsulesArr.length()) {
                    val c = capsulesArr.getJSONObject(i)
                    capsules.add(
                        MoodCapsule(
                            tag = c.optString("tag"),
                            title = c.optString("title"),
                            browseId = c.optString("browse_id"),
                            description = c.optString("description"),
                            colorHex = c.optString("color_hex", "#4DB6AC"),
                            iconName = c.optString("icon_name", "ic_music")
                        )
                    )
                }
            }
            DaypartingState(window, localHour, capsules)
        } catch (_: Exception) {
            null
        }
    }

    /** Fetches mood radio stream tracks for a given browseId. */
    suspend fun getMoodRadio(browseId: String, gl: String = "US", hl: String = "en"): List<TrackItem> = withContext(Dispatchers.IO) {
        val (code, json) = get("/api/v1/explore/mood/radio?browse_id=${URLEncoder.encode(browseId, "UTF-8")}&gl=${URLEncoder.encode(gl, "UTF-8")}&hl=${URLEncoder.encode(hl, "UTF-8")}")
        if (code != 200 || json.isBlank()) return@withContext emptyList()
        val list = mutableListOf<TrackItem>()
        try {
            val root = JSONObject(json)
            val tracksArr = root.optJSONArray("tracks") ?: return@withContext emptyList()
            for (i in 0 until tracksArr.length()) {
                val obj = tracksArr.getJSONObject(i)
                list.add(
                    TrackItem(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album"),
                        coverUrl = obj.optString("thumbnail"),
                        durationMs = obj.optLong("duration_ms"),
                        source = obj.optString("source", "youtube")
                    )
                )
            }
        } catch (_: Exception) {}
        list
    }

    /** Triggers crawler & magic byte storage scan across paths. */
    suspend fun scanStorage(paths: List<String>): StorageScanResponse? = withContext(Dispatchers.IO) {
        val arr = org.json.JSONArray()
        paths.forEach { arr.put(it) }
        val payload = JSONObject().apply { put("paths", arr) }
        val (code, json) = post("/api/v1/storage/scan", payload.toString())
        if (code != 200 || json.isBlank()) return@withContext null
        try {
            val root = JSONObject(json)
            StorageScanResponse(
                status = root.optString("status"),
                scannedFiles = root.optInt("scanned_files"),
                audioFilesFound = root.optInt("audio_files_found"),
                newTracksIndexed = root.optInt("new_tracks_indexed"),
                unchangedTracks = root.optInt("unchanged_tracks"),
                elapsedMs = root.optLong("elapsed_ms")
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Fetches indexed local tracks filtered by source. */
    suspend fun getLocalTracks(source: String = "all"): List<LocalTrack> = withContext(Dispatchers.IO) {
        val (code, json) = get("/api/v1/storage/tracks?source=${URLEncoder.encode(source, "UTF-8")}")
        if (code != 200 || json.isBlank()) return@withContext emptyList()
        val list = mutableListOf<LocalTrack>()
        try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("tracks") ?: return@withContext emptyList()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    LocalTrack(
                        id = obj.optString("id"),
                        filePath = obj.optString("file_path"),
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album"),
                        durationMs = obj.optLong("duration_ms"),
                        format = obj.optString("format", "mp3"),
                        fileSize = obj.optLong("file_size"),
                        sourceFolder = obj.optString("source_folder", "music"),
                        dateIndexed = obj.optLong("date_indexed"),
                        mtime = obj.optLong("mtime")
                    )
                )
            }
        } catch (_: Exception) {}
        list
    }

    /** Natural language Vibe AI Search. */
    suspend fun searchVibe(prompt: String): VibeSearchResponse = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply { put("prompt", prompt) }
        val (code, json) = post("/api/v1/search/vibe", payload.toString())
        if (code != 200 || json.isBlank()) {
            throw RuntimeException("Vibe search returned HTTP $code: $json")
        }
        val root = JSONObject(json)
        val vrObj = root.getJSONObject("vibe_result")
        val genres = mutableListOf<String>()
        vrObj.optJSONArray("target_genres")?.let { for (i in 0 until it.length()) genres.add(it.getString(i)) }
        val tags = mutableListOf<String>()
        vrObj.optJSONArray("mood_tags")?.let { for (i in 0 until it.length()) tags.add(it.getString(i)) }
        val keywords = mutableListOf<String>()
        vrObj.optJSONArray("search_keywords")?.let { for (i in 0 until it.length()) keywords.add(it.getString(i)) }

        val vibeResult = VibeResult(
            originalPrompt = vrObj.optString("original_prompt", prompt),
            targetGenres = genres,
            moodTags = tags,
            energyLevel = vrObj.optString("energy_level", "MEDIUM"),
            suggestedBpm = vrObj.optInt("suggested_bpm", 120),
            searchKeywords = keywords
        )

        val radioTracks = mutableListOf<TrackItem>()
        root.optJSONArray("radio_tracks")?.let { arr ->
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                radioTracks.add(
                    TrackItem(
                        id = t.optString("id"),
                        title = t.optString("title"),
                        artist = t.optString("artist"),
                        coverUrl = t.optString("thumbnail"),
                        durationMs = t.optLong("duration_ms"),
                        source = t.optString("source", "youtube")
                    )
                )
            }
        }

        VibeSearchResponse(vibeResult, radioTracks)
    }

    /** Generates on-demand serendipity magic radio mix. */
    suspend fun getMagicRadio(localHour: Int? = null, seedTrackId: String? = null): MagicRadioResult? = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            if (localHour != null) put("local_hour", localHour)
            if (!seedTrackId.isNullOrBlank()) put("seed_track_id", seedTrackId)
        }
        val (code, json) = post("/api/v1/radio/magic", payload.toString())
        if (code != 200 || json.isBlank()) return@withContext null
        try {
            val root = JSONObject(json)
            val seedObj = root.getJSONObject("seed_track")
            val seed = TrackItem(
                id = seedObj.optString("id"),
                title = seedObj.optString("title"),
                artist = seedObj.optString("artist"),
                album = seedObj.optString("album"),
                coverUrl = seedObj.optString("thumbnail"),
                durationMs = seedObj.optLong("duration_ms"),
                source = seedObj.optString("source", "youtube")
            )
            val queueArr = root.optJSONArray("queue")
            val queue = mutableListOf<TrackItem>()
            if (queueArr != null) {
                for (i in 0 until queueArr.length()) {
                    val t = queueArr.getJSONObject(i)
                    queue.add(
                        TrackItem(
                            id = t.optString("id"),
                            title = t.optString("title"),
                            artist = t.optString("artist"),
                            album = t.optString("album"),
                            coverUrl = t.optString("thumbnail"),
                            durationMs = t.optLong("duration_ms"),
                            source = t.optString("source", "youtube")
                        )
                    )
                }
            }
            MagicRadioResult(
                seedTrack = seed,
                queue = queue,
                source = root.optString("source", "youtube_hybrid"),
                generatedMs = root.optLong("generated_ms", 0L)
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Ingests physical listening event (skip, completion, replay) into on-device taste engine. */
    suspend fun recordTasteEvent(
        trackId: String,
        title: String,
        artistId: String,
        artistName: String,
        durationMs: Long,
        listenedMs: Long,
        eventType: String? = null
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("track_id", trackId)
            put("title", title)
            put("artist_id", artistId)
            put("artist_name", artistName)
            put("duration_ms", durationMs)
            put("listened_ms", listenedMs)
            if (!eventType.isNullOrBlank()) put("event_type", eventType)
        }
        val (code, _) = post("/api/v1/analytics/taste_event", payload.toString())
        code in 200..299
    }

    /** Retrieves on-device taste profile summary. */
    suspend fun getTasteProfile(limit: Int = 10): TasteProfileResponse? = withContext(Dispatchers.IO) {
        val (code, json) = get("/api/v1/taste/profile?limit=$limit")
        if (code != 200 || json.isBlank()) return@withContext null
        try {
            val root = JSONObject(json)
            val artistsArr = root.optJSONArray("top_artists")
            val list = mutableListOf<ArtistAffinityItem>()
            if (artistsArr != null) {
                for (i in 0 until artistsArr.length()) {
                    val a = artistsArr.getJSONObject(i)
                    list.add(
                        ArtistAffinityItem(
                            artistId = a.optString("artist_id"),
                            artistName = a.optString("artist_name"),
                            affinityScore = a.optDouble("affinity_score", 0.0),
                            playCount = a.optInt("play_count", 0),
                            skipCount = a.optInt("skip_count", 0),
                            isBanned = a.optBoolean("is_banned", false)
                        )
                    )
                }
            }
            TasteProfileResponse(
                topArtists = list,
                tasteDiversityScore = root.optDouble("taste_diversity_score", 5.0)
            )
        } catch (_: Exception) {
            null
        }
    }

    // ==================== SECTION 12: Account Authentication, Sync & Cascade Search ====================

    /** Authenticates YouTube cookies with local Go engine and triggers library synchronization. */
    suspend fun syncAccount(cookie: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("cookie", cookie).toString()
        post("/api/v1/account/sync", payload)
    }

    /** Retrieves YouTube connection state, user account name, and synced track count. */
    suspend fun getAccountStatus(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/account/status")
    }

    /** Clears stored YouTube cookies and local synced cache on device. */
    suspend fun disconnectAccount(): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/account/disconnect", "{}")
    }

    /** Retrieves all cached synced Liked Music tracks from Go daemon. */
    suspend fun getLikedTracks(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/account/liked")
    }

    /** Dispatches a like or unlike mutation for a video ID directly to YouTube Music InnerTube. */
    suspend fun toggleTrackLike(videoId: String, isLiked: Boolean): Pair<Int, String> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("video_id", videoId).put("like", isLiked).toString()
        post("/api/v1/track/like", payload)
    }

    /** Executes the 4-stage search cascade (official -> fan lyric/audio -> broad community sweep -> clean empty). */
    suspend fun searchCascade(query: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        get("/api/v1/search/cascade?q=$encoded")
    }

    /** Parses AccountStatusData payload from JSON string. */
    fun parseAccountStatus(jsonStr: String): AccountStatusData? {
        return try {
            val root = JSONObject(jsonStr)
            AccountStatusData(
                connected = root.optBoolean("connected", false),
                accountName = root.optString("account_name", "Local User"),
                syncedTracksCount = root.optInt("synced_tracks_count", 0),
                lastSynced = root.optString("last_synced", "")
            )
        } catch (_: Exception) {
            null
        }
    }

    /** Parses liked tracks array from JSON string. */
    fun parseLikedTracks(jsonStr: String): List<TrackItem> {
        val list = mutableListOf<TrackItem>()
        try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("tracks") ?: return list
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                val id = t.optString("id", "")
                if (id.isBlank()) continue
                list.add(
                    TrackItem(
                        id = id,
                        title = t.optString("title", "Unknown Track"),
                        artist = t.optString("artist", "Unknown Artist"),
                        album = t.optString("album", ""),
                        durationMs = t.optLong("duration_ms", 0L),
                        coverUrl = t.optString("thumbnail_url", ""),
                        streamUrl = "http://127.0.0.1:45731/api/v1/stream?id=$id",
                        source = "YouTube Liked"
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    /** Parses CascadeSearchResponse from JSON string. */
    fun parseCascadeSearch(jsonStr: String): CascadeSearchResponse? {
        return try {
            val root = JSONObject(jsonStr)
            val tracksList = mutableListOf<TrackItem>()
            val arr = root.optJSONArray("tracks")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val t = arr.optJSONObject(i) ?: continue
                    val id = t.optString("id", "")
                    if (id.isBlank()) continue
                    tracksList.add(
                        TrackItem(
                            id = id,
                            title = t.optString("title", "Unknown"),
                            artist = t.optString("artist", "Unknown"),
                            album = t.optString("album", ""),
                            durationMs = t.optLong("duration_ms", 0L),
                            coverUrl = t.optString("thumbnail_url", ""),
                            streamUrl = "http://127.0.0.1:45731/api/v1/stream?id=$id",
                            source = "Cascade Search"
                        )
                    )
                }
            }
            CascadeSearchResponse(
                query = root.optString("query", ""),
                stageReached = root.optInt("stage_reached", 4),
                stageName = root.optString("stage_name", "No Results"),
                tracks = tracksList
            )
        } catch (_: Exception) {
            null
        }
    }

    // ==================== SECTION 12: Phase 3 Settings Studio & Audio DSP Hub ====================

    /** Fetches all persisted application key-value settings. */
    suspend fun getAppSettings(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/settings")
    }

    /** Stores or updates a key-value setting pair. */
    suspend fun setAppSetting(key: String, value: String): Pair<Int, String> = withContext(Dispatchers.IO) {
        val payload = JSONObject().put("key", key).put("value", value).toString()
        post("/api/v1/settings", payload)
    }

    /** Retrieves all custom user equalizer presets. */
    suspend fun getCustomEqPresets(): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/eq/presets")
    }

    /** Persists a custom equalizer preset to SQLite via daemon. */
    suspend fun saveCustomEqPreset(preset: UserEqPresetDto): Pair<Int, String> = withContext(Dispatchers.IO) {
        val gainsArray = org.json.JSONArray()
        preset.bandGains.forEach { gainsArray.put(it.toDouble()) }
        val payload = JSONObject()
            .put("id", preset.id)
            .put("name", preset.name)
            .put("band_gains", gainsArray)
            .put("bass_boost", preset.bassBoost)
            .put("virtualizer", preset.virtualizer)
            .put("loudness", preset.loudness)
            .toString()
        post("/api/v1/eq/presets", payload)
    }

    /** Triggers on-device storage cache purge across cache, tmp, and lyrics. */
    suspend fun purgeStorageCache(): Pair<Int, String> = withContext(Dispatchers.IO) {
        post("/api/v1/storage/purge_cache", "{}")
    }

    /** Parses settings map from JSON response. */
    fun parseSettings(jsonStr: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val root = JSONObject(jsonStr)
            val settingsObj = root.optJSONObject("settings")
            if (settingsObj != null) {
                val keys = settingsObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    map[k] = settingsObj.optString(k, "")
                }
            }
        } catch (_: Exception) {}
        return map
    }

    /** Parses UserEqPresetDto list from JSON response. */
    fun parseEqPresets(jsonStr: String): List<UserEqPresetDto> {
        val list = mutableListOf<UserEqPresetDto>()
        try {
            val root = JSONObject(jsonStr)
            val arr = root.optJSONArray("presets") ?: return list
            for (i in 0 until arr.length()) {
                val p = arr.optJSONObject(i) ?: continue
                val bandGains = mutableListOf<Float>()
                val bandsArr = p.optJSONArray("band_gains")
                if (bandsArr != null) {
                    for (b in 0 until bandsArr.length()) {
                        bandGains.add(bandsArr.optDouble(b, 0.0).toFloat())
                    }
                }
                list.add(
                    UserEqPresetDto(
                        id = p.optString("id", ""),
                        name = p.optString("name", "Custom"),
                        bandGains = bandGains,
                        bassBoost = p.optInt("bass_boost", 0),
                        virtualizer = p.optInt("virtualizer", 0),
                        loudness = p.optInt("loudness", 0)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    /** Parses CachePurgeResult from JSON response. */
    fun parseCachePurgeResult(jsonStr: String): CachePurgeResult? {
        return try {
            val root = JSONObject(jsonStr)
            val categories = mutableListOf<String>()
            val arr = root.optJSONArray("purged_categories")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    categories.add(arr.optString(i))
                }
            }
            CachePurgeResult(
                freedBytes = root.optLong("freed_bytes", 0L),
                purgedCategories = categories
            )
        } catch (_: Exception) {
            null
        }
    }

    // ==================== SECTION 13: Phase 4 Genre & Mood Boards ====================

    /** Retrieves structured genre and mood discovery boards with 7-day cache. */
    suspend fun getMoodsAndGenres(countryCode: String = "US", langCode: String = "en"): Pair<Int, String> = withContext(Dispatchers.IO) {
        get("/api/v1/explore/moods_genres?gl=$countryCode&hl=$langCode")
    }

    /** Fetches curated playlist shelves for a genre token. */
    suspend fun getGenreDetail(params: String, genreName: String = "Genre", countryCode: String = "US", langCode: String = "en"): Pair<Int, String> = withContext(Dispatchers.IO) {
        val encodedParams = URLEncoder.encode(params, "UTF-8")
        val encodedName = URLEncoder.encode(genreName, "UTF-8")
        get("/api/v1/explore/genre_detail?params=$encodedParams&name=$encodedName&gl=$countryCode&hl=$langCode")
    }

    /** Parses GenreSectionDto list from JSON response. */
    fun parseMoodsAndGenres(jsonStr: String): List<GenreSectionDto> {
        val sections = mutableListOf<GenreSectionDto>()
        try {
            val root = JSONObject(jsonStr)
            val secArr = root.optJSONArray("sections") ?: return sections
            for (i in 0 until secArr.length()) {
                val sObj = secArr.optJSONObject(i) ?: continue
                val secTitle = sObj.optString("title", "Explore")
                val itemsArr = sObj.optJSONArray("items") ?: continue
                val items = mutableListOf<GenreItemDto>()
                for (j in 0 until itemsArr.length()) {
                    val itObj = itemsArr.optJSONObject(j) ?: continue
                    items.add(
                        GenreItemDto(
                            title = itObj.optString("title", ""),
                            stripeColor = itObj.optLong("stripe_color", 0L),
                            params = itObj.optString("params", ""),
                            browseId = itObj.optString("browse_id", "")
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    sections.add(GenreSectionDto(secTitle, items))
                }
            }
        } catch (_: Exception) {}
        return sections
    }

    /** Parses PlaylistShelfDto list from JSON response. */
    fun parseGenreDetail(jsonStr: String): List<PlaylistShelfDto> {
        val shelves = mutableListOf<PlaylistShelfDto>()
        try {
            val root = JSONObject(jsonStr)
            val shelfArr = root.optJSONArray("shelves") ?: return shelves
            for (i in 0 until shelfArr.length()) {
                val sObj = shelfArr.optJSONObject(i) ?: continue
                val shelfTitle = sObj.optString("title", "Featured Playlists")
                val itemsArr = sObj.optJSONArray("items") ?: continue
                val items = mutableListOf<PlaylistItemDto>()
                for (j in 0 until itemsArr.length()) {
                    val itObj = itemsArr.optJSONObject(j) ?: continue
                    items.add(
                        PlaylistItemDto(
                            id = itObj.optString("id", ""),
                            title = itObj.optString("title", "Unknown"),
                            subtitle = itObj.optString("subtitle", ""),
                            thumbnailUrl = itObj.optString("thumbnail_url", ""),
                            playlistId = itObj.optString("playlist_id", "")
                        )
                    )
                }
                if (items.isNotEmpty()) {
                    shelves.add(PlaylistShelfDto(shelfTitle, items))
                }
            }
        } catch (_: Exception) {}
        return shelves
    }

    // ==================== HTTP Transport ====================

    private fun get(path: String): Pair<Int, String> {
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "GET"
            conn.connectTimeout = 5000
            conn.readTimeout = 10000
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.let {
                BufferedReader(InputStreamReader(it)).use { r -> r.readText() }
            } ?: ""
            Pair(code, response)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "Network error")
        } finally {
            conn.disconnect()
        }
    }

    private fun post(path: String, jsonBody: String): Pair<Int, String> {
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.connectTimeout = 5000
            conn.readTimeout = 15000
            conn.doOutput = true

            OutputStreamWriter(conn.outputStream, "UTF-8").use { os ->
                os.write(jsonBody)
                os.flush()
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.let {
                BufferedReader(InputStreamReader(it)).use { r -> r.readText() }
            } ?: ""
            Pair(code, response)
        } catch (e: Exception) {
            Pair(-1, e.message ?: "Network error")
        } finally {
            conn.disconnect()
        }
    }
}
