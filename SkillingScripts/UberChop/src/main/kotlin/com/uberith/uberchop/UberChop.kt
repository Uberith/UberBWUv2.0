package com.uberith.uberchop

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.uberith.uberchop.config.Settings
import com.uberith.uberchop.config.TreeLocation
import com.uberith.uberchop.config.TreeLocations
import com.uberith.uberchop.config.TreeTypes
import com.uberith.uberchop.gui.UberChopGUI
import com.uberith.uberchop.state.Banking
import com.uberith.uberchop.state.BotState
import com.uberith.uberchop.state.Chopping
import com.uberith.api.utils.Statistics
import net.botwithus.kxapi.game.inventory.Backpack
import net.botwithus.kxapi.permissive.PermissiveDSL
import net.botwithus.kxapi.permissive.PermissiveScript
import net.botwithus.events.EventInfo
import net.botwithus.rs3.inventories.events.InventoryEvent
import net.botwithus.rs3.item.InventoryItem
import net.botwithus.rs3.stats.Stats
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.script.ui.interfaces.BuildableUI
import net.botwithus.rs3.world.World
import net.botwithus.kxapi.game.scene.groundItem.PickupMessages
import net.botwithus.kxapi.game.scene.groundItem.PickupItemPriority
import net.botwithus.kxapi.game.scene.scene
import org.slf4j.LoggerFactory
import java.util.concurrent.atomic.AtomicBoolean
import java.util.regex.Pattern
import kotlin.math.roundToInt

@Info(

    name = "UberChop",
    description = "Simple tree chopper using the permissive DSL",
    version = "0.2.0",
    author = "Uberith"
)

class UberChop : PermissiveScript<BotState>(debug = false) {
    private companion object {
        private const val BACKPACK_INVENTORY_ID = 93
        private const val STAT_LOGS_KEY = "logs"
        private const val STAT_LOGS_PER_HOUR_KEY = "logsPerHour"
        private const val STAT_BIRD_NESTS_KEY = "birdNests"
    }

    /**
     * Minimal woodcutting loop that hands control to two permissive states.
     * The class only tracks shared context (settings, tiles, cached flags) so
     * each leaf can read or update it without juggling extra helpers.
     */
    private val log = getLogger()
    private val gson = Gson()
    // Lazy regex for any item whose name contains "logs".
    internal val logPattern = Pattern.compile(".*logs.*", Pattern.CASE_INSENSITIVE)
    internal val birdNestPattern = Pattern.compile(".*bird['\\u2019]?s nest.*", Pattern.CASE_INSENSITIVE)
    private val birdNestRegex = Regex("(?i).*bird['\\u2019]?s nest.*")
    private val statistics = Statistics("UberChop")
    private val statsLock = Any()
    val woodBoxPattern: Pattern = Pattern.compile(".*wood box.*", Pattern.CASE_INSENSITIVE)
    internal enum class LogHandling {
        BANK,
        BURN,
        FLETCH;

        companion object {
            fun from(index: Int): LogHandling = values().getOrElse(index) { BANK }
        }
    }

    internal val logHandlingPreference: LogHandling
        get() = LogHandling.from(settings.logHandlingMode)
    internal val shouldUseWoodBox: Boolean
        get() = settings.withdrawWoodBox && logHandlingPreference == LogHandling.BANK
    // Simple guard to avoid spamming movement requests while one is "in flight".
    internal val movementGate = AtomicBoolean(false)
    private val gui by lazy { UberChopGUI(this) }
    private val woodBoxRetryCooldownMs = 30_000L
    private var nextWoodBoxWithdrawTimeMs: Long = 0L
    internal var woodBoxWithdrawAttempted = false
    internal var woodBoxWithdrawSucceeded = false

