/*
 * Package: com.cubicreates.unboundmusic.data
 * File: BackendClientExplore.kt
 * Purpose: Extension functions for BackendClient covering catalog search, audio stream resolution,
 *          curated mood capsules, charts, artist profiles, genre discovery boards, custom playlists,
 *          and vector Vibe search.
 * Subsystem: Native Go Engine REST Client - Search, Explore & Discovery
 */

package com.cubicreates.unboundmusic.data

import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Searches YouTube Music catalog with optional category ("all", "music", "podcast"). */
suspend fun BackendClient.search(query: String, type: String = "all"): Pair<Int, String> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    val encodedType = URLEncoder.encode(type, "UTF-8")
    get("/api/v1/search?q=$encoded&type=$encodedType")
}

/** Executes the 4-stage search cascade (official -> fan lyric/audio -> broad community sweep -> clean empty). */
suspend fun BackendClient.searchCascade(query: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(query, "UTF-8")
    get("/api/v1/search/cascade?q=$encoded")
}

/** Type-safe decode of SearchResultDto using kotlinx.serialization with fallback to manual parsing. */
fun BackendClient.decodeSearchResult(jsonStr: String): SearchResultDto {
    if (jsonStr.isBlank()) return SearchResultDto()
    return try {
        BackendClient.defaultJson.decodeFromString<SearchResultDto>(jsonStr)
    } catch (_: Exception) {
        val fallbackTracks = parseSearchResults(jsonStr).map {
            TrackMetadataDto(
                id = it.id,
                title = it.title,
                artist = it.artist,
                album = it.album,
                durationMs = it.durationMs,
                thumbnail = it.coverUrl,
                streamUrl = it.streamUrl,
                source = it.source
            )
        }
        SearchResultDto(tracks = fallbackTracks)
    }
}

/** Parses raw search JSON from /api/v1/search into polymorphic TrackItem list. */
fun BackendClient.parseSearchResults(jsonStr: String): List<TrackItem> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        val json = JSONObject(jsonStr)
        val tracksArray = json.optJSONArray("tracks")
            ?: json.optJSONArray("results")
            ?: json.optJSONArray("items")
            ?: return emptyList()

        val list = mutableListOf<TrackItem>()
        for (i in 0 until tracksArray.length()) {
            val item = tracksArray.optJSONObject(i) ?: continue
            val id = item.optString("id", item.optString("video_id", ""))
            val title = item.optString("title", "Unknown Track")
            val artist = item.optString("artist",
                item.optJSONArray("artists")?.optJSONObject(0)?.optString("name", "Unknown Artist")
                    ?: "Unknown Artist")
            val thumb = item.optString("thumbnail",
                item.optString("thumbnail_url", item.optString("cover_url", "")))
            val durMs = item.optLong("duration_ms", 0L)
            var itemType = item.optString("item_type", "song")
            val browseId = item.optString("browse_id", "")
            if (itemType == "song") {
                if (browseId.startsWith("UC") || id.startsWith("UC")) {
                    itemType = "artist"
                } else if (browseId.startsWith("VL") || browseId.startsWith("PL") || id.startsWith("VL") || id.startsWith("PL")) {
                    itemType = "playlist"
                } else if (browseId.startsWith("MPREb_") || id.startsWith("MPREb_")) {
                    itemType = "album"
                }
            }
            val resolvedArtist = if ((artist == "Unknown Artist" || artist.isBlank()) && itemType == "artist") {
                title
            } else {
                artist
            }
            val year = item.optString("year", "")
            list.add(
                TrackItem(
                    id = id,
                    title = title,
                    artist = resolvedArtist,
                    coverUrl = thumb.ifBlank { if (id.length == 11) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else "" },
                    streamUrl = "",
                    durationMs = durMs,
                    source = "youtube",
                    itemType = itemType,
                    browseId = browseId,
                    year = year
                )
            )
        }
        list
    } catch (e: Exception) {
        emptyList()
    }
}

