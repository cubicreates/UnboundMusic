/*
 * Package: com.cubicreates.unboundmusic.data
 * File: DaemonModels.kt
 * Purpose: Strongly-typed Kotlin domain models representing REST API payloads from the embedded Go daemon (127.0.0.1:45731).
 * Subsystem: Domain / REST API Contracts
 * Concurrency: Immutable data classes safe for multi-threaded coroutine dispatch.
 */

package com.cubicreates.unboundmusic.data

import com.cubicreates.unboundmusic.ui.components.RecognizedTrackVariant
import com.cubicreates.unboundmusic.ui.components.TrackItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Top-level media section on the redesigned Home screen.
 */
enum class HomeSection {
    MUSIC,
    PODCASTS
}

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
 * Category classifying pure musical tracks vs mixed voice notes/recordings/clips.
 */
enum class AudioCategory {
    MUSIC,
    MIXED_AUDIO
}

/**
 * Type of playlist: standard user created, or system synthesized (artist discography, albumic).
 */
enum class SmartPlaylistType {
    USER_CUSTOM,
    ARTIST_COLLECTION,
    ALBUMIC,
    ARTIST_SMART,
    ALBUMIC_SMART
}

/**
 * State representing an album's completion status for the 50%-75% recommendation engine.
 */
data class AlbumCompletionStatus(
    val albumTitle: String,
    val artistName: String,
    val coverUrl: String = "",
    val albumBrowseId: String = "",
    val downloadedTracks: List<TrackItem> = emptyList(),
    val missingTracks: List<TrackItem> = emptyList(),
    val totalOfficialTracks: Int = 0,
    val completionRatio: Float = 0.0f,
    val downloadedCount: Int = downloadedTracks.size,
    val officialTotalCount: Int = if (totalOfficialTracks > 0) totalOfficialTracks else downloadedTracks.size
) {
    /** True if within the 50% to 75% prompt threshold */
    val isEligibleForPrompt: Boolean
        get() = completionRatio in 0.50f..0.75f && (missingTracks.isNotEmpty() || (officialTotalCount > downloadedCount && downloadedCount > 0))

    /** Total percentage formatted (e.g. "58%") */
    val percentageString: String
        get() = "${(completionRatio * 100).toInt()}%"
}

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
    val coverUrl: String = "",
    val audioCategory: AudioCategory = AudioCategory.MUSIC,
    val isIdentifiedMusic: Boolean = true
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
            source = sourceFolder,
            audioCategory = audioCategory,
            isIdentifiedMusic = isIdentifiedMusic
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
 * UI State for unified Home normal/lyrics search when on-device AI is not installed (or as fallback).
 */
