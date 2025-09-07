package com.uberith

import com.uberith.api.SuspendableScript
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace

@Info(
    name = "SuspendableTemplate",
    description = "Template script showcasing SuspendableScript",
    version = "1.0.0",
    author = "Template"
)
class SuspendableTemplate : SuspendableScript() {

    override fun onDraw(workspace: Workspace) {
        // Draw minimal info or keep empty
    }

    override suspend fun onLoop() {
        // Put your logic here; use awaitTicks()/awaitUntil()
        // Example: wait until a condition or timeout
        awaitUntil(timeout = 5) { false }
    }
}
