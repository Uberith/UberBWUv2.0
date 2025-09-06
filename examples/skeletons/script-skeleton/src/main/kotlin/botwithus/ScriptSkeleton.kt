package botwithus

import net.botwithus.scripts.Info
import net.botwithus.scripts.Script
import net.botwithus.ui.workspace.Workspace
import java.lang.Thread.sleep


@Info(name = "ScriptSkeleton", description = "A skeleton script.", version = "1.0.0", author = "YourName")
class ScriptSkeleton : Script() {

    private val scriptGUI = ScriptSkeletonGUI(this)

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        scriptGUI.render(workspace)
    }

    override fun run() {
        println("Running main script logic...")
        // Implement loop or state machine as needed
        // Example: simple tick
        sleep(1000)
    }

    override fun onActivation() {
        super.onActivation()
        println("ScriptSkeleton activated.")
    }

    override fun onDeactivation() {
        super.onDeactivation()
        println("ScriptSkeleton deactivated.")
    }

    override fun onInitialize() {
        super.onInitialize()
    }
}

