package com.uberith.skilling

import net.botwithus.imgui.ImGui
import net.botwithus.ui.workspace.Workspace

class UberChopGUI(private val script: UberChop) {

    fun render(workspace: Workspace) {
        if (ImGui.begin("UberChop", 0)) {
            ImGui.text("UberChop - Woodcutting")
            ImGui.separator()

            // Config
            var nameBuffer = script.targetTree
            nameBuffer = ImGui.inputTextWithHint("Tree", "Tree/Oak/Willow/Yew/Magic", nameBuffer, 0)
            script.targetTree = if (nameBuffer.isBlank()) "Tree" else nameBuffer

            var bank = script.bankWhenFull
            bank = ImGui.checkbox("Bank When Full", bank)
            script.bankWhenFull = bank

            ImGui.separator()
            ImGui.text("Status: ${script.status}")
            ImGui.text("Logs: ${script.logsChopped}")
        }
        ImGui.end()
    }
}

