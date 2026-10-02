/*
 * Package: com.cubicreates.unboundmusic.viewmodel
 * File: LibraryViewModel.kt
 * Purpose: Dedicated ViewModel for managing local library tracks, folders, and custom playlists.
 * Subsystem: Domain / Library Layer (Adheres to Single Responsibility Principle)
 */

package com.cubicreates.unboundmusic.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cubicreates.unboundmusic.daemon.DaemonManager
import com.cubicreates.unboundmusic.data.*
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Domain ViewModel responsible strictly for managing the offline/device library, storage scans, and playlists.
 */
class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val daemonManager = DaemonManager.getInstance(application)
    private val client = daemonManager.client

    private val _libraryTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val libraryTracks: StateFlow<List<TrackItem>> = _libraryTracks.asStateFlow()

    private val _musicTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val musicTracks: StateFlow<List<TrackItem>> = _musicTracks.asStateFlow()

    private val _mixedAudioTracks = MutableStateFlow<List<TrackItem>>(emptyList())
    val mixedAudioTracks: StateFlow<List<TrackItem>> = _mixedAudioTracks.asStateFlow()

    private val _customPlaylists = MutableStateFlow<List<CustomPlaylist>>(emptyList())
    val customPlaylists: StateFlow<List<CustomPlaylist>> = _customPlaylists.asStateFlow()

    private val _libraryFolders = MutableStateFlow<Map<String, List<LocalTrack>>>(emptyMap())
    val libraryFolders: StateFlow<Map<String, List<LocalTrack>>> = _libraryFolders.asStateFlow()

    private val _whatsappCount = MutableStateFlow(0)
    val whatsappCount: StateFlow<Int> = _whatsappCount.asStateFlow()

    private val _telegramCount = MutableStateFlow(0)
    val telegramCount: StateFlow<Int> = _telegramCount.asStateFlow()

    private val _downloadsCount = MutableStateFlow(0)
    val downloadsCount: StateFlow<Int> = _downloadsCount.asStateFlow()

    init {
        loadCustomPlaylists()
        rescanLocalStorage()
    }

    fun loadCustomPlaylists() {
        viewModelScope.launch(Dispatchers.IO) {
            _customPlaylists.value = LocalPlaylistStore.getPlaylists(getApplication())
        }
    }

    fun createCustomPlaylist(title: String, initialTracks: List<TrackItem> = emptyList()) {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch(Dispatchers.IO) {
            LocalPlaylistStore.createPlaylist(
                context = getApplication(),
                title = trimmed,
                description = "",
                coverUrl = "",
                initialTracks = initialTracks
            )
            loadCustomPlaylists()
        }
    }

    fun deleteCustomPlaylist(playlistId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            LocalPlaylistStore.deletePlaylist(getApplication(), playlistId)
            loadCustomPlaylists()
        }
    }

    fun rescanLocalStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val mediaStoreTracks = MediaStoreAudioBridge.queryMediaStoreAudio(getApplication())
                if (mediaStoreTracks.isNotEmpty()) {
                    val initialMapped = mediaStoreTracks.map { it.toTrackItem() }
                    _libraryTracks.value = initialMapped
                    _musicTracks.value = initialMapped.filter { it.audioCategory == AudioCategory.MUSIC || it.isIdentifiedMusic }
                    _mixedAudioTracks.value = initialMapped.filter { it.audioCategory == AudioCategory.MIXED_AUDIO && !it.isIdentifiedMusic }

                    val initialFolders = mutableMapOf<String, MutableList<LocalTrack>>()
                    for (track in mediaStoreTracks) {
                        val folderName = track.sourceFolder.ifBlank { "Device Audio" }
                        initialFolders.getOrPut(folderName) { mutableListOf() }.add(track)
                    }
                    _libraryFolders.value = initialFolders

                    _whatsappCount.value = mediaStoreTracks.count {
                        it.sourceFolder.contains("WhatsApp", ignoreCase = true) || it.filePath.contains("WhatsApp", ignoreCase = true)
                    }
                    _telegramCount.value = mediaStoreTracks.count {
                        it.sourceFolder.contains("Telegram", ignoreCase = true) || it.filePath.contains("Telegram", ignoreCase = true)
                    }
                    _downloadsCount.value = mediaStoreTracks.count {
                        it.sourceFolder.contains("Download", ignoreCase = true) || it.sourceFolder.contains("Unbound", ignoreCase = true) || it.filePath.contains("Download", ignoreCase = true)
                    }
                }

                try {
                    val deviceRoots = MediaStoreAudioBridge.discoverDeviceStorageRoots(getApplication())
                    client.scanStorage(deviceRoots)
                    if (mediaStoreTracks.isNotEmpty()) {
                        client.ingestMediaStoreTracks(mediaStoreTracks)
                    }

                    val daemonTracks = client.getLocalTracks("all")
                    val combinedMap = LinkedHashMap<String, LocalTrack>()
                    for (track in mediaStoreTracks) {
                        combinedMap[track.filePath.lowercase(java.util.Locale.ROOT)] = track
                    }
                    for (track in daemonTracks) {
                        combinedMap[track.filePath.lowercase(java.util.Locale.ROOT)] = track
                    }

                    val allLocal = combinedMap.values.toList()
                    val updatedFolders = mutableMapOf<String, MutableList<LocalTrack>>()
                    for (track in allLocal) {
                        val folderName = track.sourceFolder.ifBlank { "Device Audio" }
                        updatedFolders.getOrPut(folderName) { mutableListOf() }.add(track)
                    }
                    _libraryFolders.value = updatedFolders

                    _whatsappCount.value = allLocal.count {
                        it.sourceFolder.contains("WhatsApp", ignoreCase = true) || it.filePath.contains("WhatsApp", ignoreCase = true)
                    }
                    _telegramCount.value = allLocal.count {
                        it.sourceFolder.contains("Telegram", ignoreCase = true) || it.filePath.contains("Telegram", ignoreCase = true)
                    }
                    _downloadsCount.value = allLocal.count {
                        it.sourceFolder.contains("Download", ignoreCase = true) || it.sourceFolder.contains("Unbound", ignoreCase = true) || it.filePath.contains("Download", ignoreCase = true)
                    }

                    val mapped = allLocal.map { it.toTrackItem() }
                    _libraryTracks.value = mapped
                    _musicTracks.value = mapped.filter { it.audioCategory == AudioCategory.MUSIC || it.isIdentifiedMusic }
                    _mixedAudioTracks.value = mapped.filter { it.audioCategory == AudioCategory.MIXED_AUDIO && !it.isIdentifiedMusic }
                } catch (_: Exception) {}
            } catch (_: Exception) {}
        }
    }
}
