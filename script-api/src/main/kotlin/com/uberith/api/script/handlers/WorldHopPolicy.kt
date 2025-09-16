package com.uberith.api.script.handlers

import org.slf4j.LoggerFactory

/** Simple heuristic for deciding when to world-hop. */
data class WorldHopSettings(
    val enabled: Boolean = false,
    val hopOnCrowd: Boolean = false,
    val playerThreshold: Int = 3,
    val hopOnNoResources: Boolean = false,
    val hopCooldownSeconds: Int = 90
)

class WorldHopPolicy(settings: WorldHopSettings = WorldHopSettings()) {

    private val logger = LoggerFactory.getLogger(WorldHopPolicy::class.java)

    private var settings: WorldHopSettings = settings
    private var lastHopMs: Long = 0L

    fun update(settings: WorldHopSettings) {
        this.settings = settings
    }

    fun shouldHop(playersNearby: Int, resourcesRemaining: Int, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (!settings.enabled) return false
        val cooldownMs = settings.hopCooldownSeconds.coerceAtLeast(10) * 1000L
        if (nowMs - lastHopMs < cooldownMs) {
            return false
        }
        if (settings.hopOnCrowd && playersNearby >= settings.playerThreshold) {
            logger.info("[WorldHopPolicy] crowd threshold met -> hop")
            lastHopMs = nowMs
            return true
        }
        if (settings.hopOnNoResources && resourcesRemaining <= 0) {
            logger.info("[WorldHopPolicy] no resources -> hop")
            lastHopMs = nowMs
            return true
        }
        return false
    }
}
