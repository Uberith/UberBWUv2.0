package com.uberith.uberchop.state

import com.uberith.uberchop.Equipment
import com.uberith.api.game.world.Coordinates
import com.uberith.uberchop.UberChop
import net.botwithus.kxapi.game.inventory.Backpack
import net.botwithus.kxapi.game.skilling.impl.woodcutting.woodcutting
import net.botwithus.kxapi.game.skilling.skilling
import net.botwithus.kxapi.permissive.dsl.BranchName
import net.botwithus.kxapi.permissive.dsl.LeafName
import net.botwithus.kxapi.permissive.dsl.StateBuilder

class Chopping(
    script: UberChop,
    name: String = BotState.CHOPPING.description
) : UberChopState(script, name) {

    constructor(script: UberChop) : this(script, BotState.CHOPPING.description)

    override fun StateBuilder<UberChop>.create() {
        // Stop swinging when the pack is full so the banking tree can take over.
        branch(BranchName("BackpackIsFull"), condition = { Backpack.isFull() }) {
            onSuccess(LeafName("HandleFullBackpack"))
            onFailure(BranchName("TreeIsReachable"))
        }

        // Only attempt to chop when the target tile is within a short walking radius.
        branch(BranchName("TreeIsReachable"), condition = {
            if (bot.treeTile == null) {
                true
            } else {
                val distance = Coordinates.distanceToPlayer(bot.treeTile!!)
                distance == null || distance <= 12.0
            }
        }) {
            onSuccess(LeafName("StartChopping"))
            onFailure(LeafName("StepToTree"))
        }

        // Small placeholder move step; keep the UI updated while navigation logic is pending.
        leaf(LeafName("StepToTree")) {
            if (bot.treeTile == null) {
                bot.updateStatus("Waiting for target tile")
                return@leaf
            }
            if (!bot.movementGate.compareAndSet(false, true)) {
                return@leaf
            }
            try {
                bot.updateStatus("Moving to ${bot.targetTree}")
            } finally {
                bot.movementGate.set(false)
            }
        }

        // Swing at the tree again when we either just moved or a new action is needed.
        leaf(LeafName("StartChopping")) {
            val treeName = bot.targetTree.ifBlank { "Tree" }
            if (bot.chopWorkedLastTick) {
                bot.updateStatus("Chopping $treeName")
                return@leaf
            }
            bot.updateStatus("Chopping $treeName")
            val started = runCatching {
                bot.skilling.woodcutting.chop(treeName).nearest()
            }.onFailure {
                bot.warn("Could not start chopping $treeName: ${it.message}")
            }.getOrDefault(false)
            bot.chopWorkedLastTick = started
        }

        // Hand control to the banking state when the inventory is capped.
        leaf(LeafName("HandleFullBackpack")) {
            if (bot.shouldUseWoodBox && Equipment.hasWoodBox()) {
                bot.updateStatus("Filling wood box")
                val filled = runCatching { Equipment.fillWoodBox(bot) }
                    .onFailure { error -> bot.warn("HandleFullBackpack: fillWoodBox failed ${error.message}") }
                    .getOrDefault(false)

                if (filled) {
                    bot.chopWorkedLastTick = false
                    if (!Backpack.isFull()) {
                        bot.info("HandleFullBackpack: wood box filled and backpack freed space")
                        return@leaf
                    }
                    bot.warn("HandleFullBackpack: wood box filled but backpack remains full")
                }
            }

            bot.switchState(BotState.BANKING, "Backpack is full")
        }

        root(BranchName("BackpackIsFull"))
    }
}


