package com.uberith.uberminer

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
    name = "UberMiner",
    description = "Scans ground items and logs nearby stacks.",
    version = "0.1.0",
    author = "Uberith"
)
class UberMiner : SuspendableScript() {

    private val log = LoggerFactory.getLogger(UberMiner::class.java)
    @Volatile private var status: String = "Idle"
    @Volatile private var lastSummary: String? = null
    @Volatile private var lastScanAt: Long = 0L
    @Volatile private var latestSnapshots: List<GroundItemSnapshot> = emptyList()
    @Volatile private var minStackSizeValue: Int = 1
    @Volatile private var maxDistanceTiles: Int = 12

    private val gui by lazy { UberMinerGUI(this) }

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
        log.info("UberMiner activated. Beginning ground item scan.")
    }

    override fun onDeactivation() {
        status = "Inactive"
        log.info("UberMiner deactivated.")
        lastSummary = null
        latestSnapshots = emptyList()
    }

    override suspend fun onLoop() {
        val scanMinStack = minStackSizeValue
        val scanMaxDistance = maxDistanceTiles

        val results = GroundItemQuery.newQuery()
            .distance(scanMaxDistance.toDouble())
            .results()

        val snapshots = mutableListOf<GroundItemSnapshot>()
        for (item in results) {
            if (item.quantity < scanMinStack) continue
            snapshots.add(createSnapshot(item))
        }

        lastScanAt = System.currentTimeMillis()
        if (snapshots.isEmpty()) {
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

        status = "Tracking ${snapshots.size} stacks"
        val sortedSnapshots = snapshots.sortedByDescending { it.quantity }
        latestSnapshots = sortedSnapshots.take(25)

        val summary = sortedSnapshots.take(5).joinToString { it.displayLabel }
        if (summary != lastSummary) {
            log.info("Nearby stacks (min qty $scanMinStack, radius $scanMaxDistance): $summary")
            lastSummary = summary
        } else {
            log.debug("Tracking ${snapshots.size} stacks (min qty $scanMinStack).")
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
}
