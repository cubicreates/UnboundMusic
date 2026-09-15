/*
 * Package: com.cubicreates.unboundmusic.data
 * File: LocalPlaylistStore.kt
 * Purpose: Local storage & persistence engine for user-created custom playlists.
 *          Provides zero-data offline persistence using SharedPreferences & JSON file mirroring.
 * Subsystem: Local Playlists Engine
 * Concurrency: Thread-safe synchronized operations.
 */

package com.cubicreates.unboundmusic.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.cubicreates.unboundmusic.ui.components.TrackItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * User-created custom local playlist.
 */
data class CustomPlaylist(
    val id: String,
    val title: String,
    val description: String = "",
    val coverUrl: String = "",
    val tracks: List<TrackItem> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    /** Total duration formatted as mm:ss or hh:mm:ss */
    val formattedDuration: String
        get() {
            val totalMs = tracks.sumOf { it.durationMs }
            val totalSec = totalMs / 1000
            val hours = totalSec / 3600
            val mins = (totalSec % 3600) / 60
            return if (hours > 0) {
                "${hours}h ${mins}m"
            } else {
                "${mins} mins"
            }
        }

    /** Effective cover image (custom cover or fallback to first track's cover) */
    val effectiveCoverUrl: String
        get() = coverUrl.ifBlank { tracks.firstOrNull { it.coverUrl.isNotBlank() }?.coverUrl ?: "" }
}

