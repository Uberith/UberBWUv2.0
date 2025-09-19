package com.uberith.api.script.handlers

import net.botwithus.kxapi.script.SuspendableScript
import org.slf4j.LoggerFactory
import kotlin.random.Random

/** Lightweight AFK jitter injection. */
data class AfkSettings(
    val enabled: Boolean = false,
    val minEveryMinutes: Int = 20,
    val maxEveryMinutes: Int = 40,
    val minDurationSeconds: Int = 10,
    val maxDurationSeconds: Int = 30
)

class AfkJitter(settings: AfkSettings = AfkSettings()) {

    private val logger = LoggerFactory.getLogger(AfkJitter::class.java)

    private var settings: AfkSettings = settings
    private var nextAfkAt: Long? = null

    fun update(settings: AfkSettings, nowMs: Long = System.currentTimeMillis()) {
        this.settings = settings
        if (!settings.enabled) {
            nextAfkAt = null
        } else if (nextAfkAt == null) {
            schedule(nowMs)
        }
    }

    suspend fun tick(script: SuspendableScript, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!settings.enabled) return false
        val next = nextAfkAt ?: run {
            schedule(nowMs)
            nextAfkAt
        } ?: return false

        if (nowMs < next) return false
        val duration = Random.nextInt(
            settings.minDurationSeconds.coerceAtLeast(1),
            settings.maxDurationSeconds.coerceAtLeast(settings.minDurationSeconds + 1) + 1
        )
        logger.info("[AfkJitter] idling for {} seconds", duration)
        script.awaitTicks(millisToTicks(duration * 1000L))
        schedule(nowMs + duration * 1000L)
        return true
    }

    private fun schedule(nowMs: Long) {
        val minMs = settings.minEveryMinutes.coerceAtLeast(1) * 60 * 1000L
        val maxMs = settings.maxEveryMinutes.coerceAtLeast(settings.minEveryMinutes) * 60 * 1000L
        nextAfkAt = nowMs + Random.nextLong(minMs, maxMs + 1L)
    }

    private fun millisToTicks(ms: Long): Int = (ms / 600L).toInt().coerceAtLeast(1)
}
