/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: MainViewModelDownloads.kt
 * Purpose: Offline downloader orchestration, storage metrics, public media export, and cache clearing.
 * Subsystem: ViewModel / Domain Extensions
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.data.AlbumCompletionStatus
import com.cubicreates.unboundmusic.data.DownloadTaskDto
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "MainViewModelDownloads"

/**
 * Initiates a physical background chunked download for an audio track in MP3 format.
 */
fun MainViewModel.startTrackDownload(track: TrackItem) {
    if (track.title.isBlank() && track.id.isBlank()) return

    val taskId = if (track.id.isNotBlank()) track.id else "custom_${System.currentTimeMillis()}"
    recentlyCancelledIds.remove(taskId)
    recentlyCancelledIds.remove(track.id)
    if (track.title.isNotBlank()) recentlyCancelledIds.remove(track.title.lowercase())

    // 1. Optimistic UI update: immediately show DOWNLOADING on the DownloadButton
    val optimisticTask = DownloadTaskDto(
        videoId = taskId,
        title = track.title,
        artist = track.artist,
        album = track.album,
        artworkUrl = track.coverUrl,
        targetFormat = "mp3",
        status = "DOWNLOADING",
        progress = 0.0
    )
    _downloadTasks.value = _downloadTasks.value + (taskId to optimisticTask)

    // 2. User feedback: In-app Toast + Android System Notification
    com.cubicreates.unboundmusic.util.UnboundToast.show(
        getApplication(),
        "Downloading '${track.title}' to Unbound/Downloads (MP3)...",
        isLong = false
    )
    downloadNotificationHelper.notifyDownloadStarted(taskId, track.title, track.artist)

    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.startDownload(
                videoId = track.id,
                title = track.title,
                artist = track.artist,
                album = track.album,
                artworkUrl = track.coverUrl,
                streamUrl = track.streamUrl
            )
            if (code in 200..299 && resp.isNotBlank()) {
                val task = client.parseDownloadTask(resp)
                if (task != null) {
                    _downloadTasks.value = _downloadTasks.value + (task.videoId to task)
                    startDownloadPollingLoop()
                    // Phase 6: Pre-fetch and cache lyrics offline in SQLite without altering active playback UI
                    launch {
                        try {
                            client.getLyrics(
                                trackId = task.videoId,
                                title = track.title,
                                artist = track.artist,
                                durationMs = track.durationMs
                            )
                        } catch (_: Exception) {}
                    }
                }
            } else {
                Log.e(TAG, "startTrackDownload failed HTTP $code: $resp")
                downloadNotificationHelper.notifyDownloadFailed(taskId, track.title, track.artist, "Download rejected ($code)")
                withContext(Dispatchers.Main) {
                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                        getApplication(),
                        "Download failed for '${track.title}' ($code)"
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "startTrackDownload failed: ${e.message}")
            downloadNotificationHelper.notifyDownloadFailed(taskId, track.title, track.artist, e.message ?: "Network error")
            withContext(Dispatchers.Main) {
                com.cubicreates.unboundmusic.util.UnboundToast.show(
                    getApplication(),
                    "Download failed: ${e.message}"
                )
            }
        }
    }
}

/**
 * Cancels an ongoing download and clears partial artifacts immediately.
 */
fun MainViewModel.cancelTrackDownload(videoId: String, title: String = "") {
    downloadNotificationHelper.cancelNotification()
    
    if (videoId.isNotBlank()) recentlyCancelledIds.add(videoId)
    val effectiveTitle = title.ifBlank { _currentTrack.value.title }
    if (effectiveTitle.isNotBlank()) recentlyCancelledIds.add(effectiveTitle.lowercase())

    // Optimistically remove from _downloadTasks immediately so UI resets to NOT_DOWNLOADED instantly
    val current = _downloadTasks.value.toMutableMap()
    val matchKey = current.keys.firstOrNull { key ->
        key == videoId ||
        current[key]?.videoId == videoId ||
        (effectiveTitle.isNotBlank() && current[key]?.title.equals(effectiveTitle, ignoreCase = true)) ||
        (videoId.isNotBlank() && current[key]?.title.equals(videoId, ignoreCase = true))
    }
    if (matchKey != null) {
        val task = current.remove(matchKey)
        _downloadTasks.value = current
        if (task != null && task.videoId.isNotBlank()) {
            recentlyCancelledIds.add(task.videoId)
            downloadNotificationHelper.cancelNotification(task.videoId)
        }
    } else if (effectiveTitle.isNotBlank()) {
        val toRemove = current.filterValues { it.title.equals(effectiveTitle, ignoreCase = true) }.keys
        for (k in toRemove) {
            current.remove(k)
        }
        _downloadTasks.value = current
    }

    com.cubicreates.unboundmusic.util.UnboundToast.show(
        getApplication(),
        "Download cancelled",
        isLong = false
    )

    viewModelScope.launch(Dispatchers.IO) {
        try {
            client.cancelDownload(videoId, effectiveTitle)
        } catch (e: Exception) {
            Log.e(TAG, "cancelTrackDownload failed: ${e.message}")
        }
    }
}

