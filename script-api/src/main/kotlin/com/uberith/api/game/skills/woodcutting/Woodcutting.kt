package com.uberith.api.game.skills.woodcutting

import com.uberith.api.script.SuspendableScript
import com.uberith.api.game.inventory.Backpack
import com.uberith.api.game.inventory.Bank
import com.uberith.api.game.items.BirdNests
import com.uberith.api.game.skills.firemaking.Firemaking
import com.uberith.api.game.skills.fletching.Fletching
import com.uberith.api.game.skills.woodcutting.internal.LogHandlingMode
import com.uberith.api.game.world.Traverse
import net.botwithus.rs3.item.InventoryItem
import net.botwithus.rs3.world.Coordinate
import org.slf4j.Logger
import java.util.regex.Pattern

/**
 * Stateless woodcutting helpers. Callers supply just the values needed per action.
 */
object Woodcutting {

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

    suspend fun prepare(
        script: SuspendableScript,
        logger: Logger,
        shouldWithdrawWoodBox: Boolean,
        bankTile: Coordinate?
    ): Boolean {
        logger.info(
            "[Woodcutting] prepare(): shouldWithdraw={}, hasWoodBox={}",
            shouldWithdrawWoodBox,
            Equipment.hasWoodBox()
        )
        if (!shouldWithdrawWoodBox) {
            logger.info("[Woodcutting] prepare(): wood box disabled -> no preparation needed")
            return false
        }
        if (Equipment.hasWoodBox()) {
            logger.info("[Woodcutting] prepare(): wood box already present -> no preparation needed")
            return false
        }
        script.awaitIdle()
        logger.info("[Woodcutting] prepare(): bankDestination={}", bankTile)
        val movingToBank = Traverse.ensureWithin(script, bankTile, 10)
        logger.info("[Woodcutting] prepare(): ensureWithin -> {}", movingToBank)
        if (movingToBank) {
            logger.info("[Woodcutting] prepare(): issued travel towards bank")
            return true
        }
        val openedBank = openBankIfNeeded(script, logger)
        logger.info("[Woodcutting] prepare(): openBankIfNeeded -> {}", openedBank)
        if (openedBank) {
            logger.info("[Woodcutting] prepare(): bank opened, waiting next tick")
            return true
        }
        val backpackFull = Backpack.isFull()
        logger.info("[Woodcutting] prepare(): backpackFull={}", backpackFull)
        if (backpackFull) {
            val deposited = Bank.depositAll()
            logger.info("[Woodcutting] prepare(): Bank.depositAll() -> {}", deposited)
        }
        val withdrew = Equipment.ensureWoodBox(script)
        logger.info("[Woodcutting] prepare(): ensureWoodBox -> {}", withdrew)
        if (withdrew) {
            logger.info("[Woodcutting] prepare(): acquired wood box from bank")
            return true
        }
        logger.info("[Woodcutting] prepare(): no actions this tick; will re-evaluate next loop")
        return false
    }

    suspend fun bankInventory(
        script: SuspendableScript,
        logger: Logger,
        shouldWithdrawWoodBox: Boolean,
        bankTile: Coordinate?
    ): BankOutcome {
        script.awaitIdle()
        val initialBackpackFull = Backpack.isFull()
        logger.info(
            "[Woodcutting] bankInventory(): shouldWithdraw={}, bankDestination={}, backpackFull={}",
            shouldWithdrawWoodBox,
            bankTile,
            initialBackpackFull
        )
        if (Traverse.ensureWithin(script, bankTile, 10)) {
            logger.info("[Woodcutting] bankInventory(): travelling towards bank")
            return BankOutcome.WAITED
        }
        if (openBankIfNeeded(script, logger)) {
            logger.info("[Woodcutting] bankInventory(): opened bank interface")
            return BankOutcome.WAITED
        }
        if (shouldWithdrawWoodBox && Equipment.ensureWoodBox(script)) {
            logger.info("[Woodcutting] bankInventory(): retrieved wood box before banking inventory")
            return BankOutcome.WAITED
        }
        if (!Backpack.isFull()) {
            logger.info("[Woodcutting] bankInventory(): backpack ready -> transitioning to chopping")
            return BankOutcome.INVENTORY_READY
        }
        if (shouldWithdrawWoodBox) {
            val emptied = Equipment.emptyWoodBox(script)
            if (emptied) {
                if (!Backpack.isFull()) {
                    logger.info("[Woodcutting] bankInventory(): emptied wood box and freed space")
                    return BankOutcome.WAITED
                }
                logger.info("[Woodcutting] bankInventory(): emptied wood box but backpack still full")
            }
        }
        logger.info("[Woodcutting] bankInventory(): depositing logs pattern={}", logsPattern)
        Bank.depositAll(script, logsPattern)
        script.awaitTicks(1)
        return BankOutcome.WAITED
    }

    suspend fun chop(
        script: SuspendableScript,
        logger: Logger,
        targetTree: String,
        shouldWithdrawWoodBox: Boolean,
        pickupBirdNests: Boolean,
        logHandling: LogHandlingMode,
        chopTile: Coordinate?
    ): ChopOutcome {
        if (Backpack.isFull()) {
            logger.info(
                "[Woodcutting] chop(): backpack full -> hasWoodBox={}, shouldWithdraw={}",
                Equipment.hasWoodBox(),
                shouldWithdrawWoodBox
            )
            var attemptedWoodBoxFill = false
            if (Equipment.hasWoodBox()) {
                attemptedWoodBoxFill = true
                val filled = Equipment.fillWoodBox(script)
                if (filled && !Backpack.isFull()) {
                    logger.info("Filled wood box to free backpack space")
                    return ChopOutcome.WAITED
                }
            }
            if (!attemptedWoodBoxFill && shouldWithdrawWoodBox) {
                attemptedWoodBoxFill = true
                val filled = Equipment.fillWoodBox(script)
                if (filled && !Backpack.isFull()) {
                    logger.info("Filled wood box to free backpack space")
                    return ChopOutcome.WAITED
                }
            }
            when (logHandling) {
                LogHandlingMode.DROP -> {
                    if (dropLogs(script, logger)) {
                        return ChopOutcome.WAITED
                    }
                }
                LogHandlingMode.BURN -> {
                    if (burnLogs(script, logger)) {
                        return ChopOutcome.WAITED
                    }
                }
                LogHandlingMode.FLETCH -> {
                    if (fletchLogs(script, logger)) {
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
        logger.info("[Woodcutting] chop(): proceeding to chop '{}'", targetTree)

        if (pickupBirdNests) {
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

        if (Traverse.ensureWithin(script, chopTile, 40)) {
            logger.info("[Woodcutting] chop(): travelling towards chop destination {}", chopTile)
            return ChopOutcome.WAITED
        }

        if (Trees.chop(script, targetTree)) {
            logger.info("Chop target: '{}'", targetTree)
            return ChopOutcome.STARTED
        }

        logger.debug("[Woodcutting] chop(): no target '{}' nearby", targetTree)
        return ChopOutcome.NONE
    }

    private suspend fun dropLogs(script: SuspendableScript, logger: Logger): Boolean {
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

    private suspend fun burnLogs(script: SuspendableScript, logger: Logger): Boolean {
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

    private suspend fun fletchLogs(script: SuspendableScript, logger: Logger): Boolean {
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

    private suspend fun openBankIfNeeded(script: SuspendableScript, logger: Logger): Boolean {
        if (Bank.isOpen()) return false
        val opened = Bank.open(script)
        logger.info("Bank.open() -> {}", opened)
        if (opened) {
            script.awaitTicks(1)
        }
        return opened
    }
}
