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
import net.botwithus.kxapi.permissive.PermissiveDSL
import net.botwithus.kxapi.permissive.PermissiveScript
import net.botwithus.rs3.stats.Stats
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import net.botwithus.xapi.script.ui.interfaces.BuildableUI
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
class UberChop : PermissiveScript<BotState>(debug = true) {

    /**
     * Minimal woodcutting loop that hands control to two permissive states.
     * The class only tracks shared context (settings, tiles, cached flags) so
     * each leaf can read or update it without juggling extra helpers.
     */

    private val log = LoggerFactory.getLogger(UberChop::class.java)
    private val gson = Gson()
    // Lazy regex for any item whose name contains "logs".
    internal val logPattern = Pattern.compile(".*logs.*", Pattern.CASE_INSENSITIVE)
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

    private var mode: BotState = BotState.CHOPPING
    private var statusText: String = "Starting up"
    private var startTimeMs: Long = System.currentTimeMillis()
    private var uiSettingsLoaded = false
    internal var chopWorkedLastTick = false
    private val stateInstances = mutableMapOf<BotState, PermissiveDSL<*>>()
    private var statesInitialised = false

    var settings: Settings = Settings()
    var targetTree: String = "Tree"
    var location: String = ""
    var treeTile: Coordinate? = null
    var bankTile: Coordinate? = null

    @Volatile var logsChopped: Int = 0
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
        runCatching { gui.preload() }
        ensureUiSettingsLoaded()
        initializeStateMachine()
        if (!statesInitialised) {
            log.error("State machine failed to initialise; script cannot run")
            updateStatus("Idle: missing states")
            return
        }
        updateStatus("Ready to go")
        switchState(mode, "Initialised")
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
        if (!statesInitialised) {
            initializeStateMachine()
        }
        if (!statesInitialised) {
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
        if (statesInitialised) {
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
            statesInitialised = false
            return
        }

        val orderedStates = stateInstances.entries
            .sortedBy { it.key.ordinal }
            .map { it.value }
            .toTypedArray()

        initStates(*orderedStates)
        statesInitialised = true
        setCurrentState(mode.description)
    }

    private fun instantiateState(state: BotState): PermissiveDSL<*>? = when (state) {
        BotState.CHOPPING -> Chopping(this)
        BotState.BANKING -> Banking(this)
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

    private fun toCoordinate(x: Int?, y: Int?, z: Int?): Coordinate? =
        if (x != null && y != null && z != null) Coordinate(x, y, z) else null
}
