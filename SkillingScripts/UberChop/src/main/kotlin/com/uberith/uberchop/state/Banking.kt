package com.uberith.uberchop.state

import com.uberith.api.game.world.Coordinates
import com.uberith.uberchop.Equipment
import com.uberith.uberchop.UberChop
import net.botwithus.kxapi.game.inventory.Backpack
import net.botwithus.kxapi.game.inventory.Bank
import net.botwithus.xapi.game.traversal.Traverse
import net.botwithus.kxapi.permissive.dsl.BranchName
import net.botwithus.kxapi.permissive.dsl.LeafName
import net.botwithus.kxapi.permissive.dsl.StateBuilder

class Banking(
    script: UberChop,
    name: String = BotState.BANKING.description
) : UberChopState(script, name) {

    constructor(script: UberChop) : this(script, BotState.BANKING.description)

    override fun StateBuilder<UberChop>.create() {
        branch(BranchName("NeedsBanking"), condition = {
            val needsWoodBox = bot.shouldUseWoodBox && (
                !Equipment.hasWoodBox() ||
                    (bot.woodBoxWithdrawAttempted && !bot.woodBoxWithdrawSucceeded)
            )
            Backpack.contains(bot.logPattern) ||
                (bot.settings.pickupNests && Backpack.contains(bot.birdNestPattern)) ||
                needsWoodBox
        }) {
            onSuccess(BranchName("BankIsOpen"))
            onFailure(LeafName("SwitchToChopping"))
        }

        branch(BranchName("BankIsOpen"), condition = {
            Bank.isOpen()
        }) {
            onSuccess(BranchName("ShouldDepositLogs"))
            onFailure(BranchName("NearBank"))
        }

        branch(BranchName("NearBank"), condition = {
            val bankTile = bot.bankTile
            bankTile == null || Coordinates.isPlayerWithinRadius(bankTile, 5)
        }) {
            onSuccess(LeafName("OpenBank"))
            onFailure(LeafName("StepToBank"))
        }

        leaf(LeafName("StepToBank")) {
            val bankTile = bot.bankTile
            if (bankTile == null) {
                bot.warn("StepToBank: bank tile is not configured; skipping navigation")
                return@leaf
            }

            if (Coordinates.isPlayerWithinRadius(bankTile, 5)) {
                return@leaf
            }

            if (!bot.movementGate.compareAndSet(false, true)) {
                return@leaf
            }

            try {
                bot.updateStatus("Walking to bank")
                val traversed = runCatching { Traverse.to(bankTile) }
                    .onFailure { error -> bot.warn("StepToBank: traverse failed ${error.message}") }
                    .getOrDefault(false)
                if (!traversed) {
                    bot.debug("StepToBank: traversal request was not started")
                }
            } finally {
                bot.movementGate.set(false)
            }
        }

        // Attempt to open the nearest bank chest or booth.
        leaf(LeafName("OpenBank")) {
            val bankTile = bot.bankTile
            if (bankTile != null && !Coordinates.isPlayerWithinRadius(bankTile, 5)) {
                bot.debug("OpenBank: not within 5 tiles of bank; stepping closer")
                return@leaf
            }

            bot.updateStatus("Opening bank")
            runCatching { Bank.open(bot) }
                .onFailure { bot.warn("Bank open failed: ${it.message}") }
        }

        branch(BranchName("ShouldDepositLogs"), condition = {
            Backpack.contains(bot.logPattern)
        }) {
            onSuccess(LeafName("DepositLogs"))
            onFailure(BranchName("ShouldDepositNests"))
        }

        branch(BranchName("ShouldDepositNests"), condition = {
            bot.settings.pickupNests && Backpack.contains(bot.birdNestPattern)
        }) {
            onSuccess(LeafName("DepositBirdNests"))
            onFailure(BranchName("ShouldWithdrawWoodBox"))
        }

        branch(BranchName("ShouldWithdrawWoodBox"), condition = {
            bot.shouldUseWoodBox && !Equipment.hasWoodBox() && bot.canAttemptWoodBoxWithdraw()
        }) {
            onSuccess(LeafName("WithdrawWoodBox"))
            onFailure(BranchName("AwaitWoodBox"))
        }

        branch(BranchName("AwaitWoodBox"), condition = {
            bot.shouldUseWoodBox && !Equipment.hasWoodBox()
        }) {
            onSuccess(LeafName("WaitForWoodBox"))
            onFailure(LeafName("SwitchToChopping"))
        }

        // Dump any matching log items, then let chopping try again.
        leaf(LeafName("DepositLogs")) {
            if (!Backpack.contains(bot.logPattern)) {
                return@leaf
            }

            val emptiedWoodBox = Equipment.hasWoodBox() && Equipment.emptyWoodBox(bot)
            if (emptiedWoodBox) {
                bot.updateStatus("Emptying wood box")
                bot.delay(5)
            }

            bot.updateStatus("Depositing logs")

            val matchingItems = Backpack.getItems()
                .filter { item -> bot.logPattern.matcher(item.name).find() }
                .map { item -> "${item.name} (id=${item.id})" }
            val sample = matchingItems.take(5).joinToString()
            bot.debug(
                "DepositLogs: bankOpen=${Bank.isOpen()} matches=${matchingItems.size} sample=[$sample]"
            )

            var depositResult = runCatching { Bank.depositAll(bot, bot.logPattern) }
                .onFailure { error -> bot.warn("DepositLogs: depositAll(pattern) threw ${error.message}") }
                .getOrElse { false }

            var stillContainsLogs = Backpack.contains(bot.logPattern)
            bot.debug(
                "DepositLogs: depositAll(pattern) -> $depositResult; stillContains=$stillContainsLogs"
            )
            if (stillContainsLogs) {
                val fallbackWorked = bot.depositLogsFallback()
                if (fallbackWorked) {
                    bot.debug("DepositLogs: fallback backpack interactions triggered")
                    depositResult = true
                    bot.delay(5)
                }
                stillContainsLogs = Backpack.contains(bot.logPattern)
                if (stillContainsLogs) {
                    bot.warn("DepositLogs: backpack still has log-pattern items after all deposit attempts")
                }
            }

            val booleanValue = !Backpack.isFull()
            val callableBoolean: () -> Boolean = { booleanValue }
            bot.delayUntil(callableBoolean, 10)

            bot.chopWorkedLastTick = false
        }

        leaf(LeafName("DepositBirdNests")) {
            if (!bot.settings.pickupNests) {
                return@leaf
            }
            if (!Backpack.contains(bot.birdNestPattern)) {
                return@leaf
            }

            bot.updateStatus("Depositing bird nests")

            var nestsDeposited = runCatching { Bank.depositAll(bot, bot.birdNestPattern) }
                .onFailure { error -> bot.warn("DepositBirdNests: depositAll(bird nests) threw ${error.message}") }
                .getOrElse { false }
            var stillHasNests = Backpack.contains(bot.birdNestPattern)
            bot.debug(
                "DepositBirdNests: depositAll -> $nestsDeposited; stillContains=$stillHasNests"
            )
            bot.delay(1)
            if (stillHasNests) {
                var fallbackAttempts = 0
                while (stillHasNests && fallbackAttempts < 5) {
                    val fallbackWorked = bot.depositItemsFallback(bot.birdNestPattern)
                    if (!fallbackWorked) {
                        bot.debug("DepositBirdNests: fallback attempt ${fallbackAttempts + 1} made no progress")
                        break
                    }
                    nestsDeposited = true
                    fallbackAttempts++
                    stillHasNests = Backpack.contains(bot.birdNestPattern)
                    bot.debug(
                        "DepositBirdNests: fallback attempt $fallbackAttempts stillContains=$stillHasNests"
                    )
                    bot.delay(1)
                }
                if (stillHasNests) {
                    bot.warn("DepositBirdNests: backpack still has bird nests after all deposit attempts")
                }
            }

            val booleanValue = !Backpack.isFull()
            val callableBoolean: () -> Boolean = { booleanValue }
            bot.delayUntil(callableBoolean, 10)

            bot.chopWorkedLastTick = false
        }

        leaf(LeafName("WithdrawWoodBox")) {
            if (!bot.shouldUseWoodBox) {
                return@leaf
            }

            bot.woodBoxWithdrawAttempted = false
            bot.woodBoxWithdrawSucceeded = false

            if (Equipment.hasWoodBox()) {
                bot.recordWoodBoxWithdraw(true)
                return@leaf
            }
            if (Backpack.isFull()) {
                bot.warn("WithdrawWoodBox: backpack is full; cannot withdraw wood box")
                bot.recordWoodBoxWithdraw(false)
                return@leaf
            }

            bot.updateStatus("Withdrawing wood box")

            val withdrew = runCatching { Bank.withdraw(bot.woodBoxPattern, 1) }
                .onFailure { error -> bot.warn("WithdrawWoodBox: withdraw threw ${error.message}") }
                .getOrElse { false }

            if (!withdrew) {
                bot.warn("WithdrawWoodBox: failed to withdraw wood box from bank")
                bot.recordWoodBoxWithdraw(false)
            } else {
                bot.recordWoodBoxWithdraw(true)
                bot.delay(1)
            }

            bot.chopWorkedLastTick = false
        }

        leaf(LeafName("WaitForWoodBox")) {
            bot.updateStatus("Waiting for wood box availability")
            bot.delay(1)
        }

        // Fall back to chopping when there is nothing left to bank.
        leaf(LeafName("SwitchToChopping")) {
            if (bot.shouldUseWoodBox && !Equipment.hasWoodBox()) {
                bot.debug("SwitchToChopping: still waiting for wood box")
                return@leaf
            }

            if (Bank.isOpen()) {
                bot.updateStatus("Closing bank")
                runCatching { Bank.close() }
                    .onFailure { error -> bot.warn("SwitchToChopping: Bank.close() threw ${error.message}") }
            }
            bot.woodBoxWithdrawAttempted = false
            bot.woodBoxWithdrawSucceeded = false
            bot.switchState(BotState.CHOPPING, "Backpack clear")
            bot.chopWorkedLastTick = false
        }

        root(BranchName("NeedsBanking"))
    }

}