/**
 * Removes an offline track from disk and local database.
 */
fun MainViewModel.deleteTrackDownload(videoId: String, title: String = "") {
    downloadNotificationHelper.cancelNotification(videoId)
    val current = _downloadTasks.value.toMutableMap()
    val effectiveTitle = title.ifBlank { _currentTrack.value.title }
    val matchKey = current.keys.firstOrNull { key ->
        key == videoId ||
        current[key]?.videoId == videoId ||
        (effectiveTitle.isNotBlank() && current[key]?.title.equals(effectiveTitle, ignoreCase = true)) ||
        (videoId.isNotBlank() && current[key]?.title.equals(videoId, ignoreCase = true))
    }
    if (matchKey != null) {
        current.remove(matchKey)
        _downloadTasks.value = current
    }
    _downloadedTrackIds.value = _downloadedTrackIds.value - videoId
    
    viewModelScope.launch(Dispatchers.IO) {
        try {
            client.deleteDownload(videoId, true, effectiveTitle)
            refreshLibrary()
        } catch (e: Exception) {
            Log.e(TAG, "deleteTrackDownload failed: ${e.message}")
        }
    }
}

/**
 * Polls active downloads from Go daemon until all tasks are completed, failed, or cancelled.
 */
fun MainViewModel.startDownloadPollingLoop() {
    if (isPollingDownloads) return
    isPollingDownloads = true

    viewModelScope.launch(Dispatchers.IO) {
        var prevBytes = 0L
        var prevTime = System.currentTimeMillis()

        try {
            while (isActive) {
                val (code, resp) = client.getActiveDownloads()
                if (code in 200..299 && resp.isNotBlank()) {
                    val activeTasks = client.parseActiveDownloads(resp)
                    val validTasks = activeTasks.filter { task ->
                        task.status != "CANCELLED" &&
                        !recentlyCancelledIds.contains(task.videoId) &&
                        !recentlyCancelledIds.contains(task.title.lowercase())
                    }
                    val taskMap = validTasks.associateBy { it.videoId }
                    _downloadTasks.value = taskMap

                    // Calculate live download speed across all downloading tasks
                    val currentBytes = validTasks.filter { it.status == "DOWNLOADING" || it.status == "TAGGING" }
                        .sumOf { it.downloadedBytes }
                    val now = System.currentTimeMillis()
                    val elapsedSec = (now - prevTime) / 1000.0
                    if (elapsedSec >= 0.8 && prevBytes > 0) {
                        val delta = (currentBytes - prevBytes).coerceAtLeast(0)
                        _downloadSpeedBps.value = (delta / elapsedSec).toLong()
                    }
                    prevBytes = currentBytes
                    prevTime = now

                    // Update Android notifications for active/failed tasks
                    val downloadingTask = validTasks.firstOrNull { it.status == "DOWNLOADING" || it.status == "TAGGING" }
                    if (downloadingTask != null) {
                        downloadNotificationHelper.notifyDownloadProgress(
                            downloadingTask.videoId,
                            downloadingTask.title,
                            downloadingTask.artist,
                            downloadingTask.progress.toInt()
                        )
                    }

                    val failedTask = validTasks.firstOrNull { it.status == "FAILED" }
                    if (failedTask != null) {
                        downloadNotificationHelper.notifyDownloadFailed(
                            failedTask.videoId,
                            failedTask.title,
                            failedTask.artist,
                            failedTask.error
                        )
                    }

                    val completedTasks = activeTasks.filter { it.status == "COMPLETED" }
                    val completedIds = completedTasks.map { it.videoId }.toSet()
                    if (completedIds.isNotEmpty()) {
                        val prevCompleted = _downloadedTrackIds.value
                        val newlyCompleted = completedTasks.filter { it.videoId !in prevCompleted }
                        _downloadedTrackIds.value = prevCompleted + completedIds
                        if (newlyCompleted.isNotEmpty()) {
                            refreshLibrary()
                            loadDownloadedMusicFiles()
                            for (task in newlyCompleted) {
                                downloadNotificationHelper.notifyDownloadCompleted(
                                    task.videoId,
                                    task.title,
                                    task.artist
                                )
                                withContext(Dispatchers.Main) {
                                    com.cubicreates.unboundmusic.util.UnboundToast.show(
                                        getApplication(),
                                        "Downloaded '${task.title}' as MP3 to Unbound/Downloads!",
                                        isLong = false
                                    )
                                }
                                if (task.localPath.isNotBlank()) {
                                    try {
                                        android.media.MediaScannerConnection.scanFile(
                                            getApplication<Application>().applicationContext,
                                            arrayOf(task.localPath),
                                            null
                                        ) { path, uri ->
                                            Log.i(TAG, "MediaScannerConnection indexed $path -> $uri")
                                        }
                                    } catch (scanErr: Exception) {
                                        Log.w(TAG, "MediaScanner scanFile failed: ${scanErr.message}")
                                    }
                                }
                            }
                        }
                    }

                    val hasActive = activeTasks.any {
                        it.status == "QUEUED" || it.status == "DOWNLOADING" || it.status == "TAGGING"
                    }
                    if (!hasActive) {
                        _downloadSpeedBps.value = 0L
                        break
                    }
                } else {
                    _downloadSpeedBps.value = 0L
                    break
                }
                delay(1000)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Download polling loop finished: ${e.message}")
        } finally {
            isPollingDownloads = false
            _downloadSpeedBps.value = 0L
        }
    }
}

/**
 * Pauses an active download.
 */
fun MainViewModel.pauseDownload(videoId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val current = _downloadTasks.value.toMutableMap()
            current[videoId]?.let {
                current[videoId] = it.copy(status = "PAUSED")
                _downloadTasks.value = current
            }
            client.pauseDownload(videoId)
        } catch (e: Exception) {
            Log.e(TAG, "pauseDownload failed: ${e.message}")
        }
    }
}

