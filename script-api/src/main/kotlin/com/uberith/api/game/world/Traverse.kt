package com.uberith.api.game.world

import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.minimenu.Action
import net.botwithus.rs3.minimenu.MiniMenu
import net.botwithus.rs3.world.Coordinate
import net.botwithus.rs3.world.Distance
import net.botwithus.util.Rand

/**
 * Lightweight walking helpers for initiating movement to world coordinates.
 *
 * - Uses a simple step-based approach when targets are far away ("Bresenham-like").
 * - Defers to MiniMenu WALK actions for actual movement.
 * - Randomizes step sizes and minimap usage thresholds for human-like behavior.
 */
object Traverse {

    private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(Traverse::class.java)

    private const val MAX_LOCAL_DISTANCE = 80
    private const val MAX_STEP_SIZE = 16
    private const val MIN_STEP_SIZE = 10

    /** Returns true if the destination is considered reachable (see Reachability). */
    fun isReachable(destination: Coordinate?): Boolean = Reachability.isReachable(destination)

    /**
     * Walks to [destination], choosing minimap usage and step size automatically.
     * - If distance >= random(22..27), attempts minimap walking; otherwise uses local.
     * - Step size randomized in [10, 16].
     */
    fun to(destination: Coordinate?): Boolean {
        if (destination == null) {
            logger.warn("[Traverse#to] Destination is null")
            return false
        }
        val dist = Distance.to(destination)
        val useMinimap = dist >= Rand.nextInt(22, 28)
        val stepSize = Rand.nextInt(MIN_STEP_SIZE, MAX_STEP_SIZE + 1)
        return bresenhamTo(destination, useMinimap, stepSize)
    }

    /**
     * Moves toward [destination] by taking a single step along the line from the player's current
     * position to [destination], with maximum length [stepSize]. If within that step size, walks
     * directly to [destination].
     *
     * Returns false when player/position is unavailable.
     */
    fun bresenhamTo(destination: Coordinate, minimap: Boolean, stepSize: Int): Boolean {
        val player = LocalPlayer.self()
        if (player == null) {
            logger.warn("[Traverse#bresenhamTo] Player is null")
            return false
        }
        val current = player.coordinate
        if (current == null) {
            logger.warn("[Traverse#bresenhamTo] Current coordinate is null")
            return false
        }

        val dx = destination.x() - current.x()
        val dy = destination.y() - current.y()
        val dist = kotlin.math.hypot(dx.toDouble(), dy.toDouble())

        val target = if (dist > stepSize) {
            val stepX = current.x() + (dx * stepSize / dist).toInt()
            val stepY = current.y() + (dy * stepSize / dist).toInt()
            Coordinate(stepX, stepY, destination.z())
        } else destination

        return walkTo(target, minimap)
    }

    /**
     * Issues a MiniMenu WALK to [destination]. If very close (< 2 tiles) returns true without walking.
     * If target is beyond [MAX_LOCAL_DISTANCE], falls back to a single bresenham step.
     */
    fun walkTo(destination: Coordinate?, minimap: Boolean): Boolean {
        if (destination == null) {
            logger.warn("[Traverse#walkTo] Destination is null")
            return false
        }
        return try {
            logger.info("[Traverse] Walking to {},{} (z={})", destination.x(), destination.y(), destination.z())

            val dist = Distance.to(destination)
            if (dist < 2) {
                logger.info("[Traverse] Already near destination; skipping walk")
                return true
            }

            if (dist > MAX_LOCAL_DISTANCE) {
                logger.info("[Traverse] Destination too far ({}), taking a step toward it", dist)
                return bresenhamTo(destination, minimap, Rand.nextInt(MIN_STEP_SIZE, MAX_STEP_SIZE))
            }

            val result = MiniMenu.doAction(Action.WALK, if (minimap) 1 else 0, destination.x(), destination.y())
            if (result > 0) {
                logger.info("[Traverse] Walk initiated")
                true
            } else {
                logger.warn("[Traverse] Walk failed: result {}", result)
                false
            }
        } catch (t: Throwable) {
            logger.trace("[Traverse] Exception walking to {},{}: {}", destination.x(), destination.y(), t.message, t)
            false
        }
    }
}