/** Parses CascadeSearchResponse from JSON string. */
fun BackendClient.parseCascadeSearch(jsonStr: String): CascadeSearchResponse? {
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
                        streamUrl = "",
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

/** Resolves a direct audio stream URL for a given video ID (or title+artist for zero-data interception). */
suspend fun BackendClient.getStream(
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

/** Fetches Top 100 regional charts directly into TrackItem list. */
suspend fun BackendClient.getCharts(gl: String = "US", hl: String = "en"): List<TrackItem> = withContext(Dispatchers.IO) {
    val (code, json) = get("/api/v1/explore/charts?gl=${URLEncoder.encode(gl, "UTF-8")}&hl=${URLEncoder.encode(hl, "UTF-8")}&country=${URLEncoder.encode(gl, "UTF-8")}")
    if (code != 200 || json.isBlank()) return@withContext emptyList()
    val list = mutableListOf<TrackItem>()
    try {
        val trimmed = json.trim()
        val tracksArr = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("tracks") ?: root.optJSONArray("charts") ?: JSONArray()
        }
        for (i in 0 until tracksArr.length()) {
            val obj = tracksArr.getJSONObject(i)
            val id = obj.optString("id").ifBlank { obj.optString("track_id") }
            val thumb = obj.optString("thumbnail").ifBlank { obj.optString("thumbnail_url").ifBlank { obj.optString("cover_url") } }
            if (id.isNotBlank()) {
                list.add(
                    TrackItem(
                        id = id,
                        title = obj.optString("title"),
                        artist = obj.optString("artist"),
                        album = obj.optString("album"),
                        coverUrl = thumb,
                        streamUrl = "",
                        durationMs = obj.optLong("duration_ms"),
                        source = obj.optString("source", "youtube"),
                        isExplicit = obj.optBoolean("is_explicit", false)
                    )
                )
            }
        }
    } catch (_: Exception) {}
    list
}

/** Curated moods & moments categories. */
suspend fun BackendClient.getExploreMoods(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/explore/moods")
}

/** Regional & global Top 100 charts. */
suspend fun BackendClient.getExploreCharts(country: String = "US"): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/explore/charts?country=${URLEncoder.encode(country, "UTF-8")}")
}

/** Fetches 24-hour situational mood capsules. */
suspend fun BackendClient.getMoodCapsules(hour: Int? = null): DaypartingState? = withContext(Dispatchers.IO) {
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
suspend fun BackendClient.getMoodRadio(browseId: String, gl: String = "US", hl: String = "en"): List<TrackItem> = withContext(Dispatchers.IO) {
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

/** Artist deep-dive profile with discography and similar artists. */
suspend fun BackendClient.getArtistProfile(name: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/artist/profile?name=${URLEncoder.encode(name, "UTF-8")}")
}

/** Fetches complete album or playlist details, including high-res metadata and full tracklist. */
suspend fun BackendClient.getPlaylist(id: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(id, "UTF-8")
    get("/api/v1/playlist?id=$encoded")
}

/** Alias for getPlaylist() when querying an album. */
suspend fun BackendClient.getAlbum(id: String): Pair<Int, String> = getPlaylist(id)

/** Parses raw album or playlist response JSON into domain AlbumPlaylistDto. */
fun BackendClient.parseAlbumPlaylist(json: String): AlbumPlaylistDto? {
    if (json.isBlank()) return null
    return try {
        val root = JSONObject(json)
        val tList = mutableListOf<TrackItem>()
        val tracksArr = root.optJSONArray("tracks")
        if (tracksArr != null) {
            for (i in 0 until tracksArr.length()) {
                val tObj = tracksArr.getJSONObject(i)
                val tId = tObj.optString("id")
                val durSec = tObj.optLong("duration_seconds", 0L)
                val cover = tObj.optString("thumbnail_url").ifBlank {
                    if (tId.isNotBlank()) "https://i.ytimg.com/vi/$tId/hqdefault.jpg" else ""
                }
                tList.add(
                    TrackItem(
                        id = tId,
                        title = tObj.optString("title"),
                        artist = tObj.optString("artist"),
                        album = tObj.optString("album"),
                        durationMs = durSec * 1000,
                        coverUrl = cover,
                        streamUrl = "",
                        source = tObj.optString("source", "youtube")
                    )
                )
            }
        }
        AlbumPlaylistDto(
            id = root.optString("id"),
            title = root.optString("title"),
            subtitle = root.optString("subtitle"),
            description = root.optString("description"),
            thumbnailUrl = root.optString("thumbnail_url"),
            isAlbum = root.optBoolean("is_album", false),
            year = root.optString("year"),
            trackCount = root.optInt("track_count", tList.size),
            totalDuration = root.optString("total_duration"),
            tracks = tList
        )
    } catch (_: Exception) {
        null
    }
}

/** Retrieves structured genre and mood discovery boards with 7-day cache. */
suspend fun BackendClient.getMoodsAndGenres(countryCode: String = "US", langCode: String = "en"): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/explore/moods_genres?gl=$countryCode&hl=$langCode")
}

/** Fetches curated playlist shelves for a genre token. */
suspend fun BackendClient.getGenreDetail(
    params: String,
    genreName: String = "Genre",
    countryCode: String = "US",
    langCode: String = "en"
): Pair<Int, String> = withContext(Dispatchers.IO) {
    val encodedParams = URLEncoder.encode(params, "UTF-8")
    val encodedName = URLEncoder.encode(genreName, "UTF-8")
    get("/api/v1/explore/genre_detail?params=$encodedParams&name=$encodedName&gl=$countryCode&hl=$langCode")
}

/** Parses GenreSectionDto list from JSON response. */
fun BackendClient.parseMoodsAndGenres(jsonStr: String): List<GenreSectionDto> {
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
fun BackendClient.parseGenreDetail(jsonStr: String): List<PlaylistShelfDto> {
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

/** Natural language Vibe AI Search with regional cultural awareness. */
suspend fun BackendClient.searchVibe(prompt: String, region: String = "IN", language: String = "en"): VibeSearchResponse = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("prompt", prompt)
        put("region", region)
        put("language", language)
    }
    val (code, json) = post(
        "/api/v1/search/vibe",
        payload.toString(),
        headers = mapOf("X-Unbound-Region" to region)
    )
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
        searchKeywords = keywords,
        region = vrObj.optString("region", region)
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
suspend fun BackendClient.getMagicRadio(localHour: Int? = null, seedTrackId: String? = null): MagicRadioResult? = withContext(Dispatchers.IO) {
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

/** Natural language semantic vibe search. */
suspend fun BackendClient.queryVibe(prompt: String, topK: Int = 10): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/ai/query", JSONObject().apply {
        put("prompt", prompt)
        put("top_k", topK)
    }.toString())
}

