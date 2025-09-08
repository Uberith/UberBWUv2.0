package com.uberith.uberchop.gui

import com.uberith.uberchop.UberChop
import net.botwithus.imgui.ImGui
import net.botwithus.ui.workspace.Workspace

class UberChopGUI(private val script: UberChop) {
    // 0 Overview, 1 Core, 2 Handlers, 3 WorldHop, 4 Advanced, 5 Statistics, 6 Support
    private var selectedTab: Int = 0
    private val FIXED_W = 560f
    private val FIXED_H = 520f
    private val CONTENT_H = 400f

    private fun adjustInt(label: String, value: Int, min: Int, max: Int, step: Int = 1): Int {
        ImGui.text(label)
        ImGui.sameLine(0f, 6f)
        var v = value
        if (ImGui.button("-", 24f, 0f)) v = (v - step).coerceAtLeast(min)
        ImGui.sameLine(0f, 4f)
        ImGui.text(v.toString())
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("+", 24f, 0f)) v = (v + step).coerceAtMost(max)
        return v
    }

    fun render(workspace: Workspace) {
        // Fixed-size, small-screen friendly window
        ImGui.setNextWindowSize(FIXED_W, FIXED_H)
        if (ImGui.begin("UberChop", 0)) {
            val cm = ColorManager()
            cm.pushColors()
            // Top summary (compact)
            ImGui.text("Runtime ${script.formattedRuntime()}  |  Logs ${script.logsChopped} (${script.logsPerHour()}/h)  |  Status ${script.status}")
            ImGui.separator()

            // Left navigation (vertical buttons with icons + selection marker), Right content (scrollable)
            val navW = 120f
            if (ImGui.beginChild("LeftNav", navW, CONTENT_H, true, 0)) {
                val navCount = 7
                val btnH = 28f
                val navGap = ((CONTENT_H - navCount * btnH) / (navCount + 1)).coerceAtLeast(6f)
                val rightPad = 22f

                drawNavSpacer(navGap)
                drawNavItem(0, "Overview", navW, cm, btnH, rightPad)
                drawNavSpacer(navGap)
                drawNavItem(1, "Core", navW, cm, btnH, rightPad)
                drawNavSpacer(navGap)
                drawNavItem(2, "Handlers", navW, cm, btnH, rightPad)
                drawNavSpacer(navGap)
                drawNavItem(3, "WorldHop", navW, cm, btnH, rightPad)
                drawNavSpacer(navGap)
                drawNavItem(4, "Advanced", navW, cm, btnH, rightPad)
                drawNavSpacer(navGap)
                drawNavItem(5, "Statistics", navW, cm, btnH, rightPad)
                drawNavSpacer(navGap)
                drawNavItem(6, "Support", navW, cm, btnH, rightPad)
                drawNavSpacer(navGap)
            }
            ImGui.endChild()

            ImGui.sameLine(0f, 8f)

            // Darker child background for content area to add depth
            ImGui.pushStyleColor(ColorManager.ColorType.ChildBg.index, 12f/255f, 18f/255f, 45f/255f, 0.62f)
            if (ImGui.beginChild("RightContent", 0f, CONTENT_H, true, 0)) {
                // Inner child with darker border to simulate inner shadow
                val borderDark = cm.getColor(ColorManager.ColorType.BorderShadow) ?: intArrayOf(8,8,16,170)
                val bf = cm.colorToFloats(borderDark)
                ImGui.pushStyleColor(ColorManager.ColorType.Border.index, bf[0], bf[1], bf[2], bf[3])
                if (ImGui.beginChild("RightInner", 0f, 0f, true, 0)) {
                    when (selectedTab) {
                        0 -> drawOverview()
                        1 -> drawCore()
                        2 -> drawHandlers()
                        3 -> drawWorldHop()
                        4 -> drawAdvanced()
                        5 -> drawStatistics()
                        6 -> drawSupport()
                    }
                }
                ImGui.endChild()
                ImGui.popStyleColor(1)
            }
            ImGui.endChild()
            ImGui.popStyleColor(1)

            // Bottom status bar
            ImGui.separator()
            val worldText = tryGetWorldIdText()
            val pingText = tryGetPingMsText()
            ImGui.text("W: $worldText  |  Ping: $pingText ms")
            cm.popColors()
        }
        ImGui.end()
    }

    private fun header(title: String, cm: ColorManager) {
        val fa = cm.accentF()
        // Accent-colored title text
        ImGui.pushStyleColor(ColorManager.ColorType.Text.index, fa[0], fa[1], fa[2], fa[3])
        ImGui.text(title)
        ImGui.popStyleColor(1)
        // Accent underline bar
        ImGui.pushStyleColor(ColorManager.ColorType.ChildBg.index, fa[0], fa[1], fa[2], fa[3])
        ImGui.beginChild("underline_$title", 0f, 2f, false, 0)
        ImGui.endChild()
        ImGui.popStyleColor(1)
        ImGui.spacing()
    }

    private fun drawNavItem(index: Int, label: String, navW: Float, cm: ColorManager, btnH: Float, rightPad: Float) {
        val isSelected = (selectedTab == index)
        if (isSelected) {
            // Colored selection bar using modern accent
            run {
                val f = cm.accentF()
                ImGui.pushStyleColor(ColorManager.ColorType.ChildBg.index, f[0], f[1], f[2], f[3])
            }
            ImGui.beginChild("NavSelBar$index", 5f, btnH, false, 0)
            ImGui.endChild()
            ImGui.popStyleColor(1)
            ImGui.sameLine(0f, 6f)
            // Accent the active button background
            val fa = cm.accentF()
            val fh = cm.accentHoverF()
            val ft = cm.accentActiveF()
            ImGui.pushStyleColor(ColorManager.ColorType.Button.index, fa[0], fa[1], fa[2], fa[3])
            ImGui.pushStyleColor(ColorManager.ColorType.ButtonHovered.index, fh[0], fh[1], fh[2], fh[3])
            ImGui.pushStyleColor(ColorManager.ColorType.ButtonActive.index, ft[0], ft[1], ft[2], ft[3])
        } else {
            ImGui.beginChild("NavSelBar$index", 5f, btnH, false, 0)
            ImGui.endChild()
            ImGui.sameLine(0f, 6f)
        }
        val shown = label
        val clicked = ImGui.button(shown, navW - 5f - 6f - rightPad, btnH)
        if (isSelected) ImGui.popStyleColor(3)
        if (clicked) {
            selectedTab = index
        }
    }

    private fun drawNavSpacer(height: Float) {
        // Create vertical space using an empty child of specified height
        ImGui.beginChild("NavSpacer_${System.nanoTime()}", 0f, height, false, 0)
        ImGui.endChild()
    }

    private fun drawOverview() {
        val bank = ImGui.checkbox("Bank When Full", script.bankWhenFull)
        script.bankWhenFull = bank

        ImGui.separator()
        ImGui.text("Target Tree:")
        ImGui.sameLine(0f, 6f)
        if (ImGui.button("Tree", 58f, 0f)) script.targetTree = "Tree"
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("Oak", 54f, 0f)) script.targetTree = "Oak"
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("Willow", 64f, 0f)) script.targetTree = "Willow"
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("Yew", 54f, 0f)) script.targetTree = "Yew"
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("Magic", 64f, 0f)) script.targetTree = "Magic"

        ImGui.separator()
        ImGui.text("Log Handling:")
        if (ImGui.button("Bank logs+nests", 140f, 0f)) script.settings.logHandlingMode = 0
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("Magic Notepaper", 130f, 0f)) script.settings.logHandlingMode = 1
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("No Bank", 80f, 0f)) script.settings.logHandlingMode = 2
        ImGui.text("Current: " + when (script.settings.logHandlingMode) {
            1 -> "Magic Notepaper"
            2 -> "No Bank"
            else -> "Bank logs + nests"
        })
    }

    private fun drawCore() {
        var b = ImGui.checkbox("Pickup Nests", script.settings.pickupNests)
        script.settings.pickupNests = b
        b = ImGui.checkbox("Enable Tree Rotation", script.settings.enableTreeRotation)
        script.settings.enableTreeRotation = b
        b = ImGui.checkbox("Enable World Hopping", script.settings.enableWorldHopping)
        script.settings.enableWorldHopping = b
        b = ImGui.checkbox("Use Magic Notepaper", script.settings.useMagicNotepaper)
        script.settings.useMagicNotepaper = b
        b = ImGui.checkbox("Use Crystallise", script.settings.useCrystallise)
        script.settings.useCrystallise = b
        b = ImGui.checkbox("Use Juju Potions", script.settings.useJujuPotions)
        script.settings.useJujuPotions = b
    }

    private fun drawHandlers() {
        header("Break Handler", ColorManager())
        var b = ImGui.checkbox("Random Breaks", script.settings.performRandomBreak)
        script.settings.performRandomBreak = b
        script.settings.breakFrequency = adjustInt("Breaks/hr", script.settings.breakFrequency, 0, 12)
        script.settings.minBreak = adjustInt("Min Break (s)", script.settings.minBreak, 0, 600, 5)
        script.settings.maxBreak = adjustInt("Max Break (s)", script.settings.maxBreak, 0, 3600, 5)

        ImGui.separator()
        header("Logout Handler", ColorManager())
        b = ImGui.checkbox("Enable Timed Logout", script.settings.logoutDurationEnable)
        script.settings.logoutDurationEnable = b
        script.settings.logoutHours = adjustInt("After (h)", script.settings.logoutHours, 0, 24)
        script.settings.logoutMinutes = adjustInt("After (m)", script.settings.logoutMinutes, 0, 59)
        script.settings.logoutSeconds = adjustInt("After (s)", script.settings.logoutSeconds, 0, 59)

        ImGui.separator()
        header("AFK Handler", ColorManager())
        b = ImGui.checkbox("Enable AFK", script.settings.enableAfk)
        script.settings.enableAfk = b
        script.settings.afkEveryMin = adjustInt("Every min (m)", script.settings.afkEveryMin, 0, 180)
        script.settings.afkEveryMax = adjustInt("Every max (m)", script.settings.afkEveryMax, 0, 240)
        script.settings.afkDurationMin = adjustInt("Duration min (s)", script.settings.afkDurationMin, 0, 600, 5)
        script.settings.afkDurationMax = adjustInt("Duration max (s)", script.settings.afkDurationMax, 0, 3600, 5)

        ImGui.separator()
        header("Auto-Stop", ColorManager())
        b = ImGui.checkbox("Enable Auto-Stop", script.settings.enableAutoStop)
        script.settings.enableAutoStop = b
        script.settings.stopAfterHours = adjustInt("After (h)", script.settings.stopAfterHours, 0, 48)
        script.settings.stopAfterMinutes = adjustInt("After (m)", script.settings.stopAfterMinutes, 0, 59)
        script.settings.stopAfterXp = adjustInt("After XP", script.settings.stopAfterXp, 0, 50_000_000, 1000)
        script.settings.stopAfterLogs = adjustInt("After Logs", script.settings.stopAfterLogs, 0, 1_000_000, 10)
    }

    private fun drawWorldHop() {
        header("World Hop Filters", ColorManager())
        script.settings.minPing = adjustInt("Min Ping", script.settings.minPing, 0, 1000, 5)
        script.settings.maxPing = adjustInt("Max Ping", script.settings.maxPing, 0, 1000, 5)
        script.settings.minPopulation = adjustInt("Min Pop", script.settings.minPopulation, 0, 4000, 10)
        script.settings.maxPopulation = adjustInt("Max Pop", script.settings.maxPopulation, 0, 4000, 10)
        script.settings.hopDelayMs = adjustInt("Hop Delay (ms)", script.settings.hopDelayMs, 0, 60_000, 100)
        var b = ImGui.checkbox("Members-Only Worlds", script.settings.memberOnlyWorlds)
        script.settings.memberOnlyWorlds = b
        b = ImGui.checkbox("Only F2P", script.settings.onlyFreeToPlay)
        script.settings.onlyFreeToPlay = b
        b = ImGui.checkbox("Hop On Chat Activity", script.settings.hopOnChat)
        script.settings.hopOnChat = b
        b = ImGui.checkbox("Hop On Crowd Threshold", script.settings.hopOnCrowd)
        script.settings.hopOnCrowd = b
        script.settings.playerThreshold = adjustInt("Player Threshold", script.settings.playerThreshold, 0, 200, 1)
        b = ImGui.checkbox("Hop On No Trees", script.settings.hopOnNoTrees)
        script.settings.hopOnNoTrees = b
    }

    private fun drawAdvanced() {
        ImGui.text("Saved Preferences")
        script.settings.savedTreeType = adjustInt("Saved Tree Type", script.settings.savedTreeType, 0, 50, 1)
        ImGui.text("Saved Location: ${script.settings.savedLocation}")
        ImGui.separator()
        ImGui.text("Auto-Skill")
        var b = ImGui.checkbox("Auto-Progress Tree", script.settings.autoProgressTree)
        script.settings.autoProgressTree = b
        b = ImGui.checkbox("Auto-Upgrade Tree", script.settings.autoUpgradeTree)
        script.settings.autoUpgradeTree = b
        script.settings.tanningProductIndex = adjustInt("Preset/Prod Index", script.settings.tanningProductIndex, 0, 20)
    }

    private fun drawStatistics() {
        ImGui.text("Statistics")
        ImGui.separator()
        ImGui.text("Runtime: ${script.formattedRuntime()}")
        ImGui.text("Logs chopped: ${script.logsChopped}")
        ImGui.text("Logs/hour: ${script.logsPerHour()}")
        ImGui.separator()
        ImGui.text("Target: ${script.targetTree}")
        ImGui.text("Phase: ${script.phase}")
        ImGui.text("Banking enabled: ${script.bankWhenFull}")
        ImGui.text("Log handling: " + when (script.settings.logHandlingMode) {
            1 -> "Magic Notepaper"
            2 -> "No Bank"
            else -> "Bank logs + nests"
        })
    }

    private fun drawSupport() {
        ImGui.text("Support")
        ImGui.separator()
        ImGui.text("Having issues or suggestions?")
        ImGui.text("- Share logs, screenshots, and steps to reproduce.")
        ImGui.text("- Include your target tree and location.")
        ImGui.separator()
        ImGui.text("Quick Tips")
        ImGui.text("- Use Magic Notepaper mode when stationary.")
        ImGui.text("- WorldHop filters can reduce crowds.")
        ImGui.text("- Breaks/AFK help reduce detection risk.")
    }

    private fun tryGetWorldIdText(): String {
        return try {
            // Attempt common providers via reflection
            val candidates = arrayOf(
                "net.botwithus.rs3.world.World",
                "net.botwithus.xapi.game.world.World"
            )
            for (cn in candidates) {
                try {
                    val cls = Class.forName(cn)
                    // Look for zero-arg static methods with world info
                    for (m in cls.methods) {
                        if (m.parameterCount == 0 && java.lang.reflect.Modifier.isStatic(m.modifiers)) {
                            val name = m.name.lowercase()
                            if (name.contains("world") || name.contains("current")) {
                                val v = m.invoke(null)
                                when (v) {
                                    is Number -> return v.toString()
                                    is String -> if (v.isNotEmpty()) return v
                                    else -> {
                                        // If object, try id()/getId()
                                        try {
                                            val idM = v.javaClass.methods.firstOrNull { it.parameterCount == 0 && it.name.lowercase().contains("id") }
                                            if (idM != null) return (idM.invoke(v) as? Number)?.toString() ?: v.toString()
                                        } catch (_: Throwable) { }
                                        return v.toString()
                                    }
                                }
                            }
                        }
                    }
                } catch (_: Throwable) { }
            }
            "N/A"
        } catch (_: Throwable) { "N/A" }
    }

    private fun tryGetPingMsText(): String {
        return try {
            val candidates = arrayOf(
                "net.botwithus.rs3.world.World",
                "net.botwithus.xapi.game.world.World",
                "net.botwithus.client.Network",
                "net.botwithus.rs3.client.Network"
            )
            for (cn in candidates) {
                try {
                    val cls = Class.forName(cn)
                    for (m in cls.methods) {
                        if (m.parameterCount == 0 && java.lang.reflect.Modifier.isStatic(m.modifiers)) {
                            val name = m.name.lowercase()
                            if (name.contains("ping") || name.contains("latency") || name.contains("rtt")) {
                                val v = m.invoke(null)
                                return when (v) {
                                    is Number -> v.toString()
                                    is String -> v
                                    else -> v.toString()
                                }
                            }
                        }
                    }
                } catch (_: Throwable) { }
            }
            "N/A"
        } catch (_: Throwable) { "N/A" }
    }
}

