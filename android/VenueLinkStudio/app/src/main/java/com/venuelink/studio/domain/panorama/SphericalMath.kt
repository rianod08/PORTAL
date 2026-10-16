package com.venuelink.studio.domain.panorama

import androidx.compose.ui.geometry.Offset
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

object SphericalMath {
    fun normalizeYaw(yaw: Float): Float {
        var value = yaw % 360f
        if (value > 180f) value -= 360f
        if (value < -180f) value += 360f
        return value
    }

    fun clampPitch(pitch: Float): Float = pitch.coerceIn(-89f, 89f)

    fun angularDistance(yaw1: Float, pitch1: Float, yaw2: Float, pitch2: Float): Float {
        val y1 = Math.toRadians(yaw1.toDouble())
        val p1 = Math.toRadians(pitch1.toDouble())
        val y2 = Math.toRadians(yaw2.toDouble())
        val p2 = Math.toRadians(pitch2.toDouble())
        val dot = sin(p1) * sin(p2) + cos(p1) * cos(p2) * cos(y1 - y2)
        return Math.toDegrees(acos(dot.coerceIn(-1.0, 1.0))).toFloat()
    }

    fun isHotspotVisible(hotspotYaw: Float, hotspotPitch: Float, currentYaw: Float, currentPitch: Float, fov: Float): Boolean {
        val yawDelta = abs(normalizeYaw(hotspotYaw - currentYaw))
        val pitchDelta = abs(hotspotPitch - currentPitch)
        return yawDelta < fov * 0.68f && pitchDelta < fov * 0.55f
    }

    fun yawPitchToScreenPoint(
        yaw: Float,
        pitch: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        currentYaw: Float,
        currentPitch: Float,
        fov: Float,
    ): Offset? {
        if (!isHotspotVisible(yaw, pitch, currentYaw, currentPitch, fov)) return null
        val yawDelta = normalizeYaw(yaw - currentYaw)
        val pitchDelta = pitch - currentPitch
        val x = viewportWidth / 2f + (yawDelta / fov) * viewportWidth
        val y = viewportHeight / 2f - (pitchDelta / fov) * viewportHeight
        return Offset(x.coerceIn(0f, viewportWidth), y.coerceIn(0f, viewportHeight))
    }

    fun screenPointToYawPitch(
        x: Float,
        y: Float,
        viewportWidth: Float,
        viewportHeight: Float,
        currentYaw: Float,
        currentPitch: Float,
        fov: Float,
    ): Pair<Float, Float> {
        val yaw = normalizeYaw(currentYaw + ((x - viewportWidth / 2f) / viewportWidth) * fov)
        val pitch = clampPitch(currentPitch - ((y - viewportHeight / 2f) / viewportHeight) * fov)
        return yaw to pitch
    }
}
