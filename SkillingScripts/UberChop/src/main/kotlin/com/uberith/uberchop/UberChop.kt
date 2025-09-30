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
import net.botwithus.kxapi.game.inventory.Backpack
import net.botwithus.kxapi.game.inventory.Bank
import net.botwithus.kxapi.permissive.PermissiveDSL
import net.botwithus.kxapi.permissive.PermissiveScript
import net.botwithus.events.EventInfo
import net.botwithus.rs3.inventories.events.InventoryEvent
import net.botwithus.rs3.item.InventoryItem
import net.botwithus.rs3.stats.Stats
import net.botwithus.rs3.vars.VarDomain
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
        private const val STAT_RUNTIME_KEY = "runtimeMs"
        private const val STAT_XP_KEY = "xp"
        private const val STAT_LEVELS_KEY = "levels"
        private const val STAT_XP_PER_HOUR_KEY = "xpPerHour"
        private const val STAT_NESTS_PER_HOUR_KEY = "nestsPerHour"
        private const val RUNTIME_PERSIST_INTERVAL_MS = 60_000L
        private const val LOGS_PER_HOUR_REFRESH_MS = 5_000L
        private const val JUJU_EFFECT_DURATION_MS = 360_000L
        private const val JUJU_WITHDRAW_COUNT = 5
        private const val JUJU_WITHDRAW_RETRY_MS = 10_000L
        private val JUJU_EFFECT_VARBITS = intArrayOf(4394, 4395, 4396)
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
    internal val jujuPotionPattern =
        Pattern.compile(".*(?:perfect\\s+)?juju\\s+woodcutting\\s+potion.*", Pattern.CASE_INSENSITIVE)
    internal val jujuVialPattern = Pattern.compile(".*juju\\s+vial.*", Pattern.CASE_INSENSITIVE)
    private val statsLock = Any()
    private val statusLogger = LoggerFactory.getLogger("${UberChop::class.java.name}.status")
    private var lifetimeLogs: Long = 0L
    private var lifetimeBirdNests: Long = 0L
    private var lifetimeRuntimeMs: Long = 0L
    private var sessionRuntimeCommittedMs: Long = 0L
    private var lifetimeWoodcuttingXp: Long = 0L
    private var lifetimeWoodcuttingLevels: Long = 0L
    private var startingWoodcuttingXp: Int = 0
    private var startingWoodcuttingLevel: Int = 0
    private var sessionWoodcuttingXpCommitted: Int = 0
    private var sessionWoodcuttingLevelsCommitted: Int = 0
    private var cachedLogsPerHour: Int = 0
    private var nextLogsPerHourUpdateAt: Long = 0L
    private var lastLogsPerHourLogs: Int = 0
    private var cachedWoodcuttingXpPerHour: Int = 0
    private var nextXpPerHourUpdateAt: Long = 0L
    private var lastWoodcuttingXp: Int = 0
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
    private var jujuEffectExpiresAt: Long = 0L
    private var lastJujuDrinkAttemptAt: Long = 0L
    private var jujuWithdrawRetryAt: Long = 0L
    private enum class JujuRestockMode {
        IDLE,
        REQUIRED,
        IGNORED
    }

    private var jujuRestockMode: JujuRestockMode = JujuRestockMode.IDLE
    private var jujuRestockInitialized = false

    private var mode: BotState = BotState.CHOPPING
    private var statusText: String = "Starting up"
    private var accumulatedRuntimeMs: Long = 0L
    private var activeRuntimeStartMs: Long = 0L
    private var nextRuntimePersistAt: Long = 0L
    private var lastStatusLogMessage: String = ""
    private var lastStatusLogAt: Long = 0L
    private val statusLogCooldownMs: Long = 5_000L
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
        configureLogging()
        super.onInitialize()
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

    override fun onActivation() {
        jujuRestockInitialized = false
        jujuRestockMode = JujuRestockMode.IDLE
        jujuWithdrawRetryAt = 0L
        jujuEffectExpiresAt = 0L
        lastJujuDrinkAttemptAt = 0L
        super.onActivation()
        synchronized(statsLock) {
            if (activeRuntimeStartMs == 0L) {
                activeRuntimeStartMs = System.currentTimeMillis()
                nextRuntimePersistAt = 0L
            }
        }
    }

    override fun onDeactivation() {
        synchronized(statsLock) {
            if (activeRuntimeStartMs != 0L) {
                accumulatedRuntimeMs += System.currentTimeMillis() - activeRuntimeStartMs
                activeRuntimeStartMs = 0L
            }
            nextRuntimePersistAt = 0L
        }
        persistStats()
        super.onDeactivation()
    }

    private fun resetRuntimeStatistics() {
        synchronized(statsLock) {
            logsChopped = 0
            birdNestsCollected = 0
            accumulatedRuntimeMs = 0L
            activeRuntimeStartMs = 0L
            nextRuntimePersistAt = 0L
            sessionRuntimeCommittedMs = 0L
            startingWoodcuttingXp = Stats.WOODCUTTING.xp
            startingWoodcuttingLevel = Stats.WOODCUTTING.level
            sessionWoodcuttingXpCommitted = 0
            sessionWoodcuttingLevelsCommitted = 0
            cachedLogsPerHour = 0
            nextLogsPerHourUpdateAt = 0L
            lastLogsPerHourLogs = 0
            cachedWoodcuttingXpPerHour = 0
            nextXpPerHourUpdateAt = 0L
            lastWoodcuttingXp = 0
        }
        persistStats()
    }

    private fun currentRuntimeMillis(): Long =
        synchronized(statsLock) { currentRuntimeMillisLocked(System.currentTimeMillis()) }

    private fun currentRuntimeMillisLocked(now: Long): Long {
        val accumulated = accumulatedRuntimeMs
        val activeStart = activeRuntimeStartMs
        return if (activeStart != 0L) accumulated + (now - activeStart) else accumulated
    }

    private fun commitSessionTotals() {
        val now = System.currentTimeMillis()
        synchronized(statsLock) {
            val sessionRuntime = currentRuntimeMillisLocked(now)
            val runtimeDelta = sessionRuntime - sessionRuntimeCommittedMs
            if (runtimeDelta > 0) {
                lifetimeRuntimeMs += runtimeDelta
                sessionRuntimeCommittedMs += runtimeDelta
            }

            val sessionXp = (Stats.WOODCUTTING.xp - startingWoodcuttingXp).coerceAtLeast(0)
            val xpDelta = sessionXp - sessionWoodcuttingXpCommitted
            if (xpDelta > 0) {
                lifetimeWoodcuttingXp += xpDelta.toLong()
                sessionWoodcuttingXpCommitted += xpDelta
            }

            val sessionLevels = (Stats.WOODCUTTING.level - startingWoodcuttingLevel).coerceAtLeast(0)
            val levelDelta = sessionLevels - sessionWoodcuttingLevelsCommitted
            if (levelDelta > 0) {
                lifetimeWoodcuttingLevels += levelDelta.toLong()
                sessionWoodcuttingLevelsCommitted += levelDelta
            }
        }
    }

    private fun calculatePerHour(count: Long, runtimeMs: Long): Int {
        if (runtimeMs <= 0L) return 0
        return ((count.toDouble() / runtimeMs.toDouble()) * 3_600_000.0).roundToInt()
    }

    fun logsPerHour(): Int {
        val now = System.currentTimeMillis()
        return synchronized(statsLock) {
            val runtimeSnapshot = currentRuntimeMillisLocked(now)
            if (runtimeSnapshot <= 0L) {
                cachedLogsPerHour = 0
                nextLogsPerHourUpdateAt = now + LOGS_PER_HOUR_REFRESH_MS
                lastLogsPerHourLogs = logsChopped
                return@synchronized 0
            }

            if (now >= nextLogsPerHourUpdateAt || logsChopped != lastLogsPerHourLogs) {
                cachedLogsPerHour = calculatePerHour(logsChopped.toLong(), runtimeSnapshot)
                nextLogsPerHourUpdateAt = now + LOGS_PER_HOUR_REFRESH_MS
                lastLogsPerHourLogs = logsChopped
            }
            val currentXp = woodcuttingXpGained()
            if (now >= nextXpPerHourUpdateAt || currentXp != lastWoodcuttingXp) {
                cachedWoodcuttingXpPerHour = calculatePerHour(currentXp.toLong(), runtimeSnapshot)
                nextXpPerHourUpdateAt = now + LOGS_PER_HOUR_REFRESH_MS
                lastWoodcuttingXp = currentXp
            }
            cachedLogsPerHour
        }
    }

    fun birdNestsPerHour(): Int {
        val runtime = currentRuntimeMillis()
        if (runtime <= 0L) return 0
        return calculatePerHour(birdNestsCollected.toLong(), runtime)
    }

    fun woodcuttingXpGained(): Int =
        (Stats.WOODCUTTING.xp - startingWoodcuttingXp).coerceAtLeast(0)

    fun woodcuttingLevelsGained(): Int =
        (Stats.WOODCUTTING.level - startingWoodcuttingLevel).coerceAtLeast(0)

    fun woodcuttingXpPerHour(): Int = synchronized(statsLock) { cachedWoodcuttingXpPerHour }

    fun lifetimeLogsPerHour(): Int = calculatePerHour(lifetimeLogs, lifetimeRuntimeMs)

    fun lifetimeBirdNestsPerHour(): Int = calculatePerHour(lifetimeBirdNests, lifetimeRuntimeMs)

    fun lifetimeWoodcuttingXpPerHour(): Int = calculatePerHour(lifetimeWoodcuttingXp, lifetimeRuntimeMs)

    fun lifetimeWoodcuttingXpGained(): Long =
        synchronized(statsLock) { lifetimeWoodcuttingXp }

    fun lifetimeWoodcuttingLevelsGained(): Long =
        synchronized(statsLock) { lifetimeWoodcuttingLevels }

    fun lifetimeLogsChopped(): Long = synchronized(statsLock) { lifetimeLogs }

    fun lifetimeBirdNestsCollected(): Long = synchronized(statsLock) { lifetimeBirdNests }

    fun lifetimeRuntimeMillis(): Long = synchronized(statsLock) { lifetimeRuntimeMs }

    private fun setLoggerLevel(loggerName: String, levelName: String) {
        val logger = LoggerFactory.getLogger(loggerName)
        try {
            val levelClass = Class.forName("ch.qos.logback.classic.Level")
            val toLevel = levelClass.getMethod("toLevel", String::class.java)
            val levelInstance = toLevel.invoke(null, levelName)
            val setLevel = logger.javaClass.getMethod("setLevel", levelClass)
            setLevel.invoke(logger, levelInstance)
        } catch (_: Throwable) {
            // Logging backend not available or does not support dynamic level changes.
        }
    }

    private fun configureLogging() {
        setLoggerLevel(javaClass.name, "WARN")
        setLoggerLevel("net.botwithus.xapi.script.permissive.Permissive", "WARN")
        setLoggerLevel("${UberChop::class.java.name}.status", "INFO")
    }

    private fun logEvent(message: String) {
        statusLogger.info(message)
    }

    private fun logStatus(message: String, force: Boolean = false) {
        val now = System.currentTimeMillis()
        val shouldLog = force || message != lastStatusLogMessage || (now - lastStatusLogAt) >= statusLogCooldownMs
        if (shouldLog) {
            statusLogger.info(message)
            lastStatusLogMessage = message
            lastStatusLogAt = now
        }
    }

    private fun persistStats() {
        commitSessionTotals()
        performSavePersistentData()
    }

    private fun maybePersistRuntime() {
        commitSessionTotals()
        logsPerHour()
        val now = System.currentTimeMillis()
        val shouldPersist = synchronized(statsLock) {
            if (activeRuntimeStartMs == 0L) {
                return@synchronized false
            }
            if (nextRuntimePersistAt == 0L || now >= nextRuntimePersistAt) {
                nextRuntimePersistAt = now + RUNTIME_PERSIST_INTERVAL_MS
                true
            } else {
                false
            }
        }
        if (shouldPersist) {
            persistStats()
        }
    }

    override fun onPreTick(): Boolean {
        if (!uiSettingsLoaded) {
            ensureUiSettingsLoaded()
        }
        ensureJujuRestockBootstrap()
        maybePersistRuntime()

        return super.onPreTick()
    }

