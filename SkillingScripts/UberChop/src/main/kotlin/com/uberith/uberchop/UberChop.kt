package com.uberith.uberchop

import com.uberith.api.script.PhasedPersistentScript
import com.uberith.api.game.skills.woodcutting.TreeLocation
import com.uberith.uberchop.TreeTypes
import com.uberith.api.game.skills.woodcutting.LogHandlingMode
import com.uberith.api.script.handlers.AfkSettings
import com.uberith.api.script.handlers.BreakSettings
import com.uberith.api.script.handlers.LogoutSettings
import com.uberith.api.script.handlers.WorldHopSettings
import com.uberith.api.utils.ConfigFiles
import com.google.gson.GsonBuilder
import com.uberith.api.game.skills.woodcutting.Trees
import com.uberith.api.game.skills.woodcutting.WoodcuttingSession
import com.uberith.api.script.handlers.AfkJitter
import com.uberith.api.script.handlers.BreakScheduler
import com.uberith.api.script.handlers.LogoutGuard
import com.uberith.api.script.handlers.WorldHopPolicy
import com.uberith.uberchop.gui.UberChopGUI
import net.botwithus.rs3.stats.Stats
import net.botwithus.rs3.world.Coordinate
import net.botwithus.scripts.Info
import net.botwithus.ui.workspace.Workspace
import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Info(
    name = "UberChop",
    description = "Uber tree chopper",
    version = "0.5.0",
    author = "Uberith"
)
class UberChop : PhasedPersistentScript<UberChopSettings, Phase, WoodcuttingSession>(
    moduleName = "UberChop",
    settingsClass = UberChopSettings::class.java,
    defaultFactory = { UberChopSettings() },
    initialPhase = Phase.READY,
    phaseLogger = LoggerFactory.getLogger("UberChopPhases")
) {

    companion object {
        private const val MODULE_NAME = "UberChop"
    }

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    private val breakScheduler = BreakScheduler()
    private val logoutGuard = LogoutGuard()
    private val afkJitter = AfkJitter()
    private val worldHopPolicy = WorldHopPolicy()

    private val gui: UberChopGUI by lazy { UberChopGUI(this) }

    @Volatile private var uiPrimed = false
    @Volatile private var status: String = "Idle"

    private val LOG_COUNTER = "logs"

    private fun migrateLegacySettings() {
        val raw = ConfigFiles.readModuleSettings(MODULE_NAME) ?: return
        if (raw.contains("breakSettings")) return
        val gson = GsonBuilder().create()
        try {
            val legacy = gson.fromJson(raw, LegacySettings::class.java) ?: return
            val converted = legacy.toUberChopSettings()
            val pretty = GsonBuilder().setPrettyPrinting().create().toJson(converted, UberChopSettings::class.java)
            ConfigFiles.writeModuleSettings(MODULE_NAME, pretty)
            logger.info("Migrated legacy UberChop settings to new format")
        } catch (t: Throwable) {
            logger.warn("Unable to migrate legacy UberChop settings: {}", t.message)
        }
    }

    override fun onInitialize() {
        migrateLegacySettings()
        super.onInitialize()
        ensureSettingsLoaded()
    }

    override fun onActivation() {
        super.onActivation()
        uiPrimed = false
        status = "Active - Preparing"
        phases.transition(Phase.PREPARING, "Phase: READY -> PREPARING")
        applyHandlerSettings(settingsSnapshot())
        logger.info(
            "Activated: tree='{}', location='{}' (locations={})",
            resolveTreeName(settingsSnapshot().treeIndex),
            settingsSnapshot().locationName,
            Trees.locations.size
        )
    }

    override fun onDeactivation() {
        status = "Inactive"
        phases.transition(Phase.READY)
        persistSettings(logOnFailure = true)
        logger.info("Deactivated")
        super.onDeactivation()
    }

    override fun onDraw(workspace: Workspace) {
        ensureUiSettingsLoaded()
        gui.render(workspace)
    }

    fun ensureUiSettingsLoaded() {
        if (uiPrimed) return
        ensureSettingsLoaded()
        refreshLocationFallback()
        applyHandlerSettings(settingsSnapshot())
        uiPrimed = true
    }

    fun statusText(): String = status
    fun formattedRuntime(): String = runtime.snapshot().formattedTime()
    fun logsChopped(): Long = runtime.snapshot().count(LOG_COUNTER)
    fun logsPerHour(): Int = runtime.snapshot().perHour(LOG_COUNTER)
    fun playerWoodcuttingLevel(): Int = Stats.WOODCUTTING.currentLevel

    fun settingsSnapshot(): UberChopSettings = settingsState.value

    fun mutateSettings(transform: (UberChopSettings) -> UberChopSettings) {
        val updated = updateSettings(transform)
        refreshLocationFallback()
        applyHandlerSettings(updated)
    }

    fun applySettingsSnapshot(snapshot: UberChopSettings) {
        replaceSettings(snapshot, persist = true)
        refreshLocationFallback()
        applyHandlerSettings(snapshot)
    }

    override fun createContext(): WoodcuttingSession {
        return WoodcuttingSession(
            script = this,
            logger = logger,
            withdrawWoodBox = { settingsSnapshot().withdrawWoodBox },
            pickupBirdNests = { settingsSnapshot().pickupNests },
            logHandling = { settingsSnapshot().logHandling },
            chopTile = { effectiveChopCoordinate() },
            bankTile = { effectiveBankCoordinate() }
        )
    }

    override suspend fun ScriptLoop.onStart(): Boolean {
        status = "Preparing"
        return session.prepare()
    }

    override suspend fun ScriptLoop.onTick(): Boolean {
        val now = System.currentTimeMillis()
        if (breakScheduler.tick(this@UberChop, now)) return true
        if (afkJitter.tick(this@UberChop, now)) return true

        val snapshot = runtime.snapshot()
        if (logoutGuard.shouldLogout(snapshot, logsChopped())) {
            status = "Stopping soon (logout target met)"
            return false
        }

        return phaseLoop {
            when (phase) {
                Phase.READY -> transitionTo(Phase.PREPARING)
                Phase.PREPARING -> {
                    status = "Preparing"
                    if (session.prepare()) stay() else transitionTo(Phase.CHOPPING, "Phase: PREPARING -> CHOPPING")
                }
                Phase.CHOPPING -> {
                    val targetTree = resolveTreeName(settingsSnapshot().treeIndex)
                    status = "Chopping $targetTree"
                    when (session.chop(targetTree)) {
                        WoodcuttingSession.ChopOutcome.WAITED -> stay()
                        WoodcuttingSession.ChopOutcome.STARTED -> {
                            runtime.increment(LOG_COUNTER)
                            stay()
                        }
                        WoodcuttingSession.ChopOutcome.NEEDS_BANK -> transitionTo(Phase.BANKING, "Phase: CHOPPING -> BANKING")
                        WoodcuttingSession.ChopOutcome.NONE -> {
                            if (maybeRotateLocation(targetTree)) {
                                stay()
                            } else if (worldHopPolicy.shouldHop(playersNearby = 0, resourcesRemaining = 0)) {
                                status = "Considering world hop"
                                stay()
                            } else {
                                repeat()
                            }
                        }
                    }
                }
                Phase.BANKING -> {
                    status = "Banking"
                    when (session.bankInventory()) {
                        WoodcuttingSession.BankOutcome.WAITED -> stay()
                        WoodcuttingSession.BankOutcome.INVENTORY_READY -> transitionTo(Phase.CHOPPING, "Phase: BANKING -> CHOPPING")
                    }
                }
            }
        }
    }

    fun phase(): Phase = phases.phase

    fun treeNames(): List<String> = TreeTypes.ALL

    fun availableLocations(): List<TreeLocation> {
        val treeName = resolveTreeName(settingsSnapshot().treeIndex)
        return Trees.locationsFor(treeName)
    }

    fun selectedLocation(): TreeLocation? {
        val name = settingsSnapshot().locationName
        return Trees.locations.firstOrNull { it.name == name }
    }

    private fun resolveTreeName(index: Int): String {
        val names = TreeTypes.ALL
        return if (names.isEmpty()) "Tree" else names[index.coerceIn(0, names.lastIndex)]
    }

    private fun refreshLocationFallback() {
        val snapshot = settingsSnapshot()
        val treeName = resolveTreeName(snapshot.treeIndex)
        val resolved = Trees.resolveLocation(treeName, snapshot.locationName)
        val fallback = resolved?.name ?: Trees.locations.firstOrNull()?.name.orEmpty()
        if (fallback != snapshot.locationName) {
            super.updateSettings(transform = { current -> current.copy(locationName = fallback) }, persist = true)
        }
    }

    private fun effectiveChopCoordinate(): Coordinate? {
        val snapshot = settingsSnapshot()
        val locName = snapshot.locationName
        snapshot.customLocations[locName]?.let { override ->
            val (cx, cy, cz) = override.chopCoords()
            if (cx != null && cy != null && cz != null) return Coordinate(cx, cy, cz)
        }
        return selectedLocation()?.chop
    }

    private fun effectiveBankCoordinate(): Coordinate? {
        val snapshot = settingsSnapshot()
        val locName = snapshot.locationName
        snapshot.customLocations[locName]?.let { override ->
            val (bx, by, bz) = override.bankCoords()
            if (bx != null && by != null && bz != null) return Coordinate(bx, by, bz)
        }
        return selectedLocation()?.bank
    }

    private val ScriptLoop.session: WoodcuttingSession
        get() = context

    private val ScriptLoop.PhaseMachine.session: WoodcuttingSession
        get() = context

    private fun maybeRotateLocation(treeName: String): Boolean {
        val snapshot = settingsSnapshot()
        if (!snapshot.enableTreeRotation) return false
        val locations = Trees.locationsFor(treeName)
        if (locations.isEmpty()) return false
        val currentName = snapshot.locationName
        val currentIndex = locations.indexOfFirst { it.name.equals(currentName, ignoreCase = true) }
        val nextIndex = if (currentIndex == -1 || currentIndex + 1 >= locations.size) 0 else currentIndex + 1
        val next = locations[nextIndex]
        if (next.name.equals(currentName, ignoreCase = true)) return false
        status = "Rotating to ${next.name}"
        mutateSettings { it.copy(locationName = next.name) }
        logger.info("Rotated location to {}", next.name)
        return true
    }

    private fun applyHandlerSettings(settings: UberChopSettings) {
        breakScheduler.update(settings.breakSettings)
        logoutGuard.update(settings.logoutSettings, runtime.snapshot())
        afkJitter.update(settings.afkSettings)
        worldHopPolicy.update(settings.worldHopSettings)
    }
}