sealed interface HomeSearchUiState {
    object Idle : HomeSearchUiState
    data class Loading(val query: String) : HomeSearchUiState
    data class Success(val query: String, val tracks: List<TrackItem>) : HomeSearchUiState
    data class Empty(val query: String, val message: String) : HomeSearchUiState
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
 * Backend-generated YouTube-style mix item for Home ("Made For Your Mood").
 */
data class HomeMixDto(
    val id: String,
    val title: String,
    val subtitle: String,
    val coverUrl: String,
    val mixType: String,
    val seedTrackId: String = "",
    val seedTitle: String = "",
    val seedArtist: String = ""
)

/**
 * Unified Home screen payload computed on the Go backend for both guest and logged-in users.
 */
data class HomeFeedDto(
    val isLoggedIn: Boolean = false,
    val accountName: String = "",
    val avatarUrl: String? = null,
    val regionCode: String = "IN",
    val jumpBackIn: List<TrackItem> = emptyList(),
    val madeForYouMixes: List<HomeMixDto> = emptyList(),
    val trendingGlobally: List<TrackItem> = emptyList(),
    val trendingRegionally: List<TrackItem> = emptyList()
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

/**
 * Type-safe @Serializable Headphone Profile metadata DTO from /api/v1/autoeq/search.
 */
@Serializable
data class AutoEqHeadphoneDto(
    val id: String = "",
    val name: String = "",
    val brand: String = "",
    val type: String = "Over-Ear",
    @SerialName("has_preset") val hasPreset: Boolean = true
)

/**
 * Type-safe @Serializable AutoEq Search Response DTO from /api/v1/autoeq/search.
 */
@Serializable
data class AutoEqSearchResponseDto(
    val query: String = "",
    val count: Int = 0,
    val headphones: List<AutoEqHeadphoneDto> = emptyList()
)

/**
 * Type-safe @Serializable Parametric EQ Band DTO.
 */
@Serializable
data class EQBandDto(
    @SerialName("frequency_hz") val frequencyHz: Int = 1000,
    @SerialName("gain_db") val gainDb: Double = 0.0,
    @SerialName("q_factor") val qFactor: Double = 1.414
)

/**
 * Type-safe @Serializable Equalizer Preset DTO from /api/v1/autoeq/preset.
 */
@Serializable
data class EQPresetDto(
    @SerialName("model_id") val modelId: String = "",
    @SerialName("model_name") val modelName: String = "",
    val brand: String = "",
    @SerialName("target_curve") val targetCurve: String = "Harman",
    @SerialName("preamp_gain_db") val preampGainDb: Double = 0.0,
    val bands: List<EQBandDto> = emptyList()
)

/**
 * Multi-candidate recognition result item returned by /api/v1/recognize/lyrics and /api/v1/shazam/identify.
 */
@Serializable
data class RecognitionCandidateDto(
    val id: String = "",
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("cover_url") val coverUrl: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    val year: String = "",
    val confidence: Double = 0.0,
    @SerialName("relevance_label") val relevanceLabel: String = "Match",
    @SerialName("matched_lyric") val matchedLyric: String = "",
    @SerialName("match_source") val matchSource: String = "lyrics",
    @SerialName("is_original") val isOriginal: Boolean = true,
    @SerialName("is_radar_match") val isRadarMatch: Boolean = false,
    val explanation: String = ""
) {
    val effectiveCoverUrl: String
        get() = coverUrl.ifBlank {
            thumbnailUrl.ifBlank {
                if (id.length == 11 && !id.startsWith("local:") && !id.startsWith("lrc_") && !id.startsWith("genius_")) {
                    "https://i.ytimg.com/vi/$id/hqdefault.jpg"
                } else ""
            }
        }

    val confidencePercent: Int
        get() = (confidence * 100.0).toInt().coerceIn(0, 100)

    fun toTrackItem(): TrackItem {
        return TrackItem(
            id = id,
            title = title,
            artist = artist.ifBlank { "Unknown Artist" },
            album = album,
            durationMs = durationMs,
            coverUrl = effectiveCoverUrl,
            streamUrl = "",
            source = matchSource.ifBlank { "lyrics" },
            year = year
        )
    }

    fun toRecognizedVariant(): RecognizedTrackVariant {
        return RecognizedTrackVariant(
            track = toTrackItem(),
            badge = relevanceLabel.ifBlank { if (isOriginal) "Original Match" else "Alternative Match" },
            isOriginal = isOriginal,
            isRadarMatch = isRadarMatch,
            explanation = explanation.ifBlank { matchedLyric }
        )
    }
}

/**
 * Structured multi-candidate response from /api/v1/recognize/lyrics.
 */
@Serializable
data class RecognitionResponseDto(
    val query: String = "",
    val mode: String = "lyrics",
    val matched: Boolean = false,
    val count: Int = 0,
    val candidates: List<RecognitionCandidateDto> = emptyList(),
    @SerialName("latency_ms") val latencyMs: Long = 0L,
    val message: String = ""
)

/**
 * Unified UI State for Music Screen & Identify Screen multi-match recognition.
 */
sealed interface RecognitionUiState {
    object Idle : RecognitionUiState
    data class Listening(val message: String = "Listening...", val durationSeconds: Int = 0) : RecognitionUiState
    data class Loading(val query: String, val mode: String = "lyrics") : RecognitionUiState
    data class Success(
        val query: String,
        val mode: String,
        val candidates: List<RecognitionCandidateDto>,
        val latencyMs: Long = 0L
    ) : RecognitionUiState
    data class Empty(val query: String, val message: String) : RecognitionUiState
    data class Error(val message: String) : RecognitionUiState
}

/**
 * Dedicated Podcast Episode DTO (strictly isolated from Music tracks).
 */
@Serializable
data class PodcastEpisodeDto(
    val id: String = "",
    @SerialName("show_id") val showId: String = "",
    @SerialName("show_title") val showTitle: String = "",
    val author: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    @SerialName("duration_ms") val durationMs: Long = 0L,
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("release_date") val releaseDate: String = "",
    @SerialName("resume_pos_ms") val resumePosMs: Long = 0L
) {
    val effectiveThumbnail: String
        get() = thumbnailUrl.ifBlank {
            if (id.length == 11) "https://i.ytimg.com/vi/$id/hqdefault.jpg" else ""
        }

    val resumeFraction: Float
        get() = if (durationMs > 0L && resumePosMs > 0L) {
            (resumePosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        } else 0f

    fun toPlayableTrackItem(): TrackItem {
        return TrackItem(
            id = id,
            title = title,
            artist = author.ifBlank { showTitle.ifBlank { "Podcast" } },
            album = showTitle,
            durationMs = durationMs,
            coverUrl = effectiveThumbnail,
            streamUrl = "",
            source = "podcast",
            itemType = "podcast",
            audioCategory = AudioCategory.MIXED_AUDIO,
            isIdentifiedMusic = false
        )
    }
}

/**
 * Dedicated Podcast Show DTO.
 */
@Serializable
data class PodcastShowDto(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val description: String = "",
    val category: String = "",
    @SerialName("thumbnail_url") val thumbnailUrl: String = "",
    @SerialName("episode_count") val episodeCount: Int = 0,
    val episodes: List<PodcastEpisodeDto> = emptyList()
)

/**
 * Dedicated Podcast Discovery Feed DTO from /api/v1/podcasts/discover.
 */
@Serializable
data class PodcastDiscoveryDto(
    val category: String = "All Podcasts",
    @SerialName("featured_shows") val featuredShows: List<PodcastShowDto> = emptyList(),
    @SerialName("recent_episodes") val recentEpisodes: List<PodcastEpisodeDto> = emptyList(),
    @SerialName("top_episodes") val topEpisodes: List<PodcastEpisodeDto> = emptyList(),
    @SerialName("global_episodes") val globalEpisodes: List<PodcastEpisodeDto> = emptyList(),
    @SerialName("regional_episodes") val regionalEpisodes: List<PodcastEpisodeDto> = emptyList()
)

/**
 * Persisted Podcast Library state from GET /api/v1/podcasts/library.
 */
@Serializable
data class PodcastLibraryDto(
    @SerialName("followed_shows") val followedShows: List<PodcastShowDto> = emptyList(),
    @SerialName("saved_episodes") val savedEpisodes: List<PodcastEpisodeDto> = emptyList(),
    @SerialName("in_progress_episodes") val inProgressEpisodes: List<PodcastEpisodeDto> = emptyList()
)



