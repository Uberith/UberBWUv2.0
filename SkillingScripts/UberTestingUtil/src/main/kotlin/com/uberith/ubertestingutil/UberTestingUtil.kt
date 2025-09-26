package com.uberith.ubertestingutil

import net.botwithus.kxapi.game.traversal.teleportSuspend
import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.rs3.item.GroundItem
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.game.traversal.LodestoneNetwork
import net.botwithus.xapi.game.traversal.enums.LodestoneType
import net.botwithus.xapi.query.GroundItemQuery
import net.botwithus.xapi.script.ui.interfaces.BuildableUI
import org.slf4j.LoggerFactory
import kotlin.jvm.Volatile
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Info(
    name = "UberTestingUtil",
    description = "Scans ground items and logs nearby stacks.",
    version = "0.1.0",
    author = "Uberith"
)
class UberTestingUtil : SuspendableScript() {

    enum class TestingOption {
        NONE,
        GROUND_ITEMS
    }

    private val log = LoggerFactory.getLogger(UberTestingUtil::class.java)
    @Volatile private var status: String = "Idle"
    @Volatile private var lastSummary: String? = null
    @Volatile private var lastScanAt: Long = 0L
    @Volatile private var latestSnapshots: List<GroundItemSnapshot> = emptyList()
    @Volatile private var minStackSizeValue: Int = 1
    @Volatile private var maxDistanceTiles: Int = 12
    @Volatile private var latestDiagnostics: List<GroundItemQueryTester.TestResult> = emptyList()
    @Volatile private var lastDiagnosticsCheckAt: Long = 0L
    @Volatile private var lastDiagnosticsFailureSignature: String? = null
    @Volatile private var diagnosticsHeightBufferPx: Int = 0
    @Volatile private var pickupRequested: Boolean = false
    @Volatile private var lastPickupMessage: String? = null
    @Volatile private var lastPickupAt: Long = 0L
    @Volatile private var selectedLodestone: LodestoneType = LodestoneType.BURTHORPE
    @Volatile private var lodestoneTeleportRequest: LodestoneType? = null
    @Volatile private var lastLodestoneMessage: String? = null
    @Volatile private var lastLodestoneAt: Long = 0L
    @Volatile private var testingOption: TestingOption = TestingOption.NONE

    private val queryTester = GroundItemQueryTester()
    private val gui by lazy { UberTestingUtilGUI(this) }

    override fun getStatus(): String? = status

    override fun onInitialize() {
        super.onInitialize()
        try {
            gui.preload()
        } catch (_: Throwable) {
            // Ignore pre-load issues; UI will attempt to build lazily.
        }
    }

    override fun onActivation() {
        status = "Awaiting selection"
        testingOption = TestingOption.NONE
        clearGroundItemResults()
        log.info("UberTestingUtil activated. Awaiting testing selection.")
    }

    override fun onDeactivation() {
        status = "Inactive"
        log.info("UberTestingUtil deactivated.")
        testingOption = TestingOption.NONE
        clearGroundItemResults()
        diagnosticsHeightBufferPx = 0
        selectedLodestone = LodestoneType.BURTHORPE
        lodestoneTeleportRequest = null
        lastLodestoneMessage = null
        lastLodestoneAt = 0L
    }

    override suspend fun onLoop() {
        processLodestoneTeleportRequest()

        if (testingOption != TestingOption.GROUND_ITEMS) {
            if (testingOption == TestingOption.NONE) {
                status = "Awaiting selection"
            }
            awaitTicks(5)
            return
        }

        val scanMinStack = minStackSizeValue
        val scanMaxDistance = maxDistanceTiles

        val distanceQuery = GroundItemQuery.newQuery().distance(scanMaxDistance.toDouble())
        val groundItems = distanceQuery.results().toList()

        maybeRunDiagnostics(groundItems)

        val matchingItems = groundItems.filter { it.quantity >= scanMinStack }
        lastScanAt = System.currentTimeMillis()

        handlePickupRequests(matchingItems)

        if (matchingItems.isEmpty()) {
            status = "No ground items"
            if (lastSummary != null) {
                log.info("No ground items detected nearby.")
                lastSummary = null
            } else {
                log.debug("No ground items detected nearby.")
            }
            latestSnapshots = emptyList()
            awaitTicks(5)
            return
        }

        status = "Tracking ${matchingItems.size} stacks"
        val sortedSnapshots = matchingItems
            .map { createSnapshot(it) }
            .sortedByDescending { it.quantity }
        latestSnapshots = sortedSnapshots.take(25)

        val summary = sortedSnapshots.take(5).joinToString { it.displayLabel }
        if (summary != lastSummary) {
            log.info("Nearby stacks (min qty $scanMinStack, radius $scanMaxDistance): $summary")
            lastSummary = summary
        } else {
            log.debug("Tracking ${matchingItems.size} stacks (min qty $scanMinStack).")
        }

        awaitTicks(5)
    }