    private var mode: BotState = BotState.CHOPPING
    private var statusText: String = "Starting up"
    private var startTimeMs: Long = System.currentTimeMillis()
    private var uiSettingsLoaded = false
    internal var chopWorkedLastTick = false
    private val stateInstances = mutableMapOf<BotState, PermissiveDSL<*>>()
    private var statesInitialized = false
    var settings: Settings = Settings()
    var targetTree: String = "Tree"
    var location: String = ""
    var treeTile: Coordinate? = null
    var bankTile: Coordinate? = null

    @Volatile var logsChopped: Int = 0
        private set
    @Volatile var birdNestsCollected: Int = 0
        private set
    val treeLocations: List<TreeLocation>
        get() = TreeLocations.ALL
    val WCLevel: Int
        get() = Stats.WOODCUTTING.currentLevel
    val currentStatus: String
        get() = statusText

    override fun getBuildableUI(): BuildableUI = gui

    override fun onDrawConfig(workspace: Workspace?) {
        workspace?.let {
            runCatching { gui.render(it) }
                .onFailure { log.warn("GUI render error", it) }
        }
    }

    override fun onDraw(workspace: Workspace) {
        runCatching { gui.render(workspace) }
            .onFailure { log.warn("GUI render error", it) }
    }

    override fun init() {
        initializeStateMachine()
    }

    override fun onInitialize() {
        super.onInitialize()
        startTimeMs = System.currentTimeMillis()
        resetRuntimeStatistics()
        runCatching { gui.preload() }

        ensureUiSettingsLoaded()
        initializeStateMachine()
        if (!statesInitialized) {
            log.error("State machine failed to initialize; script cannot run")
            updateStatus("Idle: missing states")
            return
        }

        val initialState = if (Backpack.isFull()) BotState.BANKING else BotState.CHOPPING
        val reason = if (initialState == BotState.BANKING) {
            "Initialized with full backpack"
        } else {
            "Initialized"
        }

        switchState(initialState, reason)
    }

    private fun resetRuntimeStatistics() {
        synchronized(statsLock) {
            logsChopped = 0
            birdNestsCollected = 0
            statistics.saveStatistic(STAT_LOGS_KEY, logsChopped)
            statistics.saveStatistic(STAT_LOGS_PER_HOUR_KEY, 0)
            statistics.saveStatistic(STAT_BIRD_NESTS_KEY, birdNestsCollected)
        }
    }

    override fun onPreTick(): Boolean {
        if (!uiSettingsLoaded) {
            ensureUiSettingsLoaded()
        }

        return super.onPreTick()
    }

    override fun savePersistentData(container: JsonObject?) {
        val target = container ?: return
        target.add("settings", gson.toJsonTree(settings))
        target.addProperty("targetTree", targetTree)
        target.addProperty("location", location)
    }

    override fun loadPersistentData(container: JsonObject?) {
        val source = container ?: return
        source.getAsJsonObject("settings")?.let {
            runCatching { gson.fromJson(it, Settings::class.java) }
                .onSuccess { loaded -> settings = loaded }
                .onFailure { error -> log.warn("Failed to deserialize settings", error) }
        }
        source.get("targetTree")?.asString?.let { targetTree = it }
        source.get("location")?.asString?.let { location = it }

        uiSettingsLoaded = false
    }

    fun updateStatus(text: String) {
        statusText = text
        setStatus(text)
    }

    fun switchState(next: BotState, reason: String) {
        if (!statesInitialized) {
            initializeStateMachine()
        }

        if (!statesInitialized) {
            log.error("Cannot switch state to {} because no states are registered", next.description)
            updateStatus("${next.description}: unavailable")
            return
        }

        val stateName = stateInstances[next]?.name ?: next.description
        setCurrentState(stateName)
        mode = next
        chopWorkedLastTick = false
        info("Now ${next.description.lowercase()}: $reason")
        updateStatus("${next.description}: $reason")
    }

