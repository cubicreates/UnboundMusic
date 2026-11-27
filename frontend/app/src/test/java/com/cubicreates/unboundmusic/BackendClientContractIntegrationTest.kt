/*
 * Package: com.cubicreates.unboundmusic
 * File: BackendClientContractIntegrationTest.kt
 * Purpose: End-to-end contract integration tests verifying JSON serialization and deserialization
 *          between the embedded Go daemon REST API and the Android Kotlin client.
 * Subsystem: Integration Test Suite
 * Concurrency: Thread-safe local unit test execution.
 */

package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.*
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendClientContractIntegrationTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val client = BackendClient()

    @Test
    fun testFallbackResolutionPipelineContract() {
        val payload = """
            {
                "stage": "STREAMING",
                "message": "Connected to 14 peers on torrent fallback swarm",
                "elapsed_ms": 320,
                "verified_track": {
                    "title": "Around the World",
                    "artist": "Daft Punk",
                    "found_on": "SPOTIFY"
                },
                "selected_source": {
                    "audio_format": "FLAC",
                    "seeders": 14
                }
            }
        """.trimIndent()

        val status = client.parseFallbackStatus(payload)
        assertNotNull(status)
        assertEquals("STREAMING", status?.stage)
        assertEquals("Around the World", status?.verifiedTitle)
        assertEquals("Daft Punk", status?.verifiedArtist)
        assertEquals("SPOTIFY", status?.verifiedFoundOn)
        assertEquals("FLAC", status?.sourceFormat)
        assertEquals(14, status?.seeders)
    }

    @Test
    fun testSearchCatalogResponseContract() {
        val payload = """
            {
                "query": "Not Like Us",
                "type": "song",
                "count": 1,
                "tracks": [
                    {
                        "id": "T6eK-2OQtew",
                        "title": "Not Like Us",
                        "artist": "Kendrick Lamar",
                        "album": "Not Like Us - Single",
                        "duration_ms": 274000,
                        "thumbnail": "https://i.ytimg.com/vi/T6eK-2OQtew/maxresdefault.jpg",
                        "stream_url": "http://127.0.0.1:45731/api/v1/stream?id=T6eK-2OQtew",
                        "source": "youtube"
                    }
                ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<SearchResultDto>(payload)
        assertEquals("Not Like Us", dto.query)
        assertEquals(1, dto.tracks.size)

        val track = dto.tracks[0]
        assertEquals("T6eK-2OQtew", track.id)
        assertEquals("Kendrick Lamar", track.artist)
        assertEquals(274000L, track.durationMs)

        val trackItem = track.toTrackItem()
        assertEquals("T6eK-2OQtew", trackItem.id)
        assertEquals("Kendrick Lamar", trackItem.artist)
    }

    @Test
    fun testSyncedLyricsContract() {
        val payload = """
            {
                "track_id": "trk_456",
                "title": "Blinding Lights",
                "artist": "The Weeknd",
                "plain_lyrics": "I've been on my own for long enough",
                "is_word_synced": false,
                "lines": [
                    {
                        "text": "Yeah",
                        "start_ms": 1200,
                        "end_ms": 2400
                    },
                    {
                        "text": "I've been tryna call",
                        "start_ms": 15400,
                        "end_ms": 18200
                    }
                ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<LyricsPayloadDto>(payload)
        assertEquals("trk_456", dto.trackId)
        assertEquals("Blinding Lights", dto.title)
        assertEquals(2, dto.lines.size)
        assertEquals("Yeah", dto.lines[0].text)
        assertEquals(1200L, dto.lines[0].startMs)
        assertEquals(2400L, dto.lines[0].endMs)
    }

    @Test
    fun testEqualizerAndAutoEqContract() {
        val payload = """
            {
                "model_id": "sony_wh1000xm5",
                "model_name": "WH-1000XM5",
                "brand": "Sony",
                "target_curve": "Harman Over-Ear 2018",
                "preamp_gain_db": -5.4,
                "bands": [
                    { "frequency_hz": 31, "gain_db": -1.2, "q_factor": 1.41 },
                    { "frequency_hz": 62, "gain_db": -3.8, "q_factor": 1.41 },
                    { "frequency_hz": 125, "gain_db": -4.5, "q_factor": 1.41 },
                    { "frequency_hz": 250, "gain_db": -1.0, "q_factor": 1.41 },
                    { "frequency_hz": 500, "gain_db": 0.5, "q_factor": 1.41 },
                    { "frequency_hz": 1000, "gain_db": 1.8, "q_factor": 1.41 },
                    { "frequency_hz": 2000, "gain_db": 3.2, "q_factor": 1.41 },
                    { "frequency_hz": 4000, "gain_db": -2.1, "q_factor": 1.41 },
                    { "frequency_hz": 8000, "gain_db": 2.0, "q_factor": 1.41 },
                    { "frequency_hz": 16000, "gain_db": -0.5, "q_factor": 1.41 }
                ]
            }
        """.trimIndent()

        val dto = json.decodeFromString<EQPresetDto>(payload)
        assertEquals("sony_wh1000xm5", dto.modelId)
        assertEquals("Sony", dto.brand)
        assertEquals(-5.4, dto.preampGainDb, 0.001)
        assertEquals(10, dto.bands.size)
        assertEquals(1000, dto.bands[5].frequencyHz)
        assertEquals(1.8, dto.bands[5].gainDb, 0.001)
    }

    @Test
    fun testMultiMatchLyricsRecognitionContract() {
        val payload = """
            {
                "query": "hello from the other side",
                "mode": "lyrics",
                "matched": true,
                "count": 2,
                "latency_ms": 142,
                "candidates": [
                    {
                        "id": "YQHsXMglC9A",
                        "title": "Hello",
                        "artist": "Adele",
                        "album": "25",
                        "duration_ms": 295000,
                        "thumbnail_url": "https://i.ytimg.com/vi/YQHsXMglC9A/hqdefault.jpg",
                        "matched_lyric": "Hello from the other side, I must have called a thousand times",
                        "section_type": "Chorus",
                        "confidence": 0.94,
                        "confidence_percent": 94,
                        "relevance_label": "Exact Match",
                        "source": "genius+lrclib"
                    },
                    {
                        "id": "kOkQ4T5WO9E",
                        "title": "Hello",
                        "artist": "Lionel Richie",
                        "album": "Can't Slow Down",
                        "duration_ms": 249000,
                        "matched_lyric": "Hello, is it me you're looking for?",
                        "section_type": "Chorus",
                        "confidence": 0.61,
                        "confidence_percent": 61,
                        "relevance_label": "Lyric Match",
                        "source": "lrclib"
                    }
                ]
            }
        """.trimIndent()

        val response = client.parseRecognitionResponse(payload)
        assertTrue(response.matched)
        assertEquals(2, response.candidates.size)
        assertEquals(94, response.candidates[0].confidencePercent)
        assertEquals("Hello from the other side, I must have called a thousand times", response.candidates[0].matchedLyric)
        val firstTrack = response.candidates[0].toTrackItem()
        assertEquals("YQHsXMglC9A", firstTrack.id)
        assertEquals("Adele", firstTrack.artist)
        assertTrue(firstTrack.isIdentifiedMusic)
    }

    @Test
    fun testPodcastDiscoveryAndIsolationContract() {
        val payload = """
            {
                "category": "Technology",
                "featured_shows": [
                    {
                        "id": "PL123456",
                        "title": "Systems Unbound",
                        "author": "Alex Chen",
                        "category": "Technology",
                        "episode_count": 42
                    }
                ],
                "recent_episodes": [
                    {
                        "id": "dQw4w9WgXcQ",
                        "show_id": "PL123456",
                        "show_title": "Systems Unbound",
                        "author": "Alex Chen",
                        "title": "Ep 42: Designing Low-Latency Audio Pipelines",
                        "category": "Technology",
                        "duration_ms": 2700000,
                        "resume_pos_ms": 540000
                    }
                ],
                "top_episodes": []
            }
        """.trimIndent()

        val discovery = client.parsePodcastDiscovery(payload)
        assertNotNull(discovery)
        assertEquals("Technology", discovery!!.category)
        assertEquals(1, discovery.featuredShows.size)
        assertEquals(1, discovery.recentEpisodes.size)

        val episode = discovery.recentEpisodes.first()
        assertEquals(0.2f, episode.resumeFraction, 0.01f)
        val playable = episode.toPlayableTrackItem()
        assertEquals("podcast", playable.source)
        assertEquals("podcast", playable.itemType)
        assertEquals(AudioCategory.MIXED_AUDIO, playable.audioCategory)
        assertEquals(false, playable.isIdentifiedMusic)
    }

    @Test
    fun testSplitMusicAndPodcastSearchCategoriesContract() {
        val musicCats = com.cubicreates.unboundmusic.ui.search.SearchCategory.values().map { it.apiParam }
        assertEquals(listOf("all", "songs", "artists", "albums", "playlists"), musicCats)
        assertTrue(!musicCats.contains("podcast"))

        val podFilters = com.cubicreates.unboundmusic.ui.search.PodcastSearchFilter.values().map { it.apiParam }
        assertEquals(listOf("all", "episodes", "shows"), podFilters)

        val musicPayload = """
            {
                "query": "daft punk",
                "domain": "music",
                "filter": "songs",
                "count": 1,
                "tracks": [
                    {
                        "id": "FGBhQbmPwH8",
                        "title": "One More Time",
                        "artist": "Daft Punk",
                        "duration_ms": 320000,
                        "item_type": "song"
                    }
                ]
            }
        """.trimIndent()
        val parsedMusic = client.parseSearchResults(musicPayload)
        assertEquals(1, parsedMusic.size)
        assertEquals("FGBhQbmPwH8", parsedMusic[0].id)
        assertEquals("song", parsedMusic[0].itemType)
    }

    @Test
    fun testPodcastLibraryContract() {
        val payload = """
            {
                "followed_shows": [
                    {
                        "id": "show_lex_01",
                        "title": "Lex Fridman Podcast",
                        "author": "Lex Fridman",
                        "category": "AI & Tech",
                        "episode_count": 418
                    }
                ],
                "saved_episodes": [
                    {
                        "id": "ep_lex_418",
                        "show_id": "show_lex_01",
                        "show_title": "Lex Fridman Podcast",
                        "author": "Lex Fridman",
                        "title": "Ep 418: Systems",
                        "duration_ms": 3600000,
                        "resume_pos_ms": 900000
                    }
                ],
                "in_progress_episodes": [
                    {
                        "id": "ep_lex_418",
                        "show_id": "show_lex_01",
                        "show_title": "Lex Fridman Podcast",
                        "author": "Lex Fridman",
                        "title": "Ep 418: Systems",
                        "duration_ms": 3600000,
                        "resume_pos_ms": 900000
                    }
                ]
            }
        """.trimIndent()

        val lib = client.parsePodcastLibrary(payload)
        assertNotNull(lib)
        assertEquals(1, lib!!.followedShows.size)
        assertEquals("Lex Fridman Podcast", lib.followedShows[0].title)
        assertEquals(1, lib.savedEpisodes.size)
        assertEquals(900000L, lib.savedEpisodes[0].resumePosMs)
        assertEquals(1, lib.inProgressEpisodes.size)
    }

    @Test
    fun testWallpaperBackupMetaContract() {
        val payload = """
            {
                "has_backup": true,
                "mime_type": "image/png",
                "source_flag": "system_shared",
                "lock_wallpaper_id": -1,
                "system_wallpaper_id": 42,
                "sha256_hex": "deadbeef",
                "byte_size": 204800,
                "updated_at": 1700000000000
            }
        """.trimIndent()

        val meta = client.parseWallpaperBackupMeta(payload)
        assertNotNull(meta)
        assertTrue(meta!!.hasBackup)
        assertEquals("image/png", meta.mimeType)
        assertEquals("system_shared", meta.sourceFlag)
        assertEquals(-1L, meta.lockWallpaperId)
        assertEquals(42L, meta.systemWallpaperId)
        assertEquals(204800L, meta.byteSize)
    }
}
