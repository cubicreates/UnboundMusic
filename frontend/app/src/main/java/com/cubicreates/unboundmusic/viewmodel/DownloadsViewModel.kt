/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: DownloadsViewModel.kt
 * Purpose: Dedicated Domain ViewModel for Scoped Storage offline audio management,
 *          background download orchestration, active task polling, and cache state tracking.
 * Subsystem: Domain Layer / Offline Storage & Downloads
 * Concurrency: Thread-safe reactive StateFlow orchestration on viewModelScope.
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Domain ViewModel encapsulating download lifecycle, task progress polling, and offline storage.
 */
class DownloadsViewModel(application: Application) : AndroidViewModel(application) {

    private val daemonManager = DaemonManager.getInstance(application)
    private val client = daemonManager.client

    private val _downloadTasks = MutableStateFlow<Map<String, DownloadTaskDto>>(emptyMap())
    val downloadTasks: StateFlow<Map<String, DownloadTaskDto>> = _downloadTasks.asStateFlow()

    private val _downloadedTrackIds = MutableStateFlow<Set<String>>(emptySet())
    val downloadedTrackIds: StateFlow<Set<String>> = _downloadedTrackIds.asStateFlow()

    private val _downloadedMusicTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val downloadedMusicTracks: StateFlow<List<TrackItem>> = _downloadedMusicTracks.asStateFlow()

    private var isPollingDownloads = false
    private val recentlyCancelledIds = mutableSetOf<String>()

    companion object {
        private const val TAG = "DownloadsViewModel"
    }

    fun startTrackDownload(track: TrackItem) {
        val taskId = track.id.ifBlank { track.title.hashCode().toString() }
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
                    }
                } else {
                    Log.e(TAG, "startTrackDownload failed HTTP $code: $resp")
                }
            } catch (e: Exception) {
                Log.e(TAG, "startTrackDownload failed: ${e.message}")
            }
        }
    }

    fun cancelTrackDownload(videoId: String, title: String = "") {
        if (videoId.isNotBlank()) recentlyCancelledIds.add(videoId)
        val current = _downloadTasks.value.toMutableMap()
        val matchKey = current.keys.firstOrNull { key ->
            key == videoId ||
            current[key]?.videoId == videoId ||
            (title.isNotBlank() && current[key]?.title.equals(title, ignoreCase = true))
        }
        if (matchKey != null) {
            val task = current.remove(matchKey)
            _downloadTasks.value = current
            if (task != null && task.videoId.isNotBlank()) {
                recentlyCancelledIds.add(task.videoId)
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.cancelDownload(videoId, title)
            } catch (e: Exception) {
                Log.e(TAG, "cancelTrackDownload failed: ${e.message}")
            }
        }
    }

    fun deleteTrackDownload(videoId: String, title: String = "") {
        val current = _downloadTasks.value.toMutableMap()
        val matchKey = current.keys.firstOrNull { key ->
            key == videoId ||
            current[key]?.videoId == videoId ||
            (title.isNotBlank() && current[key]?.title.equals(title, ignoreCase = true))
        }
        if (matchKey != null) {
            current.remove(matchKey)
            _downloadTasks.value = current
        }
        _downloadedTrackIds.value = _downloadedTrackIds.value - videoId

        viewModelScope.launch(Dispatchers.IO) {
            try {
                client.deleteDownload(videoId, true, title)
            } catch (e: Exception) {
                Log.e(TAG, "deleteTrackDownload failed: ${e.message}")
            }
        }
    }

    fun startDownloadPollingLoop() {
        if (isPollingDownloads) return
        isPollingDownloads = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val (code, resp) = client.getActiveDownloads()
                    if (code in 200..299 && resp.isNotBlank()) {
                        val tasks = client.parseActiveDownloads(resp)
                        val validTasks = tasks.filter { task ->
                            task.status != "CANCELLED" &&
                            !recentlyCancelledIds.contains(task.videoId) &&
                            !recentlyCancelledIds.contains(task.title.lowercase())
                        }
                        val taskMap = validTasks.associateBy { it.videoId }
                        _downloadTasks.value = taskMap

                        val completedIds = validTasks.filter { it.status.equals("COMPLETED", ignoreCase = true) }
                            .map { it.videoId }.toSet()
                        _downloadedTrackIds.value = _downloadedTrackIds.value + completedIds

                        val activeCount = validTasks.count {
                            it.status.equals("DOWNLOADING", ignoreCase = true) ||
                            it.status.equals("PENDING", ignoreCase = true) ||
                            it.status.equals("TAGGING", ignoreCase = true)
                        }
                        if (activeCount == 0) {
                            break
                        }
                    }
                    delay(1000)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download polling loop error: ${e.message}")
            } finally {
                isPollingDownloads = false
            }
        }
    }

    fun getDownloadUiStatusForTrack(track: TrackItem): DownloadUiStatus {
        if (_downloadedTrackIds.value.contains(track.id)) return DownloadUiStatus.DOWNLOADED

        val task = _downloadTasks.value[track.id]
            ?: _downloadTasks.value.values.firstOrNull { it.title.equals(track.title, ignoreCase = true) }

        return when (task?.status?.uppercase()) {
            "DOWNLOADING", "PENDING", "TAGGING" -> DownloadUiStatus.DOWNLOADING
            "COMPLETED" -> DownloadUiStatus.DOWNLOADED
            else -> DownloadUiStatus.NOT_DOWNLOADED
        }
    }

    fun getDownloadProgressForTrack(track: TrackItem): Double {
        val task = _downloadTasks.value[track.id]
            ?: _downloadTasks.value.values.firstOrNull { it.title.equals(track.title, ignoreCase = true) }
        return task?.progress ?: 0.0
    }
}
