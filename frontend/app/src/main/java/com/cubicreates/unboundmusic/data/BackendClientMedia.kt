/*
 * Package: com.cubicreates.unboundmusic.data
 * File: BackendClientMedia.kt
 * Purpose: Extension functions for BackendClient covering synchronized lyrics, AutoEq DSP,
 *          parametric equalizer curves, Shazam recognition, SponsorBlock, and BitTorrent fallback.
 * Subsystem: Native Go Engine REST Client - Audio DSP & Media
 */

package com.cubicreates.unboundmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder

/** Fetches synchronized lyrics with phonetic Romanization and offline caching from 3-tier cascade. */
suspend fun BackendClient.getLyrics(
    trackId: String = "",
    title: String = "",
    artist: String = "",
    durationMs: Long = 0
): Pair<Int, String> = withContext(Dispatchers.IO) {
    val params = mutableListOf<String>()
    if (trackId.isNotBlank()) {
        params.add("track_id=${URLEncoder.encode(trackId, "UTF-8")}")
        params.add("id=${URLEncoder.encode(trackId, "UTF-8")}")
    }
    if (title.isNotBlank()) params.add("title=${URLEncoder.encode(title, "UTF-8")}")
    if (artist.isNotBlank()) params.add("artist=${URLEncoder.encode(artist, "UTF-8")}")
    if (durationMs > 0) {
        val durationSec = durationMs / 1000
        params.add("duration=$durationSec")
    }
    get("/api/v1/lyrics?${params.joinToString("&")}")
}

/** Type-safe decode of LyricsPayloadDto using kotlinx.serialization. */
fun BackendClient.decodeLyricsPayload(jsonStr: String): LyricsPayloadDto? {
    if (jsonStr.isBlank()) return null
    return try {
        BackendClient.defaultJson.decodeFromString<LyricsPayloadDto>(jsonStr)
    } catch (_: Exception) {
        null
    }
}

/** Type-safe decode of AutoEqSearchResponseDto using kotlinx.serialization. */
fun BackendClient.decodeAutoEqSearchResponse(jsonStr: String): AutoEqSearchResponseDto {
    if (jsonStr.isBlank()) return AutoEqSearchResponseDto()
    return try {
        BackendClient.defaultJson.decodeFromString<AutoEqSearchResponseDto>(jsonStr)
    } catch (_: Exception) {
        AutoEqSearchResponseDto()
    }
}

/** Type-safe decode of EQPresetDto using kotlinx.serialization. */
fun BackendClient.decodeEQPreset(jsonStr: String): EQPresetDto? {
    if (jsonStr.isBlank()) return null
    return try {
        BackendClient.defaultJson.decodeFromString<EQPresetDto>(jsonStr)
    } catch (_: Exception) {
        null
    }
}

/** Get SponsorBlock segments for a video. */
suspend fun BackendClient.getSponsorBlock(videoId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/sponsorblock?id=${URLEncoder.encode(videoId, "UTF-8")}")
}

/** Queries SponsorBlock music_offtopic skip segments for a video ID. */
suspend fun BackendClient.getSkipSegments(videoId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val v = URLEncoder.encode(videoId, "UTF-8")
    get("/api/v1/track/skip_segments?video_id=$v")
}

