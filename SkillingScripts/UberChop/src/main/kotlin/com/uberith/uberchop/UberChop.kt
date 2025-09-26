package com.uberith.uberchop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.uberith.api.game.world.Coordinates
import com.uberith.uberchop.gui.UberChopGUI
import net.botwithus.kxapi.game.skilling.impl.fletching.FletchingProduct
import net.botwithus.kxapi.game.skilling.impl.fletching.fletching
import net.botwithus.kxapi.game.skilling.impl.woodcutting.woodcutting
import net.botwithus.kxapi.game.skilling.skilling
import net.botwithus.kxapi.script.SuspendableScript
import net.botwithus.rs3.stats.Stats
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.kxapi.game.inventory.Backpack
import net.botwithus.kxapi.game.inventory.Bank
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
        private const val PREPARING_TIMEOUT_MS = 45_000L
    }

    var settings = Settings()
    var targetTree: String = "Tree"
    var location: String = ""
    @Volatile var logsChopped: Int = 0
        private set
    @get:JvmName("getStatusText")
    var status: String = "Idle"
        private set
    @Volatile var phase: Phase = Phase.READY
    var WCLevel = Stats.WOODCUTTING.currentLevel
    private val gson = Gson()
    private val logger: Logger = LoggerFactory.getLogger(this::class.java)
    @Volatile private var totalRuntimeMs: Long = 0L
    @Volatile private var lastRuntimeUpdateMs: Long = 0L
    private var lastKnownLogCount: Int = 0
    @Volatile private var uiInitialized: Boolean = false
    @Volatile private var preppingDetailsLogged: Boolean = false
    private var preparingStartedAt: Long = 0L

    private data class CustomLocationSnapshot(
        val chopX: Int?,
        val chopY: Int?,
        val chopZ: Int?,
        val bankX: Int?,
        val bankY: Int?,
        val bankZ: Int?
    )

    private data class LocationSnapshot(
        val locationName: String,
        val custom: CustomLocationSnapshot?,
        val selected: TreeLocation?,
        val chop: Coordinate?,
        val bank: Coordinate?
    )

    private data class RuntimeSnapshot(
        val logsChopped: Int,
        val totalRuntimeMs: Long
    )

    private enum class LogHandling {
        BANK,
        DROP,
        FLETCH;

        companion object {
            fun from(index: Int): LogHandling = values().getOrElse(index) { BANK }
        }
    }

    @Volatile private var cachedLocationSnapshot: LocationSnapshot? = null

    private fun invalidateLocationSnapshot() {
        cachedLocationSnapshot = null
    }

    private val logHandlingPreference: LogHandling
        get() = LogHandling.from(settings.logHandlingMode)

    private val shouldUseWoodBox: Boolean
        get() = settings.withdrawWoodBox && logHandlingPreference == LogHandling.BANK

    private val gui by lazy { UberChopGUI(this) }

    val treeLocations: List<TreeLocation>
        get() = TreeLocations.ALL

    override fun getStatus(): String = status
    override fun onInitialize() {
        super.onInitialize()
        configPanel(gui)
        try { gui.preload() } catch (_: Throwable) { }
        refreshDerivedPreferences()
    }

    override fun onActivation() {
        super.onActivation()
        refreshDerivedPreferences()
        logger.info("Activated: tree='{}', location='{}' (locations={})", targetTree, location, treeLocations.size)
        setStatus("Active - Preparing")
        transitionPhase(Phase.PREPARING, "Phase: READY -> PREPARING (activation)")
        logsChopped = 0
        totalRuntimeMs = 0L
        lastRuntimeUpdateMs = System.currentTimeMillis()
        resetLogTracking()
    }

    override fun onDeactivation() {
        super.onDeactivation()
        updateRuntimeSnapshot()
        performSavePersistentData()
        setStatus("Inactive")
        phase = Phase.READY
        logger.info("Deactivated")
    }

    override suspend fun onLoop() {
        updateRuntimeSnapshot()
        try {
            when (phase) {
                Phase.READY -> transitionPhase(Phase.PREPARING, "Phase: READY -> PREPARING")
                Phase.PREPARING -> if (handlePreparingPhase()) return
                Phase.BANKING -> if (handleBankingPhase()) return
                Phase.CHOPPING -> if (handleChoppingPhase()) return
            }
        } catch (t: Throwable) {
            logger.error("Unhandled exception while executing phase {}", phase, t)
            setStatus("Error: ${t.message ?: t::class.simpleName}")
            awaitTicks(1)
        }
    }

    override fun saveData(data: JsonObject) {
        data.add("settings", gson.toJsonTree(settings).asJsonObject)
        data.add("runtime", gson.toJsonTree(RuntimeSnapshot(logsChopped, totalRuntimeMs)).asJsonObject)
    }

    override fun loadData(data: JsonObject) {
        data.getAsJsonObject("settings")?.let { obj ->
            settings = gson.fromJson(obj, Settings::class.java)
        }
        data.getAsJsonObject("runtime")?.let { obj ->
            val snapshot = gson.fromJson(obj, RuntimeSnapshot::class.java)
            logsChopped = snapshot.logsChopped.coerceAtLeast(0)
            totalRuntimeMs = snapshot.totalRuntimeMs.coerceAtLeast(0L)
        }
        refreshDerivedPreferences()
    }

    fun ensureUiSettingsLoaded() {
        if (uiInitialized) return
        refreshDerivedPreferences()
        uiInitialized = true
    }

    fun onSettingsChanged() {
        performSavePersistentData()
        invalidateLocationSnapshot()
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
        updateLogTracking()
        val now = System.currentTimeMillis()
        if (lastRuntimeUpdateMs != 0L) {
            totalRuntimeMs += now - lastRuntimeUpdateMs
        }
        lastRuntimeUpdateMs = now
        WCLevel = Stats.WOODCUTTING.currentLevel
    }

    private fun updateLogTracking() {
        val currentLogCount = currentBackpackLogCount()
        val gained = currentLogCount - lastKnownLogCount
        if (gained > 0) {
            logsChopped += gained
        }
        lastKnownLogCount = currentLogCount
    }

    private fun currentBackpackLogCount(): Int = runCatching {
        Backpack.getItems().sumOf { item ->
            if (LOGS_PATTERN.matcher(item.name).matches()) item.quantity else 0
        }
    }.getOrDefault(0)

    private fun resetLogTracking() {
        lastKnownLogCount = currentBackpackLogCount()
    }

    // Phase management
    private fun transitionPhase(next: Phase, message: String? = null) {
        message?.let { logger.info(it) }
        if (phase != next) {
            logger.debug("Phase transition {} -> {}", phase, next)
        }
        val previous = phase
        phase = next
        if (next == Phase.PREPARING) {
            preppingDetailsLogged = false
            preparingStartedAt = System.currentTimeMillis()
        } else if (previous == Phase.PREPARING) {
            preparingStartedAt = 0L
        }
    }

    override fun setStatus(next: String): Boolean {
        if (status != next) {
            status = next
            logger.info("Status -> {}", next)
            return true
        }
        return false
    }

    private fun readyToChop(reason: String) {
        transitionPhase(Phase.CHOPPING, reason)
        setStatus("Ready to chop $targetTree")
    }

    private fun preparingWaitOrTimeout(reason: String): Boolean {
        if (preparingStartedAt == 0L) {
            preparingStartedAt = System.currentTimeMillis()
        }
        val elapsed = System.currentTimeMillis() - preparingStartedAt
        if (elapsed >= PREPARING_TIMEOUT_MS) {
            logger.error("Preparing timeout after {} ms: {}", elapsed, reason)
            setStatus("Preparing timeout - continuing")
            transitionPhase(Phase.CHOPPING, "Phase: PREPARING -> CHOPPING (timeout)")
            return false
        }
        logger.debug("Preparing wait: {} (elapsed={} ms)", reason, elapsed)
        return true
    }

    private suspend fun handlePreparingPhase(): Boolean {
        if (!preppingDetailsLogged) {
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
            preppingDetailsLogged = true
        }

        val bankTarget = effectiveBankCoordinate()

        if (!shouldUseWoodBox) {
            readyToChop("Phase: PREPARING -> CHOPPING (wood box disabled)")
            return false
        }

        setStatus("Preparing supplies")

        if (bankTarget == null) {
            logger.warn("Wood box enabled but no bank coordinate for location='{}'; continuing without banking prep", location)
            readyToChop("Phase: PREPARING -> CHOPPING (no bank target)")
            return false
        }

        if (!Coordinates.isPlayerWithinRadius(bankTarget, BANK_RADIUS)) {
            setStatus("Moving to bank")
            if (!Bank.isOpen()) {
                openBankIfNeeded()
            }
            return preparingWaitOrTimeout("Awaiting arrival at bank for '$location'")
        }

        if (!Bank.isOpen()) {
            setStatus("Opening bank")
            val opened = openBankIfNeeded()
            if (opened) {
                return preparingWaitOrTimeout("Opening bank at '$location'")
            }
            logger.warn("Bank failed to open near location='{}'; will retry", location)
            return preparingWaitOrTimeout("Opening bank at '$location' (retry)")
        }

        if (ensureWoodBoxInBackpack(
                statusMessage = "Withdrawing wood box",
                warnMessage = "No wood box retrieved; proceeding without to avoid stalling",
                errorMessage = "Failed to withdraw wood box"
        )) {
            return true
        }

        readyToChop("Phase: PREPARING -> CHOPPING")
        return false
    }

    private suspend fun handleBankingPhase(): Boolean {
        setStatus("Banking logs")
        val bankTarget = effectiveBankCoordinate()

        if (bankTarget != null && !Coordinates.isPlayerWithinRadius(bankTarget, BANK_RADIUS)) {
            setStatus("Moving to bank")
            if (!Bank.isOpen()) {
                openBankIfNeeded()
            }
            return true
        }

        if (!Bank.isOpen()) {
            setStatus("Opening bank")
            val opened = openBankIfNeeded()
            if (opened) {
                return true
            }
            logger.warn("Failed to open bank at location='{}'; retrying", location)
            return true
        }

        if (shouldUseWoodBox && Equipment.emptyWoodBox(this)) {
            setStatus("Emptying wood box")
            return true
        }

        if (shouldUseWoodBox && ensureWoodBoxInBackpack(
                statusMessage = "Retrieving wood box",
                warnMessage = "Unable to retrieve wood box during banking; continuing without box",
                errorMessage = "Failed to retrieve wood box during banking"
        )) {
            return true
        }

        if (Backpack.contains(LOGS_PATTERN)) {
            logger.info("Depositing logs")
            Bank.depositAll(this, LOGS_PATTERN)
            awaitTicks(1)
            return true
        }

        if (Backpack.isFull()) {
            logger.warn("Backpack still full after deposit attempt; retrying")
            return true
        }

        readyToChop("Phase: BANKING -> CHOPPING")
        return false
    }

    private suspend fun handleChoppingPhase(): Boolean {
        if (Backpack.isFull()) {
            setStatus("Backpack full")
            return handleFullBackpack()
        }

        setStatus("Locating nearest $targetTree")
        awaitIdle()

        val treeType = TreeTypes.resolve(targetTree)
        val started = runCatching {
            val chopRequest = treeType?.let { this.skilling.woodcutting.chop(it) }
                ?: this.skilling.woodcutting.chop(targetTree)
            chopRequest.nearest()
        }.onFailure { logger.error("Failed to initiate chopping for '{}'", targetTree, it) }
            .getOrDefault(false)

        if (started) {
            setStatus("Chopping $targetTree")
            logger.info("Chop target: '{}'", targetTree)
            return true
        }

        setStatus("No $targetTree nearby")
        logger.debug("No target '{}' nearby", targetTree)
        return false
    }

    private suspend fun handleFullBackpack(): Boolean {
        val handling = logHandlingPreference

        if (handling == LogHandling.BANK && Backpack.contains(LOGS_PATTERN) && Equipment.hasWoodBox()) {
            setStatus("Filling wood box")
            val filled = Equipment.fillWoodBox(this)
            if (filled) {
                if (!Backpack.isFull()) {
                    resetLogTracking()
                    logger.debug("Filled wood box; continuing chopping phase")
                    return true
                }
                logger.debug("Filled wood box but backpack remains full; continuing handling")
            } else {
                logger.debug("Attempted to fill wood box but interaction failed; proceeding with {} handling", handling)
            }
        }

        return when (handling) {
            LogHandling.BANK -> {
                setStatus("Banking logs")
                transitionPhase(Phase.BANKING, "Phase: CHOPPING -> BANKING")
                true
            }
            LogHandling.DROP -> {
                setStatus("Dropping logs")
                if (dropLogsFromBackpack()) {
                    logger.debug("Dropped logs to clear backpack space")
                    resetLogTracking()
                    true
                } else {
                    logger.warn("Dropping logs failed; defaulting to banking")
                    setStatus("Banking logs")
                    transitionPhase(Phase.BANKING, "Phase: CHOPPING -> BANKING (drop fallback)")
                    true
                }
            }
            LogHandling.FLETCH -> {
                if (tryFletchArrowShafts()) {
                    resetLogTracking()
                    true
                } else {
                    logger.warn("Fletching logs failed; defaulting to banking")
                    setStatus("Banking logs")
                    transitionPhase(Phase.BANKING, "Phase: CHOPPING -> BANKING (fletch fallback)")
                    true
                }
            }
        }
    }

    private suspend fun dropLogsFromBackpack(): Boolean {
        val logItems = Backpack.getItems().filter { item -> LOGS_PATTERN.matcher(item.name).matches() }
        if (logItems.isEmpty()) {
            logger.debug("Drop requested but no logs detected in backpack")
            return false
        }

        var droppedAny = false
        for (item in logItems) {
            val dropped = runCatching { Backpack.interact(item, "Drop") }
                .onFailure { logger.error("Failed to drop '{}' from backpack", item.name, it) }
                .getOrDefault(false)
            if (dropped) {
                droppedAny = true
                awaitTicks(1)
            }
        }

        if (!droppedAny) {
            return false
        }

        repeat(5) {
            if (!Backpack.isFull()) {
                return true
            }
            awaitTicks(1)
        }
        return !Backpack.isFull()
    }

    private suspend fun tryFletchArrowShafts(): Boolean {
        val logCountBefore = currentBackpackLogCount()
        if (logCountBefore == 0) {
            logger.debug("Fletching requested but no logs detected in backpack")
            return false
        }

        val fletching = this.skilling.fletching
        if (!fletching.canProduce(FletchingProduct.ARROW_SHAFTS)) {
            logger.warn("Unable to fletch arrow shafts with current inventory; missing materials")
            return false
        }

        setStatus("Fletching arrow shafts")
        val started = runCatching {
            fletching.produce(FletchingProduct.ARROW_SHAFTS).produceItem()
            true
        }.onFailure { logger.error("Failed to start arrow shaft fletching", it) }
            .getOrDefault(false)

        if (!started) {
            return false
        }

        repeat(30) {
            awaitTicks(1)
            val currentLogs = currentBackpackLogCount()
            if (!Backpack.isFull() || currentLogs < logCountBefore) {
                logger.debug("Arrow shaft fletching freed backpack space")
                return true
            }
        }

        logger.warn("Arrow shaft fletching did not relieve backpack space")
        return false
    }

    private suspend fun openBankIfNeeded(): Boolean {
        if (Bank.isOpen()) return false
        val opened = runCatching { Bank.open(this@UberChop) }
            .onFailure { logger.error("Bank.open() threw", it) }
            .getOrDefault(false)
        logger.info("Bank.open() -> {}", opened)
        if (opened) {
            awaitTicks(1)
        }
        return opened
    }

    private suspend fun ensureWoodBoxInBackpack(
        statusMessage: String,
        warnMessage: String,
        errorMessage: String
    ): Boolean {
        if (Equipment.hasWoodBox()) {
            return false
        }
        setStatus(statusMessage)
        val retrieved = runCatching { Equipment.ensureWoodBox(this) }
            .onFailure { logger.error(errorMessage, it) }
            .getOrDefault(false)
        if (retrieved) {
            awaitTicks(1)
            return true
        }
        if (!Equipment.hasWoodBox()) {
            logger.warn(warnMessage)
        }
        return false
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
        invalidateLocationSnapshot()
    }

    // Location helpers
    private fun currentLocationSnapshot(): LocationSnapshot {
        val activeLocation = location
        val custom = settings.customLocations[activeLocation]
        val cached = cachedLocationSnapshot

        if (cached != null && cached.locationName == activeLocation) {
            val cachedCustom = cached.custom
            val customMatches = when {
                cachedCustom == null && custom == null -> true
                cachedCustom != null && custom != null ->
                    cachedCustom.chopX == custom.chopX &&
                    cachedCustom.chopY == custom.chopY &&
                    cachedCustom.chopZ == custom.chopZ &&
                    cachedCustom.bankX == custom.bankX &&
                    cachedCustom.bankY == custom.bankY &&
                    cachedCustom.bankZ == custom.bankZ
                else -> false
            }
            if (customMatches) {
                return cached
            }
        }

        val customSnapshot = custom?.let {
            CustomLocationSnapshot(it.chopX, it.chopY, it.chopZ, it.bankX, it.bankY, it.bankZ)
        }
        val selected = treeLocations.firstOrNull { it.name == activeLocation }
        val chop = customSnapshot?.let { toCoordinate(it.chopX, it.chopY, it.chopZ) } ?: selected?.chop
        val bank = customSnapshot?.let { toCoordinate(it.bankX, it.bankY, it.bankZ) } ?: selected?.bank

        return LocationSnapshot(activeLocation, customSnapshot, selected, chop, bank).also {
            cachedLocationSnapshot = it
        }
    }

    private fun selectedLocation(): TreeLocation? = currentLocationSnapshot().selected

    private fun effectiveChopCoordinate(): Coordinate? = currentLocationSnapshot().chop

    private fun effectiveBankCoordinate(): Coordinate? = currentLocationSnapshot().bank

    private fun toCoordinate(x: Int?, y: Int?, z: Int?): Coordinate? =
        if (x != null && y != null && z != null) Coordinate(x, y, z) else null
}


