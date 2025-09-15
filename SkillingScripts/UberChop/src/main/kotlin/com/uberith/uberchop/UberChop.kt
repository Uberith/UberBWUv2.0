package com.uberith.uberchop

import com.uberith.api.SuspendableScript
import com.uberith.api.utils.Persistence
import com.google.gson.reflect.TypeToken
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
    description = "Uber tree chopper",
    version = "0.3.0",
    author = "Uberith"
)
class UberChop : SuspendableScript() {
    // Persistence for user settings
    private val settingsPersistence = Persistence<Settings>("UberChop.json", object : TypeToken<Settings>() {}.type)

    // Ensure persistence directory/file is initialized as soon as the script is constructed
    init {
        try {
            ensurePersistenceInitialized()
        } catch (_: Throwable) { }
    }

    fun ensurePersistenceInitialized() {
        // Ensure base directory exists
        val dir = settingsPersistence.location.parentFile
        val localLog = Persistence.forLog("UberChop")
        try {
            val dirOk = settingsPersistence.ensureBaseDir()
            val msg = "Init persistence: ensureBaseDir dir='${dir?.absolutePath}' ok=${dirOk}"
            logger.info(msg)
            localLog.appendLine(msg)
            if (!dirOk) localLog.appendLine("Init persistence: base dir creation failed for '${dir?.absolutePath}'")
        } catch (e: Throwable) {
            logger.error("Init persistence: exception ensuring base dir '{}'", dir?.absolutePath, e)
            localLog.appendLine("Init persistence: exception ensuring base dir '${dir?.absolutePath}': ${e.message}")
        }
        // Ensure file exists; initialize with current in-memory defaults if missing
        try {
            val fileOk = settingsPersistence.ensureFile(defaultProvider = { settings })
            val exists = settingsPersistence.location.exists()
            val msg = "Init persistence: ensureFile path='${settingsPersistence.location.absolutePath}' ok=${fileOk} exists=${exists}"
            logger.info(msg)
            localLog.appendLine(msg)
            if (!fileOk) localLog.appendLine("Init persistence: failed to create settings file at '${settingsPersistence.location.absolutePath}'")
        } catch (e: Throwable) {
            logger.error("Init persistence: exception creating settings file '{}'", settingsPersistence.location.absolutePath, e)
            localLog.appendLine("Init persistence: exception creating settings file '${settingsPersistence.location.absolutePath}': ${e.message}")
        }
    }

    // Load settings from disk (if present) into the current settings instance
    private fun loadSettings() {
        val s = settingsPersistence.loadOrCreate { settings }
        run {
            // Break handler
            settings.performRandomBreak = s.performRandomBreak
            settings.breakFrequency = s.breakFrequency
            settings.minBreak = s.minBreak
            settings.maxBreak = s.maxBreak

            // Logout handler
            settings.logoutDurationEnable = s.logoutDurationEnable
            settings.logoutHours = s.logoutHours
            settings.logoutMinutes = s.logoutMinutes
            settings.logoutSeconds = s.logoutSeconds

            // AFK handler
            settings.enableAfk = s.enableAfk
            settings.afkEveryMin = s.afkEveryMin
            settings.afkEveryMax = s.afkEveryMax
            settings.afkDurationMin = s.afkDurationMin
            settings.afkDurationMax = s.afkDurationMax

            // Auto-Stop handler
            settings.enableAutoStop = s.enableAutoStop
            settings.stopAfterHours = s.stopAfterHours
            settings.stopAfterMinutes = s.stopAfterMinutes
            settings.stopAfterXp = s.stopAfterXp
            settings.stopAfterLogs = s.stopAfterLogs

            // Extra features
            settings.pickupNests = s.pickupNests
            settings.enableTreeRotation = s.enableTreeRotation

            // Control
            settings.enableWorldHopping = s.enableWorldHopping
            settings.useMagicNotepaper = s.useMagicNotepaper
            settings.useCrystallise = s.useCrystallise
            settings.useJujuPotions = s.useJujuPotions

            // Auto-skill
            settings.autoProgressTree = s.autoProgressTree
            settings.autoUpgradeTree = s.autoUpgradeTree
            settings.tanningProductIndex = s.tanningProductIndex

            // World hopping filters
            settings.minPing = s.minPing
            settings.maxPing = s.maxPing
            settings.minPopulation = s.minPopulation
            settings.maxPopulation = s.maxPopulation
            settings.hopDelayMs = s.hopDelayMs
            settings.memberOnlyWorlds = s.memberOnlyWorlds
            settings.onlyFreeToPlay = s.onlyFreeToPlay
            settings.hopOnChat = s.hopOnChat
            settings.hopOnCrowd = s.hopOnCrowd
            settings.playerThreshold = s.playerThreshold
            settings.hopOnNoTrees = s.hopOnNoTrees

            // Last used
            settings.savedTreeType = s.savedTreeType
            settings.savedLocation = s.savedLocation
            settings.logHandlingMode = s.logHandlingMode

            // Items
            settings.withdrawWoodBox = s.withdrawWoodBox

            // Custom per-location and deposit filters
            settings.customLocations.clear(); settings.customLocations.putAll(s.customLocations)
            settings.depositInclude.clear(); settings.depositInclude.addAll(s.depositInclude)
            settings.depositKeep.clear(); settings.depositKeep.addAll(s.depositKeep)
        }
    }

    // Persist current settings to disk
    fun saveSettings(s: Settings = settings) {
        // Ensure saved tree and location reflect current selections
        val idx = com.uberith.uberchop.TreeTypes.ALL.indexOf(targetTree)
        if (idx >= 0) s.savedTreeType = idx
        s.savedLocation = location
        // Safer write using atomic replace
        settingsPersistence.saveDataAtomic(s)
        try {
            logger.info("Settings saved to {}", settingsPersistence.location.absolutePath)
        } catch (_: Throwable) { }
    }
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
        try {
            ensurePersistenceInitialized()
        } catch (_: Throwable) { }

        // Load persisted settings before reading them
        loadSettings()
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
        // Persist initial selections so a config file is created even before any UI changes
        saveSettings()
        status = "Active - Preparing"
        phase = Phase.PREPARING
        // sync cached flags from settings
        withdrawWoodBox = settings.withdrawWoodBox
        totalRuntimeMs = 0L
        lastRuntimeUpdateMs = System.currentTimeMillis()
    }

    override fun onDeactivation() {
        // Save settings on deactivation for good measure
        saveSettings()
        status = "Inactive"
        phase = Phase.READY
        logger.info("Deactivated")
    }

    override suspend fun onLoop() {
        try {
            ensurePersistenceInitialized()
        } catch (_: Throwable) { }

        // Load persisted settings before reading them
        loadSettings()
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

                // If Withdrawal wood box is enabled, retrieve from bank
                val woodBoxPat = java.util.regex.Pattern.compile(".*wood box.*", java.util.regex.Pattern.CASE_INSENSITIVE)
                if (!Backpack.contains(woodBoxPat) && withdrawWoodBox) {
                    Bank.withdraw(woodBoxPat, 1)
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
                    if (!Coordinates.isPlayerWithinRadius(chopTile, 40)) {
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
