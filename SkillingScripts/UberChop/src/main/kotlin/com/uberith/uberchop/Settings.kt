package com.uberith.uberchop

import com.uberith.api.game.skills.woodcutting.Trees
import com.uberith.api.script.handlers.AfkSettings
import com.uberith.api.script.handlers.BreakSettings
import com.uberith.api.script.handlers.LogoutSettings
import com.uberith.api.script.handlers.WorldHopSettings

/** Persisted configuration for UberChop. */
data class UberChopSettings(
    val treeIndex: Int = 0,
    val locationName: String = "",
    val withdrawWoodBox: Boolean = false,
    val customLocations: Map<String, CustomLocation> = emptyMap(),
    val breakSettings: BreakSettings = BreakSettings(),
    val logoutSettings: LogoutSettings = LogoutSettings(),
    val afkSettings: AfkSettings = AfkSettings(),
    val worldHopSettings: WorldHopSettings = WorldHopSettings()
)

/** Optional coordinate overrides for a named location. */
data class CustomLocation(
    val chopX: Int? = null,
    val chopY: Int? = null,
    val chopZ: Int? = null,
    val bankX: Int? = null,
    val bankY: Int? = null,
    val bankZ: Int? = null
) {
    fun chopCoords(): Triple<Int?, Int?, Int?> = Triple(chopX, chopY, chopZ)
    fun bankCoords(): Triple<Int?, Int?, Int?> = Triple(bankX, bankY, bankZ)
}

object TreeTypes {
    val ALL: List<String> = Trees.allNamesCamelCase()
}
