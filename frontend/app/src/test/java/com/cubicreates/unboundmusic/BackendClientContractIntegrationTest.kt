/*
 * Package: com.cubicreates.unboundmusic
 * File: BackendClientContractIntegrationTest.kt
 * Purpose: End-to-end contract integration tests verifying JSON serialization and deserialization
 *          between the embedded Go daemon REST API and the Android Kotlin client.
 * Subsystem: Integration Test Suite
 * Concurrency: Thread-safe local unit test execution.
 */

package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.AutoEqSearchResponseDto
import com.cubicreates.unboundmusic.data.BackendClient
import com.cubicreates.unboundmusic.data.EQPresetDto
import com.cubicreates.unboundmusic.data.LyricsPayloadDto
import com.cubicreates.unboundmusic.data.SearchResultDto
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
}
