package com.venuelink.studio.domain.capture

import com.venuelink.studio.data.model.CaptureDot

enum class CaptureProfile { Quick, Standard, HighQuality }

object CaptureProfiles {
    fun getCaptureDots(profile: CaptureProfile): List<CaptureDot> {
        val dots = mutableListOf<CaptureDot>()
        fun ring(pitch: Float, step: Int) {
            var yaw = -180
            while (yaw < 180) {
                dots += CaptureDot(yaw = yaw.toFloat(), pitch = pitch, captureOrder = dots.size)
                yaw += step
            }
        }
        when (profile) {
            CaptureProfile.Quick -> {
                ring(0f, 45); ring(45f, 60); ring(-45f, 60)
            }
            CaptureProfile.Standard -> {
                ring(0f, 30); ring(45f, 45); ring(-45f, 45)
                dots += CaptureDot(yaw = 0f, pitch = 80f, captureOrder = dots.size)
                dots += CaptureDot(yaw = 0f, pitch = -80f, captureOrder = dots.size)
            }
            CaptureProfile.HighQuality -> {
                ring(0f, 20); ring(35f, 30); ring(-35f, 30); ring(65f, 60); ring(-65f, 60)
            }
        }
        return dots.mapIndexed { index, dot -> dot.copy(captureOrder = index) }
    }
}

data class OrientationReading(val yaw: Float = 0f, val pitch: Float = 0f, val roll: Float = 0f)

class OrientationTracker {
    private var last = OrientationReading()
    fun updateCurrentOrientation(yaw: Float, pitch: Float, roll: Float) {
        last = OrientationReading(yaw, pitch, roll)
    }
    fun current(): OrientationReading = last
}
