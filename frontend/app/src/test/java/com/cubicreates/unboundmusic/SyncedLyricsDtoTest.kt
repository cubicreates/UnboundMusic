package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.LyricsPayloadDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncedLyricsDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSyncedLyricsDeserialization() {
        val rawJson = """
            {
                "track_id": "trk_999",
                "title": "Harder, Better, Faster, Stronger",
                "artist": "Daft Punk",
                "plain_lyrics": "Work it make it do it makes us",
                "is_word_synced": true,
                "lines": [
                    {
                        "text": "Work it, make it, do it, makes us",
                        "start_ms": 1250,
                        "end_ms": 3500,
                        "syllables": [
                            { "text": "Work", "start_ms": 1250, "end_ms": 1500 },
                            { "text": "it", "start_ms": 1500, "end_ms": 1800 }
                        ]
                    }
                ]
            }
        """.trimIndent()

        val payload = json.decodeFromString<LyricsPayloadDto>(rawJson)
        assertEquals("trk_999", payload.trackId)
        assertEquals("Harder, Better, Faster, Stronger", payload.title)
        assertTrue(payload.isWordSynced)
        assertEquals(1, payload.lines.size)

        val firstLine = payload.lines[0]
        assertEquals("Work it, make it, do it, makes us", firstLine.text)
        assertEquals(1250L, firstLine.startMs)
        assertEquals(3500L, firstLine.endMs)
        assertEquals(2, firstLine.syllables.size)
        assertEquals("Work", firstLine.syllables[0].text)
    }
}
