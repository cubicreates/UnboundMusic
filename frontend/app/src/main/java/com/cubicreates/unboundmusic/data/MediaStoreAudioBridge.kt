/*
 * Package: com.cubicreates.unboundmusic.data
 * File: MediaStoreAudioBridge.kt
 * Purpose: Android System MediaStore Bridge.
 *          Queries system-indexed audio from Android's ContentResolver for immediate UI display
 *          and discovers storage roots (including external SD cards and chat folders)
 *          to feed into the native Go storage crawler daemon.
 * Subsystem: Offline Media Storage Bridge
 */

package com.cubicreates.unboundmusic.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale

object MediaStoreAudioBridge {

    private const val TAG = "MediaStoreAudioBridge"

    /**
     * Fast system MediaStore query returning tracks already indexed by Android OS.
     */
    suspend fun queryMediaStoreAudio(context: Context): List<LocalTrack> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<LocalTrack>()
        try {
            val resolver = context.contentResolver
            val uri: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

            val projection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.DATA,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.DATE_MODIFIED
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                projection.add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
            }

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
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
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
                        val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else -1L
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

                        val sourceFolder = inferCategory(rawPath, bucketName)
                        val trackId = hashPath(canonicalKey)

                        // 1. Resolve local companion artwork (.cover.jpg, folder.jpg, cover.jpg)
                        var resolvedCover = ""
                        if (rawPath.isNotBlank()) {
                            try {
                                val audioFile = File(rawPath)
                                val directCover = File("$rawPath.cover.jpg")
                                val baseCover = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.cover.jpg")
                                when {
                                    directCover.exists() && directCover.length() > 0 -> {
                                        resolvedCover = "file://${directCover.absolutePath}"
                                    }
                                    baseCover.exists() && baseCover.length() > 0 -> {
                                        resolvedCover = "file://${baseCover.absolutePath}"
                                    }
                                    else -> {
                                        val parent = audioFile.parentFile
                                        if (parent != null && parent.isDirectory) {
                                            val names = listOf("cover.jpg", "folder.jpg", "album.jpg", "front.jpg", "cover.png")
                                            for (candName in names) {
                                                val candFile = File(parent, candName)
                                                if (candFile.exists() && candFile.length() > 0) {
                                                    resolvedCover = "file://${candFile.absolutePath}"
                                                    break
                                                }
                                            }
                                        }
                                    }
                                }
                            } catch (_: Exception) {}
                        }

                        // 2. Fallback to MediaStore system album art URI
                        if (resolvedCover.isBlank() && albumId > 0) {
                            resolvedCover = ContentUris.withAppendedId(
                                Uri.parse("content://media/external/audio/albumart"),
                                albumId
                            ).toString()
                        }

                        tracks.add(
                            LocalTrack(
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
                                mtime = dateMod,
                                coverUrl = resolvedCover
                            )
                        )
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaStore query warning: ${e.message}")
        }
        tracks
    }

    /**
     * Resolves an Android content:// URI to its underlying POSIX filesystem path via MediaStore _DATA.
     */
    fun resolveContentUriToPath(context: Context, contentUriString: String): String? {
        if (!contentUriString.startsWith("content://")) return null
        return try {
            val uri = Uri.parse(contentUriString)
            val projection = arrayOf(MediaStore.Audio.Media.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                    if (idx >= 0) cursor.getString(idx) else null
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Discovers all physical storage root directories on the device to send to the Go crawler.
     */
    fun discoverDeviceStorageRoots(context: Context): List<String> {
        val roots = mutableListOf<String>()

        val primary = Environment.getExternalStorageDirectory()
        if (primary != null && primary.exists()) {
            val primaryPath = primary.absolutePath
            roots.add("$primaryPath/Music")
            roots.add("$primaryPath/Download")
            roots.add("$primaryPath/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Audio")
            roots.add("$primaryPath/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes")
            roots.add("$primaryPath/WhatsApp/Media/WhatsApp Audio")
            roots.add("$primaryPath/WhatsApp/Media/WhatsApp Voice Notes")
            roots.add("$primaryPath/Telegram/Telegram Audio")
            roots.add("$primaryPath/Android/data/org.telegram.messenger/files/Telegram/Telegram Audio")
            roots.add("$primaryPath/Podcasts")
            roots.add("$primaryPath/Audiobooks")
            roots.add("$primaryPath/Recordings")
        }

        // Removable SD cards
        try {
            val externalDirs = ContextCompat.getExternalFilesDirs(context, null)
            for (dir in externalDirs) {
                if (dir != null) {
                    val path = dir.absolutePath
                    val idx = path.indexOf("/Android/")
                    if (idx > 0) {
                        val sdRoot = path.substring(0, idx)
                        if (File(sdRoot).exists()) {
                            roots.add(sdRoot)
                        }
                    }
                }
            }

            val storageDir = File("/storage")
            if (storageDir.exists() && storageDir.isDirectory) {
                storageDir.listFiles()?.forEach { mount ->
                    if (mount.isDirectory && mount.name != "emulated" && mount.name != "self") {
                        roots.add(mount.absolutePath)
                    }
                }
            }
        } catch (_: Exception) {}

        return roots.distinct().filter { File(it).exists() }
    }

    private fun inferCategory(rawPath: String, bucketName: String): String {
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
