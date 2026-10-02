/*
 * Package: com.cubicreates.unboundmusic.data
 * File: BackendClientStorage.kt
 * Purpose: Extension functions for BackendClient covering Scoped Storage inspection,
 *          in-place virtual indexing, audio classification, acoustic fingerprinting, and cache purges.
 * Subsystem: Native Go Engine REST Client - Storage & Fingerprinting
 */

package com.cubicreates.unboundmusic.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/** Returns the provisioned Unbound/ directory tree structure. */
suspend fun BackendClient.getStorageTree(): Pair<Int, String> = withContext(Dispatchers.IO) {
    get("/api/v1/storage/tree")
}

/** Non-destructive in-place virtual audio indexer. */
suspend fun BackendClient.storageIndex(directoryPath: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/storage/index", JSONObject().apply { put("directory_path", directoryPath) }.toString())
}

/** Opt-in library consolidator (copy/move based on source rules). */
suspend fun BackendClient.storageConsolidate(sourceDir: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/storage/consolidate", JSONObject().apply { put("source_dir", sourceDir) }.toString())
}

/** Classify audio file as music vs voice/noise. */
suspend fun BackendClient.storageClassify(filePath: String, durationMs: Long): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/storage/classify", JSONObject().apply {
        put("file_path", filePath)
        put("duration_ms", durationMs)
    }.toString())
}

/** Multi-threaded directory scanner. */
suspend fun BackendClient.scanDirectory(directoryPath: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/scan", JSONObject().apply { put("directory_path", directoryPath) }.toString())
}

/** Triggers crawler & magic byte storage scan across paths. */
suspend fun BackendClient.scanStorage(paths: List<String>): StorageScanResponse? = withContext(Dispatchers.IO) {
    val arr = JSONArray()
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

/** Ingests an array of MediaStore-indexed audio tracks directly into the Go SQLite database. */
suspend fun BackendClient.ingestMediaStoreTracks(tracks: List<LocalTrack>): Boolean = withContext(Dispatchers.IO) {
    if (tracks.isEmpty()) return@withContext true
    try {
        val arr = JSONArray()
        for (track in tracks) {
            val obj = JSONObject().apply {
                put("id", track.id)
                put("file_path", track.filePath)
                put("title", track.title)
                put("artist", track.artist)
                put("album", track.album)
                put("duration_ms", track.durationMs)
                put("format", track.format)
                put("file_size", track.fileSize)
                put("source_folder", track.sourceFolder)
                put("cover_url", track.coverUrl)
                put("mtime", track.mtime)
                put("audio_category", track.audioCategory.name)
                put("is_identified_music", track.isIdentifiedMusic)
            }
            arr.put(obj)
        }
        val payload = JSONObject().apply { put("tracks", arr) }
        val (code, _) = post("/api/v1/storage/ingest-batch", payload.toString())
        code == 200
    } catch (_: Exception) {
        false
    }
}

/** Fetches indexed local tracks filtered by source. */
suspend fun BackendClient.getLocalTracks(source: String = "all"): List<LocalTrack> = withContext(Dispatchers.IO) {
    val (code, json) = get("/api/v1/storage/tracks?source=${URLEncoder.encode(source, "UTF-8")}")
    if (code != 200 || json.isBlank()) return@withContext emptyList()
    val list = mutableListOf<LocalTrack>()
    try {
        val root = JSONObject(json)
        val arr = root.optJSONArray("tracks") ?: return@withContext emptyList()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            val catName = obj.optString("audio_category", "MUSIC")
            val audioCategory = if (catName.equals(AudioCategory.MIXED_AUDIO.name, ignoreCase = true)) {
                AudioCategory.MIXED_AUDIO
            } else {
                AudioCategory.MUSIC
            }
            val isIdentified = obj.optBoolean("is_identified_music", audioCategory == AudioCategory.MUSIC)
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
                    mtime = obj.optLong("mtime"),
                    coverUrl = obj.optString("cover_url", ""),
                    audioCategory = audioCategory,
                    isIdentifiedMusic = isIdentified
                )
            )
        }
    } catch (_: Exception) {}
    list
}

