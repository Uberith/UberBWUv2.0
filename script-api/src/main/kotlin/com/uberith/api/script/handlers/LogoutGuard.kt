package com.uberith.api.script.handlers

import com.uberith.api.script.RuntimeTracker

/** Settings for automated logouts after runtime or target goals. */
data class LogoutSettings(
    val enabled: Boolean = false,
    val maxHours: Int = 0,
    val maxMinutes: Int = 0,
    val maxSeconds: Int = 0,
    val targetXpGained: Int = 0,
    val targetActions: Long = 0L
)

/** Simple guard that signals when a logout condition is met. */
class LogoutGuard(settings: LogoutSettings = LogoutSettings()) {

    private var settings: LogoutSettings = settings
    private var initialSnapshot: RuntimeTracker.Snapshot? = null

    fun update(settings: LogoutSettings, baseline: RuntimeTracker.Snapshot? = null) {
        this.settings = settings
        if (baseline != null) {
            initialSnapshot = baseline
        }
    }

    fun shouldLogout(current: RuntimeTracker.Snapshot, progressCounter: Long = 0L): Boolean {
        if (!settings.enabled) return false

        val limitSeconds = (settings.maxHours * 3600) + (settings.maxMinutes * 60) + settings.maxSeconds
        val limitMs = limitSeconds.coerceAtLeast(0) * 1000L
        if (limitMs > 0 && current.elapsedMillis >= limitMs) {
            return true
        }

        if (settings.targetActions > 0 && progressCounter >= settings.targetActions) {
            return true
        }

        val baseline = initialSnapshot
        if (settings.targetXpGained > 0 && baseline != null) {
            val gained = current.count("xp") - baseline.count("xp")
            if (gained >= settings.targetXpGained) {
                return true
            }
        }
        return false
    }
}
