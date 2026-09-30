/*
 * Package: com.cubicreates.unboundmusic.data
 * File: BackendClientAccount.kt
 * Purpose: Extension functions for BackendClient covering YouTube Account authentication,
 *          library synchronization, liked songs, smart feed, device auth flow, and taste tracking.
 * Subsystem: Native Go Engine REST Client - Account & Auth
 */

package com.cubicreates.unboundmusic.data

import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

/** Authenticates YouTube cookies with local Go engine and triggers library synchronization. */
suspend fun BackendClient.syncAccount(cookie: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().put("cookie", cookie).toString()
    post("/api/v1/account/sync", payload)
}

/** Alias for syncAccount() for backward compatibility. */
suspend fun BackendClient.accountSync(cookie: String): Pair<Int, String> = syncAccount(cookie)

/** Retrieves YouTube connection state, user account name, and synced track count. */
suspend fun BackendClient.getAccountStatus(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/account/status")
}

/** Clears stored YouTube cookies and local synced cache on device. */
suspend fun BackendClient.disconnectAccount(): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/account/disconnect", "{}")
}

/** Initiates zero-typing OAuth 2.0 device flow for YouTube on TV / device activation. */
suspend fun BackendClient.startDeviceAuth(clientId: String? = null): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        if (!clientId.isNullOrBlank()) put("client_id", clientId)
    }.toString()
    post("/api/v1/account/device/start", payload)
}

/** Polls Google's device token endpoint via local Go daemon. */
suspend fun BackendClient.pollDeviceAuth(
    deviceCode: String,
    clientId: String? = null,
    clientSecret: String? = null
): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("device_code", deviceCode)
        if (!clientId.isNullOrBlank()) put("client_id", clientId)
        if (!clientSecret.isNullOrBlank()) put("client_secret", clientSecret)
    }.toString()
    post("/api/v1/account/device/poll", payload)
}

/** Parses DeviceCodeData response from JSON. */
fun BackendClient.parseDeviceCodeData(jsonStr: String): DeviceCodeData? {
    return try {
        val root = JSONObject(jsonStr)
        DeviceCodeData(
            deviceCode = root.optString("device_code", ""),
            userCode = root.optString("user_code", ""),
            verificationUrl = root.optString("verification_url", "https://www.youtube.com/activate"),
            expiresIn = root.optInt("expires_in", 1800),
            interval = root.optInt("interval", 5)
        )
    } catch (_: Exception) {
        null
    }
}

/** Retrieves all cached synced Liked Music tracks from Go daemon. */
suspend fun BackendClient.getLikedTracks(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/account/liked")
}

/** Alias for getLikedTracks() */
suspend fun BackendClient.getAccountLiked(): Pair<Int, String> = getLikedTracks()

/** Dispatches a like or unlike mutation for a video ID directly to YouTube Music InnerTube. */
suspend fun BackendClient.toggleTrackLike(videoId: String, isLiked: Boolean): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().put("video_id", videoId).put("like", isLiked).toString()
    post("/api/v1/track/like", payload)
}

/** Parses AccountStatusData payload from JSON string. */
fun BackendClient.parseAccountStatus(jsonStr: String): AccountStatusData? {
    return try {
        val root = JSONObject(jsonStr)
        AccountStatusData(
            connected = root.optBoolean("connected", false),
            accountName = root.optString("account_name", "Local User"),
            syncedTracksCount = root.optInt("synced_tracks_count", 0),
            lastSynced = root.optString("last_synced", ""),
            avatarUrl = root.optString("avatar_url", "")
        )
    } catch (_: Exception) {
        null
    }
}

/** Parses liked tracks array from JSON string. */
fun BackendClient.parseLikedTracks(jsonStr: String): List<TrackItem> {
    val list = mutableListOf<TrackItem>()
    try {
        val trimmed = jsonStr.trim()
        val arr = if (trimmed.startsWith("[")) {
            org.json.JSONArray(trimmed)
        } else {
            val root = JSONObject(trimmed)
            root.optJSONArray("tracks") ?: return list
        }
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
                    streamUrl = "",
                    source = "YouTube Liked"
                )
            )
        }
    } catch (_: Exception) {}
    return list
}

/** Streams continuous music tracks and related radio recommendations for infinite home scroll. */
suspend fun BackendClient.getAccountFeedInfinite(seed: String? = null): Pair<Int, String> = withContext(Dispatchers.IO) {
    val query = if (!seed.isNullOrBlank()) "?seed=${URLEncoder.encode(seed, "UTF-8")}" else ""
    get("/api/v1/account/feed/infinite$query")
}

/** Fetches native algorithmic smart recommendations (variations, quick picks, artist spotlights). */
suspend fun BackendClient.getSmartFeed(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/feed/smart")
}

/** Parses SmartFeedDto from JSON string. */
fun BackendClient.parseSmartFeed(json: String): SmartFeedDto? {
    if (json.isBlank()) return null
    return try {
        val root = JSONObject(json)
        val hasPersonalization = root.optBoolean("has_personalization", false)
        val shelvesArr = root.optJSONArray("shelves")
        val shelves = mutableListOf<SmartShelfDto>()
        if (shelvesArr != null) {
            for (i in 0 until shelvesArr.length()) {
                val sObj = shelvesArr.getJSONObject(i)
                val tracksArr = sObj.optJSONArray("tracks")
                val tracks = mutableListOf<TrackItem>()
                if (tracksArr != null) {
                    for (j in 0 until tracksArr.length()) {
                        val tObj = tracksArr.getJSONObject(j)
                        tracks.add(
                            TrackItem(
                                id = tObj.optString("id"),
                                title = tObj.optString("title"),
                                artist = tObj.optString("artist"),
                                album = tObj.optString("album"),
                                durationMs = tObj.optLong("duration_ms"),
                                coverUrl = tObj.optString("thumbnail_url").ifBlank {
                                    "https://i.ytimg.com/vi/${tObj.optString("id")}/hqdefault.jpg"
                                },
                                streamUrl = ""
                            )
                        )
                    }
                }
                shelves.add(
                    SmartShelfDto(
                        id = sObj.optString("id"),
                        title = sObj.optString("title"),
                        subtitle = sObj.optString("subtitle"),
                        type = sObj.optString("type"),
                        tracks = tracks
                    )
                )
            }
        }
        SmartFeedDto(hasPersonalization, shelves)
    } catch (_: Exception) {
        null
    }
}

/** Parses mixes array from JSON string. */
fun BackendClient.parseUserMixes(jsonStr: String): List<MixDto> {
    val list = mutableListOf<MixDto>()
    try {
        val root = JSONObject(jsonStr.trim())
        val arr = root.optJSONArray("mixes") ?: return list
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val id = m.optString("id", "")
            if (id.isBlank()) continue
            list.add(
                MixDto(
                    id = id,
                    title = m.optString("title", "Music Mix"),
                    subtitle = m.optString("subtitle", "YouTube Mix"),
                    coverUrl = m.optString("thumbnail_url", "")
                )
            )
        }
    } catch (_: Exception) {}
    return list
}

/** Import playlist from Spotify URL. */
suspend fun BackendClient.importSpotify(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/import/spotify", JSONObject().apply { put("url", url) }.toString())
}

/** Ingests physical listening event (skip, completion, replay) into on-device taste engine. */
suspend fun BackendClient.recordTasteEvent(
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
suspend fun BackendClient.getTasteProfile(limit: Int = 10): TasteProfileResponse? = withContext(Dispatchers.IO) {
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