private data class LegacySettings(
    val savedTreeType: Int? = null,
    val savedLocation: String? = null,
    val withdrawWoodBox: Boolean? = null,
    val pickupNests: Boolean? = null,
    val logHandlingMode: Int? = null,
    val enableTreeRotation: Boolean? = null,
    val customLocations: Map<String, LegacyCustomLocation>? = null,
    val performRandomBreak: Boolean? = null,
    val breakFrequency: Int? = null,
    val minBreak: Int? = null,
    val maxBreak: Int? = null,
    val enableAfk: Boolean? = null,
    val afkEveryMin: Int? = null,
    val afkEveryMax: Int? = null,
    val afkDurationMin: Int? = null,
    val afkDurationMax: Int? = null,
    val enableAutoStop: Boolean? = null,
    val stopAfterHours: Int? = null,
    val stopAfterMinutes: Int? = null,
    val stopAfterLogs: Int? = null,
    val stopAfterXp: Int? = null,
    val logoutDurationEnable: Boolean? = null,
    val logoutHours: Int? = null,
    val logoutMinutes: Int? = null,
    val logoutSeconds: Int? = null,
    val enableWorldHopping: Boolean? = null,
    val hopOnCrowd: Boolean? = null,
    val hopOnNoTrees: Boolean? = null,
    val playerThreshold: Int? = null,
    val hopDelayMs: Int? = null
)

