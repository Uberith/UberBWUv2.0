package botwithus

import net.botwithus.scripts.Info
import net.botwithus.scripts.Script
import net.botwithus.ui.workspace.Workspace

@Info(name = "ChickenKiller", description = "Simple combat example.", version = "1.0.0", author = "YourName")
class ChickenKiller : Script() {

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        // You could draw basic stats/UI here if desired
    }

    override fun run() {
        println("ChickenKiller running...")
        // Minimal placeholder loop – integrate API calls as needed
        // e.g., find NPCs, attack, loot, bank, etc.
        Thread.sleep(1000)
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
