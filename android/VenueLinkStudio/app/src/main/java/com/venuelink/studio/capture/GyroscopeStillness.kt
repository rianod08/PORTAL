package com.venuelink.studio.capture

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlin.math.sqrt

/** Uses physical angular speed so small AR pose corrections do not reset the hold. */
class GyroscopeStillness(context: Context) : SensorEventListener {
    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    @Volatile private var speedRadiansPerSecond = Float.POSITIVE_INFINITY
    @Volatile private var sampleTimeNanos = 0L
    private var running = false

    fun start() {
        if (running) return
        running = gyroscope != null && manager.registerListener(this, gyroscope, SensorManager.SENSOR_DELAY_GAME)
    }

    fun stop() {
        if (running) manager.unregisterListener(this)
        running = false
        speedRadiansPerSecond = Float.POSITIVE_INFINITY
        sampleTimeNanos = 0L
    }

    fun isStill(): Boolean = running &&
        SystemClock.elapsedRealtimeNanos() - sampleTimeNanos < 300_000_000L &&
        speedRadiansPerSecond <= 0.12f // About 7 degrees per second.

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val current = sqrt(x * x + y * y + z * z)
        speedRadiansPerSecond = if (speedRadiansPerSecond.isInfinite()) current
            else speedRadiansPerSecond * 0.7f + current * 0.3f
        sampleTimeNanos = SystemClock.elapsedRealtimeNanos()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
