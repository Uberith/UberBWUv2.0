package com.uberith.api.game.world

import net.botwithus.rs3.world.Coordinate

/**
 * Generic in‑game location descriptor with optional activity and bank coordinates.
 */
data class Location(
    val name: String,
    val bank: Coordinate?,
    val activity: Coordinate? = null,
    val tags: Set<String> = emptySet()
)

/**
 * Central registry of reusable locations. Scripts can read or populate this at runtime.
 *
 * Examples:
 * - Register at startup: `Locations.register(Location("Varrock West", bank = Coordinate(...)))`
 * - Iterate all: `Locations.ALL.forEach { ... }`
 */
object Locations {
    @JvmField
    val ALL: MutableList<Location> = mutableListOf()

    /** Replaces any existing entry with the same [name], then adds [location]. */
    fun register(location: Location) {
        val idx = ALL.indexOfFirst { it.name.equals(location.name, ignoreCase = true) }
        if (idx >= 0) ALL[idx] = location else ALL.add(location)
    }

    fun find(name: String): Location? =
        ALL.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

