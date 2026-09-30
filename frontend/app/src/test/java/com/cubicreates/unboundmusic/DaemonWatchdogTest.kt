package com.cubicreates.unboundmusic

import com.cubicreates.unboundmusic.service.DaemonHealthState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DaemonWatchdogTest {

    @Test
    fun testDaemonHealthStatesEnum() {
        val states = DaemonHealthState.values()
        assertEquals(4, states.size)
        assertTrue(states.contains(DaemonHealthState.ONLINE))
        assertTrue(states.contains(DaemonHealthState.DEGRADED))
        assertTrue(states.contains(DaemonHealthState.DISCONNECTED))
        assertTrue(states.contains(DaemonHealthState.RECONNECTING))
    }
}
