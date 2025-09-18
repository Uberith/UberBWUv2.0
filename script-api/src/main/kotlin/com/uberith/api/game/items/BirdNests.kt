package com.uberith.api.game.items

import net.botwithus.rs3.item.GroundItem
import java.util.regex.Pattern

/**
 * Helpers for locating bird's nest ground items.
 */
object BirdNests {

    /** Regex that matches any variant of "Bird's nest" seen on the ground. */
    val NAME_PATTERN: Pattern = Pattern.compile("Bird's nest(.*)?", Pattern.CASE_INSENSITIVE)

    /** Builds a ground-item query constrained to bird's nests. */
    fun query(): GroundItemQuery = GroundItemQuery.newQuery().name(NAME_PATTERN)

    /** Executes the bird's nest query and returns the matching ground items. */
    fun groundItems(): ResultSet<GroundItem> {
        val raw = query().results()
        val list = raw.stream().toList()
        return ResultSet(list)
    }

    /** Returns the nearest bird's nest ground item, or null when none are visible. */
    fun nearest(): GroundItem? = groundItems().nearest()
}

