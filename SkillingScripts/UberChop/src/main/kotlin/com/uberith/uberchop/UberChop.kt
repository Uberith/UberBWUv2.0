package com.uberith.uberchop

import com.uberith.api.SuspendableScript
import com.uberith.api.game.inventory.Backpack
import com.uberith.api.game.inventory.Bank
import com.uberith.api.game.skills.woodcutting.Trees
import com.uberith.api.game.world.Coordinates
import com.uberith.api.game.world.Traverse
import com.uberith.uberchop.gui.UberChopGUI
import net.botwithus.rs3.entities.LocalPlayer
import net.botwithus.rs3.stats.Stats
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.random.Random

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
    var withdrawWoodBox: Boolean = false
    var logsChopped: Int = 0
    var status: String = "Idle"
    @Volatile var phase: Phase = Phase.READY
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    private var totalRuntimeMs: Long = 0L
    private var lastRuntimeUpdateMs: Long = 0L
    var WCLevel = Stats.WOODCUTTING.currentLevel

    

    override fun onActivation() {
        val idx = settings.savedTreeType.coerceIn(0, TreeTypes.ALL.size - 1)
        targetTree = TreeTypes.ALL[idx]
        // Initialize location from saved preference if valid for current tree
        val curTreeLower = targetTree.lowercase()
        val validForTree = treeLocations.filter { loc ->
            loc.availableTrees.any { at ->
                val a = at.lowercase(); a.contains(curTreeLower) || curTreeLower.contains(a)
            }
        }
        val saved = settings.savedLocation
        location = if (saved.isNotBlank() && validForTree.any { it.name == saved }) saved
            else validForTree.firstOrNull()?.name ?: treeLocations.firstOrNull()?.name.orEmpty()
        settings.savedLocation = location
        logger.info("Activated: tree='$targetTree', location='$location' (locations=${treeLocations.size})")
        status = "Active - Preparing"
        phase = Phase.PREPARING
        // sync cached flags from settings
        withdrawWoodBox = settings.withdrawWoodBox
        totalRuntimeMs = 0L
        lastRuntimeUpdateMs = System.currentTimeMillis()
    }

    override fun onDeactivation() {
        status = "Inactive"
        phase = Phase.READY
        logger.info("Deactivated")
    }

    override suspend fun onLoop() {
        val now = System.currentTimeMillis()
        if (lastRuntimeUpdateMs != 0L) totalRuntimeMs += (now - lastRuntimeUpdateMs)
        lastRuntimeUpdateMs = now
        // sync cached flags from settings to reflect GUI changes
        withdrawWoodBox = settings.withdrawWoodBox
        WCLevel = Stats.WOODCUTTING.currentLevel

        when (phase) {
            Phase.READY -> {
                logger.info("Phase: READY -> PREPARING")
                phase = Phase.PREPARING
            }
            Phase.PREPARING -> {
                // Configuration summary before moving to active loop
                logger.info("Preparing: target='{}', location='{}'", targetTree, location)
                logger.info(
                    "Preparing: withdrawWoodBox={}, bankWhenFull={}, worldHop={}, notepaper={}, crystallise={}, rotation={}, pickupNests={}",
                    withdrawWoodBox,
                    bankWhenFull,
                    settings.enableWorldHopping,
                    settings.useMagicNotepaper,
                    settings.useCrystallise,
                    settings.enableTreeRotation,
                    settings.pickupNests
                )
                val prepChop = effectiveChopCoordinate()
                val prepBank = effectiveBankCoordinate()
                logger.info("Preparing: chopTile={}, bankTile={}", prepChop ?: "none", prepBank ?: "none")
                logger.info("Phase: PREPARING -> CHOPPING")
                if (!withdrawWoodBox) {
                    phase = Phase.CHOPPING
                    return
                }

                // Wait to be idle only if currently animating, and handle null player safely
                val currentAnim = LocalPlayer.self()?.animationId ?: -1
                if (currentAnim != -1) {
                    awaitUntil { (LocalPlayer.self()?.animationId ?: -1) == -1 }
                }

                // Wait to not be moving
                if (LocalPlayer.self().isMoving) {
                    awaitUntil { !LocalPlayer.self().isMoving }
                }

                // Walk near configured bank tile if present
                val bankTile = effectiveBankCoordinate()
                if (bankTile != null && !Coordinates.isPlayerWithinRadius(bankTile, 10)) {
                    logger.info("Walking to bank tile $bankTile")
                    val walkCoordinates = Coordinates.randomReachableNear(bankTile, 10, 32)
                    Traverse.walkTo(walkCoordinates, true)
                    awaitTicks(1)
                    return
                }

                // Open the bank if not open already
                if (!Bank.isOpen()) {
                    val opened = Bank.open(this)
                    logger.info("Bank.open() -> $opened")
                    awaitTicks(1)
                    return
                }

                val woodBoxPat = java.util.regex.Pattern.compile(".*wood box.*", java.util.regex.Pattern.CASE_INSENSITIVE)
                if (!Backpack.contains(woodBoxPat) && withdrawWoodBox) {
                    Bank.withdraw(woodBoxPat, 1)
                    awaitTicks(1)
                    return
                }

                phase = Phase.CHOPPING
            }
            Phase.BANKING -> {
                // Wait to be idle only if currently animating, and handle null player safely
                val currentAnim = LocalPlayer.self()?.animationId ?: -1
                if (currentAnim != -1) {
                    awaitUntil { (LocalPlayer.self()?.animationId ?: -1) == -1 }
                }

                // Wait to not be moving
                if (LocalPlayer.self().isMoving) {
                    awaitUntil { !LocalPlayer.self().isMoving }
                }

                // Walk near configured bank tile if present
                val bankTile = effectiveBankCoordinate()
                if (bankTile != null && !Coordinates.isPlayerWithinRadius(bankTile, 10)) {
                    logger.info("Walking to bank tile $bankTile")
                    val walkCoordinates = Coordinates.randomReachableNear(bankTile, 10, 32)
                    Traverse.walkTo(walkCoordinates, true)
                    awaitTicks(1)
                    return
                }

                // Open the bank if not open already
                if (!Bank.isOpen()) {
                    val opened = Bank.open(this)
                    logger.info("Bank.open() -> $opened")
                    awaitTicks(1)
                    return
                }

                // If backpack is full, deposit, otherwise switch to chopping
                if (!Backpack.isFull()) {
                    phase = Phase.CHOPPING
                } else {
                    val woodBox = Backpack.getItem({ n, h -> h.toString().contains(n, true) }, "wood box")
                    if (woodBox != null && Bank.isOpen()) {
                        logger.info("Emptying wood box via bank option or backpack fallback")
                        Bank.emptyBox(this, woodBox.name, "Empty - logs and bird's nests")
                        awaitTicks(1)
                    }
                    val logsPat = java.util.regex.Pattern.compile(".*logs.*", java.util.regex.Pattern.CASE_INSENSITIVE)
                    logger.info("Depositing logs $logsPat")
                    Bank.depositAll(this, logsPat)
                    awaitTicks(1)
                }

            }
            Phase.CHOPPING -> {
                if (Backpack.isFull()) {
                    val woodBox = Backpack.getItem({ n, h -> h.toString().contains(n, true) }, "wood box")
                    if (woodBox != null) {
                        woodBox.let { Backpack.interact(it, "Fill") }
                        awaitTicks(1)
                        if (Backpack.isFull()) {
                            phase = Phase.BANKING
                            return
                        }
                    } else {
                        phase = Phase.BANKING
                        return
                    }
                }

                // Update status before any waits so UI doesn't look stuck
                status = "Locating nearest $targetTree"

                // Wait to be idle only if currently animating, and handle null player safely
                val currentAnim = LocalPlayer.self()?.animationId ?: -1
                if (currentAnim != -1) {
                    awaitUntil { (LocalPlayer.self()?.animationId ?: -1) == -1 }
                }

                if (LocalPlayer.self().isMoving) {
                    awaitUntil { !LocalPlayer.self().isMoving }
                }

                // Move towards configured chop tile if present and not nearby
                effectiveChopCoordinate()?.let { chopTile ->
                    if (!Coordinates.isPlayerWithinRadius(chopTile, 20)) {
                        logger.info("Walking to chop tile $chopTile")
                        val walkCoordinates = Coordinates.randomReachableNear(chopTile, 10, 32)
                        Traverse.walkTo(walkCoordinates, true)
                        awaitTicks(1)
                        return
                    }
                }

                val tree = Trees.nearest(targetTree)

                if (tree != null) {
                    status = "Chopping ${tree.name}"
                    logger.info("Chop target: '${tree.name}'")
                    Trees.chop(tree)
                    awaitTicks(1)
                } else {
                    status = "No $targetTree nearby"
                    logger.debug("No target '$targetTree' nearby")
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

    // Location catalog embedded in code
    var treeLocations: List<TreeLocation> = LocationCatalog.ALL

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

    // Helper accessors for selected location and effective tiles

    private fun selectedLocation(): TreeLocation? = treeLocations.firstOrNull { it.name == location }

    private fun effectiveChopCoordinate(): Coordinate? {
        val locName = location
        // Custom override takes precedence
        settings.customLocations[locName]?.let { c ->
            val x = c.chopX; val y = c.chopY; val z = c.chopZ
            if (x != null && y != null && z != null) return Coordinate(x, y, z)
        }
        return selectedLocation()?.chop
    }

    private fun effectiveBankCoordinate(): Coordinate? {
        val locName = location
        // Custom override takes precedence
        settings.customLocations[locName]?.let { c ->
            val x = c.bankX; val y = c.bankY; val z = c.bankZ
            if (x != null && y != null && z != null) return Coordinate(x, y, z)
        }
        return selectedLocation()?.bank
    }
}