/** Parses raw skip segments JSON into domain SkipSegmentDto list. */
fun BackendClient.parseSkipSegments(jsonStr: String): List<SkipSegmentDto> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        val array = org.json.JSONArray(jsonStr)
        val list = mutableListOf<SkipSegmentDto>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                SkipSegmentDto(
                    category = obj.optString("category", "music_offtopic"),
                    startMs = obj.optLong("start_ms", 0),
                    endMs = obj.optLong("end_ms", 0),
                    action = obj.optString("action", "skip"),
                    uuid = obj.optString("uuid", "")
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

/**
 * Queries Return YouTube Dislike (RYD) statistics for a video ID.
 * Primary: Local Go daemon endpoint /api/v1/ryd/votes?videoId={videoId} (cached, low latency).
 * Fallback: Direct call to https://returnyoutubedislikeapi.com/votes?videoId={videoId}
 *           if daemon is unreachable or returns non-200.
 */
suspend fun BackendClient.getRydVotes(videoId: String): RydVoteData? = withContext(Dispatchers.IO) {
    if (videoId.isBlank()) return@withContext null

    // 1. Primary: Query embedded daemon
    try {
        val (status, body) = get("/api/v1/ryd/votes?videoId=${URLEncoder.encode(videoId, "UTF-8")}")
        if (status == 200 && body.isNotBlank()) {
            val json = JSONObject(body)
            val likes = json.optLong("likes", 0L)
            val dislikes = json.optLong("dislikes", 0L)
            val rating = json.optDouble("rating", 0.0)
            val viewCount = json.optLong("view_count", json.optLong("viewCount", 0L))
            val likePct = json.optInt("like_percentage", -1)
            val calculatedPct = if (likePct >= 0) {
                likePct
            } else {
                val total = likes + dislikes
                if (total > 0) ((likes.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100) else 100
            }

            return@withContext RydVoteData(
                id = json.optString("id", videoId),
                likes = likes,
                dislikes = dislikes,
                rating = rating,
                viewCount = viewCount,
                likePercentage = calculatedPct
            )
        }
    } catch (_: Throwable) {}

    // 2. Direct HTTPS Fallback to RYD upstream
    try {
        val directReq = Request.Builder()
            .url("https://returnyoutubedislikeapi.com/votes?videoId=${URLEncoder.encode(videoId, "UTF-8")}")
            .header("User-Agent", "UnboundMusic/1.0.0 (FOSS Android Client)")
            .header("Accept", "application/json")
            .get()
            .build()

        BackendClient.sharedOkHttpClient.newCall(directReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val likes = json.optLong("likes", 0L)
                val dislikes = json.optLong("dislikes", 0L)
                val rating = json.optDouble("rating", 0.0)
                val viewCount = json.optLong("viewCount", json.optLong("view_count", 0L))
                val total = likes + dislikes
                val pct = if (total > 0) ((likes.toDouble() / total.toDouble()) * 100).toInt().coerceIn(0, 100) else 100

                return@withContext RydVoteData(
                    id = json.optString("id", videoId),
                    likes = likes,
                    dislikes = dislikes,
                    rating = rating,
                    viewCount = viewCount,
                    likePercentage = pct
                )
            }
        }
    } catch (_: Throwable) {}

    null
}

/** Queries Spotify Canvas video loop art for a track ID. */
suspend fun BackendClient.getCanvas(trackId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/canvas?id=${URLEncoder.encode(trackId, "UTF-8")}")
}

/** Fetches high-resolution visual assets (canvas video, album art, artist portrait) by title and artist. */
suspend fun BackendClient.getCanvas(title: String, artist: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val t = URLEncoder.encode(title, "UTF-8")
    val a = URLEncoder.encode(artist, "UTF-8")
    get("/api/v1/canvas?title=$t&artist=$a")
}

/** Identifies song from raw 16kHz 16-bit Mono PCM audio bytes via Go Shazam SigX engine. */
suspend fun BackendClient.identifyPcmAudio(pcmData: ByteArray): Pair<Int, String> = withContext(Dispatchers.IO) {
    val mediaType = "application/octet-stream".toMediaType()
    val body = pcmData.toRequestBody(mediaType)
    val endpoint = "$baseUrl/api/v1/shazam/identify"
    val req = Request.Builder()
        .url(endpoint)
        .post(body)
        .build()
    try {
        httpClient.newCall(req).execute().use { resp ->
            Pair(resp.code, resp.body?.string() ?: "")
        }
    } catch (e: Exception) {
        Pair(-1, e.message ?: "Failed connecting to identification endpoint")
    }
}

/** Pure-Go Shazam 16kHz PCM audio fingerprint recognition. */
suspend fun BackendClient.recognizeShazam(pcmBytesBase64: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/shazam/recognize", JSONObject().apply {
        put("pcm_data", pcmBytesBase64)
    }.toString())
}

/** Shazam DSP pipeline diagnostics and spectrogram peak counter. */
suspend fun BackendClient.getShazamDspStats(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/shazam/dsp")
}

/** Search AutoEq database for headphone calibration presets. */
suspend fun BackendClient.autoEqSearch(query: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/autoeq/search?q=${URLEncoder.encode(query, "UTF-8")}")
}

