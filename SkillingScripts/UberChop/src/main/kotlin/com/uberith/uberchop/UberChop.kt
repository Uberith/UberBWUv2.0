package com.uberith.uberchop

import com.uberith.api.script.PhasedPersistentScript
import com.uberith.api.game.skills.woodcutting.TreeLocation
import com.uberith.uberchop.TreeTypes
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

    private val logger: Logger = LoggerFactory.getLogger(this.javaClass)

    private val breakScheduler = BreakScheduler()
    private val logoutGuard = LogoutGuard()
    private val afkJitter = AfkJitter()
    private val worldHopPolicy = WorldHopPolicy()

    private val gui: UberChopGUI by lazy { UberChopGUI(this) }

    @Volatile private var uiPrimed = false
    @Volatile private var status: String = "Idle"

    private val LOG_COUNTER = "logs"

    override fun onInitialize() {
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
