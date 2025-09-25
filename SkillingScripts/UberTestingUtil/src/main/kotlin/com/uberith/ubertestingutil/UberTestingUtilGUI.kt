package com.uberith.ubertestingutil

import net.botwithus.imgui.ImGui
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.game.traversal.enums.LodestoneType
import net.botwithus.xapi.script.ui.interfaces.BuildableUI
import kotlin.math.max

class UberTestingUtilGUI(private val script: UberTestingUtil) : BuildableUI {

    fun preload() {
        // No resources to preload currently.
    }

    fun render(workspace: Workspace) {
        renderPanel()
    }

    override fun buildUI() {
        renderPanel()
    }

    private fun renderPanel() {
        val diagnostics = script.diagnosticsResults()
        val baseHeight = if (diagnostics.isEmpty()) {
            MIN_WINDOW_HEIGHT
        } else {
            max(MIN_WINDOW_HEIGHT, BASE_WINDOW_HEIGHT + diagnostics.size * DIAGNOSTIC_LINE_HEIGHT)
        }
        val windowHeight = baseHeight + script.diagnosticsHeightBuffer().toFloat()
        ImGui.setNextWindowSize(WINDOW_WIDTH, windowHeight)
        if (ImGui.begin("UberTestingUtil", 0)) {
            if (ImGui.beginTabBar("UberTestingUtilTabs", 0)) {
                if (ImGui.beginTabItem("Ground Items", 0)) {
                    renderGroundItemsTab(diagnostics)
                    ImGui.endTabItem()
                }
                if (ImGui.beginTabItem("Lodestone Network", 0)) {
                    renderLodestoneTab()
                    ImGui.endTabItem()
                }
                ImGui.endTabBar()
            }
        }
        ImGui.end()
    }

    private fun renderGroundItemsTab(diagnostics: List<GroundItemQueryTester.TestResult>) {
        renderStatusSection()
        ImGui.separator()
        renderFilterSection()
        ImGui.separator()
        renderStacksSection()
        ImGui.separator()
        renderDiagnosticsSection(diagnostics)
    }

    private fun renderStatusSection() {
        val status = script.getStatus() ?: "Idle"
        ImGui.text("Status: $status")
        val ageMs = script.lastScanAgeMs()
        val scanLabel = if (ageMs < 0) "Last scan: Not yet" else "Last scan: ${formatDuration(ageMs)} ago"
        ImGui.text(scanLabel)
        ImGui.text("Stacks matching filters: ${script.latestStacks().size}")
    }

    private fun renderFilterSection() {
        var minStack = script.minStackSize()
        var maxDistance = script.maxDistance()

        minStack = adjustInt("Minimum stack size", minStack, 1, 10_000, if (minStack >= 100) 10 else 1)
        maxDistance = adjustInt("Scan radius (tiles)", maxDistance, 1, 128, if (maxDistance >= 20) 5 else 1)

        script.updateMinStackSize(minStack)
        script.updateMaxDistance(maxDistance)
    }

    private fun renderStacksSection() {
        val stacks = script.latestStacks()
        if (stacks.isEmpty()) {
            ImGui.text("No nearby stacks passed the filters.")
        } else {
            ImGui.text("Nearby stacks:")
            var index = 1
            for (snapshot in stacks.take(8)) {
                val locationSuffix = snapshot.coordinate?.let { " @ ${script.formatCoordinate(it)}" } ?: ""
                ImGui.text("${index}. ${snapshot.displayLabel}$locationSuffix")
                index++
            }
            if (stacks.size > 8) {
                ImGui.text("${stacks.size - 8} more stack(s) tracked")
            }
        }

        if (ImGui.button("Pick up matching items", PICKUP_BUTTON_WIDTH, 0f) && stacks.isNotEmpty()) {
            script.requestPickup()
        }

        script.lastPickupResult()?.let { message ->
            val ageMs = script.lastPickupAgeMs()
            val suffix = if (ageMs < 0) "" else " (${formatDuration(ageMs)} ago)"
            ImGui.text("Pickup: $message$suffix")
        }
    }

