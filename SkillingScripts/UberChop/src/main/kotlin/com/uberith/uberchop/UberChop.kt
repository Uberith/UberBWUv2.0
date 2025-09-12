package com.uberith.uberchop

import com.uberith.api.SuspendableScript
import net.botwithus.scripts.Info
import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.entities.SceneObject
import net.botwithus.rs3.world.World
import java.util.Locale

@Info(
    name = "UberChop",
    description = "Minimal tree chopper",
    version = "0.3.0",
    author = "Uberith"
)
class UberChop : SuspendableScript() {

    // Minimal public fields still referenced by the GUI
    val settings = Settings()
    var targetTree: String = "Tree"
    var location: String = ""
    var bankWhenFull: Boolean = false
    var logsChopped: Int = 0
    var status: String = "Idle"
    @Volatile var phase: Phase = Phase.READY

    private var totalRuntimeMs: Long = 0L
    private var lastRuntimeUpdateMs: Long = 0L

    override fun onActivation() {
        val idx = settings.savedTreeType.coerceIn(0, TreeTypes.ALL.size - 1)
        targetTree = TreeTypes.ALL[idx]
        status = "Active - Preparing"
        phase = Phase.PREPARING
        totalRuntimeMs = 0L
        lastRuntimeUpdateMs = System.currentTimeMillis()
    }

    override fun onDeactivation() {
        status = "Inactive"
        phase = Phase.READY
    }

    override suspend fun onLoop() {
        // runtime accounting
        val now = System.currentTimeMillis()
        if (lastRuntimeUpdateMs != 0L) totalRuntimeMs += (now - lastRuntimeUpdateMs)
        lastRuntimeUpdateMs = now

        when (phase) {
            Phase.READY -> phase = Phase.PREPARING
            Phase.PREPARING -> { phase = Phase.CHOPPING }
            Phase.BANKING -> { phase = Phase.CHOPPING }
            Phase.CHOPPING -> {
                val tree = pickNearestTree(targetTree)
                if (tree != null) {
                    status = "Chopping ${tree.name}"
                    val options = tree.getOptions()
                    val idx = options.indexOfFirst { it != null && (it.equals("Chop", true) || it!!.lowercase(Locale.ROOT).contains("chop")) }
                    if (idx >= 0) {
                        if (tree.interact(idx)) {
                            logsChopped++
                        }
                    }
                } else {
                    status = "No $targetTree nearby"
                }
            }
        }

        awaitTicks(1)
    }

    private fun pickNearestTree(name: String): SceneObject? {
        val me = LocalPlayer.self() ?: return null
        val n = name.lowercase(Locale.ROOT)
        return World.getSceneObjects()
            .asSequence()
            .filter { so ->
                so.name.lowercase(Locale.ROOT).contains(n) &&
                so.getOptions().any { it != null && it.lowercase(Locale.ROOT).contains("chop") }
            }
            .minByOrNull { so -> me.distanceTo(so.coordinate) }
    }

    fun formattedRuntime(): String {
        val ms = totalRuntimeMs.coerceAtLeast(0L)
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    fun logsPerHour(): Int {
        val ms = totalRuntimeMs.coerceAtLeast(1L)
        val perMs = logsChopped.toDouble() / ms.toDouble()
        return (perMs * 3_600_000.0).toInt()
    }

    // Minimal location catalog so GUI can render
    data class TreeLocation(
        val name: String,
        val availableTrees: List<String>,
        val chop: net.botwithus.rs3.world.Coordinate? = null,
        val bank: net.botwithus.rs3.world.Coordinate? = null
    )

    val treeLocations: List<TreeLocation> = listOf(
        TreeLocation(name = "Default", availableTrees = TreeTypes.ALL)
    )
}