/** Track mood and emotional valence evaluation. */
suspend fun BackendClient.queryMood(title: String = "", artist: String = "", lyrics: String = ""): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/ai/mood", JSONObject().apply {
        put("title", title)
        put("artist", artist)
        put("lyrics", lyrics)
    }.toString())
}

/** 128-D vector cosine similarity benchmark. */
suspend fun BackendClient.vectorSimilarity(vectorA: FloatArray, vectorB: FloatArray): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/vector/similarity", JSONObject().apply {
        put("vector_a", JSONArray().also { arr -> vectorA.forEach { arr.put(it.toDouble()) } })
        put("vector_b", JSONArray().also { arr -> vectorB.forEach { arr.put(it.toDouble()) } })
    }.toString())
}

/** Offline smart radio mix generator based on seed track. */
suspend fun BackendClient.getRecommendations(seedId: String = "", limit: Int = 10): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/recommend?id=${URLEncoder.encode(seedId, "UTF-8")}&limit=$limit")
}

// ==================== Custom Playlists (SQLite-backed) ====================

suspend fun BackendClient.getPlaylists(): List<CustomPlaylist> = withContext(Dispatchers.IO) {
    val (code, json) = get("/api/v1/playlists")
    if (code != 200 || json.isBlank()) return@withContext emptyList()
    val list = mutableListOf<CustomPlaylist>()
    try {
        val root = JSONObject(json)
        val arr = root.optJSONArray("playlists") ?: return@withContext emptyList()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(parsePlaylistJson(obj))
        }
    } catch (_: Exception) {}
    list
}

suspend fun BackendClient.createPlaylist(
    title: String,
    description: String = "",
    coverUrl: String = "",
    initialTracks: List<TrackItem> = emptyList(),
    id: String? = null
): CustomPlaylist? = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        if (!id.isNullOrBlank()) {
            put("id", id)
        }
        put("title", title)
        put("description", description)
        put("cover_url", coverUrl)
        val tracksArr = JSONArray()
        for (t in initialTracks) {
            tracksArr.put(
                JSONObject().apply {
                    put("id", t.id)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("album", t.album)
                    put("duration_ms", t.durationMs)
                    put("stream_url", t.streamUrl)
                    put("cover_url", t.coverUrl)
                    put("source", t.source)
                }
            )
        }
        put("tracks", tracksArr)
    }
    val (code, json) = post("/api/v1/playlists", payload.toString())
    if (code !in 200..299 || json.isBlank()) return@withContext null
    try {
        val root = JSONObject(json)
        val pObj = root.optJSONObject("playlist") ?: return@withContext null
        parsePlaylistJson(pObj)
    } catch (_: Exception) {
        null
    }
}

