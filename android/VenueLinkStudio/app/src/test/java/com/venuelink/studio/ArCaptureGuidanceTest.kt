package com.venuelink.studio

import com.venuelink.studio.data.model.CaptureDot
import com.venuelink.studio.domain.capture.ArCaptureGuidance
import com.venuelink.studio.domain.capture.CaptureArrowDirection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class ArCaptureGuidanceTest {
    @Test
    fun projectsForwardTargetToScreenCenter() {
        val dot = CaptureDot(id = "front", yaw = 0f, pitch = 0f, captureOrder = 0)
        val pose = ArCaptureGuidance.identityPoseMatrix()

        val projection = ArCaptureGuidance.projectActiveTarget(dot, pose, pose, 1000f, 500f)

        assertTrue(projection.isVisible)
        assertTrue(projection.isAimed)
        assertNotNull(projection.screenPoint)
        assertEquals(500f, projection.screenPoint!!.x, 0.01f)
        assertEquals(250f, projection.screenPoint!!.y, 0.01f)
    }

    @Test
    fun marksTargetBehindCameraAsNotVisible() {
        val dot = CaptureDot(id = "behind", yaw = 0f, pitch = 0f, captureOrder = 0)
        val origin = ArCaptureGuidance.identityPoseMatrix()
        val currentCameraTurnedAround = floatArrayOf(
            -1f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f,
            0f, 0f, -1f, 0f,
            0f, 0f, 0f, 1f,
        )

        val projection = ArCaptureGuidance.projectActiveTarget(dot, origin, currentCameraTurnedAround, 1000f, 500f)

        assertFalse(projection.isVisible)
        assertFalse(projection.isAimed)
        assertNotNull(projection.arrow)
    }

    @Test
    fun choosesEdgeArrowForOffscreenTarget() {
        val dot = CaptureDot(id = "right", yaw = 90f, pitch = 0f, captureOrder = 0)
        val pose = ArCaptureGuidance.identityPoseMatrix()

        val projection = ArCaptureGuidance.projectActiveTarget(dot, pose, pose, 1000f, 500f)

        assertFalse(projection.isVisible)
        assertEquals(CaptureArrowDirection.Right, projection.arrow)
    }

    @Test
    fun panoramaTargetStaysOnSameBearingWhenArPositionDrifts() {
        val dot = CaptureDot(id = "front", yaw = 0f, pitch = 0f, captureOrder = 0)
        val origin = ArCaptureGuidance.identityPoseMatrix()
        val movedRight = origin.copyOf().apply { this[12] = 0.2f }

        val projection = ArCaptureGuidance.projectActiveTarget(dot, origin, movedRight, 1000f, 500f)

        assertTrue(projection.isVisible)
        assertEquals(500f, projection.screenPoint!!.x, 0.01f)
        assertTrue(projection.isAimed)
    }

    @Test
    fun seventhUpperDotCanBeAimedDespiteVerticalArDrift() {
        val origin = ArCaptureGuidance.identityPoseMatrix()
        val pitch = Math.toRadians(60.0)
        val camera = origin.copyOf().apply {
            this[5] = cos(pitch).toFloat()
            this[6] = sin(pitch).toFloat()
            this[9] = -sin(pitch).toFloat()
            this[10] = cos(pitch).toFloat()
            this[13] = 1.2f
        }
        val seventh = CaptureDot(id = "ultrawide-6", yaw = 0f, pitch = 60f, captureOrder = 6)

        val projection = ArCaptureGuidance.projectActiveTarget(seventh, origin, camera, 1000f, 500f)

        assertTrue(projection.isAimed)
        assertEquals(500f, projection.screenPoint!!.x, 0.01f)
        assertEquals(250f, projection.screenPoint!!.y, 0.01f)
    }
}
