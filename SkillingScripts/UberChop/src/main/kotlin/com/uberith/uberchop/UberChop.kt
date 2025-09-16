package com.uberith.uberchop

import com.uberith.api.SuspendableScript
import com.uberith.api.utils.ConfigFiles
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
import java.util.regex.Pattern

@Info(
    name = "UberChop",
    description = "Uber tree chopper",
    version = "0.3.0",
    author = "Uberith"
)
class UberChop : SuspendableScript() {
    // Minimal public fields still referenced by the GUI
    var settings = Settings()
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
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    @Volatile private var uiInitialized: Boolean = false

    private val woodBoxPattern: Pattern = Pattern.compile(".*wood box.*", Pattern.CASE_INSENSITIVE)
    private val logsPattern: Pattern = Pattern.compile(".*logs.*", Pattern.CASE_INSENSITIVE)

    private fun applySettings(s: Settings) {
        settings = s
    }

    private fun configFile() = java.io.File(ConfigFiles.configsDir(), "UberChop.json")

    private fun ensurePersistenceInitialized() { /* no-op with JSON-only persistence */ }

    // Ensure GUI sees persisted targetTree/location before activation
    fun ensureUiSettingsLoaded() {
        if (uiInitialized) return
        loadSettings(logOnFailure = false)
        refreshDerivedPreferences()
        uiInitialized = true
    }

    private fun loadSettings(logOnFailure: Boolean = true): Boolean {
        val cfg = configFile()
        return try {
            val json = ConfigFiles.readModuleSettings("UberChop")
            if (json.isNullOrBlank()) {
                false
            } else {
                val loaded = gson.fromJson(json, Settings::class.java)
                if (loaded != null) {
                    applySettings(loaded)
                    logger.info("Loaded settings from {}", cfg.absolutePath)
                    true
                } else {
                    false
                }
            }
        } catch (e: Exception) {
            if (logOnFailure) {
                logger.warn("Failed to load settings from {}: {}", cfg.absolutePath, e.message)
            }
            false
        }
    }

    private fun persistSettings(successMessage: String? = null, errorMessage: String = "Failed to write JSON settings") {
        settings.savedLocation = location
        try {
            val file = ConfigFiles.writeModuleSettings("UberChop", gson.toJson(settings))
            if (successMessage != null) {
                logger.info(successMessage, file.absolutePath)
            }
        } catch (t: Throwable) {
            logger.error(errorMessage, t)
        }
    }

    fun onSettingsChanged() {
        // Keep settings in sync and persist immediately to JSON
        persistSettings()
    }
    

    override fun onActivation() {
        loadSettings()
        refreshDerivedPreferences()
        persistSettings()
        logger.info("Activated: tree='$targetTree', location='$location' (locations=${treeLocations.size})")
        status = "Active - Preparing"
        phase = Phase.PREPARING
        withdrawWoodBox = settings.withdrawWoodBox
        totalRuntimeMs = 0L
        lastRuntimeUpdateMs = System.currentTimeMillis()
    }

    override fun onDeactivation() {
        status = "Inactive"
        phase = Phase.READY
        persistSettings("Saved settings on deactivation to {}", "Failed to save settings on deactivation")
        logger.info("Deactivated")
    }

    override suspend fun onLoop() {
        updateRuntimeSnapshot()

        when (phase) {
            Phase.READY -> transitionPhase(Phase.PREPARING, "Phase: READY -> PREPARING")
            Phase.PREPARING -> if (handlePreparingPhase()) return
            Phase.BANKING -> if (handleBankingPhase()) return
            Phase.CHOPPING -> if (handleChoppingPhase()) return
        }

        awaitTicks(1)
    }

    private fun updateRuntimeSnapshot() {
        val now = System.currentTimeMillis()
        if (lastRuntimeUpdateMs != 0L) {
            totalRuntimeMs += (now - lastRuntimeUpdateMs)
        }
        lastRuntimeUpdateMs = now
        withdrawWoodBox = settings.withdrawWoodBox
        WCLevel = Stats.WOODCUTTING.currentLevel
    }

    private fun transitionPhase(next: Phase, message: String? = null) {
        if (message != null) {
            logger.info(message)
        }
        phase = next
    }

    private suspend fun handlePreparingPhase(): Boolean {
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
        if (!withdrawWoodBox) {
            transitionPhase(Phase.CHOPPING, "Phase: PREPARING -> CHOPPING")
            return false
        }

        waitForIdle()
        if (moveTowards(prepBank, 10, "bank tile")) return true
        if (openBankIfNeeded()) return true
        if (ensureWoodBoxPresent()) return true

        transitionPhase(Phase.CHOPPING, "Phase: PREPARING -> CHOPPING")
        return false
    }

