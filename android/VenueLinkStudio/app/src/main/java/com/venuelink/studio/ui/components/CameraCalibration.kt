package com.venuelink.studio.ui.components

/** Convert calibrated active-array pixels to an uncropped JPEG of the same aspect ratio. */
internal object CameraCalibration {
    fun jpegIntrinsics(
        calibrated: FloatArray?, activeWidth: Int, activeHeight: Int,
        width: Int, height: Int, focalMm: Float, sensorWidthMm: Float, sensorHeightMm: Float,
    ): FloatArray {
        if (calibrated != null && calibrated.size >= 4 && calibrated.take(4).all { it.isFinite() } &&
            calibrated[0] > 0f && calibrated[1] > 0f && activeWidth > 0 && activeHeight > 0) {
            val cropWidth = minOf(activeWidth.toFloat(), activeHeight * width.toFloat() / height)
            val cropHeight = minOf(activeHeight.toFloat(), activeWidth * height.toFloat() / width)
            val left = (activeWidth - cropWidth) / 2f
            val top = (activeHeight - cropHeight) / 2f
            return floatArrayOf(
                calibrated[0] * width / cropWidth,
                calibrated[1] * height / cropHeight,
                (calibrated[2] - left) * width / cropWidth,
                (calibrated[3] - top) * height / cropHeight,
            )
        }
        return floatArrayOf(focalMm / sensorWidthMm * width, focalMm / sensorHeightMm * height, width / 2f, height / 2f)
    }
}
