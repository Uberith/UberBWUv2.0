package com.uberith.skilling

import net.botwithus.imgui.ImGui
import net.botwithus.ui.workspace.Workspace
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class UberChopGUI(private val script: UberChop) {

    // Logo support: detect image API once; if absent, skip images quietly
    private var logoTexId: Long? = null
    private var imageApiChecked = false
    private var imageLoadMethod: java.lang.reflect.Method? = null
    private var imageDrawMethod: java.lang.reflect.Method? = null

    // Simple nav state
    private var selectedTab: String = "Settings"

    fun render(workspace: Workspace) {
        if (ImGui.begin("UberChop", 0)) {
            drawBanner()

            // Top controls
            if (ImGui.button("Start", 0f, 0f)) script.requestStart()
            if (script.userPaused) {
                if (ImGui.button("Resume", 0f, 0f)) script.resume()
            } else {
                if (ImGui.button("Pause", 0f, 0f)) script.pause()
            }

            ImGui.separator()

            // Left navigation
            ImGui.text("Navigation")
            if (ImGui.button("Settings", 0f, 0f)) selectedTab = "Settings"
            if (ImGui.button("Statistics", 0f, 0f)) selectedTab = "Statistics"
            if (ImGui.button("Advanced", 0f, 0f)) selectedTab = "Advanced"
            if (ImGui.button("Debug", 0f, 0f)) selectedTab = "Debug"

            ImGui.separator()

            when (selectedTab) {
                "Settings" -> drawSettingsTab()
                "Statistics" -> drawStatisticsTab()
                "Advanced" -> drawAdvancedTab()
                "Debug" -> drawDebugTab()
            }

            ImGui.separator()
            ImGui.text("Phase: ${script.phase}")
            ImGui.text("Status: ${script.status}")
            ImGui.text("Logs: ${script.logsChopped}")
        }
        ImGui.end()
    }

    private fun drawSettingsTab() {
        // Tree selection
        var nameBuffer = script.targetTree
        nameBuffer = ImGui.inputTextWithHint("Tree", "Tree/Oak/Willow/Yew/Magic", nameBuffer, 0)
        script.targetTree = if (nameBuffer.isBlank()) "Tree" else nameBuffer

        // Location selection (cycle through known locations)
        val locs = script.locationNames()
        var idx = script.selectedLocationIndex
        ImGui.text("Location: ${locs.getOrNull(idx) ?: "Any"}")
        if (ImGui.button("Prev Location", 0f, 0f)) {
            idx = if (idx <= 0) locs.size - 1 else idx - 1
            script.selectedLocationIndex = idx
        }
        if (ImGui.button("Next Location", 0f, 0f)) {
            idx = if (idx >= locs.size - 1) 0 else idx + 1
            script.selectedLocationIndex = idx
        }

        // Banking option
        var bank = script.bankWhenFull
        bank = ImGui.checkbox("Bank When Full", bank)
        script.bankWhenFull = bank
    }

    private fun drawStatisticsTab() {
        ImGui.text("Runtime: ${script.formattedRuntime()}")
        ImGui.text("Logs chopped: ${script.logsChopped}")
        ImGui.text("Logs/hr: ${script.logsPerHour()}")
    }

    private fun drawAdvancedTab() {
        ImGui.text("Advanced options coming soon")
        ImGui.text("- Banking pathing (WIP)")
        ImGui.text("- Tile navigation (WIP)")
    }

    private fun drawDebugTab() {
        val t = script.peekNearestTree()
        if (t != null) {
            ImGui.text("Nearest: ${t.name}")
        } else {
            ImGui.text("Nearest: none")
        }
    }

    private fun drawBanner() {
        ensureImageApiChecked()
        if (logoTexId == null && imageLoadMethod != null) {
            logoTexId = tryLoadLogoTex()
        }
        val tex = logoTexId
        if (tex != null && tex != 0L && imageDrawMethod != null) {
            try {
                imageDrawMethod!!.invoke(null, tex, 260f, 60f)
            } catch (_: Throwable) {
                ImGui.text("UberChop")
            }
        } else {
            ImGui.text("UberChop")
        }
        ImGui.separator()
    }

    private fun tryLoadLogoTex(): Long? {
        return try {
            val stream = this::class.java.classLoader.getResourceAsStream("images/Uberith_Logo_Full_Text.png")
                ?: return null
            val img: BufferedImage = ImageIO.read(stream) ?: return null
            val m = imageLoadMethod ?: return null
            val tex = m.invoke(null, img) as? Long
            if (tex != null && tex != 0L) tex else null
        } catch (_: Throwable) { null }
    }

    private fun ensureImageApiChecked() {
        if (imageApiChecked) return
        imageApiChecked = true
        try {
            imageLoadMethod = ImGui::class.java.getMethod("loadTexture", BufferedImage::class.java)
            imageDrawMethod = ImGui::class.java.getMethod("image", java.lang.Long.TYPE, java.lang.Float.TYPE, java.lang.Float.TYPE)
        } catch (_: Throwable) {
            imageLoadMethod = null
            imageDrawMethod = null
        }
    }
}