override fun savePersistentData(container: JsonObject?) {
    commitSessionTotals()
    val target = container ?: return
    val settingsObj = gson.toJsonTree(settings).asJsonObject
    settingsObj.addProperty("targetTree", targetTree)
    settingsObj.addProperty("location", location)
    target.add("settings", settingsObj)

    data class SessionSnapshot(
        val logs: Int,
        val nests: Int,
        val runtimeMs: Long,
        val xp: Int,
        val levels: Int
    )

    data class LifetimeSnapshot(
        val logs: Long,
        val nests: Long,
        val runtimeMs: Long,
        val xp: Long,
        val levels: Long
    )

    val now = System.currentTimeMillis()
    val sessionSnapshot: SessionSnapshot
    val lifetimeSnapshot: LifetimeSnapshot
    synchronized(statsLock) {
        val runtimeMs = currentRuntimeMillisLocked(now)
        val sessionXp = (Stats.WOODCUTTING.xp - startingWoodcuttingXp).coerceAtLeast(0)
        val sessionLevels = (Stats.WOODCUTTING.level - startingWoodcuttingLevel).coerceAtLeast(0)
        sessionSnapshot = SessionSnapshot(
            logsChopped,
            birdNestsCollected,
            runtimeMs,
            sessionXp,
            sessionLevels
        )
        lifetimeSnapshot = LifetimeSnapshot(
            lifetimeLogs,
            lifetimeBirdNests,
            lifetimeRuntimeMs,
            lifetimeWoodcuttingXp,
            lifetimeWoodcuttingLevels
        )
    }

val sessionLogsPerHour = calculatePerHour(sessionSnapshot.logs.toLong(), sessionSnapshot.runtimeMs)
val sessionNestsPerHour = calculatePerHour(sessionSnapshot.nests.toLong(), sessionSnapshot.runtimeMs)
val sessionXpPerHour = calculatePerHour(sessionSnapshot.xp.toLong(), sessionSnapshot.runtimeMs)
val lifetimeLogsPerHour = calculatePerHour(lifetimeSnapshot.logs, lifetimeSnapshot.runtimeMs)
val lifetimeNestsPerHour = calculatePerHour(lifetimeSnapshot.nests, lifetimeSnapshot.runtimeMs)
val lifetimeXpPerHour = calculatePerHour(lifetimeSnapshot.xp, lifetimeSnapshot.runtimeMs)

val statsObject = JsonObject().apply {
    add("session", JsonObject().apply {
        addProperty(STAT_LOGS_KEY, sessionSnapshot.logs)
        addProperty(STAT_LOGS_PER_HOUR_KEY, sessionLogsPerHour)
        addProperty(STAT_NESTS_PER_HOUR_KEY, sessionNestsPerHour)
        addProperty(STAT_XP_PER_HOUR_KEY, sessionXpPerHour)
        addProperty(STAT_BIRD_NESTS_KEY, sessionSnapshot.nests)
        addProperty(STAT_RUNTIME_KEY, sessionSnapshot.runtimeMs)
        addProperty(STAT_XP_KEY, sessionSnapshot.xp)
        addProperty(STAT_LEVELS_KEY, sessionSnapshot.levels)
    })
    add("overall", JsonObject().apply {
        addProperty(STAT_LOGS_KEY, lifetimeSnapshot.logs)
        addProperty(STAT_LOGS_PER_HOUR_KEY, lifetimeLogsPerHour)
        addProperty(STAT_NESTS_PER_HOUR_KEY, lifetimeNestsPerHour)
        addProperty(STAT_XP_PER_HOUR_KEY, lifetimeXpPerHour)
        addProperty(STAT_BIRD_NESTS_KEY, lifetimeSnapshot.nests)
        addProperty(STAT_RUNTIME_KEY, lifetimeSnapshot.runtimeMs)
        addProperty(STAT_XP_KEY, lifetimeSnapshot.xp)
        addProperty(STAT_LEVELS_KEY, lifetimeSnapshot.levels)
    })
}
    target.add("stats", statsObject)
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

    source.getAsJsonObject("stats")?.let { stats ->
        val overall = stats.getAsJsonObject("overall") ?: stats
        synchronized(statsLock) {
            lifetimeLogs = overall.get(STAT_LOGS_KEY)?.asLong ?: lifetimeLogs
            lifetimeBirdNests = overall.get(STAT_BIRD_NESTS_KEY)?.asLong ?: lifetimeBirdNests
            lifetimeRuntimeMs = overall.get(STAT_RUNTIME_KEY)?.asLong ?: lifetimeRuntimeMs
            lifetimeWoodcuttingXp = overall.get(STAT_XP_KEY)?.asLong ?: lifetimeWoodcuttingXp
            lifetimeWoodcuttingLevels = overall.get(STAT_LEVELS_KEY)?.asLong ?: lifetimeWoodcuttingLevels
        }
    }

    uiSettingsLoaded = false
}

    fun updateStatus(text: String) {
        statusText = text
        setStatus(text)
        logStatus("Status: $text")
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
        logEvent("State -> ${next.description}: $reason")
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

    internal fun hasJujuPotionInBackpack(): Boolean =
        Backpack.getItems().any { jujuPotionPattern.matcher(it.name).matches() }

    private fun ensureJujuRestockBootstrap() {
        if (jujuRestockInitialized) {
            if (!settings.useJujuPotions) {
                jujuRestockMode = JujuRestockMode.IDLE
            }
            return
        }
        if (!settings.useJujuPotions) {
            jujuRestockMode = JujuRestockMode.IDLE
            jujuRestockInitialized = true
            return
        }

        jujuRestockInitialized = true
        if (hasJujuPotionInBackpack()) {
            jujuRestockMode = JujuRestockMode.IDLE
        } else {
            jujuRestockMode = JujuRestockMode.REQUIRED
            log.debug("Juju restock: no potions in backpack; requesting bank visit")
        }
    }

    private fun requireJujuRestock(reason: String? = null) {
        if (!settings.useJujuPotions || jujuRestockMode == JujuRestockMode.IGNORED) {
            return
        }
        if (jujuRestockMode != JujuRestockMode.REQUIRED) {
            jujuRestockMode = JujuRestockMode.REQUIRED
            jujuWithdrawRetryAt = 0L
        }
        reason?.let { log.debug("Juju restock requested: $it") }
    }

    private fun markJujuUnavailable() {
        if (jujuRestockMode != JujuRestockMode.IGNORED) {
            jujuRestockMode = JujuRestockMode.IGNORED
            log.warn("No juju woodcutting potions available in bank; continuing without juju support")
        }
    }

    private fun bankContainsJujuPotion(): Boolean? = runCatching {
        val methods = Bank::class.java.methods
        val contains = methods.firstOrNull { method ->
            method.name == "contains" && method.parameterTypes.size == 1 &&
                Pattern::class.java.isAssignableFrom(method.parameterTypes[0])
        }
        if (contains != null) {
            contains.isAccessible = true
            val result = contains.invoke(null, jujuPotionPattern) as? Boolean
            if (result != null) {
                return@runCatching result
            }
        }

        val itemsMethod = methods.firstOrNull { method ->
            method.parameterTypes.isEmpty() && (method.name == "getItems" || method.name == "items")
        } ?: return@runCatching null
        itemsMethod.isAccessible = true
        val itemsResult = itemsMethod.invoke(null)
        val iterable = when (itemsResult) {
            is Collection<*> -> itemsResult
            is Array<*> -> itemsResult.asList()
            else -> return@runCatching null
        }
        for (entry in iterable) {
            val name = runCatching {
                val nameMethod = entry?.javaClass?.methods?.firstOrNull { method ->
                    method.name == "getName" && method.parameterTypes.isEmpty()
                }
                nameMethod?.isAccessible = true
                nameMethod?.invoke(entry) as? String
            }.getOrNull()
            if (name != null && jujuPotionPattern.matcher(name).matches()) {
                return@runCatching true
            }
        }
        false
    }.getOrNull()

    internal fun needsJujuRestock(): Boolean =
        settings.useJujuPotions && jujuRestockMode == JujuRestockMode.REQUIRED && !hasJujuPotionInBackpack()

    internal fun shouldStayAtBankForJuju(): Boolean = needsJujuRestock()

    internal fun shouldDepositJujuVials(): Boolean =
        settings.useJujuPotions && Backpack.contains(jujuVialPattern)

    internal fun shouldRestockJujuPotions(): Boolean {
        if (!needsJujuRestock()) {
            if (settings.useJujuPotions && jujuRestockMode == JujuRestockMode.REQUIRED && hasJujuPotionInBackpack()) {
                jujuRestockMode = JujuRestockMode.IDLE
                jujuWithdrawRetryAt = 0L
            }
            return false
        }
        if (Backpack.isFull()) {
            return false
        }
        return System.currentTimeMillis() >= jujuWithdrawRetryAt
    }

    internal fun attemptJujuWithdraw(): Boolean {
        val now = System.currentTimeMillis()
        if (now < jujuWithdrawRetryAt) {
            return false
        }
        val bankHas = bankContainsJujuPotion()
        if (bankHas == false && !hasJujuPotionInBackpack()) {
            markJujuUnavailable()
            jujuWithdrawRetryAt = 0L
            return false
        }

        val withdrew = runCatching { Bank.withdraw(jujuPotionPattern, JUJU_WITHDRAW_COUNT) }
            .onFailure { error -> log.warn("AttemptJujuWithdraw: withdraw threw ${error.message}") }
            .getOrDefault(false)

        if (hasJujuPotionInBackpack()) {
            jujuRestockMode = JujuRestockMode.IDLE
            jujuWithdrawRetryAt = 0L
            return true
        }

        if (bankHas == false) {
            markJujuUnavailable()
            jujuWithdrawRetryAt = 0L
        } else if (!withdrew) {
            jujuWithdrawRetryAt = now + JUJU_WITHDRAW_RETRY_MS
        }
        return withdrew
    }

    internal fun shouldDrinkJujuPotion(): Boolean {
        if (!settings.useJujuPotions) {
            return false
        }
        if (isJujuEffectActive()) {
            return false
        }
        if (!hasJujuPotionInBackpack()) {
            requireJujuRestock("Backpack has no juju potions while effect inactive")
            return false
        }
        val now = System.currentTimeMillis()
        return now - lastJujuDrinkAttemptAt >= 1_000L
    }

    internal fun drinkJujuPotion(): Boolean {
        val potion = Backpack.getItems().firstOrNull { jujuPotionPattern.matcher(it.name).matches() }
            ?: run {
                requireJujuRestock("Attempted to drink juju potion but none found in backpack")
                return false
            }
        lastJujuDrinkAttemptAt = System.currentTimeMillis()
        val drank = Backpack.interact(potion, "Drink") || Backpack.interact(potion, "Sip")
        if (drank) {
            jujuEffectExpiresAt = System.currentTimeMillis() + JUJU_EFFECT_DURATION_MS
            log.debug("DrinkJujuPotion: consumed ${potion.name}")
            delay(1)
        } else {
            log.warn("DrinkJujuPotion: failed to interact with ${potion.name}")
            requireJujuRestock("Failed to interact with juju potion ${potion.name}")
        }
        return drank
    }

    internal fun isJujuEffectActive(): Boolean {
        val now = System.currentTimeMillis()
        if (now < jujuEffectExpiresAt) {
            return true
        }
        for (varbit in JUJU_EFFECT_VARBITS) {
            val value = runCatching { VarDomain.getVarBitValue(varbit) }
                .onFailure { error -> log.debug("isJujuEffectActive: varbit $varbit read failed: ${error.message}") }
                .getOrNull()
            if (value != null && value != 0) {
                jujuEffectExpiresAt = now + JUJU_EFFECT_DURATION_MS
                return true
            }
        }
        return false
    }


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
    fun onInventoryEvent(event: InventoryEvent) {
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

        var statsChanged = false
        synchronized(statsLock) {
            if (isLog) {
                logsChopped += quantityAdded
                lifetimeLogs += quantityAdded
                statsChanged = true
            }
            if (isBirdNest) {
                birdNestsCollected += quantityAdded
                lifetimeBirdNests += quantityAdded
                statsChanged = true
            }
        }
        if (statsChanged) {
            persistStats()
        }
    }

    fun formattedRuntime(): String {
        val elapsed = currentRuntimeMillis().coerceAtLeast(0L)
        val totalSeconds = elapsed / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d:%02d", hours, minutes, seconds)
    }

    fun ensureUiSettingsLoaded() {
        if (uiSettingsLoaded) {
            return
        }

        applyLocationSelection()
        uiSettingsLoaded = true
    }

    fun onSettingsChanged() {
        jujuRestockInitialized = false
        if (!settings.useJujuPotions) {
            jujuRestockMode = JujuRestockMode.IDLE
        }
        jujuWithdrawRetryAt = 0L
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

