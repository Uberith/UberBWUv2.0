package com.uberith

import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import com.uberith.api.SuspendableScript


@Info(name = "ScriptSkeleton", description = "A skeleton script.", version = "1.0.0", author = "YourName")
class ScriptSkeleton : SuspendableScript() {

    private val scriptGUI = ScriptSkeletonGUI(this)

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        scriptGUI.render(workspace)
    }

    override suspend fun onLoop() {
        // Implement loop or state machine as needed using wait()/waitUntil()
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
