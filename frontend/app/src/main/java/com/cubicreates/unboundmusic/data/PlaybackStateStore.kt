/*
 * Package: com.cubicreates.unboundmusic.data
 * File: PlaybackStateStore.kt
 * Purpose: SharedPreferences persistence for playback automation, settings, and last played queue.
 */

package com.cubicreates.unboundmusic.data

import android.content.Context
import android.content.SharedPreferences
import com.cubicreates.unboundmusic.ui.components.TrackItem
import org.json.JSONArray
import org.json.JSONObject

enum class AudioQuality(val id: String, val title: String, val subtitle: String) {
    LOW("low", "Data Saver (48 kbps)", "48 kbps OPUS – Conserves mobile data"),
    MEDIUM("medium", "Standard (128 kbps)", "128 kbps OPUS – Great balance"),
    HIGH("high", "High (256 kbps)", "256 kbps OPUS/AAC – Maximum fidelity");

    companion object {
        fun fromId(id: String): AudioQuality {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: HIGH
        }
    }
}

data class SavedPlaybackState(
    val track: TrackItem,
    val queue: List<TrackItem>,
    val positionMs: Long
)

object PlaybackStateStore {
    private const val PREFS_NAME = "unbound_playback_state_prefs"

    private const val KEY_AUTO_DOWNLOAD_LIKED = "key_auto_download_liked"
    private const val KEY_SKIP_SILENCE = "key_skip_silence"
    private const val KEY_NORMALIZE_VOLUME = "key_normalize_volume"
    private const val KEY_SPONSOR_BLOCK = "key_sponsor_block_enabled"
    private const val KEY_STREAMING_QUALITY = "key_streaming_quality"
    private const val KEY_DOWNLOAD_QUALITY = "key_download_quality"
    private const val KEY_FAVORITE_TRACK_IDS = "key_favorite_track_ids"

    private const val KEY_HAS_SAVED_STATE = "key_has_saved_state"
    private const val KEY_SAVED_TRACK = "key_saved_track"
    private const val KEY_SAVED_QUEUE = "key_saved_queue"
    private const val KEY_SAVED_POSITION_MS = "key_saved_position_ms"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // ==================== Audio Quality Settings ====================

    fun getStreamingQuality(context: Context): AudioQuality {
        val raw = getPrefs(context).getString(KEY_STREAMING_QUALITY, AudioQuality.HIGH.id) ?: AudioQuality.HIGH.id
        return AudioQuality.fromId(raw)
    }

    fun setStreamingQuality(context: Context, quality: AudioQuality) {
        getPrefs(context).edit().putString(KEY_STREAMING_QUALITY, quality.id).apply()
    }

    fun getDownloadQuality(context: Context): AudioQuality {
        val raw = getPrefs(context).getString(KEY_DOWNLOAD_QUALITY, AudioQuality.HIGH.id) ?: AudioQuality.HIGH.id
        return AudioQuality.fromId(raw)
    }

    fun setDownloadQuality(context: Context, quality: AudioQuality) {
        getPrefs(context).edit().putString(KEY_DOWNLOAD_QUALITY, quality.id).apply()
    }

    // ==================== Favorites Persistence ====================

