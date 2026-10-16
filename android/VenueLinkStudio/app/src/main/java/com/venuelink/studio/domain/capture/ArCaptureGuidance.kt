package com.venuelink.studio.domain.capture

import androidx.compose.ui.geometry.Offset
import com.venuelink.studio.data.model.CaptureDot
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

data class ArTargetProjection(
    val dot: CaptureDot,
    val screenPoint: Offset?,
    val arrow: CaptureArrowDirection?,
    val isVisible: Boolean,
    val isAimed: Boolean,
    val cameraDirection: FloatArray,
    val worldDirection: FloatArray,
)

object ArCaptureGuidance {
    private const val HorizontalFovDegrees = 70f

    fun captureDirectionForDot(dot: CaptureDot): FloatArray {
        val yaw = Math.toRadians(dot.yaw.toDouble())
        val pitch = Math.toRadians(dot.pitch.toDouble())
        val cosPitch = cos(pitch).toFloat()
        return floatArrayOf(
            (sin(yaw) * cosPitch).toFloat(),
            sin(pitch).toFloat(),
            (-cos(yaw) * cosPitch).toFloat(),
        ).normalized()
    }

    fun worldDirectionFromOrigin(originCameraPoseMatrix: FloatArray, dot: CaptureDot): FloatArray =
        rotateByCameraPose(originCameraPoseMatrix, captureDirectionForDot(dot)).normalized()

    fun projectActiveTarget(
        dot: CaptureDot,
        originCameraPoseMatrix: FloatArray,
        currentCameraPoseMatrix: FloatArray,
        viewportWidth: Float,
        viewportHeight: Float,
        aimThresholdDegrees: Float = CaptureGuidance.AimThresholdDegrees,
        projectionMatrix: FloatArray? = null,
    ): ArTargetProjection {
        val worldDirection = worldDirectionFromOrigin(originCameraPoseMatrix, dot)
        // A panorama target is a direction, not a nearby AR point. ARCore's
        // estimated translation can drift while the user only tilts the phone.
        val cameraDirection = worldDirectionToCamera(currentCameraPoseMatrix, worldDirection).normalized()
        val point = if (projectionMatrix != null && projectionMatrix.size == 16 && cameraDirection[2] < -0.01f) {
            val p = projectionMatrix
            val x = p[0]*cameraDirection[0]+p[4]*cameraDirection[1]+p[8]*cameraDirection[2]
            val y = p[1]*cameraDirection[0]+p[5]*cameraDirection[1]+p[9]*cameraDirection[2]
            val w = p[3]*cameraDirection[0]+p[7]*cameraDirection[1]+p[11]*cameraDirection[2]
            if (w > 0f) Offset((x/w+1f)*viewportWidth/2f,(1f-y/w)*viewportHeight/2f) else null
        } else projectCameraDirection(cameraDirection, viewportWidth, viewportHeight)
        val inFront = cameraDirection[2] < -0.01f
        val visible = inFront && point != null && point.x in 0f..viewportWidth && point.y in 0f..viewportHeight
        val degreesFromCenter = Math.toDegrees(kotlin.math.acos((-cameraDirection[2]).coerceIn(-1f, 1f).toDouble())).toFloat()
        val aimed = visible && degreesFromCenter <= aimThresholdDegrees
        return ArTargetProjection(
            dot = dot,
            screenPoint = if (visible) point else null,
            arrow = if (visible) null else arrowForCameraDirection(cameraDirection, point, viewportWidth, viewportHeight),
            isVisible = visible,
            isAimed = aimed,
            cameraDirection = cameraDirection,
            worldDirection = worldDirection,
        )
    }

    fun worldDirectionToCamera(cameraPoseMatrix: FloatArray, worldDirection: FloatArray): FloatArray {
        require(cameraPoseMatrix.size >= 16) { "Camera pose matrix must have 16 values." }
        val x = cameraPoseMatrix[0] * worldDirection[0] + cameraPoseMatrix[1] * worldDirection[1] + cameraPoseMatrix[2] * worldDirection[2]
        val y = cameraPoseMatrix[4] * worldDirection[0] + cameraPoseMatrix[5] * worldDirection[1] + cameraPoseMatrix[6] * worldDirection[2]
        val z = cameraPoseMatrix[8] * worldDirection[0] + cameraPoseMatrix[9] * worldDirection[1] + cameraPoseMatrix[10] * worldDirection[2]
        return floatArrayOf(x, y, z)
    }

    fun projectCameraDirection(cameraDirection: FloatArray, viewportWidth: Float, viewportHeight: Float): Offset? {
        if (viewportWidth <= 0f || viewportHeight <= 0f || cameraDirection[2] >= -0.01f) return null
        val aspect = viewportWidth / viewportHeight
        val horizontalTan = tan(Math.toRadians((HorizontalFovDegrees / 2f).toDouble())).toFloat()
        val verticalTan = horizontalTan / aspect.coerceAtLeast(0.01f)
        val normalizedX = (cameraDirection[0] / -cameraDirection[2]) / horizontalTan
        val normalizedY = (cameraDirection[1] / -cameraDirection[2]) / verticalTan
        return Offset(
            x = (0.5f + normalizedX * 0.5f) * viewportWidth,
            y = (0.5f - normalizedY * 0.5f) * viewportHeight,
        )
    }

    fun arrowForCameraDirection(
        cameraDirection: FloatArray,
        projectedPoint: Offset?,
        viewportWidth: Float,
        viewportHeight: Float,
    ): CaptureArrowDirection {
        val dx: Float
        val dy: Float
        if (projectedPoint != null) {
            dx = projectedPoint.x - viewportWidth / 2f
            dy = viewportHeight / 2f - projectedPoint.y
        } else {
            dx = cameraDirection[0]
            dy = cameraDirection[1]
        }
        return if (abs(dx) >= abs(dy)) {
            if (dx < 0f) CaptureArrowDirection.Left else CaptureArrowDirection.Right
        } else {
            if (dy > 0f) CaptureArrowDirection.Up else CaptureArrowDirection.Down
        }
    }

    fun identityPoseMatrix(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )

    private fun rotateByCameraPose(cameraPoseMatrix: FloatArray, cameraDirection: FloatArray): FloatArray {
        require(cameraPoseMatrix.size >= 16) { "Camera pose matrix must have 16 values." }
        val x = cameraPoseMatrix[0] * cameraDirection[0] + cameraPoseMatrix[4] * cameraDirection[1] + cameraPoseMatrix[8] * cameraDirection[2]
        val y = cameraPoseMatrix[1] * cameraDirection[0] + cameraPoseMatrix[5] * cameraDirection[1] + cameraPoseMatrix[9] * cameraDirection[2]
        val z = cameraPoseMatrix[2] * cameraDirection[0] + cameraPoseMatrix[6] * cameraDirection[1] + cameraPoseMatrix[10] * cameraDirection[2]
        return floatArrayOf(x, y, z)
    }

    private fun FloatArray.normalized(): FloatArray {
        val length = kotlin.math.sqrt((this[0] * this[0] + this[1] * this[1] + this[2] * this[2]).toDouble()).toFloat()
        if (length <= 0.0001f) return floatArrayOf(0f, 0f, -1f)
        return floatArrayOf(this[0] / length, this[1] / length, this[2] / length)
    }
}
