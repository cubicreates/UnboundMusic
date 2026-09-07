/*
 * Package: com.cubicreates.unboundmusic.data
 * File: DaemonModels.kt
 * Purpose: Strongly-typed Kotlin domain models representing REST API payloads from the embedded Go daemon (127.0.0.1:45731).
 * Subsystem: Domain / REST API Contracts
 * Concurrency: Immutable data classes safe for multi-threaded coroutine dispatch.
 */

package com.cubicreates.unboundmusic.data

import com.cubicreates.unboundmusic.ui.components.TrackItem

/**
 * Represents a situational 24-hour time-aware recommendation card.
 */
data class MoodCapsule(
    val tag: String,
    val title: String,
    val browseId: String,
    val description: String,
    val colorHex: String,
    val iconName: String
)

/**
 * Current 24-hour temporal window and its prioritized mood capsules.
 */
data class DaypartingState(
    val activeWindow: String,
    val localHour: Int,
    val capsules: List<MoodCapsule>
)

/**
 * Physical audio track indexed on device storage with Chromaprint / AcoustID metadata.
 */
data class LocalTrack(
    val id: String,
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationMs: Long = 0,
    val format: String = "mp3",
    val fileSize: Long = 0,
    val sourceFolder: String = "music",
    val dateIndexed: Long = 0,
    val mtime: Long = 0
) {
    fun toTrackItem(): TrackItem {
        return TrackItem(
            title = title,
            artist = artist,
            coverUrl = "",
            streamUrl = "file://$filePath",
            id = id,
            album = album,
            durationMs = durationMs,
            source = sourceFolder
        )
    }
}

/**
 * Structured parameters extracted from natural language prompts by on-device Edge AI.
 */
data class VibeResult(
    val originalPrompt: String,
    val targetGenres: List<String> = emptyList(),
    val moodTags: List<String> = emptyList(),
    val energyLevel: String = "MEDIUM",
    val suggestedBpm: Int = 120,
    val searchKeywords: List<String> = emptyList()
)

/**
 * Complete response payload from POST /api/v1/search/vibe.
 */
data class VibeSearchResponse(
    val vibeResult: VibeResult,
    val radioTracks: List<TrackItem>
)

/**
 * UI State for natural language vibe queries.
 */
sealed interface VibeSearchUiState {
    object Idle : VibeSearchUiState
    object Loading : VibeSearchUiState
    data class Success(
        val vibeResult: VibeResult,
        val radioTracks: List<TrackItem>
    ) : VibeSearchUiState
    data class Error(val message: String) : VibeSearchUiState
}

/**
 * Summary metrics from POST /api/v1/storage/scan.
 */
data class StorageScanResponse(
    val status: String,
    val scannedFiles: Int,
    val audioFilesFound: Int,
    val newTracksIndexed: Int,
    val unchangedTracks: Int,
    val elapsedMs: Long
)

/**
 * Response payload from POST /api/v1/radio/magic.
 */
data class MagicRadioResult(
    val seedTrack: TrackItem,
    val queue: List<TrackItem>,
    val source: String,
    val generatedMs: Long
)

/**
 * Top artist preference summary from GET /api/v1/taste/profile.
 */
data class ArtistAffinityItem(
    val artistId: String,
    val artistName: String,
    val affinityScore: Double,
    val playCount: Int,
    val skipCount: Int,
    val isBanned: Boolean
)

/**
 * User taste profile and Shannon entropy diversity metrics.
 */
data class TasteProfileResponse(
    val topArtists: List<ArtistAffinityItem>,
    val tasteDiversityScore: Double
)

/**
 * Connection state and sync metrics for YouTube Music from GET /api/v1/account/status.
 */
data class AccountStatusData(
    val connected: Boolean,
    val accountName: String,
    val syncedTracksCount: Int,
    val lastSynced: String = ""
)

/**
 * Result from 4-stage intelligent search cascade GET /api/v1/search/cascade.
 */
data class CascadeSearchResponse(
    val query: String,
    val stageReached: Int,
    val stageName: String,
    val tracks: List<TrackItem>
)

