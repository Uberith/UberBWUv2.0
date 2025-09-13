package com.uberith.api.game.world

import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.world.Coordinate
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Coordinate/positioning helpers for RS3 world space.
 *
 * All distance/area checks are 2D (x,y) by default; `z` is compared for
 * equality only where noted. Functions return null-safe booleans where
 * appropriate to simplify call sites.
 */
object Coordinates {

    /** Returns the local player's current coordinate, or null if unavailable. */
    fun player(): Coordinate? = LocalPlayer.self()?.coordinate

    /** Creates a new [Coordinate] from components. */
    fun of(x: Int, y: Int, z: Int = 0): Coordinate = Coordinate(x, y, z)

    /** Returns a new [Coordinate] offset from [base] by the given deltas. */
    fun offset(base: Coordinate, dx: Int = 0, dy: Int = 0, dz: Int = 0): Coordinate =
        Coordinate(base.x + dx, base.y + dy, base.z + dz)

    /**
     * Exact position match against components.
     * Returns false when the player coordinate is not available.
     */
    fun isPlayerAt(x: Int, y: Int, z: Int = 0): Boolean = player() == Coordinate(x, y, z)

    /** Exact position match between two coordinates (null-safe). */
    fun isAt(a: Coordinate?, b: Coordinate?): Boolean = a != null && b != null && a == b

    /** True when two coordinates are on the same plane (same `z`). */
    fun samePlane(a: Coordinate, b: Coordinate): Boolean = a.z == b.z

    /** 2D Euclidean distance between [a] and [b] (ignores `z`). */
    fun distance(a: Coordinate, b: Coordinate): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

    /** 2D Euclidean distance from player to [target], or null if player is unknown. */
    fun distanceToPlayer(target: Coordinate): Double? = player()?.let { distance(it, target) }

    /**
     * True if [point] lies within [radius] (inclusive) of [center], using 2D distance.
     */
    fun withinRadius(point: Coordinate, center: Coordinate, radius: Int): Boolean =
        distance(point, center) <= radius.toDouble()

    /**
     * True if the player is within [radius] (inclusive) of [center]. Returns false if player is unknown.
     */
    fun isPlayerWithinRadius(center: Coordinate, radius: Int): Boolean =
        player()?.let { withinRadius(it, center, radius) } ?: false

    /**
     * True if [point] is inside the axis-aligned rectangle defined by [minX,minY]..[maxX,maxY].
     * The `z` (plane) is ignored.
     */
    fun withinRect(point: Coordinate, minX: Int, minY: Int, maxX: Int, maxY: Int): Boolean =
        point.x in minX..maxX && point.y in minY..maxY

    /**
     * Convenience: checks if the player is within the axis-aligned rectangle.
     * Returns false if player is unknown.
     */
    fun isPlayerWithinRect(minX: Int, minY: Int, maxX: Int, maxY: Int): Boolean =
        player()?.let { withinRect(it, minX, minY, maxX, maxY) } ?: false

    /**
     * True if [point] is inside an axis-aligned square centered at ([cx],[cy]) with the given [radius].
     * Equivalent to [withinRect] with [cx - radius, cy - radius]..[cx + radius, cy + radius].
     */
    fun withinSquare(point: Coordinate, cx: Int, cy: Int, radius: Int): Boolean =
        withinRect(point, cx - radius, cy - radius, cx + radius, cy + radius)

    /** Returns true when [c] is non-null for readability at call sites. */
    fun isValid(c: Coordinate?): Boolean = c != null

    /**
     * Generates a random integer coordinate within a circle of [radius] around [center].
     * Uses uniform sampling over the area (not just angle) and preserves the plane (`z`).
     */
    fun randomNear(center: Coordinate, radius: Int, rng: Random = Random): Coordinate {
        require(radius >= 0) { "radius must be non-negative" }
        if (radius == 0) return center
        val theta = rng.nextDouble(0.0, Math.PI * 2)
        val r = kotlin.math.sqrt(rng.nextDouble(0.0, 1.0)) * radius
        val dx = (r * kotlin.math.cos(theta)).roundToInt()
        val dy = (r * kotlin.math.sin(theta)).roundToInt()
        return Coordinate(center.x + dx, center.y + dy, center.z)
    }

    /**
     * Attempts to find a random coordinate within [radius] of [center] that satisfies [isReachable].
     * Returns null if no candidate passes within [maxAttempts].
     *
     * Provide your navigation check via [isReachable], e.g. `nav.canReach(coord)`.
     * When no validator is supplied, this returns the first random candidate (no reachability check).
     */
    fun randomReachableNear(
        center: Coordinate,
        radius: Int,
        maxAttempts: Int = 32,
        rng: Random = Random,
        isReachable: ((Coordinate) -> Boolean)? = null
    ): Coordinate? {
        repeat(maxAttempts.coerceAtLeast(1)) {
            val c = randomNear(center, radius, rng)
            if (isReachable?.invoke(c) != false) return c
        }
        return null
    }

    /**
     * Convenience overload centered on the local player. Returns null if player coordinate is unknown
     * or if no candidate passes within [maxAttempts].
     */
    fun randomReachableNearPlayer(
        radius: Int,
        maxAttempts: Int = 32,
        rng: Random = Random,
        isReachable: ((Coordinate) -> Boolean)? = null
    ): Coordinate? = player()?.let { randomReachableNear(it, radius, maxAttempts, rng, isReachable) }
}
