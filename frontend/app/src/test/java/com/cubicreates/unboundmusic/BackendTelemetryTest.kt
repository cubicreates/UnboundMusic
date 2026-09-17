package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.data.BackendClient
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for client-side stream error telemetry and 502 error window tracking.
 */
class BackendTelemetryTest {

    @Before
    fun setUp() {
        BackendClient.resetTelemetryForTesting()
    }

    @Test
    fun testSingleOrDouble502DoesNotExceedThreshold() {
        assertEquals(0, BackendClient.recent502Count)

        BackendClient.recordProxyStreamStatus(502)
        assertEquals(1, BackendClient.recent502Count)

        BackendClient.recordProxyStreamStatus(502)
        assertEquals(2, BackendClient.recent502Count)
    }

    @Test
    fun testSuccessfulStreamResetsConsecutiveFailureStreak() {
        BackendClient.recordProxyStreamStatus(502)
        BackendClient.recordProxyStreamStatus(502)
        assertEquals(2, BackendClient.recent502Count)

        // Successful stream code (200 OK) resets the counter
        BackendClient.recordProxyStreamStatus(200)
        assertEquals(0, BackendClient.recent502Count)
    }

    @Test
    fun testTriple502TriggersAlertAndClearsWindow() {
        BackendClient.recordProxyStreamStatus(502)
        BackendClient.recordProxyStreamStatus(502)
        assertEquals(2, BackendClient.recent502Count)

        // Third consecutive 502 triggers alert and clears window to prevent duplicate toasts
        BackendClient.recordProxyStreamStatus(502)
        assertEquals(0, BackendClient.recent502Count)
    }

    @Test
    fun testNon502ErrorDoesNotIncrementWindow() {
        BackendClient.recordProxyStreamStatus(404)
        BackendClient.recordProxyStreamStatus(400)
        assertEquals(0, BackendClient.recent502Count)
    }
}
