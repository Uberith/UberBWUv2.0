package com.uberith.uberchop

import net.botwithus.kxapi.game.inventory.BackpackExtensions
import net.botwithus.kxapi.game.inventory.BankExtensions
import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.rs3.item.InventoryItem
import net.botwithus.xapi.game.inventory.Backpack
import net.botwithus.xapi.game.inventory.Bank
import org.slf4j.LoggerFactory
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

    suspend fun ensureWoodBox(
        script: SuspendableScript,
        pattern: Pattern = WOOD_BOX_PATTERN,
        withdraw: Boolean = true
    ): Boolean {
        if (hasWoodBox(pattern)) {
            logger.info("[Equipment] Wood box already present in backpack")
            return false
        }
        if (!withdraw) {
            logger.info("[Equipment] Withdraw disabled; skipping wood box retrieval")
            return false
        }
        val withdrew = Bank.withdraw(pattern, 1)
        logger.info("[Equipment] Bank.withdraw(pattern) -> {}", withdrew)
        if (withdrew) {
            script.awaitTicks(1)
        }
        return withdrew
    }

    suspend fun fillWoodBox(
        script: SuspendableScript,
        pattern: Pattern = WOOD_BOX_PATTERN,
        option: String = FILL_OPTION
    ): Boolean {
        val box = findWoodBox(pattern)
        if (box == null) {
            logger.info("[Equipment] No wood box found to fill")
            return false
        }
        val interacted = BackpackExtensions.interact(box, option)
        logger.info("[Equipment] Backpack.interact('{}') -> {}", option, interacted)
        if (interacted) {
            script.awaitTicks(1)
        }
        return interacted
    }

    suspend fun emptyWoodBox(
        script: SuspendableScript,
        pattern: Pattern = WOOD_BOX_PATTERN,
        option: String = "Empty - logs and bird's nests"
    ): Boolean {
        val box = findWoodBox(pattern)
        if (box == null) {
            logger.info("[Equipment] No wood box found to empty")
            return false
        }
        if (!Bank.isOpen()) {
            logger.info("[Equipment] Bank must be open to empty the wood box")
            return false
        }
        val emptied = BankExtensions.emptyBox(script, box.name, option)
        logger.info("[Equipment] Bank.emptyBox(option='{}') -> {}", option, emptied)
        return emptied
    }
}
