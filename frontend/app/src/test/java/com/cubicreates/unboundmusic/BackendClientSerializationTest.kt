package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendClientSerializationTest {

    private val client = BackendClient()

    @Test
    fun testDecodeSearchResult() {
        val json = """
            {
                "query": "Starboy",
                "type": "song",
                "count": 1,
                "tracks": [
                    {
                        "id": "trk_starboy",
                        "title": "Starboy",
                        "artist": "The Weeknd",
                        "album": "Starboy",
                        "duration_ms": 230000,
                        "thumbnail": "https://example.com/starboy.jpg",
                        "stream_url": "http://127.0.0.1:45731/api/v1/stream?id=trk_starboy",
                        "source": "youtube"
                    }
                ]
            }
        """.trimIndent()

        val result = client.decodeSearchResult(json)
        assertEquals("Starboy", result.query)
        assertEquals(1, result.tracks.size)
        assertEquals("The Weeknd", result.tracks[0].artist)
    }

    @Test
    fun testDecodeLyricsPayload() {
        val json = """
            {
                "track_id": "trk_starboy",
                "title": "Starboy",
                "artist": "The Weeknd",
                "plain_lyrics": "I'm tryna put you in the worst mood, ah",
                "is_word_synced": false,
                "lines": [
                    { "text": "I'm tryna put you in the worst mood, ah", "start_ms": 1000, "end_ms": 4000 }
                ]
            }
        """.trimIndent()

        val lyrics = client.decodeLyricsPayload(json)
        assertNotNull(lyrics)
        assertEquals("trk_starboy", lyrics?.trackId)
        assertEquals(1, lyrics?.lines?.size)
        assertEquals(1000L, lyrics?.lines?.get(0)?.startMs)
    }

    @Test
    fun testDecodeAutoEqSearchResponse() {
        val json = """
            {
                "query": "Bose",
                "count": 1,
                "headphones": [
                    { "id": "bose_qc45", "name": "Bose QC45", "brand": "Bose", "type": "Over-Ear", "has_preset": true }
                ]
            }
        """.trimIndent()

        val response = client.decodeAutoEqSearchResponse(json)
        assertEquals(1, response.count)
        assertEquals("bose_qc45", response.headphones[0].id)
    }
}
