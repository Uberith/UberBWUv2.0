package com.uberith.uberminer

import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.rs3.item.GroundItem
import net.botwithus.rs3.item.Item
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.xapi.query.GroundItemQuery
import org.slf4j.LoggerFactory
import kotlin.jvm.Volatile

@Info(
    name = "UberMiner",
    description = "Scans ground items and logs nearby stacks.",
    version = "0.1.0",
    author = "Uberith"
)
class UberMiner : SuspendableScript() {

    private val logger = LoggerFactory.getLogger(UberMiner::class.java)
    @Volatile private var status: String = "Idle"
    // Track the previous summary so INFO logs only fire when the view meaningfully changes
    private var lastSummary: String? = null

    override fun getStatus(): String? = status

    override fun onActivation() {
        status = "Activating"
        logger.info("UberMiner activated. Beginning ground item scan.")
    }

    override fun onDeactivation() {
        status = "Inactive"
        logger.info("UberMiner deactivated.")
        lastSummary = null
    }

    override suspend fun onLoop() {
        val results = GroundItemQuery.newQuery().results()
        val nearest = results.firstOrNull()
        val stackCount = results.size()

        if (nearest == null) {
            status = "No ground items"
            if (lastSummary != null) {
                logger.info("No ground items detected nearby.")
                lastSummary = null
            } else {
                logger.debug("No ground items detected nearby.")
            }
            awaitTicks(5)
            return
        }

        status = "Tracking $stackCount stacks"
        awaitTicks(5)
    }


    private fun describeItem(item: Item): String {
        val name = item.name.ifBlank { "Item ${item.id}" }
        val quantity = item.quantity
        return if (quantity > 1) "$name x$quantity" else name
    }

    private fun formatCoordinate(coordinate: Coordinate): String =
        "${coordinate.x()}, ${coordinate.y()}, ${coordinate.z()}"
}