    override fun getBuildableUI(): BuildableUI = gui

    override fun onDrawConfig(p0: Workspace?) {
        p0?.let {
            try {
                gui.render(it)
            } catch (_: Throwable) {
                // Swallow UI errors to avoid interrupting scripting engine.
            }
        }
    }

    override fun onDraw(p0: Workspace) {
        try {
            gui.render(p0)
        } catch (_: Throwable) {
            // Swallow UI errors to avoid interrupting scripting engine.
        }
    }

    fun minStackSize(): Int = minStackSizeValue
    fun maxDistance(): Int = maxDistanceTiles

    fun updateMinStackSize(value: Int) {
        minStackSizeValue = value.coerceIn(1, 10_000)
    }

    fun updateMaxDistance(value: Int) {
        maxDistanceTiles = value.coerceIn(1, 128)
    }

    fun latestStacks(): List<GroundItemSnapshot> = latestSnapshots

    fun currentTestingOption(): TestingOption = testingOption

    fun selectTestingOption(option: TestingOption) {
        if (testingOption == option) {
            return
        }

        testingOption = option
        when (option) {
            TestingOption.NONE -> {
                clearGroundItemResults()
                status = "Awaiting selection"
                log.info("Testing option cleared; awaiting selection.")
            }
            TestingOption.GROUND_ITEMS -> {
                clearGroundItemResults()
                status = "Preparing ground item scan"
                log.info("Ground item testing option selected.")
            }
        }
    }

    fun lastScanAgeMs(): Long = if (lastScanAt == 0L) -1 else System.currentTimeMillis() - lastScanAt

    fun formatCoordinate(coordinate: Coordinate): String =
        "${coordinate.x()}, ${coordinate.y()}, ${coordinate.z()}"

    fun diagnosticsResults(): List<GroundItemQueryTester.TestResult> = latestDiagnostics

    fun lastDiagnosticsAgeMs(): Long = if (lastDiagnosticsCheckAt == 0L) -1 else System.currentTimeMillis() - lastDiagnosticsCheckAt

    fun diagnosticsHeightBuffer(): Int = diagnosticsHeightBufferPx

    fun updateDiagnosticsHeightBuffer(value: Int) {
        diagnosticsHeightBufferPx = value.coerceIn(0, 600)
    }

    fun requestPickup() {
        pickupRequested = true
    }

    fun lastPickupResult(): String? = lastPickupMessage

    fun lastPickupAgeMs(): Long = if (lastPickupAt == 0L) -1 else System.currentTimeMillis() - lastPickupAt

    fun selectedLodestone(): LodestoneType = selectedLodestone

    fun updateSelectedLodestone(value: LodestoneType) {
        selectedLodestone = value
    }

    fun requestLodestoneTeleport() {
        lodestoneTeleportRequest = selectedLodestone
    }

    fun isLodestoneUnlocked(type: LodestoneType): Boolean = try {
        type.isAvailable()
    } catch (t: Throwable) {
        log.debug("Failed to evaluate availability for lodestone {}: {}", type.name, t.message)
        false
    }

    fun lodestoneNetworkOpen(): Boolean = try {
        LodestoneNetwork.isOpen()
    } catch (_: Throwable) {
        false
    }

    fun lastLodestoneTeleportResult(): String? = lastLodestoneMessage

    fun lastLodestoneTeleportAgeMs(): Long = if (lastLodestoneAt == 0L) -1 else System.currentTimeMillis() - lastLodestoneAt