private data class LegacyCustomLocation(
    val chopX: Int? = null,
    val chopY: Int? = null,
    val chopZ: Int? = null,
    val bankX: Int? = null,
    val bankY: Int? = null,
    val bankZ: Int? = null
)

private fun LegacySettings.toUberChopSettings(): UberChopSettings {
    val defaultBreaks = BreakSettings()
    val breaksPerHour = breakFrequency ?: 0
    val computedFrequency = when {
        performRandomBreak != true -> defaultBreaks.frequencyMinutes
        breaksPerHour <= 0 -> defaultBreaks.frequencyMinutes
        else -> (60 / breaksPerHour).coerceAtLeast(5)
    }
    val minBreakSeconds = (minBreak ?: defaultBreaks.minDurationSeconds).coerceAtLeast(10)
    val maxBreakSeconds = (maxBreak ?: defaultBreaks.maxDurationSeconds).coerceAtLeast(minBreakSeconds)
    val breakSettings = BreakSettings(
        enabled = performRandomBreak == true,
        frequencyMinutes = computedFrequency,
        varianceMinutes = 0,
        minDurationSeconds = minBreakSeconds,
        maxDurationSeconds = maxBreakSeconds
    )

    val defaultAfk = AfkSettings()
    val minEvery = (afkEveryMin ?: defaultAfk.minEveryMinutes).coerceAtLeast(1)
    val maxEvery = (afkEveryMax ?: defaultAfk.maxEveryMinutes).coerceAtLeast(minEvery)
    val minAfkSeconds = (afkDurationMin ?: defaultAfk.minDurationSeconds).coerceAtLeast(5)
    val maxAfkSeconds = (afkDurationMax ?: defaultAfk.maxDurationSeconds).coerceAtLeast(minAfkSeconds)
    val afkSettings = AfkSettings(
        enabled = enableAfk == true,
        minEveryMinutes = minEvery,
        maxEveryMinutes = maxEvery,
        minDurationSeconds = minAfkSeconds,
        maxDurationSeconds = maxAfkSeconds
    )

    val defaultLogout = LogoutSettings()
    val logoutEnabled = (enableAutoStop == true) || (logoutDurationEnable == true)
    val durationHours = when {
        logoutDurationEnable == true -> logoutHours
        enableAutoStop == true -> stopAfterHours
        else -> null
    }?.coerceAtLeast(0) ?: defaultLogout.maxHours
    val baseMinutes = when {
        logoutDurationEnable == true -> logoutMinutes
        enableAutoStop == true -> stopAfterMinutes
        else -> null
    }?.coerceAtLeast(0) ?: defaultLogout.maxMinutes
    val extraMinutes = if (logoutDurationEnable == true) ((logoutSeconds ?: 0).coerceAtLeast(0) / 60) else 0
    val logoutSettings = LogoutSettings(
        enabled = logoutEnabled,
        maxHours = durationHours,
        maxMinutes = (baseMinutes + extraMinutes).coerceAtLeast(0),
        targetXpGained = if (enableAutoStop == true) (stopAfterXp ?: 0).coerceAtLeast(0) else defaultLogout.targetXpGained,
        targetActions = if (enableAutoStop == true) (stopAfterLogs ?: 0).coerceAtLeast(0).toLong() else defaultLogout.targetActions
    )

    val defaultHop = WorldHopSettings()
    val hopCooldownSeconds = when {
        hopDelayMs != null && hopDelayMs > 0 -> (hopDelayMs / 1000).coerceAtLeast(10)
        else -> defaultHop.hopCooldownSeconds
    }
    val worldHopSettings = WorldHopSettings(
        enabled = enableWorldHopping == true,
        hopOnCrowd = hopOnCrowd == true,
        playerThreshold = (playerThreshold ?: defaultHop.playerThreshold).coerceAtLeast(1),
        hopOnNoResources = hopOnNoTrees == true,
        hopCooldownSeconds = hopCooldownSeconds
    )

    val convertedCustomLocations = customLocations?.mapValues { (_, value) ->
        CustomLocation(
            chopX = value.chopX,
            chopY = value.chopY,
            chopZ = value.chopZ,
            bankX = value.bankX,
            bankY = value.bankY,
            bankZ = value.bankZ
        )
    } ?: emptyMap()

    val handling = when (logHandlingMode) {
        1 -> LogHandlingMode.DROP
        else -> LogHandlingMode.BANK
    }

    return UberChopSettings(
        treeIndex = savedTreeType ?: 0,
        locationName = savedLocation ?: "",
        withdrawWoodBox = withdrawWoodBox ?: false,
        pickupNests = pickupNests ?: false,
        logHandling = handling,
        enableTreeRotation = enableTreeRotation ?: false,
        customLocations = convertedCustomLocations,
        breakSettings = breakSettings,
        logoutSettings = logoutSettings,
        afkSettings = afkSettings,
        worldHopSettings = worldHopSettings
    )
}
