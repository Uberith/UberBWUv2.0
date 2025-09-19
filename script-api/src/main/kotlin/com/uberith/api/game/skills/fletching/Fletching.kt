package com.uberith.api.game.skills.fletching

import net.botwithus.rs3.interfaces.Component
import net.botwithus.rs3.item.InventoryItem
import net.botwithus.xapi.query.ComponentQuery
import org.slf4j.Logger

/**
 * Helpers for invoking the backpack fletching flow.
 */
object Fletching {

    data class Result(val consumed: Boolean, val startedProduction: Boolean)

    suspend fun fletchFirstLog(
        logger: Logger,
        logs: () -> List<InventoryItem>,
        currentLogCount: () -> Int,
        logFletchOptions: Array<String>,
        productionOptionKeywords: Array<String>,
        productionInterfaceChunks: List<IntArray>,
        interactWithLog: suspend (InventoryItem, String) -> Boolean,
        awaitTicks: suspend (Int) -> Unit
    ): Result {
        val logs = logs()
        if (logs.isEmpty()) {
            return Result(consumed = false, startedProduction = false)
        }
        val before = logs.size
        val logItem = logs.first()
        if (!logFletchOptions.any { option -> interactWithLog(logItem, option) }) {
            logger.info("Failed to start fletching; banking instead")
            return Result(consumed = false, startedProduction = false)
        }
        awaitTicks(1)
        val productionStarted = startProductionIfPresent(productionOptionKeywords, productionInterfaceChunks, logger)
        val maxWait = if (productionStarted) 25 else 15
        repeat(maxWait) {
            awaitTicks(1)
            if (currentLogCount() < before) {
                logger.info("Fletched logs to free backpack space")
                return Result(consumed = true, startedProduction = productionStarted)
            }
        }
        logger.info("Fletching attempt did not consume logs; banking instead")
        return Result(consumed = false, startedProduction = productionStarted)
    }

    private fun startProductionIfPresent(
        productionOptionKeywords: Array<String>,
        productionInterfaceChunks: List<IntArray>,
        logger: Logger
    ): Boolean {
        val options = productionOptionKeywords
        for (chunk in productionInterfaceChunks) {
            try {
                val optionMatch = ComponentQuery.newQuery(*chunk)
                    .option({ needle, haystack -> needle.equals(haystack as String?, ignoreCase = true) }, *options)
                    .results()
                    .firstOrNull()
                if (optionMatch != null && optionMatch.clickPreferred(*options, logger = logger)) {
                    return true
                }
                val textMatch = ComponentQuery.newQuery(*chunk)
                    .text({ needle, haystack -> needle.equals(haystack as String?, ignoreCase = true) }, *options)
                    .results()
                    .firstOrNull()
                if (textMatch != null && textMatch.clickPreferred(*options, logger = logger)) {
                    return true
                }
            } catch (t: Throwable) {
                logger.debug("Failed to evaluate production interface chunk {}: {}", chunk.joinToString(), t.message)
            }
        }
        return false
    }

    private fun Component.clickPreferred(vararg preferred: String, logger: Logger): Boolean {
        val opts = options?.filterNotNull()?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val target = preferred.firstOrNull { pref -> opts.any { it.equals(pref, ignoreCase = true) } }
            ?: opts.firstOrNull { option ->
                option.contains("make", ignoreCase = true) || option.contains("start", ignoreCase = true) || option.contains("craft", ignoreCase = true)
            }
            ?: opts.firstOrNull()
        if (target != null) {
            return try {
                interact(target) > 0
            } catch (t: Throwable) {
                logger.warn("Production component interaction failed: {}", t.message)
                false
            }
        }
        return false
    }
}