/** Get 10-band parametric EQ preset for a specific headphone model. */
suspend fun BackendClient.autoEqPreset(headphoneId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/autoeq/preset?id=${URLEncoder.encode(headphoneId, "UTF-8")}")
}

/** ReplayGain / EBU R128 loudness normalization. */
suspend fun BackendClient.audioNormalize(filePath: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/audio/normalize", JSONObject().apply { put("file_path", filePath) }.toString())
}

/** DJ crossfade curve calculation. */
suspend fun BackendClient.audioCrossfade(progress: Float): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/audio/crossfade?progress=$progress", "")
}

/** Retrieves all custom user equalizer presets. */
suspend fun BackendClient.getCustomEqPresets(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/eq/presets")
}

/** Persists a custom equalizer preset to SQLite via daemon. */
suspend fun BackendClient.saveCustomEqPreset(preset: UserEqPresetDto): Pair<Int, String> = withContext(Dispatchers.IO) {
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

/** Parses UserEqPresetDto list from JSON response. */
fun BackendClient.parseEqPresets(jsonStr: String): List<UserEqPresetDto> {
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

/** Update Discord Rich Presence. */
suspend fun BackendClient.setDiscordPresence(title: String, artist: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/discord/presence", JSONObject().apply {
        put("details", title)
        put("state", artist)
        put("large_image_key", "unbound_logo")
    }.toString())
}

/** Last.fm now playing update. */
suspend fun BackendClient.lastfmNowPlaying(title: String, artist: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/lastfm/nowplaying", JSONObject().apply {
        put("title", title)
        put("artist", artist)
    }.toString())
}

/** Last.fm scrobble a completed track. */
suspend fun BackendClient.lastfmScrobble(
    title: String,
    artist: String,
    album: String,
    durationSec: Int
): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/lastfm/scrobble", JSONObject().apply {
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("duration", durationSec)
    }.toString())
}

/** Start sleep timer with countdown. */
suspend fun BackendClient.startSleepTimer(durationMin: Int): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/sleeptimer/start", JSONObject().apply { put("duration_min", durationMin) }.toString())
}

/** Get sleep timer status. */
suspend fun BackendClient.getSleepTimerStatus(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/sleeptimer/status")
}

/** Queries Spotify/MusicBrainz verification gate and resolves P2P fallback streams. */
suspend fun BackendClient.resolveFallbackTrack(title: String, artist: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val encTitle = URLEncoder.encode(title, "UTF-8")
    val encArtist = URLEncoder.encode(artist, "UTF-8")
    get("/api/v1/fallback/resolve?title=$encTitle&artist=$encArtist")
}

/** Parses fallback resolution payload into FallbackStatusDto. */
fun BackendClient.parseFallbackStatus(jsonStr: String): FallbackStatusDto? {
    if (jsonStr.isBlank()) return null
    return try {
        val json = JSONObject(jsonStr)
        val verifiedObj = json.optJSONObject("verified_track")
        val sourceObj = json.optJSONObject("selected_source")

        FallbackStatusDto(
            stage = json.optString("stage", "UNKNOWN"),
            message = json.optString("message", ""),
            elapsedMs = json.optLong("elapsed_ms", 0L),
            verifiedTitle = verifiedObj?.optString("title"),
            verifiedArtist = verifiedObj?.optString("artist"),
            verifiedFoundOn = verifiedObj?.optString("found_on"),
            sourceFormat = sourceObj?.optString("audio_format"),
            seeders = sourceObj?.optInt("seeders")
        )
    } catch (_: Exception) {
        null
    }
}

