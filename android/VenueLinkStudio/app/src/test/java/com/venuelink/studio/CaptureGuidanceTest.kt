package com.venuelink.studio

import com.venuelink.studio.data.model.CaptureDot
import com.venuelink.studio.domain.capture.CaptureArrowDirection
import com.venuelink.studio.domain.capture.CaptureGuidance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureGuidanceTest {
    @Test
    fun calibratesYawRelativeToCaptureStart() {
        assertEquals(0f, CaptureGuidance.calibratedYaw(73f, 73f), 0.001f)
        assertEquals(20f, CaptureGuidance.calibratedYaw(93f, 73f), 0.001f)
        assertEquals(-20f, CaptureGuidance.calibratedYaw(53f, 73f), 0.001f)
    }

    @Test
    fun calibratesPitchRelativeToCaptureStart() {
        assertEquals(0f, CaptureGuidance.calibratedPitch(12f, 12f), 0.001f)
        assertEquals(18f, CaptureGuidance.calibratedPitch(30f, 12f), 0.001f)
        assertEquals(-18f, CaptureGuidance.calibratedPitch(-6f, 12f), 0.001f)
    }

    @Test
    fun smoothsOrientationWithoutBreakingYawWrap() {
        assertEquals(180f, CaptureGuidance.smoothYaw(179f, -179f, 0.5f), 0.001f)
        assertEquals(5f, CaptureGuidance.smoothPitch(0f, 10f, 0.5f), 0.001f)
    }

    @Test
    fun choosesOffscreenArrowDirection() {
        assertEquals(CaptureArrowDirection.Right, CaptureGuidance.arrowForDelta(80f, 4f))
        assertEquals(CaptureArrowDirection.Left, CaptureGuidance.arrowForDelta(-80f, 4f))
        assertEquals(CaptureArrowDirection.Up, CaptureGuidance.arrowForDelta(3f, 40f))
        assertEquals(CaptureArrowDirection.Down, CaptureGuidance.arrowForDelta(3f, -40f))
    }

    @Test
    fun reportsVisibilityAndAiming() {
        val centered = CaptureGuidance.guidanceFor(targetYaw = 0f, targetPitch = 0f, currentYaw = 0f, currentPitch = 0f)
        assertTrue(centered.isVisible)
        assertTrue(centered.isAimed)
        assertNull(centered.arrow)

        val offscreen = CaptureGuidance.guidanceFor(targetYaw = 130f, targetPitch = 0f, currentYaw = 0f, currentPitch = 0f)
        assertFalse(offscreen.isVisible)
        assertEquals(CaptureArrowDirection.Right, offscreen.arrow)
    }

    @Test
    fun ordersFirstDotInFrontOfUser() {
        val dots = listOf(
            CaptureDot(yaw = -180f, pitch = 0f, captureOrder = 0),
            CaptureDot(yaw = 90f, pitch = 0f, captureOrder = 1),
            CaptureDot(yaw = 0f, pitch = 0f, captureOrder = 2),
        )
        assertEquals(0f, CaptureGuidance.orderDotsForCapture(dots).first().yaw, 0.001f)
    }

    @Test
    fun selectsOnlyOneActiveDotAndExcludesCapturedOrSkippedDotsFromOverlay() {
        val dots = listOf(
            CaptureDot(id = "a", yaw = 0f, pitch = 0f, captureOrder = 0),
            CaptureDot(id = "b", yaw = 30f, pitch = 0f, captureOrder = 1),
            CaptureDot(id = "c", yaw = 60f, pitch = 0f, captureOrder = 2),
        )
        val active = CaptureGuidance.activeDotForCapture(dots, capturedIds = setOf("a"), skippedIds = setOf("b"))
        assertEquals("c", active?.id)
        val visible = CaptureGuidance.guidanceFor(targetYaw = 60f, targetPitch = 0f, currentYaw = 60f, currentPitch = 0f)
        assertEquals(listOf(active), CaptureGuidance.visibleOverlayDots(active, visible))
        val hidden = CaptureGuidance.guidanceFor(targetYaw = 160f, targetPitch = 0f, currentYaw = 0f, currentPitch = 0f)
        assertTrue(CaptureGuidance.visibleOverlayDots(active, hidden).isEmpty())
    }

    @Test
    fun projectsOnlyActiveTargetState() {
        val dot = CaptureDot(id = "active", yaw = 0f, pitch = 0f, captureOrder = 0)
        val state = CaptureGuidance.activeTargetProjection(
            activeDot = dot,
            currentYaw = 0f,
            currentPitch = 0f,
            viewportWidth = 1000f,
            viewportHeight = 500f,
            holdStart = 0L,
            nowMillis = 500L,
        )
        assertEquals(dot, state.dot)
        assertTrue(state.guidance?.isAimed == true)
        assertTrue(state.screenPoint != null)
        assertEquals(0.5f, state.holdProgress, 0.001f)
    }

    @Test
    fun holdCompletesAfterRequiredTimeAndResetsWhenNotAimed() {
        val start = CaptureGuidance.nextHoldStart(currentHoldStart = null, aimed = true, targetId = "dot", lastTargetId = "dot", nowMillis = 100)
        assertEquals(100L, start)
        assertFalse(CaptureGuidance.isHoldComplete(start, nowMillis = 900))
        assertTrue(CaptureGuidance.isHoldComplete(start, nowMillis = 1_100))
        assertNull(CaptureGuidance.nextHoldStart(currentHoldStart = start, aimed = false, targetId = "dot", lastTargetId = "dot", nowMillis = 1_200))
    }

    @Test
    fun blocksProcessingEmptyCaptureSessions() {
        assertFalse(CaptureGuidance.canProcessCapturedFrames(0))
        assertTrue(CaptureGuidance.canProcessCapturedFrames(1))
    }
}
