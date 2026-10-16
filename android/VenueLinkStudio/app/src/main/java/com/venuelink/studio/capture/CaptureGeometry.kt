package com.venuelink.studio.capture

import com.venuelink.studio.data.model.CaptureDot
import kotlin.math.*

object CaptureGeometry {
    // A rotation-only panorama needs a stationary optical centre, not merely
    // stationary feet. The worker also checks the true shutter positions.
    const val MaxDistanceFromStartMeters = .12f

    fun isNearStartingPoint(originPose: FloatArray, currentPose: FloatArray): Boolean =
        translation(originPose, currentPose) <= MaxDistanceFromStartMeters

    fun rotationalPanoramaPose(originPose: FloatArray, cameraPose: FloatArray): FloatArray {
        require(originPose.size >= 16 && cameraPose.size >= 16)
        return cameraPose.copyOf()
    }

    fun ultrawideTargets(): List<CaptureDot> {
        val directions = buildList {
            // Seven horizon views provide wall overlap in portrait capture.
            // The tilted rings also cover the ceiling and floor poles.
            for (index in 0 until 7) add(index * 360f / 7 to 0f)
            for (yaw in 0 until 360 step 90) add(yaw.toFloat() to 55f)
            for (yaw in 45 until 360 step 90) add(yaw.toFloat() to -55f)
        }
        return directions.mapIndexed { index, (yaw, pitch) ->
            CaptureDot(id = "ultrawide-$index", yaw = yaw, pitch = pitch, captureOrder = index)
        }
    }

    fun targets(width: Int, height: Int, fx: Float, fy: Float): List<CaptureDot> {
        require(width > 0 && height > 0 && fx > 0 && fy > 0)
        val fov = min(Math.toDegrees(2 * atan(width / (2.0 * fx))), Math.toDegrees(2 * atan(height / (2.0 * fy))))
        val step = (fov * .85).coerceIn(20.0,42.0)
        val rings = ceil(180.0 / step).toInt()
        val dots = mutableListOf<CaptureDot>()
        for (ring in 0..rings) {
            val pitch = -90.0 + ring * 180.0 / rings
            val count = max(1,ceil(360.0 * cos(Math.toRadians(pitch)) / step).toInt())
            for (index in 0 until count) dots += CaptureDot(id="target-$ring-$index",yaw=(index*360.0/count-180).toFloat(),pitch=pitch.toFloat(),captureOrder=dots.size)
        }
        return dots.sortedWith(compareBy({abs(it.pitch)},{it.pitch},{(it.yaw+360f)%360f})).mapIndexed { index,dot -> dot.copy(captureOrder=index) }
    }
    fun origin(pose: FloatArray): FloatArray {
        val x=pose[8]; val z=pose[10]; val length=sqrt(x*x+z*z).coerceAtLeast(.001f)
        return floatArrayOf(z/length,0f,-x/length,0f, 0f,1f,0f,0f, x/length,0f,z/length,0f, pose[12],pose[13],pose[14],1f)
    }
    fun translation(a:FloatArray,b:FloatArray):Float = sqrt((12..14).sumOf { (a[it]-b[it]).toDouble().pow(2) }).toFloat()
    fun rotationDegrees(a:FloatArray,b:FloatArray):Float {
        var trace=0.0
        for(col in 0..2) for(row in 0..2) trace+=(a[col*4+row]*b[col*4+row]).toDouble()
        return Math.toDegrees(acos(((trace-1)/2).coerceIn(-1.0,1.0))).toFloat()
    }
}
