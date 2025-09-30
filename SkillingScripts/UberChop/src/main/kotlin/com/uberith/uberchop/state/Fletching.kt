package com.uberith.uberchop.state

import com.uberith.uberchop.UberChop
import net.botwithus.kxapi.game.inventory.Backpack
import net.botwithus.kxapi.permissive.dsl.BranchName
import net.botwithus.kxapi.permissive.dsl.LeafName
import net.botwithus.kxapi.permissive.dsl.StateBuilder

class Fletching(
    script: UberChop,
    name: String = BotState.FLETCHING.description
) : UberChopState(script, name) {

    constructor(script: UberChop) : this(script, BotState.FLETCHING.description)

    override fun StateBuilder<UberChop>.create() {
        branch(BranchName("ReadyToFletch"), condition = {
            bot.logHandlingPreference == UberChop.LogHandling.FLETCH &&
                Backpack.isFull() &&
                bot.hasFletchableLogs()
        }) {
            onSuccess(BranchName("HasLogs"))
            onFailure(LeafName("ReturnToChopping"))
        }

        branch(BranchName("HasLogs"), condition = {
            Backpack.contains(bot.logPattern)
        }) {
            onSuccess(LeafName("FletchLogs"))
            onFailure(LeafName("ReturnToChopping"))
        }

        leaf(LeafName("FletchLogs")) {
            val started = bot.attemptFletchLogs()
            if (!started) {
                bot.delay(1)
            }
        }

        leaf(LeafName("ReturnToChopping")) {
            if (Backpack.contains(bot.logPattern) && Backpack.isFull() &&
                bot.logHandlingPreference == UberChop.LogHandling.FLETCH
            ) {
                bot.delay(1)
                return@leaf
            }
            bot.switchState(BotState.CHOPPING, "Backpack clear after fletching")
        }

        root(BranchName("ReadyToFletch"))
    }
}
