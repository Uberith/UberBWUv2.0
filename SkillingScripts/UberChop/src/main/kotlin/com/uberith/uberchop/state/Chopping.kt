package com.uberith.uberchop.state

import com.uberith.uberchop.Equipment
import com.uberith.api.game.world.Coordinates
import com.uberith.uberchop.UberChop
import net.botwithus.rs3.entities.LocalPlayer
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
        branch(BranchName("NeedsWoodBox"), condition = {
            bot.shouldUseWoodBox && !Equipment.hasWoodBox()
        }) {
            onSuccess(LeafName("SwitchToBankForWoodBox"))
            onFailure(BranchName("BackpackIsFull"))
        }

        // Stop swinging when the pack is full so the banking tree can take over.
        branch(BranchName("BackpackIsFull"), condition = { Backpack.isFull() }) {
            onSuccess(LeafName("HandleFullBackpack"))
            onFailure(BranchName("NeedsJujuPotion"))
        }

        branch(BranchName("NeedsJujuPotion"), condition = {
            bot.shouldDrinkJujuPotion()
        }) {
            onSuccess(LeafName("DrinkJujuPotion"))
            onFailure(BranchName("ShouldPickupNest"))
        }

        branch(BranchName("ShouldPickupNest"), condition = {
            bot.shouldPickupBirdNest()
        }) {
            onSuccess(LeafName("PickupBirdNest"))
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
            onSuccess(BranchName("AlreadyChopping"))
            onFailure(LeafName("StepToTree"))
        }

        branch(BranchName("AlreadyChopping"), condition = {
            val currentAnim = LocalPlayer.self()?.animationId ?: -1
            currentAnim != -1 && bot.chopWorkedLastTick
        }) {
            onSuccess(LeafName("MaintainChopping"))
            onFailure(LeafName("StartChopping"))
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


        leaf(LeafName("DrinkJujuPotion")) {
            bot.updateStatus("Drinking juju potion")
            val drank = bot.drinkJujuPotion()
            if (!drank) {
                bot.debug("DrinkJujuPotion leaf: no usable juju potion found")
            }
            bot.chopWorkedLastTick = false
        }
        leaf(LeafName("PickupBirdNest")) {
            bot.updateStatus("Collecting bird's nest")
            val pickedUp = bot.pickupBirdNests()
            if (pickedUp) {
                bot.delay(1)
            } else {
                bot.debug("PickupBirdNest: no nests present on attempt")
            }
            bot.chopWorkedLastTick = false
        }

        leaf(LeafName("MaintainChopping")) {
            val treeName = bot.targetTree.ifBlank { "Tree" }
            bot.updateStatus("Chopping $treeName")
            bot.chopWorkedLastTick = true
        }

        // Swing at the tree again when we either just moved or a new action is needed.
        leaf(LeafName("StartChopping")) {
            val treeName = bot.targetTree.ifBlank { "Tree" }
            val player = LocalPlayer.self()
            if (player == null) {
                bot.warn("StartChopping: no local player instance available")
                return@leaf
            }
            if (player.isMoving) {
                bot.debug("StartChopping: player still moving; deferring new swing")
                return@leaf
            }
            bot.chopWorkedLastTick = false
            bot.updateStatus("Chopping $treeName")
            val started = runCatching {
                bot.skilling.woodcutting.chop(treeName).nearest()
            }.onFailure {
                bot.warn("Could not start chopping $treeName: ${it.message}")
            }.getOrDefault(false)
            bot.chopWorkedLastTick = started
            if (started) {
                bot.delay(1)
            }
        }

        leaf(LeafName("SwitchToBankForWoodBox")) {
            bot.updateStatus("Retrieving wood box")
            bot.switchState(BotState.BANKING, "Retrieve wood box")
        }

        // Hand control to the banking state when the inventory is capped.
        leaf(LeafName("HandleFullBackpack")) {
            var waitedAfterFill = false
            if (bot.shouldUseWoodBox && Equipment.hasWoodBox()) {
                bot.updateStatus("Filling wood box")
                val filled = runCatching { Equipment.fillWoodBox(bot) }
                    .onFailure { error -> bot.warn("HandleFullBackpack: fillWoodBox failed ${error.message}") }
                    .getOrDefault(false)

                if (filled) {
                    bot.chopWorkedLastTick = false
                    repeat(3) { attempt ->
                        bot.delay(1)
                        waitedAfterFill = true
                        if (!Backpack.isFull()) {
                            bot.debug("HandleFullBackpack: wood box fill freed space after ${attempt + 1} tick(s)")
                            return@leaf
                        }
                    }
                    bot.warn("HandleFullBackpack: wood box filled but backpack remains full after waits")
                }
            }

            if (!Backpack.isFull()) {
                bot.switchState(BotState.CHOPPING, "Backpack had space after wood box handling")
                return@leaf
            }

            if (!waitedAfterFill) {
                repeat(2) {
                    bot.delay(1)
                    if (!Backpack.isFull()) {
                        bot.switchState(BotState.CHOPPING, "Backpack space freed after wait")
                        return@leaf
                    }
                }
            }

            if (bot.logHandlingPreference == UberChop.LogHandling.FLETCH) {
                if (bot.hasFletchableLogs()) {
                    bot.switchState(BotState.FLETCHING, "Backpack is full")
                } else {
                    bot.warn("HandleFullBackpack: log handling set to fletch but no recipe was found")
                    bot.updateStatus("Fletching unavailable for current logs")
                    bot.delay(1)
                }
                return@leaf
            }

            bot.switchState(BotState.BANKING, "Backpack is full")
        }

        root(BranchName("NeedsWoodBox"))
    }
}