    private fun renderDiagnosticsSection(results: List<GroundItemQueryTester.TestResult>) {
        ImGui.text("GroundItemQuery diagnostics:")
        var bufferPx = script.diagnosticsHeightBuffer()
        bufferPx = adjustInt("Height buffer (px)", bufferPx, 0, 600, 20)
        script.updateDiagnosticsHeightBuffer(bufferPx)

        val diagnosticsAge = script.lastDiagnosticsAgeMs()
        val diagnosticsLabel = if (diagnosticsAge < 0) {
            "Last run: Not yet"
        } else {
            "Last run: ${formatDuration(diagnosticsAge)} ago"
        }
        ImGui.text(diagnosticsLabel)

        if (results.isEmpty()) {
            ImGui.text("Waiting for diagnostic data...")
            return
        }

        for (result in results) {
            ImGui.text("[${statusTag(result.status)}] ${result.name}: ${result.detail}")
        }
    }

    private fun renderLodestoneTab() {
        val lodestones = LodestoneType.values()
        val selected = script.selectedLodestone()
        val preview = script.formatLodestoneName(selected)

        if (ImGui.beginCombo("Destination", preview, 0)) {
            for (lodestone in lodestones) {
                val label = buildString {
                    append(script.formatLodestoneName(lodestone))
                    if (!script.isLodestoneUnlocked(lodestone)) {
                        append(" (Locked)")
                    }
                }
                val isSelected = lodestone == selected
                if (ImGui.selectable(label, isSelected, 0, 0f, 0f)) {
                    script.updateSelectedLodestone(lodestone)
                }
                if (isSelected) {
                    ImGui.setItemDefaultFocus()
                }
            }
            ImGui.endCombo()
        }

        ImGui.separator()

        val networkStatus = if (script.lodestoneNetworkOpen()) "Open" else "Closed"
        ImGui.text("Network interface: $networkStatus")
        val availability = if (script.isLodestoneUnlocked(selected)) "Unlocked" else "Locked"
        ImGui.text("Selected lodestone: $availability")

        if (ImGui.button("Teleport via Lodestone", TELEPORT_BUTTON_WIDTH, 0f)) {
            script.requestLodestoneTeleport()
        }

        script.lastLodestoneTeleportResult()?.let { message ->
            val ageMs = script.lastLodestoneTeleportAgeMs()
            val suffix = if (ageMs < 0) "" else " (${formatDuration(ageMs)} ago)"
            ImGui.text("Teleport: $message$suffix")
        }
    }

    private fun adjustInt(label: String, value: Int, min: Int, max: Int, step: Int): Int {
        var current = value
        val id = label.replace(' ', '_')
        ImGui.text(label)
        ImGui.sameLine(0f, 8f)
        if (ImGui.button("-##${id}_dec", 26f, 0f)) {
            current = (current - step).coerceAtLeast(min)
        }
        ImGui.sameLine(0f, 4f)
        ImGui.text(current.toString())
        ImGui.sameLine(0f, 4f)
        if (ImGui.button("+##${id}_inc", 26f, 0f)) {
            current = (current + step).coerceAtMost(max)
        }
        return current
    }

    private fun statusTag(status: GroundItemQueryTester.Status): String = when (status) {
        GroundItemQueryTester.Status.PASSED -> "PASS"
        GroundItemQueryTester.Status.FAILED -> "FAIL"
        GroundItemQueryTester.Status.SKIPPED -> "SKIP"
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%02d:%02d", minutes, seconds)
        }
    }

    private companion object {
        private const val WINDOW_WIDTH = 420f
        private const val MIN_WINDOW_HEIGHT = 360f
        private const val BASE_WINDOW_HEIGHT = 260f
        private const val DIAGNOSTIC_LINE_HEIGHT = 18f
        private const val PICKUP_BUTTON_WIDTH = 220f
        private const val TELEPORT_BUTTON_WIDTH = 240f
    }
}