object LocalPlaylistStore {
    private const val TAG = "LocalPlaylistStore"
    private const val PREFS_NAME = "unbound_custom_playlists_prefs"
    private const val KEY_PLAYLISTS_INDEX = "key_playlists_index"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Returns all user-created playlists sorted by recently updated. */
    @Synchronized
    fun getPlaylists(context: Context): List<CustomPlaylist> {
        val prefs = getPrefs(context)
        val jsonStr = prefs.getString(KEY_PLAYLISTS_INDEX, null) ?: return emptyList()
        val list = mutableListOf<CustomPlaylist>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                list.add(jsonToPlaylist(obj))
            }
        } catch (e: Exception) {
            Log.e(TAG, "getPlaylists failed to parse: ${e.message}")
        }
        return list.sortedByDescending { it.updatedAt }
    }

    /** Creates a new custom playlist and persists it immediately. */
    @Synchronized
    fun createPlaylist(
        context: Context,
        title: String,
        description: String = "",
        coverUrl: String = "",
        initialTracks: List<TrackItem> = emptyList()
    ): CustomPlaylist {
        val current = getPlaylists(context).toMutableList()
        val newId = "pl_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}"
        val playlist = CustomPlaylist(
            id = newId,
            title = title.trim().ifBlank { "My Playlist" },
            description = description.trim(),
            coverUrl = coverUrl.trim(),
            tracks = initialTracks,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        current.add(0, playlist)
        saveAll(context, current)
        exportPlaylistToFile(context, playlist)
        return playlist
    }

    /** Updates title, description, and cover image for an existing playlist. */
    @Synchronized
    fun updatePlaylistDetails(
        context: Context,
        playlistId: String,
        title: String,
        description: String = "",
        coverUrl: String = ""
    ): CustomPlaylist? {
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null

        val updated = current[index].copy(
            title = title.trim().ifBlank { current[index].title },
            description = description.trim(),
            coverUrl = coverUrl.trim(),
            updatedAt = System.currentTimeMillis()
        )
        current[index] = updated
        saveAll(context, current)
        exportPlaylistToFile(context, updated)
        return updated
    }

    /** Deletes an existing custom playlist. */
    @Synchronized
    fun deletePlaylist(context: Context, playlistId: String): Boolean {
        val current = getPlaylists(context).toMutableList()
        val removed = current.removeAll { it.id == playlistId }
        if (removed) {
            saveAll(context, current)
            deletePlaylistFile(context, playlistId)
        }
        return removed
    }

    /** Adds a track to an existing playlist (avoids duplicates unless desired). */
    @Synchronized
    fun addTrackToPlaylist(
        context: Context,
        playlistId: String,
        track: TrackItem,
        allowDuplicates: Boolean = false
    ): Pair<Boolean, CustomPlaylist?> {
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return Pair(false, null)

        val target = current[index]
        if (!allowDuplicates && target.tracks.any { it.id.isNotBlank() && it.id == track.id }) {
            return Pair(false, target) // Track already in playlist
        }

        val updatedTracks = target.tracks + track
        val updated = target.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
        current[index] = updated
        saveAll(context, current)
        exportPlaylistToFile(context, updated)
        return Pair(true, updated)
    }

    /** Removes a track at a specific index from a playlist. */
    @Synchronized
    fun removeTrackFromPlaylist(context: Context, playlistId: String, trackIndex: Int): CustomPlaylist? {
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null

        val target = current[index]
        if (trackIndex !in target.tracks.indices) return target

        val updatedTracks = target.tracks.toMutableList().apply { removeAt(trackIndex) }
        val updated = target.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
        current[index] = updated
        saveAll(context, current)
        exportPlaylistToFile(context, updated)
        return updated
    }

    /** Reorders tracks inside a custom playlist (drag or move up/down). */
    @Synchronized
    fun reorderTracks(context: Context, playlistId: String, fromIndex: Int, toIndex: Int): CustomPlaylist? {
        val current = getPlaylists(context).toMutableList()
        val index = current.indexOfFirst { it.id == playlistId }
        if (index == -1) return null

        val target = current[index]
        if (fromIndex !in target.tracks.indices || toIndex !in target.tracks.indices) return target

        val updatedTracks = target.tracks.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
        val updated = target.copy(
            tracks = updatedTracks,
            updatedAt = System.currentTimeMillis()
        )
        current[index] = updated
        saveAll(context, current)
        exportPlaylistToFile(context, updated)
        return updated
    }

    // ==================== Serialization Helpers ====================

    private fun saveAll(context: Context, playlists: List<CustomPlaylist>) {
        try {
            val arr = JSONArray()
            for (p in playlists) {
                arr.put(playlistToJson(p))
            }
            getPrefs(context).edit().putString(KEY_PLAYLISTS_INDEX, arr.toString()).apply()
        } catch (e: Exception) {
            Log.e(TAG, "saveAll failed: ${e.message}")
        }
    }

    private fun playlistToJson(p: CustomPlaylist): JSONObject {
        return JSONObject().apply {
            put("id", p.id)
            put("title", p.title)
            put("description", p.description)
            put("cover_url", p.coverUrl)
            put("created_at", p.createdAt)
            put("updated_at", p.updatedAt)

            val tracksArr = JSONArray()
            for (t in p.tracks) {
                tracksArr.put(trackToJson(t))
            }
            put("tracks", tracksArr)
        }
    }

    private fun jsonToPlaylist(obj: JSONObject): CustomPlaylist {
        val id = obj.optString("id", "")
        val title = obj.optString("title", "Untitled")
        val desc = obj.optString("description", "")
        val cover = obj.optString("cover_url", "")
        val createdAt = obj.optLong("created_at", System.currentTimeMillis())
        val updatedAt = obj.optLong("updated_at", System.currentTimeMillis())

        val tracksList = mutableListOf<TrackItem>()
        val arr = obj.optJSONArray("tracks")
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val tObj = arr.optJSONObject(i) ?: continue
                tracksList.add(jsonToTrack(tObj))
            }
        }

        return CustomPlaylist(
            id = id,
            title = title,
            description = desc,
            coverUrl = cover,
            tracks = tracksList,
            createdAt = createdAt,
            updatedAt = updatedAt
        )
    }

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
            source = obj.optString("source", "local_playlist")
        )
    }

    private fun exportPlaylistToFile(context: Context, playlist: CustomPlaylist) {
        try {
            val dir = File(context.getExternalFilesDir(null), "Unbound/Playlists")
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "${playlist.id}.json")
            file.writeText(playlistToJson(playlist).toString(2))
        } catch (_: Exception) {}
    }

    private fun deletePlaylistFile(context: Context, playlistId: String) {
        try {
            val dir = File(context.getExternalFilesDir(null), "Unbound/Playlists")
            val file = File(dir, "${playlistId}.json")
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }
}
