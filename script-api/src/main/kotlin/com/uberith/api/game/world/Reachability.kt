package com.uberith.api.game.world

import net.botwithus.rs3.world.Coordinate
import net.botwithus.rs3.world.Distance

/**
 * Pluggable reachability checks for coordinates. By default, falls back to a
 * lightweight heuristic when no checker is installed.
 *
 * Install your navigation/pathfinding predicate at startup:
 *   Reachability.install { coord -> myNavigator.canReach(coord) }
 */
object Reachability {

    /**
     * Optional global predicate for reachability. If null, [isReachable] falls back
     * to a distance-based heuristic.
     */
    @Volatile
    var checker: ((Coordinate) -> Boolean)? = null

    /** Installs a global reachability predicate. */
    fun install(check: (Coordinate) -> Boolean) { checker = check }

    /** Clears any installed predicate and uses the fallback heuristic. */
    fun reset() { checker = null }

    /**
     * Returns true when [destination] is considered reachable.
     *
     * Behavior:
     * - If a [checker] is installed, delegates to it.
     * - Otherwise, uses a simple heuristic: within ~120 tiles.
     */
    fun isReachable(destination: Coordinate?): Boolean {
        if (destination == null) return false
        val c = checker
        return if (c != null) c(destination) else Distance.to(destination) <= 120
    }
}

