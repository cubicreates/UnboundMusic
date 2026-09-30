package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.AutoEqSearchResponseDto
import com.cubicreates.unboundmusic.data.EQPresetDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPresetDtoTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testAutoEqSearchResponseDeserialization() {
        val rawJson = """
            {
                "query": "Sony WH-1000XM4",
                "count": 1,
                "headphones": [
                    {
                        "id": "sony_wh1000xm4",
                        "name": "Sony WH-1000XM4",
                        "brand": "Sony",
                        "type": "Over-Ear",
                        "has_preset": true
                    }
                ]
            }
        """.trimIndent()

        val response = json.decodeFromString<AutoEqSearchResponseDto>(rawJson)
        assertEquals("Sony WH-1000XM4", response.query)
        assertEquals(1, response.count)
        assertEquals(1, response.headphones.size)

        val headphone = response.headphones[0]
        assertEquals("sony_wh1000xm4", headphone.id)
        assertEquals("Sony WH-1000XM4", headphone.name)
        assertTrue(headphone.hasPreset)
    }

    @Test
    fun testEQPresetDeserialization() {
        val rawJson = """
            {
                "model_id": "sony_wh1000xm4",
                "model_name": "Sony WH-1000XM4",
                "brand": "Sony",
                "target_curve": "Harman Over-Ear 2018",
                "preamp_gain_db": -5.2,
                "bands": [
                    { "frequency_hz": 31, "gain_db": 4.5, "q_factor": 1.414 },
                    { "frequency_hz": 1000, "gain_db": -1.2, "q_factor": 2.0 }
                ]
            }
        """.trimIndent()

        val preset = json.decodeFromString<EQPresetDto>(rawJson)
        assertEquals("sony_wh1000xm4", preset.modelId)
        assertEquals("Harman Over-Ear 2018", preset.targetCurve)
        assertEquals(-5.2, preset.preampGainDb, 0.001)
        assertEquals(2, preset.bands.size)
        assertEquals(31, preset.bands[0].frequencyHz)
        assertEquals(4.5, preset.bands[0].gainDb, 0.001)
    }
}
