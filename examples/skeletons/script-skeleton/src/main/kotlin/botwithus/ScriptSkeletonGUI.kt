package botwithus

import net.botwithus.imgui.ImGui
import net.botwithus.ui.workspace.Workspace

class ScriptSkeletonGUI(private val script: ScriptSkeleton) {

    fun render(workspace: Workspace) {
        ImGui.begin("Script Skeleton", 0)

        ImGui.text("This is a sample GUI for the ScriptSkeleton.")
        ImGui.separator()

        if (ImGui.button("Click Me!", 0.0f, 0.0f)) {
            script.println("Button clicked!")
        }

        ImGui.end()
    }
}

