package com.venuelink.studio

import com.venuelink.studio.domain.panorama.SphericalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SphericalMathTest {
    @Test
    fun normalizesYaw() {
        assertEquals(-170f, SphericalMath.normalizeYaw(190f), 0.001f)
        assertEquals(170f, SphericalMath.normalizeYaw(-190f), 0.001f)
    }

    @Test
    fun clampsPitch() {
        assertEquals(89f, SphericalMath.clampPitch(120f), 0.001f)
        assertEquals(-89f, SphericalMath.clampPitch(-120f), 0.001f)
    }

    @Test
    fun calculatesAngularDistance() {
        assertEquals(0f, SphericalMath.angularDistance(0f, 0f, 0f, 0f), 0.001f)
        assertEquals(90f, SphericalMath.angularDistance(0f, 0f, 90f, 0f), 0.2f)
    }

    @Test
    fun projectsVisibleHotspots() {
        assertTrue(SphericalMath.isHotspotVisible(5f, 0f, 0f, 0f, 90f))
        assertFalse(SphericalMath.isHotspotVisible(140f, 0f, 0f, 0f, 90f))
        assertNotNull(SphericalMath.yawPitchToScreenPoint(5f, 0f, 1000f, 500f, 0f, 0f, 90f))
        assertNull(SphericalMath.yawPitchToScreenPoint(140f, 0f, 1000f, 500f, 0f, 0f, 90f))
    }
}
