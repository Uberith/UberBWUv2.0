package com.uberith.api

import com.uberith.api.game.query.result.ResultSet
import net.botwithus.rs3.world.Distance
import net.botwithus.rs3.world.Locatable

@Suppress("UNCHECKED_CAST")
fun <T> ResultSet<T>.nearest(): T? {
    var best: T? = null
    var bestDist = Double.POSITIVE_INFINITY
    for (e in this) {
        try {
            val loc = e as? Locatable ?: continue
            val d = Distance.to(loc)
            if (!d.isNaN() && d < bestDist) {
                bestDist = d
                best = e
            }
        } catch (_: Throwable) { }
    }
    return best
}
