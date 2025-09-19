package com.uberith.uberchop

import net.botwithus.kxapi.script.SuspendableScript
import com.google.gson.JsonObject
import com.uberith.api.utils.ConfigStore
import com.uberith.api.game.skills.woodcutting.Trees
import com.uberith.api.game.world.Coordinates
import com.uberith.uberchop.gui.UberChopGUI
import net.botwithus.kxapi.game.inventory.BackpackExtensions
import net.botwithus.rs3.stats.Stats
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.game.inventory.Backpack
import net.botwithus.xapi.game.inventory.Bank
import net.botwithus.xapi.game.traversal.Traverse
import net.botwithus.xapi.script.ui.interfaces.BuildableUI
import kotlin.jvm.JvmName
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
    @get:JvmName("getStatusText") var status: String = "Idle"
    @Volatile var phase: Phase = Phase.READY
    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)
    private var totalRuntimeMs: Long = 0L
    private var lastRuntimeUpdateMs: Long = 0L
    var WCLevel = Stats.WOODCUTTING.currentLevel
    private val settingsStore = ConfigStore<Settings>("UberChop", Settings::class.java)
    @Volatile private var uiInitialized: Boolean = false

    override fun getStatus(): String? = status
    private val logsPattern: Pattern = Pattern.compile(".*logs.*", Pattern.CASE_INSENSITIVE)
    private val woodBoxPattern: Pattern = Pattern.compile(".*wood box.*", Pattern.CASE_INSENSITIVE)

    private fun applySettings(s: Settings) {
        settings = s
    }

    // Ensure GUI sees persisted targetTree/location before activation
    fun ensureUiSettingsLoaded() {
        if (uiInitialized) return
        loadSettings(logOnFailure = false)
        refreshDerivedPreferences()
        uiInitialized = true
    }

    private fun loadSettings(logOnFailure: Boolean = true): Boolean {
        val loaded = settingsStore.load(logOnFailure)
        if (loaded != null) {
            applySettings(loaded)
            return true
        }
        return false
    }

    private fun persistSettings(successMessage: String? = null, errorMessage: String? = null) {
        settings.savedLocation = location
        val saved = settingsStore.save(settings, logOnFailure = errorMessage != null)
        if (saved) {
            successMessage?.let { logger.info(it) }
        } else {
            errorMessage?.let { logger.error(it) }
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
        persistSettings("Saved UberChop settings on deactivation", "Failed to save UberChop settings on deactivation")
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

        awaitIdle()
        if (Coordinates.isPlayerWithinRadius(prepBank, 10)) return true
        if (openBankIfNeeded()) return true
        if (withdrawWoodBox && Equipment.ensureWoodBox(this)) return true

        transitionPhase(Phase.CHOPPING, "Phase: PREPARING -> CHOPPING")
        return false
    }

    private suspend fun handleBankingPhase(): Boolean {
        awaitIdle()
        if (Coordinates.isPlayerWithinRadius(effectiveBankCoordinate(), 10)) return true
        if (openBankIfNeeded()) return true
        if (withdrawWoodBox && Equipment.ensureWoodBox(this)) return true

        if (!Backpack.isFull()) {
            transitionPhase(Phase.CHOPPING, "Phase: BANKING -> CHOPPING")
            return false
        }

        if (withdrawWoodBox) {
            Equipment.emptyWoodBox(this)
        }

        logger.info("Depositing logs {}", logsPattern)
        Bank.depositAll(this, logsPattern)
        awaitTicks(1)
        return true
    }

    private suspend fun handleChoppingPhase(): Boolean {
        if (Backpack.isFull()) {
            if (withdrawWoodBox && BackpackExtensions.interact("Fill",woodBoxPattern)) {
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

        awaitIdle()

        if (Coordinates.isPlayerWithinRadius(effectiveChopCoordinate(), 40)) return true

        if (Trees.chop(this, targetTree)) {
            status = "Chopping $targetTree"
            logger.info("Chop target: '$targetTree'")
            return true
        }

        status = "No $targetTree nearby"
        logger.debug("No target '$targetTree' nearby")
        return false
    }



    private suspend fun openBankIfNeeded(): Boolean {
        if (Bank.isOpen()) return false
        val opened = Bank.open(this)
        logger.info("Bank.open() -> {}", opened)
        awaitTicks(1)
        return true
    }



    private fun refreshDerivedPreferences() {
        val names = TreeTypes.ALL
        val treeIndex = settings.savedTreeType.coerceIn(0, names.lastIndex)
        targetTree = names[treeIndex]

        val available = TreeLocations.locationsFor(targetTree)
        val resolved = settings.savedLocation.takeIf { it.isNotBlank() }?.let { saved ->
            available.firstOrNull { it.name.equals(saved, ignoreCase = true) }
        } ?: available.firstOrNull() ?: TreeLocations.ALL.firstOrNull()

        location = resolved?.name.orEmpty()
        settings.savedLocation = location
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
    val treeLocations: List<TreeLocation>
        get() = TreeLocations.ALL

    // GUI instance
    private val gui by lazy { UberChopGUI(this) }

    override fun onInitialize() {
        super.onInitialize()
        // Prepare GUI resources
        try { gui.preload() } catch (_: Throwable) { }

        // Load persisted settings before reading them
        loadSettings()
    }

    override fun getBuildableUI(): BuildableUI = gui

    override fun savePersistentData(data: JsonObject?) {
        persistSettings()
    }

    override fun loadPersistentData(data: JsonObject?) {
        loadSettings()
    }

    override fun onDrawConfig(workspace: Workspace?) {
        workspace?.let { gui.render(it) }
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

