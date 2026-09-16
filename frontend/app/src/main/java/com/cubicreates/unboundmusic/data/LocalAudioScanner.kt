/*
 * Package: com.cubicreates.unboundmusic.data
 * File: LocalAudioScanner.kt
 * Purpose: Universal VLC-style Local Audio Discovery Engine.
 *
 * Implements a dual-engine scanning architecture:
 * 1. Fast system MediaStore query for standard indexed audio.
 * 2. Deep recursive filesystem crawler that deliberately bypasses and ignores .nomedia flags
 *    to discover WhatsApp voice/audio notes, Telegram downloads, and hidden media folders.
 * 3. Unified deduplication and folder categorization.
 */

package com.cubicreates.unboundmusic.data

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object LocalAudioScanner {

    private const val TAG = "LocalAudioScanner"

    private val SUPPORTED_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "flac", "opus", "wav", "ogg", "wma", "m4b", "oga"
    )

    data class ScanResult(
        val allTracks: List<TrackItem>,
        val localTracks: List<LocalTrack>,
        val folders: Map<String, List<LocalTrack>>,
        val whatsappCount: Int,
        val telegramCount: Int,
        val downloadsCount: Int
    )

    /**
     * Executes the dual-engine scan:
     * 1) Fast MediaStore query
     * 2) Deep filesystem walk ignoring .nomedia files
     * 3) Deduplication by canonical path and folder categorization
     */
    suspend fun scanDeviceAudio(context: Context): ScanResult = withContext(Dispatchers.IO) {
        val discoveredByPath = LinkedHashMap<String, LocalTrack>()

        // Step 1: Query system MediaStore
        try {
            scanMediaStore(context, discoveredByPath)
            Log.d(TAG, "MediaStore pass found ${discoveredByPath.size} tracks")
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore scan warning: ${e.message}")
        }

        // Step 2: Deep direct filesystem crawler ignoring .nomedia flags
        try {
            val countBeforeCrawl = discoveredByPath.size
            scanFileSystemBypassingNoMedia(context, discoveredByPath)
            val addedByCrawl = discoveredByPath.size - countBeforeCrawl
            Log.d(TAG, "Filesystem crawler (.nomedia bypass) added $addedByCrawl hidden/unindexed tracks")
        } catch (e: Exception) {
            Log.w(TAG, "Filesystem crawler warning: ${e.message}")
        }

        val allLocalList = discoveredByPath.values.toList()

        // Step 3: Categorize into Audio Folders
        val folders = mutableMapOf<String, MutableList<LocalTrack>>()
        var whatsappCount = 0
        var telegramCount = 0
        var downloadsCount = 0

        for (track in allLocalList) {
            val folderKey = track.sourceFolder.ifBlank { "Device Audio" }
            folders.getOrPut(folderKey) { mutableListOf() }.add(track)

            val lowerKey = folderKey.lowercase(Locale.ROOT)
            val lowerPath = track.filePath.lowercase(Locale.ROOT)
            if (lowerKey.contains("whatsapp") || lowerPath.contains("whatsapp")) {
                whatsappCount++
            } else if (lowerKey.contains("telegram") || lowerPath.contains("telegram")) {
                telegramCount++
            } else if (lowerKey.contains("download") || lowerPath.contains("download") || lowerKey.contains("unbound")) {
                downloadsCount++
            }
        }

        val allTracksItems = allLocalList.map { it.toTrackItem() }

        ScanResult(
            allTracks = allTracksItems,
            localTracks = allLocalList,
            folders = folders,
            whatsappCount = whatsappCount,
            telegramCount = telegramCount,
            downloadsCount = downloadsCount
        )
    }

    /**
     * Queries MediaStore for instantly accessible indexed tracks.
     */
    private fun scanMediaStore(
        context: Context,
        discovered: MutableMap<String, LocalTrack>
    ) {
        val resolver = context.contentResolver
        val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

        val projection = mutableListOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            projection.add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
        }

        // Include audio tracks with positive duration or valid size
        val selection = "${MediaStore.Audio.Media.SIZE} >= 8192"

        resolver.query(
            uri,
            projection.toTypedArray(),
            selection,
            null,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC"
        )?.use { cursor ->
            val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
            val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
            val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
            val dateModCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_MODIFIED)
            val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            } else -1

            while (cursor.moveToNext()) {
                try {
                    val idVal = if (idCol >= 0) cursor.getLong(idCol) else 0L
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "" else ""
                    val artist = if (artistCol >= 0) cursor.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val album = if (albumCol >= 0) cursor.getString(albumCol) ?: "" else ""
                    val duration = if (durCol >= 0) cursor.getLong(durCol) else 0L
                    val rawPath = if (dataCol >= 0) cursor.getString(dataCol) ?: "" else ""
                    val fileSize = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateMod = if (dateModCol >= 0) cursor.getLong(dateModCol) else 0L
                    val bucketName = if (bucketCol >= 0) cursor.getString(bucketCol) ?: "" else ""

                    val contentUri = ContentUris.withAppendedId(uri, idVal)
                    val effectivePath = if (rawPath.isNotBlank()) rawPath else contentUri.toString()
                    val canonicalKey = try {
                        if (rawPath.isNotBlank()) File(rawPath).canonicalPath.lowercase(Locale.ROOT) else effectivePath.lowercase(Locale.ROOT)
                    } catch (_: Exception) {
                        effectivePath.lowercase(Locale.ROOT)
                    }

                    val sourceFolder = inferCategoryFromMetadata(rawPath, bucketName)

                    val trackId = hashPath(canonicalKey)
                    val localTrack = LocalTrack(
                        id = trackId,
                        filePath = effectivePath,
                        title = title.ifBlank {
                            if (rawPath.isNotBlank()) File(rawPath).nameWithoutExtension else "Track $idVal"
                        },
                        artist = if (artist.isBlank() || artist == "<unknown>") "Unknown Artist" else artist,
                        album = album,
                        durationMs = duration,
                        format = if (rawPath.isNotBlank()) File(rawPath).extension.lowercase(Locale.ROOT).ifBlank { "mp3" } else "mp3",
                        fileSize = fileSize,
                        sourceFolder = sourceFolder,
                        dateIndexed = System.currentTimeMillis() / 1000,
                        mtime = dateMod
                    )

                    discovered[canonicalKey] = localTrack
                } catch (e: Exception) {
                    // Skip corrupt entry
                }
            }
        }
    }

    /**
     * Crawls device storage directories directly, explicitly ignoring .nomedia files.
     * This brings in WhatsApp Audio, WhatsApp Voice Notes, Telegram Audio, and hidden download folders.
     */
    private fun scanFileSystemBypassingNoMedia(
        context: Context,
        discovered: MutableMap<String, LocalTrack>
    ) {
        val rootCandidates = mutableListOf<File>()

        // 1. High-priority known chat & offline directories
        val primaryStorage = Environment.getExternalStorageDirectory()
        if (primaryStorage != null && primaryStorage.exists()) {
            rootCandidates.add(File(primaryStorage, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio"))
            rootCandidates.add(File(primaryStorage, "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes"))
            rootCandidates.add(File(primaryStorage, "WhatsApp/Media/WhatsApp Audio"))
            rootCandidates.add(File(primaryStorage, "WhatsApp/Media/WhatsApp Voice Notes"))
            rootCandidates.add(File(primaryStorage, "Telegram/Telegram Audio"))
            rootCandidates.add(File(primaryStorage, "Android/data/org.telegram.messenger/files/Telegram/Telegram Audio"))
            rootCandidates.add(File(primaryStorage, "Download"))
            rootCandidates.add(File(primaryStorage, "Music"))
            rootCandidates.add(File(primaryStorage, "Podcasts"))
            rootCandidates.add(File(primaryStorage, "Audiobooks"))
            rootCandidates.add(File(primaryStorage, "Recordings"))
            rootCandidates.add(File(primaryStorage, "Ringtones"))
            rootCandidates.add(File(primaryStorage, "Notifications"))
        }

        // 2. Add public standard media directories
        try {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.let { rootCandidates.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)?.let { rootCandidates.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PODCASTS)?.let { rootCandidates.add(it) }
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_RECORDINGS)?.let { rootCandidates.add(it) }
        } catch (_: Exception) {}

        // 3. Add removable SD cards / OTG storage
        try {
            val externalFilesDirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in externalFilesDirs) {
                if (dir != null) {
                    val path = dir.absolutePath
                    val androidDataIndex = path.indexOf("/Android/")
                    if (androidDataIndex > 0) {
                        val sdRootPath = path.substring(0, androidDataIndex)
                        val sdRoot = File(sdRootPath)
                        if (sdRoot.exists() && sdRoot.canRead()) {
                            rootCandidates.add(sdRoot)
                        }
                    }
                }
            }

            // Inspect /storage mounts directly for SD cards
            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { mount ->
                    val name = mount.name
                    if (name != "emulated" && name != "self" && mount.isDirectory && mount.canRead()) {
                        rootCandidates.add(mount)
                    }
                }
            }
        } catch (_: Exception) {}

        // 4. Also scan root primary storage for general custom music folders
        if (primaryStorage != null && primaryStorage.exists()) {
            rootCandidates.add(primaryStorage)
        }

        val visitedDirs = HashSet<String>()

        for (candidate in rootCandidates.distinctBy { it.absolutePath }) {
            if (candidate.exists() && candidate.isDirectory && candidate.canRead()) {
                crawlDirectory(
                    dir = candidate,
                    maxDepth = if (candidate == primaryStorage) 4 else 8,
                    currentDepth = 0,
                    visitedDirs = visitedDirs,
                    discovered = discovered
                )
            }
        }
    }

    /**
     * Recursively traverses directories without stopping or skipping on .nomedia flags.
     */
    private fun crawlDirectory(
        dir: File,
        maxDepth: Int,
        currentDepth: Int,
        visitedDirs: HashSet<String>,
        discovered: MutableMap<String, LocalTrack>
    ) {
        if (currentDepth > maxDepth) return

        val canonicalDirPath = try {
            dir.canonicalPath
        } catch (_: Exception) {
            dir.absolutePath
        }

        if (!visitedDirs.add(canonicalDirPath)) {
            return
        }

        val entries = try {
            dir.listFiles()
        } catch (_: Exception) {
            null
        } ?: return

        for (entry in entries) {
            try {
                if (entry.isDirectory) {
                    val name = entry.name
                    // Skip system internals or app caches to optimize speed & prevent loops
                    if (name == ".git" || name == ".gradle" || name == ".idea" || name == "proc" || name == "sys" || name == "cache") {
                        continue
                    }
                    val abs = entry.absolutePath
                    if (abs.contains("/Android/data/com.cubicreates.unboundmusic/cache") ||
                        abs.contains("/code_cache") ||
                        abs.contains("/.thumbnails")
                    ) {
                        continue
                    }

                    // Notice: Even if entry contains a .nomedia file inside it,
                    // we DO NOT skip! We continue walking into it!
                    crawlDirectory(entry, maxDepth, currentDepth + 1, visitedDirs, discovered)
                } else if (entry.isFile) {
                    // Do not treat .nomedia file as an audio track
                    if (entry.name.equals(".nomedia", ignoreCase = true)) {
                        continue
                    }

                    val ext = entry.extension.lowercase(Locale.ROOT)
                    if (SUPPORTED_EXTENSIONS.contains(ext)) {
                        if (entry.length() < 8192) continue // Skip corrupt or zero-byte files

                        val normKey = try {
                            entry.canonicalPath.lowercase(Locale.ROOT)
                        } catch (_: Exception) {
                            entry.absolutePath.lowercase(Locale.ROOT)
                        }

                        // If not already found by MediaStore (which suppresses .nomedia files), extract tags
                        if (!discovered.containsKey(normKey)) {
                            val category = inferFolderCategory(entry)
                            val track = extractTrackMetadata(entry, category)
                            discovered[normKey] = track
                        }
                    }
                }
            } catch (_: Exception) {
                // Ignore single file or subfolder read permission issues
            }
        }
    }

    /**
     * Extracts embedded audio metadata from unindexed physical audio files using MediaMetadataRetriever.
     */
    private fun extractTrackMetadata(file: File, sourceFolder: String): LocalTrack {
        var title = file.nameWithoutExtension
        var artist = "Unknown Artist"
        var album = sourceFolder
        var durationMs = 0L

        var mmr: MediaMetadataRetriever? = null
        try {
            mmr = MediaMetadataRetriever()
            mmr.setDataSource(file.absolutePath)

            val metaTitle = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            if (!metaTitle.isNullOrBlank()) {
                title = metaTitle.trim()
            }

            val metaArtist = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
                ?: mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST)
            if (!metaArtist.isNullOrBlank()) {
                artist = metaArtist.trim()
            }

            val metaAlbum = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
            if (!metaAlbum.isNullOrBlank()) {
                album = metaAlbum.trim()
            }

            val durStr = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            durationMs = durStr?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            // Header parsing failed; filename fallback is already set
        } finally {
            try {
                mmr?.release()
            } catch (_: Exception) {}
        }

        val canonical = try {
            file.canonicalPath
        } catch (_: Exception) {
            file.absolutePath
        }

        val id = hashPath(canonical)
        val ext = file.extension.lowercase(Locale.ROOT).ifBlank { "mp3" }

        return LocalTrack(
            id = id,
            filePath = file.absolutePath,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            format = ext,
            fileSize = file.length(),
            sourceFolder = sourceFolder,
            dateIndexed = System.currentTimeMillis() / 1000,
            mtime = file.lastModified() / 1000
        )
    }

    private fun inferCategoryFromMetadata(rawPath: String, bucketName: String): String {
        val lowerPath = rawPath.lowercase(Locale.ROOT)
        val lowerBucket = bucketName.lowercase(Locale.ROOT)

        return when {
            lowerPath.contains("whatsapp audio") || lowerBucket.contains("whatsapp audio") -> "WhatsApp Audio"
            lowerPath.contains("whatsapp voice") || lowerPath.contains("voice notes") -> "WhatsApp Voice Notes"
            lowerPath.contains("whatsapp") || lowerBucket.contains("whatsapp") -> "WhatsApp Audio"
            lowerPath.contains("telegram audio") || lowerBucket.contains("telegram audio") -> "Telegram Audio"
            lowerPath.contains("telegram") || lowerBucket.contains("telegram") -> "Telegram Audio"
            lowerPath.contains("download") || lowerBucket.contains("download") -> "Downloads"
            lowerPath.contains("unbound") -> "Unbound Downloads"
            lowerPath.contains("music") || lowerBucket.contains("music") -> "Music"
            lowerPath.contains("podcast") || lowerBucket.contains("podcast") -> "Podcasts"
            lowerPath.contains("audiobook") || lowerBucket.contains("audiobook") -> "Audiobooks"
            lowerPath.contains("recording") || lowerBucket.contains("recording") -> "Recordings"
            bucketName.isNotBlank() -> bucketName
            rawPath.isNotBlank() -> File(rawPath).parentFile?.name ?: "Device Audio"
            else -> "Device Audio"
        }
    }

    private fun inferFolderCategory(file: File): String {
        val lower = file.absolutePath.lowercase(Locale.ROOT)
        return when {
            lower.contains("whatsapp audio") -> "WhatsApp Audio"
            lower.contains("whatsapp voice") || lower.contains("voice notes") -> "WhatsApp Voice Notes"
            lower.contains("whatsapp") -> "WhatsApp Audio"
            lower.contains("telegram audio") -> "Telegram Audio"
            lower.contains("telegram") -> "Telegram Audio"
            lower.contains("/download") || lower.contains("/downloads") -> "Downloads"
            lower.contains("unbound") -> "Unbound Downloads"
            lower.contains("/music") -> "Music"
            lower.contains("podcast") -> "Podcasts"
            lower.contains("audiobook") -> "Audiobooks"
            lower.contains("recording") -> "Recordings"
            else -> file.parentFile?.name ?: "Device Audio"
        }
    }

    private fun hashPath(path: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(path.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }.take(16)
        } catch (_: Exception) {
            path.hashCode().toString()
        }
    }
}
