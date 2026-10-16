package com.venuelink.studio.ui.components

/** Carries the last AR pose through a temporary visual-tracking pause. Matrices are column-major. */
internal object CameraPoseFusion {
    data class Estimate(val display: FloatArray, val physical: FloatArray)

    fun estimate(
        trackedDisplay: FloatArray,
        trackedPhysical: FloatArray,
        sensorAtTrack: FloatArray,
        sensorNow: FloatArray,
        trackedAndroidSensor: FloatArray = trackedDisplay,
    ): Estimate {
        val deviceTurn = multiply(transposeRotation(sensorAtTrack), sensorNow)
        // The device sensor axes are not the display or physical image axes.
        // Apply the turn in the ARCore Android-sensor frame, then restore each
        // camera's extrinsic rotation. This also works in landscape orientation.
        val worldSensor = multiply(rotationOnly(trackedAndroidSensor), deviceTurn)
        val sensorToDisplay = multiply(transposeRotation(trackedAndroidSensor), rotationOnly(trackedDisplay))
        val sensorToPhysical = multiply(transposeRotation(trackedAndroidSensor), rotationOnly(trackedPhysical))
        val display = multiply(worldSensor, sensorToDisplay)
        val physical = multiply(worldSensor, sensorToPhysical)
        display[12] = trackedDisplay[12]
        display[13] = trackedDisplay[13]
        display[14] = trackedDisplay[14]
        physical[12] = trackedPhysical[12]
        physical[13] = trackedPhysical[13]
        physical[14] = trackedPhysical[14]
        return Estimate(display, physical)
    }

    /** SensorManager returns row-major values; ARCore poses and OpenGL use column-major. */
    fun sensorMatrixToPose(sensorMatrix: FloatArray): FloatArray {
        require(sensorMatrix.size == 16)
        val result = FloatArray(16)
        for (row in 0..3) for (col in 0..3) result[col * 4 + row] = sensorMatrix[row * 4 + col]
        return result
    }

    private fun rotationOnly(matrix: FloatArray): FloatArray = matrix.copyOf().also {
        it[12] = 0f; it[13] = 0f; it[14] = 0f
    }

    private fun transposeRotation(matrix: FloatArray): FloatArray {
        val result = identity()
        for (row in 0..2) for (col in 0..2) result[col * 4 + row] = matrix[row * 4 + col]
        return result
    }

    private fun multiply(left: FloatArray, right: FloatArray): FloatArray {
        val result = FloatArray(16)
        for (col in 0..3) for (row in 0..3) for (index in 0..3) {
            result[col * 4 + row] += left[index * 4 + row] * right[col * 4 + index]
        }
        return result
    }

    private fun identity(): FloatArray = floatArrayOf(
        1f, 0f, 0f, 0f,
        0f, 1f, 0f, 0f,
        0f, 0f, 1f, 0f,
        0f, 0f, 0f, 1f,
    )
}
