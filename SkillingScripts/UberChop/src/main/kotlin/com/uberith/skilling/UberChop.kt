package com.uberith.skilling

import com.uberith.api.SuspendableScript
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.rs3.world.World
import net.botwithus.rs3.entities.SceneObject
import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.inventories.InventoryManager
import java.util.Locale
import org.slf4j.LoggerFactory

@Info(
    name = "UberChop",
    description = "Smart RS3 woodcutting: targets trees, chops, optional banking.",
    version = "0.1.0",
    author = "Uberith"
)
class UberChop : SuspendableScript() {

    // Configurable via GUI
    var targetTree: String = "Tree" // e.g., Tree, Oak, Willow, Yew, Magic
    var bankWhenFull: Boolean = false

    // Runtime stats
    var logsChopped: Int = 0
    var status: String = "Idle"
    private var startEpochMs: Long = 0L

    // Batch-job style phases and controls
    enum class Phase { READY, PREPARING, CHOPPING, BANKING, PAUSED }
    @Volatile var phase: Phase = Phase.READY
    @Volatile private var startRequested: Boolean = false
    @Volatile var userPaused: Boolean = false

    private val gui = UberChopGUI(this)
    private val log = LoggerFactory.getLogger(UberChop::class.java)

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        gui.render(workspace)
    }

    override fun onActivation() {
        super.onActivation()
        log.info("UberChop activated")
        status = "Active - Ready"
        phase = Phase.READY
        startRequested = false
        if (startEpochMs == 0L) startEpochMs = System.currentTimeMillis()
    }

    override fun onDeactivation() {
        super.onDeactivation()
        log.info("UberChop deactivated")
        status = "Inactive"
        phase = Phase.PAUSED
        startRequested = false
    }

    override suspend fun onLoop() {
        // Respect user pause
        if (userPaused) {
            phase = Phase.PAUSED
            status = "Paused"
            awaitTicks(1)
            return
        }

        // State machine: READY -> PREPARING -> CHOPPING (-> BANKING when applicable)
        when (phase) {
            Phase.READY -> {
                status = "Ready to start. Configure and press Start."
                if (startRequested) {
                    phase = Phase.PREPARING
                }
                awaitTicks(1)
            }
            Phase.PREPARING -> {
                // Basic readiness checks (expand later with navigation, gear, etc.)
                val t = nearestTree(targetTree)
                selectedLocation()?.let { loc ->
                    status = "Preparing: head to ${'$'}{loc.name} chop tile"
                }
                if (t == null) {
                    status = "Preparing: looking for ${'$'}{targetTree} nearby"
                    awaitTicks(2)
                } else {
                    status = "Preparing: ${'$'}{t.name} found"
                    awaitTicks(1)
                    phase = Phase.CHOPPING
                }
            }
            Phase.BANKING -> {
                // Basic bank interaction: find nearby bank object and interact
                val bank = nearestBank()
                if (bank == null) {
                    status = "Banking: no bank nearby"
                    awaitTicks(2)
                    // After attempt, return to chopping
                    phase = Phase.CHOPPING
                } else {
                    status = "Banking: interacting with ${'$'}{bank.name}"
                    val options = bank.getOptions()
                    val idx = options.indexOfFirst { it != null && it.lowercase(Locale.ROOT).contains("bank") }
                    val ok = if (idx >= 0) bank.interact(idx) else false
                    awaitTicks(3)
                    status = if (ok == true) "Banked (stub)" else "Bank failed (stub)"
                    awaitTicks(1)
                    phase = Phase.CHOPPING
                }
            }
            Phase.CHOPPING -> {
                // Optional: bank if full
                if (bankWhenFull && isAnyInventoryFull()) {
                    phase = Phase.BANKING
                    return
                }

                // Find nearest target tree by name
                val tree = nearestTree(targetTree)
                if (tree == null) {
                    status = "No ${targetTree} nearby"
                    awaitTicks(2)
                    // Go back to preparing to reacquire
                    phase = Phase.PREPARING
                    return
                }

                // Interact with a "Chop" option
                status = "Chopping ${tree.name}"
                val options = tree.getOptions()
                val idx = options.indexOfFirst { it != null && it.lowercase(Locale.ROOT).contains("chop") }
                val interacted = if (idx >= 0) tree.interact(idx) else false
                if (interacted == false) {
                    status = "Failed to interact; retrying"
                    awaitTicks(2)
                    return
                }

                // Wait briefly; future: wait on animation or inventory event
                awaitTicks(3)

                logsChopped++
                status = "Chopped (${logsChopped})"
                awaitTicks(1)
            }
            Phase.PAUSED -> {
                status = "Paused"
                awaitTicks(1)
            }
        }
    }

    fun requestStart() {
        startRequested = true
        if (startEpochMs == 0L) startEpochMs = System.currentTimeMillis()
    }

    fun pause() {
        userPaused = true
        phase = Phase.PAUSED
    }

    fun resume() {
        userPaused = false
        if (phase == Phase.PAUSED) phase = Phase.PREPARING
    }

    private fun nearestTree(name: String): SceneObject? {
        val player = LocalPlayer.self() ?: return null
        val nLower = name.lowercase(Locale.ROOT)
        return World.getSceneObjects()
            .asSequence()
            .filter { (it.isHidden() == false) && it.name.lowercase(Locale.ROOT).contains(nLower) }
            .minByOrNull { player.distanceTo(it.coordinate) }
    }

    fun peekNearestTree(): SceneObject? = nearestTree(targetTree)

    private fun nearestBank(): SceneObject? {
        val player = LocalPlayer.self() ?: return null
        return World.getSceneObjects()
            .asSequence()
            .filter { so ->
                if (so.isHidden() == true) return@filter false
                val name = so.name.lowercase(Locale.ROOT)
                val byName = name.contains("bank")
                val byOpt = so.getOptions().any { it != null && it.lowercase(Locale.ROOT).contains("bank") }
                byName || byOpt
            }
            .minByOrNull { player.distanceTo(it.coordinate) }
    }

    // Minimal location mapping from external example (subset; tiles omitted for now)
    data class TreeLocation(val name: String)
    private val locations = listOf(
        TreeLocation("Burthorpe"),
        TreeLocation("Draynor Village"),
        TreeLocation("Edgeville"),
        TreeLocation("Lumbridge"),
        TreeLocation("Seers' Village"),
    )
    var selectedLocationIndex: Int = 0
    fun locationNames(): List<String> = locations.map { it.name }
    private fun selectedLocation(): TreeLocation? = locations.getOrNull(selectedLocationIndex)

    // Stats helpers
    fun runtimeMs(): Long {
        val start = startEpochMs
        return if (start == 0L) 0L else (System.currentTimeMillis() - start)
    }
    fun formattedRuntime(): String {
        val ms = runtimeMs()
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }
    fun logsPerHour(): Int {
        val ms = runtimeMs().coerceAtLeast(1L)
        val perMs = logsChopped.toDouble() / ms.toDouble()
        return (perMs * 3_600_000.0).toInt()
    }

    private fun isAnyInventoryFull(): Boolean {
        return InventoryManager.getInventories().any { inv ->
            try {
                inv.isFull()
            } catch (_: Throwable) {
                false
            }
        }
    }
}
