package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for Multi-Stage Fallback status DTO parsing in BackendClient.
 */
class FallbackStatusDtoTest {

    private val client = BackendClient()

    @Test
    fun testParseFallbackStatusSuccess() {
        val json = """
            {
                "stage": "STREAMING",
                "message": "Found FLAC stream with 14 seeders via P2P network.",
                "elapsed_ms": 350,
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

        val status = client.parseFallbackStatus(json)
        assertNotNull(status)
        assertEquals("STREAMING", status?.stage)
        assertEquals("Around the World", status?.verifiedTitle)
        assertEquals("Daft Punk", status?.verifiedArtist)
        assertEquals("SPOTIFY", status?.verifiedFoundOn)
        assertEquals("FLAC", status?.sourceFormat)
        assertEquals(14, status?.seeders)
    }

    @Test
    fun testParseFallbackStatusNoSeeds() {
        val json = """
            {
                "stage": "NO_SEEDS",
                "message": "Track confirmed on Spotify, but no active seeders are online.",
                "elapsed_ms": 120,
                "verified_track": {
                    "title": "Unreleased Demo",
                    "artist": "Indie Band",
                    "found_on": "SPOTIFY"
                }
            }
        """.trimIndent()

        val status = client.parseFallbackStatus(json)
        assertNotNull(status)
        assertEquals("NO_SEEDS", status?.stage)
        assertEquals("Unreleased Demo", status?.verifiedTitle)
        assertNull(status?.sourceFormat)
        assertNull(status?.seeders)
    }

    @Test
    fun testParseFallbackStatusBlank() {
        assertNull(client.parseFallbackStatus(""))
        assertNull(client.parseFallbackStatus("   "))
        assertNull(client.parseFallbackStatus("invalid-json"))
    }
}
