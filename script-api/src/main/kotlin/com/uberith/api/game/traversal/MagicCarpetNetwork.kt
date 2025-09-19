package com.uberith.api.game.traversal

import net.botwithus.kxapi.game.query.ComponentQuery
import net.botwithus.kxapi.game.query.NpcQuery


/**
 * Helpers for opening and interacting with the Magic Carpet travel interface.
 * Mirrors behavior from external XAPI, adapted for Kotlin.
 */
object MagicCarpetNetwork {

    /** Returns true if the Magic Carpet interface appears to be open. */
    fun isOpen(): Boolean = try { ComponentQuery.newQuery(1928).results().first() != null } catch (_: Throwable) { false }

    /**
     * Attempts to open the Magic Carpet travel UI by talking to a Rug merchant.
     *
     * @return true if interaction was initiated, false otherwise.
     */
    fun open(): Boolean {
        val npc = try {
            NpcQuery.newQuery()
                .name("Rug merchant")
                .option("Travel")
                .results()
                .first()
        } catch (_: Throwable) { null }
        return try { npc != null && npc.interact("Travel") > 0 } catch (_: Throwable) { false }
    }
}
