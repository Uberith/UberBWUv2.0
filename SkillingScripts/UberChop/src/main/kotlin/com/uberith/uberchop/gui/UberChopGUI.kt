package com.uberith.uberchop.gui

import com.uberith.api.game.skills.woodcutting.internal.LogHandlingMode
import com.uberith.api.ui.imgui.ImGuiWidgets
import com.uberith.uberchop.CustomLocation
import com.uberith.uberchop.UberChop
import com.uberith.uberchop.UberChopSettings
import net.botwithus.imgui.ImGui
import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.world.ClientState
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.script.ui.interfaces.BuildableUI

class UberChopGUI(private val script: UberChop) : BuildableUI {

    private var currentTab: Int = 0
    private val navItems = listOf(
        "Overview",
        "Core",
        "Handlers",
        "World Hop",
        "Advanced",
        "Stats",
        "Support"
    )

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
        if (currentTab >= navItems.size) {
            currentTab = navItems.lastIndex
        }

        ImGui.setNextWindowSize(560f, 540f)
        if (!ImGui.begin("UberChop", 0)) {
            ImGui.end()
            return
        }

        ImGui.text("Runtime ${script.formattedRuntime()}  |  Logs ${script.logsChopped()} (${script.logsPerHour()}/h)")
        ImGui.text("Status ${script.statusText()}  |  WC lvl ${script.playerWoodcuttingLevel()}  |  Phase ${script.phase()}")
        ImGui.separator()

        val navWidth = 140f
        val contentHeight = 420f
        val navItemHeight = (contentHeight / navItems.size).coerceAtLeast(26f)

        if (ImGui.beginChild("UberChopLeftNav", navWidth, contentHeight, true, 0)) {
            navItems.forEachIndexed { index, label ->
                val isSelected = index == currentTab
                if (ImGui.selectable(label, isSelected, 0, navWidth - 12f, navItemHeight)) {
                    currentTab = index
                }
            }
        }
        ImGui.endChild()

        ImGui.sameLine(0f, 12f)

        if (ImGui.beginChild("UberChopContent", 0f, contentHeight, true, 0)) {
            when (currentTab) {
                0 -> drawOverview(settings, treeNames)
                1 -> drawCore(settings, treeNames)
                2 -> drawHandlers(settings)
                3 -> drawWorldHop(settings)
                4 -> drawAdvanced(settings)
                5 -> drawStats(settings, treeNames)
                6 -> drawSupport()
            }
        }
        ImGui.endChild()

        ImGui.separator()

        val worldId = ClientState.GAME.id
        val player = LocalPlayer.self()
        val coordText = player?.coordinate?.toString() ?: "Unknown"
        val animId = player?.animationId ?: -1
        val animText = if (animId >= 0) animId.toString() else "Idle"
        ImGui.text("World $worldId | XYZ $coordText | Anim $animText")

