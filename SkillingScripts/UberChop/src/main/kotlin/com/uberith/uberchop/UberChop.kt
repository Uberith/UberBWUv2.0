package com.uberith.uberchop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.uberith.api.game.world.Coordinates
import com.uberith.uberchop.gui.UberChopGUI
import net.botwithus.kxapi.game.skilling.impl.woodcutting.Woodcutting
import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.rs3.stats.Stats
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.game.inventory.Backpack
import net.botwithus.xapi.game.inventory.Bank
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

    companion object {
        private val LOGS_PATTERN: Pattern = Pattern.compile(".*logs.*", Pattern.CASE_INSENSITIVE)
        private const val BANK_RADIUS = 10
        private const val CHOP_SEARCH_RADIUS = 40
    }

    var settings = Settings()
    var targetTree: String = "Tree"
    var location: String = ""
    var logsChopped: Int = 0
    @get:JvmName("getStatusText") var status: String = "Idle"
    @Volatile var phase: Phase = Phase.READY
    var WCLevel = Stats.WOODCUTTING.currentLevel
    private val gson = Gson()
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    private var totalRuntimeMs: Long = 0L
    private var lastRuntimeUpdateMs: Long = 0L
    @Volatile private var uiInitialized: Boolean = false

    private val shouldUseWoodBox: Boolean
        get() = settings.withdrawWoodBox

    private val gui by lazy { UberChopGUI(this) }

    val treeLocations: List<TreeLocation>
        get() = TreeLocations.ALL

    override fun getStatus(): String = status

    override fun onInitialize() {
        super.onInitialize()
        try { gui.preload() } catch (_: Throwable) { }
        refreshDerivedPreferences()
    }

    override fun onActivation() {
        super.onActivation()
        refreshDerivedPreferences()
        logger.info("Activated: tree='{}', location='{}' (locations={})", targetTree, location, treeLocations.size)
        status = "Active - Preparing"
        phase = Phase.PREPARING
        totalRuntimeMs = 0L
        lastRuntimeUpdateMs = System.currentTimeMillis()
    }

    override fun onDeactivation() {
        super.onDeactivation()
        performSavePersistentData()
        status = "Inactive"
        phase = Phase.READY
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
    }

    override suspend fun buildUI(): BuildableUI = gui

    override fun saveData(data: JsonObject) {
        data.add("settings", gson.toJsonTree(settings).asJsonObject)
    }

    override fun loadData(data: JsonObject) {
        data.getAsJsonObject("settings")?.let { obj ->
            settings = gson.fromJson(obj, Settings::class.java)
        }
        refreshDerivedPreferences()
    }

    override suspend fun onDrawConfigSuspend(workspace: Workspace) {
        gui.render(workspace)
    }

    override fun onDraw(workspace: Workspace) {
        super.onDraw(workspace)
        try { gui.render(workspace) } catch (_: Throwable) { }
    }

    fun ensureUiSettingsLoaded() {
        if (uiInitialized) return
        refreshDerivedPreferences()
        uiInitialized = true
    }

    fun onSettingsChanged() {
        performSavePersistentData()
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

    private fun updateRuntimeSnapshot() {
        val now = System.currentTimeMillis()
        if (lastRuntimeUpdateMs != 0L) {
            totalRuntimeMs += now - lastRuntimeUpdateMs
        }
        lastRuntimeUpdateMs = now
        WCLevel = Stats.WOODCUTTING.currentLevel
    }

    // Phase management
    private fun transitionPhase(next: Phase, message: String? = null) {
        message?.let { logger.info(it) }
        phase = next
    }

    private suspend fun handlePreparingPhase(): Boolean {
        logger.info("Preparing target='{}', location='{}'", targetTree, location)
        logger.debug(
            "Preparing: withdrawWoodBox={}, worldHop={}, notepaper={}, crystallise={}, rotation={}, pickupNests={}",
            shouldUseWoodBox,
            settings.enableWorldHopping,
            settings.useMagicNotepaper,
            settings.useCrystallise,
            settings.enableTreeRotation,
            settings.pickupNests
        )

        val prepChop = effectiveChopCoordinate()
        val prepBank = effectiveBankCoordinate()
        logger.debug("Preparing: chopTile={}, bankTile={}", prepChop ?: "none", prepBank ?: "none")

        if (!shouldUseWoodBox) {
            transitionPhase(Phase.CHOPPING, "Phase: PREPARING -> CHOPPING")
            return false
        }

        awaitIdle()
        if (Coordinates.isPlayerWithinRadius(prepBank, BANK_RADIUS)) return true
        if (openBankIfNeeded()) return true
        if (Equipment.ensureWoodBox(this)) return true

        transitionPhase(Phase.CHOPPING, "Phase: PREPARING -> CHOPPING")
        return false
    }

    private suspend fun handleBankingPhase(): Boolean {
        awaitIdle()
        if (Coordinates.isPlayerWithinRadius(effectiveBankCoordinate(), BANK_RADIUS)) return true
        if (openBankIfNeeded()) return true
        if (shouldUseWoodBox && Equipment.ensureWoodBox(this)) return true

        if (!Backpack.isFull()) {
            transitionPhase(Phase.CHOPPING, "Phase: BANKING -> CHOPPING")
            return false
        }

        if (shouldUseWoodBox) {
            Equipment.emptyWoodBox(this)
        }

        logger.info("Depositing logs")
        Bank.depositAll(this, LOGS_PATTERN)
        awaitTicks(1)
        return true
    }

    private suspend fun handleChoppingPhase(): Boolean {
        if (Backpack.isFull()) {
            return handleFullBackpack()
        }

        status = "Locating nearest $targetTree"
        awaitIdle()

        if (Coordinates.isPlayerWithinRadius(effectiveChopCoordinate(), CHOP_SEARCH_RADIUS)) return true

        if (Woodcutting.chop(this, targetTree)) {
            status = "Chopping $targetTree"
            logger.info("Chop target: '{}'", targetTree)
            return true
        }

        status = "No $targetTree nearby"
        logger.debug("No target '{}' nearby", targetTree)
        return false
    }

    private suspend fun handleFullBackpack(): Boolean {
        if (shouldUseWoodBox && Equipment.fillWoodBox(this)) {
            if (!Backpack.isFull()) {
                logger.debug("Filled wood box; continuing chopping phase")
                return true
            }
        }
        transitionPhase(Phase.BANKING, "Phase: CHOPPING -> BANKING")
        return true
    }

    private suspend fun openBankIfNeeded(): Boolean {
        if (Bank.isOpen()) return false
        val opened = Bank.open(this)
        logger.info("Bank.open() -> {}", opened)
        if (opened) {
            awaitTicks(1)
        }
        return opened
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

    // Location helpers
    private fun selectedLocation(): TreeLocation? = treeLocations.firstOrNull { it.name == location }

    private fun effectiveChopCoordinate(): Coordinate? =
        resolveCustomCoordinate { toCoordinate(it.chopX, it.chopY, it.chopZ) } ?: selectedLocation()?.chop

    private fun effectiveBankCoordinate(): Coordinate? =
        resolveCustomCoordinate { toCoordinate(it.bankX, it.bankY, it.bankZ) } ?: selectedLocation()?.bank

    private fun resolveCustomCoordinate(extractor: (CustomLocation) -> Coordinate?): Coordinate? {
        val custom = settings.customLocations[location] ?: return null
        return extractor(custom)
    }

    private fun toCoordinate(x: Int?, y: Int?, z: Int?): Coordinate? =
        if (x != null && y != null && z != null) Coordinate(x, y, z) else null
}

