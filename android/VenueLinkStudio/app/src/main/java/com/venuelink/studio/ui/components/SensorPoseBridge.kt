package com.venuelink.studio.ui.components

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock

/** Uses relative device rotation only while ARCore cannot see enough visual features. */
internal class SensorPoseBridge(context: Context) : SensorEventListener {
    private data class TimedRotation(val timestampNanos: Long, val matrix: FloatArray)
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor = manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR)
    private val recentRotations = ArrayDeque<TimedRotation>()
    private var latestSensor: FloatArray? = null
    private var latestSensorNanos = 0L
    private var trackedSensor: FloatArray? = null
    private var trackedDisplay: FloatArray? = null
    private var trackedPhysical: FloatArray? = null
    private var trackedAndroidSensor: FloatArray? = null
    private var lastTrackedNanos = 0L

    fun start() {
        sensor?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
    }

    fun stop() {
        manager.unregisterListener(this)
        synchronized(this) {
            latestSensor = null
            trackedSensor = null
            trackedDisplay = null
            trackedPhysical = null
            trackedAndroidSensor = null
            recentRotations.clear()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type != Sensor.TYPE_GAME_ROTATION_VECTOR) return
        val rowMajor = FloatArray(16)
        SensorManager.getRotationMatrixFromVector(rowMajor, event.values)
        synchronized(this) {
            latestSensor = CameraPoseFusion.sensorMatrixToPose(rowMajor)
            latestSensorNanos = event.timestamp
            recentRotations.addLast(TimedRotation(event.timestamp, latestSensor!!.copyOf()))
            while (recentRotations.size > 256 ||
                (recentRotations.isNotEmpty() && event.timestamp - recentRotations.first().timestampNanos > 4_000_000_000L)) {
                recentRotations.removeFirst()
            }
        }
    }

    fun observeTracked(display: FloatArray, physical: FloatArray, androidSensor: FloatArray, timestampNanos: Long) {
        synchronized(this) {
            val sample = recentRotations.minByOrNull { kotlin.math.abs(it.timestampNanos - timestampNanos) } ?: return
            if (kotlin.math.abs(sample.timestampNanos - timestampNanos) > 100_000_000L) return
            trackedSensor = sample.matrix.copyOf()
            trackedDisplay = display.copyOf()
            trackedPhysical = physical.copyOf()
            trackedAndroidSensor = androidSensor.copyOf()
            lastTrackedNanos = SystemClock.elapsedRealtimeNanos()
        }
    }

    fun estimate(): CameraPoseFusion.Estimate? = estimateAt(SystemClock.elapsedRealtimeNanos(), 500_000_000L)

    /** Match a camera exposure to the nearest rotation-vector event in the elapsed-realtime clock. */
    fun estimateAt(timestampNanos: Long, maxSampleGapNanos: Long = 100_000_000L): CameraPoseFusion.Estimate? = synchronized(this) {
        val now = SystemClock.elapsedRealtimeNanos()
        if (now - lastTrackedNanos > 120_000_000_000L) return null
        val nearest = recentRotations.minByOrNull { kotlin.math.abs(it.timestampNanos - timestampNanos) } ?: return null
        if (kotlin.math.abs(nearest.timestampNanos - timestampNanos) > maxSampleGapNanos) return null
        CameraPoseFusion.estimate(
            trackedDisplay ?: return null,
            trackedPhysical ?: return null,
            trackedSensor ?: return null,
            nearest.matrix,
            trackedAndroidSensor ?: return null,
        )
    }
}