/** Logs a playback event for analytics tracking. */
suspend fun BackendClient.logPlaybackEvent(
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
suspend fun BackendClient.getRecap(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/analytics/recap")
}

/**
 * Multi-candidate lyrics/singing song recognition via POST /api/v1/recognize/lyrics.
 * Falls back to music catalog search if the endpoint is unavailable.
 */
suspend fun BackendClient.recognizeByLyrics(
    query: String,
    mode: String = "lyrics",
    limit: Int = 8
): RecognitionResponseDto = withContext(Dispatchers.IO) {
    val trimmed = query.trim()
    if (trimmed.isBlank()) {
        return@withContext RecognitionResponseDto(query = trimmed, mode = mode, matched = false)
    }

    val payload = JSONObject().apply {
        put("query", trimmed)
        put("mode", mode)
        put("limit", limit)
    }.toString()

    val (code, body) = post("/api/v1/recognize/lyrics", payload)
    if (code in 200..299 && body.isNotBlank()) {
        return@withContext parseRecognitionResponse(body, fallbackQuery = trimmed, fallbackMode = mode)
    }

    // Fallback to music-only search if daemon endpoint returned non-2xx
    val (sCode, sBody) = search(trimmed, type = "music")
    if (sCode in 200..299 && sBody.isNotBlank()) {
        val tracks = parseSearchResults(sBody).filter {
            it.itemType != "album" && it.itemType != "artist" && it.itemType != "playlist"
        }.take(limit)
        val cands = tracks.mapIndexed { idx, t ->
            RecognitionCandidateDto(
                id = t.id,
                title = t.title,
                artist = t.artist,
                album = t.album,
                durationMs = t.durationMs,
                coverUrl = t.coverUrl,
                thumbnailUrl = t.coverUrl,
                confidence = (0.82 - idx * 0.04).coerceAtLeast(0.50),
                relevanceLabel = if (idx == 0) "Top Catalog Match" else "Catalog Candidate",
                matchSource = "ytmusic",
                isOriginal = true,
                explanation = "Matched via YouTube Music catalog"
            )
        }
        return@withContext RecognitionResponseDto(
            query = trimmed,
            mode = mode,
            matched = cands.isNotEmpty(),
            count = cands.size,
            candidates = cands
        )
    }

    RecognitionResponseDto(
        query = trimmed,
        mode = mode,
        matched = false,
        message = "Could not recognize lyrics ($code)"
    )
}

/**
 * Parses either /api/v1/recognize/lyrics or /api/v1/shazam/identify JSON payloads into [RecognitionResponseDto].
 */
fun BackendClient.parseRecognitionResponse(
    jsonStr: String,
    fallbackQuery: String = "",
    fallbackMode: String = "lyrics"
): RecognitionResponseDto {
    if (jsonStr.isBlank()) {
        return RecognitionResponseDto(query = fallbackQuery, mode = fallbackMode, matched = false)
    }
    return try {
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("candidates") ?: root.optJSONArray("variants")
        val list = mutableListOf<RecognitionCandidateDto>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val id = obj.optString("id", obj.optString("track_id", ""))
                val title = obj.optString("title", "")
                if (title.isBlank()) continue
                val cover = obj.optString("cover_url", obj.optString("thumbnail_url", obj.optString("thumbnail", "")))
                list.add(
                    RecognitionCandidateDto(
                        id = id,
                        title = title,
                        artist = obj.optString("artist", ""),
                        album = obj.optString("album", ""),
                        durationMs = obj.optLong("duration_ms", 0L),
                        coverUrl = cover,
                        thumbnailUrl = obj.optString("thumbnail_url", cover),
                        year = obj.optString("year", ""),
                        confidence = obj.optDouble("confidence", (0.90 - i * 0.05).coerceAtLeast(0.55)),
                        relevanceLabel = obj.optString("relevance_label", obj.optString("badge", "Candidate Match")),
                        matchedLyric = obj.optString("matched_lyric", ""),
                        matchSource = obj.optString("match_source", obj.optString("source", fallbackMode)),
                        isOriginal = obj.optBoolean("is_original", true),
                        isRadarMatch = obj.optBoolean("is_radar_match", false),
                        explanation = obj.optString("explanation", "")
                    )
                )
            }
        }
        RecognitionResponseDto(
            query = root.optString("query", fallbackQuery),
            mode = root.optString("mode", fallbackMode),
            matched = root.optBoolean("matched", list.isNotEmpty()),
            count = root.optInt("count", list.size),
            candidates = list,
            latencyMs = root.optLong("latency_ms", 0L),
            message = root.optString("message", "")
        )
    } catch (_: Exception) {
        RecognitionResponseDto(query = fallbackQuery, mode = fallbackMode, matched = false)
    }
}


