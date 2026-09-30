/*
 * Package: com.cubicreates.unboundmusic.data
 * File: BackendClientDownloader.kt
 * Purpose: Extension functions for BackendClient covering offline download dispatch,
 *          task status polling, pause/resume, cancel, deletion, and local audio parsing.
 * Subsystem: Native Go Engine REST Client - Offline Downloader
 */

package com.cubicreates.unboundmusic.data

import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder

/** Starts an asynchronous chunked download worker for the given track. */
suspend fun BackendClient.startDownload(
    videoId: String,
    title: String,
    artist: String,
    album: String = "",
    artworkUrl: String = "",
    streamUrl: String = ""
): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("video_id", videoId)
        put("title", title)
        put("artist", artist)
        put("album", album)
        put("artwork_url", artworkUrl)
        if (streamUrl.isNotBlank()) {
            put("stream_url", streamUrl)
        }
    }
    post("/api/v1/download/start", payload.toString())
}

/** Legacy signature for starting download with minimal fields. */
suspend fun BackendClient.downloadStart(
    trackId: String,
    title: String,
    artist: String,
    album: String
): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/download/start", JSONObject().apply {
        put("track_id", trackId)
        put("title", title)
        put("artist", artist)
        put("album", album)
    }.toString())
}

/** Queries the current progress and state of an offline download. */
suspend fun BackendClient.getDownloadStatus(videoId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val encoded = URLEncoder.encode(videoId, "UTF-8")
    get("/api/v1/download/status?video_id=$encoded")
}

/** Returns all active, queued, or completed download tasks. */
suspend fun BackendClient.getActiveDownloads(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/download/active")
}

/** Pauses an ongoing download task. */
suspend fun BackendClient.pauseDownload(videoId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply { put("video_id", videoId) }
    post("/api/v1/download/pause", payload.toString())
}

/** Resumes a paused download task. */
suspend fun BackendClient.resumeDownload(videoId: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply { put("video_id", videoId) }
    post("/api/v1/download/resume", payload.toString())
}

/** Cancels an active download and deletes partial .part artifacts. */
suspend fun BackendClient.cancelDownload(videoId: String, title: String = ""): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("video_id", videoId)
        put("track_id", videoId)
        if (title.isNotBlank()) put("title", title)
    }
    post("/api/v1/download/cancel", payload.toString())
}

/** Purges a downloaded track from physical disk and SQLite database. */
suspend fun BackendClient.deleteDownload(
    videoId: String,
    deleteFile: Boolean = true,
    title: String = ""): Pair<Int, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("video_id", videoId)
        put("track_id", videoId)
        if (title.isNotBlank()) put("title", title)
        put("delete_file", deleteFile)
    }
    post("/api/v1/download/delete", payload.toString())
}

/** Parses a DownloadTaskDto from JSON string. */
fun BackendClient.parseDownloadTask(jsonStr: String): DownloadTaskDto? {
    return try {
        val obj = JSONObject(jsonStr)
        DownloadTaskDto(
            videoId = obj.optString("video_id", obj.optString("track_id", "")),
            title = obj.optString("title", ""),
            artist = obj.optString("artist", ""),
            album = obj.optString("album", ""),
            artworkUrl = obj.optString("artwork_url", ""),
            targetFormat = obj.optString("target_format", "opus"),
            status = obj.optString("status", "QUEUED"),
            downloadedBytes = obj.optLong("downloaded_bytes", obj.optLong("bytes_written", 0L)),
            totalBytes = obj.optLong("total_bytes", 0L),
            progress = obj.optDouble("progress", obj.optDouble("percent", 0.0)),
            localPath = obj.optString("local_path", ""),
            error = obj.optString("error", "")
        )
    } catch (_: Exception) {
        null
    }
}

/** Parses a list of active download tasks from JSON array response. */
fun BackendClient.parseActiveDownloads(jsonStr: String): List<DownloadTaskDto> {
    val list = mutableListOf<DownloadTaskDto>()
    try {
        val arr = org.json.JSONArray(jsonStr)
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            list.add(
                DownloadTaskDto(
                    videoId = obj.optString("video_id", obj.optString("track_id", "")),
                    title = obj.optString("title", ""),
                    artist = obj.optString("artist", ""),
                    album = obj.optString("album", ""),
                    artworkUrl = obj.optString("artwork_url", ""),
                    targetFormat = obj.optString("target_format", "opus"),
                    status = obj.optString("status", "QUEUED"),
                    downloadedBytes = obj.optLong("downloaded_bytes", obj.optLong("bytes_written", 0L)),
                    totalBytes = obj.optLong("total_bytes", 0L),
                    progress = obj.optDouble("progress", obj.optDouble("percent", 0.0)),
                    localPath = obj.optString("local_path", ""),
                    error = obj.optString("error", "")
                )
            )
        }
    } catch (_: Exception) {}
    return list
}

/** Lists all physical tracks downloaded to Unbound/Downloads. */
suspend fun BackendClient.getDownloadedFiles(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/download/list")
}

/** Alias for getDownloadedFiles() */
suspend fun BackendClient.downloadList(): Pair<Int, String> = getDownloadedFiles()

/** Parses tracks from /api/v1/download/list JSON response into TrackItem list. */
fun BackendClient.parseDownloadedFiles(jsonStr: String): List<TrackItem> {
    val list = mutableListOf<TrackItem>()
    if (jsonStr.isBlank()) return list
    try {
        val root = JSONObject(jsonStr)
        val arr = root.optJSONArray("tracks") ?: return list
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id", "")
            val title = obj.optString("title", "Unknown Track")
            val artist = obj.optString("artist", "Unknown Artist")
            val album = obj.optString("album", "")
            val thumb = obj.optString("thumbnail_url", "")
            val localPath = obj.optString("local_path", "")
            val durMs = obj.optLong("duration_ms", 0L)
            val stream = if (localPath.isNotBlank()) "file://$localPath" else obj.optString("stream_url", "")
            val resolvedThumb = thumb.ifBlank {
                if (localPath.isNotBlank()) {
                    val f = File(localPath)
                    val coverFile = File("$localPath.cover.jpg")
                    val altCover = File(f.parentFile, "${f.nameWithoutExtension}.cover.jpg")
                    when {
                        coverFile.exists() && coverFile.length() > 0 -> "file://${coverFile.absolutePath}"
                        altCover.exists() && altCover.length() > 0 -> "file://${altCover.absolutePath}"
                        else -> ""
                    }
                } else ""
            }.ifBlank {
                if (id.length == 11 && !id.startsWith("local_")) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else ""
            }
            list.add(
                TrackItem(
                    id = id,
                    title = title,
                    artist = artist,
                    album = album,
                    coverUrl = resolvedThumb,
                    streamUrl = stream,
                    durationMs = durMs,
                    source = "Unbound Downloads"
                )
            )
        }
    } catch (_: Exception) {}
    return list
}
