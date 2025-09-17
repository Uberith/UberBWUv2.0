package com.uberith.api.game.skills.woodcutting

import com.uberith.api.SuspendableScript
import com.uberith.api.game.inventory.Backpack
import com.uberith.api.game.inventory.Bank
import com.uberith.api.game.items.BirdNests
import com.uberith.api.game.skills.woodcutting.internal.LogHandlingMode
import com.uberith.api.game.skills.firemaking.Firemaking
import com.uberith.api.game.skills.fletching.Fletching
import com.uberith.api.game.world.Traverse
import net.botwithus.rs3.item.InventoryItem
import net.botwithus.rs3.world.Coordinate
import org.slf4j.Logger
import java.util.regex.Pattern

class Woodcutting(
    private val script: SuspendableScript,
    private val logger: Logger,
    private val withdrawWoodBox: () -> Boolean,
    private val pickupBirdNests: () -> Boolean,
    private val logHandling: () -> LogHandlingMode,
    private val chopTile: () -> Coordinate?,
    private val bankTile: () -> Coordinate?
) {

    private val logsPattern: Pattern = Pattern.compile(".*logs.*", Pattern.CASE_INSENSITIVE)
    private val logBurnOptions = arrayOf("Light", "Burn")
    private val logFletchOptions = arrayOf("Craft", "Fletch", "Whittle")
    private val productionOptionKeywords = arrayOf("Make All", "Make all", "Start", "Make", "Craft")
    private val productionInterfaceChunks: List<IntArray> = listOf(
        intArrayOf(1370, 1371, 1372, 1251, 1252, 1140, 1141, 1469),
        IntArray(201) { 900 + it },
        IntArray(201) { 1200 + it }
    )

    enum class ChopOutcome { NONE, WAITED, STARTED, NEEDS_BANK }
    enum class BankOutcome { WAITED, INVENTORY_READY }

    suspend fun prepare(): Boolean {
        if (!withdrawWoodBox()) return false
        if (Equipment.hasWoodBox()) return false
        script.awaitIdle()
        val bankDestination = bankTile()
        if (Traverse.ensureWithin(script, bankDestination, 10)) return true
        if (openBankIfNeeded()) return true
        if (Backpack.isFull()) Bank.depositAll()
        if (Equipment.ensureWoodBox(script)) return true
        return false
    }

    suspend fun bankInventory(): BankOutcome {
        script.awaitIdle()
        if (Traverse.ensureWithin(script, bankTile(), 10)) return BankOutcome.WAITED
        if (openBankIfNeeded()) return BankOutcome.WAITED
        if (withdrawWoodBox() && Equipment.ensureWoodBox(script)) return BankOutcome.WAITED

        if (!Backpack.isFull()) {
            return BankOutcome.INVENTORY_READY
        }

        if (withdrawWoodBox()) {
            val emptied = Equipment.emptyWoodBox(script)
            if (emptied) {
                if (!Backpack.isFull()) {
                    return BankOutcome.WAITED
                }
                logger.info("Wood box empty did not free backpack space; depositing logs")
            }
        }

        logger.info("Depositing logs {}", logsPattern)
        Bank.depositAll(script, logsPattern)
        script.awaitTicks(1)
        return BankOutcome.WAITED
    }

    suspend fun chop(targetTree: String): ChopOutcome {
        if (Backpack.isFull()) {
            var attemptedWoodBoxFill = false
            if (Equipment.hasWoodBox()) {
                attemptedWoodBoxFill = true
                val filled = Equipment.fillWoodBox(script)
                if (filled && !Backpack.isFull()) {
                    logger.info("Filled wood box to free backpack space")
                    return ChopOutcome.WAITED
                }
            }
            if (!attemptedWoodBoxFill && withdrawWoodBox()) {
                attemptedWoodBoxFill = true
                val filled = Equipment.fillWoodBox(script)
                if (filled && !Backpack.isFull()) {
                    logger.info("Filled wood box to free backpack space")
                    return ChopOutcome.WAITED
                }
            }
            when (logHandling()) {
                LogHandlingMode.DROP -> {
                    if (dropLogs()) {
                        return ChopOutcome.WAITED
                    }
                }
                LogHandlingMode.BURN -> {
                    if (burnLogs()) {
                        return ChopOutcome.WAITED
                    }
                }
                LogHandlingMode.FLETCH -> {
                    if (fletchLogs()) {
                        return ChopOutcome.WAITED
                    }
                }
                LogHandlingMode.BANK -> {}
            }

            if (attemptedWoodBoxFill) {
                logger.info("Backpack remains full after attempting to fill wood box; banking")
            }
            return ChopOutcome.NEEDS_BANK
        }

        script.awaitIdle()

        if (pickupBirdNests()) {
            val nest = BirdNests.nearest()
            if (nest != null) {
                val result = nest.interact("Take")
                if (result > 0) {
                    logger.info("Picked up bird's nest from ground")
                    script.awaitTicks(2)
                    return ChopOutcome.WAITED
                }
            }
        }

        if (Traverse.ensureWithin(script, chopTile(), 40)) {
            return ChopOutcome.WAITED
        }

        if (Trees.chop(script, targetTree)) {
            logger.info("Chop target: '{}'", targetTree)
            return ChopOutcome.STARTED
        }

        logger.debug("No target '{}' nearby", targetTree)
        return ChopOutcome.NONE
    }


    private suspend fun dropLogs(): Boolean {
        val items = Backpack.getItems().filter { logsPattern.matcher(it.name).find() }
        var dropped = false
        for (item in items) {
            if (Backpack.interact(item, "Drop")) {
                dropped = true
                script.awaitTicks(1)
            }
        }
        if (dropped) {
            logger.info("Dropped logs to free inventory space")
        }
        return dropped
    }


    private suspend fun burnLogs(): Boolean {
        val result = Firemaking.burnFirstLog(
            logger = logger,
            logs = ::logItems,
            currentLogCount = ::currentLogCount,
            interactWithLog = { item ->
                logBurnOptions.any { option -> Backpack.interact(item, option) }
            },
            awaitTicks = { ticks -> script.awaitTicks(ticks) }
        )
        return result == Firemaking.Result.SUCCESS
    }

    private suspend fun fletchLogs(): Boolean {
        val result = Fletching.fletchFirstLog(
            logger = logger,
            logs = ::logItems,
            currentLogCount = ::currentLogCount,
            logFletchOptions = logFletchOptions,
            productionOptionKeywords = productionOptionKeywords,
            productionInterfaceChunks = productionInterfaceChunks,
            interactWithLog = { item, option -> Backpack.interact(item, option) },
            awaitTicks = { ticks -> script.awaitTicks(ticks) }
        )
        return result.consumed
    }

    private fun logItems(): List<InventoryItem> {
        val inventory = Backpack.getInventory() ?: return emptyList()
        return inventory.items.filter { item ->
            item.name.isNotEmpty() && logsPattern.matcher(item.name).find()
        }
    }

    private fun currentLogCount(): Int {
        val inventory = Backpack.getInventory() ?: return 0
        return inventory.items.count { item ->
            item.name.isNotEmpty() && logsPattern.matcher(item.name).find()
        }
    }

    private suspend fun openBankIfNeeded(): Boolean {
        if (Bank.isOpen()) return false
        val opened = Bank.open(script)
        logger.info("Bank.open() -> {}", opened)
        script.awaitTicks(1)
        return opened
    }
}
