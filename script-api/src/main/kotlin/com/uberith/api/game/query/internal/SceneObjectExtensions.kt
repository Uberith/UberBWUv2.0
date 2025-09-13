package com.uberith.api.game.query.internal

import net.botwithus.rs3.entities.SceneObject
import net.botwithus.rs3.world.Distance

/**
 * Extension helpers for working with SceneObject collections.
 */
fun List<SceneObject>.nearest(): SceneObject? =
    this.minByOrNull { so -> Distance.to(so) }

fun Sequence<SceneObject>.nearest(): SceneObject? =
    this.minByOrNull { so -> Distance.to(so) }
