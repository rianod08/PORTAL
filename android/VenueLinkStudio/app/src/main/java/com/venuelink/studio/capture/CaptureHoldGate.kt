package com.venuelink.studio.capture

/** Requires one uninterrupted second of aiming and physical stillness per target. */
class CaptureHoldGate(private val holdNanos: Long = 1_000_000_000L) {
    private var activeTargetId: String? = null
    private var startedAtNanos = 0L

    fun progress(targetId: String?, eligible: Boolean, timestampNanos: Long): Float {
        if (!eligible || targetId == null || timestampNanos <= 0L) {
            reset()
            return 0f
        }
        if (activeTargetId != targetId || startedAtNanos == 0L || timestampNanos < startedAtNanos) {
            activeTargetId = targetId
            startedAtNanos = timestampNanos
        }
        return ((timestampNanos - startedAtNanos).toDouble() / holdNanos).toFloat().coerceIn(0f, 1f)
    }

    fun reset() {
        activeTargetId = null
        startedAtNanos = 0L
    }
}
