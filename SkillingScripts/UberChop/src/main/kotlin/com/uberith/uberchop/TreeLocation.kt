package com.uberith.uberchop

data class TreeLocation(
    val name: String,
    val availableTrees: List<String>,
    val chop: net.botwithus.rs3.world.Coordinate? = null,
    val bank: net.botwithus.rs3.world.Coordinate? = null
)

