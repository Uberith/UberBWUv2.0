package botwithus

import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.drj.SuspendableScript

@Info(name = "FlaxPicker", description = "Simple flax picking example.", version = "1.0.0", author = "YourName")
class FlaxPicker : SuspendableScript() {

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
    }

    override suspend fun onLoop() {
        // TODO: add movement/banking/state logic via API
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