    private fun initializeStateMachine() {
        if (statesInitialized) {
            return
        }

        stateInstances.clear()
        BotState.entries.forEach { state ->
            val instance = instantiateState(state)
            if (instance != null) {
                stateInstances[state] = instance
            } else {
                log.error("Failed to instantiate state {}", state.description)
            }

        }

        if (stateInstances.size != BotState.entries.size) {
            val missing = BotState.entries
                .filterNot { stateInstances.containsKey(it) }

                .joinToString(", ") { it.description }

            log.error("Unable to build state machine; missing states: {}", missing)
            stateInstances.clear()
            statesInitialized = false
            return
        }

        val orderedStates = stateInstances.entries
            .sortedBy { it.key.ordinal }
            .map { it.value }
            .toTypedArray()
        initStates(*orderedStates)
        statesInitialized = true
        setCurrentState(mode.description)
    }

    private fun instantiateState(state: BotState): PermissiveDSL<*>? = when (state) {
        BotState.CHOPPING -> Chopping(this)
        BotState.BANKING -> Banking(this)
    }

    internal fun depositItemsFallback(
        pattern: Pattern,
        maxIterations: Int = 10
    ): Boolean {
        var depositedAny = false
        repeat(maxIterations) {
            val item = Backpack.getItems().firstOrNull { pattern.matcher(it.name).matches() }
                ?: return depositedAny
            val interacted = Backpack.interact(item, "Deposit-All") ||
                Backpack.interact(item, "Deposit") ||
                Backpack.interact(item, "Deposit-1")
            if (!interacted) {
                log.warn("Fallback deposit failed for ${item.name} (${item.id})")
                return depositedAny
            }

            depositedAny = true
            delay(1)
        }

        return depositedAny
    }

    internal fun depositLogsFallback(maxIterations: Int = 10): Boolean =
        depositItemsFallback(logPattern, maxIterations)

    internal fun canAttemptWoodBoxWithdraw(): Boolean =
        System.currentTimeMillis() >= nextWoodBoxWithdrawTimeMs


    internal fun recordWoodBoxWithdraw(success: Boolean) {
        woodBoxWithdrawAttempted = true
        woodBoxWithdrawSucceeded = success
        nextWoodBoxWithdrawTimeMs = if (success) 0L else System.currentTimeMillis() + woodBoxRetryCooldownMs
        if (!success) {
            log.debug("Wood box withdraw retry delayed for ${woodBoxRetryCooldownMs / 1000}s")
        }
    }

    internal fun ensureWoodBoxInBackpack(
        statusMessage: String,
        warnMessage: String,
        errorMessage: String
    ): Boolean {
        if (Equipment.hasWoodBox()) {
            return false
        }

        updateStatus(statusMessage)

        val retrieved = runCatching { Equipment.ensureWoodBox(this) }
            .onFailure { log.error(errorMessage, it) }
            .getOrDefault(false)

        if (retrieved) {
            return true
        }

        if (!Equipment.hasWoodBox()) {
            log.warn(warnMessage)
        }
        return false
    }

    private fun computeAcquiredQuantity(oldItem: InventoryItem, newItem: InventoryItem): Int {
        if (newItem.id <= -1) {
            return 0
        }

        if (oldItem.id <= -1) {
            return newItem.quantity
        }

        if (oldItem.id == newItem.id) {
            val delta = newItem.quantity - oldItem.quantity
            return if (delta > 0) delta else 0
        }
        return newItem.quantity
    }

    @EventInfo(type = InventoryEvent::class)
    private fun onInventoryEvent(event: InventoryEvent) {
        if (event.inventory.id != BACKPACK_INVENTORY_ID) {
            return
        }

        val oldItem = event.oldItem()
        val newItem = event.newItem()
        val quantityAdded = computeAcquiredQuantity(oldItem, newItem)
        if (quantityAdded <= 0) {
            return
        }

        val itemName = newItem.name
        val isLog = logPattern.matcher(itemName).matches()
        val isBirdNest = settings.pickupNests && birdNestRegex.matches(itemName)

        if (!isLog && !isBirdNest) {
            return
        }

        synchronized(statsLock) {
            if (isLog) {
                logsChopped += quantityAdded
                statistics.saveStatistic(STAT_LOGS_KEY, logsChopped)
                statistics.saveStatistic(STAT_LOGS_PER_HOUR_KEY, logsPerHour())
            }
            if (isBirdNest) {
                birdNestsCollected += quantityAdded
                statistics.saveStatistic(STAT_BIRD_NESTS_KEY, birdNestsCollected)
            }
        }
    }