        ImGui.end()
    }

    private fun drawOverview(settings: UberChopSettings, treeNames: List<String>) {
        val treeName = treeNames.getOrNull(settings.treeIndex)?.ifBlank { "Tree" } ?: "Tree"
        ImGui.text("Configured tree: $treeName")
        ImGui.text("Location: ${settings.locationName.ifBlank { "Auto" }}")
        ImGui.separator()
        val location = script.selectedLocation()
        if (location != null) {
            ImGui.text("Base chop tile: ${location.chop}")
            ImGui.text("Base bank tile: ${location.bank}")
        } else {
            ImGui.text("No location resolved for the selected tree.")
        }
        val override = settings.customLocations[settings.locationName]
        if (override != null && !override.isEmptyEntry()) {
            ImGui.separator()
            ImGui.text("Custom overrides")
            override.chopCoords().let { (x, y, z) ->
                if (x != null && y != null && z != null) {
                    ImGui.text("Chop override: ($x, $y, $z)")
                }
            }
            override.bankCoords().let { (x, y, z) ->
                if (x != null && y != null && z != null) {
                    ImGui.text("Bank override: ($x, $y, $z)")
                }
            }
        }
    }

    private fun drawCore(settings: UberChopSettings, treeNames: List<String>) {
        if (treeNames.isNotEmpty()) {
            val currentTree = treeNames[settings.treeIndex.coerceIn(0, treeNames.lastIndex)]
            if (ImGui.beginCombo("Tree", currentTree, 0)) {
                treeNames.forEachIndexed { index, name ->
                    val selected = index == settings.treeIndex
                    if (ImGui.selectable(name, selected, 0, 0f, 0f)) {
                        script.mutateSettings { it.copy(treeIndex = index) }
                    }
                    if (selected) ImGui.setItemDefaultFocus()
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
                if (selected) ImGui.setItemDefaultFocus()
            }
            ImGui.endCombo()
        }

        val logModes = LogHandlingMode.values()
        val currentMode = settings.logHandling
        if (ImGui.beginCombo("Log handling", currentMode.label(), 0)) {
            logModes.forEach { mode ->
                val selected = mode == currentMode
                if (ImGui.selectable(mode.label(), selected, 0, 0f, 0f)) {
                    script.mutateSettings { it.copy(logHandling = mode) }
                }
                if (selected) ImGui.setItemDefaultFocus()
            }
            ImGui.endCombo()
        }

        ImGuiWidgets.toggle("Withdraw wood box", settings.withdrawWoodBox) { updated ->
            script.mutateSettings { it.copy(withdrawWoodBox = updated) }
        }

        ImGuiWidgets.toggle("Pickup bird nests", settings.pickupNests) { updated ->
            script.mutateSettings { it.copy(pickupNests = updated) }
        }

    }

    private fun drawHandlers(settings: UberChopSettings) {
        drawBreakSettings(settings.breakSettings)
        ImGui.separator()
        drawAfkSettings(settings.afkSettings)
        ImGui.separator()
        drawLogoutSettings(settings.logoutSettings)
    }

    private fun drawWorldHop(settings: UberChopSettings) {
        val worldHop = settings.worldHopSettings
        ImGui.text("World Hop Policy")
        ImGuiWidgets.toggle("Enable world hopping", worldHop.enabled) { enabled ->
            script.mutateSettings { it.copy(worldHopSettings = worldHop.copy(enabled = enabled)) }
        }
        ImGuiWidgets.toggle("Hop on crowd", worldHop.hopOnCrowd) { enabled ->
            script.mutateSettings { it.copy(worldHopSettings = worldHop.copy(hopOnCrowd = enabled)) }
        }
        ImGuiWidgets.boundedInt("Player threshold", worldHop.playerThreshold, 1, 20) { value ->
            script.mutateSettings { it.copy(worldHopSettings = worldHop.copy(playerThreshold = value)) }
        }
        ImGuiWidgets.toggle("Hop on no resources", worldHop.hopOnNoResources) { enabled ->
            script.mutateSettings { it.copy(worldHopSettings = worldHop.copy(hopOnNoResources = enabled)) }
        }
        ImGuiWidgets.boundedInt("Hop cooldown (sec)", worldHop.hopCooldownSeconds, 30, 600) { value ->
            script.mutateSettings { it.copy(worldHopSettings = worldHop.copy(hopCooldownSeconds = value)) }
        }
    }

    private fun drawAdvanced(settings: UberChopSettings) {
        ImGui.text("Location Handling")
        ImGuiWidgets.toggle("Enable rotation across locations", settings.enableTreeRotation) { enabled ->
            script.mutateSettings { it.copy(enableTreeRotation = enabled) }
        }
        ImGui.separator()
        val locationName = settings.locationName
        if (locationName.isBlank()) {
            ImGui.text("Select a location to edit overrides.")
            return
        }
        val location = script.selectedLocation()
        if (location == null) {
            ImGui.text("No location metadata available for overrides.")
            return
        }
        val override = settings.customLocations[locationName]
        var chopOverrideEnabled = override?.chopX != null && override.chopY != null && override.chopZ != null
        ImGuiWidgets.toggle("Override chop tile", chopOverrideEnabled) { enabled ->
            chopOverrideEnabled = enabled
            if (enabled) {
                updateCustomLocation(locationName) { current ->
                    val base = current ?: CustomLocation()
                    base.copy(
                        chopX = base.chopX ?: location.chop?.x,
                        chopY = base.chopY ?: location.chop?.y,
                        chopZ = base.chopZ ?: location.chop?.z
                    )
                }
            } else {
                updateCustomLocation(locationName) { current ->
                    current?.copy(chopX = null, chopY = null, chopZ = null)
                }
            }
        }
        if (chopOverrideEnabled) {
            val latest = script.settingsSnapshot().customLocations[locationName]
            val current = latest?.chopCoords()
            val baseX = current?.first ?: location.chop?.x ?: 0
            val baseY = current?.second ?: location.chop?.y ?: 0
            val baseZ = current?.third ?: location.chop?.z ?: 0
            ImGuiWidgets.boundedInt("Chop X", baseX, -5000, 5000) { value ->
                updateCustomLocation(locationName) { existing ->
                    val base = existing ?: CustomLocation()
                    base.copy(chopX = value)
                }
            }
            ImGuiWidgets.boundedInt("Chop Y", baseY, -5000, 5000) { value ->
                updateCustomLocation(locationName) { existing ->
                    val base = existing ?: CustomLocation()
                    base.copy(chopY = value)
                }
            }
            ImGuiWidgets.boundedInt("Chop Z", baseZ, 0, 3) { value ->
                updateCustomLocation(locationName) { existing ->
                    val base = existing ?: CustomLocation()
                    base.copy(chopZ = value)
                }
            }
        }
        ImGui.separator()
        var bankOverrideEnabled = override?.bankX != null && override.bankY != null && override.bankZ != null
        ImGuiWidgets.toggle("Override bank tile", bankOverrideEnabled) { enabled ->
            bankOverrideEnabled = enabled
            if (enabled) {
                updateCustomLocation(locationName) { current ->
                    val base = current ?: CustomLocation()
                    base.copy(
                        bankX = base.bankX ?: location.bank?.x,
                        bankY = base.bankY ?: location.bank?.y,
                        bankZ = base.bankZ ?: location.bank?.z
                    )
                }
            } else {
                updateCustomLocation(locationName) { current ->
                    current?.copy(bankX = null, bankY = null, bankZ = null)
                }
            }
        }
        if (bankOverrideEnabled) {
            val latest = script.settingsSnapshot().customLocations[locationName]
            val current = latest?.bankCoords()
            val baseX = current?.first ?: location.bank?.x ?: 0
            val baseY = current?.second ?: location.bank?.y ?: 0
            val baseZ = current?.third ?: location.bank?.z ?: 0
            ImGuiWidgets.boundedInt("Bank X", baseX, -5000, 5000) { value ->
                updateCustomLocation(locationName) { existing ->
                    val base = existing ?: CustomLocation()
                    base.copy(bankX = value)
                }
            }
            ImGuiWidgets.boundedInt("Bank Y", baseY, -5000, 5000) { value ->
                updateCustomLocation(locationName) { existing ->
                    val base = existing ?: CustomLocation()
                    base.copy(bankY = value)
                }
            }
            ImGuiWidgets.boundedInt("Bank Z", baseZ, 0, 3) { value ->
                updateCustomLocation(locationName) { existing ->
                    val base = existing ?: CustomLocation()
                    base.copy(bankZ = value)
                }
            }
        }
        val latestOverride = script.settingsSnapshot().customLocations[locationName]
        if (latestOverride != null && !latestOverride.isEmptyEntry()) {
            if (ImGui.button("Clear overrides", 0f, 0f)) {
                updateCustomLocation(locationName) { null }
            }
        }
    }

    private fun drawStats(settings: UberChopSettings, treeNames: List<String>) {
        ImGui.text("Session Statistics")
        ImGui.separator()
        ImGui.text("Runtime: ${script.formattedRuntime()}")
        ImGui.text("Logs chopped: ${script.logsChopped()}")
        ImGui.text("Logs per hour: ${script.logsPerHour()}")
        ImGui.text("Current tree: ${treeNames.getOrNull(settings.treeIndex) ?: "Tree"}")
    }

    private fun drawSupport() {
        ImGui.text("Need help?")
        ImGui.separator()
        ImGui.text("- Share logs, screenshots, and reproduction steps.")
        ImGui.text("- Include your tree, location, and log handling mode.")
        ImGui.text("- Report issues in the Uberith Discord or BWU forums.")
    }


    private fun drawBreakSettings(settings: com.uberith.api.script.handlers.BreakSettings) {
        ImGui.text("Break Scheduler")
        ImGuiWidgets.toggle("Enable breaks", settings.enabled) { enabled ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(enabled = enabled)) }
        }
        ImGuiWidgets.boundedInt("Frequency (min)", settings.frequencyMinutes, 5, 180) { value ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(frequencyMinutes = value)) }
        }
        ImGuiWidgets.boundedInt("Variance (min)", settings.varianceMinutes, 0, 180) { value ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(varianceMinutes = value)) }
        }
        ImGuiWidgets.boundedInt("Min duration (sec)", settings.minDurationSeconds, 10, 600) { value ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(minDurationSeconds = value)) }
        }
        ImGuiWidgets.boundedInt("Max duration (sec)", settings.maxDurationSeconds, 30, 900) { value ->
            script.mutateSettings { it.copy(breakSettings = settings.copy(maxDurationSeconds = value)) }
        }
    }

    private fun drawAfkSettings(settings: com.uberith.api.script.handlers.AfkSettings) {
        ImGui.text("AFK Jitter")
        ImGuiWidgets.toggle("Enable AFK jitter", settings.enabled) { enabled ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(enabled = enabled)) }
        }
        ImGuiWidgets.boundedInt("Every min (min)", settings.minEveryMinutes, 5, 120) { value ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(minEveryMinutes = value)) }
        }
        ImGuiWidgets.boundedInt("Every max (min)", settings.maxEveryMinutes, settings.minEveryMinutes, 180) { value ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(maxEveryMinutes = value)) }
        }
        ImGuiWidgets.boundedInt("Min AFK (sec)", settings.minDurationSeconds, 5, 120) { value ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(minDurationSeconds = value)) }
        }
        ImGuiWidgets.boundedInt("Max AFK (sec)", settings.maxDurationSeconds, settings.minDurationSeconds, 240) { value ->
            script.mutateSettings { it.copy(afkSettings = settings.copy(maxDurationSeconds = value)) }
        }
    }

    private fun drawLogoutSettings(settings: com.uberith.api.script.handlers.LogoutSettings) {
        ImGui.text("Logout Guard")
        ImGuiWidgets.toggle("Enable logout guard", settings.enabled) { enabled ->
            script.mutateSettings { it.copy(logoutSettings = settings.copy(enabled = enabled)) }
        }
        ImGuiWidgets.boundedInt("Max hours", settings.maxHours, 0, 12) { value ->
            script.mutateSettings { it.copy(logoutSettings = settings.copy(maxHours = value)) }
        }
        ImGuiWidgets.boundedInt("Max minutes", settings.maxMinutes, 0, 59) { value ->
            script.mutateSettings { it.copy(logoutSettings = settings.copy(maxMinutes = value)) }
        }
        ImGuiWidgets.boundedInt("Target actions", settings.targetActions.toInt(), 0, 5000, 50) { value ->
            script.mutateSettings { it.copy(logoutSettings = settings.copy(targetActions = value.toLong())) }
        }
    }

    private fun updateCustomLocation(locationName: String, builder: (CustomLocation?) -> CustomLocation?): Unit {
        script.mutateSettings { current ->
            val mutable = current.customLocations.toMutableMap()
            val updated = builder(mutable[locationName])
            if (updated == null || updated.isEmptyEntry()) {
                mutable.remove(locationName)
            } else {
                mutable[locationName] = updated
            }
            current.copy(customLocations = mutable)
        }
    }

    private fun CustomLocation.isEmptyEntry(): Boolean =
        chopX == null && chopY == null && chopZ == null && bankX == null && bankY == null && bankZ == null

    private fun LogHandlingMode.label(): String =
        when (this) {
            LogHandlingMode.BANK -> "Bank"
            LogHandlingMode.DROP -> "Drop"
            LogHandlingMode.BURN -> "Burn"
            LogHandlingMode.FLETCH -> "Fletch"
        }
}

