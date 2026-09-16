/*
 * Package: com.cubicreates.unboundmusic.data
 * File: BackupRestoreManager.kt
 * Purpose: Full Backup & Restore engine for Unbound Music (Custom Playlists, Favorites, Queue, Audio Settings).
 *          Standard one-click JSON backup compatible across device upgrades and re-installs.
 * Subsystem: Data Persistence & Backup Engine
 */

package com.cubicreates.unboundmusic.data

import android.content.Context
import android.util.Log
import com.cubicreates.unboundmusic.ui.components.TrackItem
import org.json.JSONArray
import org.json.JSONObject

data class BackupRestoreResult(
    val success: Boolean,
    val playlistsRestored: Int = 0,
    val favoritesRestored: Int = 0,
    val settingsRestored: Boolean = false,
    val errorMessage: String? = null
)

object BackupRestoreManager {
    private const val TAG = "BackupRestoreManager"
    const val CURRENT_BACKUP_VERSION = 1

    /**
     * Exports full user library, playlists, favorites, playback history, and settings to a JSON string.
     */
    fun exportBackupJson(context: Context): String {
        val root = JSONObject()
        root.put("app", "UnboundMusic")
        root.put("version", CURRENT_BACKUP_VERSION)
        root.put("export_timestamp", System.currentTimeMillis())

        // 1. Custom Playlists
        val playlists = LocalPlaylistStore.getPlaylists(context)
        val playlistsArr = JSONArray()
        for (pl in playlists) {
            val plObj = JSONObject().apply {
                put("id", pl.id)
                put("title", pl.title)
                put("description", pl.description)
                put("cover_url", pl.coverUrl)
                put("created_at", pl.createdAt)
                put("updated_at", pl.updatedAt)

                val tracksArr = JSONArray()
                for (t in pl.tracks) {
                    val tObj = JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("album", t.album)
                        put("duration_ms", t.durationMs)
                        put("cover_url", t.coverUrl)
                        put("stream_url", t.streamUrl)
                        put("source", t.source)
                    }
                    tracksArr.put(tObj)
                }
                put("tracks", tracksArr)
            }
            playlistsArr.put(plObj)
        }
        root.put("custom_playlists", playlistsArr)

        // 2. Favorites
        val favorites = PlaybackStateStore.getFavoriteTrackIds(context)
        val favArr = JSONArray()
        for (id in favorites) {
            favArr.put(id)
        }
        root.put("favorite_track_ids", favArr)

        // 3. Saved Playback / Queue State
        val savedState = PlaybackStateStore.loadPlaybackState(context)
        if (savedState != null) {
            val pbObj = JSONObject().apply {
                put("position_ms", savedState.positionMs)
                val trackObj = JSONObject().apply {
                    put("id", savedState.track.id)
                    put("title", savedState.track.title)
                    put("artist", savedState.track.artist)
                    put("album", savedState.track.album)
                    put("duration_ms", savedState.track.durationMs)
                    put("cover_url", savedState.track.coverUrl)
                    put("stream_url", savedState.track.streamUrl)
                    put("source", savedState.track.source)
                }
                put("track", trackObj)

                val queueArr = JSONArray()
                for (t in savedState.queue) {
                    val qObj = JSONObject().apply {
                        put("id", t.id)
                        put("title", t.title)
                        put("artist", t.artist)
                        put("album", t.album)
                        put("duration_ms", t.durationMs)
                        put("cover_url", t.coverUrl)
                        put("stream_url", t.streamUrl)
                        put("source", t.source)
                    }
                    queueArr.put(qObj)
                }
                put("queue", queueArr)
            }
            root.put("saved_playback_state", pbObj)
        }

        // 4. Preferences & Settings
        val settingsObj = JSONObject().apply {
            put("auto_download_liked", PlaybackStateStore.isAutoDownloadLiked(context))
            put("skip_silence", PlaybackStateStore.isSkipSilence(context))
            put("normalize_volume", PlaybackStateStore.isNormalizeVolume(context))
            put("sponsor_block", PlaybackStateStore.isSponsorBlockEnabled(context))
            put("streaming_quality", PlaybackStateStore.getStreamingQuality(context).id)
            put("download_quality", PlaybackStateStore.getDownloadQuality(context).id)
        }
        root.put("settings", settingsObj)

