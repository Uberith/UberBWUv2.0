package com.uberith.ubertestingutil

import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.rs3.item.GroundItem
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.query.GroundItemQuery
import net.botwithus.xapi.script.ui.interfaces.BuildableUI
import org.slf4j.LoggerFactory
import kotlin.jvm.Volatile

@Info(
    name = "UberTestingUtil",
    description = "Scans ground items and logs nearby stacks.",
    version = "0.1.0",
    author = "Uberith"
)
class UberTestingUtil : SuspendableScript() {

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
        status = "Activating"
        log.info("UberTestingUtil activated. Beginning ground item scan.")
    }

    override fun onDeactivation() {
        status = "Inactive"
        log.info("UberTestingUtil deactivated.")
        lastSummary = null
        latestSnapshots = emptyList()
        latestDiagnostics = emptyList()
        lastDiagnosticsCheckAt = 0L
        lastDiagnosticsFailureSignature = null
        diagnosticsHeightBufferPx = 0
        pickupRequested = false
        lastPickupMessage = null
        lastPickupAt = 0L
    }

    override suspend fun onLoop() {
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
        var successes = 0
        val failures = mutableListOf<Int>()

        for (item in items.take(MAX_PICKUP_ATTEMPTS)) {
            attempts++
            val interacted = try {
                (item.interact(PICKUP_ACTION) as? Boolean) == true
            } catch (t: Throwable) {
                log.debug("Pickup interaction failed for ground item ${item.id}: ${t.message}")
                false
            }
            if (interacted) {
                successes++
            } else {
                failures += item.id
            }
        }

        if (items.size > MAX_PICKUP_ATTEMPTS) {
            lastPickupMessage = "Interacted with $attempts stack(s); $successes succeeded (limited)."
        } else {
            lastPickupMessage = "Interacted with $attempts stack(s); $successes succeeded."
        }

        if (successes > 0) {
            log.info("Pickup attempt succeeded for $successes/$attempts stack(s).")
        } else {
            log.warn("Pickup attempt failed for $attempts stack(s).")
        }

        if (failures.isNotEmpty()) {
            log.debug("Failed ground item ids: ${failures.joinToString()}")
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

    private companion object {
        private const val DIAGNOSTIC_INTERVAL_MS = 5_000L
        private const val MAX_PICKUP_ATTEMPTS = 20
        private const val PICKUP_ACTION = "Take"
    }
}

