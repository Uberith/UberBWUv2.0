package botwithus

import net.botwithus.scripts.Info
import net.botwithus.scripts.Script
import net.botwithus.ui.workspace.Workspace
import java.lang.Thread.sleep

@Info(name = "FlaxPicker", description = "Simple flax picking example.", version = "1.0.0", author = "YourName")
class FlaxPicker : Script() {

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
    }

    override fun run() {
        println("FlaxPicker running...")
        // Placeholder – add movement/banking/state logic via API
        sleep(1000)
    }

    override fun onActivation() {
        super.onActivation()
        println("FlaxPicker activated.")
    }

    override fun onDeactivation() {
        super.onDeactivation()
        println("FlaxPicker deactivated.")
    }
}