suspend fun BackendClient.updatePlaylistDetails(
    id: String,
    title: String,
    description: String = "",
    coverUrl: String = ""
): CustomPlaylist? = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("title", title)
        put("description", description)
        put("cover_url", coverUrl)
    }
    val (code, json) = put("/api/v1/playlists/$id", payload.toString())
    if (code !in 200..299 || json.isBlank()) return@withContext null
    try {
        val root = JSONObject(json)
        val pObj = root.optJSONObject("playlist") ?: return@withContext null
        parsePlaylistJson(pObj)
    } catch (_: Exception) {
        null
    }
}

suspend fun BackendClient.deletePlaylist(id: String): Boolean = withContext(Dispatchers.IO) {
    val (code, _) = delete("/api/v1/playlists/$id")
    code in 200..299
}

suspend fun BackendClient.addTrackToPlaylist(playlistId: String, track: TrackItem): CustomPlaylist? = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("duration_ms", track.durationMs)
        put("stream_url", track.streamUrl)
        put("cover_url", track.coverUrl)
        put("source", track.source)
    }
    val (code, json) = post("/api/v1/playlists/$playlistId/tracks", payload.toString())
    if (code !in 200..299 || json.isBlank()) return@withContext null
    try {
        val root = JSONObject(json)
        val pObj = root.optJSONObject("playlist") ?: return@withContext null
        parsePlaylistJson(pObj)
    } catch (_: Exception) {
        null
    }
}

suspend fun BackendClient.removeTrackFromPlaylist(playlistId: String, position: Int): CustomPlaylist? = withContext(Dispatchers.IO) {
    val (code, json) = delete("/api/v1/playlists/$playlistId/tracks/$position")
    if (code !in 200..299 || json.isBlank()) return@withContext null
    try {
        val root = JSONObject(json)
        val pObj = root.optJSONObject("playlist") ?: return@withContext null
        parsePlaylistJson(pObj)
    } catch (_: Exception) {
        null
    }
}

suspend fun BackendClient.reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int): CustomPlaylist? = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("from_index", fromIndex)
        put("to_index", toIndex)
    }
    val (code, json) = put("/api/v1/playlists/$playlistId/reorder", payload.toString())
    if (code !in 200..299 || json.isBlank()) return@withContext null
    try {
        val root = JSONObject(json)
        val pObj = root.optJSONObject("playlist") ?: return@withContext null
        parsePlaylistJson(pObj)
    } catch (_: Exception) {
        null
    }
}

// ==================== User Favorites (SQLite-backed) ====================

suspend fun BackendClient.getFavorites(): List<TrackItem> = withContext(Dispatchers.IO) {
    val (code, json) = get("/api/v1/favorites")
    if (code != 200 || json.isBlank()) return@withContext emptyList()
    val list = mutableListOf<TrackItem>()
    try {
        val root = JSONObject(json)
        val arr = root.optJSONArray("favorites") ?: return@withContext emptyList()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                TrackItem(
                    id = obj.optString("track_id"),
                    title = obj.optString("title"),
                    artist = obj.optString("artist"),
                    album = obj.optString("album"),
                    durationMs = obj.optLong("duration_ms"),
                    streamUrl = obj.optString("stream_url"),
                    coverUrl = obj.optString("cover_url"),
                    source = obj.optString("source", "local")
                )
            )
        }
    } catch (_: Exception) {}
    list
}

suspend fun BackendClient.toggleFavorite(track: TrackItem): Boolean = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("track_id", track.id)
        put("title", track.title)
        put("artist", track.artist)
        put("album", track.album)
        put("duration_ms", track.durationMs)
        put("stream_url", track.streamUrl)
        put("cover_url", track.coverUrl)
        put("source", track.source)
    }
    val (code, json) = post("/api/v1/favorites/toggle", payload.toString())
    if (code !in 200..299 || json.isBlank()) return@withContext false
    try {
        val root = JSONObject(json)
        root.optBoolean("favorite", false)
    } catch (_: Exception) {
        false
    }
}