    fun getFavoriteTrackIds(context: Context): Set<String> {
        val json = getPrefs(context).getString(KEY_FAVORITE_TRACK_IDS, null) ?: return emptySet()
        return try {
            val arr = JSONArray(json)
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) {
                val id = arr.optString(i)
                if (id.isNotBlank()) set.add(id)
            }
            set
        } catch (_: Exception) {
            emptySet()
        }
    }

    fun setFavoriteTrackIds(context: Context, ids: Set<String>) {
        try {
            val arr = JSONArray()
            for (id in ids) {
                if (id.isNotBlank()) arr.put(id)
            }
            getPrefs(context).edit().putString(KEY_FAVORITE_TRACK_IDS, arr.toString()).apply()
        } catch (_: Exception) {}
    }

    fun addFavoriteTrackId(context: Context, id: String) {
        if (id.isBlank()) return
        val current = getFavoriteTrackIds(context).toMutableSet()
        current.add(id)
        setFavoriteTrackIds(context, current)
    }

    fun removeFavoriteTrackId(context: Context, id: String) {
        if (id.isBlank()) return
        val current = getFavoriteTrackIds(context).toMutableSet()
        current.remove(id)
        setFavoriteTrackIds(context, current)
    }

    fun isFavoriteTrack(context: Context, id: String): Boolean {
        if (id.isBlank()) return false
        return getFavoriteTrackIds(context).contains(id)
    }

    // ==================== Playback Automation Settings ====================

    fun isAutoDownloadLiked(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_AUTO_DOWNLOAD_LIKED, false)
    }

    fun setAutoDownloadLiked(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_AUTO_DOWNLOAD_LIKED, enabled).apply()
    }

    fun isSkipSilence(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SKIP_SILENCE, false)
    }

    fun setSkipSilence(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SKIP_SILENCE, enabled).apply()
    }

    fun isNormalizeVolume(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_NORMALIZE_VOLUME, false)
    }

    fun setNormalizeVolume(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_NORMALIZE_VOLUME, enabled).apply()
    }

    fun isSponsorBlockEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_SPONSOR_BLOCK, true)
    }

    fun setSponsorBlockEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_SPONSOR_BLOCK, enabled).apply()
    }

    // ==================== Queue & Position Persistence ====================

    fun savePlaybackState(context: Context, track: TrackItem, queue: List<TrackItem>, positionMs: Long) {
        if (track.id.isBlank() && track.title.isBlank()) return
        try {
            val trackJson = trackToJson(track)
            val queueArray = JSONArray()
            val queueToSave = if (queue.isNotEmpty()) queue.take(100) else listOf(track)
            for (t in queueToSave) {
                queueArray.put(trackToJson(t))
            }

            getPrefs(context).edit()
                .putBoolean(KEY_HAS_SAVED_STATE, true)
                .putString(KEY_SAVED_TRACK, trackJson.toString())
                .putString(KEY_SAVED_QUEUE, queueArray.toString())
                .putLong(KEY_SAVED_POSITION_MS, positionMs)
                .apply()
        } catch (_: Exception) {}
    }

    fun loadPlaybackState(context: Context): SavedPlaybackState? {
        val prefs = getPrefs(context)
        if (!prefs.getBoolean(KEY_HAS_SAVED_STATE, false)) return null
        return try {
            val trackStr = prefs.getString(KEY_SAVED_TRACK, null) ?: return null
            val queueStr = prefs.getString(KEY_SAVED_QUEUE, null) ?: return null
            val posMs = prefs.getLong(KEY_SAVED_POSITION_MS, 0L)

            val track = jsonToTrack(JSONObject(trackStr))
            val queueArr = JSONArray(queueStr)
            val queue = mutableListOf<TrackItem>()
            for (i in 0 until queueArr.length()) {
                val obj = queueArr.optJSONObject(i) ?: continue
                queue.add(jsonToTrack(obj))
            }

            SavedPlaybackState(
                track = track,
                queue = if (queue.isNotEmpty()) queue else listOf(track),
                positionMs = posMs
            )
        } catch (_: Exception) {
            null
        }
    }

    fun clearPlaybackState(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_HAS_SAVED_STATE)
            .remove(KEY_SAVED_TRACK)
            .remove(KEY_SAVED_QUEUE)
            .remove(KEY_SAVED_POSITION_MS)
            .apply()
    }

    // ==================== JSON Helpers ====================

    private fun trackToJson(track: TrackItem): JSONObject {
        return JSONObject().apply {
            put("id", track.id)
            put("title", track.title)
            put("artist", track.artist)
            put("album", track.album)
            put("duration_ms", track.durationMs)
            put("cover_url", track.coverUrl)
            put("stream_url", track.streamUrl)
            put("source", track.source)
        }
    }

    private fun jsonToTrack(obj: JSONObject): TrackItem {
        return TrackItem(
            id = obj.optString("id"),
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            album = obj.optString("album"),
            durationMs = obj.optLong("duration_ms", 0L),
            coverUrl = obj.optString("cover_url"),
            streamUrl = obj.optString("stream_url"),
            source = obj.optString("source", "youtube")
        )
    }
}
