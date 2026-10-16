package com.venuelink.studio

import com.venuelink.studio.capture.CaptureHoldGate
import org.junit.Assert.assertEquals
import org.junit.Test

class CaptureHoldGateTest {
    @Test fun requiresOneUninterruptedSecondOnTheSameTarget() {
        val gate = CaptureHoldGate()
        assertEquals(0f, gate.progress("dot-1", true, 1_000_000_000L), .001f)
        assertEquals(.5f, gate.progress("dot-1", true, 1_500_000_000L), .001f)
        assertEquals(1f, gate.progress("dot-1", true, 2_000_000_000L), .001f)
    }

    @Test fun motionOrChangingTargetsRestartsTheHold() {
        val gate = CaptureHoldGate()
        gate.progress("dot-1", true, 1_000_000_000L)
        assertEquals(0f, gate.progress("dot-1", false, 1_600_000_000L), .001f)
        assertEquals(0f, gate.progress("dot-1", true, 1_700_000_000L), .001f)
        assertEquals(.5f, gate.progress("dot-1", true, 2_200_000_000L), .001f)
        assertEquals(0f, gate.progress("dot-2", true, 2_300_000_000L), .001f)
    }
}
