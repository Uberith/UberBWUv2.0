package com.uberith.uberchop.gui

import com.uberith.api.ui.imgui.ImGuiWidgets
import com.uberith.api.script.handlers.AfkSettings
import com.uberith.api.script.handlers.BreakSettings
import com.uberith.api.script.handlers.LogoutSettings
import com.uberith.api.script.handlers.WorldHopSettings
import com.uberith.uberchop.UberChop
import net.botwithus.imgui.ImGui
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.script.ui.interfaces.BuildableUI

class UberChopGUI(private val script: UberChop) : BuildableUI {

    fun preload() {}

    fun render(workspace: Workspace) {
        renderInternal()
    }

    override fun buildUI() {
        renderInternal()
    }

    private fun renderInternal() {
        val settings = script.settingsSnapshot()
        val treeNames = script.treeNames()

        ImGui.setNextWindowSize(460f, 360f)
        if (!ImGui.begin("UberChop", 0)) {
            ImGui.end()
            return
        }

        ImGui.text("Runtime ${script.formattedRuntime()} | Logs ${script.logsChopped()} (${script.logsPerHour()}/h)")
        ImGui.text("Status ${script.statusText()} | WC lvl ${script.playerWoodcuttingLevel()} | Phase ${script.phase()}")
        ImGui.separator()

        ImGuiWidgets.tabs("UberChopTabs", listOf("Core", "Handlers"), currentIndex = currentTab, onSelect = { index ->
            currentTab = index
        })

        when (currentTab) {
            0 -> drawCore(settings, treeNames)
            1 -> drawHandlers(settings)
        }

        ImGui.end()
    }