    private suspend fun handleBankingPhase(): Boolean {
        waitForIdle()
        if (moveTowards(effectiveBankCoordinate(), 10, "bank tile")) return true
        if (openBankIfNeeded()) return true
        if (ensureWoodBoxPresent()) return true

        if (!Backpack.isFull()) {
            transitionPhase(Phase.CHOPPING, "Phase: BANKING -> CHOPPING")
            return false
        }

        findWoodBox()?.let { woodBox ->
            if (Bank.isOpen()) {
                logger.info("Emptying wood box via bank option or backpack fallback")
                Bank.emptyBox(this, woodBox.name, "Empty - logs and bird's nests")
                awaitTicks(1)
            }
        }

        logger.info("Depositing logs {}", logsPattern)
        Bank.depositAll(this, logsPattern)
        awaitTicks(1)
        return true
    }

    private suspend fun handleChoppingPhase(): Boolean {
        if (Backpack.isFull()) {
            val woodBox = findWoodBox()
            if (woodBox != null) {
                Backpack.interact(woodBox, "Fill")
                awaitTicks(1)
                if (Backpack.isFull()) {
                    phase = Phase.BANKING
                    return true
                }
            } else {
                phase = Phase.BANKING
                return true
            }
        }

        status = "Locating nearest $targetTree"

        waitForIdle()

        if (moveTowards(effectiveChopCoordinate(), 40, "chop tile")) return true

        if (Trees.chop(targetTree).nearest()) {
            status = "Chopping $targetTree"
            logger.info("Chop target: '$targetTree'")
            awaitTicks(1)
            return true
        }

        status = "No $targetTree nearby"
        logger.debug("No target '$targetTree' nearby")
        return false
    }

    private suspend fun waitForIdle() {
        val animationId = LocalPlayer.self()?.animationId ?: -1
        if (animationId != -1) {
            awaitUntil { (LocalPlayer.self()?.animationId ?: -1) == -1 }
        }
        if (LocalPlayer.self()?.isMoving == true) {
            awaitUntil { LocalPlayer.self()?.isMoving != true }
        }
    }

    private suspend fun moveTowards(target: Coordinate?, within: Int, description: String): Boolean {
        val tile = target ?: return false
        if (Coordinates.isPlayerWithinRadius(tile, within)) return false
        logger.info("Walking to $description $tile")
        val walkCoordinates = Coordinates.randomReachableNear(tile, 10, 32)
        Traverse.walkTo(walkCoordinates, true)
        awaitTicks(1)
        return true
    }

    private suspend fun openBankIfNeeded(): Boolean {
        if (Bank.isOpen()) return false
        val opened = Bank.open(this)
        logger.info("Bank.open() -> {}", opened)
        awaitTicks(1)
        return true
    }

    private suspend fun ensureWoodBoxPresent(): Boolean {
        if (!withdrawWoodBox || Backpack.contains(woodBoxPattern)) return false
        Bank.withdraw(woodBoxPattern, 1)
        awaitTicks(1)
        return true
    }

    private fun findWoodBox() = Backpack.getItem({ name, item -> item.toString().contains(name, true) }, "wood box")

    private fun refreshDerivedPreferences() {
        val treeIndex = settings.savedTreeType.coerceIn(0, TreeTypes.ALL.lastIndex)
        targetTree = TreeTypes.ALL[treeIndex]
        location = resolvedLocationForTree(targetTree, settings.savedLocation)
        settings.savedLocation = location
    }

    private fun resolvedLocationForTree(tree: String, saved: String): String {
        val valid = validLocationsForTree(tree)
        if (saved.isNotBlank() && valid.any { it.name == saved }) {
            return saved
        }
        return valid.firstOrNull()?.name ?: treeLocations.firstOrNull()?.name.orEmpty()
    }

    private fun validLocationsForTree(tree: String): List<TreeLocation> {
        val desired = tree.lowercase()
        return treeLocations.filter { loc ->
            loc.availableTrees.any { candidate ->
                val lower = candidate.lowercase()
                lower.contains(desired) || desired.contains(lower)
            }
        }
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
        try {
            ensurePersistenceInitialized()
        } catch (_: Throwable) { }

        // Load persisted settings before reading them
        loadSettings()
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
