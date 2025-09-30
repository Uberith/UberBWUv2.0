package com.uberith.uberchop

import net.botwithus.kxapi.game.inventory.Bank
import net.botwithus.rs3.item.InventoryItem
import net.botwithus.kxapi.game.inventory.Backpack
import org.slf4j.LoggerFactory
import kotlinx.coroutines.runBlocking
import java.util.regex.Pattern

/**
 * Wood box helpers scoped to the UberChop script now that the shared API version was removed.
 */
object Equipment {

    private val logger = LoggerFactory.getLogger(Equipment::class.java)

    val WOOD_BOX_PATTERN: Pattern = Pattern.compile(".*wood box.*", Pattern.CASE_INSENSITIVE)
    private const val FILL_OPTION = "Fill"

    fun hasWoodBox(pattern: Pattern = WOOD_BOX_PATTERN): Boolean = Backpack.contains(pattern)

    fun findWoodBox(pattern: Pattern = WOOD_BOX_PATTERN): InventoryItem? =
        Backpack.getItems().firstOrNull { item -> pattern.matcher(item.name).matches() }

    fun ensureWoodBox(
        script: UberChop,
        pattern: Pattern = WOOD_BOX_PATTERN,
        withdraw: Boolean = true
    ): Boolean {
        if (hasWoodBox(pattern)) {
            logger.debug("[Equipment] Wood box already present in backpack")
            return false
        }
        if (!withdraw) {
            logger.debug("[Equipment] Withdraw disabled; skipping wood box retrieval")
            return false
        }
        val withdrew = Bank.withdraw(pattern, 1)
        logger.debug("[Equipment] Bank.withdraw(pattern) -> {}", withdrew)
        if (withdrew) {
        }
        return withdrew
    }

    fun fillWoodBox(
        script: UberChop,
        pattern: Pattern = WOOD_BOX_PATTERN,
        option: String = FILL_OPTION
    ): Boolean {
        val box = findWoodBox(pattern)
        if (box == null) {
            logger.debug("[Equipment] No wood box found to fill")
            return false
        }
        val interacted = Backpack.interact(box, option)
        logger.debug("[Equipment] Backpack.interact('{}') -> {}", option, interacted)
        if (interacted) {
        }
        return interacted
    }

    fun emptyWoodBox(
        script: UberChop,
        pattern: Pattern = WOOD_BOX_PATTERN,
        option: String = "Empty - logs and bird's nests"
    ): Boolean {
        val box = findWoodBox(pattern)
        if (box == null) {
            logger.debug("[Equipment] No wood box found to empty")
            return false
        }
        if (!Bank.isOpen()) {
            logger.debug("[Equipment] Bank must be open to empty the wood box")
            return false
        }
        val interacted = Backpack.interact(box, option)
        logger.debug("[Equipment] Backpack.interact('{}') -> {}", option, interacted)
        if (interacted) {
            script.info("Emptying wood box contents into bank")
        }
        return interacted
    }
}