    fun formattedRuntime(): String {
        val elapsed = System.currentTimeMillis() - startTimeMs
        val totalSeconds = (elapsed / 1000).coerceAtLeast(0)
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun logsPerHour(): Int {
        val elapsed = System.currentTimeMillis() - startTimeMs
        if (elapsed <= 0) return 0
        val hours = elapsed / 3_600_000.0
        if (hours <= 0.0) return 0
        return (logsChopped / hours).roundToInt()
    }

    fun ensureUiSettingsLoaded() {
        if (uiSettingsLoaded) {
            return
        }

        applyLocationSelection()
        uiSettingsLoaded = true
    }

    fun onSettingsChanged() {
        applyLocationSelection()
        uiSettingsLoaded = true
        performSavePersistentData()
    }

    private fun applyLocationSelection() {
        // Keep target tree / location consistent with whatever the UI last persisted.
        val allTrees = TreeTypes.ALL
        if (allTrees.isNotEmpty()) {
            val index = settings.savedTreeType.coerceIn(0, allTrees.size - 1)
            targetTree = allTrees[index]
        }

        val resolvedLocation = when {
            settings.savedLocation.isNotBlank() -> settings.savedLocation
            location.isNotBlank() -> location
            else -> treeLocations.firstOrNull()?.name ?: ""
        }

        location = resolvedLocation
        if (settings.savedLocation.isBlank()) {
            // First run: remember whichever spot we defaulted to so the GUI reflects it.
            settings.savedLocation = resolvedLocation
        }

        val locationData = treeLocations.firstOrNull { it.name == resolvedLocation }

        val custom = settings.customLocations[resolvedLocation]
        treeTile = custom?.let { toCoordinate(it.chopX, it.chopY, it.chopZ) } ?: locationData?.chop
        bankTile = custom?.let { toCoordinate(it.bankX, it.bankY, it.bankZ) } ?: locationData?.bank
        if (locationData != null && locationData.availableTrees.isNotEmpty()) {
            // Make sure the selected tree is actually available at the chosen spot.
            val matches = locationData.availableTrees.firstOrNull { type ->
                type.displayName.equals(targetTree, ignoreCase = true)
            }

            if (matches == null) {
                targetTree = locationData.availableTrees.first().displayName
            }
        }
    }

    fun shouldPickupBirdNest(): Boolean {
        if (!settings.pickupNests) {
            return false
        }

        if (Backpack.isFull()) {
            return false
        }

        return World.getGroundItems()
            .asSequence()
            .flatMap { ground -> ground.items.asSequence() }
            .any { item -> birdNestRegex.matches(item.name) }

    }

    fun pickupBirdNests(): Boolean {
        if (!settings.pickupNests) {
            return false
        }

        if (Backpack.isFull()) {
            warn("Cannot pick up bird's nest because backpack is full")
            return false
        }

        val pickupTask = scene.pickup {
            global {
                distance(8)
                itemPriority(PickupItemPriority.VERY_HIGH)
            }

            item(birdNestRegex)
        }

        return when (pickupTask.pickup()) {
            PickupMessages.NO_ITEMS_FOUND -> false
            PickupMessages.FULL_INVENTORY -> {
                warn("Failed to pick up bird's nest: inventory full")
                false
            }

            else -> true
        }

    }

    private fun toCoordinate(x: Int?, y: Int?, z: Int?): Coordinate? =
        if (x != null && y != null && z != null) Coordinate(x, y, z) else null
}