/** Fetches indexed local folder summaries from Go daemon. */
suspend fun BackendClient.getLocalFolders(): List<LocalFolderDto> = withContext(Dispatchers.IO) {
    val (code, json) = get("/api/v1/storage/folders")
    if (code != 200 || json.isBlank()) return@withContext emptyList()
    val list = mutableListOf<LocalFolderDto>()
    try {
        val root = JSONObject(json)
        val arr = root.optJSONArray("folders") ?: return@withContext emptyList()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            list.add(
                LocalFolderDto(
                    name = obj.optString("name"),
                    count = obj.optInt("count"),
                    path = obj.optString("path")
                )
            )
        }
    } catch (_: Exception) {}
    list
}

/** Identifies an untagged local audio file using AcoustID + Chromaprint. */
suspend fun BackendClient.identifyFingerprint(filePath: String, fpcalcPath: String = ""): Pair<Int, String> = withContext(Dispatchers.IO) {
    val json = JSONObject().apply {
        put("file_path", filePath)
        if (fpcalcPath.isNotBlank()) put("fpcalc_path", fpcalcPath)
    }.toString()
    post("/api/v1/fingerprint/identify", json)
}

/** Identifies an untagged local audio file with rich status code and error reporting. */
suspend fun BackendClient.identifyTrackDetailed(filePath: String, fpcalcPath: String = ""): BackendClient.IdentifyTrackResult = withContext(Dispatchers.IO) {
    val (code, json) = identifyFingerprint(filePath, fpcalcPath)
    if (code !in 200..299 || json.isBlank()) {
        val err = try {
            if (json.isNotBlank()) JSONObject(json).optString("error", json) else "Empty response"
        } catch (_: Exception) {
            json.ifBlank { "HTTP $code" }
        }
        return@withContext BackendClient.IdentifyTrackResult(statusCode = code, errorMessage = err)
    }
    try {
        val obj = JSONObject(json)
        val dto = IdentifiedTrackDto(
            id = obj.optString("id", filePath),
            filePath = obj.optString("file_path", filePath),
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            album = obj.optString("album"),
            durationMs = obj.optLong("duration_ms", 0L),
            coverUrl = obj.optString("cover_url"),
            method = obj.optString("identification_method", "acoustid"),
            confidence = obj.optDouble("confidence", 0.0)
        )
        BackendClient.IdentifyTrackResult(track = dto, statusCode = code)
    } catch (e: Exception) {
        BackendClient.IdentifyTrackResult(statusCode = code, errorMessage = "JSON Parse Error: ${e.message}")
    }
}

/** Identifies an untagged local audio file using the 3-tier AcoustID + On-Device LLM engine. */
suspend fun BackendClient.identifyTrack(filePath: String, fpcalcPath: String = ""): IdentifiedTrackDto? = withContext(Dispatchers.IO) {
    identifyTrackDetailed(filePath, fpcalcPath).track
}

/** Triggers on-device storage cache purge across cache, tmp, and lyrics. */
suspend fun BackendClient.purgeStorageCache(): Pair<Int, String> = withContext(Dispatchers.IO) {
    post("/api/v1/storage/purge_cache", "{}")
}

/** Parses CachePurgeResult from JSON response. */
fun BackendClient.parseCachePurgeResult(jsonStr: String): CachePurgeResult? {
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

/**
 * Decompresses an archived payload (such as models.zst) into the destination directory using
 * the Go engine's high-speed streaming Zstandard decoder.
 */
suspend fun BackendClient.unpackPayload(archivePath: String, destDir: String): Boolean = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("archive_path", archivePath)
        put("dest_dir", destDir)
    }
    val (code, _) = post("/api/v1/system/unpack-payload", payload.toString())
    code == 200
}
