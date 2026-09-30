package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.ui.player.components.formatDurationMs
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerProgressBarTest {

    @Test
    fun testFormatDurationMs() {
        assertEquals("0:00", formatDurationMs(0L))
        assertEquals("0:00", formatDurationMs(-100L))
        assertEquals("0:05", formatDurationMs(5000L))
        assertEquals("3:45", formatDurationMs(225000L))
        assertEquals("1:05:30", formatDurationMs(3930000L))
    }
}
