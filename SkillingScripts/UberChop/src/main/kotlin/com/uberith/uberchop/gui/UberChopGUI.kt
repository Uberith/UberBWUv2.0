package com.uberith.uberchop.gui

import com.uberith.uberchop.UberChop
import net.botwithus.imgui.ImGui
import net.botwithus.ui.workspace.Workspace

class UberChopGUI(private val script: UberChop) {
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
        ImGui.setNextWindowSize(700f, 520f)
        if (ImGui.begin("UberChop", 0)) {
            ImGui.text("Runtime: ${script.formattedRuntime()}")
            ImGui.text("Logs: ${script.logsChopped} (${script.logsPerHour()}/hr)")
            val bank = ImGui.checkbox("Bank When Full", script.bankWhenFull)
            script.bankWhenFull = bank
            ImGui.separator()
            ImGui.text("Target Tree:")
            ImGui.sameLine(0f, 8f)
            // Simple choices
            if (ImGui.button("Tree", 70f, 0f)) script.targetTree = "Tree"
            ImGui.sameLine(0f, 5f)
            if (ImGui.button("Oak", 70f, 0f)) script.targetTree = "Oak"
            ImGui.sameLine(0f, 5f)
            if (ImGui.button("Willow", 70f, 0f)) script.targetTree = "Willow"
            ImGui.sameLine(0f, 5f)
            if (ImGui.button("Yew", 70f, 0f)) script.targetTree = "Yew"
            ImGui.sameLine(0f, 5f)
            if (ImGui.button("Magic", 70f, 0f)) script.targetTree = "Magic"
            ImGui.separator()
            ImGui.text("Status: ")
            ImGui.sameLine(0f, 6f)
            ImGui.text(script.status)

            ImGui.separator()
            ImGui.text("Log Handling:")
            ImGui.sameLine(0f, 8f)
            if (ImGui.button("Bank logs + nests", 160f, 0f)) script.settings.logHandlingMode = 0
            ImGui.sameLine(0f, 5f)
            if (ImGui.button("Magic Notepaper", 140f, 0f)) script.settings.logHandlingMode = 1
            ImGui.sameLine(0f, 5f)
            if (ImGui.button("No Bank", 90f, 0f)) script.settings.logHandlingMode = 2
            ImGui.text("Current: " + when (script.settings.logHandlingMode) {
                1 -> "Magic Notepaper"
                2 -> "No Bank"
                else -> "Bank logs + nests"
            })

            ImGui.separator()
            ImGui.text("Break Handler")
            var b = ImGui.checkbox("Perform Random Breaks", script.settings.performRandomBreak)
            script.settings.performRandomBreak = b
            script.settings.breakFrequency = adjustInt("Break Frequency (per hr)", script.settings.breakFrequency, 0, 12)
            script.settings.minBreak = adjustInt("Min Break (sec)", script.settings.minBreak, 0, 600, 5)
            script.settings.maxBreak = adjustInt("Max Break (sec)", script.settings.maxBreak, 0, 3600, 5)

            ImGui.separator()
            ImGui.text("Logout Handler")
            b = ImGui.checkbox("Enable Timed Logout", script.settings.logoutDurationEnable)
            script.settings.logoutDurationEnable = b
            script.settings.logoutHours = adjustInt("Logout After (hours)", script.settings.logoutHours, 0, 24)
            script.settings.logoutMinutes = adjustInt("Logout After (minutes)", script.settings.logoutMinutes, 0, 59)
            script.settings.logoutSeconds = adjustInt("Logout After (seconds)", script.settings.logoutSeconds, 0, 59)

            ImGui.separator()
            ImGui.text("AFK Handler")
            b = ImGui.checkbox("Enable AFK", script.settings.enableAfk)
            script.settings.enableAfk = b
            script.settings.afkEveryMin = adjustInt("AFK Every (min) min", script.settings.afkEveryMin, 0, 180)
            script.settings.afkEveryMax = adjustInt("AFK Every (min) max", script.settings.afkEveryMax, 0, 240)
            script.settings.afkDurationMin = adjustInt("AFK Duration min (sec)", script.settings.afkDurationMin, 0, 600, 5)
            script.settings.afkDurationMax = adjustInt("AFK Duration max (sec)", script.settings.afkDurationMax, 0, 3600, 5)

            ImGui.separator()
            ImGui.text("Auto-Stop Handler")
            b = ImGui.checkbox("Enable Auto-Stop", script.settings.enableAutoStop)
            script.settings.enableAutoStop = b
            script.settings.stopAfterHours = adjustInt("Stop After (hours)", script.settings.stopAfterHours, 0, 48)
            script.settings.stopAfterMinutes = adjustInt("Stop After (minutes)", script.settings.stopAfterMinutes, 0, 59)
            script.settings.stopAfterXp = adjustInt("Stop After XP", script.settings.stopAfterXp, 0, 50_000_000, 1000)
            script.settings.stopAfterLogs = adjustInt("Stop After Logs", script.settings.stopAfterLogs, 0, 1_000_000, 10)

            ImGui.separator()
            ImGui.text("Extras & Control")
            b = ImGui.checkbox("Pickup Nests", script.settings.pickupNests)
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

            ImGui.separator()
            ImGui.text("Auto-Skill")
            b = ImGui.checkbox("Auto-Progress Tree", script.settings.autoProgressTree)
            script.settings.autoProgressTree = b
            b = ImGui.checkbox("Auto-Upgrade Tree", script.settings.autoUpgradeTree)
            script.settings.autoUpgradeTree = b
            script.settings.tanningProductIndex = adjustInt("Preset/Prod Index", script.settings.tanningProductIndex, 0, 20)

            ImGui.separator()
            ImGui.text("World Hopping Filters")
            script.settings.minPing = adjustInt("Min Ping", script.settings.minPing, 0, 1000, 5)
            script.settings.maxPing = adjustInt("Max Ping", script.settings.maxPing, 0, 1000, 5)
            script.settings.minPopulation = adjustInt("Min Pop", script.settings.minPopulation, 0, 4000, 10)
            script.settings.maxPopulation = adjustInt("Max Pop", script.settings.maxPopulation, 0, 4000, 10)
            script.settings.hopDelayMs = adjustInt("Hop Delay (ms)", script.settings.hopDelayMs, 0, 60_000, 100)
            b = ImGui.checkbox("Members-Only Worlds", script.settings.memberOnlyWorlds)
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

            ImGui.separator()
            ImGui.text("Saved Preferences")
            script.settings.savedTreeType = adjustInt("Saved Tree Type", script.settings.savedTreeType, 0, 50, 1)
            ImGui.text("Saved Location: ${script.settings.savedLocation}")
        }
        ImGui.end()
    }
}

