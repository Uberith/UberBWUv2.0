package com.uberith.uberchop.state

import com.uberith.uberchop.Equipment
import com.uberith.uberchop.UberChop
import net.botwithus.kxapi.game.inventory.Backpack
import net.botwithus.kxapi.game.inventory.Bank
import net.botwithus.kxapi.permissive.dsl.BranchName
import net.botwithus.kxapi.permissive.dsl.LeafName
import net.botwithus.kxapi.permissive.dsl.StateBuilder

class Banking(
    script: UberChop,
    name: String = BotState.BANKING.description
) : UberChopState(script, name) {

    constructor(script: UberChop) : this(script, BotState.BANKING.description)

    override fun StateBuilder<UberChop>.create() {
        // Only enter the banking tree when logs are actually present.
        branch(BranchName("BackpackHasLogs"), condition = {
            Backpack.contains(bot.logPattern)
        }) {
            onSuccess(BranchName("BankIsOpen"))
            onFailure(LeafName("SwitchToChopping"))
        }

        // Keep retrying the open interaction until the client reports the bank is ready.
        branch(BranchName("BankIsOpen"), condition = {
            Bank.isOpen()
        }) {
            onSuccess(LeafName("DepositLogs"))
            onFailure(LeafName("OpenBank"))
        }

        // Attempt to open the nearest bank chest or booth.
        leaf(LeafName("OpenBank")) {
            bot.updateStatus("Opening bank")
            runCatching { Bank.open(bot) }
                .onFailure { bot.warn("Bank open failed: ${it.message}") }
        }

        // Dump any matching log items, then let chopping try again.
        leaf(LeafName("DepositLogs")) {
            if (!Backpack.contains(bot.logPattern)) {
                bot.switchState(BotState.CHOPPING, "Nothing to bank")
                return@leaf
            }

            val emptiedWoodBox = Equipment.hasWoodBox() && Equipment.emptyWoodBox(bot)
            if (emptiedWoodBox) {
                bot.updateStatus("Emptying wood box")
                bot.delay(1)
            }

            if (bot.shouldUseWoodBox) {
                if (!Equipment.hasWoodBox() && bot.ensureWoodBoxInBackpack(
                        statusMessage = "Retrieving wood box",
                        warnMessage = "Unable to retrieve wood box during banking; continuing without box",
                        errorMessage = "Failed to retrieve wood box during banking"
                    )
                ) {
                    return@leaf
                }
            }

            bot.updateStatus("Depositing logs")

            val matchingItems = Backpack.getItems()
                .filter { item -> bot.logPattern.matcher(item.name).find() }
                .map { item -> "${item.name} (id=${item.id})" }
            val sample = matchingItems.take(5).joinToString()
            bot.info(
                "DepositLogs: bankOpen=${Bank.isOpen()} matches=${matchingItems.size} sample=[$sample]"
            )

            var depositResult = runCatching { Bank.depositAll(bot, bot.logPattern) }
                .onFailure { error -> bot.warn("DepositLogs: depositAll(pattern) threw ${error.message}") }
                .getOrElse { false }

            var stillContainsLogs = Backpack.contains(bot.logPattern)
            bot.info(
                "DepositLogs: depositAll(pattern) -> $depositResult; stillContains=$stillContainsLogs"
            )
            if (stillContainsLogs) {
                val fallbackWorked = bot.depositLogsFallback()
                if (fallbackWorked) {
                    bot.info("DepositLogs: fallback backpack interactions triggered")
                    depositResult = true
                    bot.delay(1)
                }
                stillContainsLogs = Backpack.contains(bot.logPattern)
                if (stillContainsLogs) {
                    bot.warn("DepositLogs: backpack still has log-pattern items after all deposit attempts")
                }
            }

            if (bot.settings.pickupNests && Backpack.contains(bot.birdNestPattern)) {
                val nestsDeposited = runCatching { Bank.depositAll(bot, bot.birdNestPattern) }
                    .onFailure { error -> bot.warn("DepositLogs: depositAll(bird nests) threw ${error.message}") }
                    .getOrElse { false }
                val stillHasNests = Backpack.contains(bot.birdNestPattern)
                bot.info(
                    "DepositLogs: depositAll(bird nests) -> $nestsDeposited; stillContains=$stillHasNests"
                )
                if (stillHasNests && nestsDeposited) {
                    bot.warn("DepositLogs: backpack still has bird nests after deposit attempt")
                }
            }

            bot.chopWorkedLastTick = false
        }

        // Fall back to chopping when there is nothing left to bank.
        leaf(LeafName("SwitchToChopping")) {
            bot.switchState(BotState.CHOPPING, "Backpack clear")
        }

        root(BranchName("BackpackHasLogs"))
    }
}