    fun formatLodestoneName(type: LodestoneType): String = type.name
        .lowercase(Locale.ROOT)
        .split('_')
        .joinToString(" ") { part -> part.replaceFirstChar { ch -> ch.uppercaseChar() } }

    private fun maybeRunDiagnostics(groundItems: List<GroundItem>) {
        val now = System.currentTimeMillis()
        if (now - lastDiagnosticsCheckAt < DIAGNOSTIC_INTERVAL_MS) {
            return
        }

        val diagnostics = queryTester.runDiagnostics(groundItems)
        latestDiagnostics = diagnostics
        lastDiagnosticsCheckAt = now

        val failureSignature = diagnostics.filter { it.status == GroundItemQueryTester.Status.FAILED }
            .joinToString(",") { it.name }

        if (failureSignature != lastDiagnosticsFailureSignature) {
            lastDiagnosticsFailureSignature = failureSignature
            if (failureSignature.isNotEmpty()) {
                log.warn("GroundItemQuery diagnostics failing: $failureSignature")
            } else if (diagnostics.isNotEmpty()) {
                log.info("GroundItemQuery diagnostics passing")
            }
        }
    }

    private fun handlePickupRequests(items: List<GroundItem>) {
        if (!pickupRequested) {
            return
        }
        pickupRequested = false
        lastPickupAt = System.currentTimeMillis()

        if (items.isEmpty()) {
            lastPickupMessage = "No matching stacks available."
            log.info("Pickup requested but no matching ground items were found.")
            return
        }

        var attempts = 0
        val failures = mutableListOf<Int>()

        for (item in items.take(MAX_PICKUP_ATTEMPTS)) {
            attempts++
            try {
                item.interact("Take")
                log.info("Attempting to interact with ${item.options}")
            } catch (t: Throwable) {
                log.debug("Pickup interaction failed for ground item ${item.id}: ${t.message}")
            }
        }
    }

    private fun createSnapshot(item: GroundItem): GroundItemSnapshot {
        val name = item.name?.takeIf { it.isNotBlank() } ?: "Item ${item.id}"
        val coordinate = try {
            item.stack?.coordinate
        } catch (_: Throwable) {
            null
        }
        return GroundItemSnapshot(
            id = item.id,
            name = name,
            quantity = item.quantity,
            coordinate = coordinate
        )
    }

    data class GroundItemSnapshot(
        val id: Int,
        val name: String,
        val quantity: Int,
        val coordinate: Coordinate?
    ) {
        val displayLabel: String
            get() = if (quantity > 1) "$name x$quantity" else name
    }

    private fun clearGroundItemResults() {
        lastSummary = null
        lastScanAt = 0L
        latestSnapshots = emptyList()
        latestDiagnostics = emptyList()
        lastDiagnosticsCheckAt = 0L
        lastDiagnosticsFailureSignature = null
        pickupRequested = false
        lastPickupMessage = null
        lastPickupAt = 0L
    }

    private suspend fun processLodestoneTeleportRequest() {
        val target = lodestoneTeleportRequest ?: return
        lodestoneTeleportRequest = null
        val destinationName = formatLodestoneName(target)

        if (!isLodestoneUnlocked(target)) {
            lastLodestoneMessage = "$destinationName is locked."
            lastLodestoneAt = System.currentTimeMillis()
            log.info("Lodestone teleport request ignored: {} is locked.", destinationName)
            return
        }

        status = "Teleporting to $destinationName"
        try {
            val result = target.teleportSuspend(this)
            lastLodestoneMessage = if (result) {
                log.info("Teleporting via lodestone to {}", destinationName)
                "Teleport initiated to $destinationName."
            } else {
                log.warn("Teleport attempt to {} failed to start.", destinationName)
                "Failed to teleport to $destinationName."
            }
        } catch (t: Throwable) {
            lastLodestoneMessage = "Error teleporting to $destinationName."
            log.error("Unexpected error during lodestone teleport to {}: {}", destinationName, t.message, t)
        } finally {
            lastLodestoneAt = System.currentTimeMillis()
        }
    }

    private companion object {
        private const val DIAGNOSTIC_INTERVAL_MS = 5_000L
        private const val MAX_PICKUP_ATTEMPTS = 20
        private const val PICKUP_ACTION = "Take"
    }
}




