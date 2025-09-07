package com.uberith

import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import com.uberith.api.SuspendableScript

@Info(name = "ChickenKiller", description = "Simple combat example.", version = "1.0.0", author = "YourName")
class ChickenKiller : SuspendableScript() {

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        // You could draw basic stats/UI here if desired
    }

    override suspend fun onLoop() {
        // TODO: integrate API calls: select NPCs, attack, loot, bank, etc.
        // Example: waitUntil(timeout = 10) { /* condition */ false }
    }

    override fun onActivation() {
        super.onActivation()
        println("ChickenKiller activated.")
    }

    override fun onDeactivation() {
        super.onDeactivation()
        println("ChickenKiller deactivated.")
    }
}