/**
 * Resumes a paused download.
 */
fun MainViewModel.resumeDownload(videoId: String) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val current = _downloadTasks.value.toMutableMap()
            current[videoId]?.let {
                current[videoId] = it.copy(status = "DOWNLOADING")
                _downloadTasks.value = current
            }
            client.resumeDownload(videoId)
            startDownloadPollingLoop()
        } catch (e: Exception) {
            Log.e(TAG, "resumeDownload failed: ${e.message}")
        }
    }
}

/**
 * Retries a failed or paused download.
 */
fun MainViewModel.retryDownload(task: DownloadTaskDto) {
    startTrackDownload(
        TrackItem(
            id = task.videoId,
            title = task.title,
            artist = task.artist,
            album = task.album,
            coverUrl = task.artworkUrl
        )
    )
}

/**
 * Loads physical downloaded music files and updates storage metrics.
 */
fun MainViewModel.loadDownloadedMusicFiles() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val (code, resp) = client.getDownloadedFiles()
            if (code in 200..299 && resp.isNotBlank()) {
                val tracks = client.parseDownloadedFiles(resp)
                _downloadedMusicTracks.value = tracks
                var totalBytes = 0L
                for (t in tracks) {
                    if (t.streamUrl.startsWith("file://")) {
                        val f = File(t.streamUrl.removePrefix("file://"))
                        if (f.exists()) {
                            totalBytes += f.length()
                        }
                    }
                }
                _downloadsSizeMB.value = (totalBytes / (1024.0 * 1024.0))
            } else {
                val local = client.getLocalTracks("Unbound Downloads")
                _downloadedMusicTracks.value = local.map { it.toTrackItem() }
                _downloadsSizeMB.value = local.sumOf { it.fileSize } / (1024.0 * 1024.0)
            }
            updateStorageMetrics()
        } catch (e: Exception) {
            Log.w(TAG, "loadDownloadedMusicFiles failed: ${e.message}")
        }
    }
}

/**
 * Recalculates storage usage and free space on device.
 */
