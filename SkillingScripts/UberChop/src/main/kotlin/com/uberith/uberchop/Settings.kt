package com.uberith.uberchop

import com.uberith.api.game.skills.woodcutting.Trees
data class Settings(
    // Break handler
    var performRandomBreak: Boolean = true,
    var breakFrequency: Int = 2,
    var minBreak: Int = 30,
    var maxBreak: Int = 90,

    // Logout handler
    var logoutDurationEnable: Boolean = false,
    var logoutHours: Int = 1,
    var logoutMinutes: Int = 0,
    var logoutSeconds: Int = 0,

    // AFK handler
    var enableAfk: Boolean = false,
    var afkEveryMin: Int = 30,
    var afkEveryMax: Int = 60,
    var afkDurationMin: Int = 5,
    var afkDurationMax: Int = 15,

    // Auto-Stop handler
    var enableAutoStop: Boolean = false,
    var stopAfterHours: Int = 0,
    var stopAfterMinutes: Int = 0,
    var stopAfterXp: Int = 0,
    var stopAfterLogs: Int = 0,

    // Extra Features
    var pickupNests: Boolean = false,
    var enableTreeRotation: Boolean = false,

    // Control
    var enableWorldHopping: Boolean = false,
    var useMagicNotepaper: Boolean = false,
    var useCrystallise: Boolean = false,
    var useJujuPotions: Boolean = false,

    // Auto-skill
    var autoProgressTree: Boolean = false,
    var autoUpgradeTree: Boolean = false,
    var tanningProductIndex: Int = 0,

    // World hopping filters
    var minPing: Int = 0,
    var maxPing: Int = 500,
    var minPopulation: Int = 0,
    var maxPopulation: Int = 2000,
    var hopDelayMs: Int = 5000,
    var memberOnlyWorlds: Boolean = true,
    var onlyFreeToPlay: Boolean = false,
    var hopOnChat: Boolean = false,
    var hopOnCrowd: Boolean = false,
    var playerThreshold: Int = 3,
    var hopOnNoTrees: Boolean = false,

    var savedTreeType: Int = 0,
    var savedLocation: String = "",
    var logHandlingMode: Int = 0,

    // Items
    var withdrawWoodBox: Boolean = false,

    // Custom per-location overrides: location name -> custom tiles
    var customLocations: MutableMap<String, CustomLocation> = mutableMapOf(),

    // Deposit filters
    var depositInclude: MutableList<String> = mutableListOf(),
    var depositKeep: MutableList<String> = mutableListOf()

)

// Central list of supported tree types for selection and matching.
// Names are matched using case-insensitive contains(...) in targeting logic.
object TreeTypes {
    val ALL: List<String> = Trees.allNamesCamelCase()
}

// Serializable container for user overrides of location tiles
data class CustomLocation(
    var chopX: Int? = null,
    var chopY: Int? = null,
    var chopZ: Int? = null,
    var bankX: Int? = null,
    var bankY: Int? = null,
    var bankZ: Int? = null
)
