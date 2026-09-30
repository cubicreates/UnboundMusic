/*
 * Package: com.cubicreates.unboundmusic.data
 * File: DaemonModels.kt
 * Purpose: Strongly-typed Kotlin domain models representing REST API payloads from the embedded Go daemon (127.0.0.1:45731).
 * Subsystem: Domain / REST API Contracts
 * Concurrency: Immutable data classes safe for multi-threaded coroutine dispatch.
 */

package com.cubicreates.unboundmusic.data

import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val mtime: Long = 0,
    val coverUrl: String = ""
) {
    fun toTrackItem(): TrackItem {
        val stream = when {
            filePath.startsWith("content://") || filePath.startsWith("file://") -> filePath
            else -> "file://$filePath"
        }
        val effectiveCover = coverUrl.ifBlank {
            try {
                val cleanPath = when {
                    filePath.startsWith("file://") -> filePath.removePrefix("file://")
                    filePath.startsWith("content://") -> null
                    else -> filePath
                }
                if (cleanPath != null) {
                    val directCover = java.io.File("$cleanPath.cover.jpg")
                    if (directCover.exists() && directCover.length() > 0) {
                        "file://${directCover.absolutePath}"
                    } else null
                } else null
            } catch (_: Exception) { null } ?: ""
        }
        return TrackItem(
            title = title,
            artist = artist,
            coverUrl = effectiveCover,
            streamUrl = stream,
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
    val searchKeywords: List<String> = emptyList(),
    val region: String = "IN"
) {
    val primaryMood: String
        get() = moodTags.firstOrNull() ?: targetGenres.firstOrNull() ?: "Vibe"

    val isIndianRegion: Boolean
        get() = region.equals("IN", ignoreCase = true)
}

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

    val isSuccess: Boolean
        get() = this is Success
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
 * Folder summary from GET /api/v1/storage/folders.
 */
data class LocalFolderDto(
    val name: String,
    val count: Int,
    val path: String
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
    val lastSynced: String = "",
    val avatarUrl: String = ""
)

/**
 * Initial device handshake for zero-typing YouTube login from POST /api/v1/account/device/start.
 */
data class DeviceCodeData(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresIn: Int,
    val interval: Int
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

/**
 * Custom User Equalizer Preset representation.
 */
data class UserEqPresetDto(
    val id: String,
    val name: String,
    val bandGains: List<Float>,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val loudness: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Storage cache purge result.
 */
data class CachePurgeResult(
    val freedBytes: Long,
    val purgedCategories: List<String>
)

/**
 * Item in a Genre or Mood discovery board.
 */
data class GenreItemDto(
    val title: String,
    val stripeColor: Long,
    val params: String,
    val browseId: String
)

/**
 * Section grouping Genre or Mood discovery items (e.g. "Moods & moments", "Genres").
 */
data class GenreSectionDto(
    val title: String,
    val items: List<GenreItemDto>
)

/**
 * Playlist or album item inside a genre shelf.
 */
data class PlaylistItemDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String,
    val playlistId: String
)

/**
 * Shelf of playlists within GenreDetailScreen.
 */
data class PlaylistShelfDto(
    val title: String,
    val items: List<PlaylistItemDto>
)

/**
 * Phase 5: Offline Download Task state model.
 */
data class DownloadTaskDto(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val targetFormat: String = "opus",
    val status: String = "QUEUED",
    val downloadedBytes: Long = 0,
    val totalBytes: Long = 0,
    val progress: Double = 0.0,
    val localPath: String = "",
    val error: String = ""
)

/**
 * Request payload to start a download task.
 */
data class DownloadStartRequest(
    val videoId: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = ""
)

/**
 * Status states for download button UI.
 */
enum class DownloadUiStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED
}

/**
 * Phase 6: Script display modes for Kinetic Typography.
 */
enum class RomanizationMode {
    ORIGINAL,
    ROMANIZED,
    DUAL
}

@Serializable
data class SyllableDto(
    val text: String = "",
    @SerialName("start_ms") val startMs: Long = 0,
    @SerialName("end_ms") val endMs: Long = 0
)

/**
 * LyricLine model with phonetic Romanization support.
 */
@Serializable
data class LyricLineDto(
    val text: String = "",
    @SerialName("start_ms") val startMs: Long = 0,
    @SerialName("end_ms") val endMs: Long = 0,
    val romanized: String = "",
    val syllables: List<SyllableDto> = emptyList()
)

/**
 * Full lyrics payload from GET /api/v1/lyrics.
 */
@Serializable
data class LyricsPayloadDto(
    @SerialName("track_id") val trackId: String = "",
    val title: String = "",
    val artist: String = "",
    @SerialName("plain_lyrics") val plainLyrics: String = "",
    val lines: List<LyricLineDto> = emptyList(),
    @SerialName("is_word_synced") val isWordSynced: Boolean = false,
    val instrumental: Boolean = false,
    val source: String = ""
)

/**
 * Phase 7: SponsorBlock music_offtopic skip interval model.
 */
data class SkipSegmentDto(
    val category: String,
    val startMs: Long,
    val endMs: Long,
    val action: String = "skip",
    val uuid: String = ""
)

/**
 * Phase 7: Bedtime sleep timer state.
 */
data class SleepTimerState(
    val isActive: Boolean = false,
    val remainingMs: Long = 0L,
    val initialDurationMs: Long = 0L,
    val endOfTrack: Boolean = false
) {
    val progress: Float
        get() = if (initialDurationMs > 0) {
            (remainingMs.toFloat() / initialDurationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    val formattedRemaining: String
        get() {
            if (endOfTrack) return "End of Track"
            val totalSec = (remainingMs / 1000).coerceAtLeast(0)
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            return String.format("%02d:%02d", minutes, seconds)
        }
}
/**
 * Represents a curated YouTube song mix or artist station.
 */
data class MixDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String
)

/**
 * Represents a dynamic algorithmic recommendation shelf (Quick Picks, Similar to X, More from Artist, Listen Again).
 */
data class SmartShelfDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val tracks: List<com.cubicreates.unboundmusic.ui.components.TrackItem>
)

data class SmartFeedDto(
    val hasPersonalization: Boolean,
    val shelves: List<SmartShelfDto>
)

/**
 * Album or Playlist details with full hydrated tracklist.
 */
data class AlbumPlaylistDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val description: String = "",
    val thumbnailUrl: String = "",
    val isAlbum: Boolean = false,
    val year: String = "",
    val trackCount: Int = 0,
    val totalDuration: String = "",
    val tracks: List<TrackItem> = emptyList()
) {
    fun toAlbumPlaylistData(): com.cubicreates.unboundmusic.ui.album.AlbumPlaylistData {
        return com.cubicreates.unboundmusic.ui.album.AlbumPlaylistData(
            title = title,
            subtitle = subtitle,
            coverUrl = thumbnailUrl,
            tracks = tracks,
            totalDuration = totalDuration.ifBlank { "${tracks.size} songs" }
        )
    }
}

/**
 * Return YouTube Dislike (RYD) community vote statistics and approval percentage.
 */
data class RydVoteData(
    val id: String,
    val likes: Long,
    val dislikes: Long,
    val rating: Double,
    val viewCount: Long,
    val likePercentage: Int
) {
    /**
     * Human-friendly compact number formatting (e.g. 1.2M, 45K).
     */
    val formattedLikes: String get() = formatCompactNumber(likes)
    val formattedDislikes: String get() = formatCompactNumber(dislikes)
    val formattedViews: String get() = formatCompactNumber(viewCount)

    companion object {
        fun formatCompactNumber(number: Long): String {
            if (number < 0) return "0"
            if (number < 1_000) return number.toString()
            if (number < 1_000_000) return String.format(java.util.Locale.US, "%.1fK", number / 1_000.0).replace(".0K", "K")
            if (number < 1_000_000_000) return String.format(java.util.Locale.US, "%.1fM", number / 1_000_000.0).replace(".0M", "M")
            return String.format(java.util.Locale.US, "%.1fB", number / 1_000_000_000.0).replace(".0B", "B")
        }
    }
}

/**
 * Result of AcoustID + On-Device LLM acoustic fingerprinting.
 */
data class IdentifiedTrackDto(
    val id: String,
    val filePath: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String,
    val method: String,
    val confidence: Double
)

/**
 * Result of Multi-Stage Spotify/P2P Fallback Resolution.
 */
data class FallbackStatusDto(
    val stage: String,
    val message: String,
    val elapsedMs: Long = 0,
    val verifiedTitle: String? = null,
    val verifiedArtist: String? = null,
    val verifiedFoundOn: String? = null,
    val sourceFormat: String? = null,
    val seeders: Int? = null
)

/**
 * Type-safe @Serializable Track Metadata DTO from daemon /api/v1/search and /api/v1/stream endpoints.
 */
@Serializable
data class TrackMetadataDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0,
    val thumbnail: String = "",
    @SerialName("stream_url") val streamUrl: String = "",
    val source: String = "youtube"
) {
    fun toTrackItem(): TrackItem {
        return TrackItem(
            id = id,
            title = title,
            artist = artist,
            album = album,
            durationMs = durationMs,
            coverUrl = thumbnail,
            streamUrl = streamUrl,
            source = source
        )
    }
}

/**
 * Type-safe @Serializable Search Response DTO from daemon /api/v1/search endpoint.
 */
@Serializable
data class SearchResultDto(
    val query: String = "",
    val type: String = "all",
    val count: Int = 0,
    val tracks: List<TrackMetadataDto> = emptyList()
)


