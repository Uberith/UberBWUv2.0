package com.uberith.uberchop

import net.botwithus.kxapi.game.skilling.impl.woodcutting.TreeType
import net.botwithus.rs3.world.Coordinate

/**
 * Associates a named woodcutting spot with the tree types available there and optional default tiles.
 */
data class TreeLocation(
    val name: String,
    val availableTrees: List<TreeType>,
    val chop: Coordinate? = null,
    val bank: Coordinate? = null
)
