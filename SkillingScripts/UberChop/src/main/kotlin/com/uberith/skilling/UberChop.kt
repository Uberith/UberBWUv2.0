package com.uberith.skilling

import com.uberith.api.SuspendableScript
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.rs3.world.World
import net.botwithus.rs3.entities.SceneObject
import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.inventories.InventoryManager
import java.util.Locale
import java.util.regex.Pattern

@Info(
    name = "UberChop",
    description = "Smart RS3 woodcutting: targets trees, chops, optional banking.",
    version = "0.1.0",
    author = "Uberith"
)
class UberChop : SuspendableScript() {

    // Configurable via GUI
    var targetTree: String = "Tree" // e.g., Tree, Oak, Willow, Yew, Magic
    var bankWhenFull: Boolean = false

    // Runtime stats
    var logsChopped: Int = 0
    var status: String = "Idle"

    private val gui = UberChopGUI(this)

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        gui.render(workspace)
    }

    override fun onActivation() {
        super.onActivation()
        println("UberChop activated")
        status = "Active"
    }

    override fun onDeactivation() {
        super.onDeactivation()
        println("UberChop deactivated")
        status = "Inactive"
    }

    override suspend fun onLoop() {
        // 1) Optional: bank if full (stub)
        if (bankWhenFull && isAnyInventoryFull()) {
            status = "Inventory full — banking (stub)"
            // TODO: Implement banking via interfaces/world interactions
            awaitTicks(2)
            return
        }

        // 2) Find nearest target tree by name
        val tree = nearestTree(targetTree)
        if (tree == null) {
            status = "No ${targetTree} nearby"
            awaitTicks(2)
            return
        }

        // 3) Interact with a "Chop" option (explicitly choose option index)
        status = "Chopping ${tree.name}"
        val options = tree.getOptions()
        val idx = options.indexOfFirst { it != null && it.lowercase(Locale.ROOT).contains("chop") }
        val interacted = if (idx >= 0) tree.interact(idx) else false
        if (interacted == false) {
            status = "Failed to interact; retrying"
            awaitTicks(2)
            return
        }

        // 4) Wait briefly; in future wait until animation or log gain
        awaitTicks(3)

        // TODO: Hook inventory events to increment accurately
        // For now just increment optimistically as a placeholder
        logsChopped++
        status = "Chopped (${logsChopped})"
        awaitTicks(1)
    }

    private fun nearestTree(name: String): SceneObject? {
        val player = LocalPlayer.self() ?: return null
        val nLower = name.lowercase(Locale.ROOT)
        return World.getSceneObjects()
            .asSequence()
            .filter { (it.isHidden() == false) && it.name.lowercase(Locale.ROOT).contains(nLower) }
            .minByOrNull { player.distanceTo(it.coordinate) }
    }

    private fun isAnyInventoryFull(): Boolean {
        return InventoryManager.getInventories().any { inv ->
            try {
                inv.isFull()
            } catch (_: Throwable) {
                false
            }
        }
    }
}
