package com.uberith.uberchop

import com.uberith.api.SuspendableScript
import com.uberith.api.game.inventories.Bank
import com.uberith.api.game.skills.woodcutting.Trees
import net.botwithus.scripts.Info
import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.entities.SceneObject
import net.botwithus.rs3.world.World
import java.util.Locale
import net.botwithus.ui.workspace.Workspace
import com.uberith.uberchop.gui.UberChopGUI

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
        val now = System.currentTimeMillis()
        if (lastRuntimeUpdateMs != 0L) totalRuntimeMs += (now - lastRuntimeUpdateMs)
        lastRuntimeUpdateMs = now

        when (phase) {
            Phase.READY -> phase = Phase.PREPARING
            Phase.PREPARING -> { phase = Phase.CHOPPING }
            Phase.BANKING -> {
                phase = Phase.CHOPPING
                Bank.open(this)
            }
            Phase.CHOPPING -> {
                status = "Locating nearest $targetTree"
                val tree = Trees.nearest(targetTree)
                if (tree != null) {
                    status = "Chopping $tree"
                    Trees.chop(tree)
                } else {
                    status = "No $targetTree nearby"
                }
            }
        }

        awaitTicks(1)
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

    // GUI instance
    private val gui by lazy { UberChopGUI(this) }

    override fun onInitialize() {
        super.onInitialize()
        // Prepare GUI resources
        try { gui.preload() } catch (_: Throwable) { }
    }

    override fun onDraw(workspace: Workspace) {
        // Render the script configuration UI
        try { gui.render(workspace) } catch (_: Throwable) { }
    }
}
