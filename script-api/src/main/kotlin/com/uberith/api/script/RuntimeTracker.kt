package com.uberith.api.script

import kotlin.math.max

/**
 * Tracks elapsed runtime and arbitrary counters for per-hour projections.
 */
class RuntimeTracker {

    data class Snapshot(
        val elapsedMillis: Long,
        val counters: Map<String, Long>
    ) {
        fun formattedTime(): String {
            val totalSeconds = elapsedMillis / 1000
            val h = totalSeconds / 3600
            val m = (totalSeconds % 3600) / 60
            val s = totalSeconds % 60
            return String.format("%02d:%02d:%02d", h, m, s)
        }

        fun perHour(key: String): Int {
            val count = counters[key] ?: return 0
            val ms = max(1L, elapsedMillis)
            return ((count.toDouble() / ms.toDouble()) * 3_600_000.0).toInt()
        }

        fun count(key: String): Long = counters[key] ?: 0L
    }

    private var accumulatedMs = 0L
    private var lastUpdate = 0L
    private val counters = linkedMapOf<String, Long>()

    fun reset() {
        accumulatedMs = 0L
        lastUpdate = 0L
        counters.clear()
    }

    fun start() {
        reset()
        mark()
    }

    fun mark() {
        val now = System.currentTimeMillis()
        if (lastUpdate != 0L) {
            accumulatedMs += (now - lastUpdate)
        }
        lastUpdate = now
    }

    fun stop() {
        mark()
    }

    fun increment(key: String, delta: Long = 1L): Long {
        val updated = (counters[key] ?: 0L) + delta
        counters[key] = updated
        return updated
    }

    fun snapshot(): Snapshot = Snapshot(accumulatedMs, counters.toMap())
}
