package com.uberith.api.script.handlers

import com.uberith.api.script.SuspendableScript
import org.slf4j.LoggerFactory
import kotlin.math.max
import kotlin.random.Random

/** Settings for opportunistic break scheduling. */
data class BreakSettings(
    val enabled: Boolean = false,
    val frequencyMinutes: Int = 45,
    val varianceMinutes: Int = 15,
    val minDurationSeconds: Int = 60,
    val maxDurationSeconds: Int = 240
)

/**
 * Issues periodic AFK breaks using script suspension helpers.
 * - Call [update] whenever settings change.
 * - Invoke [tick] each loop; it returns true while the break is in progress.
 */
class BreakScheduler(settings: BreakSettings = BreakSettings()) {

    private val logger = LoggerFactory.getLogger(BreakScheduler::class.java)
    private var settings: BreakSettings = settings
    private var nextBreakAtMs: Long? = null
    private var breakEndsAtMs: Long? = null

    fun update(settings: BreakSettings, nowMs: Long = System.currentTimeMillis()) {
        this.settings = settings
        if (!settings.enabled) {
            nextBreakAtMs = null
            breakEndsAtMs = null
        } else if (nextBreakAtMs == null) {
            scheduleNext(nowMs)
        }
    }

    suspend fun tick(script: SuspendableScript, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!settings.enabled) return false
        if (breakEndsAtMs != null && nowMs < breakEndsAtMs!!) {
            val ticksRemaining = millisToTicks(max(0L, breakEndsAtMs!! - nowMs))
            if (ticksRemaining > 0) {
                logger.info("[BreakScheduler] break in progress ({} ms remaining)", breakEndsAtMs!! - nowMs)
                script.awaitTicks(ticksRemaining)
            }
            return true
        }

        if (nextBreakAtMs != null && nowMs >= nextBreakAtMs!!) {
            val durationMs = Random.nextInt(
                settings.minDurationSeconds * 1000,
                (settings.maxDurationSeconds * 1000).coerceAtLeast((settings.minDurationSeconds * 1000) + 1000)
            )
            breakEndsAtMs = nowMs + durationMs
            nextBreakAtMs = null
            logger.info("[BreakScheduler] starting break for {} seconds", durationMs / 1000)
            script.awaitTicks(millisToTicks(durationMs.toLong()))
            return true
        }

        if (nextBreakAtMs == null) {
            scheduleNext(nowMs)
        }
        return false
    }

    private fun scheduleNext(nowMs: Long) {
        val base = settings.frequencyMinutes.coerceAtLeast(1) * 60 * 1000L
        val variance = settings.varianceMinutes.coerceAtLeast(0) * 60 * 1000L
        val window = if (variance == 0L) base else Random.nextLong(base - variance, base + variance + 1L)
        nextBreakAtMs = nowMs + window.coerceAtLeast(60_000L)
        logger.info("[BreakScheduler] next break scheduled in {} seconds", (nextBreakAtMs!! - nowMs) / 1000)
    }

    private fun millisToTicks(ms: Long): Int {
        val ticks = (ms / 600L).toInt()
        return ticks.coerceAtLeast(1)
    }
}