fun BackendClient.parsePlaylistJson(obj: JSONObject): CustomPlaylist {
    val id = obj.optString("id")
    val title = obj.optString("title")
    val description = obj.optString("description")
    val coverUrl = obj.optString("cover_url")
    val createdAt = obj.optLong("created_at")
    val updatedAt = obj.optLong("updated_at")
    val tracksList = mutableListOf<TrackItem>()
    val arr = obj.optJSONArray("tracks")
    if (arr != null) {
        for (j in 0 until arr.length()) {
            val tObj = arr.getJSONObject(j)
            tracksList.add(
                TrackItem(
                    id = tObj.optString("id"),
                    title = tObj.optString("title"),
                    artist = tObj.optString("artist"),
                    album = tObj.optString("album"),
                    durationMs = tObj.optLong("duration_ms"),
                    streamUrl = tObj.optString("stream_url"),
                    coverUrl = tObj.optString("cover_url"),
                    source = tObj.optString("source", "local")
                )
            )
        }
    }
    return CustomPlaylist(
        id = id,
        title = title,
        description = description,
        coverUrl = coverUrl,
        tracks = tracksList,
        createdAt = createdAt,
        updatedAt = updatedAt
    )
}

// ==================== Application Settings ====================

/** Fetches all persisted application key-value settings. */
suspend fun BackendClient.getAppSettings(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/settings")
}

/** Stores or updates a key-value setting pair. */
suspend fun BackendClient.setAppSetting(key: String, value: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().put("key", key).put("value", value).toString()
    post("/api/v1/settings", payload)
}

/** Parses settings map from JSON response. */
fun BackendClient.parseSettings(jsonStr: String): Map<String, String> {
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

/** YouTube podcasts browser with resume position. */
suspend fun BackendClient.getPodcasts(podcastId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/podcasts/browse?id=${URLEncoder.encode(podcastId, "UTF-8")}")
}

/** Fetches YouTube Music algorithmic automix radio queue for a seed videoId. */
suspend fun BackendClient.getRadioNext(videoId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(videoId, "UTF-8")
    get("/api/v1/radio/next?videoId=$encoded")
}

/** Parses raw radio/next response JSON into TrackItem list. */
fun BackendClient.parseRadioNext(jsonStr: String): List<TrackItem> {
    if (jsonStr.isBlank()) return emptyList()
    return try {
        val root = JSONObject(jsonStr)
        val tracksArr = root.optJSONArray("tracks") ?: return emptyList()
        val list = mutableListOf<TrackItem>()
        for (i in 0 until tracksArr.length()) {
            val tObj = tracksArr.getJSONObject(i)
            val tId = tObj.optString("id")
            if (tId.isBlank()) continue
            val durSec = tObj.optLong("duration_seconds", 0L)
            val durMs = if (durSec > 0) durSec * 1000 else tObj.optLong("duration_ms", 0L)
            val cover = tObj.optString("thumbnail_url").ifBlank {
                "https://i.ytimg.com/vi/$tId/hqdefault.jpg"
            }
            list.add(
                TrackItem(
                    id = tId,
                    title = tObj.optString("title"),
                    artist = tObj.optString("artist"),
                    album = tObj.optString("album"),
                    durationMs = durMs,
                    coverUrl = cover,
                    streamUrl = "",
                    source = tObj.optString("source", "youtube")
                )
            )
        }
        list
    } catch (_: Exception) {
        emptyList()
    }
}

// ==================== Social, Rooms & Utilities ====================


/** Create a shared listening room. */
suspend fun BackendClient.createRoom(): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/rooms/create", "")
}

/** Join an existing listening room. */
suspend fun BackendClient.joinRoom(code: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/rooms/join", JSONObject().apply { put("code", code) }.toString())
}

/** Sync room playback position. */
suspend fun BackendClient.syncRoom(code: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/rooms/sync?code=${URLEncoder.encode(code, "UTF-8")}")
}

/** Discover local LAN peers. */
suspend fun BackendClient.getPeers(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/peers")
}

/** Check for app updates from GitHub releases. */
suspend fun BackendClient.checkForUpdates(version: String? = null): Pair<Int, String> = withContext(Dispatchers.IO) {
    val query = if (!version.isNullOrBlank()) "?version=${java.net.URLEncoder.encode(version, "UTF-8")}" else ""
    get("/api/v1/updater/check$query")
}
