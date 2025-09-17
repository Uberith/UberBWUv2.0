package com.uberith.api.game.skills.firemaking

import com.uberith.api.game.inventory.Backpack
import net.botwithus.rs3.item.InventoryItem
import org.slf4j.Logger

/**
 * Helpers for lighting logs from the backpack.
 */
object Firemaking {

    enum class Result {
        SUCCESS,
        FAILED_INTERACTION,
        FAILED_NO_PROGRESS
    }

    suspend fun burnFirstLog(
        logger: Logger,
        logs: () -> List<InventoryItem>,
        currentLogCount: () -> Int,
        interactWithLog: suspend (InventoryItem) -> Boolean,
        awaitTicks: suspend (Int) -> Unit
    ): Result {
        val items = logs()
        if (items.isEmpty()) {
            return Result.FAILED_NO_PROGRESS
        }
        val before = items.size
        val logItem = items.first()
        if (!interactWithLog(logItem)) {
            logger.info("Failed to light logs in inventory; banking instead")
            return Result.FAILED_INTERACTION
        }
        repeat(10) {
            awaitTicks(1)
            if (currentLogCount() < before) {
                logger.info("Burned logs to free backpack space")
                return Result.SUCCESS
            }
        }
        logger.info("Burn attempt did not consume any logs; banking instead")
        return Result.FAILED_NO_PROGRESS
    }
}
