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