        return root.toString(2)
    }

    /**
     * Validates and restores user library, playlists, favorites, and settings from a JSON string.
     */
    fun restoreBackupJson(
        context: Context,
        jsonString: String,
        overwritePlaylists: Boolean = false
    ): BackupRestoreResult {
        return try {
            val root = JSONObject(jsonString)

            // Validate format
            val app = root.optString("app", "")
            if (app.isNotBlank() && !app.contains("Unbound", ignoreCase = true) && !app.contains("Music", ignoreCase = true)) {
                return BackupRestoreResult(success = false, errorMessage = "Unsupported backup file signature: $app")
            }

            var playlistsRestored = 0
            var favoritesRestored = 0
            var settingsRestored = false

            // 1. Restore Custom Playlists
            val playlistsArr = root.optJSONArray("custom_playlists")
            if (playlistsArr != null) {
                val list = mutableListOf<CustomPlaylist>()
                for (i in 0 until playlistsArr.length()) {
                    val pObj = playlistsArr.optJSONObject(i) ?: continue
                    val id = pObj.optString("id", "pl_${System.currentTimeMillis()}_$i")
                    val title = pObj.optString("title", "Imported Playlist")
                    val desc = pObj.optString("description", "")
                    val cover = pObj.optString("cover_url", "")
                    val createdAt = pObj.optLong("created_at", System.currentTimeMillis())
                    val updatedAt = pObj.optLong("updated_at", System.currentTimeMillis())

                    val tracks = mutableListOf<TrackItem>()
                    val tracksArr = pObj.optJSONArray("tracks")
                    if (tracksArr != null) {
                        for (j in 0 until tracksArr.length()) {
                            val tObj = tracksArr.optJSONObject(j) ?: continue
                            tracks.add(
                                TrackItem(
                                    id = tObj.optString("id"),
                                    title = tObj.optString("title"),
                                    artist = tObj.optString("artist"),
                                    album = tObj.optString("album"),
                                    durationMs = tObj.optLong("duration_ms", 0L),
                                    coverUrl = tObj.optString("cover_url"),
                                    streamUrl = tObj.optString("stream_url"),
                                    source = tObj.optString("source", "backup")
                                )
                            )
                        }
                    }

                    list.add(
                        CustomPlaylist(
                            id = id,
                            title = title,
                            description = desc,
                            coverUrl = cover,
                            tracks = tracks,
                            createdAt = createdAt,
                            updatedAt = updatedAt
                        )
                    )
                }

                playlistsRestored = LocalPlaylistStore.importPlaylists(context, list, overwritePlaylists)
            }

            // 2. Restore Favorites
            val favArr = root.optJSONArray("favorite_track_ids")
            if (favArr != null) {
                val currentFavs = PlaybackStateStore.getFavoriteTrackIds(context).toMutableSet()
                for (i in 0 until favArr.length()) {
                    val favId = favArr.optString(i)
                    if (favId.isNotBlank()) currentFavs.add(favId)
                }
                PlaybackStateStore.setFavoriteTrackIds(context, currentFavs)
                favoritesRestored = favArr.length()
            }

            // 3. Restore Playback State if present
            val pbObj = root.optJSONObject("saved_playback_state")
            if (pbObj != null) {
                val posMs = pbObj.optLong("position_ms", 0L)
                val trackObj = pbObj.optJSONObject("track")
                val track = if (trackObj != null) {
                    TrackItem(
                        id = trackObj.optString("id"),
                        title = trackObj.optString("title"),
                        artist = trackObj.optString("artist"),
                        album = trackObj.optString("album"),
                        durationMs = trackObj.optLong("duration_ms", 0L),
                        coverUrl = trackObj.optString("cover_url"),
                        streamUrl = trackObj.optString("stream_url"),
                        source = trackObj.optString("source", "backup")
                    )
                } else null

                val queueArr = pbObj.optJSONArray("queue")
                val queue = mutableListOf<TrackItem>()
                if (queueArr != null) {
                    for (i in 0 until queueArr.length()) {
                        val qObj = queueArr.optJSONObject(i) ?: continue
                        queue.add(
                            TrackItem(
                                id = qObj.optString("id"),
                                title = qObj.optString("title"),
                                artist = qObj.optString("artist"),
                                album = qObj.optString("album"),
                                durationMs = qObj.optLong("duration_ms", 0L),
                                coverUrl = qObj.optString("cover_url"),
                                streamUrl = qObj.optString("stream_url"),
                                source = qObj.optString("source", "backup")
                            )
                        )
                    }
                }

                if (track != null) {
                    PlaybackStateStore.savePlaybackState(context, track, queue, posMs)
                }
            }

            // 4. Restore Settings
            val settingsObj = root.optJSONObject("settings")
            if (settingsObj != null) {
                if (settingsObj.has("auto_download_liked")) {
                    PlaybackStateStore.setAutoDownloadLiked(context, settingsObj.optBoolean("auto_download_liked"))
                }
                if (settingsObj.has("skip_silence")) {
                    PlaybackStateStore.setSkipSilence(context, settingsObj.optBoolean("skip_silence"))
                }
                if (settingsObj.has("normalize_volume")) {
                    PlaybackStateStore.setNormalizeVolume(context, settingsObj.optBoolean("normalize_volume"))
                }
                if (settingsObj.has("sponsor_block")) {
                    PlaybackStateStore.setSponsorBlockEnabled(context, settingsObj.optBoolean("sponsor_block"))
                }
                if (settingsObj.has("streaming_quality")) {
                    PlaybackStateStore.setStreamingQuality(
                        context,
                        AudioQuality.fromId(settingsObj.optString("streaming_quality"))
                    )
                }
                if (settingsObj.has("download_quality")) {
                    PlaybackStateStore.setDownloadQuality(
                        context,
                        AudioQuality.fromId(settingsObj.optString("download_quality"))
                    )
                }
                settingsRestored = true
            }

            Log.i(TAG, "Backup restored successfully: $playlistsRestored playlists, $favoritesRestored favorites.")
            BackupRestoreResult(
                success = true,
                playlistsRestored = playlistsRestored,
                favoritesRestored = favoritesRestored,
                settingsRestored = settingsRestored
            )
        } catch (e: Exception) {
            Log.e(TAG, "restoreBackupJson failed: ${e.message}", e)
            BackupRestoreResult(
                success = false,
                errorMessage = e.message ?: "Failed to parse backup JSON file"
            )
        }
    }
}
