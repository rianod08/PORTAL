package com.venuelink.studio.domain.capture

import androidx.compose.ui.geometry.Offset
import com.venuelink.studio.data.model.CaptureDot
import com.venuelink.studio.domain.panorama.SphericalMath
import kotlin.math.abs

enum class CaptureArrowDirection(val label: String, val symbol: String) {
    Left("Turn left", "<"),
    Right("Turn right", ">"),
    Up("Tilt up", "^"),
    Down("Tilt down", "v"),
}

data class CaptureGuidanceState(
    val yaw: Float,
    val pitch: Float,
    val yawDelta: Float,
    val pitchDelta: Float,
    val isVisible: Boolean,
    val isAimed: Boolean,
    val arrow: CaptureArrowDirection?,
)

data class ActiveCaptureTargetState(
    val dot: CaptureDot?,
    val screenPoint: Offset?,
    val guidance: CaptureGuidanceState?,
    val holdProgress: Float,
)

object CaptureGuidance {
    const val CaptureFov = 78f
    const val AimThresholdDegrees = 9f
    const val HoldMillis = 1_000L

    fun calibratedYaw(rawYaw: Float, yawOffset: Float): Float =
        SphericalMath.normalizeYaw(rawYaw - yawOffset)

    fun calibratedPitch(rawPitch: Float, pitchOffset: Float): Float =
        SphericalMath.clampPitch(rawPitch - pitchOffset)

    fun smoothYaw(previousYaw: Float, nextYaw: Float, alpha: Float = 0.18f): Float {
        val delta = SphericalMath.normalizeYaw(nextYaw - previousYaw)
        return SphericalMath.normalizeYaw(previousYaw + delta * alpha.coerceIn(0f, 1f))
    }

    fun smoothPitch(previousPitch: Float, nextPitch: Float, alpha: Float = 0.18f): Float =
        SphericalMath.clampPitch(previousPitch + (nextPitch - previousPitch) * alpha.coerceIn(0f, 1f))

    fun guidanceFor(
        targetYaw: Float,
        targetPitch: Float,
        currentYaw: Float,
        currentPitch: Float,
        fov: Float = CaptureFov,
    ): CaptureGuidanceState {
        val yawDelta = SphericalMath.normalizeYaw(targetYaw - currentYaw)
        val pitchDelta = targetPitch - currentPitch
        val visible = SphericalMath.isHotspotVisible(targetYaw, targetPitch, currentYaw, currentPitch, fov)
        val aimed = SphericalMath.angularDistance(currentYaw, currentPitch, targetYaw, targetPitch) <= AimThresholdDegrees
        val arrow = if (visible) null else arrowForDelta(yawDelta, pitchDelta)
        return CaptureGuidanceState(
            yaw = currentYaw,
            pitch = currentPitch,
            yawDelta = yawDelta,
            pitchDelta = pitchDelta,
            isVisible = visible,
            isAimed = aimed,
            arrow = arrow,
        )
    }

    fun arrowForDelta(yawDelta: Float, pitchDelta: Float): CaptureArrowDirection {
        return if (abs(yawDelta) >= abs(pitchDelta)) {
            if (yawDelta < 0f) CaptureArrowDirection.Left else CaptureArrowDirection.Right
        } else {
            if (pitchDelta > 0f) CaptureArrowDirection.Up else CaptureArrowDirection.Down
        }
    }

    fun orderDotsForCapture(dots: List<com.venuelink.studio.data.model.CaptureDot>) =
        dots.sortedWith(compareBy({ abs(SphericalMath.normalizeYaw(it.yaw)) }, { abs(it.pitch) }, { it.captureOrder }))

    fun activeDotForCapture(dots: List<CaptureDot>, capturedIds: Set<String>, skippedIds: Set<String>): CaptureDot? =
        dots.firstOrNull { it.id !in capturedIds && it.id !in skippedIds }

    fun visibleOverlayDots(activeDot: CaptureDot?, guidance: CaptureGuidanceState?): List<CaptureDot> =
        if (activeDot != null && guidance?.isVisible == true) listOf(activeDot) else emptyList()

    fun activeTargetProjection(
        activeDot: CaptureDot?,
        currentYaw: Float,
        currentPitch: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        holdStart: Long?,
        nowMillis: Long,
        fov: Float = CaptureFov,
    ): ActiveCaptureTargetState {
        if (activeDot == null) return ActiveCaptureTargetState(null, null, null, 0f)
        val guidance = guidanceFor(activeDot.yaw, activeDot.pitch, currentYaw, currentPitch, fov)
        val screenPoint = if (guidance.isVisible) {
            SphericalMath.yawPitchToScreenPoint(activeDot.yaw, activeDot.pitch, viewportWidth, viewportHeight, currentYaw, currentPitch, fov)
        } else {
            null
        }
        return ActiveCaptureTargetState(
            dot = activeDot,
            screenPoint = screenPoint,
            guidance = guidance,
            holdProgress = holdProgress(holdStart, nowMillis),
        )
    }

    fun nextHoldStart(currentHoldStart: Long?, aimed: Boolean, targetId: String?, lastTargetId: String?, nowMillis: Long): Long? =
        if (!aimed || targetId == null) null else if (targetId != lastTargetId) nowMillis else currentHoldStart ?: nowMillis

    fun isHoldComplete(holdStart: Long?, nowMillis: Long, holdMillis: Long = HoldMillis): Boolean =
        holdStart != null && nowMillis - holdStart >= holdMillis

    fun holdProgress(holdStart: Long?, nowMillis: Long, holdMillis: Long = HoldMillis): Float =
        if (holdStart == null) 0f else ((nowMillis - holdStart).toFloat() / holdMillis.toFloat()).coerceIn(0f, 1f)

    fun canProcessCapturedFrames(frameCount: Int): Boolean = frameCount > 0
}