fun MainViewModel.updateStorageMetrics() {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            var cacheBytes = 0L
            context.cacheDir?.let { cacheBytes += calculateDirectorySize(it) }
            context.externalCacheDir?.let { cacheBytes += calculateDirectorySize(it) }
            _cacheSizeMB.value = (cacheBytes / (1024.0 * 1024.0))

            val stat = android.os.StatFs(context.filesDir.absolutePath)
            val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
            _freeStorageGB.value = (freeBytes / (1024.0 * 1024.0 * 1024.0))
        } catch (_: Exception) {}
    }
}

internal fun calculateDirectorySize(dir: File): Long {
    if (!dir.exists()) return 0L
    if (dir.isFile) return dir.length()
    var size = 0L
    dir.listFiles()?.forEach { child ->
        size += if (child.isDirectory) calculateDirectorySize(child) else child.length()
    }
    return size
}

/**
 * Clears temporary image and stream caches without deleting user downloaded audio files.
 */
fun MainViewModel.clearCacheAndStorage(onResult: (String) -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            var freedAppCache = 0L
            context.cacheDir?.let {
                freedAppCache += calculateDirectorySize(it)
                it.deleteRecursively()
                it.mkdirs()
            }
            context.externalCacheDir?.let {
                freedAppCache += calculateDirectorySize(it)
                it.deleteRecursively()
                it.mkdirs()
            }
            val (code, resp) = client.purgeStorageCache()
            val backendFreed = if (code in 200..299) {
                client.parseCachePurgeResult(resp)?.freedBytes ?: 0L
            } else 0L

            val totalFreedMB = ((freedAppCache + backendFreed) / (1024.0 * 1024.0)).coerceAtLeast(0.0)
            updateStorageMetrics()
            withContext(Dispatchers.Main) {
                val msg = String.format("Freed %.1f MB cache! Offline downloads preserved.", totalFreedMB)
                com.cubicreates.unboundmusic.util.UnboundToast.show(context, msg, isLong = false)
                onResult(msg)
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val errMsg = "Cache clean error: ${e.message}"
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), errMsg, isLong = false)
                onResult(errMsg)
            }
        }
    }
}

/**
 * Triggers system MediaScanner to index Unbound/Downloads/ into public Android audio stores.
 */
fun MainViewModel.exportDownloadsToPublicStorage(onResult: (String) -> Unit = {}) {
    viewModelScope.launch(Dispatchers.IO) {
        try {
            val context = getApplication<Application>()
            val tracks = _downloadedMusicTracks.value
            val pathsToScan = mutableListOf<String>()

            for (t in tracks) {
                if (t.streamUrl.startsWith("file://")) {
                    val path = t.streamUrl.removePrefix("file://")
                    val f = File(path)
                    if (f.exists()) {
                        pathsToScan.add(f.absolutePath)
                    }
                }
            }

            if (pathsToScan.isNotEmpty()) {
                android.media.MediaScannerConnection.scanFile(
                    context,
                    pathsToScan.toTypedArray(),
                    null
                ) { path, uri ->
                    Log.i(TAG, "Exported & Indexed to MediaStore: $path -> $uri")
                }
                withContext(Dispatchers.Main) {
                    val msg = "Exported ${pathsToScan.size} tracks to device media library!"
                    com.cubicreates.unboundmusic.util.UnboundToast.show(context, msg, isLong = false)
                    onResult(msg)
                }
            } else {
                withContext(Dispatchers.Main) {
                    val msg = "No downloaded tracks to export."
                    com.cubicreates.unboundmusic.util.UnboundToast.show(context, msg, isLong = false)
                    onResult(msg)
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                val errMsg = "Export error: ${e.message}"
                com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), errMsg, isLong = false)
                onResult(errMsg)
            }
        }
    }
}

fun MainViewModel.downloadMissingAlbumTracks(status: AlbumCompletionStatus) {
    val missing = status.missingTracks
    if (missing.isEmpty()) {
        com.cubicreates.unboundmusic.util.UnboundToast.show(getApplication(), "Album is already complete!", isLong = false)
        return
    }
    for (track in missing) {
        startTrackDownload(track)
    }
    com.cubicreates.unboundmusic.util.UnboundToast.show(
        getApplication(),
        "Downloading ${missing.size} remaining tracks from '${status.albumTitle}'",
        isLong = true
    )
}
