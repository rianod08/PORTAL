package com.venuelink.studio

import com.venuelink.studio.ui.components.CameraPoseFusion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CameraPoseFusionTest {
    private val identity = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    @Test fun convertsAndroidSensorMatrixToColumnMajor() {
        val rowMajor = floatArrayOf(
            0f, 0f, 1f, 0f,
            0f, 1f, 0f, 0f,
            -1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f,
        )
        val converted = CameraPoseFusion.sensorMatrixToPose(rowMajor)
        assertEquals(1f, converted[8], .0001f)
        assertEquals(-1f, converted[2], .0001f)
    }

    @Test fun followsDeviceTurnWhileHoldingLastKnownPosition() {
        val trackedDisplay = identity.copyOf().also { it[12] = 1.2f; it[13] = .4f }
        val trackedPhysical = trackedDisplay.copyOf()
        val sensorTurn = floatArrayOf(
            0f, 0f, -1f, 0f,
            0f, 1f, 0f, 0f,
            1f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f,
        )
        val estimate = CameraPoseFusion.estimate(trackedDisplay, trackedPhysical, identity, sensorTurn)
        assertEquals(1f, estimate.display[8], .0001f)
        assertEquals(-1f, estimate.display[2], .0001f)
        assertEquals(1.2f, estimate.display[12], .0001f)
        assertEquals(.4f, estimate.physical[13], .0001f)
        assertEquals(1f, estimate.physical[8], .0001f)
    }

    @Test fun preservesPhysicalCameraOffset() {
        val physical = floatArrayOf(
            0f, 1f, 0f, 0f,
            -1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f,
        )
        val estimate = CameraPoseFusion.estimate(identity, physical, identity, identity)
        for (index in 0..15) assertEquals(physical[index], estimate.physical[index], .0001f)
    }

    @Test fun landscapeDisplayDoesNotRotateTheSensorTurnAxis() {
        val landscape = floatArrayOf(
            0f, 1f, 0f, 0f, -1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f,
        )
        val deviceTurn = floatArrayOf(
            0f, 0f, -1f, 0f, 0f, 1f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f,
        )
        val estimate = CameraPoseFusion.estimate(landscape, identity, identity, deviceTurn, identity)
        // Sensor yaw remains yaw in physical camera space even when the UI
        // axes are rotated 90 degrees relative to the device sensor axes.
        assertArrayEquals(deviceTurn, estimate.physical, .0001f)
        assertEquals(1f,estimate.display[8],.0001f)
    }
}