    private fun drawCore(settings: com.uberith.uberchop.UberChopSettings, treeNames: List<String>) {
        if (treeNames.isNotEmpty()) {
            val currentTree = treeNames[settings.treeIndex.coerceIn(0, treeNames.lastIndex)]
            if (ImGui.beginCombo("Tree", currentTree, 0)) {
                treeNames.forEachIndexed { index, name ->
                    val selected = index == settings.treeIndex
                    if (ImGui.selectable(name, selected, 0, 0f, 0f)) {
                        script.mutateSettings { it.copy(treeIndex = index) }
                    }
                    if (selected) {
                        ImGui.setItemDefaultFocus()
                    }
                }
                ImGui.endCombo()
            }
        } else {
            ImGui.text("No trees available")
        }

        val locations = script.availableLocations()
        val names = locations.map { it.name }
        val current = settings.locationName.ifBlank { names.firstOrNull().orEmpty() }
        if (names.isEmpty()) {
            ImGui.text("No locations for selected tree")
        } else if (ImGui.beginCombo("Location", current, 0)) {
            names.forEach { name ->
                val selected = name == current
                if (ImGui.selectable(name, selected, 0, 0f, 0f)) {
                    script.mutateSettings { it.copy(locationName = name) }
                }
                if (selected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }

        ImGuiWidgets.toggle("Withdraw wood box", settings.withdrawWoodBox) { updated ->
            script.mutateSettings { it.copy(withdrawWoodBox = updated) }
        }

        val location = script.selectedLocation()
        ImGui.separator()
        ImGui.text("Selected location preview")
        ImGui.text("Chop tile: ${location?.chop ?: "--"}")
        ImGui.text("Bank tile: ${location?.bank ?: "--"}")
    }

    private fun drawHandlers(settings: com.uberith.uberchop.UberChopSettings) {
        drawBreakSettings(settings.breakSettings)
        ImGui.separator()
        drawAfkSettings(settings.afkSettings)
        ImGui.separator()
        drawLogoutSettings(settings.logoutSettings)
        ImGui.separator()
        drawWorldHopSettings(settings.worldHopSettings)
    }

    private fun drawBreakSettings(settings: BreakSettings) {
        ImGui.text("Break Scheduler")
        ImGuiWidgets.toggle("Enable breaks", settings.enabled) { enabled ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(enabled = enabled)) }
        }
        ImGuiWidgets.boundedInt("Frequency (min)", settings.frequencyMinutes, 5, 180, onChange = { value ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(frequencyMinutes = value)) }
        })
        ImGuiWidgets.boundedInt("Variance (min)", settings.varianceMinutes, 0, 180, onChange = { value ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(varianceMinutes = value)) }
        })
        ImGuiWidgets.boundedInt("Min duration (sec)", settings.minDurationSeconds, 10, 600, onChange = { value ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(minDurationSeconds = value)) }
        })
        ImGuiWidgets.boundedInt("Max duration (sec)", settings.maxDurationSeconds, 30, 900, onChange = { value ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(maxDurationSeconds = value)) }
        })
    }

    private fun drawAfkSettings(settings: AfkSettings) {
        ImGui.text("AFK Jitter")
        ImGuiWidgets.toggle("Enable AFK jitter", settings.enabled) { enabled ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(enabled = enabled)) }
        }
        ImGuiWidgets.boundedInt("Every min (min)", settings.minEveryMinutes, 5, 120, onChange = { value ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(minEveryMinutes = value)) }
        })
        ImGuiWidgets.boundedInt("Every max (min)", settings.maxEveryMinutes, settings.minEveryMinutes, 180, onChange = { value ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(maxEveryMinutes = value)) }
        })
        ImGuiWidgets.boundedInt("Min AFK (sec)", settings.minDurationSeconds, 5, 120, onChange = { value ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(minDurationSeconds = value)) }
        })
        ImGuiWidgets.boundedInt("Max AFK (sec)", settings.maxDurationSeconds, settings.minDurationSeconds, 240, onChange = { value ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(maxDurationSeconds = value)) }
        })
    }

    private fun drawLogoutSettings(settings: LogoutSettings) {
        ImGui.text("Logout Guard")
        ImGuiWidgets.toggle("Enable logout guard", settings.enabled) { enabled ->
            script.mutateSettings { it.copy(logoutSettings = settings.copy(enabled = enabled)) }
        }
        ImGuiWidgets.boundedInt("Max hours", settings.maxHours, 0, 12, onChange = { value ->
            script.mutateSettings { it.copy(logoutSettings = settings.copy(maxHours = value)) }
        })
        ImGuiWidgets.boundedInt("Max minutes", settings.maxMinutes, 0, 59, onChange = { value ->
            script.mutateSettings { it.copy(logoutSettings = settings.copy(maxMinutes = value)) }
        })
        ImGuiWidgets.boundedInt("Target actions", settings.targetActions.toInt(), 0, 5000, 50, onChange = { value ->
            script.mutateSettings { it.copy(logoutSettings = settings.copy(targetActions = value.toLong())) }
        })
    }

    private fun drawWorldHopSettings(settings: WorldHopSettings) {
        ImGui.text("World Hop Policy")
        ImGuiWidgets.toggle("Enable world hopping", settings.enabled) { enabled ->
            script.mutateSettings { it.copy(worldHopSettings = settings.copy(enabled = enabled)) }
        }
        ImGuiWidgets.toggle("Hop on crowd", settings.hopOnCrowd) { enabled ->
            script.mutateSettings { it.copy(worldHopSettings = settings.copy(hopOnCrowd = enabled)) }
        }
        ImGuiWidgets.boundedInt("Player threshold", settings.playerThreshold, 1, 20, onChange = { value ->
            script.mutateSettings { it.copy(worldHopSettings = settings.copy(playerThreshold = value)) }
        })
        ImGuiWidgets.toggle("Hop on no resources", settings.hopOnNoResources) { enabled ->
            script.mutateSettings { it.copy(worldHopSettings = settings.copy(hopOnNoResources = enabled)) }
        }
        ImGuiWidgets.boundedInt("Hop cooldown (sec)", settings.hopCooldownSeconds, 30, 600, onChange = { value ->
            script.mutateSettings { it.copy(worldHopSettings = settings.copy(hopCooldownSeconds = value)) }
        })
    }

    private var currentTab: Int = 0
}
