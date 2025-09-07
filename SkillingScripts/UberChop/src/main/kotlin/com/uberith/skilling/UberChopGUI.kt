package com.uberith.skilling

import net.botwithus.rs3.imgui.ImGui
import net.botwithus.rs3.imgui.ImGuiWindowFlag
import net.botwithus.ui.workspace.Workspace
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class UberChopGUI(private val script: UberChop) {

    // Lazy-loaded logo texture id via reflection when available
    private var logoTexId: Long? = null
    private var triedLoadLogo = false

    // Simple nav state
    private var selectedTab: String = "Settings"

    fun render(workspace: Workspace) {
        ImGui.SetNextWindowSize(800f, 600f, ImGuiWindowFlag.AlwaysAutoResize.value)
        if (ImGui.Begin("UberChop", ImGuiWindowFlag.AlwaysAutoResize.value)) {
            drawBanner()

            // Top controls
            if (ImGui.Button("Start")) script.requestStart()
            if (script.userPaused) {
                if (ImGui.Button("Resume")) script.resume()
            } else {
                if (ImGui.Button("Pause")) script.pause()
            }

            ImGui.Separator()

            // Left navigation
            ImGui.Text("Navigation")
            if (ImGui.Button("Settings")) selectedTab = "Settings"
            ImGui.SameLine()
            if (ImGui.Button("Statistics")) selectedTab = "Statistics"
            ImGui.SameLine()
            if (ImGui.Button("Advanced")) selectedTab = "Advanced"
            ImGui.SameLine()
            if (ImGui.Button("Debug")) selectedTab = "Debug"

            ImGui.Separator()

            when (selectedTab) {
                "Settings" -> drawSettingsTab()
                "Statistics" -> drawStatisticsTab()
                "Advanced" -> drawAdvancedTab()
                "Debug" -> drawDebugTab()
            }

            ImGui.Separator()
            ImGui.Text("Phase: ${script.phase}")
            ImGui.Text("Status: ${script.status}")
            ImGui.Text("Logs: ${script.logsChopped}")
        }
        ImGui.End()
    }

    private fun drawSettingsTab() {
        // Tree selection
        var nameBuffer = script.targetTree
        nameBuffer = ImGui.InputTextWithHint("Tree", "Tree/Oak/Willow/Yew/Magic", nameBuffer, 0)
        script.targetTree = if (nameBuffer.isBlank()) "Tree" else nameBuffer

        // Location selection (cycle through known locations)
        val locs = script.locationNames()
        var idx = script.selectedLocationIndex
        ImGui.Text("Location: ${locs.getOrNull(idx) ?: "Any"}")
        if (ImGui.Button("Prev Location")) {
            idx = if (idx <= 0) locs.size - 1 else idx - 1
            script.selectedLocationIndex = idx
        }
        ImGui.SameLine()
        if (ImGui.Button("Next Location")) {
            idx = if (idx >= locs.size - 1) 0 else idx + 1
            script.selectedLocationIndex = idx
        }

        // Banking option
        var bank = script.bankWhenFull
        bank = ImGui.Checkbox("Bank When Full", bank)
        script.bankWhenFull = bank
    }

    private fun drawStatisticsTab() {
        ImGui.Text("Runtime: ${script.formattedRuntime()}")
        ImGui.Text("Logs chopped: ${script.logsChopped}")
        ImGui.Text("Logs/hr: ${script.logsPerHour()}")
    }

    private fun drawAdvancedTab() {
        ImGui.Text("Advanced options coming soon")
        ImGui.Text("- Banking pathing (WIP)")
        ImGui.Text("- Tile navigation (WIP)")
    }

    private fun drawDebugTab() {
        val t = script.peekNearestTree()
        if (t != null) {
            ImGui.Text("Nearest: ${t.name}")
        } else {
            ImGui.Text("Nearest: none")
        }
    }

    private fun drawBanner() {
        if (!triedLoadLogo) {
            triedLoadLogo = true
            logoTexId = tryLoadLogoTex()
        }
        val tex = logoTexId
        if (tex != null && tex != 0L) {
            // Try reflectively calling ImGui.image(long, float, float)
            try {
                val m = ImGui::class.java.getMethod("Image", java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE)
                m.invoke(null, tex, 260f, 60f)
            } catch (_: Throwable) {
                // Fallback to title text if images unsupported
                ImGui.Text("UberChop")
            }
        } else {
            ImGui.Text("UberChop")
        }
        ImGui.Separator()
    }

    private fun tryLoadLogoTex(): Long? {
        return try {
            val stream = this::class.java.classLoader.getResourceAsStream("images/Uberith_Logo_Full_Text.png")
                ?: return null
            val img: BufferedImage = ImageIO.read(stream) ?: return null
            // Try both rs3 ImGui and generic ImGui for texture loading
            val clazzes = listOf(
                try { Class.forName("net.botwithus.rs3.imgui.ImGui") } catch (_: Throwable) { null },
                ImGui::class.java
            ).filterNotNull()
            for (c in clazzes) {
                try {
                    val load = c.getMethod("LoadTexture", BufferedImage::class.java)
                    val tex = load.invoke(null, img) as? Long
                    if (tex != null && tex != 0L) return tex
                } catch (_: Throwable) {
                    // try next
                }
            }
            null
        } catch (_: Throwable) {
            null
        }
    }
}
