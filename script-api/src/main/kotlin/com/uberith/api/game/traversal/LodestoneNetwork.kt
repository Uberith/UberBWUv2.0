package com.uberith.api.game.traversal

import com.uberith.api.game.query.ComponentQuery

/**
 * Helpers for interacting with the Lodestone Network UI.
 * Mirrors behavior from external XAPI, adapted for Kotlin.
 */
object LodestoneNetwork {

    /** Returns true if the Lodestone Network interface appears to be open. */
    fun isOpen(): Boolean = try { ComponentQuery.newQuery(1092).results().first() != null } catch (_: Throwable) { false }

    /**
     * Attempts to open the Lodestone Network.
     * Note: Interaction on the component is pending proper hook; returns false for now.
     */
    fun open(): Boolean {
        val result = try {
            ComponentQuery.newQuery(1465)
                .option("Lodestone network")
                .results()
                .first()
        } catch (_: Throwable) { null }

        // TODO: Wire up proper interaction once component interaction is supported here.
        // Example intent:
        // return result != null && result.interact("Lodestone network") > 0
        return false
    }

    /**
     * Attempts to use the "Previous Destination" action from the Lodestone Network.
     * Note: Interaction on the component is pending proper hook; returns false for now.
     */
    fun teleportToPreviousDestination(): Boolean {
        val result = try {
            ComponentQuery.newQuery(1465)
                .option("Previous Destination")
                .results()
                .first()
        } catch (_: Throwable) { null }

        // TODO: Wire up proper interaction once component interaction is supported here.
        // Example intent:
        // return result != null && result.interact("Previous Destination") > 0
        return false
    }
}
