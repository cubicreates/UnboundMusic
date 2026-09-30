package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.SearchResultDto
import com.cubicreates.unboundmusic.data.TrackMetadataDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class SearchResultDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testSearchResultDeserialization() {
        val rawJson = """
            {
                "query": "Daft Punk",
                "type": "song",
                "count": 1,
                "tracks": [
                    {
                        "id": "trk_123",
                        "title": "Get Lucky",
                        "artist": "Daft Punk",
                        "album": "Random Access Memories",
                        "duration_ms": 248000,
                        "thumbnail": "https://img.youtube.com/vi/trk_123/0.jpg",
                        "stream_url": "http://127.0.0.1:45731/api/v1/stream?id=trk_123",
                        "source": "youtube"
                    }
                ]
            }
        """.trimIndent()

        val result = json.decodeFromString<SearchResultDto>(rawJson)
        assertEquals("Daft Punk", result.query)
        assertEquals("song", result.type)
        assertEquals(1, result.count)
        assertEquals(1, result.tracks.size)

        val track = result.tracks[0]
        assertEquals("trk_123", track.id)
        assertEquals("Get Lucky", track.title)
        assertEquals("Daft Punk", track.artist)
        assertEquals(248000L, track.durationMs)

        val trackItem = track.toTrackItem()
        assertEquals("trk_123", trackItem.id)
        assertEquals("Get Lucky", trackItem.title)
    }
}
